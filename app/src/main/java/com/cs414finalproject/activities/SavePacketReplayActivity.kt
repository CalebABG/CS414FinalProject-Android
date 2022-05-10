package com.cs414finalproject.activities

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.cs414finalproject.Utilities.dateTimeFormatter
import com.cs414finalproject.Utilities.getReplayBook
import com.cs414finalproject.Utilities.showToast
import com.cs414finalproject.databinding.ActivitySavePacketReplayBinding
import io.paperdb.Paper
import java.time.Instant
import java.util.*

class SavePacketReplayActivity : AppCompatActivity() {
    companion object {
        const val REPLAY_RESULT_INTENT_EXTRA_KEY = "replayName"
    }

    private lateinit var binding: ActivitySavePacketReplayBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySavePacketReplayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Paper.init(this)

        binding.textView6.text = "Packets Recorded: ${ControlActivity.packetReplayList.size} / ${ControlActivity.REPLAY_LIST_SIZE}"

        binding.saveButton.setOnClickListener {
            setPacketReplaySaveResult(getReplayName())
        }

        binding.cancelButton.setOnClickListener {
            setPacketReplaySaveResult(null)
        }
    }

    // Prevent back button, only leave through buttons
    override fun onBackPressed() {}

    private fun setPacketReplaySaveResult(replayName: String?) {
        val resultIntent = Intent()
        if (!replayName.isNullOrEmpty()) resultIntent.putExtra(REPLAY_RESULT_INTENT_EXTRA_KEY, replayName)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun getReplayName(): String {
        var replayName = binding.replayNameEditText.text.toString().trim()

        if (replayName.isEmpty()) {
            replayName = "replay_${dateTimeFormatter.format(Instant.now())}"
        }
        else {
            if (getReplayBook().contains(replayName)) {
                replayName += "_${UUID.randomUUID().toString().substring(0, 7)}"
                showToast(this, "Replay already exists, adding random UUID to Replay name")
            }
        }

        return replayName
    }
}