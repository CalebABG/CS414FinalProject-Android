package com.example.cs414finalprojectandroid.bluetooth

import com.example.cs414finalprojectandroid.Utilities.toByteArray
import java.util.zip.CRC32

class ArduinoPacket {
    companion object {
        val crc32: CRC32 = CRC32()

        // Length in bytes
        const val DATA_LENGTH: Short = 0x10

        // Start and End packet bytes
        const val PACKET_START: Byte = 0x1
        const val PACKET_END: Byte = 0x4

        // Packet ID kinds
        const val SENSOR_DATA_PACKET_ID: Short = 0xD7
        const val DRIVE_PARAMETERS_ID: Short = 0xD8
        const val STOP_MOTORS_PACKET_ID: Short = 0xE0
        const val PARENTAL_CONTROL_PACKET_ID: Short = 0xDF

        fun create(packetId: Short, data: ByteArray = byteArrayOf()): ByteArray {
            return ArduinoPacket()
                .setHeader(packetId)
                .setData(data)
                .setCRC32()
                .build()
        }

        private fun addBytesToList(byteArray: ByteArray, mutableList: MutableList<Byte>) {
            for (byte in byteArray) mutableList.add(byte)
        }
    }

    var id: Short = 0x0
    var crc: Int = 0x0
    var ack: Byte = 0x1
    var dataLength: Short = 0x0
    var data: ByteArray = ByteArray(DATA_LENGTH.toInt())

    fun build(): ByteArray {
        val packet: MutableList<Byte> = mutableListOf()

        packet.add(PACKET_START)

        addBytesToList(id.toByteArray(), packet)
        addBytesToList(crc.toByteArray(), packet)

        packet.add(ack)

        addBytesToList(dataLength.toByteArray(), packet)
        addBytesToList(data, packet)

        packet.add(PACKET_END)

        return packet.toByteArray()
    }

    fun setHeader(packetId: Short): ArduinoPacket {
        id = packetId
        return this
    }

    fun setCRC32(): ArduinoPacket {
        val packet: MutableList<Byte> = mutableListOf()

        addBytesToList(id.toByteArray(), packet)

        packet.add(ack)

        addBytesToList(dataLength.toByteArray(), packet)
        addBytesToList(data, packet)

        crc32.update(packet.toByteArray())

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