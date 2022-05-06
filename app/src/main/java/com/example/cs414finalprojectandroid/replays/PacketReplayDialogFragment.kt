package com.example.cs414finalprojectandroid.replays

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.cs414finalprojectandroid.R
import kotlinx.android.synthetic.main.fragment_packet_replay_dialog.view.*
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/*
 * Reference: https://developer.android.com/guide/topics/ui/dialogs
 */
class PacketReplayDialogFragment : DialogFragment() {

    companion object {
        const val ARG_REPLAY_NAME = "replay_name"

        private const val ARG_PARAM1 = "param1"
        private const val ARG_PARAM2 = "param2"

        val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")
            .withZone(ZoneOffset.UTC)

        fun newInstance(param1: String, param2: String): PacketReplayDialogFragment {
            return PacketReplayDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
        }
    }

    private var packetsRecordedStr: String? = ""
    private var maxNumPacketsStr: String? = ""

    private lateinit var listener: PacketReplayDialogListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isCancelable = false

        arguments?.let {
            packetsRecordedStr = it.getString(ARG_PARAM1)
            maxNumPacketsStr = it.getString(ARG_PARAM2)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        try {
            listener = context as PacketReplayDialogListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement Listener interface")
        }
    }

    private fun getReplayName(editText: EditText): String {
        var replayName = editText.text.toString().trim()

        if (replayName.isEmpty())
            replayName = "replay_${dateTimeFormatter.format(Instant.now())}"

        return replayName
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            val inflater = requireActivity().layoutInflater

            val view = inflater.inflate(R.layout.fragment_packet_replay_dialog, null)

            view.textView6.text = "Packets Recorded: $packetsRecordedStr / $maxNumPacketsStr"

            view.saveButton.setOnClickListener {
                requireArguments().putString(
                    ARG_REPLAY_NAME, getReplayName(requireDialog().findViewById(
                        R.id.replayNameEditText
                    )))
                listener.onDialogPositiveClick(this)
                dismiss()
            }

            view.cancelButton.setOnClickListener {
                listener.onDialogNegativeClick(this)
                dismiss()
            }

            val dialog = builder
                .setView(view)
                .create()

            dialog.setCancelable(false)
            dialog.setCanceledOnTouchOutside(false)

            return dialog
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}