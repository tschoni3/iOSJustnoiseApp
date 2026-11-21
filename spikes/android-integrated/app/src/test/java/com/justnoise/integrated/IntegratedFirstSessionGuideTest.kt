package com.justnoise.integrated

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IntegratedFirstSessionGuideTest {
    @Test fun mainActivityNoLongerShowsThePostHomeHowItWorksDialog() {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).canonicalFile) { it.parentFile }
            .map { File(it, "spikes/android-integrated/app") }
            .first { File(it, "build.gradle.kts").isFile }
        val activity = File(root, "src/main/java/com/justnoise/integrated/MainActivity.kt").readText()
        val guide = File(root, "src/main/java/com/justnoise/integrated/IntegratedFirstSessionGuide.kt").readText()
        assertFalse(activity.contains("IntegratedFirstSessionGuideRules"))
        assertFalse(activity.contains("IntegratedFirstSessionGuide("))
        assertTrue(activity.contains("IntegratedHowItWorksGuide("))
        assertTrue(activity.contains("showingHowItWorksReplay"))
        assertTrue(guide.contains("R.raw.jn_onboarding_intro"))
        assertTrue(guide.contains("Got it") || guide.contains("first_session_guide_done"))
        val gradle = File(root, "../../../android-app/app/build.gradle.kts").canonicalFile.readText()
        assertTrue(gradle.contains("JN_onboarding_intro.mp4"))
        assertEquals(true, File(root, "../../../JN_onboarding_intro.mp4").canonicalFile.isFile)
    }
}
