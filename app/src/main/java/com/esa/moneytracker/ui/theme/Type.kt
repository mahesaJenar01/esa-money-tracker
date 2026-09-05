package com.esa.moneytracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

private val Sans = FontFamily.SansSerif

private val tightLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = Sans,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    lineHeightStyle = tightLineHeight,
)

/**
 * A tighter, more editorial scale than the Material default: large numbers get
 * negative tracking so balances read as a single shape, and small labels get
 * positive tracking so they stay legible at low contrast.
 */
val MoneyTypography = Typography(
    displayLarge = style(44, 48, FontWeight.Bold, -1.2),
    displayMedium = style(36, 42, FontWeight.Bold, -0.8),
    displaySmall = style(30, 36, FontWeight.Bold, -0.6),

    headlineLarge = style(28, 34, FontWeight.Bold, -0.5),
    headlineMedium = style(24, 30, FontWeight.SemiBold, -0.4),
    headlineSmall = style(20, 26, FontWeight.SemiBold, -0.2),

    titleLarge = style(19, 25, FontWeight.SemiBold, -0.1),
    titleMedium = style(16, 22, FontWeight.SemiBold, 0.0),
    titleSmall = style(14, 20, FontWeight.SemiBold, 0.1),

    bodyLarge = style(16, 24, FontWeight.Normal, 0.1),
    bodyMedium = style(14, 20, FontWeight.Normal, 0.1),
    bodySmall = style(12, 17, FontWeight.Normal, 0.2),

    labelLarge = style(14, 18, FontWeight.SemiBold, 0.3),
    labelMedium = style(12, 16, FontWeight.SemiBold, 0.5),
    labelSmall = style(11, 14, FontWeight.SemiBold, 0.7),
)
