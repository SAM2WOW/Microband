package com.unsame.microband.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stairs
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GolfCourse
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.unsame.microband.R
import com.unsame.microband.band.model.BandConnectionState
import com.unsame.microband.band.model.BandDeviceInfo
import com.unsame.microband.band.model.BandHealthSnapshot
import com.unsame.microband.band.model.BandActivitySummary
import com.unsame.microband.band.model.BandSleepSummary
import com.unsame.microband.band.model.BandTileInfo
import com.unsame.microband.band.model.FirmwareUpdateStage
import com.unsame.microband.band.oobe.BandOobeStep
import com.unsame.microband.data.ProtocolPacketLog
import com.unsame.microband.data.HealthDailyEntity
import com.unsame.microband.data.runSummary
import com.unsame.microband.data.sleepSummary
import com.unsame.microband.data.workoutSummary
import com.unsame.microband.notification.NotificationAppInfo
import androidx.core.graphics.drawable.toBitmap
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Destination(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Rounded.Home),
    Health("Health", Icons.Rounded.Favorite),
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
    onSetNotificationPackage: (String, Boolean) -> Unit,
    onSetAllNotifications: (Boolean) -> Unit,
    onSendTestNotification: () -> Unit,
    onOpenBatteryOptimization: () -> Unit,
    onRefreshHealth: () -> Unit,
    onSetThemeColor: (Int) -> Unit,
    onChooseWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onRefreshTiles: () -> Unit,
    onApplyTiles: (List<BandTileInfo>) -> Unit,
    onStartFirmwareUpdate: () -> Unit,
    onOpenFirmwareArchive: () -> Unit,
    onChooseFirmwarePackage: () -> Unit,
    onSetGeminiEnabled: (Boolean) -> Unit,
    onSaveGeminiKey: (String) -> Unit,
    onClearGeminiKey: () -> Unit,
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 700.dp
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (!expanded) NavigationBar {
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
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (expanded) NavigationRail(Modifier.fillMaxHeight()) {
                    Spacer(Modifier.height(12.dp))
                    destinations.forEachIndexed { index, destination ->
                        NavigationRailItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
                AnimatedContent(targetState = destinations[selected], label = "destination", modifier = Modifier.weight(1f)) { destination ->
                    when (destination) {
                        Destination.Home -> HomeScreen(state, onConnect, onDisconnect, onInspect, onFinishSetup, onSyncClock, onRefreshHealth)
                        Destination.Health -> HealthScreen(state, onRefreshHealth)
                        Destination.Personalize -> PersonalizeScreen(
                    state = state,
                    onSetThemeColor = onSetThemeColor,
                    onChooseWallpaper = onChooseWallpaper,
                    onClearWallpaper = onClearWallpaper,
                    onRefreshTiles = onRefreshTiles,
                    onApplyTiles = onApplyTiles,
                    modifier = Modifier,
                )
                        Destination.Settings -> SettingsContainer(
                    state = state,
                    onSetProtocolLogging = onSetProtocolLogging,
                    onOpenFirmwareArchive = onOpenFirmwareArchive,
                    onChooseFirmwarePackage = onChooseFirmwarePackage,
                    onStartFirmwareUpdate = onStartFirmwareUpdate,
                    onConnect = onConnect,
                    onDisconnect = onDisconnect,
                    onInspect = onInspect,
                    onClearLog = onClearLog,
                    onRefresh = onRefresh,
                    onOpenNotificationAccess = onOpenNotificationAccess,
                    onSetNotificationPackage = onSetNotificationPackage,
                    onSetAllNotifications = onSetAllNotifications,
                    onSendTestNotification = onSendTestNotification,
                    onOpenBatteryOptimization = onOpenBatteryOptimization,
                    onSetGeminiEnabled = onSetGeminiEnabled,
                    onSaveGeminiKey = onSaveGeminiKey,
                    onClearGeminiKey = onClearGeminiKey,
                    modifier = Modifier,
                )
                    }
                }
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
    onRefreshHealth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = state.connection as? BandConnectionState.Connected
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Today", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        }
        item { DeviceHeroCard(state.connection, onConnect, onDisconnect, onSyncClock) }
        item { HealthDashboard(state.healthSnapshot, connected != null, state.healthSyncInProgress, onRefreshHealth) }
        if (connected?.device?.oobeComplete != true) item {
            SetupCard(
                device = connected?.device,
                currentStep = state.oobeStep,
                setupInProgress = state.setupInProgress,
                onInspect = onInspect,
                onFinishSetup = onFinishSetup,
            )
        }
        item { NewcomerTipsCarousel() }
    }
}

@Composable
private fun HealthDashboard(
    snapshot: BandHealthSnapshot?,
    connected: Boolean,
    syncing: Boolean,
    onRefresh: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (snapshot?.daily?.cumulativeSinceReset == true) "BAND STEP COUNTER" else "STEPS TODAY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    snapshot?.stepsToday?.let { String.format(Locale.US, "%,d", it) } ?: "—",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    snapshot?.let { (if (it.daily?.cumulativeSinceReset == true) "Older firmware reports a cumulative total • " else "") + "Last synced ${healthDate(it.syncedAt)}" }
                        ?: "Connect and sync to read today's pedometer total.",
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .72f),
                )
                Button(onClick = onRefresh, enabled = connected && !syncing, modifier = Modifier.fillMaxWidth()) {
                    Text(if (syncing) "Syncing…" else "Sync Band data")
                }
                if (syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CompactMetric("Distance", snapshot?.daily?.distanceCentimeters?.let { String.format(Locale.US, "%.2f km", it / 100_000.0) } ?: "—", Modifier.weight(1f))
            CompactMetric("Sleep", snapshot?.lastSleep?.let { durationText(it.timeAsleepMillis) } ?: "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun CompactMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

private enum class HealthRange(val label: String, val days: Int) { Day("Day", 1), Week("Week", 7), Month("Month", 30) }

@Composable
private fun HealthScreen(state: MicrobandUiState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    var rangeOrdinal by rememberSaveable { mutableIntStateOf(HealthRange.Week.ordinal) }
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val range = HealthRange.entries[rangeOrdinal]
    val history = remember(state.healthHistory, range) {
        val oldest = LocalDate.now().minusDays((range.days - 1).toLong())
        state.healthHistory.filter { runCatching { LocalDate.parse(it.localDate) }.getOrNull()?.let { date -> !date.isBefore(oldest) } == true }.sortedBy { it.localDate }
    }
    val selectedDay = state.healthHistory.firstOrNull { it.localDate == selectedDate }
    val connected = state.connection is BandConnectionState.Connected
    val selectedLocalDate = runCatching { LocalDate.parse(selectedDate) }.getOrDefault(LocalDate.now())
    Column(modifier.fillMaxSize()) {
        DayNavigator(
            date = selectedLocalDate,
            onPrevious = { selectedDate = selectedLocalDate.minusDays(1).toString() },
            onNext = { selectedDate = selectedLocalDate.plusDays(1).coerceAtMost(LocalDate.now()).toString() },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text("Health", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HealthRange.entries.forEach { option ->
                        if (range == option) Button({ rangeOrdinal = option.ordinal }, Modifier.weight(1f)) { Text(option.label) }
                        else FilledTonalButton({ rangeOrdinal = option.ordinal }, Modifier.weight(1f)) { Text(option.label) }
                    }
                }
            }
            item { StepsChart(history, range, selectedDate) { selectedDate = it } }
            item { DailyHealthCard(selectedDate, selectedDay) }
            item {
                Button(onClick = onRefresh, enabled = connected && !state.healthSyncInProgress, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.healthSyncInProgress) "Syncing…" else "Sync Band data")
                }
                if (state.healthSyncInProgress) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            item { ActivitySummaryCard("Run", selectedDay?.runSummary(), showDistance = true) }
            item { ActivitySummaryCard("Exercise", selectedDay?.workoutSummary(), showDistance = false) }
            item { SleepSummaryCard(selectedDay?.sleepSummary()) }
            if (history.isEmpty()) item {
                SectionCard("No trend data", "Connect the Band and sync.")
            }
        }
    }
}

@Composable
private fun DayNavigator(
    date: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious) { Icon(Icons.Rounded.ChevronLeft, "Previous day") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (date == today) "Today" else date.format(DateTimeFormatter.ofPattern("EEEE")), fontWeight = FontWeight.SemiBold)
                Text(date.format(DateTimeFormatter.ofPattern("MMM d, yyyy")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onNext, enabled = date.isBefore(today)) { Icon(Icons.Rounded.ChevronRight, "Next day") }
        }
    }
}

@Composable
private fun HealthValue(label: String, value: String?) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value ?: "Not available", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StepsChart(
    history: List<HealthDailyEntity>,
    range: HealthRange,
    selectedDate: String,
    onSelectDate: (String) -> Unit,
) {
    val slots = remember(range) {
        val today = LocalDate.now()
        (range.days - 1 downTo 0).map { today.minusDays(it.toLong()) }
    }
    val byDate = remember(history) { history.associateBy { it.localDate } }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${range.label} steps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            val total = history.sumOf { it.steps ?: 0 }
            Text(String.format(Locale.US, "%,d total", total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            val barColor = MaterialTheme.colorScheme.primary
            val emptyColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .12f)
            val selectedColor = MaterialTheme.colorScheme.tertiary
            Canvas(
                Modifier.fillMaxWidth().height(150.dp).pointerInput(slots) {
                    detectTapGestures { offset ->
                        val index = (offset.x / (size.width / slots.size)).toInt().coerceIn(0, slots.lastIndex)
                        onSelectDate(slots[index].toString())
                    }
                },
            ) {
                val maximum = slots.maxOfOrNull { byDate[it.toString()]?.steps ?: 0 }?.coerceAtLeast(1) ?: 1
                val gap = size.width / slots.size
                slots.forEachIndexed { index, date ->
                    val value = byDate[date.toString()]?.steps ?: 0
                    val height = if (value > 0) size.height * (value.toFloat() / maximum) else 3f
                    val selected = date.toString() == selectedDate
                    val widthFraction = if (selected) .78f else .64f
                    drawRoundRect(
                        color = if (value > 0) { if (selected) selectedColor else barColor } else emptyColor,
                        topLeft = androidx.compose.ui.geometry.Offset(index * gap + gap * ((1f - widthFraction) / 2f), size.height - height),
                        size = androidx.compose.ui.geometry.Size(gap * widthFraction, height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(if (selected) gap * .34f else gap * .2f),
                    )
                }
            }
            val selected = byDate[selectedDate]
            val selectedLabel = runCatching { LocalDate.parse(selectedDate).format(DateTimeFormatter.ofPattern("MMM d")) }.getOrDefault(selectedDate)
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                Text(
                    "$selectedLabel  •  ${selected?.steps?.let { String.format(Locale.US, "%,d steps", it) } ?: "No data"}",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
private fun DailyHealthCard(date: String, day: HealthDailyEntity?) {
    val title = runCatching {
        LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
    }.getOrDefault(date)
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HealthMetric(Icons.Rounded.DirectionsWalk, "Steps", day?.steps?.let { String.format(Locale.US, "%,d", it) }, Modifier.weight(1f))
                HealthMetric(Icons.Rounded.LocalFireDepartment, "Calories", day?.calories?.let { "$it cal" }, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HealthMetric(Icons.Rounded.Straighten, "Distance", day?.distanceCentimeters?.let { String.format(Locale.US, "%.2f km", it / 100_000.0) }, Modifier.weight(1f))
                HealthMetric(Icons.Rounded.Stairs, "Floors", day?.flightsAscended?.toString(), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HealthMetric(Icons.Rounded.Terrain, "Elevation", day?.elevationGainCentimeters?.let { String.format(Locale.US, "%.0f m", it / 100.0) }, Modifier.weight(1f))
                HealthMetric(Icons.Rounded.WbSunny, "UV", day?.uvExposure?.toString(), Modifier.weight(1f))
            }
            Text(
                day?.let { "Last synced ${healthDate(java.time.Instant.ofEpochMilli(it.syncedAt))}" } ?: "No saved reading for this day",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HealthMetric(icon: ImageVector, label: String, value: String?, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(value ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActivitySummaryCard(title: String, summary: BandActivitySummary?, showDistance: Boolean) {
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (showDistance) Icons.Rounded.DirectionsRun else Icons.Rounded.FitnessCenter, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            Text(summary?.endedAt?.let(::healthDate) ?: "No saved record found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (summary != null) {
                Text(durationText(summary.durationMillis), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (showDistance) HealthMetric(Icons.Rounded.Straighten, "Distance", summary.distanceCentimeters?.let { String.format(Locale.US, "%.2f mi", it / 160_934.4) }, Modifier.weight(1f))
                    HealthMetric(Icons.Rounded.LocalFireDepartment, "Calories", "${summary.calories} cal", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HealthMetric(Icons.Rounded.MonitorHeart, "Average HR", summary.averageHeartRate.takeIf { it > 0 }?.let { "$it bpm" }, Modifier.weight(1f))
                    HealthMetric(Icons.Rounded.Speed, "Maximum HR", summary.maximumHeartRate.takeIf { it > 0 }?.let { "$it bpm" }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SleepSummaryCard(summary: BandSleepSummary?) {
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Sleep", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            Text(summary?.endedAt?.let(::healthDate) ?: "No saved sleep found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (summary != null) {
                val efficiency = if (summary.durationMillis > 0) {
                    (summary.timeAsleepMillis * 100.0 / summary.durationMillis).coerceIn(0.0, 100.0).toInt()
                } else 0
                Text("$efficiency%", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Sleep efficiency", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(progress = { efficiency / 100f }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HealthMetric(Icons.Rounded.Bedtime, "Time asleep", durationText(summary.timeAsleepMillis), Modifier.weight(1f))
                    HealthMetric(Icons.Rounded.Schedule, "Time in bed", durationText(summary.durationMillis), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HealthMetric(Icons.Rounded.Schedule, "Fell asleep in", durationText(summary.timeToFallAsleepMillis), Modifier.weight(1f))
                    HealthMetric(Icons.Rounded.Bedtime, "Wake-ups", summary.timesWokeUp.toString(), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HealthMetric(Icons.Rounded.MonitorHeart, "Resting HR", summary.restingHeartRate.takeIf { it > 0 }?.let { "$it bpm" }, Modifier.weight(1f))
                    HealthMetric(Icons.Rounded.LocalFireDepartment, "Calories", "${summary.calories} cal", Modifier.weight(1f))
                }
                HealthValue("Deep sleep", null)
                Text("Sleep stages are not included in the Band summary currently available to Microband.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun durationText(milliseconds: Long): String {
    val totalMinutes = milliseconds / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun healthDate(instant: java.time.Instant): String =
    DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault()).format(instant)

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
private fun NotificationSettingsSection(
    accessGranted: Boolean,
    batteryOptimizationIgnored: Boolean,
    apps: List<NotificationAppInfo>,
    allEnabled: Boolean,
    disabledPackages: Set<String>,
    connected: Boolean,
    onOpenNotificationAccess: () -> Unit,
    onSetPackage: (String, Boolean) -> Unit,
    onSetAll: (Boolean) -> Unit,
    onSendTestNotification: () -> Unit,
    onOpenBatteryOptimization: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Notification forwarding", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                StatusRow(accessGranted, if (accessGranted) "Access enabled" else "Access required")
                StatusRow(batteryOptimizationIgnored, if (batteryOptimizationIgnored) "Background access enabled" else "Background access restricted")
                if (!accessGranted) Button(onClick = onOpenNotificationAccess, modifier = Modifier.fillMaxWidth()) { Text("Enable access") }
                else FilledTonalButton(onClick = onOpenNotificationAccess, modifier = Modifier.fillMaxWidth()) { Text("Manage access") }
                if (!batteryOptimizationIgnored) FilledTonalButton(onClick = onOpenBatteryOptimization, modifier = Modifier.fillMaxWidth()) { Text("Allow background use") }
            }
        }
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Apps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { onSetAll(true) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("All on")
                    }
                    FilledTonalButton(onClick = { onSetAll(false) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.NotificationsOff, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("All off")
                    }
                }
                Spacer(Modifier.height(6.dp))
                apps.take(if (expanded) apps.size else 5).forEachIndexed { index, app ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 18.dp))
                    ListItem(
                        leadingContent = { NotificationAppIcon(app) },
                        headlineContent = { Text(app.label) },
                        supportingContent = {
                            Text(if (app.notificationCount > 0) "${app.notificationCount} recent" else "Installed")
                        },
                        trailingContent = {
                            Switch(
                                checked = allEnabled && app.packageName !in disabledPackages,
                                onCheckedChange = { onSetPackage(app.packageName, it) },
                            )
                        },
                    )
                }
                if (apps.size > 5) {
                    HorizontalDivider(Modifier.padding(horizontal = 18.dp))
                    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                        Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (expanded) "Show fewer" else "Show ${apps.size - 5} more")
                    }
                }
                if (apps.isEmpty()) Text("No notification apps found", modifier = Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Button(onClick = onSendTestNotification, enabled = connected, modifier = Modifier.fillMaxWidth()) {
            Text(if (connected) "Send test notification" else "Connect Band to test")
        }
    }
}

@Composable
private fun NotificationAppIcon(app: NotificationAppInfo) {
    val context = LocalContext.current
    val bitmap = remember(app.packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(app.packageName).toBitmap(48, 48).asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)))
    } else {
        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

private data class ThemePreset(val name: String, val accent: Int)

private val themePresets = listOf(
    ThemePreset("Red", 0xFFE81123.toInt()),
    ThemePreset("Coral", 0xFFFF6F61.toInt()),
    ThemePreset("Amber", 0xFFFF8C00.toInt()),
    ThemePreset("Gold", 0xFFFFC83D.toInt()),
    ThemePreset("Lime", 0xFF107C10.toInt()),
    ThemePreset("Emerald", 0xFF008272.toInt()),
    ThemePreset("Teal", 0xFF00B7C3.toInt()),
    ThemePreset("Cyan", 0xFF00A4EF.toInt()),
    ThemePreset("Microsoft blue", 0xFF0078D7.toInt()),
    ThemePreset("Indigo", 0xFF4F6BED.toInt()),
    ThemePreset("Violet", 0xFF744DA9.toInt()),
    ThemePreset("Purple", 0xFF881798.toInt()),
    ThemePreset("Magenta", 0xFFE3008C.toInt()),
    ThemePreset("Rose", 0xFFC239B3.toInt()),
    ThemePreset("Brown", 0xFF8E562E.toInt()),
    ThemePreset("Graphite", 0xFF5D5A58.toInt()),
)

@Composable
private fun PersonalizeScreen(
    state: MicrobandUiState,
    onSetThemeColor: (Int) -> Unit,
    onChooseWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onRefreshTiles: () -> Unit,
    onApplyTiles: (List<BandTileInfo>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = state.connection is BandConnectionState.Connected
    var tileDraft by remember { mutableStateOf<List<BandTileInfo>>(emptyList()) }
    LaunchedEffect(connected) { if (connected) onRefreshTiles() }
    LaunchedEffect(state.tiles.installed) { tileDraft = state.tiles.installed }
    var showCustomColor by rememberSaveable { mutableStateOf(false) }
    if (showCustomColor) {
        CustomColorDialog(
            initialAccent = state.themeAccent,
            onDismiss = { showCustomColor = false },
            onApply = {
                showCustomColor = false
                onSetThemeColor(it)
            },
        )
    }
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
                    Text(
                        "Selected: #${String.format(Locale.US, "%06X", state.themeAccent and 0xFFFFFF)}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    FilledTonalButton(
                        onClick = { showCustomColor = true },
                        enabled = connected,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Choose custom color") }
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
        item {
            TileManagerCard(
                connected = connected,
                installed = tileDraft,
                available = (state.tiles.installed + state.tiles.available).distinctBy { it.id },
                capacity = state.tiles.capacity,
                onInstalledChange = { tileDraft = it },
                onRefresh = onRefreshTiles,
                onApply = { onApplyTiles(tileDraft) },
            )
        }
    }
}

@Composable
private fun TileManagerCard(
    connected: Boolean,
    installed: List<BandTileInfo>,
    available: List<BandTileInfo>,
    capacity: Int,
    onInstalledChange: (List<BandTileInfo>) -> Unit,
    onRefresh: () -> Unit,
    onApply: () -> Unit,
) {
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Band tiles", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                if (capacity > 0) "${installed.size} of $capacity tile slots in use" else "Connect to load the Band's tiles",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            installed.forEachIndexed { index, tile ->
                val protected = tile.name.equals("Me", true) || tile.name.equals("Settings", true)
                val label = tileLabel(tile.name)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(tileIcon(tile.name), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(
                        onClick = {
                            val changed = installed.toMutableList()
                            changed.add(index - 1, changed.removeAt(index))
                            onInstalledChange(changed)
                        },
                        enabled = index > 0,
                    ) { Icon(Icons.Rounded.ArrowUpward, "Move $label up") }
                    IconButton(
                        onClick = {
                            val changed = installed.toMutableList()
                            changed.add(index + 1, changed.removeAt(index))
                            onInstalledChange(changed)
                        },
                        enabled = index < installed.lastIndex,
                    ) { Icon(Icons.Rounded.ArrowDownward, "Move $label down") }
                    IconButton(
                        onClick = { onInstalledChange(installed.filterNot { it.id == tile.id }) },
                        enabled = !protected && installed.size > 1,
                    ) { Icon(Icons.Rounded.Remove, "Remove $label") }
                }
            }
            if (available.isNotEmpty()) {
                HorizontalDivider()
                Text("Available tiles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                available.filter { candidate -> installed.none { it.id == candidate.id } }.forEach { tile ->
                    val label = tileLabel(tile.name)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(tileIcon(tile.name), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(
                            onClick = { onInstalledChange(installed + tile) },
                            enabled = capacity > 0 && installed.size < capacity,
                        ) { Icon(Icons.Rounded.Add, "Add $label") }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onRefresh, enabled = connected, modifier = Modifier.weight(1f)) { Text("Reload") }
                Button(
                    onClick = onApply,
                    enabled = connected && installed.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("Apply tiles") }
            }
        }
    }
}

private fun tileLabel(name: String): String = when (name.lowercase()) {
    "sms" -> "Messages"
    "guidedworkout" -> "Guided workout"
    "hike" -> "Explore"
    else -> name
}

private fun tileIcon(name: String): ImageVector = when {
    name.equals("Me", true) -> Icons.Rounded.AccountCircle
    "step" in name.lowercase() -> Icons.Rounded.DirectionsWalk
    "bike" in name.lowercase() -> Icons.Rounded.DirectionsBike
    "run" in name.lowercase() -> Icons.Rounded.DirectionsRun
    "exercise" in name.lowercase() || "workout" in name.lowercase() -> Icons.Rounded.FitnessCenter
    "sleep" in name.lowercase() -> Icons.Rounded.Bedtime
    "heart" in name.lowercase() -> Icons.Rounded.MonitorHeart
    "uv" in name.lowercase() -> Icons.Rounded.WbSunny
    "alarm" in name.lowercase() -> Icons.Rounded.Alarm
    "timer" in name.lowercase() || "stopwatch" in name.lowercase() -> Icons.Rounded.Timer
    "call" in name.lowercase() -> Icons.Rounded.Call
    "message" in name.lowercase() || "text" in name.lowercase() || name.equals("SMS", true) -> Icons.Rounded.Mail
    "mail" in name.lowercase() || "email" in name.lowercase() -> Icons.Rounded.Email
    "calendar" in name.lowercase() -> Icons.Rounded.CalendarMonth
    "weather" in name.lowercase() -> Icons.Rounded.Cloud
    "finance" in name.lowercase() || "stock" in name.lowercase() -> Icons.Rounded.ShowChart
    "starbucks" in name.lowercase() -> Icons.Rounded.LocalCafe
    "cortana" in name.lowercase() -> Icons.Rounded.Mic
    "explore" in name.lowercase() || "hike" in name.lowercase() -> Icons.Rounded.Explore
    "golf" in name.lowercase() -> Icons.Rounded.GolfCourse
    "map" in name.lowercase() -> Icons.Rounded.Place
    "setting" in name.lowercase() -> Icons.Rounded.Settings
    else -> Icons.Rounded.Favorite
}

@Composable
private fun CustomColorDialog(
    initialAccent: Int,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit,
) {
    val initialHsv = remember(initialAccent) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialAccent, it) }
    }
    var hue by rememberSaveable { mutableFloatStateOf(initialHsv[0]) }
    var saturation by rememberSaveable { mutableFloatStateOf(initialHsv[1]) }
    var brightness by rememberSaveable { mutableFloatStateOf(initialHsv[2]) }
    val color = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom theme color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(18.dp)).background(Color(color)),
                )
                Text("#${String.format(Locale.US, "%06X", color and 0xFFFFFF)}", fontFamily = FontFamily.Monospace)
                Text("Hue", style = MaterialTheme.typography.labelLarge)
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                Text("Saturation", style = MaterialTheme.typography.labelLarge)
                Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..1f)
                Text("Brightness", style = MaterialTheme.typography.labelLarge)
                Slider(value = brightness, onValueChange = { brightness = it }, valueRange = 0.1f..1f)
            }
        },
        confirmButton = { TextButton(onClick = { onApply(color) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SettingsContainer(
    state: MicrobandUiState,
    onSetProtocolLogging: (Boolean) -> Unit,
    onOpenFirmwareArchive: () -> Unit,
    onChooseFirmwarePackage: () -> Unit,
    onStartFirmwareUpdate: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onInspect: () -> Unit,
    onClearLog: () -> Unit,
    onRefresh: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onSetNotificationPackage: (String, Boolean) -> Unit,
    onSetAllNotifications: (Boolean) -> Unit,
    onSendTestNotification: () -> Unit,
    onOpenBatteryOptimization: () -> Unit,
    onSetGeminiEnabled: (Boolean) -> Unit,
    onSaveGeminiKey: (String) -> Unit,
    onClearGeminiKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var developerOpen by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (developerOpen) {
        DebugScreen(state, onConnect, onDisconnect, onInspect, onClearLog, onRefresh, { developerOpen = false }, modifier)
    } else {
        SettingsScreen(
            state, onSetProtocolLogging, onOpenFirmwareArchive, onChooseFirmwarePackage, onStartFirmwareUpdate,
            onOpenNotificationAccess, onSetNotificationPackage, onSetAllNotifications, onSendTestNotification, onOpenBatteryOptimization,
            onSetGeminiEnabled, onSaveGeminiKey, onClearGeminiKey,
            { developerOpen = true }, modifier,
        )
    }
}

@Composable
private fun SettingsScreen(
    state: MicrobandUiState,
    onSetProtocolLogging: (Boolean) -> Unit,
    onOpenFirmwareArchive: () -> Unit,
    onChooseFirmwarePackage: () -> Unit,
    onStartFirmwareUpdate: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onSetNotificationPackage: (String, Boolean) -> Unit,
    onSetAllNotifications: (Boolean) -> Unit,
    onSendTestNotification: () -> Unit,
    onOpenBatteryOptimization: () -> Unit,
    onSetGeminiEnabled: (Boolean) -> Unit,
    onSaveGeminiKey: (String) -> Unit,
    onClearGeminiKey: () -> Unit,
    onOpenDeveloper: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold) }
        item {
            NotificationSettingsSection(
                state.notificationAccessGranted,
                state.batteryOptimizationIgnored,
                state.notificationApps,
                state.allNotificationsEnabled,
                state.disabledNotificationPackages,
                state.connection is BandConnectionState.Connected,
                onOpenNotificationAccess,
                onSetNotificationPackage,
                onSetAllNotifications,
                onSendTestNotification,
                onOpenBatteryOptimization,
            )
        }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                ListItem(
                    headlineContent = { Text("Protocol logging") },
                    supportingContent = { Text("Developer-only packet metadata. Off by default.") },
                    trailingContent = { Switch(state.protocolLogging, onCheckedChange = onSetProtocolLogging) },
                )
            }
        }
        item {
            var apiKey by rememberSaveable { mutableStateOf("") }
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                        Column(Modifier.weight(1f)) {
                            Text("Gemini on Cortana", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (state.bandReplyServiceConnected) "Band voice channel connected" else "Connect the Band to use voice",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.geminiAssistantEnabled,
                            onCheckedChange = onSetGeminiEnabled,
                            enabled = state.geminiConfigured,
                        )
                    }
                    Text(
                        "Experimental. Cortana microphone audio is sent to the Google Gemini API only when you invoke it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!state.geminiConfigured) {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("Gemini API key") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = { onSaveGeminiKey(apiKey); apiKey = "" }, enabled = apiKey.isNotBlank()) {
                            Text("Save and enable")
                        }
                    } else {
                        TextButton(onClick = onClearGeminiKey) { Text("Remove API key") }
                    }
                }
            }
        }
        item {
            FirmwareCard(
                (state.connection as? BandConnectionState.Connected)?.device,
                state.firmwarePackageStatus,
                state.firmwareUpdate,
                onOpenFirmwareArchive,
                onChooseFirmwarePackage,
                onStartFirmwareUpdate,
            )
        }
        item {
            FilledTonalButton(onClick = onOpenDeveloper, modifier = Modifier.fillMaxWidth()) {
                Text("Open Developer tools")
            }
        }
        item { Text("Microband 0.8.0", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

private data class NewcomerTip(val title: String, val body: String)

private val newcomerTips = listOf(
    NewcomerTip("Wear it snugly", "Place the sensor flat against your skin, just above the wrist bone."),
    NewcomerTip("Choose your screen side", "Wear the display inside or outside your wrist. Change orientation in Band Settings."),
    NewcomerTip("Keep contacts dry", "Dry the Band and charging contacts before charging."),
    NewcomerTip("Sync after travel", "Connect Microband and tap Sync time after a timezone change."),
    NewcomerTip("Connection trouble", "Close other Band tools, keep the phone nearby, then reconnect."),
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
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(22.dp),
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
    updateStatus: com.unsame.microband.band.model.FirmwareUpdateStatus,
    onOpenFirmwareArchive: () -> Unit,
    onChooseFirmwarePackage: () -> Unit,
    onStartFirmwareUpdate: () -> Unit,
) {
    val latestKnown = "2.0.5202.0"
    var showWarning by rememberSaveable { mutableStateOf(false) }
    if (showWarning) {
        AlertDialog(
            onDismissRequest = { showWarning = false },
            title = { Text("Firmware update warning") },
            text = {
                Text("A firmware update can permanently damage or brick the Band if it is interrupted. Keep the Band charging, keep the phone nearby, and do not close Microband. You accept responsibility for any damage. Microband is community software and is not affiliated with Microsoft.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showWarning = false
                    onStartFirmwareUpdate()
                }) { Text("I understand — update") }
            },
            dismissButton = { TextButton(onClick = { showWarning = false }) { Text("Cancel") } },
        )
    }
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
            if (updateStatus.stage != FirmwareUpdateStage.Idle) {
                Text(updateStatus.message, color = if (updateStatus.stage == FirmwareUpdateStage.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                if (updateStatus.isRunning) LinearProgressIndicator(
                    progress = { updateStatus.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(
                onClick = { showWarning = true },
                enabled = device != null && device.firmwareVersion != latestKnown && !updateStatus.isRunning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        updateStatus.isRunning -> "Updating • ${updateStatus.percent}%"
                        device?.firmwareVersion == latestKnown -> "Latest firmware installed"
                        device == null -> "Connect Band to update"
                        else -> "Download and install $latestKnown"
                    },
                )
            }
            Text(
                "Microband downloads the archived image, verifies its exact size and SHA-256 checksum, requires at least 50% battery, and verifies the Band after restart.",
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
