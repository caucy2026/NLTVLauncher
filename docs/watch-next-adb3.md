# Watch Next 在 ADT-3 上为什么读不到（实测记录）

记录时间：2026-11-24 ~ 11-25　应用版本：1.0.4（vc5）→ 1.0.8（vc9）

## 结论

设备侧一切正常：**TvProvider 在、三张表在、权限够、URI 对**。
读不到内容的原因是**这台设备上没有任何 app 向 Watch Next / Preview 表发布数据**——YouTube 在这里装的是 Cobalt 版（Activity 为 `dev.cobalt.app.MainActivity`，渲染 `https://www.youtube.com/tv`），不发布任何 TV 数据。

顺带纠正一个曾经写错的判断：**不存在 `android.tvprovider` 这个 authority**。AOSP 源码和 AndroidX 常量池都只有 `android.media.tv`（详见下）。1.0.7 一度按错误假设改成"回落 android.tvprovider"，1.0.8 已全部回滚。

## 设备

| 项 | 值 |
| --- | --- |
| 型号 / 产品 | ADT-3 / `adt3` |
| 系统 | Android 14（SDK 34），`UP1A.231105.001.A1`，`eng.houmk.20261012.122723`，**userdebug / test-keys** |
| 分辨率 / 语言 | 1920×1080 / `zh-Hans-CN` |
| 系统桌面 | `com.zeasn.whale.saas`（ODM 定制，无 `launcherx`） |
| 目标应用 | `com.lakefront.tvlauncher` 1.0.8（vc9），debug 签名 |

## 证据

### 1. Provider 装在系统分区

```
$ adb shell pm path com.android.providers.tv
package:/system/priv-app/TvProvider/TvProvider.apk
```

### 2. 全系统只有 `android.media.tv` 一个 TV authority

```
$ adb shell dumpsys activity providers | grep providers.tv
* ContentProviderRecord{... u0 com.android.providers.tv/.TvProvider}
    authority=android.media.tv
```

AOSP 源码（`a723` = Android 14 / SDK 34）同样只有一条：

```xml
<!-- packages/providers/TvProvider/AndroidManifest.xml:70 -->
<provider android:name="TvProvider"
          android:authorities="android.media.tv"
          android:writePermission="com.android.providers.tv.permission.WRITE_EPG_DATA">
```

### 3. 权限级别（同文件）

| 权限 | protectionLevel | 含义 |
| --- | --- | --- |
| `android.permission.READ_TV_LISTINGS` | `dangerous`（第 23 行） | 能读，但只能看到自己的行 + 别人标了 `searchable` 的行 |
| `com.android.providers.tv.permission.READ_EPG_DATA` | `normal`（第 30 行） | 已废弃，不再强制 |
| `com.android.providers.tv.permission.WRITE_EPG_DATA` | `normal`（第 39 行） | **任何 app 声明即可写自己的行** |
| `com.android.providers.tv.permission.ACCESS_ALL_EPG_DATA` | `signature\|privileged`（第 46 行） | 解除上面的筛查，需平台签名 |

### 4. AndroidX 用的就是 `android.media.tv`

拆 `androidx.tvprovider:tvprovider:1.1.0` 的 aar，`javap -constants`：

```text
TvContractCompat.AUTHORITY = "android.media.tv"
WatchNextPrograms.CONTENT_URI = content://android.media.tv/watch_next_program
PreviewPrograms.CONTENT_URI   = content://android.media.tv/preview_program
Channels.CONTENT_URI          = content://android.media.tv/channel
```

该 aar 的 manifest 里还自带 `WRITE_EPG_DATA` / `READ_EPG_DATA` 声明，合并后会出现在使用方 APK 里。

### 5. 运行期探针（持有 READ_TV_LISTINGS 的 app 读到的东西）

探针版（临时加日志，已撤销）在设备上打印：

```text
I LakeTv : probe counts watchNext=0 channels=5 preview=0
I LakeTv : probe channel id=1 pkg=com.softwinner.vis type=TYPE_OTHER browsable=false name=HDMI1
I LakeTv : probe channel id=2 pkg=com.softwinner.vis type=TYPE_OTHER browsable=false name=HDMI2
I LakeTv : probe channel id=3 pkg=com.softwinner.vis type=TYPE_OTHER browsable=false name=HDMI3
I LakeTv : probe channel id=4 pkg=com.softwinner.vis type=TYPE_OTHER browsable=false name=CVBS1
I LakeTv : probe channel id=5 pkg=com.softwinner.vis type=TYPE_OTHER browsable=false name=CVBS2
```

即：**能读到数据**（证明权限与 URI 都对），但只有 5 条 softwinner 的输入源通道，`browsable` 全为 false；Watch Next 与 preview program 均为 0 行。

### 6. 没有查询异常

```
$ adb shell logcat -d -s LakeTv
（空）
```

`LakeTv` 这个 tag 只在查询失败、缺 Provider、权限被拒时打印；空输出 = 走的全是正常路径。

### 7. 系统签名证书是 testkey

| 对象 | SHA-256 |
| --- | --- |
| 设备上 `TvProvider.apk` 的签名证书 | `a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc` |
| `build/target/product/security/testkey.x509.pem`（a723） | 同上，一致 |

因此需要平台签名时，可直接用该 testkey 签，产物即拥有平台签名身份。已用 `LeanbackLauncher.apk` 实测：原始包因 v2/v3 签名被剥离而 `INSTALL_PARSE_FAILED_NO_CERTIFICATES`，用 testkey 重签后 `pm install` 成功。

## 已验证走不通的路

| 尝试 | 结果 |
| --- | --- |
| 读 YouTube（Cobalt）的数据库/缓存 | 10 MB 缓存全是 JS bundle，`grep videoRenderer` / `ytInitialData` 0 命中，feed 只在内存中 |
| WebView devtools 抓 DOM | 不是 WebView，`/proc/net/unix` 无 `webview_devtools_remote_*` |
| `uiautomator dump` 取节点 | Cobalt 单 surface，dump 出来只有空容器，无文字节点 |
| `adb shell content query .../watch_next_program` 判断有无数据 | **无效**：shell 不持有 `READ_TV_LISTINGS`，查什么都是 `No result found.` |
| 侧载 Google `LeanbackLauncher.apk` | 包包残缺（无 `resources.arsc`、无 activity），且签名被剥离；重签后可安装但无入口 |

## 版本变更

| 版本 | 变更 |
| --- | --- |
| 1.0.4（vc5） | 起点：`TvContractCompat` 标准读法、空态/无权限/无 Provider 三种提示（英文） |
| 1.0.7（vc8） | 新增 `values-zh` 中文资源；Provider 检测改用 `PackageManager.resolveContentProvider`（`acquireUnstableContentProviderClient` 对不存在的 authority 也会返回 client，检测形同虚设）；空态文案改为可执行说明 |
| 1.0.8（vc9） | 回滚 1.0.7 中基于错误假设的改动：去掉 `android.tvprovider` 回落与"表名改写"、删掉 `TvAccess/TvUnavailable.ListingsUnsupported` 及其文案与用例；保留上面两条确实正确的修复 |

## 复现

```bash
adb connect <device-ip>:5555
adb shell pm path com.android.providers.tv
adb shell dumpsys activity providers | grep providers.tv
adb shell pm list packages --show-versioncode lakefront
adb shell logcat -d -s LakeTv
```

需要看"表里到底有几行"时，临时加回探针日志（只读，不动 UI 逻辑），启动主页后看 `logcat -s LakeTv`。

## 后续可选方向

1. **launcher 自己当发布方**：声明 `WRITE_EPG_DATA`（normal），把"最近使用 / 继续观看"写成自己的行；已实测可写、可被标准读法读回并渲染。
2. **首页换内容**：应用行 + 时钟/状态，不依赖任何外部发布方。
3. **等待真实发布方**：在设备上装 Prime Video / Crunchyroll / Jellyfin 这类会发布 Watch Next 的应用后，现有读法会自动亮起，无需改代码。
