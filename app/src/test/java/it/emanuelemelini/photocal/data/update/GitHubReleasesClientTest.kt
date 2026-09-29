package it.emanuelemelini.photocal.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleasesClientTest {

    private val latest = """
        {
          "tag_name": "v1.3.0",
          "html_url": "https://github.com/EmanueleMelini/PhotoCal/releases/tag/v1.3.0",
          "assets": [
            {"name": "notes.txt", "browser_download_url": "https://github.com/x/notes.txt", "size": 10},
            {"name": "PhotoCal-1.3.0.apk", "browser_download_url": "https://github.com/EmanueleMelini/PhotoCal/releases/download/v1.3.0/PhotoCal-1.3.0.apk", "size": 3340348}
          ]
        }
    """.trimIndent()

    @Test
    fun readsVersionPageAndApk() {
        val release = requireNotNull(GitHubReleasesClient.parseRelease(latest))
        assertEquals(AppVersion.parse("1.3.0"), release.version)
        assertEquals("https://github.com/EmanueleMelini/PhotoCal/releases/tag/v1.3.0", release.pageUrl)
        assertEquals("https://github.com/EmanueleMelini/PhotoCal/releases/download/v1.3.0/PhotoCal-1.3.0.apk", release.apkUrl)
        assertEquals(3_340_348L, release.apkSize)
    }

    @Test
    fun releaseWithoutApkIsIgnored() {
        assertNull(GitHubReleasesClient.parseRelease("""{"tag_name": "v1.3.0", "assets": []}"""))
    }

    @Test
    fun tagThatIsntAVersionIsIgnored() {
        assertNull(GitHubReleasesClient.parseRelease(latest.replace("v1.3.0\",", "nightly\",")))
    }

    @Test(expected = UpdateCheckException::class)
    fun unexpectedAnswerIsAnError() {
        GitHubReleasesClient.parseRelease("<html>rate limited</html>")
    }

    /** Same layout as .github/scripts/release_notes.py, with the CRLF of a body edited on GitHub. */
    private val body = listOf(
        "## What's new in PhotoCal 1.3.0",
        "",
        "- Checks for updates.",
        "- Shows what's new.",
        "",
        "<!-- photocal-notes:it",
        "- Controlla gli aggiornamenti.",
        "- Mostra le novità.",
        "-->",
    ).joinToString("\r\n")

    @Test
    fun notesAreReadInEveryLanguage() {
        val notes = GitHubReleasesClient.parseNotes(body)
        assertEquals(listOf("Checks for updates.", "Shows what's new."), notes["en"])
        assertEquals(listOf("Controlla gli aggiornamenti.", "Mostra le novità."), notes["it"])
    }

    @Test
    fun missingLanguageFallsBackToEnglish() {
        val release = Release(requireNotNull(AppVersion.parse("1.3.0")), "https://p", "https://a", 0, GitHubReleasesClient.parseNotes(body))
        assertEquals("Controlla gli aggiornamenti.", release.notesFor("it").first())
        assertEquals("Checks for updates.", release.notesFor("de").first())
    }

    @Test
    fun bodyWithoutBulletsHasNoNotes() {
        assertTrue(GitHubReleasesClient.parseNotes("**Full Changelog**: https://github.com/x/compare/v1.1.0...v1.2.0").isEmpty())
    }

    @Test
    fun bodyIsReadFromTheRelease() {
        val json = latest.replace("\"assets\"", "\"body\": \"- One\\n- Two\", \"assets\"")
        assertEquals(listOf("One", "Two"), GitHubReleasesClient.parseRelease(json)?.notes?.get("en"))
    }
}
