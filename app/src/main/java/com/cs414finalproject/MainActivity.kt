package com.cs414finalproject

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.cs414finalproject.Utilities.showToast
import com.cs414finalproject.databinding.ActivityMainBinding
import com.cs414finalproject.retrofit.LoremPicsumService
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit

/*
Project Points (8):

DONE 1 - Use of SharedPreferences for persisting Min/Max sensor config
DONE 1 - Use of Android service that requires user-granted permissions (Bluetooth + Location)
DONE 1 - Use of three or more Activities
1 - Use of Notifications (local notification - app put in background, de-register accelerometer listener) / safety if bluetooth connected
2 - Use of Broadcast Receiver Services
DONE 2 - Use of SQLite database (store sent packets, can use to replay motion)
DONE 2 - Use of at least one device sensor (Accelerometer)
DONE 2 - Use of a REST-ful HTTP API [Retrofit] (Use for Start screen photo) - look at `Lorem Picsum`: https://picsum.photos/
*/

class MainActivity : AppCompatActivity() {
    companion object {
        const val PERMISSIONS_REQUEST_CODE = 0xE1

        fun getSystemBluetoothAdapter(activity: Activity): BluetoothAdapter? {
            return (activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        }
    }

    private var neededPermissions = gatherNeededPermissions()
    private val permissionsMap = mutableMapOf<String, Int>()

    private var bitmap: Bitmap? = null

    private var bluetoothAdapter: BluetoothAdapter? = null

    private var retrofit = Retrofit.Builder()
        .baseUrl(Constants.LOREM_PICSUM_URL)
        .build()

    private var loremPicsService = retrofit.create(LoremPicsumService::class.java)

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bluetoothAdapter = getSystemBluetoothAdapter(this)

        binding.continueBtn.setOnClickListener { startCarControlActivity() }
    }

    override fun onResume() {
        super.onResume()
        setScreenLoremPicsImage()
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

    private fun setScreenLoremPicsImage() {
        val picSize = 350
        loremPicsService
            .fetchMainScreenImage(picSize.toString(), picSize.toString())
            .enqueue(object : Callback<ResponseBody> {
                override fun onResponse(
                    call: Call<ResponseBody>,
                    response: Response<ResponseBody>
                ) {
                    if (bitmap == null) {
                        val bytes = response.body()!!.bytes()
                        bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }

                    binding.mainScreenImage.setImageBitmap(bitmap)
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    binding.mainScreenImage.setImageResource(R.mipmap.ic_electric_car_round)
                    showToast(this@MainActivity, "Could not fetch from LoremPics")
                }
            })
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