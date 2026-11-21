package com.smartjadwal.suara.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smartjadwal.suara.util.AlarmScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            AlarmScheduler.rescheduleAll(context)
        }
    }
}
