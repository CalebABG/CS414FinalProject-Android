package com.example.cs414finalprojectandroid

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
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
import com.example.cs414finalprojectandroid.Utilities.constrain
import com.example.cs414finalprojectandroid.Utilities.showToast
import com.example.cs414finalprojectandroid.Utilities.toByteArray
import com.example.cs414finalprojectandroid.Utilities.toHex
import com.example.cs414finalprojectandroid.bluetooth.ArduinoPacket
import com.example.cs414finalprojectandroid.bluetooth.BluetoothService
import com.example.cs414finalprojectandroid.settings.AccelerometerCalibration
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_control.*
import java.lang.ref.WeakReference

class ControlActivity : AppCompatActivity(), SensorEventListener {

    companion object {
        var PARENTAL_OVERRIDE = true

        var BLUETOOTH_CONNECTED = false
        var BLUETOOTH_CONNECTING = false

        const val UBYTE_MAX = 255.0
        const val GRAV_ACCEL = 9.81
        const val MIN_MOTOR_VAL = -UBYTE_MAX
        const val MAX_MOTOR_VAL = UBYTE_MAX

        var accelCalib = AccelerometerCalibration()

        const val FILE_NAME = "CS414FinalProject"
        const val TODO_LIST_KEY = "calibration"

        const val ACTIVE_SHIELD_COLOR = "#32A341"
        const val INACTIVE_SHIELD_COLOR = "#AEB1AE"

        val gson = Gson()

        fun getAppSharedPreferences(context: Context): SharedPreferences {
            return context.getSharedPreferences(FILE_NAME, MODE_PRIVATE)
        }
    }

    private lateinit var accelerometer: Sensor
    private lateinit var sensorManager: SensorManager
    private lateinit var bluetoothService: BluetoothService
    private lateinit var bluetoothMessageHandler: BluetoothMessageHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_control)

        updateShieldIconColor()
        updateSensorInfoText()

        bluetoothMessageHandler = BluetoothMessageHandler(this)
        bluetoothService = BluetoothService(bluetoothMessageHandler)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (!BLUETOOTH_CONNECTED) connectToBluetooth()

        saveAccelCalibrationButton.setOnClickListener { saveAccelCalibrationToSharedPreferences() }
        loadAccelCalibrationButton.setOnClickListener { loadAccelCalibrationFromSharedPreferences() }
        resetAccelCalibrationButton.setOnClickListener { resetAccelCalibration() }

        parentalOverrideBtn.setOnClickListener {
            toggleParentalOverride()
            updateShieldIconColor()

            if (PARENTAL_OVERRIDE) {
                showToast(this, "Activating Parental Control")
            } else {
                if (BLUETOOTH_CONNECTED) {
                    showToast(this, "Deactivating Parental Control")
                    val packet = ArduinoPacket.create(ArduinoPacket.PARENTAL_CONTROL_PACKET_ID)
                    bluetoothService.write(packet)
                }
            }
        }

        emergencyStopBtn.setOnClickListener {
            if (BLUETOOTH_CONNECTED) {
                showToast(this, "Stopping Motors!")
                val packet = ArduinoPacket.create(ArduinoPacket.STOP_MOTORS_PACKET_ID)
                bluetoothService.write(packet)
            }
        }

        emergencyStopBtn.setOnLongClickListener {
            showToast(this, "Tooltip: ${emergencyStopBtn.contentDescription}")
            true
        }

        btReconnectBtn.setOnClickListener {
            when {
                BLUETOOTH_CONNECTED -> showToast(this, "Already Connected")
                BLUETOOTH_CONNECTING -> showToast(this, "Connecting...")
                else -> connectToBluetooth()
            }
        }

        btDisconnectBtn.setOnClickListener {
            if (BLUETOOTH_CONNECTED) disconnectFromBluetooth()
            else showToast(this, "Not Connected")
        }

        accelSwitch.setOnClickListener { updateSensorInfoText() }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        loadAccelCalibrationFromSharedPreferences()

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL, 1000)
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
            .setTitle(R.string.app_name)
            .show()
    }

    private fun getSavedAccelCalibration(): AccelerometerCalibration {
        val savedCalibrationString = getAppSharedPreferences(this).getString(TODO_LIST_KEY, "") ?: ""
        return if (savedCalibrationString.isNotEmpty()) gson.fromJson(savedCalibrationString, AccelerometerCalibration::class.java)
        else AccelerometerCalibration()
    }

    private fun saveAccelCalibrationToSharedPreferences() {
        val sharedPreferences = getAppSharedPreferences(this)
        val editor = sharedPreferences.edit()

        val todoListJson = gson.toJson(accelCalib)
        editor.putString(TODO_LIST_KEY, todoListJson)

        editor.apply()
    }

    private fun loadAccelCalibrationFromSharedPreferences() {
        accelCalib = getSavedAccelCalibration()
    }

    private fun resetAccelCalibration() {
        accelCalib.reset()
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
        PARENTAL_OVERRIDE = !PARENTAL_OVERRIDE
    }

    private fun updateShieldIconColor() {
        val bgTintColor = getBackgroundTint()
        parentalOverrideBtn.backgroundTintList = ColorStateList.valueOf(bgTintColor)
    }

    private fun getBackgroundTint(): Int {
        return if (PARENTAL_OVERRIDE) Color.parseColor(ACTIVE_SHIELD_COLOR)
        else Color.parseColor(INACTIVE_SHIELD_COLOR)
    }

    private fun updateSensorInfoText() {
        accelSwitch.text = if (accelSwitch.isChecked) "Cal" else "Raw"
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    private fun setCalibrationX(value: Int) {
        if (value < accelCalib.minX) accelCalib.minX = value
        if (value > accelCalib.maxX) accelCalib.maxX = value
    }

    private fun setCalibrationY(value: Int) {
        if (value < accelCalib.minY) accelCalib.minY = value
        if (value > accelCalib.maxY) accelCalib.maxY = value
    }

    @SuppressLint("SetTextI18n")
    override fun onSensorChanged(event: SensorEvent) {
        val accelX: Float = event.values[0]
        val accelY: Float = event.values[1]

        if (!freezeAccelCalibrationSwitch.isChecked) {
            setCalibrationX(accelX.toInt())
            setCalibrationY(accelY.toInt())
        }

        accelMinXTextView.text = "Accel Min X: ${accelCalib.minX}"
        accelMinYTextView.text = "Accel Min Y: ${accelCalib.minY}"
        accelMaxXTextView.text = "Accel Max X: ${accelCalib.maxX}"
        accelMaxYTextView.text = "Accel Max Y: ${accelCalib.maxY}"

        val multiplierX = UBYTE_MAX / GRAV_ACCEL
        val multiplierY = UBYTE_MAX / GRAV_ACCEL

        val calcAccelX =
            constrain(accelX * multiplierX, MIN_MOTOR_VAL, MAX_MOTOR_VAL).toInt().toShort()
        val calcAccelY =
            constrain(accelY * multiplierY, MIN_MOTOR_VAL, MAX_MOTOR_VAL).toInt().toShort()

        if (accelSwitch.isChecked) {
            accelXTextView.text = "$calcAccelX"
            accelYTextView.text = "$calcAccelY"
        } else {
            accelXTextView.text = "${"%.3f".format(accelX)} m/s\u00B2"
            accelYTextView.text = "${"%.3f".format(accelY)} m/s\u00B2"
        }

        if (PARENTAL_OVERRIDE) {
            // Send packets
            if (BLUETOOTH_CONNECTED) {
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
                            BLUETOOTH_CONNECTED = true
                            BLUETOOTH_CONNECTING = false

                            activity.btStatusTextView.text = "Connected"
                        }

                        BluetoothService.STATE_CONNECTING -> {
                            BLUETOOTH_CONNECTING = true
                            activity.btStatusTextView.text = "Connecting..."
                        }

                        BluetoothService.STATE_NONE,
                        BluetoothService.STATE_LISTEN -> {
                            BLUETOOTH_CONNECTED = false
                            BLUETOOTH_CONNECTING = false
                            activity.btStatusTextView.text = "Disconnected"
                        }
                    }

                    Constants.MESSAGE_WRITE -> {
                        val writeBuff = msg.obj as ByteArray
                        Log.d(Constants.TAG, writeBuff.toHex())
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