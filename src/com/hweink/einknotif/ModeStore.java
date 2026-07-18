package com.hweink.einknotif;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 全局设置 + 持久化(last_mode / default_mode / flip 清晰侧 / 开关)。
 * SharedPreferences 持久跨重启。sys.ebook.mode 本身不持久(sys. 前缀),
 * 这里存 last_mode,开机由 BootReceiver 恢复。
 */
final class ModeStore {
    private static final String PREFS = "eink_refresh_prefs";
    private static final String K_LAST_MODE      = "last_mode";        // 上次设的模式(开机恢复用)
    private static final String K_DEFAULT_MODE   = "default_mode";     // 无记忆 app 兜底模式
    private static final String K_FLIP_CLEAR     = "flip_clear_side";  // flip 的非极速侧
    private static final String K_LAST_NONFAST   = "last_non_fast";    // flip 切极速前记的非极速档
    private static final String K_NOTIF_ON       = "notif_on";         // 常驻通知开关
    private static final String K_BOOT_RESTORE   = "boot_restore";     // 开机恢复开关
    private static final String K_AUTO_FULL_CLEAR= "auto_full_on_clear"; // 切到清晰侧自动全刷
    private static final String K_PERAPP_ON      = "perapp_on";        // per-app 自动切换开关

    static final int MODE_CLEAR = 9;   // EPD_PART_GLR16
    static final int MODE_NORMAL = 7;  // EPD_PART_GC16
    static final int MODE_FAST4 = 15;  // EPD_DU4
    static final int MODE_FAST = 14;   // EPD_DU
    static final int MODE_A2 = 13;     // EPD_A2_FAST(极速)

    private final SharedPreferences sp;
    ModeStore(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    int getLastMode()        { return sp.getInt(K_LAST_MODE, MODE_CLEAR); }
    void setLastMode(int m)  { sp.edit().putInt(K_LAST_MODE, m).apply(); }

    int getDefaultMode()        { return sp.getInt(K_DEFAULT_MODE, MODE_CLEAR); }
    void setDefaultMode(int m)  { sp.edit().putInt(K_DEFAULT_MODE, m).apply(); }

    int getFlipClearSide()        { return sp.getInt(K_FLIP_CLEAR, MODE_CLEAR); }
    void setFlipClearSide(int m)  { sp.edit().putInt(K_FLIP_CLEAR, m).apply(); }

    int getLastNonFast()        { return sp.getInt(K_LAST_NONFAST, MODE_CLEAR); }
    void setLastNonFast(int m)  { sp.edit().putInt(K_LAST_NONFAST, m).apply(); }

    boolean isNotifOn()           { return sp.getBoolean(K_NOTIF_ON, true); }
    void setNotifOn(boolean b)    { sp.edit().putBoolean(K_NOTIF_ON, b).apply(); }

    boolean isBootRestore()        { return sp.getBoolean(K_BOOT_RESTORE, true); }
    void setBootRestore(boolean b) { sp.edit().putBoolean(K_BOOT_RESTORE, b).apply(); }

    boolean isAutoFullOnClear()        { return sp.getBoolean(K_AUTO_FULL_CLEAR, false); }
    void setAutoFullOnClear(boolean b) { sp.edit().putBoolean(K_AUTO_FULL_CLEAR, b).apply(); }

    boolean isPerAppOn()        { return sp.getBoolean(K_PERAPP_ON, true); }
    void setPerAppOn(boolean b) { sp.edit().putBoolean(K_PERAPP_ON, b).apply(); }

    static String nameOf(int mode) {
        switch (mode) {
            case MODE_CLEAR:  return "清晰";
            case MODE_NORMAL: return "普通";
            case MODE_FAST4:  return "快速";
            case MODE_FAST:   return "高速";
            case MODE_A2:     return "极速";
            default:          return "?(" + mode + ")";
        }
    }
}
