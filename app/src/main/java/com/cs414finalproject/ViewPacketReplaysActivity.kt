package com.cs414finalproject

import android.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import com.cs414finalproject.Utilities.getReplayBook
import com.cs414finalproject.Utilities.hexToByteArray
import com.cs414finalproject.Utilities.showToast
import com.cs414finalproject.databinding.ActivityViewPacketReplaysBinding
import com.cs414finalproject.replays.PacketReplayStatus
import io.paperdb.Paper
import kotlinx.coroutines.*

class ViewPacketReplaysActivity : AppCompatActivity() {
    private var selectedPacketReplayIndex: Int = -1

    private var packetReplayStatus = PacketReplayStatus.Stopped
    private val packetReplayList = ArrayList<String>(100)

    private lateinit var packetReplayAdapter: ArrayAdapter<String>

    private val coroutineScope: CoroutineScope = GlobalScope
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default

    private lateinit var binding: ActivityViewPacketReplaysBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityViewPacketReplaysBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Paper.init(this)

        binding.packetReplayListView.choiceMode = ListView.CHOICE_MODE_SINGLE

        packetReplayAdapter = ArrayAdapter(this, R.layout.simple_list_item_single_choice_white_text, packetReplayList)
        binding.packetReplayListView.adapter = packetReplayAdapter

        binding.packetReplayListView.setOnItemClickListener { _, _, position, _ ->
            selectedPacketReplayIndex = position
        }

        binding.stopReplayButton.setOnClickListener {
            if (packetReplayStatus == PacketReplayStatus.Canceled ||
                packetReplayStatus == PacketReplayStatus.Stopped
            ) {
                showToast(this, "Replay cancellation in progress or never started")
            } else {
                packetReplayStatus = PacketReplayStatus.Canceled
            }
        }

        binding.sendReplayButton.setOnClickListener {
            if (packetReplayStatus == PacketReplayStatus.Started) {
                showToast(this, "Replay send already started")
            } else {
                if (!ControlActivity.bluetoothService.isConnected) {
                    showToast(this, "Bluetooth not connected, please connect first")
                } else {
                    if (selectedPacketIndexIsValid()) {
                        sendPacketReplay()
                    } else {
                        showToast(this, "No Replay selected, please select one first")
                    }
                }
            }
        }

        binding.deleteReplayButton.setOnClickListener {
            if (packetReplayStatus == PacketReplayStatus.Started) {
                showToast(this, "Replay not stopped, please stop first")
            } else {
                if (packetReplayList.isEmpty()) {
                    showToast(this, "No Replays to delete")
                } else {
                    if (selectedPacketIndexIsValid()) {
                        showDeleteReplayDialog()
                    }
                }
            }
        }
    }

    private fun sendPacketReplay() {
        setReplaySendState(true)

        coroutineScope.launch {
            handleSendPacketReplayCoroutine()
        }
    }

    private suspend fun handleSendPacketReplayCoroutine() {
        withContext(defaultDispatcher) {
            try {
                val replayPackets: List<String> =
                    getReplayBook().read(packetReplayList[selectedPacketReplayIndex])!!

                for (index in replayPackets.indices) {
                    ensureActive()

                    if (packetReplayStatus == PacketReplayStatus.Canceled)
                        break

                    val packetBytes = replayPackets[index].hexToByteArray()
                    ControlActivity.bluetoothService.write(packetBytes)

                    updateReplayStatusText(index + 1, replayPackets.size)

                    // Sync with SENSOR_DELAY_UI delay
                    delay(60)
                }
            } catch (error: Throwable) {
                Log.e(Constants.TAG, error.message ?: "Error in Replay playback")
            } finally {
                setReplaySendState(false)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadSavedReplays()
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

    private fun updateReplayStatusText(packetNumber: Int, numPackets: Int) {
        val format = "Sending Packet $packetNumber / $numPackets"
        runOnUiThread { binding.replayStatusTextView.text = format }
    }

    private fun setReplaySendState(sending: Boolean) {
        packetReplayStatus = if (sending) PacketReplayStatus.Started else PacketReplayStatus.Stopped
        if (!sending) { runOnUiThread { binding.replayStatusTextView.text = getString(R.string.view_packet_replays_activity_sending_packet) } }
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