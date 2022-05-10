package com.cs414finalproject

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.cs414finalproject.Utilities.getSystemBluetoothAdapter
import com.cs414finalproject.Utilities.showToast
import com.cs414finalproject.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    companion object {
        const val PERMISSIONS_REQUEST_CODE = 0xE1
    }

    private var neededPermissions = gatherNeededPermissions()
    private val permissionsMap = mutableMapOf<String, Int>()

    private var bluetoothAdapter: BluetoothAdapter? = null

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bluetoothAdapter = getSystemBluetoothAdapter(this)

        binding.continueBtn.setOnClickListener { startCarControlActivity() }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty()) {
                for (i in permissions.indices) {
                    permissionsMap[permissions[i]] = grantResults[i]
                }

                if (neededPermissionsGranted()) {
                    gotoCarControl()
                } else {
                    if (canShowRequestPermissionsRationale()) {
                        showRequestPermissionsDialog()
                    } else {
                        showToast(this, "Please go to Settings and enable permissions")
                    }
                }
            }
        }
    }

    private fun startCarControlActivity() {
        if (bluetoothAdapter == null) {
            showToast(this, "Bluetooth is not available")
        } else {
            if (hasPermissions(this, neededPermissions)) {
                gotoCarControl()
            } else {
                requestNeededAppPermissions()
            }
        }
    }

    private fun gotoCarControl() {
        Intent(this, ControlActivity::class.java).also {
            startActivity(it)
        }
    }

    private fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
        return permissions.all {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun gatherNeededPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        }

        return permissions.toTypedArray()
    }

    private fun requestNeededAppPermissions() {
        ActivityCompat.requestPermissions(this, neededPermissions, PERMISSIONS_REQUEST_CODE)
    }

    private fun canShowRequestPermissionsRationale(): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH) ||
                ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun neededPermissionsGranted(): Boolean {
        return permissionsMap[Manifest.permission.BLUETOOTH] == PackageManager.PERMISSION_GRANTED &&
                permissionsMap[Manifest.permission.ACCESS_FINE_LOCATION] == PackageManager.PERMISSION_GRANTED
    }

    private fun showRequestPermissionsDialog() {
        AlertDialog.Builder(this)
            .setMessage("Bluetooth and Location permission are required for this app, allow?")
            .setPositiveButton("Yes") { _, _ -> requestNeededAppPermissions() }
            .setNegativeButton("No", null)
            .show()
    }
}