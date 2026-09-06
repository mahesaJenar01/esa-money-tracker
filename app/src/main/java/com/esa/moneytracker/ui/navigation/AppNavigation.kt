package com.esa.moneytracker.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.esa.moneytracker.ui.backup.BackupScreen
import com.esa.moneytracker.ui.backup.BackupViewModel
import com.esa.moneytracker.ui.banks.BanksScreen
import com.esa.moneytracker.ui.banks.BanksViewModel
import com.esa.moneytracker.ui.bin.BinScreen
import com.esa.moneytracker.ui.bin.BinViewModel
import com.esa.moneytracker.ui.check.BalanceCheckScreen
import com.esa.moneytracker.ui.check.BalanceCheckViewModel
import com.esa.moneytracker.ui.edit.EditScreen
import com.esa.moneytracker.ui.edit.EditViewModel
import com.esa.moneytracker.ui.entry.EntryScreen
import com.esa.moneytracker.ui.entry.EntryViewModel
import com.esa.moneytracker.ui.home.HomeScreen
import com.esa.moneytracker.ui.home.HomeViewModel
import com.esa.moneytracker.ui.records.RecordsScreen
import com.esa.moneytracker.ui.records.RecordsViewModel
import com.esa.moneytracker.ui.setup.SetupGate
import com.esa.moneytracker.ui.setup.SetupScreen
import com.esa.moneytracker.ui.setup.SetupViewModel

object Routes {
    const val HOME = "home"
    const val ENTRY = "entry"

    /** The full week-by-week history. */
    const val RECORDS = "records"

    /** Catatan terhapus — the 30-day bin. */
    const val BIN = "bin"

    /** Export and import. */
    const val BACKUP = "backup"

    /** The banks the Online pocket is made of. */
    const val BANKS = "banks"

    /** Cek saldo — the reconciliation that leaves a mark in the history. */
    const val CHECK = "balance-check"

    const val EDIT_ARG = "transactionId"
    const val EDIT = "edit/{$EDIT_ARG}"

    fun edit(transactionId: String): String = "edit/$transactionId"
}

/**
 * The app either asks for the opening balances or runs normally.
 *
 * The question sits in front of the whole graph rather than inside it: while it
 * is unanswered there is nothing to navigate to, and answering it swaps the
 * screen for the real app without leaving a back-stack entry to return to.
 */
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val setupViewModel: SetupViewModel = viewModel(factory = SetupViewModel.Factory)
    val setupState by setupViewModel.state.collectAsStateWithLifecycle()

    when (setupState.gate) {
        // One frame at most, while the database answers. Showing the setup
        // screen here would make it flash on every cold start.
        SetupGate.UNKNOWN -> Box(modifier.fillMaxSize())

        SetupGate.NEEDED -> SetupScreen(
            state = setupState,
            onCashChanged = setupViewModel::onCashChanged,
            onAddBank = setupViewModel::addBank,
            onRemoveBank = setupViewModel::removeBank,
            onSubmit = setupViewModel::submit,
            onImport = setupViewModel::importBackup,
            modifier = modifier,
        )

        SetupGate.READY -> MainNavHost(modifier = modifier, navController = navController)
    }
}

@Composable
private fun MainNavHost(
    modifier: Modifier,
    navController: NavHostController,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
            val state by viewModel.state.collectAsStateWithLifecycle()

            HomeScreen(
                state = state,
                onSelectPeriod = viewModel::selectPeriod,
                onAddClick = { navController.navigate(Routes.ENTRY) },
                onEdit = { navController.navigate(Routes.edit(it)) },
                onDelete = viewModel::delete,
                onRestore = viewModel::restore,
                onDeleteCheck = viewModel::deleteCheck,
                onOpenRecords = { navController.navigate(Routes.RECORDS) },
                onOpenBanks = { navController.navigate(Routes.BANKS) },
                onOpenData = { navController.navigate(Routes.BACKUP) },
            )
        }

        composable(Routes.ENTRY) {
            val viewModel: EntryViewModel = viewModel(factory = EntryViewModel.Factory)
            val state by viewModel.state.collectAsStateWithLifecycle()

            // Submitting always lands back on the main page.
            LaunchedEffect(state.saved) {
                if (state.saved) navController.popBackStack()
            }

            // System back walks the flow backwards before leaving the screen.
            BackHandler { if (!viewModel.back()) navController.popBackStack() }

            EntryScreen(
                state = state,
                onChooseType = viewModel::chooseType,
                onChooseCategory = viewModel::chooseCategory,
                onChoosePocket = viewModel::choosePocket,
                onChooseBank = viewModel::chooseBank,
                onAddBank = viewModel::addBank,
                onAmountChanged = viewModel::onAmountChanged,
                onDescriptionChanged = viewModel::onDescriptionChanged,
                onOccurredAtChanged = viewModel::onOccurredAtChanged,
                onResetOccurredAt = viewModel::resetOccurredAt,
                onBack = { if (!viewModel.back()) navController.popBackStack() },
                onSubmit = viewModel::submit,
            )
        }

        composable(Routes.RECORDS) {
            val viewModel: RecordsViewModel = viewModel(factory = RecordsViewModel.Factory)
            val state by viewModel.state.collectAsStateWithLifecycle()

            RecordsScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onOlderWeek = viewModel::showOlderWeek,
                onNewerWeek = viewModel::showNewerWeek,
                onEdit = { navController.navigate(Routes.edit(it)) },
                onDelete = viewModel::delete,
                onRestore = viewModel::restore,
                onDeleteCheck = viewModel::deleteCheck,
                onOpenBin = { navController.navigate(Routes.BIN) },
                onCheckBalance = { navController.navigate(Routes.CHECK) },
            )
        }

        composable(Routes.BIN) {
            val viewModel: BinViewModel = viewModel(factory = BinViewModel.Factory)
            val state by viewModel.state.collectAsStateWithLifecycle()

            BinScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onRestore = viewModel::restore,
            )
        }

        composable(Routes.BANKS) {
            val viewModel: BanksViewModel = viewModel(factory = BanksViewModel.Factory)
            val state by viewModel.state.collectAsStateWithLifecycle()

            BanksScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onAddBank = viewModel::addBank,
                onRenameBank = viewModel::renameBank,
                onCorrectBalance = viewModel::correctBalance,
                onCloseBank = viewModel::closeBank,
                onDismissMessage = viewModel::dismissMessage,
                onCheckBalance = { navController.navigate(Routes.CHECK) },
            )
        }

        composable(Routes.CHECK) {
            val viewModel: BalanceCheckViewModel = viewModel(factory = BalanceCheckViewModel.Factory)
            val state by viewModel.state.collectAsStateWithLifecycle()

            // Saving lands back wherever the check was started from — the bank
            // page or the full history — and the new mark is already in both.
            LaunchedEffect(state.saved) {
                if (state.saved) navController.popBackStack()
            }

            BalanceCheckScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onBalanceChanged = viewModel::onBalanceChanged,
                onClearBalance = viewModel::onClearBalance,
                onRecordDifferenceChanged = viewModel::onRecordDifferenceChanged,
                onNoteChanged = viewModel::onNoteChanged,
                onCheckedAtChanged = viewModel::onCheckedAtChanged,
                onResetCheckedAt = viewModel::resetCheckedAt,
                onSubmit = viewModel::submit,
            )
        }

        composable(Routes.BACKUP) {
            val viewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory)
            val state by viewModel.state.collectAsStateWithLifecycle()

            BackupScreen(
                state = state,
                suggestedFileName = viewModel::suggestedFileName,
                onExport = viewModel::export,
                onImport = viewModel::importBackup,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.EDIT,
            arguments = listOf(navArgument(Routes.EDIT_ARG) { type = NavType.StringType }),
        ) { entry ->
            val transactionId = entry.arguments?.getString(Routes.EDIT_ARG).orEmpty()
            val viewModel: EditViewModel = viewModel(
                key = transactionId,
                factory = EditViewModel.factory(transactionId),
            )
            val state by viewModel.state.collectAsStateWithLifecycle()

            LaunchedEffect(state.saved) {
                if (state.saved) navController.popBackStack()
            }

            EditScreen(
                state = state,
                onChooseType = viewModel::chooseType,
                onChooseCategory = viewModel::chooseCategory,
                onChoosePocket = viewModel::choosePocket,
                onChooseBank = viewModel::chooseBank,
                onAddBank = viewModel::addBank,
                onAmountChanged = viewModel::onAmountChanged,
                onDescriptionChanged = viewModel::onDescriptionChanged,
                onOccurredAtChanged = viewModel::onOccurredAtChanged,
                onResetOccurredAt = viewModel::resetOccurredAt,
                onBack = { navController.popBackStack() },
                onSubmit = viewModel::submit,
            )
        }
    }
}
