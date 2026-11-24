package com.lakefront.tvlauncher

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lakefront.tvlauncher.data.SystemStatusMonitor
import com.lakefront.tvlauncher.data.TvContentRepository
import com.lakefront.tvlauncher.logic.ComponentKey
import com.lakefront.tvlauncher.logic.MenuKeyPolicy
import com.lakefront.tvlauncher.logic.PackageUris
import com.lakefront.tvlauncher.logic.SettingsTargets
import com.lakefront.tvlauncher.logic.UninstallPolicy
import com.lakefront.tvlauncher.logic.UninstallTargets
import com.lakefront.tvlauncher.ui.AllAppsScreen
import com.lakefront.tvlauncher.ui.HomeScreen
import com.lakefront.tvlauncher.ui.LakeIcons
import com.lakefront.tvlauncher.ui.LaunchCommand
import com.lakefront.tvlauncher.ui.LauncherUiState
import com.lakefront.tvlauncher.ui.LauncherViewModel
import com.lakefront.tvlauncher.ui.Overlay
import com.lakefront.tvlauncher.ui.Screen
import com.lakefront.tvlauncher.ui.SettingsTarget
import com.lakefront.tvlauncher.ui.components.ActionMenu
import com.lakefront.tvlauncher.ui.components.AddAppsPanel
import com.lakefront.tvlauncher.ui.components.AppIconImage
import com.lakefront.tvlauncher.ui.components.InstalledGlyph
import com.lakefront.tvlauncher.ui.components.MenuAction
import com.lakefront.tvlauncher.ui.components.NoticeDialog
import com.lakefront.tvlauncher.ui.components.Scrim
import com.lakefront.tvlauncher.ui.components.StatusPill
import com.lakefront.tvlauncher.ui.components.WallpaperPanel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    internal var onMenuKey: (() -> Unit)? = null

    /**
     * Consume Menu on both down and up when this activity receives it.
     * Some TV firmwares open their own picture menu from Menu before the
     * activity sees the event. That cannot be blocked here; long-press OK
     * still opens this launcher's menu.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (MenuKeyPolicy.shouldConsume(keyCode)) {
            if (event.repeatCount == 0) onMenuKey?.invoke()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (MenuKeyPolicy.shouldConsume(keyCode)) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LakeTheme {
                val viewModel: LauncherViewModel = viewModel()
                LauncherApp(viewModel)
            }
        }
    }

}

@Composable
private fun LakeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color.Transparent,
            surface = Color(0xFF161A22),
            onSurface = Color.White,
            border = Color.White,
        ),
        content = content,
    )
}

@Composable
private fun LauncherApp(viewModel: LauncherViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus by viewModel.focus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val requesters = remember { mutableStateMapOf<String, FocusRequester>() }
    val statusRequester = remember { FocusRequester() }
    val wallpaperRequester = remember { FocusRequester() }
    val usbRequester = remember { FocusRequester() }
    val settingsRequester = remember { FocusRequester() }
    androidx.compose.runtime.SideEffect {
        requesters["status:wallpaper"] = wallpaperRequester
        requesters["status:usb"] = usbRequester
        requesters["status:settings"] = settingsRequester
        (activity as MainActivity).onMenuKey = { onMenu(state, viewModel) }
    }

    val wallpaperPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        PersistableOpenDocument(),
    ) { uri ->
        viewModel.onWallpaperPickerFinished(uri)
    }
    val listingsPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.onListingsPermissionResult()
    }
    val bluetoothPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onBluetoothPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        viewModel.commands.collect { command ->
            when (command) {
                is LaunchCommand.View -> activity.startSafely(command.intent, viewModel)
                is LaunchCommand.Uninstall -> (activity as MainActivity).launchUninstall(command.packageName, viewModel)
                is LaunchCommand.AppDetails -> (activity as MainActivity).launchAppDetails(command.packageName, viewModel)
                LaunchCommand.PickWallpaper -> wallpaperPicker.launch(arrayOf("image/*"))
                LaunchCommand.RequestBluetooth -> {
                    bluetoothPermission.launch(SystemStatusMonitor.BLUETOOTH_CONNECT)
                }
                LaunchCommand.RequestListings -> listingsPermission.launch(TvContentRepository.LISTING_PERMISSIONS)
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.requestListingsIfNeeded()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(focus, state.screen, state.overlay, state.dialog) {
        val signal = focus ?: return@LaunchedEffect
        if (state.overlay != Overlay.None || state.dialog != null) return@LaunchedEffect
        val requester = withTimeoutOrNull(600) {
            snapshotFlow { requesters[signal.key] }.filterNotNull().first()
        }
        runCatching { requester?.requestFocus() }
    }

    BackHandler {
        when {
            state.dialog != null -> viewModel.dismissDialog()
            state.overlay != Overlay.None -> viewModel.dismissOverlay()
            state.screen == Screen.AllApps -> viewModel.showHome()
            !context.isDefaultHome() -> activity.finish()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Menu) {
                    onMenu(state, viewModel)
                    true
                } else {
                    false
                }
            },
    ) {
        LakeBackground(state.wallpaperUri)
        Column(Modifier.fillMaxSize()) {
            if (state.screen == Screen.AllApps) {
                Text(
                    text = stringResource(R.string.all_apps_heading),
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 48.dp, end = 240.dp, top = 22.dp, bottom = 8.dp),
                )
                AllAppsScreen(
                    apps = state.apps,
                    focusSignal = focus,
                    requesters = requesters,
                    onLaunch = viewModel::launch,
                    onMenu = viewModel::openAppMenu,
                    onFocused = viewModel::noteAllAppsFocus,
                    modifier = Modifier.weight(1f),
                )
            } else {
                HomeScreen(
                    state = state,
                    requesters = requesters,
                    statusRequester = statusRequester,
                    onPoster = viewModel::openPoster,
                    onOpenAllApps = viewModel::openAllApps,
                    onOpenFiles = viewModel::openFileManagers,
                    onOpenStore = viewModel::openStores,
                    onOpenAdd = viewModel::openAddApps,
                    onLaunch = viewModel::launch,
                    onPinMenu = viewModel::openPinMenu,
                    onHomeFocus = viewModel::noteHomeFocus,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        StatusPill(
            status = state.status,
            onWifi = { viewModel.openSettings(SettingsTarget.Wifi) },
            onBluetooth = { viewModel.openSettings(SettingsTarget.Bluetooth) },
            onUsb = viewModel::openFileManagers,
            onWallpaper = viewModel::openWallpaper,
            onSettings = { viewModel.openSettings(SettingsTarget.System) },
            firstRequester = statusRequester,
            wallpaperRequester = wallpaperRequester,
            usbRequester = usbRequester,
            settingsRequester = settingsRequester,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 28.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                        val key = if (state.screen == Screen.AllApps) {
                            viewModel.allAppsFocusKey ?: state.apps.firstOrNull()?.id
                        } else {
                            viewModel.homeFocusKey
                        }
                        if (key != null) runCatching { requesters[key]?.requestFocus() }
                        true
                    } else {
                        false
                    }
                },
        )

        val overlay = state.overlay
        if (overlay != Overlay.None || state.dialog != null) {
            val dim = if (overlay is Overlay.AppMenu || overlay is Overlay.PinMenu) 0.42f else 0.25f
            Scrim(if (state.dialog != null) 0.45f else dim)
        }
        when (val current = state.overlay) {
            Overlay.None -> Unit
            Overlay.AddApps -> {
                val panelWidth = if (maxWidth < 760.dp) maxWidth - 36.dp else 540.dp
                AddAppsPanel(
                    apps = state.apps,
                    pinned = state.pins.toSet(),
                    fixedPackages = setOfNotNull(state.files.tile?.packageName, state.store.tile?.packageName),
                    onPin = viewModel::pin,
                    onClose = viewModel::dismissOverlay,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 76.dp, bottom = 20.dp, end = 24.dp)
                        .width(panelWidth)
                        .fillMaxHeight(),
                )
            }
            Overlay.Wallpaper -> {
                WallpaperPanel(
                    customUri = state.wallpaperUri,
                    onChoose = viewModel::pickWallpaper,
                    onUseDefault = viewModel::useDefaultWallpaper,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            is Overlay.PinMenu -> {
                ActionMenu(
                    title = state.apps.firstOrNull { it.key == current.key }?.label ?: current.key.packageName,
                    icon = {
                        val entry = state.apps.firstOrNull { it.key == current.key }
                        if (entry != null) {
                            InstalledGlyph(entry, padding = 4.dp)
                        }
                    },
                    rows = listOf(
                        MenuAction(stringResource(R.string.move_left), { viewModel.movePin(current.key, -1) }),
                        MenuAction(stringResource(R.string.move_right), { viewModel.movePin(current.key, 1) }),
                        MenuAction(stringResource(R.string.remove_from_home), { viewModel.removePin(current.key) }),
                    ),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 48.dp),
                )
            }
            is Overlay.AppMenu -> {
                val entry = current.entry
                val blocked = UninstallPolicy.blockReason(
                    isSelf = entry.packageName == context.packageName,
                    isSystem = entry.system,
                    isUpdatedSystem = entry.updatedSystem,
                )
                ActionMenu(
                    title = entry.label,
                    icon = {
                        AppIconImage(entry.packageName, entry.activityName, Modifier.fillMaxSize())
                    },
                    rows = listOf(
                        MenuAction(
                            label = stringResource(R.string.uninstall),
                            detail = blocked,
                            icon = LakeIcons.Trash,
                            danger = blocked == null,
                            onClick = { viewModel.uninstall(entry) },
                        ),
                        MenuAction(
                            label = stringResource(R.string.app_info),
                            icon = LakeIcons.Info,
                            onClick = { viewModel.appInfo(entry) },
                        ),
                    ),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 36.dp),
                )
            }
        }
        state.dialog?.let { dialog ->
            NoticeDialog(
                dialog = dialog,
                onChoice = viewModel::onDialogChoice,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun LakeBackground(customUri: String?) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.bg_default_wallpaper),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (customUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(customUri)
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.26f),
                        0.22f to Color.Black.copy(alpha = 0.06f),
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.30f),
                    ),
                ),
        )
    }
}

private fun onMenu(state: LauncherUiState, viewModel: LauncherViewModel) {
    if (state.dialog != null || state.overlay != Overlay.None) return
    when (state.screen) {
        Screen.Home -> {
            val key = viewModel.homeFocusKey
            if (key.startsWith("pin:")) {
                val parsed = ComponentKey.parse(key.removePrefix("pin:")) ?: return
                viewModel.openPinMenu(parsed)
            }
        }
        Screen.AllApps -> {
            val id = viewModel.allAppsFocusKey ?: return
            val entry = state.apps.firstOrNull { it.id == id } ?: return
            viewModel.openAppMenu(entry)
        }
    }
}

private fun Context.isDefaultHome(): Boolean {
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val resolved = packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
    return resolved?.activityInfo?.packageName == packageName
}

private fun Activity.startSafely(intent: Intent, viewModel: LauncherViewModel) {
    try {
        startActivity(intent)
    } catch (error: Exception) {
        Log.w(TAG, "Could not start ${intent.action}", error)
        val specificSettings = intent.action == Settings.ACTION_WIFI_SETTINGS ||
            intent.action == Settings.ACTION_BLUETOOTH_SETTINGS
        if (specificSettings) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (fallback: Exception) {
                Log.w(TAG, "Settings fallback failed", fallback)
            }
        }
        if (intent.action?.startsWith("android.settings.") == true) {
            viewModel.showSettingsFailure()
        } else {
            viewModel.showLaunchFailure()
        }
    }
}

private fun Activity.launchUninstall(packageName: String, viewModel: LauncherViewModel) {
    val data = PackageUris.opaquePackage(packageName)
    val pkg = packageName.trim()
    val uri = if (data == null) null else Uri.fromParts("package", pkg, null)
    if (data == null || uri == null || uri.schemeSpecificPart != pkg) {
        Log.w(TAG, "Refusing uninstall with data ${uri?.schemeSpecificPart ?: "empty"}")
        viewModel.showUninstallUnavailable()
        return
    }
    val intents = listOf(
        Intent("android.intent.action.UNINSTALL_PACKAGE", uri),
        Intent(Intent.ACTION_DELETE, uri),
    )
    for (intent in intents) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.hasExtra(Intent.EXTRA_RETURN_RESULT)) continue
        val resolved = packageManager.resolveActivity(intent, 0) ?: continue
        val className = resolved.activityInfo?.name ?: continue
        if (SettingsTargets.isSettingsHomeClass(className)) {
            Log.i(TAG, "Skip uninstall handler $className")
            continue
        }
        intent.component = ComponentName(resolved.activityInfo.packageName, className)
        try {
            Log.i(TAG, "Uninstall data=$uri via $className")
            startActivity(intent)
            return
        } catch (error: Exception) {
            Log.w(TAG, "Uninstall start failed for $className", error)
        }
    }
    Log.w(TAG, "No system uninstall confirmation for $packageName")
    viewModel.showUninstallUnavailable()
}

private fun Activity.launchAppDetails(packageName: String, viewModel: LauncherViewModel) {
    val explicit = listOf(
        ComponentName("com.android.settings", "com.android.settings.applications.InstalledAppDetails"),
        ComponentName("com.android.settings", "com.android.settings.applications.InstalledAppDetailsTop"),
        ComponentName("com.android.tv.settings", "com.android.tv.settings.device.apps.AppManagementActivity"),
    )
    for (component in explicit) {
        val info = try {
            packageManager.getActivityInfo(component, 0)
        } catch (_: Exception) {
            null
        } ?: continue
        if (!info.exported || !SettingsTargets.isAppDetailsClass(component.className)) continue
        val intent = detailsIntent(packageName).setComponent(component)
        try {
            Log.i(TAG, "App details $packageName via ${component.className}")
            startActivity(intent)
            return
        } catch (error: Exception) {
            Log.w(TAG, "App details failed for ${component.className}", error)
        }
    }
    val generic = detailsIntent(packageName)
    val resolved = packageManager.resolveActivity(generic, 0)
    val className = resolved?.activityInfo?.name
    if (className != null &&
        SettingsTargets.isAppDetailsClass(className) &&
        !SettingsTargets.isSettingsHomeClass(className)
    ) {
        generic.component = ComponentName(resolved.activityInfo.packageName, className)
        try {
            startActivity(generic)
            return
        } catch (error: Exception) {
            Log.w(TAG, "Resolved app details failed", error)
        }
    } else {
        Log.w(TAG, "Rejected app-details handler ${className ?: "none"} for $packageName")
    }
    viewModel.showAppDetailsUnavailable()
}

private fun detailsIntent(packageName: String): Intent {
    return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        putExtra("com.android.settings.ApplicationPkgName", packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private const val TAG = "LakeTv"

private class PersistableOpenDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        return super.createIntent(context, input).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
        )
    }
}
