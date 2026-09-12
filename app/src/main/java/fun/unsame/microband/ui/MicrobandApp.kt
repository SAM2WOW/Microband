package com.unsame.microband.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unsame.microband.R
import com.unsame.microband.band.model.BandConnectionState
import com.unsame.microband.band.model.BandDeviceInfo
import com.unsame.microband.band.oobe.BandOobeStep
import com.unsame.microband.data.ProtocolPacketLog
import com.unsame.microband.notification.NotificationCategory
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class Destination(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Rounded.Home),
    Notifications("Notifications", Icons.Rounded.Notifications),
    Personalize("Personalize", Icons.Rounded.Palette),
    Settings("Settings", Icons.Rounded.Settings),
}

@Composable
fun MicrobandApp(
    state: MicrobandUiState,
    onRequestBluetoothPermissions: () -> Unit,
    onFindBand: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onInspect: () -> Unit,
    onFinishSetup: () -> Unit,
    onSyncClock: () -> Unit,
    onClearLog: () -> Unit,
    onSetProtocolLogging: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onSetNotificationCategory: (String, Boolean) -> Unit,
    onSendTestNotification: () -> Unit,
    onSetThemeColor: (Int) -> Unit,
    onChooseWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onOpenFirmwareArchive: () -> Unit,
    onChooseFirmwarePackage: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it) } }

    if (!state.permissions.allGranted || state.association == null) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            WelcomeScreen(
                permissionsGranted = state.permissions.allGranted,
                associationInProgress = state.associationInProgress,
                onGrantPermissions = onRequestBluetoothPermissions,
                onFindBand = onFindBand,
                modifier = Modifier.padding(padding),
            )
        }
        return
    }

    var selected by rememberSaveable { mutableIntStateOf(0) }
    val destinations = Destination.entries
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        AnimatedContent(targetState = destinations[selected], label = "destination") { destination ->
            when (destination) {
                Destination.Home -> HomeScreen(state, onConnect, onDisconnect, onInspect, onFinishSetup, onSyncClock, Modifier.padding(padding))
                Destination.Notifications -> NotificationsScreen(
                    accessGranted = state.notificationAccessGranted,
                    enabledCategories = state.notificationCategories,
                    connected = state.connection is BandConnectionState.Connected,
                    onOpenNotificationAccess = onOpenNotificationAccess,
                    onSetCategory = onSetNotificationCategory,
                    onSendTestNotification = onSendTestNotification,
                    modifier = Modifier.padding(padding),
                )
                Destination.Personalize -> PersonalizeScreen(
                    state = state,
                    onSetThemeColor = onSetThemeColor,
                    onChooseWallpaper = onChooseWallpaper,
                    onClearWallpaper = onClearWallpaper,
                    modifier = Modifier.padding(padding),
                )
                Destination.Settings -> SettingsContainer(
                    state = state,
                    onSetProtocolLogging = onSetProtocolLogging,
                    onOpenFirmwareArchive = onOpenFirmwareArchive,
                    onChooseFirmwarePackage = onChooseFirmwarePackage,
                    onConnect = onConnect,
                    onDisconnect = onDisconnect,
                    onInspect = onInspect,
                    onClearLog = onClearLog,
                    onRefresh = onRefresh,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun WelcomeScreen(
    permissionsGranted: Boolean,
    associationInProgress: Boolean,
    onGrantPermissions: () -> Unit,
    onFindBand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(220.dp).clip(RoundedCornerShape(48.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_microband_mark),
            contentDescription = "Microband wearable icon",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(170.dp),
            )
        }
        Text("Microband", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Bring your Microsoft Band 2 back online—privately, directly, and without a cloud account.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = if (permissionsGranted) onFindBand else onGrantPermissions,
            enabled = !associationInProgress,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(
                when {
                    associationInProgress -> "Finishing association…"
                    permissionsGranted -> "Find my Band"
                    else -> "Allow Bluetooth access"
                },
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (permissionsGranted) "Android will open its secure companion-device picker."
            else "Microband needs Nearby devices access to find and connect to your Band.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HomeScreen(
    state: MicrobandUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onInspect: () -> Unit,
    onFinishSetup: () -> Unit,
    onSyncClock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = state.connection as? BandConnectionState.Connected
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Your Band", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("Local-first connection and setup", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { DeviceHeroCard(state.connection, onConnect, onDisconnect, onSyncClock) }
        item {
            SetupCard(
                device = connected?.device,
                currentStep = state.oobeStep,
                setupInProgress = state.setupInProgress,
                onInspect = onInspect,
                onFinishSetup = onFinishSetup,
            )
        }
        item { NewcomerTipsCarousel() }
        if (connected?.device?.oobeComplete == true) {
            item {
                SectionCard("Ready for everyday use", "Microband will reconnect through your saved Android companion association. Notification forwarding is opt-in.")
            }
        }
    }
}

@Composable
private fun DeviceHeroCard(
    connection: BandConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSyncClock: () -> Unit,
) {
    val connected = connection as? BandConnectionState.Connected
    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(14.dp).clip(CircleShape).background(
                        when (connection) {
                            is BandConnectionState.Connected -> Color(0xFF1B8E5A)
                            is BandConnectionState.Connecting -> MaterialTheme.colorScheme.tertiary
                            is BandConnectionState.Error -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.outline
                        },
                    ),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    when (connection) {
                        is BandConnectionState.Connected -> "Connected"
                        is BandConnectionState.Connecting -> "Connecting…"
                        is BandConnectionState.Error -> connection.reason
                        else -> "Associated"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(connected?.device?.bluetoothName ?: "Microsoft Band 2", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            connected?.device?.firmwareVersion?.let { Text("Firmware $it", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .75f)) }
            Spacer(Modifier.height(20.dp))
            if (connection is BandConnectionState.Connected) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onDisconnect) { Text("Disconnect") }
                    FilledTonalButton(onClick = onSyncClock) { Text("Sync time") }
                }
            } else {
                Button(onClick = onConnect, enabled = connection !is BandConnectionState.Connecting) { Text("Connect") }
            }
        }
    }
}

@Composable
private fun SetupCard(
    device: BandDeviceInfo?,
    currentStep: BandOobeStep,
    setupInProgress: Boolean,
    onInspect: () -> Unit,
    onFinishSetup: () -> Unit,
) {
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Band setup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            if (device?.oobeComplete == true) {
                StatusRow(true, "Setup complete")
                Text("Your Band is ready.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                StatusRow(device != null, "Bluetooth connection")
                StatusRow(device?.isBand2 == true, "Band 2 detected")
                StatusRow(device?.oobeStage != null && device.oobeStage.wireValue != 101, "Language selected")
                StatusRow(currentStep.ordinal >= BandOobeStep.Preparing.ordinal, "Preparing Band")
                StatusRow(currentStep.ordinal >= BandOobeStep.SettingClock.ordinal, "Setting clock")
                StatusRow(currentStep.ordinal >= BandOobeStep.Finalizing.ordinal, "Finishing setup")
                AnimatedVisibility(setupInProgress) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = currentStep.userLabel })
                }
                if (device == null || device.pcbId == null) {
                    Button(onClick = onInspect, enabled = device != null, modifier = Modifier.fillMaxWidth()) { Text("Check Band") }
                } else {
                    Button(onClick = onFinishSetup, enabled = device.isBand2 && device.oobeComplete == false && !setupInProgress, modifier = Modifier.fillMaxWidth()) {
                        Text("Finish setup")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(done: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(
                if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            contentAlignment = Alignment.Center,
        ) { Text(if (done) "✓" else "", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall) }
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun NotificationsScreen(
    accessGranted: Boolean,
    enabledCategories: Set<String>,
    connected: Boolean,
    onOpenNotificationAccess: () -> Unit,
    onSetCategory: (String, Boolean) -> Unit,
    onSendTestNotification: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Notifications", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("Choose what can appear on your Band.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SectionCard("Private by default", "Notification access is optional. Persistent system notifications and duplicate updates are never forwarded.")
        }
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusRow(accessGranted, if (accessGranted) "Notification access enabled" else "Notification access required")
                    Text(
                        if (accessGranted) "Microband can read the notifications you choose below."
                        else "Enable Microband in Android's notification-access settings.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(NotificationCategory.entries) { category ->
            Card(shape = RoundedCornerShape(18.dp)) {
                ListItem(
                    headlineContent = { Text(category.label) },
                    trailingContent = {
                        Switch(
                            checked = category.preferenceKey in enabledCategories,
                            onCheckedChange = { onSetCategory(category.preferenceKey, it) },
                        )
                    },
                )
            }
        }
        item {
            if (accessGranted) {
                FilledTonalButton(onClick = onOpenNotificationAccess, modifier = Modifier.fillMaxWidth()) { Text("Manage notification access") }
            } else {
                Button(onClick = onOpenNotificationAccess, modifier = Modifier.fillMaxWidth()) { Text("Open notification access") }
            }
        }
        item {
            Button(onClick = onSendTestNotification, enabled = connected, modifier = Modifier.fillMaxWidth()) {
                Text(if (connected) "Send test notification" else "Connect Band to send a test")
            }
        }
    }
}

private data class ThemePreset(val name: String, val accent: Int)

private val themePresets = listOf(
    ThemePreset("Microsoft blue", 0xFF0078D7.toInt()),
    ThemePreset("Cyan", 0xFF00A4EF.toInt()),
    ThemePreset("Emerald", 0xFF008272.toInt()),
    ThemePreset("Lime", 0xFF6B9F00.toInt()),
    ThemePreset("Orange", 0xFFF7630C.toInt()),
    ThemePreset("Magenta", 0xFFE3008C.toInt()),
    ThemePreset("Violet", 0xFF744DA9.toInt()),
    ThemePreset("Graphite", 0xFF5D5A58.toInt()),
)

@Composable
private fun PersonalizeScreen(
    state: MicrobandUiState,
    onSetThemeColor: (Int) -> Unit,
    onChooseWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = state.connection is BandConnectionState.Connected
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Personalize", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("Make the Me Tile feel like yours.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Theme color", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("The selected accent is expanded into the Band's six-color theme.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        themePresets.forEach { preset ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier.size(52.dp)
                                        .clip(CircleShape)
                                        .background(Color(preset.accent))
                                        .then(
                                            if (state.themeAccent == preset.accent) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                            else Modifier,
                                        )
                                        .clickable(enabled = connected) { onSetThemeColor(preset.accent) }
                                        .semantics { contentDescription = "Use ${preset.name} theme" },
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(preset.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Wallpaper", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Choose a photo. Microband center-crops it to the Band 2's 310 × 128 Me Tile and converts it locally.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onChooseWallpaper,
                        enabled = connected && !state.personalizationBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (state.personalizationBusy) "Preparing wallpaper…" else "Choose wallpaper") }
                    FilledTonalButton(
                        onClick = onClearWallpaper,
                        enabled = connected && !state.personalizationBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Clear wallpaper") }
                    if (!connected) Text("Connect your Band to apply changes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SettingsContainer(
    state: MicrobandUiState,
    onSetProtocolLogging: (Boolean) -> Unit,
    onOpenFirmwareArchive: () -> Unit,
    onChooseFirmwarePackage: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onInspect: () -> Unit,
    onClearLog: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var developerOpen by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (developerOpen) {
        DebugScreen(state, onConnect, onDisconnect, onInspect, onClearLog, onRefresh, { developerOpen = false }, modifier)
    } else {
        SettingsScreen(state, onSetProtocolLogging, onOpenFirmwareArchive, onChooseFirmwarePackage, { developerOpen = true }, modifier)
    }
}

@Composable
private fun SettingsScreen(
    state: MicrobandUiState,
    onSetProtocolLogging: (Boolean) -> Unit,
    onOpenFirmwareArchive: () -> Unit,
    onChooseFirmwarePackage: () -> Unit,
    onOpenDeveloper: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold) }
        item { SectionCard("Local-first", "No analytics, Microsoft account, custom backend, or cloud upload. Health data remains on this device.") }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                ListItem(
                    headlineContent = { Text("Protocol logging") },
                    supportingContent = { Text("Developer-only packet metadata. Off by default.") },
                    trailingContent = { Switch(state.protocolLogging, onCheckedChange = onSetProtocolLogging) },
                )
            }
        }
        item { SectionCard("Health Connect", "Optional export will become available after reliable Band history sync is implemented.") }
        item {
            FirmwareCard(
                (state.connection as? BandConnectionState.Connected)?.device,
                state.firmwarePackageStatus,
                onOpenFirmwareArchive,
                onChooseFirmwarePackage,
            )
        }
        item {
            FilledTonalButton(onClick = onOpenDeveloper, modifier = Modifier.fillMaxWidth()) {
                Text("Open Developer tools")
            }
        }
        item { Text("Microband 0.2.0", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

private data class NewcomerTip(val title: String, val body: String)

private val newcomerTips = listOf(
    NewcomerTip("Wear it snugly", "Keep the sensor flat against your skin, a little above the wrist bone. Snug is good; painfully tight is not."),
    NewcomerTip("Choose your screen side", "Band 2 works with the display inside or outside your wrist. Inside is often easier to glance at and protects the screen."),
    NewcomerTip("Keep the contacts dry", "Dry the Band and charging contacts before charging. Clean contacts gently and avoid leaving the battery completely empty."),
    NewcomerTip("Fix the clock anytime", "After travel or a timezone change, connect Microband and tap Sync time. The app verifies both UTC and displayed local time."),
    NewcomerTip("One companion at a time", "If connection becomes unreliable, close other Band tools and keep the phone nearby before reconnecting."),
)

@Composable
private fun NewcomerTipsCarousel() {
    val pagerState = rememberPagerState(pageCount = { newcomerTips.size })
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Band 2 tips", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        HorizontalPager(
            state = pagerState,
            pageSpacing = 12.dp,
            contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val tip = newcomerTips[page]
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth().height(176.dp),
            ) {
                Column(
                    Modifier.fillMaxSize().padding(22.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Tip ${page + 1} of ${newcomerTips.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .7f))
                    Spacer(Modifier.height(8.dp))
                    Text(tip.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(tip.body, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            newcomerTips.indices.forEach { index ->
                Box(
                    Modifier.padding(horizontal = 3.dp).size(if (index == pagerState.currentPage) 9.dp else 7.dp)
                        .clip(CircleShape)
                        .background(if (index == pagerState.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
    }
}

@Composable
private fun FirmwareCard(
    device: BandDeviceInfo?,
    packageStatus: String?,
    onOpenFirmwareArchive: () -> Unit,
    onChooseFirmwarePackage: () -> Unit,
) {
    val latestKnown = "2.0.5202.0"
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Band firmware", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Installed: ${device?.firmwareVersion ?: "Connect to check"}")
            Text(
                when {
                    device?.firmwareVersion == latestKnown -> "Latest known Band 2 firmware is installed."
                    device?.firmwareVersion != null -> "Latest archived Band 2 firmware: $latestKnown"
                    else -> "Connect to the Band to compare its installed firmware."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = onOpenFirmwareArchive) { Text("Open firmware archive") }
            FilledTonalButton(onClick = onChooseFirmwarePackage) { Text("Validate downloaded package") }
            packageStatus?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Text(
                "Microband validates the exact Band 2 image before use. Flashing remains disabled until battery and recovery safeguards are hardware-tested.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DebugScreen(
    state: MicrobandUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onInspect: () -> Unit,
    onClearLog: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val device = (state.connection as? BandConnectionState.Connected)?.device
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = onBack) { Text("‹ Settings") }
            Text("Band Debug", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("Safe read-only diagnostics", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    DebugValue("Device", device?.bluetoothName ?: "Not connected")
                    DebugValue("Association ID", state.association?.associationId?.toString() ?: "Unavailable")
                    DebugValue("Transport", if (state.connection is BandConnectionState.Connected) "RFCOMM" else "Disconnected")
                    DebugValue("PCB ID", device?.pcbId?.toString() ?: "—")
                    DebugValue("Hardware", when { device == null -> "—"; device.isBand2 -> "Envoy / Band 2"; else -> "Unsupported" })
                    DebugValue("Firmware app", device?.firmwareApplication?.name ?: "—")
                    DebugValue("Firmware", device?.firmwareVersion ?: "—")
                    DebugValue("OOBE complete", device?.oobeComplete?.toString() ?: "—")
                    DebugValue("OOBE stage", device?.oobeStage?.name ?: "—")
                    DebugValue("Band time", device?.bandTime?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) ?: "—")
                    DebugValue("Band local time", device?.bandLocalTime?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) ?: "—")
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConnect) { Text("Connect") }
                FilledTonalButton(onClick = onDisconnect) { Text("Disconnect") }
                FilledTonalButton(onClick = onInspect) { Text("Run safe checks") }
                TextButton(onClick = onRefresh) { Text("Refresh") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Protocol log", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onClearLog) { Text("Clear") }
            }
        }
        if (!state.protocolLogging) item { Text("Enable protocol logging in Settings to record packet metadata.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(state.logs) { entry -> PacketLogRow(entry) }
    }
}

@Composable
private fun DebugValue(label: String, value: String) {
    ListItem(headlineContent = { Text(label) }, trailingContent = { Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis) })
}

@Composable
private fun PacketLogRow(entry: ProtocolPacketLog) {
    Card(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("${entry.direction} • ${entry.payloadLength} bytes", style = MaterialTheme.typography.labelLarge)
            Text(entry.hexPayload.take(96), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            entry.parsedStatus?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun SectionCard(title: String, body: String) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
