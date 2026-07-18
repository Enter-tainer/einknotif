# einknotif

A no-root-either way to switch e-ink refresh modes on Rockchip RK3576 e-ink
Android devices (Zuoyebang rk3576_ebook / Khadas Edge2L ebook family).

Talks to the on-device Rockchip `IEbookManager` binder (`service call ebook 2`)
to set `sys.ebook.mode` — **no permission required**, any app can call it.

## Features

- **Persistent notification** with 3 mode buttons (Clear / Normal / Fast) + full
  refresh, current mode marked ✓. Silent updates (no sound on switch).
- **Quick-settings tile** for one-tap flip between Fastest (A2) and Clear (GLR16)
  — the two modes you toggle most.
- **Cycle tile** to step through all 5 modes.
- **Per-app memory**: each foreground app remembers its own mode — switch into a
  reader and it auto-restores Clear, switch into a note app and it restores Fast.
  Switching away saves the mode you set. Automatic learning, no manual config.
- **Boot restore**: re-applies the last mode after reboot (`sys.ebook.mode` is
  not persistent, the app re-sets it on `BOOT_COMPLETED`).
- **Settings page**: pick mode, configure flip clear-side, toggle
  notification / boot-restore / per-app / auto-full-refresh-on-clear, view the
  per-app list.

## Refresh modes

| Name | Value | Constant | Use |
|---|---|---|---|
| 清晰 Clear | 9 | EPD_PART_GLR16 | reading, images |
| 普通 Normal | 7 | EPD_PART_GC16 | everyday |
| 快速 Quick | 15 | EPD_DU4 | scrolling |
| 高速 Fast | 14 | EPD_DU | paging |
| 极速 Fastest | 13 | EPD_A2_FAST | dragging, handwriting |

## How per-app foreground detection works

The app polls `UsageStatsManager.queryEvents` every 2.5 s for the latest
`MOVE_TO_FOREGROUND` package. This needs the `PACKAGE_USAGE_STATS` permission
(grant once via Settings → Usage access, or `pm grant`).

> **Why not `TaskStackListener` (zero-power event-driven)?** It requires
> `android.permission.MANAGE_ACTIVITY_TASKS` (signature|privileged) which is not
> granted even to a platform-signed `/system/priv-app` on this build — only
> `system_server` holds it. `hidden_api_policy=1` bypasses the non-SDK blocklist
> but not the runtime permission check. Polling at 2.5 s on an e-ink device
> (no cellular, no backlight) has unmeasurable power cost. See
> `docs/EINK_REFRESH_APP_DESIGN.md` for the full investigation.

## Build

No Gradle. Uses `aapt2` + `d8` + `apksigner` from the Android SDK build-tools.

```bash
bash build.sh
# → build/einknotif.apk
```

The build needs `framework-hidden.jar` (Android `framework-minus-apex`
intermediates containing the `@hide` `TaskStackListener` class, used at compile
time by `ForegroundWatcher`). It is **not** in this repo (gitignored — too
large, and build-tooling-specific). Get it from an AOSP/Khadas build:

```
out/target/common/obj/JAVA_LIBRARIES/framework-minus-apex_intermediates/classes.jar
```

Copy it to `einknotif/framework-hidden.jar` before building.

## Install

```bash
adb install -r build/einknotif.apk
adb shell pm grant com.hweink.einknotif android.permission.POST_NOTIFICATIONS
adb shell pm grant com.hweink.einknotif android.permission.PACKAGE_USAGE_STATS
# optional, for future hidden-API paths:
adb shell settings put global hidden_api_policy 1
```

Then open "E-ink 刷新" once to start the notification service, and add the
tiles from the quick-settings edit mode.

## Device compatibility

Built and verified on a Zuoyebang rk3576_ebook learning tablet running a clean
Khadas `khadas-edge-2l-android14` system (no Zuoyebang apps) with stock
kernel/vendor/waveform. Should work on any Rockchip RK3576 e-ink device whose
framework exposes the `ebook` (`IEbookManager`) service.

## License

MIT.
