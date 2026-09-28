package it.emanuelemelini.photocal.ui

import it.emanuelemelini.photocal.AppLanguage
import it.emanuelemelini.photocal.AppLocale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormatTest {

    @After
    fun resetLocale() {
        AppLocale.current = Locale.getDefault()
    }

    @Test
    fun amountsUseTheDecimalSeparatorOfTheLanguage() {
        assertEquals("12,5", 12.5.formatAmount(Locale.ITALY))
        assertEquals("12.5", 12.5.formatAmount(Locale.US))
    }

    @Test
    fun wholeAmountsHaveNoDecimals() {
        assertEquals("150", 150.0.formatAmount(Locale.ITALY))
        assertEquals("150", 149.96.formatAmount(Locale.US))
    }

    @Test
    fun parsingAcceptsCommaAndDotInEveryLanguage() {
        assertEquals(12.5, parseDecimal("12,5")!!, 0.0)
        assertEquals(12.5, parseDecimal(" 12.5 ")!!, 0.0)
        assertEquals(null, parseDecimal("abc"))
    }

    @Test
    fun pluralCountTreatsOnlyOneAsSingular() {
        assertEquals(1, pluralCount(1.0))
        assertEquals(2, pluralCount(1.5))
        assertEquals(2, pluralCount(0.5))
    }

    @Test
    fun untranslatedLanguagesFallBackToItalian() {
        AppLocale.current = Locale.ENGLISH
        assertEquals(AppLanguage.ENGLISH, AppLocale.language)
        AppLocale.current = Locale.GERMAN
        assertEquals(AppLanguage.ITALIAN, AppLocale.language)
    }
}
