package com.justnoise.gate0.platform.discovery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectedPackagePolicyTest {
    @Test
    fun `uses exact package membership and rejects blank target`() {
        val protected = setOf("com.android.settings", "com.justnoise.gate0")

        assertTrue(ProtectedPackagePolicy.isProtected("", protected))
        assertTrue(ProtectedPackagePolicy.isProtected("com.android.settings", protected))
        assertFalse(ProtectedPackagePolicy.isProtected("com.android.settings.fake", protected))
        assertFalse(ProtectedPackagePolicy.isProtected("example.target", protected))
    }
}
