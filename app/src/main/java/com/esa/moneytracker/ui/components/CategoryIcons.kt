package com.esa.moneytracker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.ui.graphics.vector.ImageVector
import com.esa.moneytracker.data.model.Category
import com.esa.moneytracker.data.model.Pocket

/** One glyph per category, kept in a single place so the list stays consistent. */
fun iconFor(category: Category?): ImageVector = when (category) {
    Category.GAJI -> Icons.Rounded.Payments
    Category.PENDAPATAN_LAINNYA -> Icons.Rounded.Savings
    Category.JAJAN -> Icons.Rounded.Cookie
    Category.OPERATIONAL -> Icons.Rounded.Bolt
    Category.MAKAN -> Icons.Rounded.Restaurant
    Category.LANGGANAN -> Icons.Rounded.Subscriptions
    Category.KEBUTUHAN -> Icons.Rounded.Home
    Category.BELANJA -> Icons.Rounded.ShoppingBag
    Category.LAINNYA, null -> Icons.Rounded.Category
}

fun iconFor(pocket: Pocket): ImageVector = when (pocket) {
    Pocket.ONLINE -> Icons.Rounded.Bolt
    Pocket.CASH -> Icons.Rounded.AccountBalanceWallet
}
