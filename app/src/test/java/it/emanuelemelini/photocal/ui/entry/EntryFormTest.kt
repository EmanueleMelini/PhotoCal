package it.emanuelemelini.photocal.ui.entry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryFormTest {

    private val saved = EntryForm(name = "Pasta al pomodoro", quantity = "80")

    @Test
    fun aQuantityTypedByTheUserIsKept() {
        assertTrue(EntryForm(name = "Riso", quantity = "120").quantityIsTheUsers(source = null))
    }

    @Test
    fun anotherFoodWithTheOldQuantityGetsTheAiGrams() {
        assertFalse(saved.copy(name = "Pizza margherita").quantityIsTheUsers(saved))
    }

    @Test
    fun theSameFoodKeepsItsQuantity() {
        assertTrue(saved.copy(name = " pasta al pomodoro ").quantityIsTheUsers(saved))
    }

    @Test
    fun aChangedQuantityIsTheUsers() {
        assertTrue(saved.copy(name = "Pizza margherita", quantity = "300").quantityIsTheUsers(saved))
    }
}
