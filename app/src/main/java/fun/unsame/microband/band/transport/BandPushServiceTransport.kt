package com.unsame.microband.band.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.unsame.microband.band.model.BandException
import com.unsame.microband.band.protocol.readExactBlocking
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** The Band's second RFCOMM service, used for phone-bound replies and microphone audio. */
class BandPushServiceTransport {
    private var socket: BluetoothSocket? = null

    @SuppressLint("MissingPermission")
    suspend fun connectAndListen(
        device: BluetoothDevice,
        onConnected: suspend () -> Unit,
        onPacket: suspend (BandPushPacket) -> Unit,
    ) =
        withContext(Dispatchers.IO) {
            disconnect()
            val candidate = device.createRfcommSocketToServiceRecord(PUSH_SERVICE_UUID)
            try {
                withTimeout(15_000) { candidate.connect() }
                socket = candidate
                onConnected()
                val input = candidate.inputStream
                while (true) {
                    val header = input.readExactBlocking(HEADER_SIZE)
                    val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                    val type = buffer.short.toInt() and 0xFFFF
                    val length = buffer.int
                    if (length !in 0..MAX_PACKET_SIZE) {
                        throw BandException.InvalidPacket("Invalid Band push packet length: $length")
                    }
                    onPacket(BandPushPacket(type, input.readExactBlocking(length)))
                }
            } catch (exception: EOFException) {
                throw BandException.Disconnected()
            } finally {
                if (socket === candidate) socket = null
                runCatching { candidate.close() }
            }
        }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        val current = socket
        socket = null
        runCatching { current?.close() }
    }

    companion object {
        val PUSH_SERVICE_UUID: UUID = UUID.fromString("C742E1A2-6320-5ABC-9643-D206C677E580")
        private const val HEADER_SIZE = 6
        private const val MAX_PACKET_SIZE = 2 * 1024 * 1024
    }
}

data class BandPushPacket(val type: Int, val payload: ByteArray)
