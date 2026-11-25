package com.lakefront.tvlauncher.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.tvprovider.media.tv.TvContractCompat
import com.lakefront.tvlauncher.R
import com.lakefront.tvlauncher.data.AppRepository
import com.lakefront.tvlauncher.data.LauncherPreferences
import com.lakefront.tvlauncher.data.MediaSessionRepository
import com.lakefront.tvlauncher.data.PinStore
import com.lakefront.tvlauncher.data.SystemStatusMonitor
import com.lakefront.tvlauncher.data.TvContentRepository
import com.lakefront.tvlauncher.data.WallpaperStore
import com.lakefront.tvlauncher.logic.AppEntry
import com.lakefront.tvlauncher.logic.ComponentKey
import com.lakefront.tvlauncher.logic.HomeArrangement
import com.lakefront.tvlauncher.logic.PinList
import com.lakefront.tvlauncher.logic.PinPolicy
import com.lakefront.tvlauncher.logic.ShortcutDecision
import com.lakefront.tvlauncher.logic.ShortcutMatch
import com.lakefront.tvlauncher.ui.components.warmAppIcons
import com.lakefront.tvlauncher.logic.Poster
import com.lakefront.tvlauncher.logic.PosterRow
import com.lakefront.tvlauncher.logic.MediaEntries
import com.lakefront.tvlauncher.logic.RadioState
import com.lakefront.tvlauncher.logic.SystemStatus
import com.lakefront.tvlauncher.logic.TvSnapshot
import com.lakefront.tvlauncher.logic.TvUnavailable
import com.lakefront.tvlauncher.logic.UninstallPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class Screen { Home, AllApps }

enum class SettingsTarget { Wifi, Bluetooth, System }

sealed interface Overlay {
    data object None : Overlay
    data object AddApps : Overlay
    data object Wallpaper : Overlay
    data class PinMenu(val key: ComponentKey) : Overlay
    data class AppMenu(val entry: AppEntry) : Overlay
}

data class DialogChoice(val id: String, val label: String)

data class DialogModel(
    val title: String,
    val message: String,
    val choices: List<DialogChoice>,
)

data class FocusSignal(val key: String, val nonce: Int)

data class LauncherUiState(
    val screen: Screen = Screen.Home,
    val overlay: Overlay = Overlay.None,
    val dialog: DialogModel? = null,
    val apps: List<AppEntry> = emptyList(),
    val pins: List<ComponentKey> = emptyList(),
    val files: ShortcutDecision = ShortcutDecision(),
    val store: ShortcutDecision = ShortcutDecision(),
    val appsLoaded: Boolean = false,
    val tv: TvSnapshot = TvSnapshot.Unavailable(TvUnavailable.Empty),
    val tvLoading: Boolean = true,
    /** Continue-watching row built from active media sessions, independent of TvProvider. */
    val continueRow: PosterRow? = null,
    /** Whether the user granted notification access, which the media sessions need. */
    val mediaAccess: Boolean = false,
    val wallpaperUri: String? = null,
    val status: SystemStatus = SystemStatus("--:--", RadioState.Absent, RadioState.Absent),
)

sealed interface LaunchCommand {
    data class View(val intent: Intent) : LaunchCommand
    data class Uninstall(val packageName: String) : LaunchCommand
    data class AppDetails(val packageName: String) : LaunchCommand
    data object PickWallpaper : LaunchCommand
    data object RequestBluetooth : LaunchCommand
    data object RequestListings : LaunchCommand
}

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val preferences = LauncherPreferences(app)
    private val pinStore = PinStore(preferences)
    private val appsRepo = AppRepository(app)
    private val tvRepo = TvContentRepository(app)
    private val mediaRepo = MediaSessionRepository(app)
    private val wallpapers = WallpaperStore(app, preferences)
    private val statusMonitor = SystemStatusMonitor(app)
    private val gate = Mutex()

    private val _state = MutableStateFlow(
        LauncherUiState(wallpaperUri = wallpapers.uri.value),
    )
    val state: StateFlow<LauncherUiState> = _state.asStateFlow()

    private val _focus = MutableStateFlow<FocusSignal?>(null)
    val focus: StateFlow<FocusSignal?> = _focus.asStateFlow()

    private val commandChannel = Channel<LaunchCommand>(Channel.BUFFERED)
    val commands = commandChannel.receiveAsFlow()

    var homeFocusKey: String = "tile:all"
        private set
    var allAppsFocusKey: String? = null
        private set

    private var focusNonce = 0
    private var didInitialFocus = false
    private var appsJob: Job? = null
    private var tvJob: Job? = null
    private var choiceApps: Map<String, AppEntry> = emptyMap()
    private var choiceKind: String? = null
    private var restoreFocusKey: String? = null
    private var awaitingReturn = false

    private val tvObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refreshTv()
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshApps()
        }
    }

    init {
        registerTvObserver()
        registerPackageReceiver()
        viewModelScope.launch {
            statusMonitor.observe().collect { snapshot ->
                _state.update { it.copy(status = snapshot) }
            }
        }
        viewModelScope.launch {
            wallpapers.uri.collect { uri ->
                _state.update { it.copy(wallpaperUri = uri) }
            }
        }
        refreshApps()
        refreshTv()
        refreshMedia()
    }

    /**
     * Continue-watching from media sessions. Cheap enough to run on every resume: it only walks
     * the sessions the system already tracks.
     */
    fun refreshMedia() {
        val app = getApplication<Application>()
        val enabled = mediaRepo.enabled()
        val row = if (enabled) {
            MediaEntries.toRow(mediaRepo.load(), app.getString(R.string.continue_watching))
        } else {
            null
        }
        _state.update { it.copy(continueRow = row, mediaAccess = enabled) }
    }

    fun onResume() {
        statusMonitor.poke()
        refreshApps()
        refreshTv()
        refreshMedia()
        if (!awaitingReturn) return
        awaitingReturn = false
        val key = restoreFocusKey ?: when (_state.value.screen) {
            Screen.AllApps -> allAppsFocusKey
            Screen.Home -> homeFocusKey
        }
        restoreFocusKey = null
        if (!key.isNullOrBlank()) requestFocus(key)
    }

    fun noteHomeFocus(key: String) {
        homeFocusKey = key
    }

    fun noteAllAppsFocus(key: String) {
        allAppsFocusKey = key
    }

    fun openAllApps() {
        _state.update { it.copy(screen = Screen.AllApps, overlay = Overlay.None) }
        val first = _state.value.apps.firstOrNull()?.id
        if (first != null) {
            allAppsFocusKey = allAppsFocusKey ?: first
            requestFocus(allAppsFocusKey ?: first)
        }
    }

    fun showHome() {
        _state.update { it.copy(screen = Screen.Home, overlay = Overlay.None, dialog = null) }
        requestFocus(homeFocusKey)
    }

    fun openAddApps() {
        _state.update { it.copy(overlay = Overlay.AddApps, dialog = null) }
    }

    fun openWallpaper() {
        restoreFocusKey = "status:wallpaper"
        _state.update { it.copy(overlay = Overlay.Wallpaper, dialog = null) }
    }

    fun openPinMenu(key: ComponentKey) {
        if (_state.value.pins.none { it == key }) return
        _state.update { it.copy(overlay = Overlay.PinMenu(key), dialog = null) }
    }

    fun openAppMenu(entry: AppEntry) {
        _state.update { it.copy(overlay = Overlay.AppMenu(entry), dialog = null) }
    }

    fun dismissOverlay() {
        val screen = _state.value.screen
        val key = HomeArrangement.focusAfterOverlay(
            restoreKey = restoreFocusKey,
            screenIsAllApps = screen == Screen.AllApps,
            allAppsKey = allAppsFocusKey,
            homeKey = homeFocusKey,
        )
        restoreFocusKey = null
        _state.update { it.copy(overlay = Overlay.None) }
        if (!key.isNullOrBlank()) requestFocus(key)
    }

    fun dismissDialog() {
        choiceApps = emptyMap()
        _state.update { it.copy(dialog = null) }
    }

    fun onDialogChoice(id: String) {
        val entry = choiceApps[id]
        val kind = choiceKind
        choiceApps = emptyMap()
        choiceKind = null
        _state.update { it.copy(dialog = null) }
        if (id == "app-settings") {
            armReturnFocus()
            commandChannel.trySend(
                LaunchCommand.View(
                    Intent(Settings.ACTION_APPLICATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                ),
            )
            return
        }
        if (entry == null) return
        when (kind) {
            "files" -> preferences.fileManagerKey = entry.key
            "store" -> preferences.storeKey = entry.key
        }
        if (kind == "files" || kind == "store") refreshApps()
        launchEntry(entry)
    }

    fun pin(entry: AppEntry) {
        val state = _state.value
        if (PinPolicy.isFixedHomePackage(entry.packageName, state.files.tile?.packageName, state.store.tile?.packageName)) {
            showNotice(entry.label, text(R.string.already_on_home))
            return
        }
        updatePins(PinPolicy.toggle(state.pins, entry.key))
    }

    fun movePin(key: ComponentKey, delta: Int) {
        val next = PinList.move(_state.value.pins, key, delta)
        if (next == _state.value.pins) return
        updatePins(next)
    }

    fun removePin(key: ComponentKey) {
        val current = _state.value.pins
        val index = current.indexOf(key)
        val next = PinList.remove(current, key)
        updatePins(next)
        val neighbor = when {
            next.isEmpty() -> "tile:add"
            index <= 0 -> "pin:${next.first().flatten()}"
            else -> "pin:${next[(index - 1).coerceAtMost(next.lastIndex)].flatten()}"
        }
        homeFocusKey = neighbor
        _state.update { it.copy(overlay = Overlay.None) }
        requestFocus(neighbor)
    }

    fun openFileManagers() = openShortcut(
        decision = _state.value.files,
        kind = "files",
        missingTitle = text(R.string.file_manager_missing_title),
        missingBody = text(R.string.file_manager_missing_body),
        chooserTitle = text(R.string.choose_file_manager),
    )

    fun openStores() = openShortcut(
        decision = _state.value.store,
        kind = "store",
        missingTitle = text(R.string.store_missing_title),
        missingBody = text(R.string.store_missing_body),
        chooserTitle = text(R.string.choose_store),
    )

    fun openPoster(poster: Poster) {
        val uri = poster.intentUri
        if (uri.isNullOrBlank()) {
            showNotice(text(R.string.cant_open_title), text(R.string.cant_open_body))
            return
        }
        val intent = try {
            Intent.parseUri(uri, Intent.URI_INTENT_SCHEME or Intent.URI_ANDROID_APP_SCHEME)
        } catch (_: Exception) {
            showNotice(text(R.string.cant_open_title), text(R.string.cant_open_body))
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.selector = null
        intent.removeFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
        )
        armReturnFocus()
        commandChannel.trySend(LaunchCommand.View(intent))
    }

    fun uninstall(entry: AppEntry) {
        val reason = UninstallPolicy.blockReason(
            isSelf = entry.packageName == getApplication<Application>().packageName,
            isSystem = entry.system,
            isUpdatedSystem = entry.updatedSystem,
        )
        if (reason != null) {
            showNotice(text(R.string.cant_uninstall_title), reason)
            return
        }
        _state.update { it.copy(overlay = Overlay.None) }
        armReturnFocus()
        commandChannel.trySend(LaunchCommand.Uninstall(entry.packageName))
    }

    fun appInfo(entry: AppEntry) {
        _state.update { it.copy(overlay = Overlay.None) }
        armReturnFocus()
        commandChannel.trySend(LaunchCommand.AppDetails(entry.packageName))
    }

    fun openSettings(target: SettingsTarget) {
        if (target == SettingsTarget.Bluetooth && _state.value.status.bluetooth == RadioState.NeedsPermission) {
            commandChannel.trySend(LaunchCommand.RequestBluetooth)
            return
        }
        val action = when (target) {
            SettingsTarget.Wifi -> Settings.ACTION_WIFI_SETTINGS
            SettingsTarget.Bluetooth -> Settings.ACTION_BLUETOOTH_SETTINGS
            SettingsTarget.System -> Settings.ACTION_SETTINGS
        }
        armReturnFocus()
        commandChannel.trySend(
            LaunchCommand.View(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)),
        )
    }

    fun onBluetoothPermissionResult(granted: Boolean) {
        statusMonitor.poke()
        if (!granted) {
            showNotice(text(R.string.bluetooth_denied_title), text(R.string.bluetooth_denied_body))
            return
        }
        commandChannel.trySend(
            LaunchCommand.View(
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ),
        )
    }

    fun onListingsPermissionResult() {
        refreshTv()
    }

    fun requestListingsIfNeeded() {
        val granted = TvContentRepository.LISTING_PERMISSIONS.any { name ->
            getApplication<Application>().checkSelfPermission(name) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (!granted) commandChannel.trySend(LaunchCommand.RequestListings)
    }

    fun pickWallpaper() {
        restoreFocusKey = "status:wallpaper"
        awaitingReturn = true
        commandChannel.trySend(LaunchCommand.PickWallpaper)
    }

    fun onWallpaperPickerFinished(uri: Uri?) {
        if (uri != null) setCustomWallpaper(uri)
        restoreFocusKey = null
        awaitingReturn = false
        _state.update { it.copy(overlay = Overlay.None, dialog = null) }
        requestFocus("status:wallpaper")
    }

    fun showAppDetailsUnavailable() {
        choiceKind = "app-settings"
        choiceApps = emptyMap()
        _state.update {
            it.copy(
                dialog = DialogModel(
                    title = text(R.string.app_info),
                    message = text(R.string.app_settings_explains),
                    choices = listOf(
                        DialogChoice("app-settings", text(R.string.open_app_settings)),
                        DialogChoice("dismiss", text(R.string.close)),
                    ),
                ),
            )
        }
    }

    fun showUninstallUnavailable() {
        showNotice(text(R.string.uninstall), text(R.string.uninstall_unavailable))
    }

    fun setCustomWallpaper(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            wallpapers.setCustom(uri)
        }
    }

    fun useDefaultWallpaper() {
        viewModelScope.launch(Dispatchers.IO) {
            wallpapers.useDefault()
        }
    }

    fun showLaunchFailure() {
        showNotice(text(R.string.cant_open_title), text(R.string.cant_open_body))
    }

    fun showSettingsFailure() {
        showNotice(text(R.string.cd_settings), text(R.string.settings_unavailable))
    }

    fun refreshApps() {
        appsJob?.cancel()
        appsJob = viewModelScope.launch {
            delay(80)
            gate.withLock {
                val catalog = withContext(Dispatchers.IO) {
                    appsRepo.load(
                        rememberedFile = preferences.fileManagerKey,
                        rememberedStore = preferences.storeKey,
                    )
                }
                val base = if (_state.value.appsLoaded) {
                    _state.value.pins
                } else {
                    withContext(Dispatchers.IO) { pinStore.read() }
                }
                val installed = catalog.apps.map { it.key }
                val reconciled = PinPolicy.withoutFixed(
                    pins = PinList.reconcile(base, installed),
                    filePackage = catalog.files.tile?.packageName,
                    storePackage = catalog.store.tile?.packageName,
                )
                withContext(Dispatchers.IO) { pinStore.write(reconciled) }
                val lostFocus = homeFocusKey.startsWith("pin:") &&
                    reconciled.none { "pin:${it.flatten()}" == homeFocusKey }
                _state.update {
                    it.copy(
                        apps = catalog.apps,
                        pins = reconciled,
                        files = catalog.files,
                        store = catalog.store,
                        appsLoaded = true,
                    )
                }
                if (lostFocus) {
                    homeFocusKey = "tile:add"
                    requestFocus("tile:add")
                }
                maybeInitialFocus()
                val warm = catalog.apps
                viewModelScope.launch(Dispatchers.IO) {
                    warmAppIcons(getApplication(), warm)
                }
            }
        }
    }

    fun refreshTv() {
        tvJob?.cancel()
        tvJob = viewModelScope.launch {
            delay(160)
            val input = withContext(Dispatchers.IO) { tvRepo.load() }
            val snapshot = TvContentRepository.snapshot(input)
            _state.update { it.copy(tv = snapshot, tvLoading = false) }
            maybeInitialFocus()
        }
    }

    override fun onCleared() {
        val app = getApplication<Application>()
        runCatching { app.contentResolver.unregisterContentObserver(tvObserver) }
        runCatching { app.unregisterReceiver(packageReceiver) }
        super.onCleared()
    }

    private fun maybeInitialFocus() {
        val state = _state.value
        if (didInitialFocus || !state.appsLoaded || state.tvLoading) return
        if (state.screen != Screen.Home || state.overlay != Overlay.None) return
        didInitialFocus = true
        val firstPoster = (state.tv as? TvSnapshot.Ready)?.rows?.firstOrNull()?.posters?.firstOrNull()
        val key = firstPoster?.id ?: "tile:all"
        homeFocusKey = key
        requestFocus(key)
    }

    private fun requestFocus(key: String) {
        focusNonce += 1
        _focus.value = FocusSignal(key, focusNonce)
    }

    private fun updatePins(next: List<ComponentKey>) {
        _state.update { it.copy(pins = next) }
        viewModelScope.launch {
            gate.withLock {
                withContext(Dispatchers.IO) { pinStore.write(next) }
            }
        }
    }

    private fun openShortcut(
        decision: ShortcutDecision,
        kind: String,
        missingTitle: String,
        missingBody: String,
        chooserTitle: String,
    ) {
        val target = decision.launch
        when {
            target != null -> launchEntry(target)
            decision.choices.size > 1 -> {
                choiceKind = kind
                choiceApps = decision.choices.associateBy { it.id }
                _state.update {
                    it.copy(
                        dialog = DialogModel(
                            title = chooserTitle,
                            message = "",
                            choices = decision.choices.map { entry -> DialogChoice(entry.id, entry.label) } +
                                DialogChoice("dismiss", text(R.string.close)),
                        ),
                    )
                }
            }
            else -> showNotice(missingTitle, missingBody)
        }
    }

    private fun armReturnFocus() {
        awaitingReturn = true
        if (restoreFocusKey == null) {
            restoreFocusKey = when (_state.value.screen) {
                Screen.AllApps -> allAppsFocusKey
                Screen.Home -> homeFocusKey
            }
        }
    }

    fun launch(entry: AppEntry) = launchEntry(entry)

    private fun launchEntry(entry: AppEntry) {
        val intent = Intent(ShortcutMatch.preferredLaunchAction(entry.packageName) ?: Intent.ACTION_MAIN).apply {
            component = ComponentName(entry.packageName, entry.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        armReturnFocus()
        commandChannel.trySend(LaunchCommand.View(intent))
    }

    private fun showNotice(title: String, message: String) {
        choiceApps = emptyMap()
        _state.update {
            it.copy(
                dialog = DialogModel(
                    title = title,
                    message = message,
                    choices = listOf(DialogChoice("dismiss", text(R.string.ok))),
                ),
            )
        }
    }

    private fun text(id: Int): String = getApplication<Application>().getString(id)

    private fun registerTvObserver() {
        val resolver = getApplication<Application>().contentResolver
        val uris = listOf(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            TvContractCompat.PreviewPrograms.CONTENT_URI,
            TvContractCompat.Channels.CONTENT_URI,
        )
        for (uri in uris) {
            runCatching { resolver.registerContentObserver(uri, true, tvObserver) }
        }
    }

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        val app = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= 33) {
            app.registerReceiver(packageReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            app.registerReceiver(packageReceiver, filter)
        }
    }
}
