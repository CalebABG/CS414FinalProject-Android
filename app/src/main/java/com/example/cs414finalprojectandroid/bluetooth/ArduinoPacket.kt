package com.example.cs414finalprojectandroid.bluetooth

import com.example.cs414finalprojectandroid.Utilities.toByteArray
import java.util.zip.CRC32

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
        val crc32: CRC32 = CRC32()

        // Length in bytes
        const val DATA_LENGTH: Short = 0x40

        // Start and End packet bytes
        const val PACKET_START: Byte = 0x1
        const val PACKET_END: Byte = 0x4

        // Packet ID kinds
        const val SENSOR_DATA_PACKET_ID: Short = 0xD7
        const val STOP_MOTORS_PACKET_ID: Short = 0xE0
        const val PARENTAL_CONTROL_PACKET_ID: Short = 0xDF

        fun create(packetId: Short, data: ByteArray = byteArrayOf()): ByteArray {
            return ArduinoPacket()
                .setHeader(packetId)
                .setData(data)
                .setCRC32()
                .build()
        }
    }

    private var id: Short = 0x0
    private var crc: Int = 0x0
    private var ack: Byte = 0x1
    private var dataLength: Short = 0x0
    private var data: ByteArray = ByteArray(DATA_LENGTH.toInt())

    fun build(): ByteArray {
        val packetLength = 75
        val packet = ByteArray(packetLength)

        packet[0] = PACKET_START
        packet[packetLength - 1] = PACKET_END

        val idBytes = id.toByteArray()
        packet[1] = idBytes[0]
        packet[2] = idBytes[1]

        val crcBytes = crc.toByteArray()
        packet[3] = crcBytes[0]
        packet[4] = crcBytes[1]
        packet[5] = crcBytes[2]
        packet[6] = crcBytes[3]

        packet[7] = ack

        val dataLengthBytes = dataLength.toByteArray()
        packet[8] = dataLengthBytes[0]
        packet[9] = dataLengthBytes[1]

        for (i in 0 until DATA_LENGTH)
            packet[i + 10] = data[i]

        return packet
    }

    fun setHeader(packetId: Short): ArduinoPacket {
        id = packetId

        return this
    }

    fun setCRC32(): ArduinoPacket {
        crc32.update(id.toInt())
        crc32.update(ack.toInt())
        crc32.update(dataLength.toInt())
        crc32.update(data)

        crc = crc32.value.toInt()
        crc32.reset()

        return this
    }

    fun setData(data: ByteArray): ArduinoPacket {
        if (data.size in 1..DATA_LENGTH) {
            dataLength = data.size.toShort()

            for (i in data.indices)
                this.data[i] = data[i]
        }

        return this
    }
}