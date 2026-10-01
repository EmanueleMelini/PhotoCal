package it.emanuelemelini.photocal.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class DiaryCsvTest {

    @Test
    fun italianUsesSemicolonAndDecimalComma() {
        val csv = DiaryCsv(Locale.ITALY)
        assertEquals(';', csv.separator)
        assertEquals("12,5", csv.number(12.5))
        assertEquals("1234", csv.number(1234.0))
        assertEquals("", csv.number(null))
    }

    @Test
    fun englishUsesCommaAndDecimalPoint() {
        val csv = DiaryCsv(Locale.US)
        assertEquals(',', csv.separator)
        assertEquals("12.5", csv.number(12.5))
        assertEquals("0.13", csv.number(0.125))
    }

    @Test
    fun fieldsWithSeparatorsQuotesOrLineBreaksAreQuoted() {
        val text = DiaryCsv(Locale.ITALY).write(listOf("Nome", "Kcal"), listOf(listOf("Pasta; al \"pomodoro\"", "520"), listOf("Riga\nnuova", "1")))
        assertEquals("\uFEFFNome;Kcal\r\n\"Pasta; al \"\"pomodoro\"\"\";520\r\n\"Riga\nnuova\";1\r\n", text)
    }
}
