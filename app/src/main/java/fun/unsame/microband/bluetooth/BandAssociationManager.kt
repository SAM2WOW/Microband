package com.unsame.microband.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import com.unsame.microband.data.MicrobandPreferences
import java.util.Locale
import kotlinx.coroutines.flow.first

data class BandAssociation(
    val device: BluetoothDevice,
)

data class PairedDeviceOption(
    val address: String,
    val name: String,
    val looksLikeBand: Boolean,
)

/**
 * Pairing goes through classic Android Bluetooth settings, not the Companion Device Manager:
 * the user pairs the Band from the system Bluetooth screen (see [MicrobandViewModel.openBluetoothSettings]),
 * then picks it from the phone's bonded devices in [pairedDeviceOptions]. This app previously used
 * CDM's own scan-and-associate flow, but that meant tracking two parallel, occasionally
 * conflicting sources of truth (Android's own association records vs. this app's remembered
 * device) -- classic pairing plus a manual pick has exactly one source of truth.
 */
class BandAssociationManager(
    private val context: Context,
    private val preferences: MicrobandPreferences,
) {
    private val bluetoothAdapter = context.getSystemService(android.bluetooth.BluetoothManager::class.java).adapter

    fun permissionState(): BluetoothPermissionState = BluetoothPermissionManager.state(context)

    /** All of the phone's currently bonded Bluetooth devices, for picking which one is the Band.
     * Devices that look like a Band by name are listed first, but any bonded device is selectable
     * in case the Band was renamed by a previous owner or its name doesn't match. */
    @SuppressLint("MissingPermission")
    fun pairedDeviceOptions(): List<PairedDeviceOption> {
        if (!BluetoothPermissionManager.state(context).canConnect) return emptyList()
        return bluetoothAdapter.bondedDevices.orEmpty()
            .map { PairedDeviceOption(identityAddress(it), it.name ?: it.address, isBandName(it.name)) }
            .sortedWith(compareByDescending<PairedDeviceOption> { it.looksLikeBand }.thenBy { it.name })
    }

    /** Adopts a bonded device the user picked by hand. */
    suspend fun selectPairedDevice(address: String): BandAssociation? {
        val device = pairedBondedDevice(address) ?: return null
        // Persist the stable identity address, not whatever address this bond happens to be
        // listed under right now. The Band is a dual-mode device: Android enumerates its LE
        // bond under a resolvable private address that can rotate over time, while the RFCOMM
        // connection it actually needs always resolves to the fixed classic identity address
        // underneath. Storing the rotating address would silently orphan this pick once Android
        // rotates it: pairedBondedDevice() would no longer find a match on the next launch.
        preferences.setManualDeviceAddress(identityAddress(device))
        return BandAssociation(device = device)
    }

    /** Matches a bonded device by its stable identity address, not the address it happens to be
     * enumerated under right now -- see the comment in [selectPairedDevice]. */
    @SuppressLint("MissingPermission")
    private fun pairedBondedDevice(address: String): BluetoothDevice? =
        bluetoothAdapter.bondedDevices.orEmpty().firstOrNull { identityAddress(it) == address }

    /** The Bluetooth identity address for [device]: the fixed address behind a possibly-rotating
     * LE private address, available from Android 14 (API 34) onward via
     * [BluetoothDevice.getIdentityAddressWithType]. Falls back to the device's
     * currently-enumerated address when the identity address isn't available -- classic-only
     * devices, no permission, or an older platform -- which matches the previous behavior. */
    @SuppressLint("MissingPermission")
    private fun identityAddress(device: BluetoothDevice): String {
        if (Build.VERSION.SDK_INT >= 34) {
            runCatching { device.identityAddressWithType?.address }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { return normalizeBluetoothAddress(it) }
        }
        return normalizeBluetoothAddress(device.address)
    }

    suspend fun currentAssociation(): BandAssociation? {
        if (!BluetoothPermissionManager.state(context).canConnect) return null
        return manuallySelectedDevice() ?: pairedBand()
    }

    private suspend fun manuallySelectedDevice(): BandAssociation? {
        val address = preferences.manualDeviceAddress.first() ?: return null
        return pairedBondedDevice(address)?.let { BandAssociation(device = it) }
    }

    @SuppressLint("MissingPermission")
    private fun pairedBand(): BandAssociation? = bluetoothAdapter.bondedDevices
        .firstOrNull { isBandName(it.name) }
        ?.let { BandAssociation(device = it) }

    companion object {
        private const val BAND_NAME_PATTERN = "(?i)^(?:MSFT|Microsoft)\\s+Band(?:\\s+2)?(?:\\s+[0-9A-F]{2}:[0-9A-F]{2})?.*$"

        internal fun isBandName(name: String?): Boolean =
            name != null && BAND_NAME_PATTERN.toRegex().matches(name)

        internal fun normalizeBluetoothAddress(address: String): String = address.uppercase(Locale.ROOT)
    }
}
