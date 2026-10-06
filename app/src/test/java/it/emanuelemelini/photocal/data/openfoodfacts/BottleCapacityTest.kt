package it.emanuelemelini.photocal.data.openfoodfacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BottleCapacityTest {

    private fun product(quantityText: String?, packageQuantity: Double? = null, isLiquid: Boolean = true) = Product(
        barcode = "8002270014901", name = "Acqua", brand = null,
        kcalPer100 = 0.0, proteinPer100 = null, carbsPer100 = null, fatPer100 = null,
        servingQuantity = null, packageQuantity = packageQuantity, quantityText = quantityText,
        servingPieces = null, isLiquid = isLiquid, imageUrl = null,
    )

    @Test
    fun volumesInEveryUnit() {
        assertEquals(500, BottleCapacity.parse("500 ml"))
        assertEquals(500, BottleCapacity.parse("0,5 l"))
        assertEquals(750, BottleCapacity.parse("75 cl"))
        assertEquals(1500, BottleCapacity.parse("1.5L"))
        assertEquals(250, BottleCapacity.parse("2,5 dl"))
        assertEquals(1000, BottleCapacity.parse("1 litro"))
    }

    @Test
    fun aPackCountsOneBottle() {
        assertEquals(500, BottleCapacity.parse("6 x 50 cl"))
        assertEquals(1500, BottleCapacity.parse("1,5 l x 6"))
    }

    @Test
    fun textWithoutVolume() {
        assertNull(BottleCapacity.parse("6 bottiglie"))
        assertNull(BottleCapacity.parse("500 g"))
    }

    @Test
    fun packageQuantityWhenTheTextSaysNothing() {
        assertEquals(330, BottleCapacity.of(product(quantityText = null, packageQuantity = 330.0)))
        assertNull(BottleCapacity.of(product(quantityText = null, packageQuantity = 330.0, isLiquid = false)))
    }

    @Test
    fun volumesNoBottleHasAreIgnored() {
        assertNull(BottleCapacity.of(product(quantityText = "20 ml")))
        assertNull(BottleCapacity.of(product(quantityText = "5 l")))
    }
}
