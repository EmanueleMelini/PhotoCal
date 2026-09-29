package it.emanuelemelini.photocal.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    private fun v(text: String) = requireNotNull(AppVersion.parse(text))

    @Test
    fun comparesNumericallyNotAlphabetically() {
        assertTrue(v("1.10.0") > v("1.9.0"))
        assertTrue(v("2.0.0") > v("1.99.99"))
        assertTrue(v("1.2.1") > v("1.2.0"))
    }

    @Test
    fun tagPrefixMissingPartsAndSuffixesAreIgnored() {
        assertEquals(v("1.2.0"), v("v1.2.0"))
        assertEquals(v("1.2"), v("1.2.0"))
        assertEquals(v("1.2.0").hashCode(), v("1.2").hashCode())
        assertEquals(v("1.3.0"), v("v1.3.0-beta+abc"))
    }

    @Test
    fun rejectsTagsThatArentVersions() {
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse("v1.x"))
        assertNull(AppVersion.parse(""))
    }
}
