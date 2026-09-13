package com.unsame.microband

import android.bluetooth.BluetoothManager
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.unsame.microband.band.protocol.BandProtocol
import com.unsame.microband.band.transport.RfcommBandTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Hardware regression test: writing the current start strip must be accepted unchanged. */
@RunWith(AndroidJUnit4::class)
class BandTileProbeInstrumentedTest {
    @Test
    fun rewriteCurrentStartStrip() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val adapter = context.getSystemService(BluetoothManager::class.java).adapter
        val device = adapter.bondedDevices.single { it.name?.startsWith("MSFT Band 2") == true }
        val transport = RfcommBandTransport { direction, bytes, status ->
            report("$direction ${bytes.size} bytes ${status.orEmpty()}")
        }
        transport.connect(device)
        try {
            val protocol = BandProtocol(transport)
            val catalog = protocol.getTileCatalog()
            report("installed=${catalog.installed.map { it.name }} available=${catalog.available.map { it.name }} capacity=${catalog.capacity}")
            protocol.setTiles(catalog.installed)
            report("rewrite accepted")
            val candidate = catalog.available.firstOrNull() ?: return@runBlocking
            report("testing add=${candidate.name}")
            try {
                protocol.setTiles(catalog.installed + candidate)
                val changed = protocol.getTileCatalog()
                assertTrue("Added tile was not installed", changed.installed.any { it.id == candidate.id })
                report("add accepted")
            } finally {
                protocol.setTiles(catalog.installed)
                report("original strip restored")
            }
        } finally {
            transport.disconnect()
        }
    }

    private fun report(message: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply { putString("stream", "$message\n") },
        )
    }
}
