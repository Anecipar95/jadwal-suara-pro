package com.smartjadwal.suara.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.smartjadwal.suara.model.AnnouncementLog
import com.smartjadwal.suara.model.AppSettings
import com.smartjadwal.suara.model.ReminderItem
import com.smartjadwal.suara.model.RepeatMode
import com.smartjadwal.suara.model.ScheduleCategory

class ScheduleRepository(context: Context) {
    private val prefs = context.getSharedPreferences("smart_jadwal_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getReminders(): List<ReminderItem> {
        val json = prefs.getString("reminders_list", null)
        if (json == null) {
            val defaults = defaultReminders()
            saveReminders(defaults)
            return defaults
        }
        val type = object : TypeToken<List<ReminderItem>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveReminders(list: List<ReminderItem>) {
        prefs.edit().putString("reminders_list", gson.toJson(list)).apply()
    }

    fun getSettings(): AppSettings {
        val json = prefs.getString("app_settings", null) ?: return AppSettings()
        return try {
            gson.fromJson(json, AppSettings::class.java) ?: AppSettings()
        } catch (e: Exception) {
            AppSettings()
        }
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit().putString("app_settings", gson.toJson(settings)).apply()
    }

    fun getLogs(): List<AnnouncementLog> {
        val json = prefs.getString("announcement_logs", null) ?: return emptyList()
        val type = object : TypeToken<List<AnnouncementLog>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addLog(log: AnnouncementLog) {
        val current = getLogs().toMutableList()
        current.add(0, log)
        val trimmed = current.take(100)
        prefs.edit().putString("announcement_logs", gson.toJson(trimmed)).apply()
    }

    fun clearLogs() {
        prefs.edit().remove("announcement_logs").apply()
    }

    private fun defaultReminders(): List<ReminderItem> = listOf(
        ReminderItem(
            title = "Minum Air Putih & Peregangan",
            voiceMessage = "Waktunya minum segelas air putih dan peregangan ringan selama 5 menit.",
            hour = 8,
            minute = 0,
            repeatMode = RepeatMode.DAILY,
            category = ScheduleCategory.KESEHATAN,
            repeatCount = 2
        ),
        ReminderItem(
            title = "Mulai Pekerjaan & Cek Target Harian",
            voiceMessage = "Selamat pagi, waktunya memulai pekerjaan hari ini dan memeriksa daftar target.",
            hour = 9,
            minute = 0,
            repeatMode = RepeatMode.WEEKDAYS,
            selectedDays = listOf(1, 2, 3, 4, 5),
            category = ScheduleCategory.KERJA,
            repeatCount = 2
        ),
        ReminderItem(
            title = "Istirahat Siang & Makan",
            voiceMessage = "Sudah jam dua belas siang. Waktunya istirahat sejenak dan makan siang.",
            hour = 12,
            minute = 0,
            repeatMode = RepeatMode.DAILY,
            category = ScheduleCategory.PRIBADI,
            repeatCount = 1
        )
    )
}
