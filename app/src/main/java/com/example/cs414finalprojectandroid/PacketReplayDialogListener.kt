package com.example.cs414finalprojectandroid

import androidx.fragment.app.DialogFragment

interface PacketReplayDialogListener {
    fun onDialogPositiveClick(dialog: DialogFragment)
    fun onDialogNegativeClick(dialog: DialogFragment)
}