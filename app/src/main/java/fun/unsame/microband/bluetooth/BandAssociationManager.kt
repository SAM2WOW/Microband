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

    @SuppressLint("MissingPermission")
    suspend fun currentAssociation(): BandAssociation? {
        if (!BluetoothPermissionManager.state(context).canConnect) return null
        if (Build.VERSION.SDK_INT >= 33) {
            val preferredId = preferences.associationId.first()
            val info = manager.myAssociations.firstOrNull { it.id == preferredId }
                ?: manager.myAssociations.maxByOrNull { it.id }
            if (info != null) {
                val address = info.deviceMacAddress?.toString()?.let(::normalizeBluetoothAddress)
                if (address != null) {
                    preferences.setAssociationId(info.id)
                    return BandAssociation(info.id, bluetoothAdapter.getRemoteDevice(address))
                }
            }
            return pairedBand()
        }

        @Suppress("DEPRECND!")
        val address = manager.associations.firstOrNull()
        return if (address != null) {
            BandAssociation(null, bluetoothAdapter.getRemoteDevice(normalizeBluetoothAddress(address)))
        } else {
            pairedBand()
        }
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
