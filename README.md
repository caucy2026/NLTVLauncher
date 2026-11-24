# Lake TV Launcher

A native Android TV home screen. It is a normal Android app written in Kotlin and Jetpack Compose for TV. It is not a website and it does not use a WebView.

The home screen follows the approved feed-row layout: Watch Next, recommendation rows, and an Apps row on the mountain-lake wallpaper. Pill Dock, Centre Stage, and Bookends are not implemented.

## Identity

| | |
| --- | --- |
| Display name | Lake TV Launcher |
| applicationId | `com.lakefront.tvlauncher` |
| minSdk | 26 (Android 8.0) |
| targetSdk / compileSdk | 35 |
| UI | Jetpack Compose with `androidx.tv:tv-material` 1.0.0 |
| Signing | debug only (the Android Gradle debug keystore) |

minSdk is 26 because the system Watch Next and preview-channel tables (`TvContract` / AndroidX `TvContractCompat`) were added in Android 8.0. Earlier releases do not have those providers. Both 720p and 1080p Android TV devices normally lay out at about 960×540 dp, so the same dp layout is what scales between those resolutions. The All Apps grid uses 6 columns at that width and fewer columns on narrower screens.

The app does not request to become the default home by itself. Android shows it in the normal Home picker because the activity handles `MAIN` / `HOME` / `DEFAULT` and `LEANBACK_LAUNCHER`.

## Build

Install Android SDK command-line tools, platform 35, and build-tools 35 (34 is also pulled in by the Android Gradle Plugin). Point `ANDROID_HOME` or `local.properties` (`sdk.dir`) at that SDK. JDK 17 or 21 is enough.

```bash
export ANDROID_HOME="$HOME/android-sdk"
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The debug APK is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it yourself if you want to try it. Nothing in this project installs the APK, replaces the current home app, or uploads the APK.

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

To use it as the home screen, open system Settings and choose Lake TV Launcher as the Home app (wording varies by device: Device preferences, Default apps, or Home screen). To go back, choose the previous home app the same way.

## What the home screen does

- Landscape UI with D-pad, OK, Back, and Menu. Back on Home does nothing while this app is the default launcher. Otherwise Back leaves the activity. Back from All Apps returns home. Back closes the Add apps panel, wallpaper chooser, menus, and notices.
- Status pill: local time, Wi-Fi state, Bluetooth state, wallpaper, and system Settings. Bluetooth permission is requested only when the Bluetooth icon is opened on Android 12+. Devices without Wi-Fi or Bluetooth hardware do not crash; the missing radio is omitted.
- Apps row: All Apps, File Manager, App Store, pinned apps, and a dashed Add tile. File Manager matches installed file-browser apps, including localized names such as 文件管理器, and ignores document pickers. Play Store opens directly when it is installed. Another store is used only when Play Store is absent, and a chooser is shown only when several remain. The chosen handler is remembered. The home tile uses that app's real name and icon.
- Add apps lists real launchable packages (leanback activities preferred, otherwise a normal launcher activity). Choosing one pins it in front of Add. Choosing it again removes that pin. File Manager and the store shortcut, when they are already the home tiles, show a check and stay fixed. Pins are stored in app preferences. Menu or a long-press on a pinned tile can move it or remove it from home. That does not uninstall the app. All Apps and Add stay fixed.
- All Apps is a full page: heading, the same wallpaper and status pill, and a grid of real icons and labels. There is no search box, back hint, Add tile, or Settings tile.
- Menu on an All Apps icon opens Uninstall and App info. The activity consumes the Menu key on both press and release. Some TV firmwares open their own picture menu from Menu before the app receives the key; that cannot be blocked without system privileges. Long-press OK still opens this menu.
- Uninstall starts Android's normal confirmation screen and does not remove an app silently. If the device has no uninstaller activity, a message says so. System apps and this launcher explain why they cannot be uninstalled. App info opens a real application-details activity. If the only match is the settings home screen, it is not opened.
- Watch Next and browsable preview channels are read through `androidx.tvprovider` from `content://android.media.tv`. The manifest declares `android.permission.READ_TV_LISTINGS` and the older `com.android.providers.tv.permission.READ_TV_LISTINGS`. Rows the provider will not show this app are not invented. Empty, denied, and missing-provider cases show a short explanation instead of fake posters. Continue progress is drawn only when the provider includes a position and duration. Artwork that fails to load leaves a plain card. Dead intent URIs are caught when opened.
- Wallpaper uses the bundled lake image by default. Choose an image opens the system document picker (Storage Access Framework) and keeps a persistable read grant for that one URI. Restore returns to the bundled image. No broad storage permission is requested.

Installed apps and TV firmware often publish no Watch Next items and no preview channels. YouTube commonly publishes preview channels, Prime Video often publishes both, and Netflix varies by build. An empty row is not a bug in this launcher.

The default background is the approved mountain-lake JPEG, copied byte for byte into `app/src/main/res/drawable-nodpi/bg_default_wallpaper.jpg`. Its SHA-256 is `cb59dfa440c8083d596c88316298330ac98683e01b069299939f3dc276888942`.

Focus on the status icons and app tiles scales over about 160ms. That follows Android’s animator duration scale, so the motion is skipped when animations are turned off. At the real left or right end of the status row or the Apps row, another press in that direction nudges the current item and stays there. It does not wrap. Items that are only off-screen still move focus normally.

A USB icon appears only when a removable, non-primary volume is mounted and it is labeled as USB or a USB mass-storage device is connected. Keyboards, mice, ADB, internal storage, and SD cards labeled as SD stay hidden. If the label does not say USB and the kernel does not expose a mass-storage interface, a stick can stay hidden. OK on the icon opens the same File Manager shortcut. Unplugging it while it is focused moves focus to the next status control.

Icon bitmaps are 160px and the process keeps at most 6MB of them. Only the first 12 apps are decoded ahead of time. Poster images use a 16MB Coil memory cap. These are ceilings, not a measured reduction on a device.

## Permissions

| Permission | Why |
| --- | --- |
| `READ_TV_LISTINGS` (both names above) | Read Watch Next and preview channels this app is allowed to see. Not `ACCESS_ALL_EPG_DATA`. |
| `QUERY_ALL_PACKAGES` | A home screen has to list launchable apps, including sideloaded ones. Package visibility queries are declared as well. |
| `ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE` | Wi-Fi icon. |
| `BLUETOOTH` (API ≤ 30) and `BLUETOOTH_CONNECT` | Bluetooth icon. The runtime permission is requested only from the Bluetooth control. |
| `INTERNET` | Load poster URLs that other apps publish. Cleartext is allowed because some of those URLs are `http`. This app has no account and no API key. |
| `REQUEST_DELETE_PACKAGES` | Lets Android show its uninstall confirmation. The app never deletes a package itself. |

## Tests

Unit tests cover pin order, dedup, and reconcile; leanback-vs-launcher selection; file-manager and store priority; TV row mapping for empty, permission-denied, and missing-provider results; progress and artwork fallbacks; uninstall policy; and wallpaper selection.

```bash
./gradlew :app:testDebugUnitTest
```

These tests do not need a device.

The debug APK was also installed on a headless Android 34 emulator (AOSP ATD, 1920×1080). The activity came up with no crash. D-pad OK opened Add apps, pinned the real “System Tracing” package in front of Add, and Back closed the panel. All Apps opened from the home row, Menu showed Uninstall (disabled for that system app, with an explanation) and App info, and Back returned to All Apps and then home. The headless GPU framebuffer stayed blank, so those runs are recorded as UI-hierarchy dumps rather than pixel screenshots.

## Layout

```text
app/src/main/java/com/lakefront/tvlauncher/logic   pure pin, catalog, and TV mapping
app/src/main/java/com/lakefront/tvlauncher/data    PackageManager, TvContract, preferences
app/src/main/java/com/lakefront/tvlauncher/ui      Compose screens, focus, ViewModel
```
