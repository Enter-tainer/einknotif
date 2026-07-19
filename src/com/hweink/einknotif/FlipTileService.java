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
        // 复用 NavbarAction 的 flip 实现(与 navbar 按钮同一条路径,逻辑只有一份)
        NavbarAction.flip(this);
        Log.i(TAG, "flip -> " + ModeStore.nameOf(EinkControl.getMode()));
        updateTile();
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
