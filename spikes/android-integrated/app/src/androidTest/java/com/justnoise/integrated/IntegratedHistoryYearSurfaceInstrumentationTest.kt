package com.justnoise.integrated

import android.content.Context
import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justnoise.gate0.data.SessionHistoryTimelineSnapshot
import com.justnoise.gate0.domain.HistoryDaySummary
import com.justnoise.gate0.domain.HistoryWindow
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated presentation only. Never launches instrumentation on the paired phone. */
@RunWith(AndroidJUnit4::class)
class IntegratedHistoryYearSurfaceInstrumentationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var year by mutableStateOf(2026)
    private var largeText by mutableStateOf(false)

    @Before fun guardBeforeLaunching() {
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val context: Context = ApplicationProvider.getApplicationContext()
        assumeTrue(context.packageName == "com.justnoise.androiddev")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario?.onActivity { activity ->
            activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText) 2f else 1f)) {
                    JustnoiseIntegratedTheme {
                        Box(Modifier.size(360.dp, 640.dp)) {
                            val state = IntegratedHistoryState(window = HistoryWindow.YEAR,
                                today = LocalDate.of(year, 10, 11))
                            val snapshot = SessionHistoryTimelineSnapshot(state.range,
                                List(state.range.dayCount) { HistoryDaySummary(state.range.startInclusive.plusDays(it.toLong())) })
                            IntegratedHistorySurface(state.copy(snapshot = snapshot), {}, {}, {}, {}, {})
                        }
                    }
                }
            }
        }
    }

    @After fun closeOnlyTheIsolatedHost() { scenario?.close() }

    @Test fun fullNormalAndLeapYearsRemainVisibleWithoutScrollingAtBothFontScales() {
        for (fixtureYear in listOf(2026, 2028)) for (enlarged in listOf(false, true)) {
            compose.runOnIdle { year = fixtureYear; largeText = enlarged }
            compose.waitForIdle()
            val grid = compose.onNodeWithTag("history-year-dots").fetchSemanticsNode().boundsInRoot
            val calendar = compose.onNodeWithTag("history-calendar").fetchSemanticsNode()
            assertFalse(calendar.config.contains(SemanticsActions.ScrollBy))
            var day = LocalDate.of(fixtureYear, 1, 1)
            repeat(day.lengthOfYear()) {
                val dot = compose.onNodeWithTag("history-day-$day").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue(dot.left >= grid.left - 1 && dot.top >= grid.top - 1)
                assertTrue(dot.right <= grid.right + 1 && dot.bottom <= grid.bottom + 1)
                day = day.plusDays(1)
            }
            compose.onNodeWithTag("history-year-choose-day").assertIsDisplayed()
            captureZapEvidence("fixture-whole-year-$fixtureYear-font-${if (enlarged) 200 else 100}")
        }
    }
}
