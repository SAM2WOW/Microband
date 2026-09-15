package com.unsame.microband.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.unsame.microband.data.MicrobandPreferences
import java.util.Locale
import kotlinx.coroutines.flow.first

data class BandAssociation(
    val associationId: Int?,
    val device: BluetoothDevice,
)

data class PairedDeviceOption(
    val address: String,
    val name: String,
    val looksLikeBand: Boolean,
)

class BandAssociationManager(
    private val context: Context,
    private val preferences: MicrobandPreferences,
) {
    private val manager = context.getSystemService(CompanionDeviceManager::class.java)
    private val bluetoothAdapter = context.getSystemService(android.bluetooth.BluetoothManager::class.java).adapter

    fun permissionState(): BluetoothPermissionState = BluetoothPermissionManager.state(context)

    @SuppressLint("MissingPermission")
    fun associate(
        onChooser: (IntentSender) -> Unit,
        onAssociated: (BandAssociation) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val filter = BluetoothDeviceFilter.Builder()
            // Band 2 advertises names such as "MSFT Band 2 75:74". Do not require the
            // RFCOMM UUID here: some firmware does not expose SDP records while scanning.
            .setNamePattern(BAND_NAME_PATTERN.toPattern())
            .build()
        val request = AssociationRequest.Builder()
            .addDeviceFilter(filter)
            .setSingleDevice(true)
            .build()

        manager.associate(
            request,
            object : CompanionDeviceManager.Callback() {
                @Suppress("DEPRECATION")
                override fun onDeviceFound(intentSender: IntentSender) = onChooser(intentSender)
                override fun onAssociationPending(intentSender: IntentSender) = onChooser(intentSender)
                override fun onFailure(error: CharSequence?) = onFailure(error?.toString() ?: "Association failed")
                @androidx.annotation.RequiresApi(33)
                override fun onAssociationCreated(associationInfo: AssociationInfo) {
                    preferences.setAssociationIdAsync(associationInfo.id)
                    preferences.clearManualDeviceAddressAsync()
                    val address = associationInfo.deviceMacAddress?.toString()?.let(::normalizeBluetoothAddress)
                    if (address == null) {
                        onFailure("Android created the association without a Bluetooth address")
                    } else {
                        onAssociated(
                            BandAssociation(
                                associationId = associationInfo.id,
                                device = bluetoothAdapter.getRemoteDevice(address),
                            ),
                        )
                    }
                }
            },
            Handler(Looper.getMainLooper()),
        )
    }

    /** All of the phone's currently bonded Bluetooth devices, for manual pairing when a Band's
     * name does not match the expected pattern. Devices that look like a Band are listed first. */
    @SuppressLint("MissingPermission")
    fun pairedDeviceOptions(): List<PairedDeviceOption> {
        if (!BluetoothPermissionManager.state(context).canConnect) return emptyList()
        return bluetoothAdapter.bondedDevices.orEmpty()
            .map { PairedDeviceOption(identityAddress(it), it.name ?: it.address, isBandName(it.name)) }
            .sortedWith(compareByDescending<PairedDeviceOption> { it.looksLikeBand }.thenBy { it.name })
    }

    /** Adopts a bonded device the user picked by hand, bypassing the name-pattern filter. */
    suspend fun selectPairedDevice(address: String): BandAssociation? {
        val device = pairedBondedDevice(address) ?: return null
        // Disarm any remembered CDM association id: otherwise the "any known association"
        // fallback below in currentAssociation() would keep resurrecting the old device on
        // the next refresh, silently ignoring this manual pick.
        preferences.clearAssociationId()
        // Persist the stable identity address, not whatever address this bond happens to be
        // listed under right now. The Band is a dual-mode device: Android enumerates its LE
        // bond under a resolvable private address that can rotate over time, while the RFCOMM
        // connection it actually needs always resolves to the fixed classic identity address
        // underneath. Storing the rotating address would silently orphan this pick once Android
        // rotates it: pairedBondedDevice() would no longer find a match on the next launch.
        preferences.setManualDeviceAddress(identityAddress(device))
        return BandAssociation(associationId = null, device = device)
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

    @SuppressLint("MissingPermission")
    suspend fun currentAssociation(): BandAssociation? {
        if (!BluetoothPermissionManager.state(context).canConnect) return null
        if (Build.VERSION.SDK_INT >= 33) {
            // Priority: (1) the exact CDM association we last confirmed ourselves, (2) a
            // device the user picked by hand, (3) any other CDM association this app still
            // holds (recovery path, e.g. our own preference was lost but Android's record
            // wasn't), (4) a bonded device whose name simply looks like a Band. Tier 3 must
            // come after tier 2: Android never forgets a CDM association on its own, so
            // checking it unconditionally would keep resurrecting an old Band after the user
            // deliberately picked a different one.
            val preferredId = preferences.associationId.first()
            val exactMatch = preferredId?.let { id -> manager.myAssociations.firstOrNull { it.id == id } }
            if (exactMatch != null) {
                val address = exactMatch.deviceMacAddress?.toString()?.let(::normalizeBluetoothAddress)
                if (address != null) {
                    preferences.setAssociationId(exactMatch.id)
                    return BandAssociation(exactMatch.id, bluetoothAdapter.getRemoteDevice(address))
                }
            }
            manuallySelectedDevice()?.let { return it }
            val fallback = manager.myAssociations.maxByOrNull { it.id }
            if (fallback != null) {
                val address = fallback.deviceMacAddress?.toString()?.let(::normalizeBluetoothAddress)
                if (address != null) {
                    preferences.setAssociationId(fallback.id)
                    return BandAssociation(fallback.id, bluetoothAdapter.getRemoteDevice(address))
                }
            }
            return pairedBand()
        }

        manuallySelectedDevice()?.let { return it }
        @Suppress("DEPRECND!")
        val address = manager.associations.firstOrNull()
        return if (address != null) {
            BandAssociation(null, bluetoothAdapter.getRemoteDevice(normalizeBluetoothAddress(address)))
        } else {
            pairedBand()
        }
    }

    private suspend fun manuallySelectedDevice(): BandAssociation? {
        val address = preferences.manualDeviceAddress.first() ?: return null
        return pairedBondedDevice(address)?.let { BandAssociation(associationId = null, device = it) }
    }

    @SuppressLint("MissingPermission")
    private fun pairedBand(): BandAssociation? = bluetoothAdapter.bondedDevices
        .firstOrNull { isBandName(it.name) }
        ?.let { BandAssociation(associationId = null, device = it) }

    companion object {
        private const val BAND_NAME_PATTERN = "(?i)^(?:MSFT|Microsoft)\\s+Band(?:\\s+2)?(?:\\s+[0-9A-F]{2}:[0-9A-F]{2})?.*$"

        internal fun isBandName(name: String?): Boolean =
            name != null && BAND_NAME_PATTERN.toRegex().matches(name)

        internal fun normalizeBluetoothAddress(address: String): String = address.uppercase(Locale.ROOT)
    }
}
