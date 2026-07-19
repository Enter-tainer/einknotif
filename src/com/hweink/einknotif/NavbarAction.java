package com.hweink.einknotif;

import android.content.ComponentName;
import android.content.Context;
import android.service.quicksettings.TileService;

/**
 * navbar / 外部触发共享的刷新动作实现。
 *
 * 逻辑与 FlipTileService.onClick / CycleTileService.onClick 完全一致(同一套),
 * 只是抽出来能从 Context 直接调(不依赖 TileService 实例)。
 * 三个 tile service 以后也可改成调这里,保证"切模式"只有一份实现。
 */
final class NavbarAction {
    private static final int[] CYCLE_ORDER = {9, 7, 15, 14, 13};  // 清晰/普通/快速/高速/极速
    static final String COLOR_DEP = "color_dep";
    static final String CONTRAST = "contrast";
    static final String SATURATION = "saturation";
    static final String BRIGHTNESS = "brightness";

    private NavbarAction() {}

    /** 极速↔清晰 flip:当前非极速 → 切极速(记当前为 lastNonFast);极速 → 切回 lastNonFast(默认清晰侧)。 */
    static void flip(Context ctx) {
        ModeStore ms = new ModeStore(ctx);
        int cur = EinkControl.getMode();
        int fast = ModeStore.MODE_A2;
        int target;
        if (cur != fast) {
            ms.setLastNonFast(cur);
            target = fast;
        } else {
            target = ms.getLastNonFast();
            if (target == fast) target = ms.getFlipClearSide();  // 兜底清晰侧
        }
        applyMode(ctx, ms, target);
    }

    /** 循环到下一档:清晰 -> 普通 -> 快速 -> 高速 -> 极速 -> 清晰 ... */
    static void cycle(Context ctx) {
        int cur = EinkControl.getMode();
        int idx = -1;
        for (int i = 0; i < CYCLE_ORDER.length; i++) if (CYCLE_ORDER[i] == cur) { idx = i; break; }
        int target = CYCLE_ORDER[(idx + 1) % CYCLE_ORDER.length];
        applyMode(ctx, new ModeStore(ctx), target);
    }

    /** 应用 SystemUI 五档菜单选中的模式，并同步 einknotif 的持久状态。 */
    static boolean setMode(Context ctx, int target) {
        if (!isSupportedMode(target)) return false;
        ModeStore ms = new ModeStore(ctx);
        if (target != ModeStore.MODE_A2) ms.setLastNonFast(target);
        applyMode(ctx, ms, target);
        return true;
    }

    static boolean setColor(Context ctx, String color, int value) {
        if (value < 0 || value > 128) return false;
        boolean changed;
        if (COLOR_DEP.equals(color)) changed = EinkControl.setColorDep(value);
        else if (CONTRAST.equals(color)) changed = EinkControl.setContrast(value);
        else if (SATURATION.equals(color)) changed = EinkControl.setSaturation(value);
        else if (BRIGHTNESS.equals(color)) changed = EinkControl.setBrightness(value);
        else return false;
        if (!changed) return false;
        ForegroundWatcher.markDirty();
        recordCurrentProfile(ctx, new ModeStore(ctx), EinkControl.getMode());
        return true;
    }

    /** 全刷清残影。 */
    static void fullRefresh(Context ctx) {
        EinkControl.triggerFullRefresh();
        // 用 updateNotificationText(直接 NotificationManager.notify)而非 refreshNotification
        // (后者起 startForegroundService,从 BroadcastReceiver 调会被 Android 12+ 的
        // ForegroundServiceStartNotAllowedException 拦)。full refresh 本身已触发成功。
        RefreshService.updateNotificationText(ctx);
    }

    /** 设模式 + 记 last_mode + per-app 记当前前台 + 可选全刷 + 同步磁贴/通知。 */
    private static void applyMode(Context ctx, ModeStore ms, int target) {
        EinkControl.setMode(target);
        ms.setLastMode(target);
        ForegroundWatcher.markDirty();
        if (ms.isPerAppOn()) {
            recordCurrentProfile(ctx, ms, target);
        }
        // 切到非极速(清晰侧)且开了自动全刷 → 全刷清残影
        if (target != ModeStore.MODE_A2 && ms.isAutoFullOnClear()) {
            EinkControl.triggerFullRefresh();
        }
        // 同步通知 + 另两个磁贴的 listening 状态(让磁贴图标/状态更新)
        RefreshService.updateNotificationText(ctx);
        requestTileListening(ctx, FlipTileService.class);
        requestTileListening(ctx, CycleTileService.class);
    }

    private static void requestTileListening(Context ctx, Class<? extends TileService> tileCls) {
        try {
            TileService.requestListeningState(ctx, new ComponentName(ctx, tileCls));
        } catch (Throwable ignore) {}
    }

    private static boolean isSupportedMode(int mode) {
        for (int supported : CYCLE_ORDER) if (supported == mode) return true;
        return false;
    }

    private static void recordCurrentProfile(Context ctx, ModeStore ms, int mode) {
        if (!ms.isPerAppOn()) return;
        String pkg = ForegroundWatcher.currentPkg();
        if (pkg == null) return;
        new PerAppStore(ctx).putProfile(pkg, mode, EinkControl.getColorDep(),
                EinkControl.getContrast(), EinkControl.getSaturation(),
                EinkControl.getBrightness());
    }
}
