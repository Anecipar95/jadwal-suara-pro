package com.smartjadwal.suara.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.smartjadwal.suara.MainActivity
import com.smartjadwal.suara.R
import com.smartjadwal.suara.data.ScheduleRepository
import com.smartjadwal.suara.model.AnnouncementLog
import com.smartjadwal.suara.model.AppSettings
import com.smartjadwal.suara.model.AudioChannel
import java.util.Calendar
import java.util.Locale
import kotlin.math.sqrt

class VoiceAnnouncerService : Service(), TextToSpeech.OnInitListener, SensorEventListener {

    companion object {
        const val CHANNEL_ID = "smart_jadwal_voice_channel"
        const val ALERT_CHANNEL_ID = "smart_jadwal_alert_channel"
        const val ACTION_STOP = "com.smartjadwal.suara.ACTION_STOP"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_REPEAT_COUNT = "extra_repeat_count"
        const val EXTRA_SPEAK_VOICE = "extra_speak_voice"
        const val EXTRA_SEND_NOTIF = "extra_send_notif"
        const val EXTRA_IS_HOURLY = "extra_is_hourly"
        const val EXTRA_IS_TEST = "extra_is_test"
    }

    private var tts: TextToSpeech? = null
    private var sensorManager: SensorManager? = null
    private var lastShakeTime = 0L

    private var pendingText: String = ""
    private var remainingRepeats: Int = 1
    private var currentSettings: AppSettings = AppSettings()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSpeakingAndSelf()
            return START_NOT_STICKY
        }

        val repo = ScheduleRepository(this)
        currentSettings = repo.getSettings()

        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Pengingat Jadwal"
        val message = intent?.getStringExtra(EXTRA_MESSAGE) ?: title
        val repeatCount = intent?.getIntExtra(EXTRA_REPEAT_COUNT, 1) ?: 1
        val speakVoice = intent?.getBooleanExtra(EXTRA_SPEAK_VOICE, true) ?: true
        val sendNotif = intent?.getBooleanExtra(EXTRA_SEND_NOTIF, true) ?: true
        val isHourly = intent?.getBooleanExtra(EXTRA_IS_HOURLY, false) ?: false
        val isTest = intent?.getBooleanExtra(EXTRA_IS_TEST, false) ?: false

        // Tampilkan foreground notification wajib
        val stopIntent = Intent(this, VoiceAnnouncerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 101, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPendingIntent = PendingIntent.getActivity(
            this, 102, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fgNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Hentikan Suara", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        startForeground(2001, fgNotification)

        if (sendNotif && !isTest) {
            postAlertNotification(title, message, openPendingIntent)
        }

        // Cek kondisi Smart Muting kecuali sedang Tes Suara manual
        if (!isTest) {
            val muteReason = checkMuteConditions(currentSettings)
            if (muteReason != null) {
                repo.addLog(
                    AnnouncementLog(
                        title = title,
                        spokenText = message,
                        status = "Dilewati ($muteReason)"
                    )
                )
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val fullSpokenText = if (isHourly || isTest) {
            message
        } else {
            "${currentSettings.voicePrefix} $title. $message"
        }

        if (!isTest) {
            repo.addLog(
                AnnouncementLog(
                    title = title,
                    spokenText = fullSpokenText,
                    status = if (speakVoice) "Dibacakan" else "Notifikasi Saja"
                )
            )
        }

        if (!speakVoice) {
            stopSelf()
            return START_NOT_STICKY
        }

        pendingText = fullSpokenText
        remainingRepeats = repeatCount.coerceIn(1, 5)

        if (currentSettings.shakeToMute) {
            registerShakeSensor()
        }

        tts?.shutdown()
        tts = TextToSpeech(this, this)

        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = tts ?: return
            val idLocale = Locale("id", "ID")
            val langResult = engine.setLanguage(idLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                langResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                engine.setLanguage(Locale.getDefault())
            }

            engine.setSpeechRate(currentSettings.speechRate)
            engine.setPitch(currentSettings.speechPitch)

            val usage = when (currentSettings.audioChannel) {
                AudioChannel.ALARM -> AudioAttributes.USAGE_ALARM
                AudioChannel.MEDIA -> AudioAttributes.USAGE_MEDIA
                AudioChannel.RINGTONE -> AudioAttributes.USAGE_NOTIFICATION_RINGTONE
            }
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )

            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    remainingRepeats--
                    if (remainingRepeats > 0) {
                        speakOnce(engine)
                    } else {
                        stopSpeakingAndSelf()
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    stopSpeakingAndSelf()
                }
            })

            speakOnce(engine)
        } else {
            stopSpeakingAndSelf()
        }
    }

    private fun speakOnce(engine: TextToSpeech) {
        val streamType = when (currentSettings.audioChannel) {
            AudioChannel.ALARM -> AudioManager.STREAM_ALARM
            AudioChannel.MEDIA -> AudioManager.STREAM_MUSIC
            AudioChannel.RINGTONE -> AudioManager.STREAM_RING
        }
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, streamType)
        }
        engine.speak(pendingText, TextToSpeech.QUEUE_FLUSH, params, "utt_${System.currentTimeMillis()}")
    }

    private fun checkMuteConditions(settings: AppSettings): String? {
        // 1. Cek Jam Tenang (Silent Hours)
        if (settings.silentHoursEnabled && isWithinSilentHours(settings)) {
            return "Jam Tenang"
        }

        // 2. Cek Mode Silent / Getar
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (!settings.overrideSilentMode && audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
            return "Mode Hening"
        }

        // 3. Cek Hanya Headphone / Bluetooth
        if (settings.headphoneOnly && !isHeadsetConnected(audioManager)) {
            return "Tanpa Headphone"
        }

        // 4. Cek Hanya Saat Layar Mati
        if (settings.screenOffOnly) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (powerManager.isInteractive) {
                return "Layar Menyala"
            }
        }

        return null
    }

    private fun isWithinSilentHours(settings: AppSettings): Boolean {
        val cal = Calendar.getInstance()
        val nowMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMins = settings.silentStartHour * 60 + settings.silentStartMinute
        val endMins = settings.silentEndHour * 60 + settings.silentEndMinute

        return if (startMins <= endMins) {
            nowMins in startMins until endMins
        } else {
            nowMins >= startMins || nowMins < endMins
        }
    }

    private fun isHeadsetConnected(audioManager: AudioManager): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }

    private fun registerShakeSensor() {
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accel != null) {
            sensorManager?.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val gForce = sqrt((x * x + y * y + z * z).toDouble()) / SensorManager.GRAVITY_EARTH
        if (gForce > 2.3) {
            val now = System.currentTimeMillis()
            if (now - lastShakeTime > 500) {
                lastShakeTime = now
                stopSpeakingAndSelf()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun stopSpeakingAndSelf() {
        try {
            sensorManager?.unregisterListener(this)
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun postAlertNotification(title: String, message: String, intent: PendingIntent) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify((System.currentTimeMillis() % 10000).toInt(), notif)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Layanan Suara Pengingat",
                NotificationManager.IMPORTANCE_LOW
            )
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Notifikasi Jadwal Harian",
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(serviceChannel)
            nm.createNotificationChannel(alertChannel)
        }
    }

    override fun onDestroy() {
        try {
            sensorManager?.unregisterListener(this)
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
