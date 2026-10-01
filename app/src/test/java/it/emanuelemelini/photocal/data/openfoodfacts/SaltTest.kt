package it.emanuelemelini.photocal.data.openfoodfacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SaltTest {

    @Test
    fun saltWinsOverSodium() {
        assertEquals(1.2, saltPer100(1.2, 0.1)!!, 1e-9)
    }

    @Test
    fun withoutSaltItComesFromSodium() {
        assertEquals(1.0, saltPer100(null, 0.4)!!, 1e-9)
        assertNull(saltPer100(null, null))
    }
}
