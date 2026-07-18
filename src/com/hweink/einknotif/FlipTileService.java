package com.hweink.einknotif;

import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

/**
 * 极速/清晰 flip 磁贴。
 * 点一下:当前非极速 → 切极速(记当前为 lastNonFast);
 *       当前极速      → 切回 lastNonFast(默认清晰侧)。
 * 作用在"当前前台 app"——切后记到 PerAppStore(若 per-app 开)。
 */
public class FlipTileService extends TileService {
    private static final String TAG = "FlipTile";

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        ModeStore ms = new ModeStore(this);
        int cur = EinkControl.getMode();
        int fast = ModeStore.MODE_A2;
        int target;
        if (cur != fast) {
            ms.setLastNonFast(cur);      // 记住,切回用
            target = fast;
        } else {
            target = ms.getLastNonFast();
            if (target == fast) target = ms.getFlipClearSide();  // 兜底清晰侧
        }
        EinkControl.setMode(target);
        ms.setLastMode(target);
        // per-app:记到当前前台 pkg
        if (ms.isPerAppOn()) {
            String pkg = ForegroundWatcher.currentPkg();
            if (pkg != null) new PerAppStore(this).put(pkg, target);
        }
        // 切到清晰侧(非极速)且开了自动全刷 → 全刷清残影
        if (target != fast && ms.isAutoFullOnClear()) {
            EinkControl.triggerFullRefresh();
        }
        Log.i(TAG, "flip -> " + ModeStore.nameOf(target));
        updateTile();
        // 同步通知 + 另一个磁贴
        RefreshService.refreshNotification(this);
        try {
            android.service.quicksettings.TileService.requestListeningState(this,
                new android.content.ComponentName(this, CycleTileService.class));
        } catch (Throwable ignore) {}
    }

    private void updateTile() {
        Tile t = getQsTile();
        if (t == null) return;
        int cur = EinkControl.getMode();
        boolean isFast = (cur == ModeStore.MODE_A2);
        t.setState(isFast ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("极速/清晰");
        t.setContentDescription("切换 e-ink 刷新模式");
        // 副标题用 contentDescription 不显示;用 label 拼当前态更直观
        t.setLabel("刷新:" + ModeStore.nameOf(cur));
        try {
            Icon ic = Icon.createWithResource(getPackageName(),
                    isFast ? R.drawable.ic_tile_fast : R.drawable.ic_tile_clear);
            t.setIcon(ic);
        } catch (Throwable e) { /* 无图标资源时忽略 */ }
        t.updateTile();
    }
}
