package com.esa.moneytracker.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Rupiah formatting. Indonesian convention: `.` groups thousands and there are
 * no decimals in everyday use, so amounts are whole [Long] values throughout.
 */
object CurrencyFormatter {

    private val symbols = DecimalFormatSymbols(Locale.forLanguageTag("id-ID")).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }

    private val grouped = DecimalFormat("#,##0", symbols)

    /** `1250000` -> `"1.250.000"` */
    fun group(amount: Long): String = grouped.format(amount)

    /** `1250000` -> `"Rp 1.250.000"` */
    fun rupiah(amount: Long): String = "Rp " + grouped.format(amount)

    /** `-1250000` -> `"- Rp 1.250.000"`, `1250000` -> `"+ Rp 1.250.000"` */
    fun signedRupiah(amount: Long): String {
        val sign = if (amount < 0) "- " else "+ "
        return sign + rupiah(kotlin.math.abs(amount))
    }

    /** Groups a raw digit string without parsing it into a number first. */
    fun groupDigits(digits: String): String {
        if (digits.isEmpty()) return ""
        val builder = StringBuilder()
        val n = digits.length
        for (i in 0 until n) {
            builder.append(digits[i])
            val remaining = n - 1 - i
            if (remaining > 0 && remaining % 3 == 0) builder.append('.')
        }
        return builder.toString()
    }
}
