package com.sway.ringcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

class CallReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            if (TelephonyManager.EXTRA_STATE_RINGING == state) {
                if (!incomingNumber.isNullOrEmpty()) {
                    val num: String = incomingNumber
                    val sharedPrefs = context.getSharedPreferences("RingControlPrefs", Context.MODE_PRIVATE)
                    val whitelistedNumbers = sharedPrefs.getStringSet("selected_numbers", emptySet()) ?: emptySet()
                    val blacklistedNumbers = sharedPrefs.getStringSet("blacklisted_numbers", emptySet()) ?: emptySet()
                    val isGlobalSilence = sharedPrefs.getBoolean("global_silence", false)
                    
                    Log.d("RingControl", "Inbound call detected from: $num")

                    // --- SILENCE CHECK ---
                    if (RingControlLogic.shouldSilence(num, whitelistedNumbers, blacklistedNumbers, isGlobalSilence, sharedPrefs)) {
                        Log.d("RingControl", "SILENCING CALL: Suppressing alert for $num")
                        return
                    }

                    checkAndOverride(context, num) 
                } else {
                    Log.d("RingControl", "Inbound call state RINGING but number is null. Skipping logic to avoid false whitelist matches.")
                }
            } else if ((TelephonyManager.EXTRA_STATE_OFFHOOK == state) || (TelephonyManager.EXTRA_STATE_IDLE == state)) {
                context.stopService(Intent(context, RingService::class.java))
                // Safety cleanup to restore audio settings if the service was killed or failed to restore
                RingService.restoreAudioState(context)
            }
        }
    }

    private fun checkAndOverride(context: Context, incomingNumber: String) {
        val sharedPrefs = context.getSharedPreferences("RingControlPrefs", Context.MODE_PRIVATE)
        val selectedNumbers = sharedPrefs.getStringSet("selected_numbers", emptySet()) ?: emptySet()
        
        val matchedNumber = RingControlLogic.findWhitelistedMatch(incomingNumber, selectedNumbers, sharedPrefs)

        if (matchedNumber != null) {
            Log.d("RingControl", "Whitelisted caller confirmed: $matchedNumber. Starting override service.")
            val alwaysRing = sharedPrefs.getBoolean("always_ring_$matchedNumber", false)
            val ringtoneUri = sharedPrefs.getString("ring_uri_$matchedNumber", null)
            val vibPattern = sharedPrefs.getString("vib_$matchedNumber", "Default")
            
            val serviceIntent = Intent(context, RingService::class.java).apply {
                putExtra("ringtone_uri", ringtoneUri)
                putExtra("vibration_pattern", vibPattern)
                putExtra("always_ring", alwaysRing)
                putExtra("is_sms", false)
            }
            context.startForegroundService(serviceIntent)
        } else {
            Log.d("RingControl", "Number $incomingNumber is not in whitelist. Ignoring.")
        }
    }
}
