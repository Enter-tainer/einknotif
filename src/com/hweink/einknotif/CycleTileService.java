package com.hweink.einknotif;

import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * 5 模式循环磁贴。点一下循环到下一档:
 * 清晰 -> 普通 -> 快速 -> 高速 -> 极速 -> 清晰 ...
 * 作用在当前前台 app(切后记 per-app)。
 */
public class CycleTileService extends TileService {
    private static final int[] ORDER = {9, 7, 15, 14, 13};  // 清晰/普通/快速/高速/极速

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        int cur = EinkControl.getMode();
        int idx = -1;
        for (int i = 0; i < ORDER.length; i++) if (ORDER[i] == cur) { idx = i; break; }
        int target = ORDER[(idx + 1) % ORDER.length];
        EinkControl.setMode(target);
        ModeStore ms = new ModeStore(this);
        ms.setLastMode(target);
        ForegroundWatcher.markDirty();
        if (ms.isPerAppOn()) {
            String pkg = ForegroundWatcher.currentPkg();
            if (pkg != null) new PerAppStore(this).put(pkg, target);
        }
        if (target != ModeStore.MODE_A2 && ms.isAutoFullOnClear()) {
            EinkControl.triggerFullRefresh();
        }
        updateTile();
        RefreshService.refreshNotification(this);
        try {
            android.service.quicksettings.TileService.requestListeningState(this,
                new android.content.ComponentName(this, FlipTileService.class));
        } catch (Throwable ignore) {}
    }

    private void updateTile() {
        Tile t = getQsTile();
        if (t == null) return;
        int cur = EinkControl.getMode();
        t.setState(cur == ModeStore.MODE_A2 ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("模式:" + ModeStore.nameOf(cur));
        try {
            t.setIcon(Icon.createWithResource(getPackageName(), R.drawable.ic_tile_cycle));
        } catch (Throwable ignore) {}
        t.updateTile();
    }
}
