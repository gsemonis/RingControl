package com.sway.ringcontrol

import android.content.Intent
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection
import android.util.Log

/**
 * Service that allows the app to intercept and block calls before the phone rings.
 * This is the primary engine for call detection and override in RingControl.
 */
class CallScreeningServiceImpl : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val incomingNumber = callDetails.handle?.schemeSpecificPart
        val sharedPrefs = getSharedPreferences("RingControlPrefs", MODE_PRIVATE)
        
        // --- SPAM DETECTION ---
        val verificationStatus = callDetails.callerNumberVerificationStatus
        val isSpam = verificationStatus == Connection.VERIFICATION_STATUS_FAILED
        val blockSpamEnabled = sharedPrefs.getBoolean("block_spam", true)

        if (isSpam && blockSpamEnabled) {
            Log.d("RingControl", "SCREENING: Rejecting spam from $incomingNumber")
            val spamResponse = CallResponse.Builder().apply {
                setDisallowCall(true)
                setRejectCall(true)
                setSkipNotification(true)
            }.build()
            respondToCall(callDetails, spamResponse)
            return
        }

        // Prefetch matching data for performance
        val whitelistedNums = sharedPrefs.getStringSet("selected_numbers", emptySet()) ?: emptySet()
        val blacklistedNums = sharedPrefs.getStringSet("blacklisted_numbers", emptySet()) ?: emptySet()
        val whitelist = RingControlLogic.buildMatchList(whitelistedNums, sharedPrefs)
        val blacklist = RingControlLogic.buildMatchList(blacklistedNums, sharedPrefs)
        val isGlobalSilence = sharedPrefs.getBoolean("global_silence", false)

        // 1. Silencing Logic
        val shouldSilence = RingControlLogic.shouldSilence(incomingNumber, whitelist, blacklist, isGlobalSilence)
        if (shouldSilence) {
            Log.d("RingControl", "SCREENING: Silencing call from $incomingNumber")
            val silenceResponse = CallResponse.Builder().apply {
                setSilenceCall(true)
            }.build()
            respondToCall(callDetails, silenceResponse)
            return
        }

        // 2. Whitelist Check (Override to Ring Out Loud)
        if (!incomingNumber.isNullOrEmpty()) {
            val matchedNumber = RingControlLogic.findWhitelistedMatch(incomingNumber, whitelist)
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
        }

        // Allow all other calls to proceed normally
        respondToCall(callDetails, CallResponse.Builder().build())
    }
}
