package com.sway.ringcontrol

import android.content.Intent
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

/**
 * Service that allows the app to intercept and block calls before the phone rings.
 * Also used as a reliable way to detect whitelisted callers when BroadcastReceiver fails.
 */
class CallScreeningServiceImpl : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val incomingNumber = callDetails.handle?.schemeSpecificPart
        val sharedPrefs = getSharedPreferences("RingControlPrefs", MODE_PRIVATE)
        
        if (incomingNumber.isNullOrEmpty()) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val whitelistedNumbers = sharedPrefs.getStringSet("selected_numbers", emptySet()) ?: emptySet()
        val blacklistedNumbers = sharedPrefs.getStringSet("blacklisted_numbers", emptySet()) ?: emptySet()

        // 1. Blacklist Check (Silencing)
        val isBlacklisted = RingControlLogic.findWhitelistedMatch(incomingNumber, blacklistedNumbers, sharedPrefs) != null
        if (isBlacklisted) {
            Log.d("RingControl", "SCREENING: Blocking blacklisted call from $incomingNumber")
            val response = CallResponse.Builder().apply {
                setDisallowCall(true)
                setRejectCall(true)
                setSkipNotification(true)
            }.build()
            respondToCall(callDetails, response)
            return
        }

        // 2. Whitelist Check (Ringing override)
        val matchedNumber = RingControlLogic.findWhitelistedMatch(incomingNumber, whitelistedNumbers, sharedPrefs)
        if (matchedNumber != null) {
            Log.d("RingControl", "SCREENING: Whitelisted caller detected: $matchedNumber")
            val alwaysRing = sharedPrefs.getBoolean("always_ring_$matchedNumber", false)
            val ringtoneUri = sharedPrefs.getString("ring_uri_$matchedNumber", null)
            val vibPattern = sharedPrefs.getString("vib_$matchedNumber", "Default")
            
            val serviceIntent = Intent(this, RingService::class.java).apply {
                putExtra("ringtone_uri", ringtoneUri)
                putExtra("vibration_pattern", vibPattern)
                putExtra("always_ring", alwaysRing)
                putExtra("is_sms", false)
            }
            startForegroundService(serviceIntent)
        }

        // Allow all other calls to proceed normally (system will handle ringing based on user settings)
        respondToCall(callDetails, CallResponse.Builder().build())
    }
}
