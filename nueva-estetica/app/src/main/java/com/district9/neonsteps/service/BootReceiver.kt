package com.district9.neonsteps.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms the step sensor after a reboot or app update; both broadcasts may start a foreground service. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> StepCounterService.start(context)
        }
    }
}
