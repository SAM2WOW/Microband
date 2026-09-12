package com.unsame.microband.bluetooth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BandAssociationManagerTest {
    @Test
    fun `recognizes actual Band 2 advertising name`() {
        assertTrue(BandAssociationManager.isBandName("MSFT Band 2 75:74"))
    }

    @Test
    fun `recognizes legacy Microsoft name`() {
        assertTrue(BandAssociationManager.isBandName("Microsoft Band 2"))
    }

    @Test
    fun `does not accept unrelated fitness bands`() {
        assertFalse(BandAssociationManager.isBandName("Mi Smart Band 9"))
    }

    @Test
    fun `normalizes lowercase companion device address for Android Bluetooth API`() {
        assertEquals(
            "58:82:A8:D1:75:74",
            BandAssociationManager.normalizeBluetoothAddress("58:82:a8:d1:75:74"),
        )
    }
}
