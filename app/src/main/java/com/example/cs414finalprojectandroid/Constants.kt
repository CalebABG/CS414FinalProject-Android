/*
 * Adapted from: https://github.com/googlearchive/android-BluetoothChat
 */
package com.example.cs414finalprojectandroid

/**
 * Defines several constants used between Bluetooth service and the UI.
 */
object Constants {
    const val TAG = "GoBabyGoApp"

    // Blue-Smirf
    // public static final String GoBabyGoBTMAC = "00:06:66:F2:34:F8";
    // HC-06
    // public static final String GoBabyGoBTMAC = "98:D3:B1:FD:32:CE";
    const val GoBabyGoBTMAC = "00:21:06:08:34:20"

    // Message types sent from the GBGBluetoothService Handler
    const val MESSAGE_STATE_CHANGE = 1
    const val MESSAGE_READ = 2
    const val MESSAGE_WRITE = 3
    const val MESSAGE_DEVICE_NAME = 4
    const val MESSAGE_TOAST = 5

    // Key names received from the GBGBluetoothService Handler
    const val DEVICE_NAME = "device_name"
    const val TOAST = "toast"
}