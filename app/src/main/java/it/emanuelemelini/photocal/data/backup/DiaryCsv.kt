package it.emanuelemelini.photocal.data.backup

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Diary as CSV, the way a spreadsheet of that language opens it: where the decimal separator
 * is a comma (Italian) fields are separated by ';', otherwise by ','. UTF-8 with BOM, so Excel
 * reads accented letters correctly.
 */
class DiaryCsv(private val locale: Locale) {

    private val symbols = DecimalFormatSymbols.getInstance(locale)
    val separator: Char = if (symbols.decimalSeparator == ',') ';' else ','

    private val format = DecimalFormat("0.##", symbols).apply {
        isGroupingUsed = false
        roundingMode = RoundingMode.HALF_UP
    }

    /** Up to two decimals, no thousands separator. */
    fun number(value: Double?): String = value?.let(format::format).orEmpty()

    fun write(header: List<String>, rows: List<List<String>>): String = buildString {
        append(BOM)
        (listOf(header) + rows).forEach { row ->
            append(row.joinToString(separator.toString(), transform = ::escape))
            append("\r\n")
        }
    }

    /** Quotes a field that contains the separator, quotes or line breaks (RFC 4180). */
    private fun escape(field: String): String =
        if (field.any { it == separator || it == '"' || it == '\n' || it == '\r' }) "\"" + field.replace("\"", "\"\"") + "\""
        else field

    private companion object {
        const val BOM = '\uFEFF'
    }
}
