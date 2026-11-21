package com.smartjadwal.suara.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.smartjadwal.suara.data.ScheduleRepository
import com.smartjadwal.suara.model.ReminderItem
import com.smartjadwal.suara.model.RepeatMode
import com.smartjadwal.suara.receiver.ReminderReceiver
import java.util.Calendar

object AlarmScheduler {

    const val EXTRA_TYPE = "extra_type"
    const val TYPE_REMINDER = "type_reminder"
    const val TYPE_HOURLY = "type_hourly"

    const val EXTRA_REMINDER_ID = "extra_reminder_id"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_MESSAGE = "extra_message"
    const val EXTRA_REPEAT_COUNT = "extra_repeat_count"
    const val EXTRA_SPEAK_VOICE = "extra_speak_voice"
    const val EXTRA_SEND_NOTIF = "extra_send_notif"

    fun rescheduleAll(context: Context) {
        val repo = ScheduleRepository(context)
        val settings = repo.getSettings()
        val reminders = repo.getReminders()

        reminders.forEach { reminder ->
            cancelReminder(context, reminder.id)
            if (settings.masterEnabled && reminder.isEnabled) {
                scheduleReminder(context, reminder)
            }
        }

        cancelHourlyChime(context)
        if (settings.masterEnabled && settings.hourlyChimeEnabled) {
            scheduleNextHourlyChime(context, settings.hourlyIntervalMinutes)
        }
    }

    fun scheduleReminder(context: Context, reminder: ReminderItem) {
        val nextTriggerMillis = calculateNextTriggerTime(reminder) ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_TYPE, TYPE_REMINDER)
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_TITLE, reminder.title)
            putExtra(EXTRA_MESSAGE, reminder.voiceMessage)
            putExtra(EXTRA_REPEAT_COUNT, reminder.repeatCount)
            putExtra(EXTRA_SPEAK_VOICE, reminder.speakVoice)
            putExtra(EXTRA_SEND_NOTIF, reminder.sendNotification)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setExactAlarmSafe(alarmManager, nextTriggerMillis, pendingIntent)
    }

    fun cancelReminder(context: Context, reminderId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun scheduleNextHourlyChime(context: Context, intervalMinutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (intervalMinutes == 30) {
                if (now.get(Calendar.MINUTE) < 30) {
                    set(Calendar.MINUTE, 30)
                } else {
                    set(Calendar.MINUTE, 0)
                    add(Calendar.HOUR_OF_DAY, 1)
                }
            } else {
                set(Calendar.MINUTE, 0)
                add(Calendar.HOUR_OF_DAY, 1)
            }
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_TYPE, TYPE_HOURLY)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            999888,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setExactAlarmSafe(alarmManager, next.timeInMillis, pendingIntent)
    }

    fun cancelHourlyChime(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            999888,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun setExactAlarmSafe(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun calculateNextTriggerTime(reminder: ReminderItem): Long? {
        val now = Calendar.getInstance()
        val candidate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (reminder.repeatMode == RepeatMode.ONCE || reminder.repeatMode == RepeatMode.DAILY) {
            if (candidate.timeInMillis <= now.timeInMillis) {
                candidate.add(Calendar.DAY_OF_YEAR, 1)
            }
            return candidate.timeInMillis
        }

        val validDays = if (reminder.repeatMode == RepeatMode.WEEKDAYS) {
            listOf(1, 2, 3, 4, 5)
        } else {
            reminder.selectedDays.ifEmpty { listOf(1, 2, 3, 4, 5, 6, 7) }
        }

        for (offset in 0..7) {
            val check = (candidate.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, offset)
            }
            if (check.timeInMillis <= now.timeInMillis) continue
            // Calendar.DAY_OF_WEEK: Sunday=1, Monday=2 .. Saturday=7
            val calDow = check.get(Calendar.DAY_OF_WEEK)
            val isoDay = if (calDow == Calendar.SUNDAY) 7 else calDow - 1
            if (validDays.contains(isoDay)) {
                return check.timeInMillis
            }
        }
        return null
    }
}
