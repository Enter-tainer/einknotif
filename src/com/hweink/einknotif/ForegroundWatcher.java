package com.hweink.einknotif;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * 前台 app 监听(per-app 刷新模式切换用)。
 *
 * 机制:UsageStatsManager 2.5s 轮询 MOVE_TO_FOREGROUND 事件,取最新前台包名。
 *   - 权限 PACKAGE_USAGE_STATS(用户授权或 pm grant),无 root。
 *   - 功耗:e-ink 上无蜂窝无背光,CPU 空闲,2.5s 轮询功耗不可测。
 *   - 可靠性:UsageEvents 由系统 activity-transition tracker 填充,全屏阅读器也正确上报。
 *   - 延迟:切 app 后最多 2.5s 切模式,可接受。
 *
 * 切前台 → 查 PerAppStore 切记忆模式(无则 default)。
 * 切走   → 若 modeDirty 则记当前模式到旧 pkg(双向记忆)。
 *
 * 历史选型(记录备查,勿重试):
 *   - AccessibilityService:全屏沉浸 app 漏 TYPE_WINDOW_STATE_CHANGED,锁住也救不了。
 *   - TaskStackListener(registerTaskStackListener):Android 14 上 enforced
 *     MANAGE_ACTIVITY_TASKS(signature|privileged),hidden_api_policy=1 绕黑名单不绕权限,
 *     priv-app + 平台 key 重签都拿不到。彻底解法只有改 ATMS 源码重编,放弃。
 */
public class ForegroundWatcher {
    private static final String TAG = "FgWatcher";
    private static final long POLL_MS = 2500;

    private static volatile ForegroundWatcher sInstance;
    private static volatile String sCurrentPkg = null;
    /** 用户在当前 pkg 期间手动改过模式 → 置 true,切走时触发存 per-app。 */
    private static volatile boolean sProfileDirty = false;

    private final Context ctx;
    private final ModeStore ms;
    private final PerAppStore store;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastEventTime = 0;

    private ForegroundWatcher(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.ms = new ModeStore(this.ctx);
        this.store = new PerAppStore(this.ctx);
    }

    public static void start(Context ctx) {
        if (sInstance != null) return;
        sInstance = new ForegroundWatcher(ctx);
        sInstance.handler.postDelayed(sInstance.pollRunnable, POLL_MS);
        Log.i(TAG, "UsageStats polling started (" + POLL_MS + "ms)");
    }

    /** 当前前台 pkg(供 FlipTile/RefreshService 记 per-app 用)。 */
    public static String currentPkg() { return sCurrentPkg; }

    /** 用户手动改模式时调,标记 dirty 以便切走时记。 */
    public static void markDirty() { sProfileDirty = true; }

    /** 前台包名变化处理。 */
    private synchronized void onFgChanged(String pkg) {
        if (pkg == null || pkg.equals(sCurrentPkg)) return;
        // 切走旧 pkg:若 dirty 则记
        recordDirtyIfAny();
        sCurrentPkg = pkg;
        Log.i(TAG, "fg -> " + pkg);
        if (!ms.isPerAppOn()) return;
        PerAppStore.Entry e = store.get(pkg);
        int target = e != null ? e.mode : ms.getDefaultMode();
        EinkControl.setMode(target);
        EinkControl.setColorDep(e != null ? e.colorDep : EinkControl.COLOR_DEFAULT);
        EinkControl.setContrast(e != null ? e.contrast : EinkControl.COLOR_DEFAULT);
        EinkControl.setSaturation(e != null ? e.saturation : EinkControl.COLOR_DEFAULT);
        EinkControl.setBrightness(e != null ? e.brightness : EinkControl.COLOR_DEFAULT);
        ms.setLastMode(target);
        // per-app 自动切模式时:只更新通知文本(不重 startForeground,避免声音/振动)
        RefreshService.updateNotificationText(ctx);
    }

    /** 若 dirty 且当前 pkg 已知,把当前模式记到 per-app。 */
    private void recordDirtyIfAny() {
        if (sProfileDirty && sCurrentPkg != null) {
            int cur = EinkControl.getMode();
            store.recordIfDirty(sCurrentPkg, cur, EinkControl.getColorDep(),
                    EinkControl.getContrast(), EinkControl.getSaturation(),
                    EinkControl.getBrightness());
            Log.i(TAG, "recorded profile " + sCurrentPkg + " -> " + ModeStore.nameOf(cur));
        }
        sProfileDirty = false;
    }

    private final Runnable pollRunnable = new Runnable() {
        @Override public void run() {
            pollUsageStats();
            handler.postDelayed(this, POLL_MS);
        }
    };

    /** UsageStats 查最近 MOVE_TO_FOREGROUND。 */
    private void pollUsageStats() {
        try {
            UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return;
            long now = System.currentTimeMillis();
            UsageEvents events = usm.queryEvents(now - POLL_MS * 3, now);
            UsageEvents.Event e = new UsageEvents.Event();
            String latest = null; long latestT = lastEventTime;
            while (events.hasNextEvent()) {
                events.getNextEvent(e);
                if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    if (e.getTimeStamp() > latestT) {
                        latestT = e.getTimeStamp();
                        latest = e.getPackageName();
                    }
                }
            }
            lastEventTime = latestT;
            if (latest != null) onFgChanged(latest);
        } catch (Throwable t) {
            Log.w(TAG, "poll failed: " + t);
        }
    }

    public static void stop() {
        if (sInstance == null) return;
        sInstance.handler.removeCallbacksAndMessages(null);
        sInstance = null;
    }
}
