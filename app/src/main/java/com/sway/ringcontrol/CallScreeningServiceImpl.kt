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
        // VERIFICATION_STATUS_FAILED = Definite Spoof
        // VERIFICATION_STATUS_NOT_VERIFIED = Potential Spam (Missing digital signature)
        val isDefiniteSpam = verificationStatus == Connection.VERIFICATION_STATUS_FAILED
        val isPotentialSpam = verificationStatus == Connection.VERIFICATION_STATUS_NOT_VERIFIED
        
        val blockSpamEnabled = sharedPrefs.getBoolean("block_spam", true)

        if ((isDefiniteSpam || isPotentialSpam) && blockSpamEnabled) {
            Log.d("RingControl", "SCREENING: Automatically rejecting SPAM (Definite=$isDefiniteSpam, Potential=$isPotentialSpam) from $incomingNumber")
            val spamResponse = CallResponse.Builder().apply {
                setDisallowCall(true)      // Do not allow it to ring
                setRejectCall(true)        // Declines the call (usually sends to VM or hangs up depending on carrier)
                setSkipNotification(true)  // Hide from UI
                setSkipCallLog(false)      // Keep in log for records
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
                // Ensure no missed call notification or "active incoming" UI shows up for silenced calls
                setSkipNotification(true)
                setDisallowCall(true)
                setRejectCall(false) // Just silence, don't reject to voicemail yet (let it ring out silently)
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
