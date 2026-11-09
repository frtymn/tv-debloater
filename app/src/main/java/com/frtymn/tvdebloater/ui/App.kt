package com.frtymn.tvdebloater.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frtymn.tvdebloater.R

@Composable
fun App(vm: MainViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    BackHandler(enabled = s.screen != Screen.CONNECT && s.screen != Screen.PACKAGES) { vm.back() }

    LaunchedEffect(s.message) {
        val m = s.message ?: return@LaunchedEffect
        val text = when (m) {
            "NOT_CONNECTED" -> context.getString(R.string.err_not_connected)
            "NOTHING_TO_UNDO" -> context.getString(R.string.nothing_to_undo)
            "NOTHING_TO_RESTORE" -> context.getString(R.string.nothing_to_restore)
            "NOTHING_APPLICABLE" -> context.getString(R.string.nothing_applicable)
            else -> m
        }
        snackbar.showSnackbar(text)
        vm.clearMessage()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            if (s.ready) {
                when (s.screen) {
                    Screen.ONBOARDING -> OnboardingScreen(onDone = vm::finishOnboarding, canGoBack = s.onboardingDone, onBack = { vm.back() })
                    Screen.CONNECT -> ConnectScreen(s, vm)
                    Screen.PACKAGES -> PackagesScreen(s, vm)
                    Screen.HISTORY -> HistoryScreen(s, vm)
                    Screen.SETTINGS -> SettingsScreen(s, vm)
                    Screen.RESULTS -> ResultsScreen(s, vm)
                }
            }
            s.confirm?.let { ConfirmDialog(it, s.dryRun, onRun = vm::execute, onCancel = vm::dismissConfirm) }
            if (s.showAggressiveWarning) AggressiveDialog(onOk = vm::applyAggressiveProfile, onCancel = vm::dismissAggressiveWarning)
            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}
