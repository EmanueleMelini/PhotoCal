package it.emanuelemelini.photocal.data.openfoodfacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServingSizeTest {

    @Test
    fun countBeforeTheGrams() {
        val pieces = ServingSize.parse("3 biscotti (25 g)", 25.0)!!
        assertEquals(25.0 / 3, pieces.pieceGrams, 1e-9)
        assertEquals("biscotti", pieces.label)
        assertEquals("fette", ServingSize.parse("2 fette (30g)", null)!!.label)
        assertEquals(12.5, ServingSize.parse("2 Biscuits = 25 g", null)!!.pieceGrams, 1e-9)
    }

    @Test
    fun allCapsNamesAreLowercased() {
        val pieces = ServingSize.parse("4 BISCUITS (30 g)", 30.0)!!
        assertEquals(7.5, pieces.pieceGrams, 1e-9)
        assertEquals("biscuits", pieces.label)
        assertEquals("Oreo", ServingSize.parse("3 Oreo (34 g)", null)!!.label)
    }

    @Test
    fun gramsBeforeTheCount() {
        val pieces = ServingSize.parse("25 g (3 biscuits)", null)!!
        assertEquals(25.0 / 3, pieces.pieceGrams, 1e-9)
        assertEquals("biscuits", pieces.label)
    }

    @Test
    fun decimalCommaInTheGrams() {
        assertEquals(8.3, ServingSize.parse("1 biscotto (8,3 g)", null)!!.pieceGrams, 1e-9)
    }

    @Test
    fun countOnlyUsesTheServingQuantity() {
        assertEquals(12.5, ServingSize.parse("4 crackers", 50.0)!!.pieceGrams, 1e-9)
        assertNull(ServingSize.parse("4 crackers", null))
    }

    @Test
    fun singlePieceHasNoLabel() {
        val pieces = ServingSize.parse("1 biscotto (8 g)", 8.0)!!
        assertEquals(8.0, pieces.pieceGrams, 1e-9)
        assertNull(pieces.label)
    }

    @Test
    fun genericPiecesHaveNoLabel() {
        val pieces = ServingSize.parse("25 g (3 pz)", null)!!
        assertEquals(25.0 / 3, pieces.pieceGrams, 1e-9)
        assertNull(pieces.label)
    }

    @Test
    fun servingsAndPlainQuantitiesAreNotPieces() {
        assertNull(ServingSize.parse("1 serving (100 g)", 100.0))
        assertNull(ServingSize.parse("1 porzione (30 g)", 30.0))
        assertNull(ServingSize.parse("100 g", 100.0))
        assertNull(ServingSize.parse("20g", 20.0))
        assertNull(ServingSize.parse("", 20.0))
        assertNull(ServingSize.parse(null, 20.0))
        assertNull(ServingSize.parse("0 biscotti (25 g)", 25.0))
    }
}
