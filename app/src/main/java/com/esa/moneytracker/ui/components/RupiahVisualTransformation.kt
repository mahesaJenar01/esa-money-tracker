package com.esa.moneytracker.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.esa.moneytracker.util.CurrencyFormatter

/**
 * Shows a digits-only field as `Rp 1.250.000` while the state stays `1250000`.
 *
 * The offset mapping is built from the same walk that inserts the separators,
 * so the caret can never land outside the transformed string.
 */
class RupiahVisualTransformation(
    private val prefix: String = "Rp ",
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        if (digits.isEmpty()) {
            // Leave the field truly empty so the placeholder shows through.
            return TransformedText(AnnotatedString(""), OffsetMapping.Identity)
        }

        val grouped = CurrencyFormatter.groupDigits(digits)
        val output = prefix + grouped

        // offsets[i] = index in `output` just past original digit i-1.
        val offsets = IntArray(digits.length + 1)
        var cursor = prefix.length
        offsets[0] = cursor
        for (i in digits.indices) {
            cursor++
            val remaining = digits.length - 1 - i
            if (remaining > 0 && remaining % 3 == 0) cursor++
            offsets[i + 1] = cursor
        }

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                offsets[offset.coerceIn(0, digits.length)]

            override fun transformedToOriginal(offset: Int): Int {
                val clamped = offset.coerceIn(0, output.length)
                var best = 0
                for (i in offsets.indices) {
                    if (offsets[i] <= clamped) best = i else break
                }
                return best
            }
        }

        return TransformedText(AnnotatedString(output), mapping)
    }
}
