package com.hweink.einknotif;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * per-app 刷新模式 + 色彩参数记忆表。
 * 数据模型: pkg -> { mode, colorDep, contrast, saturation, brightness, lastUsed }
 *   - enabled(隐式:有记录即 enabled)的 pkg 进前台自动切它的 mode
 *   - 无记录的 pkg 用全局 default_mode
 * 用 SharedPreferences + JSON 持久(无需 ContentProvider / 系统权限)。
 *
 * 与 einkrefresh/PerAppStore 的区别:这里加 lastUsed(列表排序+清理),
 * 且配合 ForegroundWatcher 事件驱动做双向记忆(切走 dirty 时 put)。
 */
public final class PerAppStore {
    private static final String PREFS = "per_app_refresh";
    private static final String KEY_MAP = "pkg_map";

    public static final class Entry {
        public String pkg;
        public int mode;
        public int colorDep;
        public int contrast;
        public int saturation;
        public int brightness;
        public long lastUsed;
    }

    private final SharedPreferences sp;
    private final Map<String, Entry> cache = new HashMap<>();

    public PerAppStore(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
    }

    private void load() {
        cache.clear();
        try {
            String json = sp.getString(KEY_MAP, null);
            if (json == null) return;
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Entry e = new Entry();
                e.pkg = o.getString("pkg");
                e.mode = o.optInt("mode", ModeStore.MODE_CLEAR);
                e.colorDep = o.optInt("colorDep", EinkControl.COLOR_DEFAULT);
                e.contrast = o.optInt("contrast", EinkControl.COLOR_DEFAULT);
                e.saturation = o.optInt("saturation", EinkControl.COLOR_DEFAULT);
                e.brightness = o.optInt("brightness", EinkControl.COLOR_DEFAULT);
                e.lastUsed = o.optLong("lastUsed", 0);
                cache.put(e.pkg, e);
            }
        } catch (Exception e) { /* corrupt, start fresh */ }
    }

    private void save() {
        JSONArray arr = new JSONArray();
        try {
            for (Entry e : cache.values()) {
                JSONObject o = new JSONObject();
                o.put("pkg", e.pkg);
                o.put("mode", e.mode);
                o.put("colorDep", e.colorDep);
                o.put("contrast", e.contrast);
                o.put("saturation", e.saturation);
                o.put("brightness", e.brightness);
                o.put("lastUsed", e.lastUsed);
                arr.put(o);
            }
            sp.edit().putString(KEY_MAP, arr.toString()).apply();
        } catch (Exception e) { /* ignore */ }
    }

    synchronized Entry get(String pkg) {
        if (pkg == null) return null;
        return cache.get(pkg);
    }

    synchronized void putProfile(String pkg, int mode, int colorDep, int contrast,
            int saturation, int brightness) {
        Entry e = cache.get(pkg);
        if (e == null) { e = new Entry(); e.pkg = pkg; }
        e.mode = mode;
        e.colorDep = colorDep;
        e.contrast = contrast;
        e.saturation = saturation;
        e.brightness = brightness;
        e.lastUsed = System.currentTimeMillis();
        cache.put(pkg, e);
        save();
    }

    synchronized void remove(String pkg) {
        cache.remove(pkg);
        save();
    }

    /** 按 lastUsed 倒序返回,设置页列表用。 */
    synchronized List<Entry> allSorted() {
        List<Entry> l = new ArrayList<>(cache.values());
        java.util.Collections.sort(l, (a, b) -> Long.compare(b.lastUsed, a.lastUsed));
        return l;
    }

    /** 记住当前前台 pkg 的模式(dirty 触发)。即使值相同也记(首次记录必须落表)。 */
    synchronized void recordIfDirty(String pkg, int mode, int colorDep, int contrast,
            int saturation, int brightness) {
        Entry e = cache.get(pkg);
        if (e != null && e.mode == mode && e.colorDep == colorDep
                && e.contrast == contrast && e.saturation == saturation
                && e.brightness == brightness
                && System.currentTimeMillis() - e.lastUsed < 60000) {
            return;  // 同值且刚记过,跳过(避免频繁写)
        }
        putProfile(pkg, mode, colorDep, contrast, saturation, brightness);
    }
}
