package com.example.cs414finalprojectandroid

import android.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import com.example.cs414finalprojectandroid.Utilities.getReplayBook
import com.example.cs414finalprojectandroid.Utilities.hexToByteArray
import com.example.cs414finalprojectandroid.Utilities.showToast
import com.example.cs414finalprojectandroid.replays.PacketReplayStatus
import io.paperdb.Paper
import kotlinx.android.synthetic.main.activity_view_packet_replays.*

class ViewPacketReplaysActivity : AppCompatActivity() {
    private var selectedPacketReplayIndex: Int = -1

    private var packetReplayThread: Thread? = null
    private val packetReplayList = ArrayList<String>(100)

    private var packetReplayStatus = PacketReplayStatus.Stopped
    private lateinit var packetReplayAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_packet_replays)

        Paper.init(this)

        packetReplayListView.choiceMode = ListView.CHOICE_MODE_SINGLE

        packetReplayAdapter = ArrayAdapter(this, R.layout.simple_list_item_single_choice_white_text, packetReplayList)
        packetReplayListView.adapter = packetReplayAdapter

        packetReplayListView.setOnItemClickListener { parent, view, position, id ->
            selectedPacketReplayIndex = position
        }

        stopReplayButton.setOnClickListener {
            if (packetReplayStatus == PacketReplayStatus.Canceled ||
                packetReplayStatus == PacketReplayStatus.Stopped) {
                showToast(this, "Replay cancellation in progress or never started")
            }
            else {
                packetReplayStatus = PacketReplayStatus.Canceled
            }
        }

        sendReplayButton.setOnClickListener {
            if (packetReplayStatus == PacketReplayStatus.Started) {
                showToast(this, "Replay send already started")
            } else {
                if (!ControlActivity.bluetoothService.isConnected) {
                    showToast(this, "Bluetooth not connected, please connect first")
                } else {
                    if (selectedPacketIndexIsValid()) {
                        packetReplayStatus = PacketReplayStatus.Started
                        setReplayStatusTextViewVisible(true)

                        packetReplayThread = Thread {
                            try {
                                val replayPackets = getReplayBook()
                                    .read<List<String>>(packetReplayList[selectedPacketReplayIndex])!!

                                for (i in replayPackets.indices) {
                                    if (packetReplayStatus == PacketReplayStatus.Canceled) {
                                        resetReplaySendState()
                                        break
                                    }

                                    val packetBytes = replayPackets[i].hexToByteArray()

                                    ControlActivity.bluetoothService.write(packetBytes)

                                    updateReplayStatusTextView(i + 1, replayPackets.size)

                                    // Sync with SENSOR_DELAY_UI delay
                                    Thread.sleep(60)
                                }

                                resetReplaySendState()
                            } catch (interruptedException: InterruptedException) {
                                Log.e(Constants.TAG, interruptedException.message?: "Thread Interrupted in Replay playback")
                            } catch (exception: Exception) {
                                Log.e(Constants.TAG, exception.message ?: "Exception in Replay playback")
                            }
                        }

                        packetReplayThread?.start()
                    } else {
                        showToast(this, "No Replay selected, please select one first")
                    }
                }
            }
        }

        deleteReplayButton.setOnClickListener {
            if (packetReplayList.isEmpty()) showToast(this, "No Replays to delete")
            else {
                if (selectedPacketIndexIsValid()) {
                    showDeleteReplayDialog()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadSavedReplays()
    }

    override fun onDestroy() {
        packetReplayThread?.interrupt()
        super.onDestroy()
    }

    private fun showDeleteReplayDialog() {
        AlertDialog.Builder(this)
            .setPositiveButton("Yes") { _, _ -> deletePacketReplay() }
            .setNegativeButton("Cancel", null)
            .setMessage("Confirm delete?")
            .show()
    }

    private fun deletePacketReplay() {
        val selectedItem = packetReplayList[selectedPacketReplayIndex]
        removeReplay(selectedPacketReplayIndex)
        showToast(this, "Deleted Replay: $selectedItem")
    }

    private fun selectedPacketIndexIsValid(): Boolean {
        return selectedPacketReplayIndex in 0 until packetReplayList.size
    }

    private fun updateReplayStatusTextView(packetNumber: Int, numPackets: Int) {
        val format = "Sending Packet $packetNumber / $numPackets"
        runOnUiThread { replayStatusTextView.text = format }
    }

    private fun resetReplaySendState() {
        packetReplayStatus = PacketReplayStatus.Stopped
        setReplayStatusTextViewVisible(false)
        updateReplayStatusTextView(0, 0)
    }

    private fun setReplayStatusTextViewVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.INVISIBLE
        runOnUiThread { replayStatusTextView.visibility = visibility }
    }

    private fun removeReplay(index: Int) {
        getReplayBook().delete(packetReplayList[index])
        packetReplayList.removeAt(index)
        packetReplayAdapter.notifyDataSetChanged()
    }

    private fun loadSavedReplays() {
        val replays = getReplayBook().allKeys
        packetReplayList.clear()
        packetReplayList.addAll(replays)
        packetReplayAdapter.notifyDataSetChanged()
    }
}