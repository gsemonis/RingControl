package com.sway.ringcontrol

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

class RingControlLogicTest {

    @Mock
    private lateinit var sharedPrefs: SharedPreferences

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `findWhitelistedMatch returns number for exact digit match`() {
        val whitelisted = setOf("1234567890")
        val result = RingControlLogic.findWhitelistedMatch("1234567890", whitelisted, sharedPrefs)
        assertEquals("1234567890", result)
    }

    @Test
    fun `findWhitelistedMatch returns number for last 10 digit match with country code`() {
        val whitelisted = setOf("1234567890")
        // Whitelisted is local 10-digit, incoming has +1
        val result = RingControlLogic.findWhitelistedMatch("+11234567890", whitelisted, sharedPrefs)
        assertEquals("1234567890", result)
    }

    @Test
    fun `findWhitelistedMatch returns number for contact name match`() {
        val whitelisted = setOf("1234567890")
        `when`(sharedPrefs.getString("name_1234567890", "")).thenReturn("John Doe")
        
        val result = RingControlLogic.findWhitelistedMatch("John Doe", whitelisted, sharedPrefs)
        assertEquals("1234567890", result)
    }

    @Test
    fun `findWhitelistedMatch is case insensitive for names`() {
        val whitelisted = setOf("1234567890")
        `when`(sharedPrefs.getString("name_1234567890", "")).thenReturn("John Doe")
        
        val result = RingControlLogic.findWhitelistedMatch("john doe", whitelisted, sharedPrefs)
        assertEquals("1234567890", result)
    }

    @Test
    fun `findWhitelistedMatch returns number for custom matching name`() {
        val whitelisted = setOf("1234567890")
        `when`(sharedPrefs.getString("custom_name_1234567890", "")).thenReturn("My Best Friend")
        
        val result = RingControlLogic.findWhitelistedMatch("My Best Friend", whitelisted, sharedPrefs)
        assertEquals("1234567890", result)
    }

    @Test
    fun `findWhitelistedMatch returns null when no match found`() {
        val whitelisted = setOf("1234567890")
        val result = RingControlLogic.findWhitelistedMatch("9999999999", whitelisted, sharedPrefs)
        assertNull(result)
    }

    @Test
    fun `cleanNumber removes non-digits`() {
        assertEquals("1234567890", RingControlLogic.cleanNumber("+1 (234) 567-890"))
    }
}
