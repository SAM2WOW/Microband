package com.unsame.microband.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.core.app.NotificationManagerCompat
import com.unsame.microband.band.model.BandConnectionState
import com.unsame.microband.band.personalization.BandWallpaperProcessor
import com.unsame.microband.band.oobe.BandOobeStep
import com.unsame.microband.bluetooth.BandAssociation
import com.unsame.microband.bluetooth.BandAssociationManager
import com.unsame.microband.bluetooth.BandConnectionManager
import com.unsame.microband.bluetooth.BluetoothPermissionManager
import com.unsame.microband.bluetooth.BluetoothPermissionState
import com.unsame.microband.data.MicrobandPreferences
import com.unsame.microband.data.ProtocolPacketLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

data class MicrobandUiState(
    val permissions: BluetoothPermissionState = BluetoothPermissionState(false, false),
    val association: BandAssociation? = null,
    val connection: BandConnectionState = BandConnectionState.Unassociated,
    val oobeStep: BandOobeStep = BandOobeStep.Inspect,
    val protocolLogging: Boolean = false,
    val logs: List<ProtocolPacketLog> = emptyList(),
    val message: String? = null,
    val associationInProgress: Boolean = false,
    val setupInProgress: Boolean = false,
    val firmwarePackageStatus: String? = null,
    val notificationAccessGranted: Boolean = false,
    val notificationCategories: Set<String> = emptySet(),
    val themeAccent: Int = 0xFF0078D7.toInt(),
    val personalizationBusy: Boolean = false,
)

class MicrobandViewModel(
    private val associationManager: BandAssociationManager,
    private val connectionManager: BandConnectionManager,
    private val preferences: MicrobandPreferences,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MicrobandUiState())
    val state: StateFlow<MicrobandUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch { connectionManager.state.collect { value -> mutableState.update { it.copy(connection = value) } } }
        viewModelScope.launch { connectionManager.oobeStep.collect { value -> mutableState.update { it.copy(oobeStep = value) } } }
        viewModelScope.launch { connectionManager.setupInProgress.collect { value -> mutableState.update { it.copy(setupInProgress = value) } } }
        viewModelScope.launch { connectionManager.recentLogs.collect { value -> mutableState.update { it.copy(logs = value) } } }
        viewModelScope.launch { preferences.protocolLogging.collect { value -> mutableState.update { it.copy(protocolLogging = value) } } }
        viewModelScope.launch { preferences.notificationCategories.collect { value -> mutableState.update { it.copy(notificationCategories = value) } } }
        viewModelScope.launch { connectionManager.events.collect { value -> mutableState.update { it.copy(message = value) } } }
    }

    fun refresh() = viewModelScope.launch {
        val permissions = associationManager.permissionState()
        val association = if (permissions.canConnect) runCatching { associationManager.currentAssociation() }.getOrNull() else null
        mutableState.update {
            it.copy(
                permissions = permissions,
                association = association,
                connection = when {
                    association == null -> BandConnectionState.Unassociated
                    it.connection is BandConnectionState.Unassociated -> BandConnectionState.Disconnected
                    else -> it.connection
                },
            )
        }
    }

    fun findBand(launchChooser: (android.content.IntentSender) -> Unit) {
        mutableState.update { it.copy(associationInProgress = true, message = null) }
        associationManager.associate(
            onChooser = launchChooser,
            onAssociated = { association -> acceptAssociation(association) },
            onFailure = { error ->
                mutableState.update { it.copy(associationInProgress = false, message = error) }
            },
        )
    }

    fun onAssociationResult(success: Boolean) {
        if (!success) {
            mutableState.update { it.copy(associationInProgress = false, message = "Association cancelled") }
            return
        }
        // Android can return RESULT_OK slightly before CompanionDeviceManager publishes
        // the association. Retry briefly instead of leaving the welcome screen unchanged.
        viewModelScope.launch {
            repeat(12) {
                if (mutableState.value.association != null) return@launch
                associationManager.currentAssociation()?.let { association ->
                    acceptAssociation(association)
                    return@launch
                }
                delay(250)
            }
            mutableState.update {
                it.copy(
                    associationInProgress = false,
                    message = "Android did not finish saving the Band association. Please try again.",
                )
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun acceptAssociation(association: BandAssociation) {
        mutableState.update {
            it.copy(
                association = association,
                connection = BandConnectionState.Disconnected,
                associationInProgress = false,
                message = "${runCatching { association.device.name }.getOrNull() ?: "Band"} associated",
            )
        }
    }

    fun connect() {
        val association = mutableState.value.association
        if (association == null) {
            mutableState.update { it.copy(message = "Find your Band first") }
            return
        }
        connectionManager.connect(association.device)
    }

    fun disconnect() = connectionManager.disconnect()
    fun inspectBand() = connectionManager.inspect()
    fun finishSetup() = connectionManager.finishSetup()
    fun syncClock() = connectionManager.syncClock()

    fun clearProtocolLog() = viewModelScope.launch {
        connectionManager.clearProtocolLog()
        mutableState.update { it.copy(message = "Protocol log cleared") }
    }

    fun setProtocolLogging(enabled: Boolean) = viewModelScope.launch {
        preferences.setProtocolLogging(enabled)
    }

    fun openNotificationAccess(context: Context) {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun refreshNotificationAccess(context: Context) {
        val granted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        mutableState.update { it.copy(notificationAccessGranted = granted) }
    }

    fun setNotificationCategory(category: String, enabled: Boolean) = viewModelScope.launch {
        preferences.setNotificationCategory(category, enabled)
    }

    fun sendTestNotification() = connectionManager.sendTestNotification()

    fun setThemeColor(accent: Int) {
        mutableState.update { it.copy(themeAccent = accent) }
        connectionManager.setThemeColor(accent)
    }

    fun setWallpaper(context: Context, uri: Uri) = viewModelScope.launch {
        mutableState.update { it.copy(personalizationBusy = true) }
        try {
            val pixels = withContext(Dispatchers.IO) {
                BandWallpaperProcessor.decodeAndCrop(context.contentResolver, uri)
            }
            connectionManager.setWallpaper(pixels).join()
        } catch (exception: Exception) {
            mutableState.update { it.copy(message = "Could not prepare wallpaper: ${exception.message ?: "unsupported image"}") }
        } finally {
            mutableState.update { it.copy(personalizationBusy = false) }
        }
    }

    fun clearWallpaper() = connectionManager.clearWallpaper()

    fun openFirmwareArchive(context: Context) {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://github.com/MicrosoftBandDev/archive/tree/main/Firmware/Band%202%20(2.0.5202.0)"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun inspectFirmwarePackage(context: Context, uri: Uri) = viewModelScope.launch {
        val status = withContext(Dispatchers.IO) {
            runCatching {
                val digest = MessageDigest.getInstance("SHA-256")
                val size = context.contentResolver.openInputStream(uri)?.use { stream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = stream.read(buffer)
                        if (count == -1) break
                        digest.update(buffer, 0, count)
                        total += count
                    }
                    total
                } ?: error("Unable to read the selected file")
                val hash = digest.digest().joinToString("") { "%02X".format(it) }
                if (size == BAND_2_5202_SIZE && hash == BAND_2_5202_SHA256) {
                    "Verified Band 2 firmware 2.0.5202.0 package."
                } else {
                    "This is not the verified Band 2 2.0.5202.0 package; it will not be used."
                }
            }.getOrElse { "Could not validate firmware: ${it.message ?: "unknown error"}" }
        }
        mutableState.update { it.copy(firmwarePackageStatus = status, message = status) }
    }

    companion object {
        private const val BAND_2_5202_SIZE = 1_838_103L
        private const val BAND_2_5202_SHA256 = "2473896B8281B2FF81E462374A48BE8A3E8901FB6B2C55AF0FE9125930A60727"
    }

}

class MicrobandViewModelFactory(
    private val associationManager: BandAssociationManager,
    private val connectionManager: BandConnectionManager,
    private val preferences: MicrobandPreferences,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MicrobandViewModel(associationManager, connectionManager, preferences) as T
}
