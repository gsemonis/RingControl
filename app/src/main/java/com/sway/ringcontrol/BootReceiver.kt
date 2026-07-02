package com.sway.ringcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receiver that ensures audio settings are restored even if the phone restarts
 * during an active alert override.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("RingControl", "Phone rebooted. Checking for stuck audio overrides...")
            RingService.restoreAudioState(context)
        }
    }
}
