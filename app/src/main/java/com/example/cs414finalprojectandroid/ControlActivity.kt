package com.example.cs414finalprojectandroid

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.cs414finalprojectandroid.Utilities.constrain
import com.example.cs414finalprojectandroid.Utilities.toByteArray
import com.example.cs414finalprojectandroid.Utilities.toHex
import com.example.cs414finalprojectandroid.bluetooth.ArduinoPacket
import com.example.cs414finalprojectandroid.bluetooth.BluetoothService
import com.example.cs414finalprojectandroid.settings.AccelerometerCalibration
import kotlinx.android.synthetic.main.activity_control.*
import java.lang.ref.WeakReference

class ControlActivity : AppCompatActivity(), SensorEventListener {

    companion object {
        var BLUETOOTH_CONNECTED = false
        var BLUETOOTH_CONNECTING = false

        const val UBYTE_MAX = 255.0
        const val GRAV_ACCEL = 9.81

        const val MIN_MOTOR_VAL = -UBYTE_MAX
        const val MAX_MOTOR_VAL = UBYTE_MAX

        // Acceleration Calibration
        var accelCalib = AccelerometerCalibration()
    }

    lateinit var gbgBTService: BluetoothService

    lateinit var sensorManager: SensorManager
    lateinit var accelerometer: Sensor

    /**
     * The Handler that gets information back from the MyBluetoothService
     */
    private lateinit var mHandler: MyHandler

    private var parentAppControlOverride = true

    private val activeShieldColor = "#32A341"
    private val inactiveShieldColor = "#AEB1AE"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_control)

        mHandler = MyHandler(this)

        updateShieldIconColor()
        updateSensorInfoText()

        // Initialize the GBGBluetoothService to perform bluetooth connections
        gbgBTService = BluetoothService(mHandler)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (!BLUETOOTH_CONNECTED)
            connectToArduinoBluetooth()

        parentalOverrideBtn.setOnClickListener {
            toggleParentalOverride()
            updateShieldIconColor()

            if (parentAppControlOverride) {
                Toast.makeText(this, "Activating Parental Control", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Deactivating Parental Control", Toast.LENGTH_SHORT).show()
                if (BLUETOOTH_CONNECTED) {
                    val packet = ArduinoPacket.create(ArduinoPacket.PARENTAL_CONTROL_PACKET_ID)
                    gbgBTService.write(packet)
                }
            }
        }

        emergencyStopBtn.setOnClickListener {
            Toast.makeText(this, "Stopping Motors!", Toast.LENGTH_SHORT).show()
            if (BLUETOOTH_CONNECTED) {
                val packet = ArduinoPacket.create(ArduinoPacket.STOP_MOTORS_PACKET_ID)
                gbgBTService.write(packet)
            }
        }
        emergencyStopBtn.setOnLongClickListener {
            Toast.makeText(
                this,
                "Tooltip: ${emergencyStopBtn.contentDescription}",
                Toast.LENGTH_SHORT
            ).show()
            true
        }

        btReconnectBtn.setOnClickListener {
            when {
                BLUETOOTH_CONNECTED -> Toast.makeText(
                    this,
                    "Already Connected",
                    Toast.LENGTH_SHORT
                ).show()

                BLUETOOTH_CONNECTING -> Toast.makeText(
                    this,
                    "Connecting...",
                    Toast.LENGTH_SHORT
                ).show()

                else -> connectToArduinoBluetooth()
            }
        }

        bt_disconnectBtn.setOnClickListener {
            if (BLUETOOTH_CONNECTED) disconnectFromArduinoBluetooth()
            else Toast.makeText(this, "Not Connected", Toast.LENGTH_SHORT).show()
        }

        accel_Switch.setOnClickListener { updateSensorInfoText() }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL, 1000)

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (gbgBTService.state == BluetoothService.STATE_NONE) {
            // Start the Bluetooth chat services
            gbgBTService.start()
        }
    }

    override fun onDestroy() {
        gbgBTService.stop()
        super.onDestroy()
    }

    override fun onBackPressed() {
        doExit()
    }

    /**
     * Exit the app if user select yes.
     */
    private fun doExit() {
        AlertDialog.Builder(this)
            .setPositiveButton("Yes") { _, _ -> finish() }
            .setNegativeButton("No", null)
            .setMessage("Do You Want to Exit?")
            .setTitle(R.string.app_name)
            .show()
    }

    private fun connectToArduinoBluetooth() {
        connectDevice()
    }

    private fun disconnectFromArduinoBluetooth() {
        gbgBTService.stop()
    }

    private fun connectDevice() {
        val device = MainActivity.bluetoothAdapter!!.getRemoteDevice(Constants.GoBabyGoBTMAC)

        // Only if the state is STATE_NONE, do we know that we haven't started already
        if (gbgBTService.state == BluetoothService.STATE_NONE) {
            // Start the Bluetooth chat services
            gbgBTService.start()
        }

        // Attempt to connect to the device
        gbgBTService.connect(device, true)
    }

    private fun toggleParentalOverride() {
        parentAppControlOverride = !parentAppControlOverride
    }

    private fun updateShieldIconColor() {
        val bgTintColor = getBackgroundTint()
        parentalOverrideBtn.backgroundTintList = ColorStateList.valueOf(bgTintColor)
    }

    private fun getBackgroundTint(): Int {
        return if (parentAppControlOverride) Color.parseColor(activeShieldColor)
        else Color.parseColor(inactiveShieldColor)
    }

    private fun updateSensorInfoText() {
        accel_Switch.text = if (accel_Switch.isChecked) "Cal" else "Raw"
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

        setCalibrationX(accelX.toInt())
        setCalibrationY(accelY.toInt())

        accelMinXTextView.text = "Accelerometer Min X: ${accelCalib.minX}"
        accelMinYTextView.text = "Accelerometer Min Y: ${accelCalib.minY}"
        accelMaxXTextView.text = "Accelerometer Max X: ${accelCalib.maxX}"
        accelMaxYTextView.text = "Accelerometer Max Y: ${accelCalib.maxY}"

        val multiplierX = UBYTE_MAX / GRAV_ACCEL
        val multiplierY = UBYTE_MAX / GRAV_ACCEL

        val calcAccelX = constrain(accelX * multiplierX, MIN_MOTOR_VAL, MAX_MOTOR_VAL).toInt().toShort()
        val calcAccelY = constrain(accelY * multiplierY, MIN_MOTOR_VAL, MAX_MOTOR_VAL).toInt().toShort()

        if (accel_Switch.isChecked) {
            accelXTextView.text = "$calcAccelX"
            accelYTextView.text = "$calcAccelY"
        } else {
            accelXTextView.text = "${"%.3f".format(accelX)} m/s\u00B2"
            accelYTextView.text = "${"%.3f".format(accelY)} m/s\u00B2"
        }

        if (parentAppControlOverride) {
            // Send packets
            if (BLUETOOTH_CONNECTED) {
                val sensorXBytes = calcAccelX.toByteArray()
                val sensorYBytes = calcAccelY.toByteArray()

                val packetData = sensorXBytes + sensorYBytes

                val packet = ArduinoPacket.create(ArduinoPacket.SENSOR_DATA_PACKET_ID, packetData)
                gbgBTService.write(packet)
            }
        }
    }

    // TODO: Make sure using Non-Deprecated constructor behaves as intended
    private class MyHandler(activity: ControlActivity) : Handler(Looper.getMainLooper()) {
        private val mActivity: WeakReference<ControlActivity> = WeakReference(activity)

        @SuppressLint("SetTextI18n")
        override fun handleMessage(msg: Message) {
            val activity = mActivity.get()

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
                            activity.btStatusTextView.text = "Connecting... "
                        }

                        BluetoothService.STATE_NONE,
                        BluetoothService.STATE_LISTEN -> {
                            BLUETOOTH_CONNECTED = false
                            BLUETOOTH_CONNECTING = false
                            activity.btStatusTextView.text = "Disconnected"
                        }
                    }

                    Constants.MESSAGE_WRITE -> {
                        val writeBuff: ByteArray = msg.obj as ByteArray
                        Log.d(Constants.TAG, writeBuff.toHex())
                    }

                    Constants.MESSAGE_READ -> {
                        val readBuff: ByteArray = msg.obj as ByteArray
                        Log.d(Constants.TAG, readBuff.toHex())
                    }

                    Constants.MESSAGE_TOAST -> {
                        Toast.makeText(
                            activity,
                            msg.data.getString(Constants.TOAST),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }
}