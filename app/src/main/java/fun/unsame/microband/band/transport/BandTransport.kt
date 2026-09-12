package com.unsame.microband.band.transport

import android.bluetooth.BluetoothDevice
import com.unsame.microband.band.model.BandConnectionState
import com.unsame.microband.band.protocol.BandRawResponse
import kotlinx.coroutines.flow.StateFlow

interface BandTransport {
    val state: StateFlow<BandConnectionState>
    suspend fun connect(device: BluetoothDevice)
    suspend fun disconnect()
    suspend fun write(data: ByteArray)
    suspend fun readExact(length: Int): ByteArray
    suspend fun transact(
        command: ByteArray,
        responseLength: Int,
        transfer: ByteArray? = null,
        timeoutMillis: Long = 10_000,
    ): BandRawResponse
}
