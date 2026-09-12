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
import com.esa.moneytracker.ui.subscriptions.SubscriptionFormScreen
import com.esa.moneytracker.ui.subscriptions.SubscriptionFormViewModel
import com.esa.moneytracker.ui.subscriptions.SubscriptionsScreen
import com.esa.moneytracker.ui.subscriptions.SubscriptionsViewModel
import com.esa.moneytracker.ui.transfer.TransferScreen
import com.esa.moneytracker.ui.transfer.TransferViewModel
import com.esa.moneytracker.ui.transfer.TransfersScreen
import com.esa.moneytracker.ui.transfer.TransfersViewModel
import com.esa.moneytracker.ui.viewer.AttachmentViewerScreen
import com.esa.moneytracker.ui.viewer.AttachmentViewerViewModel

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

    /** Riwayat pindah dana — kept apart from Riwayat on purpose. */
    const val TRANSFERS = "transfers"

    /** The Pindah Dana form, for a move that does not exist yet. */
    const val TRANSFER_NEW = "transfer-new"

    /** Langganan — the recurring bills and what they add up to. */
    const val SUBSCRIPTIONS = "subscriptions"

    /** The plan form, for a bill that does not exist yet. */
    const val SUBSCRIPTION_NEW = "subscription-new"

    const val SUBSCRIPTION_ARG = "subscriptionId"
    const val SUBSCRIPTION_EDIT = "subscription-edit/{$SUBSCRIPTION_ARG}"

    fun subscriptionEdit(subscriptionId: String): String = "subscription-edit/$subscriptionId"

    const val TRANSFER_ARG = "transferId"
    const val TRANSFER_EDIT = "transfer-edit/{$TRANSFER_ARG}"

    fun transferEdit(transferId: String): String = "transfer-edit/$transferId"

    const val EDIT_ARG = "transactionId"
    const val EDIT = "edit/{$EDIT_ARG}"

    fun edit(transactionId: String): String = "edit/$transactionId"

    /**
     * One note's lampiran, full screen, opened at a particular one.
     *
     * The position travels in the route rather than the picture's own id: the
     * viewer reads the note's pictures for itself, so what it needs from the
     * caller is only which of them was tapped.
     */
    const val ATTACHMENT_INDEX_ARG = "index"
    const val ATTACHMENTS = "attachments/{$EDIT_ARG}/{$ATTACHMENT_INDEX_ARG}"

    fun attachments(transactionId: String, index: Int): String =
        "attachments/$transactionId/$index"
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
                onOpenAttachment = { id, index ->
                    navController.navigate(Routes.attachments(id, index))
                },
                onOpenRecords = { navController.navigate(Routes.RECORDS) },
                onOpenBanks = { navController.navigate(Routes.BANKS) },
                onOpenData = { navController.navigate(Routes.BACKUP) },
                onOpenSubscriptions = { navController.navigate(Routes.SUBSCRIPTIONS) },
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
                // Pindah Dana leaves the Catat flow rather than continuing it:
                // it asks none of the questions the remaining steps ask.
                onChooseTransfer = {
                    navController.navigate(Routes.TRANSFER_NEW) {
                        popUpTo(Routes.ENTRY) { inclusive = true }
                    }
                },
                onChooseCategory = viewModel::chooseCategory,
                onChoosePocket = viewModel::choosePocket,
                onChooseBank = viewModel::chooseBank,
                onAddBank = viewModel::addBank,
                onAmountChanged = viewModel::onAmountChanged,
                onDescriptionChanged = viewModel::onDescriptionChanged,
                onOccurredAtChanged = viewModel::onOccurredAtChanged,
                onResetOccurredAt = viewModel::resetOccurredAt,
                onPickAttachment = viewModel::addAttachment,
                onCaptureAttachment = viewModel::addCapture,
                onRemoveAttachment = { viewModel.removeAttachment(it.id) },
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
                onOpenAttachment = { id, index ->
                    navController.navigate(Routes.attachments(id, index))
                },
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
                onOpenTransfers = { navController.navigate(Routes.TRANSFERS) },
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

        composable(Routes.TRANSFERS) {
            val viewModel: TransfersViewModel = viewModel(factory = TransfersViewModel.Factory)
            val state by viewModel.state.collectAsStateWithLifecycle()

            TransfersScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onAdd = { navController.navigate(Routes.TRANSFER_NEW) },
                onEdit = { navController.navigate(Routes.transferEdit(it)) },
                onDelete = viewModel::delete,
                onRestore = viewModel::restore,
            )
        }

        composable(Routes.SUBSCRIPTIONS) {
            val viewModel: SubscriptionsViewModel =
                viewModel(factory = SubscriptionsViewModel.Factory)
            val state by viewModel.state.collectAsStateWithLifecycle()

            SubscriptionsScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onAdd = { navController.navigate(Routes.SUBSCRIPTION_NEW) },
                onEdit = { navController.navigate(Routes.subscriptionEdit(it)) },
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                onDelete = viewModel::delete,
                onRestore = viewModel::restore,
            )
        }

        composable(Routes.SUBSCRIPTION_NEW) {
            SubscriptionRoute(navController = navController, subscriptionId = null)
        }

        composable(
            route = Routes.SUBSCRIPTION_EDIT,
            arguments = listOf(navArgument(Routes.SUBSCRIPTION_ARG) { type = NavType.StringType }),
        ) { entry ->
            SubscriptionRoute(
                navController = navController,
                subscriptionId = entry.arguments?.getString(Routes.SUBSCRIPTION_ARG).orEmpty(),
            )
        }

        composable(Routes.TRANSFER_NEW) {
            TransferRoute(navController = navController, transferId = null)
        }

        composable(
            route = Routes.TRANSFER_EDIT,
            arguments = listOf(navArgument(Routes.TRANSFER_ARG) { type = NavType.StringType }),
        ) { entry ->
            TransferRoute(
                navController = navController,
                transferId = entry.arguments?.getString(Routes.TRANSFER_ARG).orEmpty(),
            )
        }

        composable(Routes.BACKUP) {
            val viewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory)
            val state by viewModel.state.collectAsStateWithLifecycle()

            BackupScreen(
                state = state,
                suggestedFileName = viewModel::suggestedFileName,
                suggestedArchiveName = viewModel::suggestedArchiveName,
                onExport = viewModel::export,
                onExportArchive = viewModel::exportArchive,
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
                onPickAttachment = viewModel::addAttachment,
                onCaptureAttachment = viewModel::addCapture,
                onRemoveAttachment = viewModel::removeAttachment,
                onBack = { navController.popBackStack() },
                onSubmit = viewModel::submit,
            )
        }

        composable(
            route = Routes.ATTACHMENTS,
            arguments = listOf(
                navArgument(Routes.EDIT_ARG) { type = NavType.StringType },
                navArgument(Routes.ATTACHMENT_INDEX_ARG) { type = NavType.IntType },
            ),
        ) { entry ->
            val transactionId = entry.arguments?.getString(Routes.EDIT_ARG).orEmpty()
            val index = entry.arguments?.getInt(Routes.ATTACHMENT_INDEX_ARG) ?: 0
            val viewModel: AttachmentViewerViewModel = viewModel(
                key = "viewer-" + transactionId,
                factory = AttachmentViewerViewModel.factory(transactionId),
            )
            val state by viewModel.state.collectAsStateWithLifecycle()

            AttachmentViewerScreen(
                state = state,
                initialIndex = index,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/**
 * The Langganan form, whether it is describing a new bill or fixing an old one.
 *
 * Both routes land here for the same reason the transfer ones do: a plan asks
 * the same questions either way, and only the starting answers differ.
 */
@Composable
private fun SubscriptionRoute(
    navController: NavHostController,
    subscriptionId: String?,
) {
    val viewModel: SubscriptionFormViewModel = viewModel(
        key = "subscription-" + (subscriptionId ?: "new"),
        factory = SubscriptionFormViewModel.factory(subscriptionId),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) navController.popBackStack()
    }

    SubscriptionFormScreen(
        state = state,
        onNameChanged = viewModel::onNameChanged,
        onAmountChanged = viewModel::onAmountChanged,
        onChooseCategory = viewModel::chooseCategory,
        onChoosePocket = viewModel::choosePocket,
        onChooseBank = viewModel::chooseBank,
        onAddBank = viewModel::addBank,
        onChooseCycle = viewModel::chooseCycle,
        onChooseDayOfMonth = viewModel::chooseDayOfMonth,
        onChooseDayOfWeek = viewModel::chooseDayOfWeek,
        onTimeChanged = viewModel::onTimeChanged,
        onBack = { navController.popBackStack() },
        onSubmit = viewModel::submit,
    )
}

/**
 * The Pindah Dana form, whether it is filling in a new move or fixing an old
 * one. Both routes land here because the questions are identical; only the
 * starting answers differ.
 */
@Composable
private fun TransferRoute(
    navController: NavHostController,
    transferId: String?,
) {
    val viewModel: TransferViewModel = viewModel(
        key = "transfer-" + (transferId ?: "new"),
        factory = TransferViewModel.factory(transferId),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) navController.popBackStack()
    }

    TransferScreen(
        state = state,
        onChooseFrom = viewModel::chooseFrom,
        onChooseTo = viewModel::chooseTo,
        onSwap = viewModel::swapEnds,
        onAmountChanged = viewModel::onAmountChanged,
        onUseWholeBalance = viewModel::useWholeBalance,
        onNoteChanged = viewModel::onNoteChanged,
        onOccurredAtChanged = viewModel::onOccurredAtChanged,
        onResetOccurredAt = viewModel::resetOccurredAt,
        onBack = { navController.popBackStack() },
        onSubmit = viewModel::submit,
    )
}
