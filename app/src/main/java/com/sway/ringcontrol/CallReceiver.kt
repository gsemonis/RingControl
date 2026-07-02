package com.sway.ringcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Receiver that monitors call state changes.
 * Used primarily for cleaning up alerts and restoring audio state when a call ends.
 * Ringing logic is now handled in CallScreeningServiceImpl for better reliability.
 */
class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)

            Log.d("RingControl", "Call state changed: $state")

            if ((TelephonyManager.EXTRA_STATE_OFFHOOK == state) || (TelephonyManager.EXTRA_STATE_IDLE == state)) {
                // Stop the override service if it's running
                context.stopService(Intent(context, RingService::class.java))
                
                // Safety cleanup to restore audio settings if the service was killed or failed to restore
                RingService.restoreAudioState(context)
            }
        }
    }
}
