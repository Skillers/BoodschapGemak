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
 * Accepts "12,34", "12.34", "EUR 12,34", "1.234,56" and "12".
 * Returns null if it is not a number.
 *
 * Both separators have to be tolerated: the Dutch keyboard gives a comma, but
 * Android's decimal keypad gives a dot on plenty of phones, and the same field
 * takes both. So the *last* separator decides - if it has one or two digits
 * behind it, it is the decimal point and everything before it groups
 * thousands; otherwise every separator is a thousands group.
 */
fun parseAmountToCents(input: String): Int? {
    val cleaned = input.trim()
        .removePrefix("€")
        .replace(" ", "")
        .replace("\u00A0", "")
        .trim()
    if (cleaned.isEmpty()) return null

    val lastSeparator = cleaned.lastIndexOfAny(charArrayOf('.', ','))
    val normalised = when {
        lastSeparator == -1 -> cleaned
        cleaned.length - lastSeparator - 1 in 1..2 ->
            cleaned.take(lastSeparator).replace(".", "").replace(",", "") +
                "." + cleaned.substring(lastSeparator + 1)
        else -> cleaned.replace(".", "").replace(",", "")
    }

    return runCatching {
        BigDecimal(normalised).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toInt()
    }.getOrNull()
}
