package com.example.cs414finalprojectandroid

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
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.cs414finalprojectandroid.Utilities.showToast
import com.example.cs414finalprojectandroid.retrofit.LoremPicsumService
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit

/*

Project Points (8):

DONE 1 - Use of SharedPreferences for persisting Min/Max sensor config
DONE 1 - Use of Android service that requires user-granted permissions (Bluetooth + Location)
1 - Use of Notifications (local notification - app put in background, de-register accelerometer listener) / safety if bluetooth connected
2 - Use of Broadcast Receiver Services
// CANNOT DO IN TIME 2 - Use of SQLite database (store sent packets, can use to replay motion)
DONE 2 - Use of at least one device sensor (Accelerometer)
DONE 2 - Use of a REST-ful HTTP API [Retrofit] (Use for Start screen photo) - look at `Lorem Picsum`: https://picsum.photos/
1 - Use of three or more Activities
*/

class MainActivity : AppCompatActivity() {
    companion object {
        const val LOREM_PICSUM_URL = "https://picsum.photos/"

        const val REQUEST_ENABLE_BT = 0xE4

        val permissionsMap: MutableMap<String, Int> = mutableMapOf(
            Manifest.permission.BLUETOOTH to PackageManager.PERMISSION_DENIED,
            Manifest.permission.ACCESS_FINE_LOCATION to PackageManager.PERMISSION_DENIED
        )

        var PERMISSIONS = listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        )

        fun getSystemBluetoothAdapter(activity: Activity): BluetoothAdapter? {
            return (activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        }
    }

    private var bitmap: Bitmap? = null

    private var bluetoothAdapter: BluetoothAdapter? = null

    private var retrofit = Retrofit.Builder()
        .baseUrl(LOREM_PICSUM_URL)
        .build()

    private var loremPicsService = retrofit
        .create(LoremPicsumService::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothAdapter = getSystemBluetoothAdapter(this)

        continueBtn.setOnClickListener { startCarControlActivity() }
    }

    override fun onResume() {
        super.onResume()
        setScreenLoremPicsImage()
    }

    private fun setScreenLoremPicsImage() {
        val call = loremPicsService.fetchMainScreenImage(350.toString(), 350.toString())

        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (bitmap == null) {
                    val body = response.body()
                    val bytes = body!!.bytes()
                    bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }

                mainScreenImage.setImageBitmap(bitmap)
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                mainScreenImage.setImageResource(R.mipmap.ic_electric_car_round)
                showToast(this@MainActivity, "Could not fetch LoremPics Image :(")
            }
        })
    }

    private fun startCarControlActivity() {
        if (bluetoothAdapter == null) {
            showToast(this, "Bluetooth is not available", Toast.LENGTH_LONG)
        } else {
            if (hasPermissions(this, PERMISSIONS)) {
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

    private fun hasPermissions(context: Context, permissions: List<String>): Boolean {
        return permissions.all {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestNeededAppPermissions() {
        ActivityCompat.requestPermissions(this, PERMISSIONS.toTypedArray(), REQUEST_ENABLE_BT)
    }

    private fun canShowRequestPermissionsRationale() =
        ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH) ||
                ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )

    private fun neededPermissionsGranted(perms: MutableMap<String, Int>) =
        perms[Manifest.permission.BLUETOOTH] == PackageManager.PERMISSION_GRANTED &&
                perms[Manifest.permission.ACCESS_FINE_LOCATION] == PackageManager.PERMISSION_GRANTED

    private fun showRequestPermissionsDialog() {
        AlertDialog.Builder(this)
            .setMessage("Bluetooth and Location permission are required for this app, allow?")
            .setPositiveButton("Yes") { _, _ -> requestNeededAppPermissions() }
            .setNegativeButton("No", null)
            .setTitle(R.string.app_name)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (grantResults.isNotEmpty()) {
                for (i in permissions.indices) permissionsMap[permissions[i]] = grantResults[i]

                if (neededPermissionsGranted(permissionsMap)) {
                    gotoCarControl()
                } else {
                    if (canShowRequestPermissionsRationale()) {
                        showRequestPermissionsDialog()
                    } else {
                        showToast(this, "Go to settings and enable permissions", Toast.LENGTH_LONG)
                    }
                }
            }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}