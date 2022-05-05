package com.example.cs414finalprojectandroid

import android.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import com.example.cs414finalprojectandroid.Utilities.hexToByteArray
import com.example.cs414finalprojectandroid.Utilities.showToast
import io.paperdb.Paper
import kotlinx.android.synthetic.main.activity_view_packet_replays.*

class ViewPacketReplaysActivity : AppCompatActivity() {
    private var selectedPacketReplayIndex: Int = -1

    private var packetReplayThread: Thread? = null
    private val packetReplayList: MutableList<String> = mutableListOf()

    private var packetReplayStatus = PacketReplayStatus.Stopped
    private lateinit var packetReplayAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_packet_replays)

        Paper.init(this)

        packetReplayListView.choiceMode = ListView.CHOICE_MODE_SINGLE

        packetReplayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, packetReplayList)
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
                                val replayPackets = Paper
                                    .book(ControlActivity.PAPER_COLLECTION_NAME)
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
                                Log.e(Constants.TAG, interruptedException.stackTraceToString())
                            } catch (exception: Exception) {
                                Log.e(Constants.TAG, exception.stackTraceToString())
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
                    val selectedItem = packetReplayList[selectedPacketReplayIndex]
                    removeReplay(selectedPacketReplayIndex)
                    showToast(this, "Deleted Replay: $selectedItem")
                }
            }
        }
    }

    override fun onBackPressed() {
        showExitDialog()
    }

    override fun onDestroy() {
        packetReplayThread?.interrupt()
        super.onDestroy()
    }

    private fun showExitDialog() {
        AlertDialog.Builder(this)
            .setPositiveButton("Yes") { _, _ -> finish() }
            .setNegativeButton("No", null)
            .setMessage("Confirm exit?")
            .show()
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

    override fun onResume() {
        super.onResume()
        loadSavedReplays()
    }

    private fun removeReplay(index: Int) {
        Paper.book(ControlActivity.PAPER_COLLECTION_NAME).delete(packetReplayList[index])
        packetReplayList.removeAt(index)
        packetReplayAdapter.notifyDataSetChanged()
    }

    private fun loadSavedReplays() {
        val replays = Paper.book(ControlActivity.PAPER_COLLECTION_NAME).allKeys
        packetReplayList.clear()
        packetReplayList.addAll(replays)
        packetReplayAdapter.notifyDataSetChanged()
    }
}