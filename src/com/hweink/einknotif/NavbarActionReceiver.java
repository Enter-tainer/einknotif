package com.hweink.einknotif;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * navbar 按钮的协同设计入口。
 * SystemUI 的模式快切按钮发 FLIP；一级设置面板发 SET_MODE / SET_COLOR。
 * 所有 action 复用 NavbarAction 的切换实现，逻辑只有一份。
 *
 * 唯一耦合是 action/extra 字符串常量，SystemUI 侧使用相同的值。
 */
public class NavbarActionReceiver extends BroadcastReceiver {
    private static final String TAG = "EinkNotif/Navbar";

    /** 极速↔清晰 flip（navbar 中间的模式按钮）。 */
    public static final String ACTION_FLIP  = "com.hweink.einknotif.action.FLIP";
    /** 循环走下一档。 */
    public static final String ACTION_CYCLE = "com.hweink.einknotif.action.CYCLE";
    /** 全刷清残影。 */
    public static final String ACTION_FULL  = "com.hweink.einknotif.action.FULL_REFRESH";
    /** 原生 SystemUI 五档菜单选择结果。 */
    public static final String ACTION_SET_MODE = "com.hweink.einknotif.action.SET_MODE";
    public static final String ACTION_SET_COLOR = "com.hweink.einknotif.action.SET_COLOR";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_COLOR = "color";
    public static final String EXTRA_VALUE = "value";
    public static final String EXTRA_FULL_REFRESH = "full_refresh";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (action == null) return;
        try {
            switch (action) {
                case ACTION_FLIP:
                    NavbarAction.flip(ctx);
                    Log.i(TAG, "flip -> " + ModeStore.nameOf(EinkControl.getMode()));
                    break;
                case ACTION_CYCLE:
                    NavbarAction.cycle(ctx);
                    Log.i(TAG, "cycle -> " + ModeStore.nameOf(EinkControl.getMode()));
                    break;
                case ACTION_FULL:
                    NavbarAction.fullRefresh(ctx);
                    Log.i(TAG, "full refresh");
                    break;
                case ACTION_SET_MODE:
                    int mode = intent.getIntExtra(EXTRA_MODE, Integer.MIN_VALUE);
                    if (!NavbarAction.setMode(ctx, mode)) {
                        Log.w(TAG, "reject unsupported mode " + mode);
                        return;
                    }
                    Log.i(TAG, "set mode -> " + ModeStore.nameOf(mode));
                    if (intent.getBooleanExtra(EXTRA_FULL_REFRESH, false)) {
                        NavbarAction.fullRefresh(ctx);
                    }
                    break;
                case ACTION_SET_COLOR:
                    String color = intent.getStringExtra(EXTRA_COLOR);
                    int value = intent.getIntExtra(EXTRA_VALUE, Integer.MIN_VALUE);
                    if (!NavbarAction.setColor(ctx, color, value)) {
                        Log.w(TAG, "reject color " + color + "=" + value);
                        return;
                    }
                    Log.i(TAG, "set color " + color + "=" + value);
                    break;
                default:
                    return;
            }
        } catch (Throwable t) {
            Log.w(TAG, "action " + action + " failed: " + t);
        }
    }
}
