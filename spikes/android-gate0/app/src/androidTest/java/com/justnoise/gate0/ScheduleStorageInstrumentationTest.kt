package com.justnoise.gate0

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justnoise.gate0.data.DEFAULT_SCHEDULE_MODE_REFERENCE
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.ScheduleDraft
import com.justnoise.gate0.domain.PortableWeekday
import com.justnoise.gate0.domain.ScheduleTiming
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Local configuration only. No alarms, service enablement, or physical NFC. */
@RunWith(AndroidJUnit4::class)
class ScheduleStorageInstrumentationTest {
    private lateinit var context: Context
    private lateinit var preferences: Gate0Preferences

    @Before
    fun prepare() {
        context = ApplicationProvider.getApplicationContext()
        preferences = Gate0Preferences(context)
        assertTrue(preferences.clearForWithdrawal())
    }

    @After
    fun clean() {
        assertTrue(Gate0Preferences(context).clearForWithdrawal())
    }

    @Test
    fun createEditToggleDeletePersistWithoutStartingASession() {
        val initial = preferences.snapshot()
        assertTrue(preferences.saveSchedule(draft().copy(name = "  Morning  "), nowEpochMilliseconds = NOW))
        val created = preferences.loadSchedules().schedules.single()
        assertEquals("Morning", created.name)
        assertEquals(1L, created.revision)
        assertEquals(DEFAULT_SCHEDULE_MODE_REFERENCE, created.modeReference)
        assertEquals(created, Gate0Preferences(context).loadSchedules().schedules.single())

        assertTrue(preferences.saveSchedule(draft().copy(name = "Work"), created.id, 1L, NOW))
        val edited = preferences.loadSchedules().schedules.single()
        assertEquals(created.id, edited.id)
        assertEquals(2L, edited.revision)
        assertEquals("Work", edited.name)
        assertTrue(preferences.setScheduleEnabled(edited.id, 2L, false, NOW))
        val disabled = preferences.loadSchedules().schedules.single()
        assertFalse(disabled.enabled)
        assertEquals(3L, disabled.revision)
        assertTrue(preferences.setScheduleEnabled(disabled.id, 3L, false, NOW))
        assertEquals(disabled, preferences.loadSchedules().schedules.single())
        assertTrue(preferences.deleteSchedule(disabled.id, 3L))
        assertTrue(Gate0Preferences(context).loadSchedules().schedules.isEmpty())
        assertEquals(initial, preferences.snapshot())
        assertEquals(0, preferences.loadHistory().totalCount)
    }

    @Test
    fun staleEditsTogglesAndDeletesCannotReplaceNewerConfiguration() {
        assertTrue(preferences.saveSchedule(draft(), nowEpochMilliseconds = NOW))
        val first = preferences.loadSchedules().schedules.single()
        assertTrue(preferences.saveSchedule(draft().copy(name = "Newer"), first.id, first.revision, NOW))
        val current = preferences.loadSchedules()
        assertFalse(preferences.saveSchedule(draft().copy(name = "Stale"), first.id, first.revision, NOW))
        assertFalse(preferences.setScheduleEnabled(first.id, first.revision, false, NOW))
        assertFalse(preferences.deleteSchedule(first.id, first.revision))
        assertFalse(preferences.saveSchedule(draft(), first.id, null, NOW))
        assertFalse(preferences.saveSchedule(draft(), expectedRevision = 1L, nowEpochMilliseconds = NOW))
        assertEquals(current, preferences.loadSchedules())
    }

    @Test
    fun configurationChangesPreserveActiveIdentityTargetsPairingAndHistory() {
        assertTrue(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, NOW - 1L))
        assertTrue(preferences.setSelectedPackages(setOf("synthetic.one", "synthetic.two")))
        assertTrue(preferences.setZapDigest("synthetic-schedule-test-digest"))
        assertTrue(preferences.startSession(NOW))
        val active = preferences.snapshot()
        assertTrue(preferences.saveSchedule(draft(), nowEpochMilliseconds = NOW))
        val record = preferences.loadSchedules().schedules.single()
        assertTrue(preferences.setScheduleEnabled(record.id, record.revision, false, NOW))
        assertTrue(preferences.deleteSchedule(record.id, record.revision + 1L))
        assertEquals(active, preferences.snapshot())
        assertEquals(0, preferences.loadHistory().totalCount)
    }

    @Test
    fun invalidOrUnsupportedStorageReportsErrorAndCannotBeOverwritten() {
        val corruptValues = listOf("{", "{\"schemaVersion\":999,\"schedules\":[]}")
        for (encoded in corruptValues) {
            assertTrue(raw().edit().putString("schedules_v1", encoded).commit())
            assertTrue(preferences.loadSchedules().error)
            assertFalse(preferences.saveSchedule(draft(), nowEpochMilliseconds = NOW))
            assertFalse(preferences.setScheduleEnabled("unknown", 1L, false, NOW))
            assertFalse(preferences.deleteSchedule("unknown", 1L))
            assertEquals(encoded, raw().getString("schedules_v1", null))
        }
    }

    @Test
    fun pastOneTimeCanRemainDisabledButCannotBeEnabledOrRolledToTomorrow() {
        val past = draft().copy(timing = ScheduleTiming(oneTimeEpochMilliseconds = NOW - 1L))
        assertFalse(preferences.saveSchedule(past, nowEpochMilliseconds = NOW))
        assertFalse(preferences.saveSchedule(draft().copy(name = "  "), nowEpochMilliseconds = NOW))
        assertFalse(preferences.saveSchedule(draft().copy(creationZoneId = "Not/AZone"), nowEpochMilliseconds = NOW))
        assertTrue(preferences.loadSchedules().schedules.isEmpty())
        assertTrue(preferences.saveSchedule(past.copy(enabled = false), nowEpochMilliseconds = NOW))
        val stored = preferences.loadSchedules().schedules.single()
        assertFalse(preferences.setScheduleEnabled(stored.id, stored.revision, true, NOW))
        assertEquals(stored, preferences.loadSchedules().schedules.single())
    }

    @Test
    fun missingDefaultReferenceDiffersFromResolvedEmptyAndLegacySelectionRemainsResolvable() {
        assertFalse(preferences.isScheduleSelectionResolvable(DEFAULT_SCHEDULE_MODE_REFERENCE))
        assertTrue(raw().edit().putString("selected_package", "synthetic.legacy").commit())
        assertTrue(preferences.isScheduleSelectionResolvable(DEFAULT_SCHEDULE_MODE_REFERENCE))
        assertFalse(preferences.isScheduleSelectionResolvable("missing-mode"))
        assertTrue(preferences.setSelectedPackages(emptySet()))
        assertTrue(preferences.isScheduleSelectionResolvable(DEFAULT_SCHEDULE_MODE_REFERENCE))
        assertTrue(preferences.snapshot().selectedPackages.isEmpty())
    }

    @Test
    fun failedCommitRestoresStoredRecordAndRetryUsesSameExpectedRevision() {
        assertTrue(preferences.saveSchedule(draft(), nowEpochMilliseconds = NOW))
        val original = preferences.loadSchedules().schedules.single()
        val failingContext = FailingContext(context)
        val failing = Gate0Preferences(failingContext)
        val before = preferences.snapshot()
        failingContext.failNextCommit = true

        assertFalse(failing.saveSchedule(draft().copy(name = "Changed"), original.id, original.revision, NOW))

        assertEquals(before.copy(recoveryRequired = true), preferences.snapshot())
        assertEquals(original, failing.loadSchedules().schedules.single())
        assertTrue(failing.saveSchedule(draft().copy(name = "Changed"), original.id, original.revision, NOW))
        val saved = Gate0Preferences(context).loadSchedules().schedules.single()
        assertEquals(original.id, saved.id)
        assertEquals(original.revision + 1L, saved.revision)
        assertEquals("Changed", saved.name)
    }

    @Test
    fun withdrawalClearsAllConfigurationsAndNewSaveGetsNewIdentity() {
        assertTrue(preferences.saveSchedule(draft(), nowEpochMilliseconds = NOW))
        val old = preferences.loadSchedules().schedules.single()
        assertTrue(preferences.clearForWithdrawal())
        assertFalse(raw().contains("schedules_v1"))
        assertFalse(preferences.loadSchedules().error)
        assertTrue(preferences.loadSchedules().schedules.isEmpty())
        assertFalse(preferences.saveSchedule(draft(), old.id, old.revision, NOW))
        assertTrue(preferences.saveSchedule(draft(), nowEpochMilliseconds = NOW))
        assertNotEquals(old.id, preferences.loadSchedules().schedules.single().id)
    }

    private fun draft() = ScheduleDraft(
        name = "Morning", enabled = true,
        timing = ScheduleTiming(repeatWeekdays = setOf(PortableWeekday.MONDAY), hour = 9, minute = 0),
        creationZoneId = "Europe/Berlin",
    )

    private fun raw(): SharedPreferences = context.getSharedPreferences("gate0_local_state", Context.MODE_PRIVATE)

    private class FailingContext(base: Context) : ContextWrapper(base) {
        var failNextCommit = false
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val storage = super.getSharedPreferences(name, mode)
            return object : SharedPreferences by storage {
                override fun edit(): SharedPreferences.Editor = FailingEditor(storage.edit()) {
                    failNextCommit.also { failNextCommit = false }
                }
            }
        }
    }

    private class FailingEditor(
        private val editor: SharedPreferences.Editor,
        private val shouldFail: () -> Boolean,
    ) : SharedPreferences.Editor by editor {
        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { editor.putString(key, value) }
        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = apply { editor.putStringSet(key, values) }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { editor.putInt(key, value) }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { editor.putLong(key, value) }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { editor.putFloat(key, value) }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { editor.putBoolean(key, value) }
        override fun remove(key: String): SharedPreferences.Editor = apply { editor.remove(key) }
        override fun clear(): SharedPreferences.Editor = apply { editor.clear() }
        override fun commit(): Boolean {
            if (!shouldFail()) return editor.commit()
            editor.apply() // Reproduce Android's changed in-memory map after a failed commit.
            return false
        }
    }

    private companion object {
        const val NOW = 1_788_393_600_000L // 2026-09-03T00:00:00Z
    }
}
