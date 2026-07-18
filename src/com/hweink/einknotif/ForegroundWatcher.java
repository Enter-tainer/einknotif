package com.hweink.einknotif;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.TaskStackListener;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * 前台 app 监听。两条路:
 *   主:直接 extends TaskStackListener 注册(ActivityTaskManager.registerTaskStackListener),
 *       系统事件回调,零轮询零功耗,全屏阅读器不漏。需 hidden_api_policy=1(userdebug)。
 *       编译期对 framework-hidden.jar(含 @hide 类)编译,生成真子类 override onTaskMovedToFront。
 *   降级:注册失败(黑名单未绕)→ UsageStatsManager 2.5s 轮询 MOVE_TO_FOREGROUND。
 *
 * 切前台 → 查 PerAppStore 切记忆模式(无则 default)。
 * 切走   → 若 modeDirty 则记当前模式到旧 pkg(双向记忆)。
 *
 * 弃用 AccessibilityService:全屏沉浸 app 漏 TYPE_WINDOW_STATE_CHANGED,锁住也救不了。
 * 弃用 Proxy:TaskStackListener 是 abstract class extends Stub,非 interface,Proxy 不适用。
 */
public class ForegroundWatcher {
    private static final String TAG = "FgWatcher";
    private static final long POLL_MS = 2500;

    private static volatile ForegroundWatcher sInstance;
    private static volatile String sCurrentPkg = null;
    /** 用户在当前 pkg 期间手动改过模式 → 置 true,切走时触发存 per-app。 */
    private static volatile boolean sModeDirty = false;

    private final Context ctx;
    private final ModeStore ms;
    private final PerAppStore store;
    private TaskStackListener taskListener;
    private boolean usePolling = false;
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
        sInstance.startInternal();
    }

    /** 当前前台 pkg(供 FlipTile/RefreshService 记 per-app 用)。 */
    public static String currentPkg() { return sCurrentPkg; }

    /** 用户手动改模式时调,标记 dirty 以便切走时记。 */
    public static void markDirty() { sModeDirty = true; }

    private void startInternal() {
        if (tryRegisterTaskStackListener()) {
            Log.i(TAG, "TaskStackListener registered (event-driven)");
        } else {
            usePolling = true;
            Log.w(TAG, "TaskStackListener failed, fallback to UsageStats polling");
            handler.postDelayed(pollRunnable, POLL_MS);
        }
    }

    /** 注册 TaskStackListener 子类。hidden_api_policy=1 时反射可用。 */
    private boolean tryRegisterTaskStackListener() {
        try {
            taskListener = new TaskStackListener() {
                @Override
                public void onTaskMovedToFront(ActivityManager.RunningTaskInfo ti) {
                    String pkg = packageNameFromTaskInfo(ti);
                    if (pkg != null) onFgChanged(pkg);
                }
                @Override
                public void onTaskMovedToFront(int taskId) {
                    // 旧签名,忽略(新 API 用 RunningTaskInfo 版)
                }
                @Override
                public void onTaskStackChanged() {
                    // 兜底:某些场景只发这个。查当前 resumed activity。
                    queryAndApplyCurrent();
                }
            };
            ActivityTaskManager atm = ActivityTaskManager.getInstance();
            // registerTaskStackListener 是 @hide,反射调(hidden_api_policy=1 绕黑名单)
            Method reg = ActivityTaskManager.class.getMethod("registerTaskStackListener", TaskStackListener.class);
            reg.invoke(atm, taskListener);
            // 注册后立即查一次当前前台
            queryAndApplyCurrent();
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "registerTaskStackListener failed: " + t);
            return false;
        }
    }

    private String packageNameFromTaskInfo(ActivityManager.RunningTaskInfo ti) {
        try {
            ComponentName cn = ti.topActivity;
            if (cn != null) return cn.getPackageName();
        } catch (Throwable ignore) {}
        return null;
    }

    /** 主动查当前 resumed activity 包名(注册后初始化 + onTaskStackChanged 兜底用)。 */
    private void queryAndApplyCurrent() {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            // getRunningTasks 限权只拿自己,改用 ActivityTaskManager.getRunningTasks(@hide)
            Method m = ActivityTaskManager.class.getMethod("getRunningTasks", int.class);
            java.util.List<ActivityManager.RunningTaskInfo> l =
                (java.util.List<ActivityManager.RunningTaskInfo>) m.invoke(ActivityTaskManager.getInstance(), 1);
            if (l != null && !l.isEmpty()) {
                String pkg = packageNameFromTaskInfo(l.get(0));
                if (pkg != null) onFgChanged(pkg);
            }
        } catch (Throwable t) {
            // 限权失败就算了,等下次事件
        }
    }

    /** 前台包名变化处理。 */
    private synchronized void onFgChanged(String pkg) {
        if (pkg == null || pkg.equals(sCurrentPkg)) return;
        // 切走旧 pkg:若 dirty 则记
        recordDirtyIfAny();
        sCurrentPkg = pkg;
        Log.i(TAG, "fg -> " + pkg);
        if (!ms.isPerAppOn()) return;
        PerAppStore.Entry e = store.get(pkg);
        int target = (e != null) ? e.mode : ms.getDefaultMode();
        EinkControl.setMode(target);
        ms.setLastMode(target);
        // per-app 自动切模式时:只更新通知文本(不重 startForeground,避免声音/振动)
        RefreshService.updateNotificationText(ctx);
    }

    /** 若 dirty 且当前 pkg 已知,把当前模式记到 per-app。 */
    private void recordDirtyIfAny() {
        if (sModeDirty && sCurrentPkg != null) {
            int cur = EinkControl.getMode();
            store.recordIfDirty(sCurrentPkg, cur);
            Log.i(TAG, "recorded " + sCurrentPkg + " -> " + ModeStore.nameOf(cur));
        }
        sModeDirty = false;
    }

    private final Runnable pollRunnable = new Runnable() {
        @Override public void run() {
            pollUsageStats();
            handler.postDelayed(this, POLL_MS);
        }
    };

    /** 降级:UsageStats 查最近 MOVE_TO_FOREGROUND。 */
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
