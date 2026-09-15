package com.unsame.microband

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unsame.microband.ui.MicrobandApp
import com.unsame.microband.ui.MicrobandViewModel
import com.unsame.microband.ui.MicrobandViewModelFactory
import com.unsame.microband.ui.theme.MicrobandTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MicrobandViewModel by viewModels {
        val app = application as MicrobandApplication
        MicrobandViewModelFactory(app.associationManager, app.connectionManager, app.preferences)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MicrobandTheme {
                val context = LocalContext.current
                val state by viewModel.state.collectAsStateWithLifecycle()
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { viewModel.refresh() }
                val firmwareLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument(),
                ) { uri -> uri?.let { viewModel.inspectFirmwarePackage(context, it) } }
                val wallpaperLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument(),
                ) { uri -> uri?.let { viewModel.setWallpaper(context, it) } }

                LaunchedEffect(Unit) { viewModel.refresh() }
                LaunchedEffect(Unit) { viewModel.refreshNotificationAccess(context) }
                LaunchedEffect(Unit) { viewModel.refreshBackgroundStatus(context) }
                LaunchedEffect(Unit) { viewModel.refreshNotificationApps(context) }

                MicrobandApp(
                    state = state,
                    onRequestBluetoothPermissions = {
                        permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
                    },
                    onOpenBluetoothSettings = { viewModel.openBluetoothSettings(context) },
                    onRefreshPairedDevices = viewModel::refreshPairedDevices,
                    onSelectPairedDevice = viewModel::selectPairedDevice,
                    onConnect = viewModel::connect,
                    onDisconnect = viewModel::disconnect,
                    onPairNewBand = viewModel::pairNewBand,
                    onInspect = viewModel::inspectBand,
                    onFinishSetup = viewModel::finishSetup,
                    onSyncClock = viewModel::syncClock,
                    onClearLog = viewModel::clearProtocolLog,
                    onSetProtocolLogging = viewModel::setProtocolLogging,
                    onRefresh = viewModel::refresh,
                    onOpenNotificationAccess = { viewModel.openNotificationAccess(context) },
                    onSetNotificationPackage = viewModel::setNotificationPackage,
                    onSetAllNotifications = viewModel::setAllNotifications,
                    onSetMaskNotificationsWhenLocked = viewModel::setMaskNotificationsWhenLocked,
                    onSendTestNotification = viewModel::sendTestNotification,
                    onOpenBatteryOptimization = { viewModel.openBatteryOptimizationSettings(context) },
                    onRefreshHealth = viewModel::refreshHealthData,
                    onSetThemeColor = viewModel::setThemeColor,
                    onChooseWallpaper = { wallpaperLauncher.launch(arrayOf("image/*")) },
                    onClearWallpaper = viewModel::clearWallpaper,
                    onRefreshTiles = viewModel::refreshTiles,
                    onApplyTiles = viewModel::applyTiles,
                    onStartFirmwareUpdate = { viewModel.startFirmwareUpdate(context) },
                    onOpenFirmwareArchive = { viewModel.openFirmwareArchive(context) },
                    onChooseFirmwarePackage = { firmwareLauncher.launch(arrayOf("*/*")) },
                    onSetGeminiEnabled = viewModel::setGeminiAssistantEnabled,
                    onSaveGeminiKey = viewModel::saveGeminiApiKey,
                    onClearGeminiKey = viewModel::clearGeminiApiKey,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
        viewModel.refreshNotificationAccess(this)
        viewModel.refreshBackgroundStatus(this)
        viewModel.refreshNotificationApps(this)
    }
}
