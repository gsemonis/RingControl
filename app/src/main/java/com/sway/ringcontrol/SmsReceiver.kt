package com.sway.ringcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (message in messages) {
                val senderNumber = message.displayOriginatingAddress
                if (senderNumber != null) {
                    checkAndOverride(context, senderNumber)
                }
            }
        }
    }

    private fun checkAndOverride(context: Context, senderNumber: String) {
        val sharedPrefs = context.getSharedPreferences("RingControlPrefs", Context.MODE_PRIVATE)
        
        // Optimize: Build lists once
        val whitelistedNums = sharedPrefs.getStringSet("selected_numbers", emptySet()) ?: emptySet()
        val blacklistedNums = sharedPrefs.getStringSet("blacklisted_numbers", emptySet()) ?: emptySet()
        val whitelist = RingControlLogic.buildMatchList(whitelistedNums, sharedPrefs)
        val blacklist = RingControlLogic.buildMatchList(blacklistedNums, sharedPrefs)
        val isGlobalSilence = sharedPrefs.getBoolean("global_silence", false)

        // 1. SILENCE CHECK FIRST
        if (RingControlLogic.shouldSilence(senderNumber, whitelist, blacklist, isGlobalSilence)) {
            // KILL the broadcast if it's blacklisted or global silence is ON
            abortBroadcast()
            return
        }

        val matchedNumber = RingControlLogic.findWhitelistedMatch(senderNumber, whitelist)

        if (matchedNumber != null) {
            val alwaysRing = sharedPrefs.getBoolean("sms_always_ring_$matchedNumber", false)
            val soundUri = sharedPrefs.getString("sms_ring_uri_$matchedNumber", null)
            val vibPattern = sharedPrefs.getString("sms_vib_$matchedNumber", "Default")
            
            val serviceIntent = Intent(context, RingService::class.java).apply {
                putExtra("ringtone_uri", soundUri)
                putExtra("vibration_pattern", vibPattern)
                putExtra("always_ring", alwaysRing)
                putExtra("is_sms", true)
                putExtra("sender_id", matchedNumber)
            }
            context.startForegroundService(serviceIntent)
        }
    }
}
