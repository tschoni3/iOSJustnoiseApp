package com.justnoise.gate0

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.HmacZapStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HmacZapStoreInstrumentationTest {
    @Test
    fun pairAndAuthorize_persistsOnlyDigestAndClearsKey() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = Gate0Preferences(context)
        val store = HmacZapStore(preferences)
        preferences.clearForWithdrawal()
        store.clear()
        val authorized = "synthetic-instrumentation-input".toByteArray()
        val unauthorized = "different-synthetic-input".toByteArray()

        try {
            assertTrue(store.pair(authorized))
            val storedDigest = preferences.snapshot().zapDigest
            assertNotNull(storedDigest)
            assertFalse(storedDigest!!.contains("synthetic-instrumentation-input"))
            assertTrue(store.isAuthorized(authorized))
            assertFalse(store.isAuthorized(unauthorized))
        } finally {
            authorized.fill(0)
            unauthorized.fill(0)
            assertTrue(store.clear())
            assertNull(preferences.snapshot().zapDigest)
        }
    }
}
