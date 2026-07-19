package com.hweink.einknotif;

import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * 256 灰阶抖动 toggle 磁贴。
 * 作业帮 HAL 支持 16→256 抖动但无 UI 入口;此磁贴直写 persist.ebook.gray256_enable。
 * 切换后立即全刷:已显示像素不会自动重新量化, 必须全刷重驱动才能看到变化。
 * persist. 前缀跨重启, 不随 app 切换, 不走 per-app/通知同步。
 */
public class Gray256TileService extends TileService {
    private static final String TAG = "Gray256Tile";

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        boolean on = EinkControl.isGray256();
        EinkControl.setGray256(!on);
        EinkControl.triggerFullRefresh();   // 让变化立即可见
        updateTile();
    }

    private void updateTile() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean on = EinkControl.isGray256();
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("256灰阶");
        t.setContentDescription("256 灰阶抖动 " + (on ? "开" : "关"));
        try {
            t.setIcon(Icon.createWithResource(getPackageName(), R.drawable.ic_tile_gray));
        } catch (Throwable ignore) {}
        t.updateTile();
    }
}
