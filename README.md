# einknotif

A no-root way to switch e-ink refresh modes on Rockchip RK3576 e-ink
Android devices (Zuoyebang rk3576_ebook / Khadas Edge2L ebook family).

Talks to the on-device Rockchip `IEbookManager` binder (`service call ebook 2`)
to set `sys.ebook.mode` — **no permission required**, any app can call it.

## Features

- **Persistent notification** with 3 mode buttons (Clear / Normal / Fast) + full
  refresh, current mode marked ✓. Silent updates (no sound on switch).
- **Quick-settings tile** for one-tap flip between Fastest (A2) and Clear (GLR16)
  — the two modes you toggle most.
- **Cycle tile** to step through all 5 modes.
- **SystemUI navbar integration**: the middle shortcut uses the same Fastest/Clear
  flip logic as the tile and changes its icon (`C / N / F / H / A2`) with the
  active mode. The gear button opens a one-level, E-ink-optimized panel for five
  modes, full-refresh frequency, front light, and image tuning.
- **Per-app memory**: each foreground app remembers refresh mode plus color depth,
  contrast, saturation, and image brightness. Switching away saves the profile;
  switching back restores it automatically. Front light and full-refresh
  frequency intentionally remain device-global.
- **Boot restore**: re-applies the last mode after reboot (`sys.ebook.mode` is
  not persistent, the app re-sets it on `BOOT_COMPLETED`).
- **E-ink widget styling**: buttons, switches, and sliders use static high-contrast
  black/white drawables without ripple or color animation.
- **Settings page**: pick mode, tune the four per-app image values, configure flip clear-side, toggle
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
> but not the runtime permission check. Polling at 2.5 s on this e-ink device has
> negligible impact compared with the display/front-light load. See the
> [ROM integration runbook](https://github.com/Enter-tainer/zuoyebang-rom-patches/blob/main/runbook/ROM_BUILD_PLAN.md)
> for the SystemUI and per-app design notes.

## SystemUI integration API

SystemUI sends explicit broadcasts to
`com.hweink.einknotif/.NavbarActionReceiver`. The receiver is exported without
a custom permission because the underlying `IEbookManager.setProperty` call is
also available to ordinary apps on the target firmware.

| Action suffix | Extras | Effect |
|---|---|---|
| `FLIP` | none | Toggle Fastest and the remembered non-fast mode. |
| `CYCLE` | none | Cycle Clear → Normal → Quick → Fast → Fastest. |
| `FULL_REFRESH` | none | Trigger one full refresh. |
| `SET_MODE` | `mode` (int), optional `full_refresh` (boolean) | Select mode `9`, `7`, `15`, `14`, or `13`. |
| `SET_COLOR` | `color` (string), `value` (int `0..128`) | Set `color_dep`, `contrast`, `saturation`, or `brightness`. |

Each suffix is prefixed with `com.hweink.einknotif.action.`. After a mode
change, einknotif sends the package-targeted
`com.hweink.einknotif.action.MODE_CHANGED` broadcast to
`com.android.systemui` with the current `mode` integer. SystemUI should still
read `sys.ebook.mode` as the authoritative value.

Example:

```bash
adb shell am broadcast \
  -n com.hweink.einknotif/.NavbarActionReceiver \
  -a com.hweink.einknotif.action.SET_MODE \
  --ei mode 9
```

## Build

No Gradle. Uses `aapt2` + `d8` + `apksigner` from the Android SDK build-tools.

```bash
bash build.sh
# → build/einknotif.apk
```

Standard Android SDK only — no `@hide` framework classes are referenced
(`TaskStackListener` was dropped after it turned out to require an un-grantable
permission; see the per-app section below).

## Install

```bash
adb install -r build/einknotif.apk
adb shell pm grant com.hweink.einknotif android.permission.POST_NOTIFICATIONS
adb shell appops set com.hweink.einknotif GET_USAGE_STATS allow
```

No root required. Then open "E-ink 刷新" once to start the notification service,
and add the tiles from the quick-settings edit mode.

## Device compatibility

Built and verified on a Zuoyebang rk3576_ebook learning tablet running a clean
Khadas `khadas-edge-2l-android14` system (no Zuoyebang apps) with stock
kernel/vendor/waveform. Should work on any Rockchip RK3576 e-ink device whose
framework exposes the `ebook` (`IEbookManager`) service.

## License

MIT.
