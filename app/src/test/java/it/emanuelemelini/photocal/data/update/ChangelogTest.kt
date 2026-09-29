package it.emanuelemelini.photocal.data.update

import it.emanuelemelini.photocal.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogTest {

    /** Stops a release (the workflow runs these tests) whose changelog wasn't written. */
    @Test
    fun currentVersionHasItsNotes() {
        val newest = Changelog.entries.first()
        assertEquals(BuildConfig.VERSION_CODE, newest.versionCode)
        assertEquals(BuildConfig.VERSION_NAME, newest.versionName)
    }

    @Test
    fun entriesAreNewestFirstWithoutDuplicates() {
        val codes = Changelog.entries.map { it.versionCode }
        assertEquals(codes.sortedDescending().distinct(), codes)
        val versions = Changelog.entries.map { requireNotNull(AppVersion.parse(it.versionName)) }
        assertTrue(versions.zipWithNext().all { (newer, older) -> newer > older })
    }

    @Test
    fun betweenReturnsOnlyTheVersionsNotSeenYet() {
        assertEquals(listOf("1.3.0"), Changelog.between(3, 4).map { it.versionName })
        assertEquals(listOf("1.3.0", "1.2.0"), Changelog.between(2, 4).map { it.versionName })
        assertTrue(Changelog.between(4, 4).isEmpty())
    }
}
