package com.example.cs414finalprojectandroid.bluetooth

import kotlin.math.abs

/*
    Index      0:       Packet START
    Index      1:       Packet ID
    Index      2:       Packet ACK Byte
    Index      3:       Packet Data Sign (01 = Sensor X is negative; 02 = sensor Y is negative; 03 = both negative, 00 = neither negative)
    Index      4:       Packet Sensor X
    Index      5:       Packet Sensor Y
    Index      6:       Packet END

    Example Packet
    Index:   0     1     2     3     4     5     6
             01    D7    01    FF    FF    FF    04
*/

class ArduinoPacket {
    companion object {
        // Length in bytes
        const val PACKET_LENGTH = 0x7

        // Start and End packet bytes
        const val PACKET_START = 0x1
        const val PACKET_END = 0x4

        // Packet ID kinds
        const val SENSOR_DATA_PACKET_ID = 0xD7
        const val STOP_MOTORS_PACKET_ID = 0xE0
        const val PARENTAL_CONTROL_PACKET_ID = 0xDF

        fun create(packetId: Int, sensorX: Int = 0, sensorY: Int = 0): ByteArray {
            return ArduinoPacket()
                .setHeader(packetId)
                .setData(sensorX, sensorY)
                .build()
        }
    }

    private var data: ByteArray = ByteArray(PACKET_LENGTH)

    init {
        // Set START byte
        data[0] = PACKET_START.toByte()

        // Set END byte
        data[6] = PACKET_END.toByte()
    }

    fun build(): ByteArray {
        return data
    }

    fun setHeader(packetId: Int): ArduinoPacket {
        // Set ID
        data[1] = packetId.toByte()

        // Set ACK
        data[2] = 0x1

        return this
    }

    fun setData(sensorX: Int, sensorY: Int): ArduinoPacket {
        var sign: Byte = 0x0

        if (sensorX < 0) sign = 0x1
        if (sensorY < 0) sign = 0x2
        if (sensorX < 0 && sensorY < 0) sign = 0x3

        // Set Sign
        data[3] = sign

        // Set Sensor Data
        data[4] = abs(sensorX).toByte()
        data[5] = abs(sensorY).toByte()

        return this
    }
}