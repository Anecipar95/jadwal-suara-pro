package com.smartjadwal.suara.model

import java.util.UUID

enum class RepeatMode(val label: String) {
    ONCE("Sekali Saja"),
    DAILY("Setiap Hari"),
    WEEKDAYS("Senin - Jumat"),
    CUSTOM_DAYS("Pilih Hari Tertentu")
}

enum class ScheduleCategory(val label: String, val colorHex: Long) {
    KERJA("Kerja & Bisnis", 0xFF2563EB),
    IBADAH("Ibadah", 0xFF059669),
    KESEHATAN("Kesehatan & Obat", 0xFFDC2626),
    PRIBADI("Pribadi & Keluarga", 0xFF7C3AED),
    LAINNYA("Lainnya", 0xFFD97706)
}

enum class AudioChannel(val label: String) {
    ALARM("Alarm (Prioritas Tinggi)"),
    MEDIA("Media / Musik"),
    RINGTONE("Nada Dering")
}

data class ReminderItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val voiceMessage: String,
    val hour: Int,
    val minute: Int,
    val repeatMode: RepeatMode = RepeatMode.DAILY,
    val selectedDays: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7), // 1=Senin .. 7=Minggu
    val category: ScheduleCategory = ScheduleCategory.PRIBADI,
    val isEnabled: Boolean = true,
    val speakVoice: Boolean = true,
    val repeatCount: Int = 2,
    val sendNotification: Boolean = true
)

data class AnnouncementLog(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val spokenText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "Dibacakan"
)

data class AppSettings(
    val masterEnabled: Boolean = true,
    val voicePrefix: String = "Pengingat jadwal Anda:",
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val audioChannel: AudioChannel = AudioChannel.ALARM,
    // Kontrol Cerdas (Smart Muting)
    val headphoneOnly: Boolean = false,
    val screenOffOnly: Boolean = false,
    val overrideSilentMode: Boolean = true,
    val shakeToMute: Boolean = true,
    // Jam Tenang (Silent Hours)
    val silentHoursEnabled: Boolean = true,
    val silentStartHour: Int = 22,
    val silentStartMinute: Int = 0,
    val silentEndHour: Int = 5,
    val silentEndMinute: Int = 0,
    // Pengumuman Waktu Berkala (Hourly / Interval Chime)
    val hourlyChimeEnabled: Boolean = false,
    val hourlyIntervalMinutes: Int = 60, // 30 atau 60 menit
    val hourlyPrefix: String = "Info waktu, sekarang pukul"
)
