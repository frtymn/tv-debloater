package com.frtymn.tvdebloater.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.frtymn.tvdebloater.BuildConfig
import com.frtymn.tvdebloater.adb.AdbError
import com.frtymn.tvdebloater.adb.AdbErrorKind
import com.frtymn.tvdebloater.adb.AdbManager
import com.frtymn.tvdebloater.adb.DeviceInfo
import com.frtymn.tvdebloater.adb.SubnetScanner
import com.frtymn.tvdebloater.core.Classifier
import com.frtymn.tvdebloater.core.CommandBuilder
import com.frtymn.tvdebloater.core.MemInfo
import com.frtymn.tvdebloater.core.PackageDatabase
import com.frtymn.tvdebloater.core.PackageEntry
import com.frtymn.tvdebloater.core.PackageListParser
import com.frtymn.tvdebloater.core.PkgAction
import com.frtymn.tvdebloater.core.PkgState
import com.frtymn.tvdebloater.core.ReportLink
import com.frtymn.tvdebloater.core.SafetyLevel
import com.frtymn.tvdebloater.core.StorageInfo
import com.frtymn.tvdebloater.data.AppStore
import com.frtymn.tvdebloater.data.Batch
import com.frtymn.tvdebloater.data.BatchEntry
import com.frtymn.tvdebloater.data.BatchKind
import com.frtymn.tvdebloater.data.PackageDbLoader
import com.frtymn.tvdebloater.data.SavedDevice
import com.frtymn.tvdebloater.db.ActiveDb
import com.frtymn.tvdebloater.db.DbCheckResult
import com.frtymn.tvdebloater.db.DbSource
import com.frtymn.tvdebloater.db.DbStore
import com.frtymn.tvdebloater.db.DbUpdater
import com.frtymn.tvdebloater.db.UrlConnectionClient
import com.frtymn.tvdebloater.demo.DemoData
import com.frtymn.tvdebloater.demo.DemoSnapshot
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

enum class Screen { ONBOARDING, CONNECT, PACKAGES, HISTORY, SETTINGS, RESULTS }
enum class Filter { ALL, SAFE, CAUTION, PROTECTED, DISABLED }

sealed interface ConnState {
    data object Idle : ConnState
    data class Connecting(val host: String, val port: Int) : ConnState
    data class Connected(val info: DeviceInfo) : ConnState
    data class Failed(val kind: AdbErrorKind, val detail: String, val host: String, val port: Int) : ConnState
}

data class PkgItem(
    val packageName: String,
    val label: String,
    val description: String,
    val level: SafetyLevel,
    val unknown: Boolean,
    val state: PkgState,
    val aggressive: Boolean,
    val sources: List<String>,
)

data class PlannedStep(
    val packageName: String,
    val label: String,
    val action: PkgAction,
    val command: String,
    val previousState: PkgState,
    val level: SafetyLevel,
    val restores: String? = null,
)

data class ConfirmRequest(val kind: BatchKind, val action: PkgAction, val steps: List<PlannedStep>, val skipped: List<String> = emptyList())

data class StepResult(val step: PlannedStep, val success: Boolean?, val output: String)

data class RunState(
    val request: ConfirmRequest,
    val dryRun: Boolean,
    val done: Int = 0,
    val running: Boolean = true,
    val results: List<StepResult> = emptyList(),
    val memBefore: MemInfo? = null,
    val memAfter: MemInfo? = null,
    val storageBefore: StorageInfo? = null,
    val storageAfter: StorageInfo? = null,
    val fatalError: String? = null,
)

data class ScanState(val running: Boolean = false, val done: Int = 0, val total: Int = 0, val found: List<String> = emptyList(), val finished: Boolean = false, val noWifi: Boolean = false)

/** What the Settings "Package database" section and the package list header show. */
data class DbUi(
    val dbVersion: Int = 0,
    val publishedAt: String = "",
    val source: DbSource = DbSource.BUNDLED,
    val lastCheckedAt: Long = 0,
    val safe: Int = 0,
    val caution: Int = 0,
    val protected: Int = 0,
    val autoUpdate: Boolean = true,
    val checking: Boolean = false,
    val needsAppUpdate: Boolean = false,
    /** Debug/log details of the last check (incl. rejected entries). */
    val lastResult: String = "",
    val lastRejected: Int = 0,
    val confirmReset: Boolean = false,
)

data class UiState(
    val screen: Screen = Screen.CONNECT,
    val ready: Boolean = false,
    val dryRun: Boolean = true,
    val onboardingDone: Boolean = false,
    val conn: ConnState = ConnState.Idle,
    val devices: List<SavedDevice> = emptyList(),
    val scan: ScanState = ScanState(),
    val packages: List<PkgItem> = emptyList(),
    val loadingPackages: Boolean = false,
    val packagesError: String? = null,
    val query: String = "",
    val filter: Filter = Filter.ALL,
    val selected: Set<String> = emptySet(),
    val mem: MemInfo? = null,
    val storage: StorageInfo? = null,
    val batches: List<Batch> = emptyList(),
    val confirm: ConfirmRequest? = null,
    val run: RunState? = null,
    val message: String? = null,
    val showAggressiveWarning: Boolean = false,
    val db: DbUi = DbUi(),
    /** False when the connected model is not in the database "devices" list. */
    val deviceVerified: Boolean = true,
    val showDeviceNotice: Boolean = false,
    /** The aggressive profile is off on unverified devices until the user allows it. */
    val aggressiveAllowed: Boolean = true,
    /** Debug-only demo mode: sample data, nothing is ever sent over ADB. */
    val demo: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private companion object { const val TAG = "TvDebloater" }

    private val store = AppStore(app)
    private val adb = AdbManager(app.filesDir)
    private val scanner = SubnetScanner(app)
    private val bundledDb = PackageDbLoader.load(app)
    private val dbStore = DbStore(File(app.filesDir, "packagedb"))
    private val updater = DbUpdater(dbStore, UrlConnectionClient(), BuildConfig.VERSION_NAME)
    private var activeDb: ActiveDb = runCatching { dbStore.loadActive(bundledDb) }.getOrElse { ActiveDb(bundledDb, DbSource.BUNDLED) }
    private var classifier = Classifier(activeDb.db)
    private val turkish get() = Locale.getDefault().language == "tr"

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var connectJob: Job? = null
    private var dbJob: Job? = null

    init {
        _state.update { it.copy(db = dbUi(it.db)) }
        viewModelScope.launch {
            store.dbAutoUpdate.collect { v -> _state.update { if (it.demo) it else it.copy(db = it.db.copy(autoUpdate = v)) } }
        }
        // App start: check at most every 6 hours, only when automatic updates are on.
        dbJob = viewModelScope.launch {
            if (store.dbAutoUpdate.first() && withContext(Dispatchers.IO) { updater.isDue(System.currentTimeMillis()) }) runDbCheck(manual = false)
        }
        viewModelScope.launch {
            val done = store.onboardingDone.first()
            _state.update { if (it.demo) it.copy(ready = true) else it.copy(onboardingDone = done, screen = if (done) Screen.CONNECT else Screen.ONBOARDING, ready = true) }
        }
        viewModelScope.launch { store.dryRun.collect { v -> _state.update { if (it.demo) it else it.copy(dryRun = v) } } }
        viewModelScope.launch { store.devices.collect { v -> _state.update { if (it.demo) it else it.copy(devices = v) } } }
        viewModelScope.launch { store.batches.collect { v -> _state.update { if (it.demo) it else it.copy(batches = v.sortedByDescending { b -> b.timestamp }) } } }
    }

    // ---------------- navigation & settings ----------------

    fun go(screen: Screen) = _state.update { it.copy(screen = screen) }

    fun back(): Boolean {
        val s = _state.value
        val target = when (s.screen) {
            Screen.HISTORY, Screen.SETTINGS -> if (s.conn is ConnState.Connected) Screen.PACKAGES else Screen.CONNECT
            Screen.RESULTS -> if (s.run?.running == true) return true else if (s.conn is ConnState.Connected) Screen.PACKAGES else Screen.HISTORY
            Screen.ONBOARDING -> if (s.onboardingDone) Screen.CONNECT else return false
            else -> return false
        }
        go(target); return true
    }

    fun finishOnboarding() = viewModelScope.launch {
        store.setOnboardingDone(true)
        _state.update { it.copy(onboardingDone = true, screen = if (it.conn is ConnState.Connected) Screen.PACKAGES else Screen.CONNECT) }
    }

    fun setDryRun(v: Boolean) {
        if (_state.value.demo) _state.update { it.copy(dryRun = v) } else viewModelScope.launch { store.setDryRun(v) }
    }
    fun clearMessage() = _state.update { it.copy(message = null) }
    fun showMessage(m: String) = _state.update { it.copy(message = m) }

    // ---------------- demo mode (debug builds only) ----------------

    private var demoData: DemoSnapshot? = null
    val demoAvailable: Boolean get() = DemoData.available

    /** Fills the app with sample data. Does nothing in release builds. */
    fun loadDemo() {
        val d = DemoData.snapshot() ?: return
        demoData = d
        connectJob?.cancel(); scanJob?.cancel(); dbJob?.cancel()
        val items = d.packages.map { (pkg, st) -> toItem(pkg, st) }
            .sortedWith(compareBy<PkgItem>({ it.level.ordinal }, { it.label.lowercase() }))
        _state.update {
            it.copy(
                demo = true, ready = true, onboardingDone = true, screen = Screen.PACKAGES, conn = ConnState.Connected(d.device),
                devices = d.savedDevices, packages = items, selected = emptySet(), query = "", filter = Filter.ALL,
                mem = d.memBefore, storage = d.storageBefore, batches = d.batches, confirm = null, run = null,
                loadingPackages = false, packagesError = null, scan = ScanState(),
                db = dbUi(DbUi()).copy(
                    dbVersion = d.dbVersion, publishedAt = d.dbPublishedAt, source = DbSource.DOWNLOADED, lastCheckedAt = d.dbCheckedAt, checking = false,
                    autoUpdate = true, needsAppUpdate = false, lastResult = "updated to ${d.dbVersion}: +2 new, 3 changed, 1 rejected", lastRejected = 1,
                ),
                deviceVerified = true, showDeviceNotice = false, aggressiveAllowed = true,
            )
        }
    }

    /** Lets screenshot tests arrange a demo screen. Ignored outside demo mode. */
    fun demoArrange(f: (UiState) -> UiState) = _state.update { if (it.demo) f(it) else it }

    private fun runDemo(req: ConfirmRequest, dry: Boolean) {
        val d = demoData ?: return
        val results = req.steps.map { st ->
            if (dry) StepResult(st, null, st.command) else StepResult(st, true, demoOutput(st))
        }
        _state.update { s ->
            if (dry) return@update s.copy(confirm = null, screen = Screen.RESULTS, run = RunState(req, true, req.steps.size, false, results))
            val newState = req.steps.associate { it.packageName to stateAfter(it.action) }
            val batch = Batch(
                id = UUID.randomUUID().toString(), timestamp = d.batches.maxOf { it.timestamp } + 3_600_000, device = "${d.device.model} (${d.device.host})",
                kind = req.kind, action = req.action,
                entries = results.map { r -> BatchEntry(r.step.packageName, r.step.action, r.step.previousState, r.step.command, true, r.output, restores = r.step.restores) },
            )
            s.copy(
                confirm = null, screen = Screen.RESULTS, selected = emptySet(),
                packages = s.packages.map { p -> newState[p.packageName]?.let { p.copy(state = it) } ?: p },
                batches = listOf(batch) + s.batches.map { b ->
                    if (b.kind != BatchKind.CHANGE) b else b.copy(entries = b.entries.mapIndexed { i, e ->
                        if (req.steps.any { it.restores == "${b.id}#$i" }) e.copy(undone = true) else e
                    })
                }, mem = d.memAfter, storage = d.storageAfter,
                run = RunState(req, false, req.steps.size, false, results, d.memBefore, d.memAfter, d.storageBefore, d.storageAfter),
            )
        }
    }

    private fun stateAfter(a: PkgAction) = when (a) {
        PkgAction.DISABLE -> PkgState.DISABLED
        PkgAction.UNINSTALL_USER -> PkgState.UNINSTALLED
        PkgAction.ENABLE, PkgAction.INSTALL_EXISTING -> PkgState.ENABLED
    }

    private fun demoOutput(st: PlannedStep) = when (st.action) {
        PkgAction.DISABLE -> "Package ${st.packageName} new state: disabled-user"
        PkgAction.ENABLE -> "Package ${st.packageName} new state: enabled"
        PkgAction.UNINSTALL_USER -> "Success"
        PkgAction.INSTALL_EXISTING -> "Package ${st.packageName} installed for user: 0"
    }

    // ---------------- connection ----------------

    fun connect(host: String, port: Int) {
        val h = host.trim()
        if (h.isEmpty() || _state.value.demo) return
        connectJob?.cancel()
        _state.update { it.copy(conn = ConnState.Connecting(h, port)) }
        connectJob = viewModelScope.launch {
            try {
                val info = adb.connect(h, port)
                store.rememberDevice(SavedDevice(h, port, info.model, System.currentTimeMillis()))
                _state.update { it.copy(conn = ConnState.Connected(info), screen = Screen.PACKAGES, selected = emptySet()) }
                updateDeviceAwareness(info.model)
                refreshPackages()
            } catch (e: AdbError) {
                _state.update { it.copy(conn = ConnState.Failed(e.kind, e.detail, h, port)) }
            } catch (e: Exception) {
                _state.update { it.copy(conn = ConnState.Failed(AdbErrorKind.OTHER, e.message ?: e.javaClass.simpleName, h, port)) }
            }
        }
    }

    fun cancelConnect() {
        connectJob?.cancel()
        viewModelScope.launch { adb.disconnect() }
        _state.update { it.copy(conn = ConnState.Idle) }
    }

    fun disconnect() = viewModelScope.launch {
        adb.disconnect()
        val wasDemo = _state.value.demo
        demoData = null
        _state.update { it.copy(demo = false, conn = ConnState.Idle, screen = Screen.CONNECT, packages = emptyList(), selected = emptySet(), mem = null, storage = null, deviceVerified = true, showDeviceNotice = false, aggressiveAllowed = true) }
        if (wasDemo) _state.update {
            it.copy(devices = store.devices.first(), batches = store.batches.first().sortedByDescending { b -> b.timestamp }, db = dbUi(DbUi()).copy(autoUpdate = store.dbAutoUpdate.first()))
        }
    }

    fun forgetDevice(d: SavedDevice) = viewModelScope.launch { store.forgetDevice(d) }

    fun scan(port: Int) {
        if (_state.value.demo) return
        scanJob?.cancel()
        if (scanner.localIpv4() == null) {
            _state.update { it.copy(scan = ScanState(noWifi = true, finished = true)) }
            return
        }
        _state.update { it.copy(scan = ScanState(running = true)) }
        scanJob = viewModelScope.launch {
            val found = scanner.scan(port) { done, total ->
                if (done % 8 == 0 || done == total) _state.update { it.copy(scan = it.scan.copy(done = done, total = total)) }
            }
            _state.update { it.copy(scan = it.scan.copy(running = false, finished = true, found = found)) }
        }
    }

    private fun onAdbError(e: AdbError) {
        if (e.kind == AdbErrorKind.DISCONNECTED || e.kind == AdbErrorKind.NOT_CONNECTED || e.kind == AdbErrorKind.TIMEOUT) {
            val c = _state.value.conn
            val (h, p) = when (c) {
                is ConnState.Connected -> c.info.host to c.info.port
                else -> "" to AdbManager.DEFAULT_PORT
            }
            _state.update { it.copy(conn = ConnState.Failed(e.kind, e.detail, h, p), screen = if (it.screen == Screen.RESULTS) it.screen else Screen.CONNECT) }
        }
    }

    // ---------------- packages ----------------

    fun refreshPackages() = viewModelScope.launch { loadPackages() }

    private suspend fun loadPackages(): Boolean {
        if (_state.value.demo) return true
        _state.update { it.copy(loadingPackages = true, packagesError = null) }
        return try {
            val system = adb.shell(CommandBuilder.LIST_SYSTEM).output
            val disabled = adb.shell(CommandBuilder.LIST_DISABLED).output
            val all = adb.shell(CommandBuilder.LIST_SYSTEM_INCLUDING_UNINSTALLED).output
            val states = PackageListParser.buildStates(system, disabled, all)
            val items = states.map { (pkg, st) -> toItem(pkg, st) }
                .sortedWith(compareBy<PkgItem>({ it.level.ordinal }, { it.label.lowercase() }))
            _state.update { s -> s.copy(packages = items, loadingPackages = false, selected = s.selected.filter { p -> items.any { it.packageName == p && it.level != SafetyLevel.PROTECTED } }.toSet()) }
            refreshStats()
            true
        } catch (e: AdbError) {
            _state.update { it.copy(loadingPackages = false, packagesError = e.detail) }
            onAdbError(e); false
        } catch (e: Exception) {
            _state.update { it.copy(loadingPackages = false, packagesError = e.message) }
            false
        }
    }

    private fun toItem(pkg: String, st: PkgState): PkgItem {
        val c = classifier.classify(pkg)
        val e: PackageEntry? = c.entry
        val desc = e?.let { if (turkish) it.descriptionTr else it.descriptionEn } ?: ""
        return PkgItem(
            packageName = pkg,
            label = e?.label?.takeIf { it.isNotBlank() } ?: pkg,
            description = desc,
            level = c.level,
            unknown = c.unknown,
            state = st,
            aggressive = e?.aggressive == true && c.level == SafetyLevel.CAUTION,
            sources = e?.sources ?: emptyList(),
        )
    }

    private suspend fun readMem(): MemInfo? = try {
        PackageListParser.parseMemInfo(adb.shell(CommandBuilder.MEMINFO).output)
            ?: PackageListParser.parseMemInfo(adb.shell(CommandBuilder.MEMINFO_FALLBACK).output)
    } catch (e: AdbError) { if (e.kind != AdbErrorKind.OTHER) throw e; null }

    private suspend fun readStorage(): StorageInfo? = try {
        PackageListParser.parseDf(adb.shell(CommandBuilder.STORAGE).output)
    } catch (e: AdbError) { if (e.kind != AdbErrorKind.OTHER) throw e; null }

    fun refreshStatsAsync() = viewModelScope.launch { runCatching { refreshStats() }.onFailure { if (it is AdbError) onAdbError(it) } }

    private suspend fun refreshStats() {
        if (_state.value.demo) return
        val m = readMem(); val s = readStorage()
        _state.update { it.copy(mem = m, storage = s) }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun setFilter(f: Filter) = _state.update { it.copy(filter = f) }

    fun toggle(pkg: String) = _state.update { s ->
        val item = s.packages.firstOrNull { it.packageName == pkg } ?: return@update s
        if (item.level == SafetyLevel.PROTECTED) return@update s
        s.copy(selected = if (pkg in s.selected) s.selected - pkg else s.selected + pkg)
    }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    fun selectVisible(items: List<PkgItem>) = _state.update { s ->
        s.copy(selected = s.selected + items.filter { it.level != SafetyLevel.PROTECTED }.map { it.packageName })
    }

    /** Light profile: preselect SAFE packages that are still enabled. */
    fun applyLightProfile() = _state.update { s ->
        s.copy(selected = s.packages.filter { it.level == SafetyLevel.SAFE && it.state == PkgState.ENABLED }.map { it.packageName }.toSet(), filter = Filter.ALL)
    }

    fun requestAggressiveProfile() = _state.update { if (it.aggressiveAllowed) it.copy(showAggressiveWarning = true) else it }
    fun dismissAggressiveWarning() = _state.update { it.copy(showAggressiveWarning = false) }

    /** Aggressive profile: SAFE + the CAUTION packages flagged as aggressive candidates. */
    fun applyAggressiveProfile() = _state.update { s ->
        s.copy(
            showAggressiveWarning = false, filter = Filter.ALL,
            selected = s.packages.filter {
                it.state == PkgState.ENABLED && (it.level == SafetyLevel.SAFE || (it.level == SafetyLevel.CAUTION && it.aggressive))
            }.map { it.packageName }.toSet(),
        )
    }

    // ---------------- planning ----------------

    private fun step(item: PkgItem, action: PkgAction, restores: String? = null): PlannedStep? {
        val cmd = CommandBuilder.buildOrNull(action, item.packageName) ?: return null
        return PlannedStep(item.packageName, item.label, action, cmd, item.state, item.level, restores)
    }

    private fun itemFor(pkg: String, state: PkgState? = null): PkgItem =
        _state.value.packages.firstOrNull { it.packageName == pkg }?.let { if (state != null) it.copy(state = state) else it }
            ?: toItem(pkg, state ?: PkgState.UNKNOWN)

    /** Disable or remove the selected packages. Protected ones are refused by the command builder. */
    fun planChange(action: PkgAction) {
        val s = _state.value
        val chosen = s.packages.filter { it.packageName in s.selected }
        val targets = when (action) {
            PkgAction.DISABLE -> chosen.filter { it.state == PkgState.ENABLED }
            PkgAction.UNINSTALL_USER -> chosen.filter { it.state != PkgState.UNINSTALLED }
            else -> emptyList()
        }
        val steps = targets.mapNotNull { step(it, action) }
        val skipped = chosen.map { it.packageName } - steps.map { it.packageName }.toSet()
        if (steps.isEmpty()) { showMessage("NOTHING_APPLICABLE"); return }
        _state.update { it.copy(confirm = ConfirmRequest(BatchKind.CHANGE, action, steps, skipped)) }
    }

    /** Restore the selected packages that are disabled or removed. */
    fun planRestoreSelected() {
        val s = _state.value
        val chosen = s.packages.filter { it.packageName in s.selected }
        val steps = chosen.mapNotNull { item -> CommandBuilder.restoreAction(item.state)?.let { step(item, it) } }
        if (steps.isEmpty()) { showMessage("NOTHING_APPLICABLE"); return }
        _state.update { it.copy(confirm = ConfirmRequest(BatchKind.CHANGE, steps.first().action, steps, chosen.map { it.packageName } - steps.map { it.packageName }.toSet())) }
    }

    fun planUndoBatch(batch: Batch) = planUndo(batch, batch.entries.indices.toList())
    fun planUndoEntry(batch: Batch, index: Int) = planUndo(batch, listOf(index))

    private fun planUndo(batch: Batch, indices: List<Int>) {
        if (_state.value.conn !is ConnState.Connected) { showMessage("NOT_CONNECTED"); return }
        val steps = mutableListOf<PlannedStep>()
        for (i in indices) {
            val e = batch.entries.getOrNull(i) ?: continue
            if (e.undone || e.success == false) continue
            val item = itemFor(e.packageName)
            for (a in CommandBuilder.undoActions(e.action, e.previousState)) {
                step(item, a, "${batch.id}#$i")?.let { steps += it }
            }
        }
        if (steps.isEmpty()) { showMessage("NOTHING_TO_UNDO"); return }
        _state.update { it.copy(confirm = ConfirmRequest(BatchKind.UNDO, steps.first().action, steps)) }
    }

    /** Re-enable everything this app ever disabled or removed (based on the current device state). */
    fun planPanic() = viewModelScope.launch {
        if (_state.value.conn !is ConnState.Connected) { showMessage("NOT_CONNECTED"); return@launch }
        if (!loadPackages()) return@launch
        val touched = _state.value.batches
            .filter { it.kind == BatchKind.CHANGE }
            .flatMap { b -> b.entries.filter { it.success != false && it.action.isDestructive } }
            .map { it.packageName }.distinct()
        val states = _state.value.packages.associate { it.packageName to it.state }
        val steps = touched.flatMap { pkg ->
            val st = states[pkg] ?: PkgState.UNKNOWN
            val item = itemFor(pkg, st)
            CommandBuilder.panicActions(st).mapNotNull { step(item, it) }
        }
        if (steps.isEmpty()) { showMessage("NOTHING_TO_RESTORE"); return@launch }
        _state.update { it.copy(confirm = ConfirmRequest(BatchKind.PANIC, PkgAction.ENABLE, steps)) }
    }

    fun dismissConfirm() = _state.update { it.copy(confirm = null) }

    // ---------------- execution ----------------

    fun execute() {
        val req = _state.value.confirm ?: return
        val dry = _state.value.dryRun
        if (_state.value.demo) { runDemo(req, dry); return }
        val device = (_state.value.conn as? ConnState.Connected)?.info?.let { "${it.model} (${it.host})" } ?: "?"
        _state.update { it.copy(confirm = null, run = RunState(req, dry), screen = Screen.RESULTS) }
        viewModelScope.launch {
            val batchId = UUID.randomUUID().toString()
            var batch = Batch(
                id = batchId, timestamp = System.currentTimeMillis(), device = device, kind = req.kind, action = req.action,
                entries = req.steps.map { BatchEntry(it.packageName, it.action, it.previousState, it.command, restores = it.restores) },
            )
            try {
                if (!dry) {
                    val mb = readMem(); val sb = readStorage()
                    _state.update { it.copy(run = it.run?.copy(memBefore = mb, storageBefore = sb)) }
                    // Backup first: the record exists before anything changes.
                    store.saveBatch(batch)
                }
                val results = mutableListOf<StepResult>()
                for ((i, st) in req.steps.withIndex()) {
                    val r: StepResult = if (dry) {
                        StepResult(st, null, st.command)
                    } else {
                        try {
                            // Guard again right before sending, in case anything changed.
                            val cmd = CommandBuilder.build(st.action, st.packageName)
                            val out = adb.shell(cmd)
                            StepResult(st, CommandBuilder.isSuccess(st.action, out.exitCode, out.output), out.output.ifBlank { "exit ${out.exitCode}" })
                        } catch (e: AdbError) {
                            if (e.kind == AdbErrorKind.DISCONNECTED || e.kind == AdbErrorKind.NOT_CONNECTED || e.kind == AdbErrorKind.TIMEOUT) {
                                results += StepResult(st, false, e.detail)
                                batch = batch.copy(entries = batch.entries.mapIndexed { j, en -> if (j == i) en.copy(success = false, output = e.detail) else en })
                                store.saveBatch(batch)
                                throw e
                            }
                            StepResult(st, false, e.detail)
                        } catch (e: Exception) {
                            StepResult(st, false, e.message ?: e.javaClass.simpleName)
                        }
                    }
                    results += r
                    if (!dry) {
                        batch = batch.copy(entries = batch.entries.mapIndexed { j, en -> if (j == i) en.copy(success = r.success, output = r.output.take(2000)) else en })
                        store.saveBatch(batch)
                    }
                    _state.update { it.copy(run = it.run?.copy(done = i + 1, results = results.toList())) }
                }
                if (!dry) {
                    markRestored(batch)
                    val ma = readMem(); val sa = readStorage()
                    _state.update { it.copy(run = it.run?.copy(memAfter = ma, storageAfter = sa)) }
                    loadPackages()
                }
                _state.update { it.copy(run = it.run?.copy(running = false), selected = emptySet()) }
            } catch (e: AdbError) {
                _state.update { it.copy(run = it.run?.copy(running = false, fatalError = e.detail)) }
                onAdbError(e)
            } catch (e: Exception) {
                _state.update { it.copy(run = it.run?.copy(running = false, fatalError = e.message)) }
            }
        }
    }

    /** Marks original entries as undone when every restore step for them succeeded. */
    private suspend fun markRestored(batch: Batch) {
        if (batch.kind == BatchKind.CHANGE) return
        val okRefs = batch.entries.filter { it.restores != null }.groupBy { it.restores!! }
            .filterValues { list -> list.all { it.success == true } }.keys
        val okPkgs = if (batch.kind == BatchKind.PANIC) {
            batch.entries.groupBy { it.packageName }.filterValues { l -> l.all { it.success == true } }.keys
        } else emptySet()
        store.updateBatches { list ->
            list.map { b ->
                if (b.kind != BatchKind.CHANGE) b else b.copy(entries = b.entries.mapIndexed { i, e ->
                    val ref = "${b.id}#$i"
                    if (ref in okRefs || (e.packageName in okPkgs && e.action.isDestructive)) e.copy(undone = true) else e
                })
            }
        }
    }

    fun closeResults() = _state.update { it.copy(run = null, screen = if (it.conn is ConnState.Connected) Screen.PACKAGES else Screen.CONNECT) }

    // ---------------- package database ----------------

    private fun dbUi(old: DbUi): DbUi {
        val d = activeDb.db
        val meta = runCatching { dbStore.readMeta() }.getOrNull()
        return old.copy(
            dbVersion = d.dbVersion, publishedAt = d.publishedAt, source = activeDb.source,
            safe = d.packages.count { it.level == SafetyLevel.SAFE }, caution = d.packages.count { it.level == SafetyLevel.CAUTION },
            protected = d.packages.count { it.level == SafetyLevel.PROTECTED },
            lastCheckedAt = meta?.lastCheckedAt ?: 0, needsAppUpdate = meta?.needsAppUpdate == true,
            lastResult = meta?.lastResult ?: "", lastRejected = meta?.lastRejectedEntries ?: 0,
        )
    }

    /** Is [model] one of the devices the active database was verified on? */
    private fun isVerified(db: PackageDatabase, model: String): Boolean =
        db.devices.isEmpty() || db.devices.any { it.trim().equals(model.trim(), ignoreCase = true) }

    private suspend fun updateDeviceAwareness(model: String) {
        val verified = isVerified(activeDb.db, model)
        val dismissed = model in store.deviceNoticeDismissed.first()
        val allowed = verified || model in store.aggressiveAllowed.first()
        _state.update { it.copy(deviceVerified = verified, showDeviceNotice = !verified && !dismissed, aggressiveAllowed = allowed) }
    }

    fun dismissDeviceNotice() {
        val model = (_state.value.conn as? ConnState.Connected)?.info?.model ?: return
        _state.update { it.copy(showDeviceNotice = false) }
        if (!_state.value.demo) viewModelScope.launch { store.dismissDeviceNotice(model) }
    }

    /** Lets the user use the aggressive profile on an unverified device after reading the notice. */
    fun allowAggressiveProfile() {
        val model = (_state.value.conn as? ConnState.Connected)?.info?.model ?: return
        _state.update { it.copy(aggressiveAllowed = true, showDeviceNotice = false) }
        if (!_state.value.demo) viewModelScope.launch { store.allowAggressive(model); store.dismissDeviceNotice(model) }
    }

    fun setDbAutoUpdate(v: Boolean) {
        if (_state.value.demo) _state.update { it.copy(db = it.db.copy(autoUpdate = v)) } else viewModelScope.launch { store.setDbAutoUpdate(v) }
    }

    /** "Check for database updates" button. */
    fun checkDbNow() {
        if (_state.value.db.checking) return
        if (_state.value.demo) { showMessage("DB_UPDATED:2:3"); return }
        dbJob?.cancel()
        dbJob = viewModelScope.launch { runDbCheck(manual = true) }
    }

    private suspend fun runDbCheck(manual: Boolean) {
        _state.update { it.copy(db = it.db.copy(checking = true)) }
        val before = activeDb
        val r = withContext(Dispatchers.IO) { runCatching { updater.check(before, System.currentTimeMillis()) }.getOrElse { DbCheckResult.Failed(it.javaClass.simpleName) } }
        if (_state.value.demo) return
        Log.i(TAG, "Package database check: ${dbStore.readMeta().lastResult}")
        if (r is DbCheckResult.Updated) applyDb(r.active)
        _state.update { it.copy(db = dbUi(it.db).copy(checking = false)) }
        val msg = when (r) {
            is DbCheckResult.Updated -> "DB_UPDATED:${r.added}:${r.changed}"
            DbCheckResult.UpToDate -> if (manual) "DB_UP_TO_DATE" else null
            DbCheckResult.NeedsAppUpdate -> if (manual) "DB_NEEDS_APP" else null
            DbCheckResult.Offline -> if (manual) "DB_OFFLINE" else null
            DbCheckResult.RateLimited -> if (manual) "DB_RATE_LIMITED" else null
            is DbCheckResult.Failed -> if (manual) "DB_FAILED" else null
        }
        msg?.let { showMessage(it) }
    }

    /** Swaps the active database. Only labels, descriptions and profiles change; nothing is run. */
    private suspend fun applyDb(next: ActiveDb) {
        activeDb = next
        classifier = Classifier(next.db)
        _state.update { s ->
            val items = s.packages.map { toItem(it.packageName, it.state) }
                .sortedWith(compareBy<PkgItem>({ it.level.ordinal }, { it.label.lowercase() }))
            s.copy(packages = items, selected = s.selected.filter { p -> items.any { it.packageName == p && it.level != SafetyLevel.PROTECTED } }.toSet())
        }
        (_state.value.conn as? ConnState.Connected)?.info?.model?.let { updateDeviceAwareness(it) }
    }

    fun requestDbReset() = _state.update { it.copy(db = it.db.copy(confirmReset = true)) }
    fun dismissDbReset() = _state.update { it.copy(db = it.db.copy(confirmReset = false)) }

    /** "Reset to bundled list": deletes downloaded databases. History is not touched. */
    fun resetDb() {
        _state.update { it.copy(db = it.db.copy(confirmReset = false)) }
        if (_state.value.demo) { _state.update { it.copy(db = it.db.copy(source = DbSource.BUNDLED, dbVersion = bundledDb.dbVersion, publishedAt = bundledDb.publishedAt)) }; showMessage("DB_RESET"); return }
        dbJob?.cancel()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { dbStore.reset(System.currentTimeMillis()) }
            applyDb(ActiveDb(bundledDb, DbSource.BUNDLED))
            _state.update { it.copy(db = dbUi(it.db).copy(checking = false)) }
            showMessage("DB_RESET")
        }
    }

    /** Prefilled GitHub issue for "how did this package behave on my device". Opened in the browser. */
    fun reportUrl(p: PkgItem): String {
        val info = (_state.value.conn as? ConnState.Connected)?.info
        return ReportLink.build(p.packageName, p.level, _state.value.db.dbVersion, BuildConfig.VERSION_NAME, info?.model ?: "", info?.androidVersion ?: "")
    }

    // ---------------- export / import ----------------

    fun exportJson(): String = store.exportJson(_state.value.batches.sortedBy { it.timestamp })

    fun importJson(text: String, onDone: (Result<Int>) -> Unit) = viewModelScope.launch {
        onDone(runCatching { store.importJson(text) })
    }
}
