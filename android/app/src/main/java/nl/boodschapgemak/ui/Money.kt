package nl.boodschapgemak.ui

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

private val DUTCH = Locale.forLanguageTag("nl-NL")

fun formatMoney(cents: Int): String =
    NumberFormat.getCurrencyInstance(DUTCH).format(BigDecimal(cents).movePointLeft(2))

/**
 * Turns whatever was typed into the amount field into cents.
 * Accepts "12,34", "12.34", "€ 12,34" and "12". Returns null if it is not a number.
 */
fun parseAmountToCents(input: String): Int? {
    val cleaned = input.trim()
        .removePrefix("€")
        .replace(" ", "")
        .replace(".", "")   // thousands separator in nl-NL
        .replace(',', '.')
        .trim()
    if (cleaned.isEmpty()) return null
    return runCatching {
        BigDecimal(cleaned).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toInt()
    }.getOrNull()
}
