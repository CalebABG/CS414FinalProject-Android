package com.example.cs414finalprojectandroid.replays

import androidx.fragment.app.DialogFragment

interface PacketReplayDialogListener {
    fun onDialogPositiveClick(dialog: DialogFragment)
    fun onDialogNegativeClick(dialog: DialogFragment)
}