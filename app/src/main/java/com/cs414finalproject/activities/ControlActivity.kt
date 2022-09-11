package com.cs414finalproject.activities

import android.app.Activity
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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.cs414finalproject.Constants
import com.cs414finalproject.Utilities.constrain
import com.cs414finalproject.Utilities.getReplayBook
import com.cs414finalproject.Utilities.getSystemBluetoothAdapter
import com.cs414finalproject.Utilities.isFull
import com.cs414finalproject.Utilities.showToast
import com.cs414finalproject.Utilities.toByte
import com.cs414finalproject.Utilities.toByteArray
import com.cs414finalproject.Utilities.toHex
import com.cs414finalproject.bluetooth.ArduinoPacket
import com.cs414finalproject.bluetooth.BluetoothService
import com.cs414finalproject.databinding.ActivityControlBinding
import com.cs414finalproject.replays.PacketReplayStatus
import com.cs414finalproject.settings.AppSettings
import com.google.gson.Gson
import io.paperdb.Paper
import java.lang.ref.WeakReference

class ControlActivity : AppCompatActivity(), SensorEventListener {
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
        var packetReplayList = ArrayList<String>(REPLAY_LIST_SIZE)

        lateinit var bluetoothService: BluetoothService

        fun getAppSharedPreferences(context: Context): SharedPreferences {
            return context.getSharedPreferences(SHARED_PREF_FILE_NAME, MODE_PRIVATE)
        }
    }

    private lateinit var accelerometer: Sensor
    private lateinit var sensorManager: SensorManager
    private lateinit var bluetoothMessageHandler: BluetoothMessageHandler
    private lateinit var packetReplayResultLauncher: ActivityResultLauncher<Intent>

    private lateinit var binding: ActivityControlBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityControlBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateShieldIconColor()
        updateSensorInfoText()
        updatePacketsRecordedText()

        bluetoothMessageHandler = BluetoothMessageHandler(this)
        bluetoothService = BluetoothService(bluetoothMessageHandler)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // if (!bluetoothService.isConnected) connectToBluetooth()

        // Initialize Paper NoSQL database
        Paper.init(this)

        binding.setDriveParametersButton.setOnClickListener { sendDriveParameters() }
        binding.accelSwitch.setOnClickListener { updateSensorInfoText() }

        binding.startPacketReplayButton.setOnClickListener {
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

        binding.stopPacketReplayButton.setOnClickListener {
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

        // Don't really like this, but it's not deprecated
        packetReplayResultLauncher = getPacketReplayResultLauncher()
        binding.savePacketReplayButton.setOnClickListener {
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
                    val saveReplayIntent = Intent(this, SavePacketReplayActivity::class.java)
                    packetReplayResultLauncher.launch(saveReplayIntent)
                }
            }
        }

        binding.viewPacketReplaysButton.setOnClickListener {
            val replays = getReplayBook().allKeys

            if (replays.isEmpty()) {
                showToast(this, "No Replays saved yet, save some first")
            }
            else {
                Intent(this, ViewPacketReplaysActivity::class.java).also {
                    startActivity(it)
                }
            }
        }

        binding.parentalOverrideBtn.setOnClickListener {
            toggleParentalOverride()

            if (bluetoothService.isConnected) {
                if (appSettings.parentalOverride) {
                    showToast(this, "Activating Parental Control")
                } else {
                    showToast(this, "Deactivating Parental Control")
                }

                val packet = ArduinoPacket.create(ArduinoPacket.PARENTAL_CONTROL_PACKET_ID,
                                                  byteArrayOf(appSettings.parentalOverride.toByte()))
                bluetoothService.write(packet)
            }
            else showToast(this, "Not Connected")
        }

        binding.emergencyStopBtn.setOnClickListener {
            if (bluetoothService.isConnected) {
                showToast(this, "Stopping Motors")
                val packet = ArduinoPacket.create(ArduinoPacket.STOP_MOTORS_PACKET_ID)
                bluetoothService.write(packet)
            } else showToast(this, "Not Connected")
        }

        binding.btReconnectBtn.setOnClickListener {
            when {
                bluetoothService.isConnected -> showToast(this, "Already Connected")
                bluetoothService.isConnecting -> showToast(this, "Connecting...")
                else -> connectToBluetooth()
            }
        }

        binding.btDisconnectBtn.setOnClickListener {
            if (bluetoothService.isConnected) disconnectFromBluetooth()
            else showToast(this, "Not Connected")
        }
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

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent) {
        val accelX: Float = event.values[0]
        val accelY: Float = event.values[1]

        val multiplierX = UBYTE_MAX / GRAV_ACCEL
        val multiplierY = UBYTE_MAX / GRAV_ACCEL

        val calcAccelX = constrain(accelX * multiplierX, MIN_MOTOR_VAL, MAX_MOTOR_VAL).toInt().toShort()
        val calcAccelY = constrain(accelY * multiplierY, MIN_MOTOR_VAL, MAX_MOTOR_VAL).toInt().toShort()

        if (binding.accelSwitch.isChecked) {
            binding.accelXTextView.text = "$calcAccelX"
            binding.accelYTextView.text = "$calcAccelY"
        } else {
            binding.accelXTextView.text = "%.3f m/s²".format(accelX)
            binding.accelYTextView.text = "%.3f m/s²".format(accelY)
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

        val driveSpeedScaleFloat = binding.driveSpeedScaleEditText.text.toString().toFloatOrNull()
        val turnSpeedScaleFloat = binding.turningSpeedScaleEditText.text.toString().toFloatOrNull()

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
        val device = getSystemBluetoothAdapter(this)!!
        if (!device.isEnabled) {
            showToast(this, "Please enable Bluetooth first")
            return
        }

        bluetoothService.start()
        bluetoothService.connect(device.getRemoteDevice(Constants.BLUETOOTH_MAC), true)
    }

    private fun toggleParentalOverride() {
        appSettings.toggleParentalOverride()
        updateShieldIconColor()
    }

    private fun updateShieldIconColor() {
        val bgTintColor = getBackgroundTint()
        binding.parentalOverrideBtn.backgroundTintList = ColorStateList.valueOf(bgTintColor)
    }

    private fun getBackgroundTint(): Int {
        return if (appSettings.parentalOverride) Color.parseColor(Constants.ACTIVE_SHIELD_COLOR)
        else Color.parseColor(Constants.INACTIVE_SHIELD_COLOR)
    }

    private fun updateSensorInfoText() {
        val accelSwitch = binding.accelSwitch
        accelSwitch.text = if (accelSwitch.isChecked) "Calc" else "Raw"
    }

    private fun updateDriveParametersFromAppSettings() {
        binding.driveSpeedScaleEditText.setText(appSettings.driveSpeedScale.toString())
        binding.turningSpeedScaleEditText.setText(appSettings.turnSpeedScale.toString())
    }

    private fun sendDriveParameters() {
        if (!bluetoothService.isConnected) {
            showToast(this, "Bluetooth not connected, please connect first")
        } else {
            val driveSpeedScaleText = binding.driveSpeedScaleEditText.text.toString()
            val turningSpeedScaleText = binding.turningSpeedScaleEditText.text.toString()

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
        binding.currentRecordedPacketCount.text = "Packets Recorded: ${packetReplayList.size} / $REPLAY_LIST_SIZE"
    }

    private fun resetPacketReplayState() {
        shownPacketReplayDoneToast = false
        packetReplayStatus = PacketReplayStatus.None
        packetReplayList.clear()
        updatePacketsRecordedText()
    }

    private fun getPacketReplayResultLauncher(): ActivityResultLauncher<Intent> {
        return registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data

                val replayName = data?.getStringExtra(SavePacketReplayActivity.REPLAY_RESULT_INTENT_EXTRA_KEY)

                if (!replayName.isNullOrEmpty()) {
                    getReplayBook().write(replayName, packetReplayList)
                    showToast(this, "Saved Replay: $replayName")
                }
                else showToast(this, "Replay Discarded")

                resetPacketReplayState()
            }
        }
    }

    private class BluetoothMessageHandler(activity: ControlActivity) : Handler(Looper.getMainLooper()) {
        private val activityReference: WeakReference<ControlActivity> = WeakReference(activity)

        override fun handleMessage(msg: Message) {
            val activity = activityReference.get()

            if (activity != null) {
                when (msg.what) {
                    Constants.MESSAGE_STATE_CHANGE -> when (msg.arg1) {
                        BluetoothService.STATE_CONNECTED -> {
                            activity.binding.btStatusTextView.text = "Connected"
                        }

                        BluetoothService.STATE_CONNECTING -> {
                            activity.binding.btStatusTextView.text = "Connecting..."
                        }

                        BluetoothService.STATE_NONE,
                        BluetoothService.STATE_LISTEN -> {
                            activity.binding.btStatusTextView.text = "Disconnected"
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