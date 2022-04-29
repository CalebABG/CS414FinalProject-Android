package com.example.cs414finalprojectandroid

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

object Utilities {
    // Converts 4 bytes (uint32_t) to an unsigned integer (Arduino is little endian)
    private fun composeUInt32(bytes: IntArray, isLittleEndian: Boolean): Int {
        return if (isLittleEndian) (bytes[3] shl 24) + (bytes[2] shl 16) + (bytes[1] shl 8) + bytes[0]
        else (bytes[0] shl 24) + (bytes[1] shl 16) + (bytes[2] shl 8) + bytes[3]
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