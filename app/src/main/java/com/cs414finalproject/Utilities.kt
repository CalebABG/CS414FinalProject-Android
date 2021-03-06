package com.cs414finalproject

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.widget.Toast
import com.cs414finalproject.activities.ControlActivity
import io.paperdb.Book
import io.paperdb.Paper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.min

object Utilities {
    val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")
        .withZone(ZoneOffset.UTC)

    fun getReplayBook(): Book {
        return Paper.book(ControlActivity.REPLAY_COLLECTION_NAME)
    }

    fun showToast(context: Context, text: String, toastLength: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, text, toastLength).show()
    }

    fun getSystemBluetoothAdapter(activity: Activity): BluetoothAdapter? {
        return (activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    fun Int.toByteArray(): ByteArray {
        return ByteBuffer
            .allocate(Int.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(this)
            .array()
    }

    fun Long.toByteArray(): ByteArray {
        return ByteBuffer
            .allocate(Long.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putLong(this)
            .array()
    }

    fun Short.toByteArray(): ByteArray {
        return ByteBuffer
            .allocate(Short.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(this)
            .array()
    }

    fun Float.toByteArray(): ByteArray {
        return ByteBuffer
            .allocate(Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(this)
            .array()
    }

    fun ByteArray.toFloat(byteOrder: ByteOrder): Float {
        return ByteBuffer
            .wrap(this)
            .order(byteOrder)
            .float
    }

    fun <T> MutableList<T>.isFull(capacity: Int): Boolean {
        return this.size >= capacity
    }

    fun Boolean.toByte(): Byte {
        if (this) return 1
        return 0
    }

    fun map(value: Double, min1: Double, max1: Double, min2: Double, max2: Double): Double {
        return min2 + (max2 - min2) * ((value - min1) / (max1 - min1))
    }

    fun map(value: Float, min1: Float, max1: Float, min2: Float, max2: Float): Double {
        return (min2 + (max2 - min2) * ((value - min1) / (max1 - min1))).toDouble()
    }

    fun constrain(amt: Double, low: Double, high: Double): Double {
        return if (amt < low) low else min(amt, high)
    }

    fun constrain(amt: Float, low: Float, high: Float): Double {
        return if (amt < low) low.toDouble() else min(amt, high)
            .toDouble()
    }

    /*
    * Reference: https://stackoverflow.com/questions/66613717/kotlin-convert-hex-string-to-bytearray
    */
    fun String.hexToByteArray(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }

        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    fun IntArray.toHex(): String {
        val s = StringBuilder()
        for (byte in this) s.append(String.format("%02X", byte))
        return s.toString()
    }

    fun ByteArray.toHex(): String {
        val s = StringBuilder()
        for (byte in this) s.append(String.format("%02X", byte))
        return s.toString()
    }
}