@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.frtymn.tvdebloater.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.frtymn.tvdebloater.BuildConfig
import com.frtymn.tvdebloater.R
import com.frtymn.tvdebloater.adb.AdbManager
import com.frtymn.tvdebloater.core.PkgAction
import com.frtymn.tvdebloater.core.PkgState
import com.frtymn.tvdebloater.core.SafetyLevel
import com.frtymn.tvdebloater.data.Batch
import com.frtymn.tvdebloater.data.BatchKind
import java.io.File
import java.text.DateFormat
import java.util.Date

private val BigButton = Modifier.heightIn(min = 52.dp)

@Composable
private fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        },
        actions = { actions() },
    )
}

// ---------------------------------------------------------------- Onboarding

@Composable
fun OnboardingScreen(onDone: () -> Unit, canGoBack: Boolean, onBack: () -> Unit) {
    Scaffold(topBar = {
        if (canGoBack) BackTopBar(stringResource(R.string.onb_title), onBack)
        else TopAppBar(title = { Text(stringResource(R.string.onb_title)) })
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.onb_intro), style = MaterialTheme.typography.bodyLarge)
            listOf(R.string.onb_step1, R.string.onb_step2, R.string.onb_step3, R.string.onb_step4, R.string.onb_step5, R.string.onb_step6)
                .forEachIndexed { i, res ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                            Text("${i + 1}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(32.dp))
                            Text(stringResource(res), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text(stringResource(R.string.onb_safety), Modifier.padding(14.dp))
            }
            Button(onClick = onDone, modifier = BigButton.fillMaxWidth()) { Text(stringResource(R.string.onb_start)) }
        }
    }
}

// ---------------------------------------------------------------- Connect

@Composable
fun ConnectScreen(s: UiState, vm: MainViewModel) {
    val failed = s.conn as? ConnState.Failed
    var ip by rememberSaveable { mutableStateOf(failed?.host ?: s.devices.firstOrNull()?.host ?: "") }
    var port by rememberSaveable { mutableStateOf((failed?.port ?: s.devices.firstOrNull()?.port ?: AdbManager.DEFAULT_PORT).toString()) }
    val portInt = port.toIntOrNull()?.takeIf { it in 1..65535 } ?: AdbManager.DEFAULT_PORT
    val connecting = s.conn is ConnState.Connecting

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.connect_title)) }, actions = {
            TextButton(onClick = { vm.go(Screen.HISTORY) }) { Text(stringResource(R.string.history)) }
            IconButton(onClick = { vm.go(Screen.SETTINGS) }) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
        })
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ip, onValueChange = { ip = it.trim() }, label = { Text(stringResource(R.string.ip_label)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.weight(1f), enabled = !connecting, placeholder = { Text("192.168.1.23") },
                )
                OutlinedTextField(
                    value = port, onValueChange = { v -> port = v.filter { it.isDigit() }.take(5) }, label = { Text(stringResource(R.string.port_label)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(100.dp), enabled = !connecting,
                )
            }
            if (connecting) {
                val c = s.conn as ConnState.Connecting
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(24.dp)); Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.connecting, "${c.host}:${c.port}"))
                        }
                        Text(stringResource(R.string.accept_prompt), style = MaterialTheme.typography.bodyLarge, color = CautionColor)
                        OutlinedButton(onClick = vm::cancelConnect, modifier = BigButton) { Text(stringResource(R.string.cancel)) }
                    }
                }
            } else {
                Button(onClick = { vm.connect(ip, portInt) }, enabled = ip.isNotBlank(), modifier = BigButton.fillMaxWidth()) {
                    Text(stringResource(R.string.connect))
                }
                Text(stringResource(R.string.accept_prompt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (failed != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(errorText(failed.kind), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.error_details, failed.detail), style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        Button(onClick = { vm.connect(failed.host.ifBlank { ip }, if (failed.host.isBlank()) portInt else failed.port) }, modifier = BigButton) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }

            OutlinedButton(onClick = { vm.scan(portInt) }, enabled = !s.scan.running && !connecting, modifier = BigButton.fillMaxWidth()) {
                Text(stringResource(R.string.scan, portInt))
            }
            if (s.scan.running) {
                LinearProgressIndicator(progress = { if (s.scan.total == 0) 0f else s.scan.done.toFloat() / s.scan.total }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.scanning, s.scan.done, s.scan.total))
            } else if (s.scan.finished) {
                when {
                    s.scan.noWifi -> Text(stringResource(R.string.scan_no_wifi), color = CautionColor)
                    s.scan.found.isEmpty() -> Text(stringResource(R.string.scan_none))
                    else -> {
                        Text(stringResource(R.string.scan_found))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            s.scan.found.forEach { h -> OutlinedButton(onClick = { ip = h }, modifier = BigButton) { Text(h) } }
                        }
                    }
                }
            }

            if (s.devices.isNotEmpty()) {
                SectionTitle(stringResource(R.string.saved_devices))
                s.devices.forEach { d ->
                    Card(Modifier.fillMaxWidth().clickable(enabled = !connecting) { ip = d.host; port = d.port.toString() }) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${d.host}:${d.port}", style = MaterialTheme.typography.titleMedium)
                                if (d.name.isNotBlank()) Text(d.name, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { vm.forgetDevice(d) }) { Icon(Icons.Default.Delete, stringResource(R.string.forget)) }
                        }
                    }
                }
            }
            TextButton(onClick = { vm.go(Screen.ONBOARDING) }) { Text(stringResource(R.string.show_guide)) }
        }
    }
}

// ---------------------------------------------------------------- Packages

@Composable
fun PackagesScreen(s: UiState, vm: MainViewModel) {
    val info = (s.conn as? ConnState.Connected)?.info
    var menu by remember { mutableStateOf(false) }
    val visible = s.packages.filter { p ->
        val q = s.query.trim().lowercase()
        (q.isEmpty() || p.packageName.lowercase().contains(q) || p.label.lowercase().contains(q)) &&
            when (s.filter) {
                Filter.ALL -> true
                Filter.SAFE -> p.level == SafetyLevel.SAFE
                Filter.CAUTION -> p.level == SafetyLevel.CAUTION
                Filter.PROTECTED -> p.level == SafetyLevel.PROTECTED
                Filter.DISABLED -> p.state == PkgState.DISABLED || p.state == PkgState.UNINSTALLED
            }
    }
    val selectedItems = s.packages.filter { it.packageName in s.selected }
    val canDisable = selectedItems.any { it.state == PkgState.ENABLED }
    val canRestore = selectedItems.any { it.state == PkgState.DISABLED || it.state == PkgState.UNINSTALLED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.packages_title), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (s.dryRun) { Spacer(Modifier.width(8.dp)); Badge(stringResource(R.string.dry_run_badge), CautionColor) }
                        }
                        if (info != null) Text(stringResource(R.string.device_line, info.model, info.androidVersion, info.host), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    IconButton(onClick = vm::refreshPackages) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.menu)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.history)) }, onClick = { menu = false; vm.go(Screen.HISTORY) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.panic)) }, onClick = { menu = false; vm.planPanic() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, onClick = { menu = false; vm.go(Screen.SETTINGS) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.show_guide)) }, onClick = { menu = false; vm.go(Screen.ONBOARDING) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.disconnect)) }, onClick = { menu = false; vm.disconnect() })
                    }
                },
            )
        },
        bottomBar = {
            if (s.selected.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().navigationBarsPadding(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.selected_count, s.selected.size), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = vm::clearSelection) { Text(stringResource(R.string.clear)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.planChange(PkgAction.DISABLE) }, enabled = canDisable, modifier = BigButton.weight(1f), contentPadding = BarButtonPadding) {
                                Text(stringResource(R.string.action_disable), maxLines = 2, textAlign = TextAlign.Center)
                            }
                            if (canRestore) {
                                OutlinedButton(onClick = vm::planRestoreSelected, modifier = BigButton.weight(1f), contentPadding = BarButtonPadding) {
                                    Text(stringResource(R.string.action_restore), maxLines = 2, textAlign = TextAlign.Center)
                                }
                            }
                            OutlinedButton(
                                onClick = { vm.planChange(PkgAction.UNINSTALL_USER) },
                                enabled = selectedItems.any { it.state != PkgState.UNINSTALLED },
                                modifier = BigButton.weight(1f),
                                contentPadding = BarButtonPadding,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ProtectedColor),
                            ) { Text(stringResource(R.string.action_remove), maxLines = 2, textAlign = TextAlign.Center) }
                        }
                    }
                }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.dryRun) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Text(stringResource(R.string.dry_run_on_hint), Modifier.padding(12.dp), color = CautionColor)
                        }
                    }
                    StatsCard(s, onRefresh = vm::refreshStatsAsync)
                    OutlinedTextField(
                        value = s.query, onValueChange = vm::setQuery, label = { Text(stringResource(R.string.search)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            Filter.ALL to R.string.filter_all, Filter.SAFE to R.string.filter_safe, Filter.CAUTION to R.string.filter_caution,
                            Filter.PROTECTED to R.string.filter_protected, Filter.DISABLED to R.string.filter_disabled,
                        ).forEach { (f, res) ->
                            val count = s.packages.count { p ->
                                when (f) {
                                    Filter.ALL -> true
                                    Filter.SAFE -> p.level == SafetyLevel.SAFE
                                    Filter.CAUTION -> p.level == SafetyLevel.CAUTION
                                    Filter.PROTECTED -> p.level == SafetyLevel.PROTECTED
                                    Filter.DISABLED -> p.state != PkgState.ENABLED
                                }
                            }
                            FilterChip(selected = s.filter == f, onClick = { vm.setFilter(f) }, label = { Text("${stringResource(res)} ($count)") }, modifier = Modifier.heightIn(min = 44.dp))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = vm::applyLightProfile, modifier = BigButton.weight(1f)) { Text(stringResource(R.string.profile_light)) }
                        OutlinedButton(onClick = vm::requestAggressiveProfile, modifier = BigButton.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = CautionColor)) {
                            Text(stringResource(R.string.profile_aggressive))
                        }
                    }
                    TextButton(onClick = { vm.selectVisible(visible) }) { Text(stringResource(R.string.select_visible)) }
                    if (s.loadingPackages) {
                        LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.loading_packages))
                    }
                    s.packagesError?.let { Text(stringResource(R.string.error_details, it), color = MaterialTheme.colorScheme.error) }
                    if (!s.loadingPackages && visible.isEmpty()) Text(stringResource(R.string.no_packages))
                }
            }
            items(visible, key = { it.packageName }) { p -> PackageRow(p, p.packageName in s.selected) { vm.toggle(p.packageName) } }
        }
    }
}

@Composable
private fun StatsCard(s: UiState, onRefresh: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (s.mem == null && s.storage == null) Text(stringResource(R.string.stats_unknown))
                s.mem?.let { Text(stringResource(R.string.ram, formatKb(it.freeKb), formatKb(it.totalKb))) }
                s.storage?.let { Text(stringResource(R.string.storage, formatKb(it.availableKb), formatKb(it.totalKb))) }
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) }
        }
    }
}

@Composable
private fun PackageRow(p: PkgItem, selected: Boolean, onToggle: () -> Unit) {
    val selectable = p.level != SafetyLevel.PROTECTED
    Column(
        Modifier.fillMaxWidth().clickable(enabled = selectable, onClick = onToggle).heightIn(min = 64.dp).padding(vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = if (selectable) { _ -> onToggle() } else null, enabled = selectable)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LevelBadge(p.level, p.unknown)
                    if (p.state == PkgState.DISABLED) Badge(stringResource(R.string.state_disabled), UnknownColor)
                    if (p.state == PkgState.UNINSTALLED) Badge(stringResource(R.string.state_uninstalled), UnknownColor)
                }
                Text(p.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (p.label != p.packageName) Text(p.packageName, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val desc = p.description.ifBlank {
                    if (p.unknown) stringResource(if (p.level == SafetyLevel.PROTECTED) R.string.unknown_core_desc else R.string.unknown_desc) else ""
                }
                if (desc.isNotBlank()) Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (p.sources.isNotEmpty()) Text(stringResource(R.string.sources, p.sources.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(Modifier.padding(top = 6.dp))
    }
}

// ---------------------------------------------------------------- Dialogs

@Composable
fun AggressiveDialog(onOk: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.aggressive_title)) },
        text = { Text(stringResource(R.string.aggressive_text)) },
        confirmButton = { Button(onClick = onOk, modifier = BigButton) { Text(stringResource(R.string.aggressive_ok)) } },
        dismissButton = { TextButton(onClick = onCancel, modifier = BigButton) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ConfirmDialog(req: ConfirmRequest, dryRun: Boolean, onRun: () -> Unit, onCancel: () -> Unit) {
    val removing = req.steps.any { it.action == PkgAction.UNINSTALL_USER } && req.kind == BatchKind.CHANGE
    var accepted by remember { mutableStateOf(!removing) }
    val n = req.steps.size
    val title = when {
        req.kind == BatchKind.PANIC -> stringResource(R.string.confirm_title_panic, n)
        req.kind == BatchKind.UNDO -> stringResource(R.string.confirm_title_undo, n)
        req.action == PkgAction.DISABLE -> stringResource(R.string.confirm_title_disable, n)
        req.action == PkgAction.UNINSTALL_USER -> stringResource(R.string.confirm_title_remove, n)
        else -> stringResource(R.string.confirm_title_restore, n)
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (dryRun) Text(stringResource(R.string.confirm_dry), color = CautionColor, fontWeight = FontWeight.Bold)
                if (req.kind == BatchKind.CHANGE && req.steps.any { it.level == SafetyLevel.CAUTION && it.action.isDestructive }) {
                    Text(stringResource(R.string.confirm_caution), color = CautionColor)
                }
                if (removing) Text(stringResource(R.string.confirm_remove_warning), color = ProtectedColor)
                Text(stringResource(R.string.confirm_text))
                req.steps.forEach { st ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LevelBadge(st.level, false)
                            Text(st.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(st.command, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
                if (req.skipped.isNotEmpty()) Text(stringResource(R.string.confirm_skipped, req.skipped.joinToString(", ")), style = MaterialTheme.typography.bodySmall)
                if (removing) {
                    Row(Modifier.clickable { accepted = !accepted }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                        Text(stringResource(R.string.confirm_remove_check))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onRun, enabled = accepted, modifier = BigButton) {
                Text(stringResource(if (dryRun) R.string.confirm_show else R.string.confirm_run))
            }
        },
        dismissButton = { TextButton(onClick = onCancel, modifier = BigButton) { Text(stringResource(R.string.cancel)) } },
    )
}

// ---------------------------------------------------------------- Results

@Composable
fun ResultsScreen(s: UiState, vm: MainViewModel) {
    val run = s.run
    if (run == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { vm.closeResults() }
        return
    }
    val total = run.request.steps.size
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.results_title)) }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (run.running) {
                        LinearProgressIndicator(progress = { if (total == 0) 0f else run.done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.running, run.done, total))
                    } else {
                        val ok = run.results.count { it.success == true }
                        val bad = run.results.count { it.success == false }
                        Text(
                            if (run.dryRun) stringResource(R.string.summary_dry, run.results.size) else stringResource(R.string.summary, ok, bad),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    run.fatalError?.let { Text(stringResource(R.string.fatal_error, it), color = MaterialTheme.colorScheme.error) }
                    if (!run.dryRun && !run.running) {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(stringResource(R.string.before_after), style = MaterialTheme.typography.titleMedium)
                                val mb = run.memBefore; val ma = run.memAfter
                                if (mb != null && ma != null) Text(stringResource(R.string.ram_diff, formatKb(mb.freeKb), formatKb(ma.freeKb), formatDiff(ma.freeKb - mb.freeKb)))
                                val sb = run.storageBefore; val sa = run.storageAfter
                                if (sb != null && sa != null) Text(stringResource(R.string.storage_diff, formatKb(sb.availableKb), formatKb(sa.availableKb), formatDiff(sa.availableKb - sb.availableKb)))
                                if ((mb == null || ma == null) && (sb == null || sa == null)) Text(stringResource(R.string.stats_unknown))
                            }
                        }
                        Text(stringResource(R.string.restart_hint), color = CautionColor)
                    }
                }
            }
            items(run.results) { r ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            when (r.success) {
                                true -> Badge(stringResource(R.string.result_ok), SafeColor)
                                false -> Badge(stringResource(R.string.result_fail), ProtectedColor)
                                null -> Badge(stringResource(R.string.result_dry), CautionColor)
                            }
                            Text(r.step.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("$ ${r.step.command}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        if (r.success != null) Text(r.output, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                if (!run.running) Button(onClick = vm::closeResults, modifier = BigButton.fillMaxWidth()) { Text(stringResource(R.string.done)) }
            }
        }
    }
}

// ---------------------------------------------------------------- History

@Composable
fun HistoryScreen(s: UiState, vm: MainViewModel) {
    val context = LocalContext.current
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
        if (text == null) { vm.showMessage(context.getString(R.string.import_failed, "read error")); return@rememberLauncherForActivityResult }
        vm.importJson(text) { r ->
            r.onSuccess { n -> vm.showMessage(context.getString(R.string.imported, n)) }
                .onFailure { e -> vm.showMessage(context.getString(R.string.import_failed, e.message ?: "?")) }
        }
    }
    val shareTitle = stringResource(R.string.share_history)
    Scaffold(topBar = { BackTopBar(stringResource(R.string.history), onBack = { vm.back() }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            runCatching {
                                val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                                val f = File(dir, "tv-debloater-history-${System.currentTimeMillis()}.json")
                                f.writeText(vm.exportJson())
                                val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", f)
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, f.name)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(send, shareTitle))
                            }.onFailure { vm.showMessage(it.message ?: "export error") }
                        }, modifier = BigButton.weight(1f)) { Text(stringResource(R.string.export)) }
                        OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }, modifier = BigButton.weight(1f)) {
                            Text(stringResource(R.string.import_))
                        }
                    }
                    Button(
                        onClick = { vm.planPanic() }, modifier = BigButton.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ProtectedColor),
                    ) { Text(stringResource(R.string.panic)) }
                    Text(stringResource(R.string.panic_explain), style = MaterialTheme.typography.bodySmall)
                    if (s.batches.isEmpty()) Text(stringResource(R.string.history_empty))
                }
            }
            items(s.batches, key = { it.id }) { b -> BatchCard(b, vm) }
        }
    }
}

@Composable
private fun BatchCard(b: Batch, vm: MainViewModel) {
    var open by remember { mutableStateOf(false) }
    val kind = stringResource(when (b.kind) { BatchKind.CHANGE -> R.string.kind_change; BatchKind.UNDO -> R.string.kind_undo; BatchKind.PANIC -> R.string.kind_panic })
    val ok = b.entries.count { it.success == true }
    val bad = b.entries.count { it.success == false }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("$kind · ${actionName(b.action)}", style = MaterialTheme.typography.titleMedium)
                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(b.timestamp)), style = MaterialTheme.typography.bodySmall)
                    Text("${b.device} · ${stringResource(R.string.summary, ok, bad)}", style = MaterialTheme.typography.bodySmall)
                }
                Text(if (open) "▲" else "▼")
            }
            if (b.kind == BatchKind.CHANGE && b.entries.any { !it.undone && it.success != false }) {
                OutlinedButton(onClick = { vm.planUndoBatch(b) }, modifier = BigButton.fillMaxWidth()) { Text(stringResource(R.string.undo_batch)) }
            }
            if (open) {
                b.entries.forEachIndexed { i, e ->
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                when (e.success) {
                                    true -> Badge(stringResource(R.string.result_ok), SafeColor)
                                    false -> Badge(stringResource(R.string.result_fail), ProtectedColor)
                                    null -> Badge(stringResource(R.string.pending), UnknownColor)
                                }
                                if (e.undone) Badge(stringResource(R.string.undone), SafeColor)
                            }
                            Text(e.packageName, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                            Text("${e.previousState} → ${e.action}", style = MaterialTheme.typography.bodySmall)
                            if (e.output.isNotBlank()) Text(e.output, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        if (b.kind == BatchKind.CHANGE && !e.undone && e.success != false) {
                            TextButton(onClick = { vm.planUndoEntry(b, i) }, modifier = BigButton) { Text(stringResource(R.string.undo)) }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Settings

@Composable
fun SettingsScreen(s: UiState, vm: MainViewModel) {
    Scaffold(topBar = { BackTopBar(stringResource(R.string.settings), onBack = { vm.back() }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.toggleable(value = s.dryRun, role = Role.Switch, onValueChange = { vm.setDryRun(it) })
                        .padding(16.dp).heightIn(min = 52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.dry_run), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.dry_run_desc), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = s.dryRun, onCheckedChange = null)
                }
            }
            Text(stringResource(R.string.language_note))
            OutlinedButton(onClick = { vm.go(Screen.ONBOARDING) }, modifier = BigButton.fillMaxWidth()) { Text(stringResource(R.string.show_guide)) }
            SectionTitle(stringResource(R.string.recovery_title))
            Text(stringResource(R.string.recovery_text))
            SectionTitle(stringResource(R.string.about))
            Text(stringResource(R.string.about_text))
            Text(stringResource(R.string.version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall)
            if (vm.demoAvailable) {
                SectionTitle(stringResource(R.string.demo_title))
                Text(stringResource(R.string.demo_text), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { vm.loadDemo() }, modifier = BigButton.fillMaxWidth()) { Text(stringResource(R.string.demo_load)) }
            }
        }
    }
}

@Composable
private fun actionName(a: PkgAction): String = stringResource(
    when (a) {
        PkgAction.DISABLE -> R.string.action_disable
        PkgAction.UNINSTALL_USER -> R.string.action_remove
        PkgAction.ENABLE -> R.string.act_enable
        PkgAction.INSTALL_EXISTING -> R.string.act_install_existing
    },
)

private val BarButtonPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)
