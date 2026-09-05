package com.esa.moneytracker.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.esa.moneytracker.data.model.BankColor
import com.esa.moneytracker.data.model.Category
import com.esa.moneytracker.data.model.Pocket
import com.esa.moneytracker.data.model.TransactionType

/**
 * Semantic colours Material 3 has no slot for: income vs. expense, the two
 * pockets, and a colour per category. Exposed through a CompositionLocal so
 * every screen reads the same palette in light and dark mode.
 */
@Immutable
data class MoneyColors(
    val income: Color,
    val incomeContainer: Color,
    val onIncomeContainer: Color,
    val expense: Color,
    val expenseContainer: Color,
    val onExpenseContainer: Color,
    val gold: Color,
    val onGold: Color,
    val pocketOnline: Color,
    val pocketCash: Color,
    val balanceGradient: List<Color>,
    val categoryTint: Map<String, Color>,
    /** One colour per [BankColor], so a bank keeps its identity across screens. */
    val bankTint: Map<String, Color>,
    val surfaceElevated: Color,
    val hairline: Color,
    val muted: Color,
) {
    fun forType(type: TransactionType): Color =
        if (type == TransactionType.INCOME) income else expense

    fun containerForType(type: TransactionType): Color =
        if (type == TransactionType.INCOME) incomeContainer else expenseContainer

    fun onContainerForType(type: TransactionType): Color =
        if (type == TransactionType.INCOME) onIncomeContainer else onExpenseContainer

    fun forPocket(pocket: Pocket): Color =
        if (pocket == Pocket.ONLINE) pocketOnline else pocketCash

    fun forCategory(category: Category?): Color =
        category?.let { categoryTint[it.id] } ?: muted

    fun forBank(color: BankColor): Color = bankTint[color.id] ?: pocketOnline

    fun balanceBrush(): Brush = Brush.linearGradient(balanceGradient)
}

private val lightCategoryTint = mapOf(
    Category.GAJI.id to Mint600,
    Category.PENDAPATAN_LAINNYA.id to Teal500,
    Category.JAJAN.id to Rose500,
    Category.OPERATIONAL.id to Slate500,
    Category.MAKAN.id to Amber500,
    Category.LANGGANAN.id to Violet500,
    Category.KEBUTUHAN.id to Sky500,
    Category.BELANJA.id to Coral500,
    Category.LAINNYA.id to Ink500,
)

private val darkCategoryTint = mapOf(
    Category.GAJI.id to Mint300,
    Category.PENDAPATAN_LAINNYA.id to Color(0xFF6FD3DE),
    Category.JAJAN.id to Color(0xFFF08CB6),
    Category.OPERATIONAL.id to Color(0xFFA8BCCE),
    Category.MAKAN.id to Color(0xFFF3B76B),
    Category.LANGGANAN.id to Violet300,
    Category.KEBUTUHAN.id to Sky300,
    Category.BELANJA.id to Coral300,
    Category.LAINNYA.id to Ink200,
)

private val lightBankTint = mapOf(
    BankColor.EMERALD.id to Emerald500,
    BankColor.SKY.id to Sky500,
    BankColor.VIOLET.id to Violet500,
    BankColor.ROSE.id to Rose500,
    BankColor.CORAL.id to Coral500,
    BankColor.AMBER.id to Amber500,
    BankColor.TEAL.id to Teal500,
    BankColor.GOLD.id to Gold600,
    BankColor.SLATE.id to Slate500,
)

private val darkBankTint = mapOf(
    BankColor.EMERALD.id to Emerald300,
    BankColor.SKY.id to Sky300,
    BankColor.VIOLET.id to Violet300,
    BankColor.ROSE.id to Color(0xFFF08CB6),
    BankColor.CORAL.id to Coral300,
    BankColor.AMBER.id to Color(0xFFF3B76B),
    BankColor.TEAL.id to Color(0xFF6FD3DE),
    BankColor.GOLD.id to Gold300,
    BankColor.SLATE.id to Color(0xFFA8BCCE),
)

val LightMoneyColors = MoneyColors(
    income = Mint600,
    incomeContainer = Mint100,
    onIncomeContainer = Color(0xFF04382C),
    expense = Coral600,
    expenseContainer = Coral100,
    onExpenseContainer = Color(0xFF54130D),
    gold = Gold600,
    onGold = Color(0xFF3A2600),
    pocketOnline = Sky500,
    pocketCash = Gold600,
    balanceGradient = listOf(Emerald500, Emerald600, Emerald800),
    categoryTint = lightCategoryTint,
    bankTint = lightBankTint,
    surfaceElevated = Color.White,
    hairline = Ink100,
    muted = Ink300,
)

val DarkMoneyColors = MoneyColors(
    income = Mint300,
    incomeContainer = Color(0xFF0B3A2F),
    onIncomeContainer = Mint300,
    expense = Coral300,
    expenseContainer = Color(0xFF4A1A15),
    onExpenseContainer = Coral300,
    gold = Gold300,
    onGold = Color(0xFF2E1D00),
    pocketOnline = Sky300,
    pocketCash = Gold300,
    balanceGradient = listOf(Emerald600, Emerald700, Ink800),
    categoryTint = darkCategoryTint,
    bankTint = darkBankTint,
    surfaceElevated = Ink700,
    hairline = Color(0xFF2A3733),
    muted = Ink300,
)

val LocalMoneyColors = staticCompositionLocalOf { LightMoneyColors }
