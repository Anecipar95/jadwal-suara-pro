package com.smartjadwal.suara.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.smartjadwal.suara.data.ScheduleRepository
import com.smartjadwal.suara.model.RepeatMode
import com.smartjadwal.suara.service.VoiceAnnouncerService
import com.smartjadwal.suara.util.AlarmScheduler
import java.util.Calendar
import java.util.Locale

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = ScheduleRepository(context)
        val settings = repo.getSettings()
        if (!settings.masterEnabled) return

        val type = intent.getStringExtra(AlarmScheduler.EXTRA_TYPE) ?: AlarmScheduler.TYPE_REMINDER

        if (type == AlarmScheduler.TYPE_HOURLY) {
            if (settings.hourlyChimeEnabled) {
                val cal = Calendar.getInstance()
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val min = cal.get(Calendar.MINUTE)
                val greeting = when (hour) {
                    in 4..10 -> "Selamat pagi."
                    in 11..14 -> "Selamat siang."
                    in 15..17 -> "Selamat sore."
                    else -> "Selamat malam."
                }
                val timeStr = if (min == 0) {
                    String.format(Locale.getDefault(), "%02d tepat", hour)
                } else {
                    String.format(Locale.getDefault(), "%02d lewat %02d menit", hour, min)
                }
                val fullMessage = "$greeting ${settings.hourlyPrefix} $timeStr."

                startVoiceService(
                    context = context,
                    title = "Pengumuman Waktu (${String.format("%02d:%02d", hour, min)})",
                    message = fullMessage,
                    repeatCount = 1,
                    speakVoice = true,
                    sendNotif = false,
                    isHourly = true
                )
                AlarmScheduler.scheduleNextHourlyChime(context, settings.hourlyIntervalMinutes)
            }
            return
        }

        val reminderId = intent.getStringExtra(AlarmScheduler.EXTRA_REMINDER_ID) ?: return
        val title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "Pengingat Jadwal"
        val message = intent.getStringExtra(AlarmScheduler.EXTRA_MESSAGE) ?: title
        val repeatCount = intent.getIntExtra(AlarmScheduler.EXTRA_REPEAT_COUNT, 1)
        val speakVoice = intent.getBooleanExtra(AlarmScheduler.EXTRA_SPEAK_VOICE, true)
        val sendNotif = intent.getBooleanExtra(AlarmScheduler.EXTRA_SEND_NOTIF, true)

        startVoiceService(
            context = context,
            title = title,
            message = message,
            repeatCount = repeatCount,
            speakVoice = speakVoice,
            sendNotif = sendNotif,
            isHourly = false
        )

        // Jadwalkan ulang atau nonaktifkan jika ONCE
        val reminders = repo.getReminders().toMutableList()
        val idx = reminders.indexOfFirst { it.id == reminderId }
        if (idx != -1) {
            val item = reminders[idx]
            if (item.repeatMode == RepeatMode.ONCE) {
                reminders[idx] = item.copy(isEnabled = false)
                repo.saveReminders(reminders)
            } else if (item.isEnabled) {
                AlarmScheduler.scheduleReminder(context, item)
            }
        }
    }

    private fun startVoiceService(
        context: Context,
        title: String,
        message: String,
        repeatCount: Int,
        speakVoice: Boolean,
        sendNotif: Boolean,
        isHourly: Boolean
    ) {
        val serviceIntent = Intent(context, VoiceAnnouncerService::class.java).apply {
            putExtra(VoiceAnnouncerService.EXTRA_TITLE, title)
            putExtra(VoiceAnnouncerService.EXTRA_MESSAGE, message)
            putExtra(VoiceAnnouncerService.EXTRA_REPEAT_COUNT, repeatCount)
            putExtra(VoiceAnnouncerService.EXTRA_SPEAK_VOICE, speakVoice)
            putExtra(VoiceAnnouncerService.EXTRA_SEND_NOTIF, sendNotif)
            putExtra(VoiceAnnouncerService.EXTRA_IS_HOURLY, isHourly)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
