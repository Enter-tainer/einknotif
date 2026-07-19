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
        // 复用 NavbarAction 的 cycle 实现 (与 navbar 按钮同一条路径,逻辑只有一份)
        NavbarAction.cycle(this);
        updateTile();
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
