package com.justnoise.gate0.domain

import com.justnoise.gate0.data.ModeCatalog
import com.justnoise.gate0.data.ModeRules
import com.justnoise.gate0.data.ModeStoreCodec
import com.justnoise.gate0.data.StoredMode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Android-native extension only; does not claim to migrate or validate the shipped iOS app. */
class AndroidModeFixtureContractTest {
    private val fixture: JSONObject by lazy {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("android-modes.v1.json")) {
            "product/behavior/android-modes.v1.json is not on the JVM test classpath"
        }
        stream.bufferedReader().use { JSONObject(it.readText()) }
    }

    @Test
    fun `catalog fixtures preserve stable identities dangling references and intentional emptiness`() {
        assertEquals(1, fixture.getInt("schemaVersion"))
        assertEquals("android-native-extension", fixture.getString("scope"))
        assertTrue(fixture.getString("iosConformance").contains("MODE-01"))
        val catalogs = fixture.getJSONObject("catalogs")
        assertEquals(5, catalogs.length())
        catalogs.keys().forEach { name ->
            val catalog = catalog(name)
            assertEquals(name, catalog, ModeStoreCodec.decode(ModeStoreCodec.encode(catalog)))
        }
        assertEquals(ModeCatalog(), catalog("empty"))
        assertEquals("33333333-3333-4333-8333-333333333333", catalog("recent-missing").recentModeId)
        assertEquals(null, catalog("recent-missing").recentMode)
    }

    @Test
    fun `every manual resolution fixture uses real mode and existing Zap entry rules`() {
        cases("entryCases", 7).forEach { case ->
            val catalog = catalog(case.getString("catalog"))
            val resolved = ModeRules.modeForStart(catalog)
            val action = ZapEntryModeRules.resolve(
                sessionActive = case.getBoolean("sessionActive"),
                recentModeExists = catalog.recentMode != null,
                recentModeSelectionValid = resolved != null,
            )
            assertEquals(case.getString("id"), case.getString("expectedAction"), action.contractValue)
            assertResolved(case, resolved)
        }
    }

    @Test
    fun `every schedule reference resolves independently without changing explicit selection`() {
        cases("scheduleResolutionCases", 4).forEach { case ->
            val catalog = catalog(case.getString("catalog"))
            val before = ModeStoreCodec.encode(catalog)
            assertResolved(case, ModeRules.modeForStart(catalog, case.getString("modeId")))
            assertEquals(case.getString("id"), before, ModeStoreCodec.encode(catalog))
        }
    }

    @Test
    fun `every deletion fixture includes disabled schedule references`() {
        cases("deletionCases", 5).forEach { case ->
            val references = case.getJSONArray("scheduleReferences")
            // Enabled state does not exempt a saved schedule from referential safety.
            val modeIds = (0 until references.length()).mapTo(mutableSetOf()) {
                references.getJSONObject(it).getString("modeId")
            }
            assertEquals(case.getString("id"), case.getBoolean("expectedAllowed"),
                ModeRules.canDelete(case.getString("modeId"), modeIds))
        }
    }

    @Test
    fun `every one-time legacy migration fixture preserves targets and schedule identity`() {
        cases("migrationCases", 3).forEach { case ->
            val oldPackages = if (case.isNull("legacyPackages")) null else case.getJSONArray("legacyPackages").strings()
            val expected = ModeCatalog(
                modes = case.getJSONArray("expectedModes").modes(),
                recentModeId = case.nullableString("expectedRecentModeId"),
            )
            val migrated = ModeRules.migrateLegacy(oldPackages)
            assertEquals(case.getString("id"), expected, migrated)
            assertEquals(case.getString("id"), migrated, ModeStoreCodec.decode(ModeStoreCodec.encode(migrated)))
        }
    }

    @Test
    fun `every name fixture checks the actual bounded trimmed name rule`() {
        assertEquals(ModeRules.MAX_NAME_LENGTH, fixture.getJSONObject("limits").getInt("nameCharacters"))
        cases("nameCases", 7).forEach { case ->
            val name = if (case.has("name")) case.getString("name") else
                case.getString("repeatCharacter").repeat(case.getInt("repeatCount"))
            assertEquals(case.getString("id"), case.getBoolean("expectedValid"), ModeRules.validName(name))
        }
    }

    @Test
    fun `every target fixture distinguishes new selections from incomplete legacy storage`() {
        assertEquals(ModeRules.MAX_TARGETS, fixture.getJSONObject("limits").getInt("targetsPerMode"))
        cases("targetCases", 7).forEach { case ->
            val packages = if (case.has("packages")) case.getJSONArray("packages").strings() else
                (0 until case.getInt("generatedTargetCount")).mapTo(mutableSetOf()) { "example.target$it" }
            assertEquals(case.getString("id"), case.getBoolean("expectedValid"),
                ModeRules.validTargets(packages, case.getBoolean("allowEmpty")))
        }
    }

    @Test
    fun `codec enforces the documented catalog limit and rejects duplicate stable identities`() {
        val limit = fixture.getJSONObject("limits").getInt("modes")
        assertEquals(ModeRules.MAX_MODES, limit)
        val modes = (1..limit + 1).map { index ->
            StoredMode("11111111-1111-4111-8111-${index.toString().padStart(12, '0')}", 1,
                "Mode $index", setOf("example.target$index"))
        }
        val atLimit = ModeCatalog(modes.take(limit))
        assertEquals(atLimit, ModeStoreCodec.decode(ModeStoreCodec.encode(atLimit)))
        assertThrows(IllegalArgumentException::class.java) { ModeStoreCodec.encode(ModeCatalog(modes)) }
        assertThrows(IllegalArgumentException::class.java) {
            ModeStoreCodec.encode(ModeCatalog(listOf(modes.first(), modes.first())))
        }
    }

    private fun catalog(name: String): ModeCatalog =
        ModeStoreCodec.decode(fixture.getJSONObject("catalogs").getJSONObject(name).toString())

    private fun cases(name: String, expectedCount: Int): List<JSONObject> {
        val values = fixture.getJSONArray(name)
        assertEquals(name, expectedCount, values.length())
        return (0 until values.length()).map(values::getJSONObject)
    }

    private fun assertResolved(case: JSONObject, mode: StoredMode?) {
        assertEquals(case.getString("id"), case.nullableString("expectedModeId"), mode?.id)
        assertEquals(case.getString("id"), case.getJSONArray("expectedTargets").strings(),
            mode?.selectedPackages.orEmpty())
    }

    private fun JSONObject.nullableString(name: String): String? = if (isNull(name)) null else getString(name)
    private fun JSONArray.strings(): Set<String> = (0 until length()).mapTo(mutableSetOf(), ::getString)
    private fun JSONArray.modes(): List<StoredMode> = (0 until length()).map { index ->
        getJSONObject(index).let {
            StoredMode(it.getString("id"), it.getLong("revision"), it.getString("name"),
                it.getJSONArray("selectedPackages").strings())
        }
    }
}
