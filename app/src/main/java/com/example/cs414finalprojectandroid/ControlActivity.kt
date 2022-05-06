package com.example.cs414finalprojectandroid

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import com.example.cs414finalprojectandroid.Utilities.constrain
import com.example.cs414finalprojectandroid.Utilities.getReplayBook
import com.example.cs414finalprojectandroid.Utilities.isFull
import com.example.cs414finalprojectandroid.Utilities.showToast
import com.example.cs414finalprojectandroid.Utilities.toByte
import com.example.cs414finalprojectandroid.Utilities.toByteArray
import com.example.cs414finalprojectandroid.Utilities.toHex
import com.example.cs414finalprojectandroid.bluetooth.ArduinoPacket
import com.example.cs414finalprojectandroid.bluetooth.BluetoothService
import com.example.cs414finalprojectandroid.replays.PacketReplayDialogFragment
import com.example.cs414finalprojectandroid.replays.PacketReplayDialogListener
import com.example.cs414finalprojectandroid.replays.PacketReplayStatus
import com.example.cs414finalprojectandroid.settings.AppSettings
import com.google.gson.Gson
import io.paperdb.Paper
import kotlinx.android.synthetic.main.activity_control.*
import java.lang.ref.WeakReference
import java.util.*

class ControlActivity : AppCompatActivity(), SensorEventListener, PacketReplayDialogListener {
    companion object {
        const val UBYTE_MAX = 255.0
        const val GRAV_ACCEL = 9.81

        const val MIN_MOTOR_VAL = -UBYTE_MAX
        const val MAX_MOTOR_VAL = UBYTE_MAX

        const val SHARED_PREF_FILE_NAME = "CS414FinalProject"
        const val SHARED_PREF_APP_SETTINGS_KEY = "appsettings"

        const val REPLAY_LIST_SIZE = 350 // ~30-40 seconds of recording time based on Arduino packet processing
        const val REPLAY_COLLECTION_NAME = "PacketReplays"

        val gson = Gson()
        var appSettings = AppSettings()

        var shownPacketReplayDoneToast = false
        var packetReplayStatus = PacketReplayStatus.None
        var packetReplayList: MutableList<String> = ArrayList(REPLAY_LIST_SIZE)

        lateinit var bluetoothService: BluetoothService

        fun getAppSharedPreferences(context: Context): SharedPreferences {
            return context.getSharedPreferences(SHARED_PREF_FILE_NAME, MODE_PRIVATE)
        }
    }

    private lateinit var accelerometer: Sensor
    private lateinit var sensorManager: SensorManager
    private lateinit var bluetoothMessageHandler: BluetoothMessageHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_control)

        updateShieldIconColor()
        updateSensorInfoText()
        updatePacketsRecordedText()

        bluetoothMessageHandler = BluetoothMessageHandler(this)
        bluetoothService = BluetoothService(bluetoothMessageHandler)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // if (!bluetoothService.isConnected) connectToBluetooth()

        setDriveParametersButton.setOnClickListener { sendDriveParameters() }
        accelSwitch.setOnClickListener { updateSensorInfoText() }

        // TODO: Move inside logic to helper methods
        startPacketReplayButton.setOnClickListener {
            when {
                packetReplayCaptureDone() -> {
                    showToast(this, "Recording limit reached or manually stopped, please save or discard")
                }
                packetReplayStatus == PacketReplayStatus.Started -> {
                    showToast(this, "Recording already started")
                }
                else -> {
                    packetReplayStatus = PacketReplayStatus.Started
                    showToast(this, "Recording started")
                }
            }
        }

        stopPacketReplayButton.setOnClickListener {
            when {
                packetReplayCaptureDone() -> {
                    showToast(this, "Recording already stopped, please save or discard")
                }
                packetReplayStatus == PacketReplayStatus.None -> {
                    showToast(this, "Recording not started yet, please start first")
                }
                else -> {
                    packetReplayStatus = PacketReplayStatus.Canceled
                    showToast(this, "Stopping recording")
                }
            }
        }

        savePacketReplayButton.setOnClickListener {
            if (packetReplayStatus == PacketReplayStatus.None) {
                showToast(this, "Recording not started, please start first")
            }
            else if (!packetReplayCaptureDone()) {
                showToast(this, "Recording not stopped, please stop first")
            } else {
                if (packetReplayList.isEmpty()) {
                    showToast(this, "No packets recorded yet, resetting state")
                    resetPacketReplayState()
                }
                else {
                    showSavePacketReplayDialog()
                }
            }
        }

        viewPacketReplaysButton.setOnClickListener {
            val replays = getReplayBook().allKeys

            if (replays.isNullOrEmpty()) {
                showToast(this, "No Replays saved yet, save some first")
            }
            else {
                Intent(this, ViewPacketReplaysActivity::class.java).also {
                    startActivity(it)
                }
            }
        }

        parentalOverrideBtn.setOnClickListener {
            toggleParentalOverride()

            if (bluetoothService.isConnected) {
                if (appSettings.parentalOverride) {
                    showToast(this, "Activating Parental Control")
                } else {
                    showToast(this, "Deactivating Parental Control")
                }

                val packet = ArduinoPacket.create(ArduinoPacket.PARENTAL_CONTROL_PACKET_ID, byteArrayOf(appSettings.parentalOverride.toByte()))
                bluetoothService.write(packet)
            }
            else showToast(this, "Not Connected")
        }

        emergencyStopBtn.setOnClickListener {
            if (bluetoothService.isConnected) {
                showToast(this, "Stopping Motors")
                val packet = ArduinoPacket.create(ArduinoPacket.STOP_MOTORS_PACKET_ID)
                bluetoothService.write(packet)
            } else showToast(this, "Not Connected")
        }

        btReconnectBtn.setOnClickListener {
            when {
                bluetoothService.isConnected -> showToast(this, "Already Connected")
                bluetoothService.isConnecting -> showToast(this, "Connecting...")
                else -> connectToBluetooth()
            }
        }

        btDisconnectBtn.setOnClickListener {
            if (bluetoothService.isConnected) disconnectFromBluetooth()
            else showToast(this, "Not Connected")
        }

        // Initialize Paper NoSQL database
        Paper.init(this)
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        saveAppSettingsToSharedPreferences()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        loadAppSettingsFromSharedPreferences()
        updateDriveParametersFromAppSettings()

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI, 1000)
        bluetoothService.start()
    }

    override fun onDestroy() {
        bluetoothService.stop()
        super.onDestroy()
    }

    override fun onBackPressed() {
        showExitDialog()
    }

    private fun showExitDialog() {
        AlertDialog.Builder(this)
            .setPositiveButton("Yes") { _, _ -> finish() }
            .setNegativeButton("No", null)
            .setMessage("Confirm exit?")
            .show()
    }

    private fun getSavedAppSettings(): AppSettings {
        val appSettingsString = getAppSharedPreferences(this).getString(SHARED_PREF_APP_SETTINGS_KEY, "") ?: ""
        return if (appSettingsString.isNotEmpty()) gson.fromJson(appSettingsString, AppSettings::class.java)
        else AppSettings()
    }

    private fun saveAppSettingsToSharedPreferences() {
        val sharedPreferences = getAppSharedPreferences(this)
        val editor = sharedPreferences.edit()

        val driveSpeedScaleFloat = driveSpeedScaleEditText.text.toString().toFloatOrNull()
        val turnSpeedScaleFloat = turningSpeedScaleEditText.text.toString().toFloatOrNull()

        if (driveSpeedScaleFloat != null) appSettings.driveSpeedScale = driveSpeedScaleFloat
        if (turnSpeedScaleFloat != null) appSettings.turnSpeedScale = turnSpeedScaleFloat

        val appSettingsJson = gson.toJson(appSettings)
        editor.putString(SHARED_PREF_APP_SETTINGS_KEY, appSettingsJson)

        editor.apply()
    }

    private fun loadAppSettingsFromSharedPreferences() {
        appSettings = getSavedAppSettings()
    }

    private fun connectToBluetooth() {
        connectDevice()
    }

    private fun disconnectFromBluetooth() {
        bluetoothService.stop()
    }

    private fun connectDevice() {
        val device = MainActivity
            .getSystemBluetoothAdapter(this)!!
            .getRemoteDevice(Constants.BLUETOOTH_MAC)

        bluetoothService.start()
        bluetoothService.connect(device, true)
    }

    private fun toggleParentalOverride() {
        appSettings.toggleParentalOverride()
        updateShieldIconColor()
    }

    private fun updateShieldIconColor() {
        val bgTintColor = getBackgroundTint()
        parentalOverrideBtn.backgroundTintList = ColorStateList.valueOf(bgTintColor)
    }

    private fun getBackgroundTint(): Int {
        return if (appSettings.parentalOverride) Color.parseColor(Constants.ACTIVE_SHIELD_COLOR)
        else Color.parseColor(Constants.INACTIVE_SHIELD_COLOR)
    }

    private fun updateSensorInfoText() {
        accelSwitch.text = if (accelSwitch.isChecked) "Calc" else "Raw"
    }

    private fun updateDriveParametersFromAppSettings() {
        driveSpeedScaleEditText.setText(appSettings.driveSpeedScale.toString())
        turningSpeedScaleEditText.setText(appSettings.turnSpeedScale.toString())
    }

    private fun sendDriveParameters() {
        if (!bluetoothService.isConnected) {
            showToast(this, "Bluetooth not connected, please connect first")
        } else {
            val driveSpeedScaleText = driveSpeedScaleEditText.text.toString()
            val turningSpeedScaleText = turningSpeedScaleEditText.text.toString()

            if (driveSpeedScaleText.isEmpty() || turningSpeedScaleText.isEmpty()) {
                showToast(this, "Please make sure both parameters are not Empty")
            } else {
                val driveSpeedScaleFloat = driveSpeedScaleText.toFloatOrNull()
                val turningSpeedScaleFloat = turningSpeedScaleText.toFloatOrNull()

                if (driveSpeedScaleFloat == null || turningSpeedScaleFloat == null)
                    showToast(this, "Please make sure both parameters are valid Floats")
                else {
                    val driveSpeedScaleBytes = driveSpeedScaleFloat.toByteArray()
                    val turningSpeedScaleBytes = turningSpeedScaleFloat.toByteArray()

                    val packetData = driveSpeedScaleBytes + turningSpeedScaleBytes

                    val packet = ArduinoPacket.create(ArduinoPacket.DRIVE_PARAMETERS_ID, packetData)
                    bluetoothService.write(packet)
                    showToast(this, "Set Drive Parameters")
                }
            }
        }
    }

    private fun packetReplayCaptureDone() : Boolean {
        return packetReplayStatus == PacketReplayStatus.Stopped ||
                packetReplayStatus == PacketReplayStatus.Canceled ||
                packetReplayList.isFull(REPLAY_LIST_SIZE)
    }

    private fun updatePacketsRecordedText() {
        currentRecordedPacketCount.text = "Packets Recorded: ${packetReplayList.size} / $REPLAY_LIST_SIZE"
    }

    private fun resetPacketReplayState() {
        shownPacketReplayDoneToast = false
        packetReplayStatus = PacketReplayStatus.None
        packetReplayList.clear()
        updatePacketsRecordedText()
    }

    private fun showSavePacketReplayDialog() {
        PacketReplayDialogFragment
            .newInstance(packetReplayList.size.toString(), REPLAY_LIST_SIZE.toString())
            .show(supportFragmentManager, "PacketReplayDialogFragment")
    }

    override fun onDialogPositiveClick(dialog: DialogFragment) {
        var replayName = dialog.requireArguments().getString(PacketReplayDialogFragment.ARG_REPLAY_NAME)!!

        if (getReplayBook().contains(replayName)) {
            replayName += "_${UUID.randomUUID().toString().substring(0, 7)}"
            showToast(this, "Replay already exists, adding random UUID to Replay name")
        }

        getReplayBook().write(replayName, packetReplayList)
        resetPacketReplayState()
        showToast(this, "Saved Replay: $replayName")
    }

    override fun onDialogNegativeClick(dialog: DialogFragment) {
        resetPacketReplayState()
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent) {
        val accelX: Float = event.values[0]
        val accelY: Float = event.values[1]

        val multiplierX = UBYTE_MAX / GRAV_ACCEL
        val multiplierY = UBYTE_MAX / GRAV_ACCEL

        val calcAccelX = constrain(accelX * multiplierX, MIN_MOTOR_VAL, MAX_MOTOR_VAL).toInt().toShort()
        val calcAccelY = constrain(accelY * multiplierY, MIN_MOTOR_VAL, MAX_MOTOR_VAL).toInt().toShort()

        if (accelSwitch.isChecked) {
            accelXTextView.text = "$calcAccelX"
            accelYTextView.text = "$calcAccelY"
        } else {
            accelXTextView.text = "%.3fm/s²".format(accelX)
            accelYTextView.text = "%.3fm/s²".format(accelY)
        }

        if (appSettings.parentalOverride) {
            if (bluetoothService.isConnected) {
                val sensorXBytes = calcAccelX.toByteArray()
                val sensorYBytes = calcAccelY.toByteArray()

                val packetData = sensorXBytes + sensorYBytes

                val packet = ArduinoPacket.create(ArduinoPacket.SENSOR_DATA_PACKET_ID, packetData)
                bluetoothService.write(packet)
            }
        }
    }

    private class BluetoothMessageHandler(activity: ControlActivity) :
        Handler(Looper.getMainLooper()) {
        private val activityReference: WeakReference<ControlActivity> = WeakReference(activity)

        @SuppressLint("SetTextI18n")
        override fun handleMessage(msg: Message) {
            val activity = activityReference.get()

            if (activity != null) {
                when (msg.what) {
                    Constants.MESSAGE_STATE_CHANGE -> when (msg.arg1) {
                        BluetoothService.STATE_CONNECTED -> {
                            activity.btStatusTextView.text = "Connected"
                        }

                        BluetoothService.STATE_CONNECTING -> {
                            activity.btStatusTextView.text = "Connecting..."
                        }

                        BluetoothService.STATE_NONE,
                        BluetoothService.STATE_LISTEN -> {
                            activity.btStatusTextView.text = "Disconnected"
                        }
                    }

                    Constants.MESSAGE_WRITE -> {
                        val writeBuff = msg.obj as ByteArray
                        val packetHex = writeBuff.toHex()

                        Log.d(Constants.TAG, packetHex)

                        if (activity.packetReplayCaptureDone()) {
                            packetReplayStatus = PacketReplayStatus.Stopped

                            if (!shownPacketReplayDoneToast) {
                                shownPacketReplayDoneToast = true
                                showToast(activity, "Recording stopped")
                            }
                        } else if(packetReplayStatus == PacketReplayStatus.Started) {
                            packetReplayList.add(packetHex)
                            activity.updatePacketsRecordedText()
                        }
                    }

                    Constants.MESSAGE_READ -> {
                        val readBuff = msg.obj as ByteArray
                        Log.d(Constants.TAG, readBuff.toHex())
                    }

                    Constants.MESSAGE_TOAST -> {
                        val toastString = msg.data.getString(Constants.TOAST)
                        if (!toastString.isNullOrEmpty()) showToast(activity, toastString)
                    }
                }
            }
        }
    }
}