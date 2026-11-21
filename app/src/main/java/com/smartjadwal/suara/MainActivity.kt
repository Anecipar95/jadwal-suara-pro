package com.smartjadwal.suara

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.smartjadwal.suara.data.ScheduleRepository
import com.smartjadwal.suara.model.*
import com.smartjadwal.suara.service.VoiceAnnouncerService
import com.smartjadwal.suara.util.AlarmScheduler
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlarmScheduler.rescheduleAll(this)

        setContent {
            val darkTheme = isSystemInDarkTheme()
            val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            } else {
                if (darkTheme) darkColorScheme(primary = Color(0xFF60A5FA))
                else lightColorScheme(primary = Color(0xFF1D4ED8))
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SmartJadwalApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartJadwalApp() {
    val context = LocalContext.current
    val repo = remember { ScheduleRepository(context) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var reminders by remember { mutableStateOf(repo.getReminders()) }
    var settings by remember { mutableStateOf(repo.getSettings()) }
    var logs by remember { mutableStateOf(repo.getLogs()) }

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<ReminderItem?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun updateReminders(newList: List<ReminderItem>) {
        reminders = newList
        repo.saveReminders(newList)
        AlarmScheduler.rescheduleAll(context)
    }

    fun updateSettings(newSettings: AppSettings) {
        settings = newSettings
        repo.saveSettings(newSettings)
        AlarmScheduler.rescheduleAll(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Jadwal Suara Pro",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (settings.masterEnabled) "Asisten Suara Aktif" else "Semua Suara Dijeda",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (settings.masterEnabled) Color(0xFF16A34A) else MaterialTheme.colorScheme.error
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        triggerVoiceTest(
                            context,
                            "Tes Suara",
                            "Halo! Sistem pengingat jadwal suara berfungsi dengan baik."
                        )
                    }) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = "Tes Suara")
                    }
                    Switch(
                        checked = settings.masterEnabled,
                        onCheckedChange = { updateSettings(settings.copy(masterEnabled = it)) },
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editingReminder = null
                        showAddEditDialog = true
                    },
                    icon = { Icon(Icons.Default.AddAlarm, contentDescription = null) },
                    text = { Text("Jadwal Baru") }
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                    label = { Text("Jadwal") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.AccessTimeFilled, contentDescription = null) },
                    label = { Text("Jam Berkala") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Tune, contentDescription = null) },
                    label = { Text("Kontrol") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = {
                        logs = repo.getLogs()
                        selectedTab = 3
                    },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text("Riwayat") }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> ScheduleScreen(
                    reminders = reminders,
                    masterEnabled = settings.masterEnabled,
                    onToggleReminder = { item, enabled ->
                        val updated = reminders.map {
                            if (it.id == item.id) it.copy(isEnabled = enabled) else it
                        }
                        updateReminders(updated)
                    },
                    onEditReminder = { item ->
                        editingReminder = item
                        showAddEditDialog = true
                    },
                    onDeleteReminder = { item ->
                        updateReminders(reminders.filterNot { it.id == item.id })
                    },
                    onPreviewVoice = { item ->
                        triggerVoiceTest(context, item.title, item.voiceMessage)
                    }
                )

                1 -> HourlyChimeScreen(
                    settings = settings,
                    onUpdateSettings = ::updateSettings
                )

                2 -> SmartControlScreen(
                    settings = settings,
                    onUpdateSettings = ::updateSettings
                )

                3 -> HistoryScreen(
                    logs = logs,
                    onRefresh = { logs = repo.getLogs() },
                    onClear = {
                        repo.clearLogs()
                        logs = emptyList()
                    }
                )
            }
        }
    }

    if (showAddEditDialog) {
        AddEditReminderDialog(
            initial = editingReminder,
            onDismiss = { showAddEditDialog = false },
            onSave = { savedItem ->
                val updated = if (editingReminder == null) {
                    reminders + savedItem
                } else {
                    reminders.map { if (it.id == savedItem.id) savedItem else it }
                }
                updateReminders(updated.sortedWith(compareBy({ it.hour }, { it.minute })))
                showAddEditDialog = false
            }
        )
    }
}

@Composable
fun ScheduleScreen(
    reminders: List<ReminderItem>,
    masterEnabled: Boolean,
    onToggleReminder: (ReminderItem, Boolean) -> Unit,
    onEditReminder: (ReminderItem) -> Unit,
    onDeleteReminder: (ReminderItem) -> Unit,
    onPreviewVoice: (ReminderItem) -> Unit
) {
    var selectedCategory by remember { mutableStateOf<ScheduleCategory?>(null) }
    val filtered = reminders.filter { selectedCategory == null || it.category == selectedCategory }
    val activeCount = reminders.count { it.isEnabled }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (masterEnabled) "$activeCount Jadwal Suara Aktif" else "Master Kontrol Nonaktif",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pengingat akan dibacakan otomatis sesuai jam & hari yang dipilih.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("Semua (${reminders.size})") }
                )
                ScheduleCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                        label = { Text(cat.label) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Belum ada jadwal pada kategori ini. Tekan '+ Jadwal Baru'.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filtered, key = { it.id }) { item ->
                ReminderCard(
                    item = item,
                    onToggle = { onToggleReminder(item, it) },
                    onEdit = { onEditReminder(item) },
                    onDelete = { onDeleteReminder(item) },
                    onPreview = { onPreviewVoice(item) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}

@Composable
fun ReminderCard(
    item: ReminderItem,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPreview: () -> Unit
) {
    val catColor = Color(item.category.colorHex)
    Card(
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(catColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:%02d", item.hour, item.minute),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        color = catColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = item.category.label,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = catColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Switch(checked = item.isEnabled, onCheckedChange = onToggle)
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${item.voiceMessage}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${formatRepeatLabel(item)} • Ulang ${item.repeatCount}x",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row {
                    IconButton(onClick = onPreview) {
                        Icon(
                            Icons.Default.PlayCircleOutline,
                            contentDescription = "Putar Suara",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Hapus",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HourlyChimeScreen(
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Pengumuman Waktu Berkala",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Membacakan jam secara otomatis setiap 30 atau 60 menit seperti fitur Time Announcement di Shouter Pro.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(shape = RoundedCornerShape(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Aktifkan Pengumuman Jam", fontWeight = FontWeight.Bold)
                    Text(
                        "Otomatis berhenti saat Jam Tenang aktif",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = settings.hourlyChimeEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(hourlyChimeEnabled = it)) }
                )
            }
        }

        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Interval Pengumuman", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = settings.hourlyIntervalMinutes == 30,
                        onClick = { onUpdateSettings(settings.copy(hourlyIntervalMinutes = 30)) },
                        label = { Text("Setiap 30 Menit") }
                    )
                    FilterChip(
                        selected = settings.hourlyIntervalMinutes == 60,
                        onClick = { onUpdateSettings(settings.copy(hourlyIntervalMinutes = 60)) },
                        label = { Text("Setiap 1 Jam") }
                    )
                }

                OutlinedTextField(
                    value = settings.hourlyPrefix,
                    onValueChange = { onUpdateSettings(settings.copy(hourlyPrefix = it)) },
                    label = { Text("Kalimat Awalan Jam") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Button(
                    onClick = {
                        val cal = Calendar.getInstance()
                        val msg = "Selamat sore. ${settings.hourlyPrefix} ${
                            String.format("%02d lewat %02d menit", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                        }."
                        triggerVoiceTest(context, "Tes Jam Berkala", msg)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Dengarkan Contoh Pengumuman Jam")
                }
            }
        }
    }
}

@Composable
fun SmartControlScreen(
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Jam Tenang & Kontrol Cerdas",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // Kartu Jam Tenang
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Jam Tenang (Silent Hours)", fontWeight = FontWeight.Bold)
                        Text(
                            "Aplikasi tidak bersuara pada rentang jam tidur/istirahat",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = settings.silentHoursEnabled,
                        onCheckedChange = { onUpdateSettings(settings.copy(silentHoursEnabled = it)) }
                    )
                }

                if (settings.silentHoursEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, h, m ->
                                        onUpdateSettings(settings.copy(silentStartHour = h, silentStartMinute = m))
                                    },
                                    settings.silentStartHour,
                                    settings.silentStartMinute,
                                    true
                                ).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Mulai: ${String.format("%02d:%02d", settings.silentStartHour, settings.silentStartMinute)}")
                        }
                        OutlinedButton(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, h, m ->
                                        onUpdateSettings(settings.copy(silentEndHour = h, silentEndMinute = m))
                                    },
                                    settings.silentEndHour,
                                    settings.silentEndMinute,
                                    true
                                ).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Selesai: ${String.format("%02d:%02d", settings.silentEndHour, settings.silentEndMinute)}")
                        }
                    }
                }
            }
        }

        // Kartu Kondisi Perangkat
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Aturan Kondisi Perangkat", fontWeight = FontWeight.Bold)

                SettingSwitchRow(
                    title = "Goyang HP untuk Diam (Shake to Mute)",
                    subtitle = "Goyangkan ponsel saat suara dibacakan untuk langsung menghentikan",
                    checked = settings.shakeToMute,
                    onCheckedChange = { onUpdateSettings(settings.copy(shakeToMute = it)) }
                )
                HorizontalDivider()
                SettingSwitchRow(
                    title = "Tetap Bersuara Saat Mode Hening",
                    subtitle = "Abaikan mode silent/getar ponsel untuk pengingat penting",
                    checked = settings.overrideSilentMode,
                    onCheckedChange = { onUpdateSettings(settings.copy(overrideSilentMode = it)) }
                )
                HorizontalDivider()
                SettingSwitchRow(
                    title = "Hanya Saat Pakai Headphone / Bluetooth",
                    subtitle = "Jangan bersuara lewat speaker luar jika tidak memakai headset",
                    checked = settings.headphoneOnly,
                    onCheckedChange = { onUpdateSettings(settings.copy(headphoneOnly = it)) }
                )
                HorizontalDivider()
                SettingSwitchRow(
                    title = "Hanya Saat Layar Mati",
                    subtitle = "Tidak mengganggu dengan suara saat Anda sedang memakai HP",
                    checked = settings.screenOffOnly,
                    onCheckedChange = { onUpdateSettings(settings.copy(screenOffOnly = it)) }
                )
            }
        }

        // Kartu Pengaturan Suara TTS
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Pengaturan Suara TTS", fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = settings.voicePrefix,
                    onValueChange = { onUpdateSettings(settings.copy(voicePrefix = it)) },
                    label = { Text("Awalan Pembacaan Jadwal") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("Saluran Audio", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AudioChannel.entries.forEach { channel ->
                        FilterChip(
                            selected = settings.audioChannel == channel,
                            onClick = { onUpdateSettings(settings.copy(audioChannel = channel)) },
                            label = { Text(channel.label) }
                        )
                    }
                }

                Text("Kecepatan Bicara: ${String.format(Locale.US, "%.1fx", settings.speechRate)}")
                Slider(
                    value = settings.speechRate,
                    onValueChange = { onUpdateSettings(settings.copy(speechRate = it)) },
                    valueRange = 0.6f..1.6f
                )

                Text("Nada Suara (Pitch): ${String.format(Locale.US, "%.1f", settings.speechPitch)}")
                Slider(
                    value = settings.speechPitch,
                    onValueChange = { onUpdateSettings(settings.copy(speechPitch = it)) },
                    valueRange = 0.7f..1.4f
                )
            }
        }
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun HistoryScreen(
    logs: List<AnnouncementLog>,
    onRefresh: () -> Unit,
    onClear: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Riwayat Pengumuman (${logs.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Row {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Segarkan")
                }
                if (logs.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Text("Hapus Semua", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Belum ada riwayat pengumuman suara.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(logs, key = { it.id }) { log ->
                    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(log.title, fontWeight = FontWeight.Bold)
                                Text(
                                    log.status,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(log.spokenText, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                formatter.format(Date(log.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddEditReminderDialog(
    initial: ReminderItem?,
    onDismiss: () -> Unit,
    onSave: (ReminderItem) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var message by remember { mutableStateOf(initial?.voiceMessage ?: "") }
    var hour by remember { mutableIntStateOf(initial?.hour ?: 7) }
    var minute by remember { mutableIntStateOf(initial?.minute ?: 0) }
    var category by remember { mutableStateOf(initial?.category ?: ScheduleCategory.PRIBADI) }
    var repeatMode by remember { mutableStateOf(initial?.repeatMode ?: RepeatMode.DAILY) }
    var selectedDays by remember { mutableStateOf(initial?.selectedDays ?: listOf(1, 2, 3, 4, 5, 6, 7)) }
    var repeatCount by remember { mutableIntStateOf(initial?.repeatCount ?: 2) }
    var speakVoice by remember { mutableStateOf(initial?.speakVoice ?: true) }
    var sendNotif by remember { mutableStateOf(initial?.sendNotification ?: true) }

    val dayLabels = listOf(1 to "Sen", 2 to "Sel", 3 to "Rab", 4 to "Kam", 5 to "Jum", 6 to "Sab", 7 to "Min")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Tambah Jadwal Suara" else "Edit Jadwal Suara") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, m ->
                                hour = h
                                minute = m
                            },
                            hour,
                            minute,
                            true
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AccessTime, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Waktu: ${String.format(Locale.getDefault(), "%02d:%02d", hour, minute)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Judul Jadwal") },
                    placeholder = { Text("Contoh: Minum Obat / Rapat Tim") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Teks yang Dibacakan Suara") },
                    placeholder = { Text("Contoh: Waktunya minum obat setelah makan.") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Kategori", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ScheduleCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }

                Text("Pengulangan Jadwal", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RepeatMode.entries.forEach { mode ->
                        FilterChip(
                            selected = repeatMode == mode,
                            onClick = { repeatMode = mode },
                            label = { Text(mode.label) }
                        )
                    }
                }

                if (repeatMode == RepeatMode.CUSTOM_DAYS) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        dayLabels.forEach { (dayNum, dayText) ->
                            val isSel = selectedDays.contains(dayNum)
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    selectedDays = if (isSel) {
                                        selectedDays - dayNum
                                    } else {
                                        (selectedDays + dayNum).sorted()
                                    }
                                },
                                label = { Text(dayText, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                Text("Jumlah Pengulangan Suara: ${repeatCount}x")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3, 4).forEach { cnt ->
                        FilterChip(
                            selected = repeatCount == cnt,
                            onClick = { repeatCount = cnt },
                            label = { Text("${cnt}x") }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = speakVoice, onCheckedChange = { speakVoice = it })
                    Text("Bacakan dengan Suara (TTS)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = sendNotif, onCheckedChange = { sendNotif = it })
                    Text("Tampilkan Notifikasi Layar")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanTitle = title.trim().ifEmpty { "Pengingat Jadwal" }
                    val cleanMsg = message.trim().ifEmpty { cleanTitle }
                    onSave(
                        ReminderItem(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            title = cleanTitle,
                            voiceMessage = cleanMsg,
                            hour = hour,
                            minute = minute,
                            repeatMode = repeatMode,
                            selectedDays = selectedDays,
                            category = category,
                            isEnabled = initial?.isEnabled ?: true,
                            speakVoice = speakVoice,
                            repeatCount = repeatCount,
                            sendNotification = sendNotif
                        )
                    )
                }
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

private fun formatRepeatLabel(item: ReminderItem): String {
    val names = mapOf(1 to "Sen", 2 to "Sel", 3 to "Rab", 4 to "Kam", 5 to "Jum", 6 to "Sab", 7 to "Min")
    return when (item.repeatMode) {
        RepeatMode.ONCE -> "Sekali saja"
        RepeatMode.DAILY -> "Setiap hari"
        RepeatMode.WEEKDAYS -> "Senin - Jumat"
        RepeatMode.CUSTOM_DAYS -> item.selectedDays.mapNotNull { names[it] }.joinToString(", ")
    }
}

private fun triggerVoiceTest(context: Context, title: String, message: String) {
    val intent = Intent(context, VoiceAnnouncerService::class.java).apply {
        putExtra(VoiceAnnouncerService.EXTRA_TITLE, title)
        putExtra(VoiceAnnouncerService.EXTRA_MESSAGE, message)
        putExtra(VoiceAnnouncerService.EXTRA_REPEAT_COUNT, 1)
        putExtra(VoiceAnnouncerService.EXTRA_SPEAK_VOICE, true)
        putExtra(VoiceAnnouncerService.EXTRA_SEND_NOTIF, false)
        putExtra(VoiceAnnouncerService.EXTRA_IS_TEST, true)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}
