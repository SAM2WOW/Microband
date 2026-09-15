package com.unsame.microband.band.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.unsame.microband.band.model.BandConnectionState
import com.unsame.microband.band.model.BandDeviceInfo
import com.unsame.microband.band.model.BandException
import com.unsame.microband.band.protocol.BandConstants
import com.unsame.microband.band.protocol.BandPacketCodec
import com.unsame.microband.band.protocol.BandRawResponse
import com.unsame.microband.band.protocol.readExactBlocking
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class RfcommBandTransport(
    private val packetObserver: suspend (direction: String, bytes: ByteArray, status: String?) -> Unit,
) : BandTransport {
    private val mutableState = MutableStateFlow<BandConnectionState>(BandConnectionState.Disconnected)
    override val state: StateFlow<BandConnectionState> = mutableState.asStateFlow()

    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    @SuppressLint("MissingPermission")
    override suspend fun connect(device: BluetoothDevice) = withContext(Dispatchers.IO) {
        disconnect()
        mutableState.value = BandConnectionState.Connecting
        var pendingSocket: BluetoothSocket? = null
        var timedOut = false
        try {
            val newSocket = device.createRfcommSocketToServiceRecord(BandConstants.RFCOMM_SERVICE_UUID)
            pendingSocket = newSocket
            coroutineScope {
                // BluetoothSocket.connect() is a plain blocking call with no suspension point,
                // so wrapping it in withTimeout{} cannot actually interrupt it: if the remote
                // device never answers (e.g. it isn't really listening on this RFCOMM service,
                // which is easy to hit now that any bonded device can be picked by hand), the
                // call can block forever. The documented way to force it to unblock is to close
                // the socket from another thread, which makes connect() throw immediately.
                val watchdog = launch {
                    delay(CONNECT_TIMEOUT_MILLIS)
                    timedOut = true
                    runCatching { newSocket.close() }
                }
                try {
                    newSocket.connect()
                } finally {
                    watchdog.cancel()
                }
            }
            socket = newSocket
            input = newSocket.inputStream
            output = newSocket.outputStream
            mutableState.value = BandConnectionState.Connected(BandDeviceInfo(device.name ?: "Microsoft Band"))
        } catch (exception: Exception) {
            runCatching { pendingSocket?.close() }
            socket = null
            input = null
            output = null
            val message = if (timedOut) {
                "This device did not respond. Make sure you picked your actual Band."
            } else {
                "Unable to connect"
            }
            mutableState.value = BandConnectionState.Error(message)
            throw BandException.ConnectionFailed(exception)
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
        mutableState.value = BandConnectionState.Disconnected
    }

    override suspend fun write(data: ByteArray) = withContext(Dispatchers.IO) {
        val stream = output ?: throw BandException.Disconnected()
        stream.write(data)
        stream.flush()
        packetObserver("TX", data, null)
    }

    override suspend fun readExact(length: Int): ByteArray = withContext(Dispatchers.IO) {
        try {
            (input ?: throw BandException.Disconnected()).readExactBlocking(length).also {
                packetObserver("RX", it, null)
            }
        } catch (_: java.io.EOFException) {
            mutableState.value = BandConnectionState.Disconnected
            throw BandException.Disconnected()
        }
    }

    override suspend fun transact(
        command: ByteArray,
        responseLength: Int,
        transfer: ByteArray?,
        timeoutMillis: Long,
    ): BandRawResponse = withTimeout(timeoutMillis) {
        write(BandPacketCodec.frame(command))
        if (transfer != null) write(transfer)
        val payload = if (responseLength > 0) readExact(responseLength) else byteArrayOf()
        val statusBytes = readExact(6)
        val status = BandPacketCodec.parseStatus(statusBytes)
        packetObserver("STATUS", statusBytes, "facility=${status.facility}, code=${status.code}, error=${status.isError}")
        BandRawResponse(payload, status)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000L
    }
}
