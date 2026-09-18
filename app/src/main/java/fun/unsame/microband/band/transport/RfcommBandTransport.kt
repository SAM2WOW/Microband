package com.unsame.microband.band.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
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
        Log.d(TAG, "connect: disconnecting any previous socket")
        disconnect()
        mutableState.value = BandConnectionState.Connecting
        var pendingSocket: BluetoothSocket? = null
        var timedOut = false
        try {
            Log.d(TAG, "connect: createRfcommSocketToServiceRecord(${BandConstants.RFCOMM_SERVICE_UUID}) for ${device.address} bondState=${device.bondState}")
            val newSocket = device.createRfcommSocketToServiceRecord(BandConstants.RFCOMM_SERVICE_UUID)
            pendingSocket = newSocket
            val connectStart = System.currentTimeMillis()
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
                    Log.w(TAG, "connect: watchdog firing after ${CONNECT_TIMEOUT_MILLIS}ms, forcing socket closed")
                    runCatching { newSocket.close() }
                }
                try {
                    Log.d(TAG, "connect: calling BluetoothSocket.connect() (blocking)")
                    newSocket.connect()
                    Log.d(TAG, "connect: BluetoothSocket.connect() returned after ${System.currentTimeMillis() - connectStart}ms")
                } finally {
                    watchdog.cancel()
                }
            }
            socket = newSocket
            input = newSocket.inputStream
            output = newSocket.outputStream
            Log.d(TAG, "connect: socket established, streams open")
            mutableState.value = BandConnectionState.Connected(BandDeviceInfo(device.name ?: "Microsoft Band"))
        } catch (exception: Exception) {
            Log.e(TAG, "connect: failed (timedOut=$timedOut)", exception)
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
    ): BandRawResponse = withContext(Dispatchers.IO) {
        var timedOut = false
        val transactStart = System.currentTimeMillis()
        Log.v(TAG, "transact: facility=0x${command.getOrNull(3)?.let { "%02X".format(it) }} code=0x${command.getOrNull(2)?.let { "%02X".format(it) }} responseLength=$responseLength transferSize=${transfer?.size ?: 0}")
        coroutineScope {
            // write()/readExact() ultimately block on the socket's plain Java streams, which have
            // no suspension point either, so the same withTimeout{} limitation as connect() above
            // applies here: if the Band stops responding mid-transaction, a wrapped withTimeout{}
            // cannot interrupt the blocked read/write, and every protocol operation (time sync,
            // health reads, notifications, firmware transfer...) could hang forever instead of
            // failing. Closing the socket from another thread unblocks it, same as in connect().
            val watchdog = launch {
                delay(timeoutMillis)
                timedOut = true
                runCatching { socket?.close() }
            }
            try {
                write(BandPacketCodec.frame(command))
                if (transfer != null) write(transfer)
                val payload = if (responseLength > 0) readExact(responseLength) else byteArrayOf()
                val statusBytes = readExact(6)
                val status = BandPacketCodec.parseStatus(statusBytes)
                packetObserver("STATUS", statusBytes, "facility=${status.facility}, code=${status.code}, error=${status.isError}")
                Log.v(TAG, "transact: completed in ${System.currentTimeMillis() - transactStart}ms, status=${status}")
                BandRawResponse(payload, status)
            } catch (exception: BandException) {
                Log.w(TAG, "transact: failed after ${System.currentTimeMillis() - transactStart}ms (timedOut=$timedOut)", exception)
                throw exception
            } catch (exception: Exception) {
                Log.w(TAG, "transact: failed after ${System.currentTimeMillis() - transactStart}ms (timedOut=$timedOut)", exception)
                if (timedOut) throw BandException.Timeout() else throw exception
            } finally {
                watchdog.cancel()
            }
        }
    }

    private companion object {
        const val TAG = "MicrobandConnect"
        const val CONNECT_TIMEOUT_MILLIS = 15_000L
    }
}
