package com.hweink.einknotif;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

/**
 * 常驻通知刷新控制服务。
 * 通知带 5 个刷新模式按钮 + 1 个全刷按钮,
 * 点按钮经 IEbookManager binder 切 sys.ebook.mode(无权限)。
 * 同时启动 ForegroundWatcher(per-app 自动切换)。
 */
public class RefreshService extends Service {
    private static final String TAG = "EinkNotif";
    private static final String CH_ID = "eink_refresh";
    private static final int NOTIF_ID = 1;

    static final int[] MODES = {9, 7, 13};              // 通知只显示 3 个(系统上限 3):清晰/普通/极速
    static final String[] NAMES = {"清晰", "普通", "极速"};
    // 全部 5 档(设置页/循环磁贴用)
    static final int[] ALL_MODES = {9, 7, 15, 14, 13};
    static final String[] ALL_NAMES = {"清晰", "普通", "快速", "高速", "极速"};

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannel ch = new NotificationChannel(CH_ID, "E-ink 刷新控制",
                NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("刷新模式切换 + 全刷 + per-app 自动");
        ch.setShowBadge(false);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        // 启动 per-app 前台监听(事件驱动,零功耗)
        try { ForegroundWatcher.start(this); } catch (Throwable t) { Log.w(TAG, "fg watcher: " + t); }
        Log.i(TAG, "service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String a = intent.getAction();
            if (a.equals("FULL")) {
                EinkControl.triggerFullRefresh();
                Log.i(TAG, "FULL refresh");
            } else if (a.startsWith("MODE_")) {
                int mode = Integer.parseInt(a.substring(5));
                applyMode(mode);
            } else if (a.equals("REFRESH_NOTIF")) {
                // 只刷通知,不改模式
            }
        }
        ModeStore ms = new ModeStore(this);
        if (ms.isNotifOn()) {
            showNotification();
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE);
        }
        return START_STICKY;
    }

    /** 设模式 + 记 last_mode + per-app 记当前前台 + dirty 标记 + 可选全刷。 */
    private void applyMode(int mode) {
        NavbarAction.setMode(this, mode);
        Log.i(TAG, "mode " + ModeStore.nameOf(mode)
                + " for " + ForegroundWatcher.currentPkg());
    }

    /** 外部调:刷新通知标题(显示当前模式)。走 startForeground,会重发通知(有声音风险)。仅手动切时用。 */
    static void refreshNotification(Context ctx) {
        Intent i = new Intent(ctx, RefreshService.class);
        i.setAction("REFRESH_NOTIF");
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
        else ctx.startService(i);
    }

    /** per-app 自动切模式时用:只更新现有通知文本,不重 startForeground(无声音/振动)。 */
    static void updateNotificationText(Context ctx) {
        try {
            int cur = EinkControl.getMode();
            android.app.Notification n = buildNotificationBody(ctx, cur);
            ((NotificationManager) ctx.getSystemService(NOTIFICATION_SERVICE))
                    .notify(NOTIF_ID, n);
        } catch (Throwable ignore) {}
    }

    /** 构造通知(复用)。 */
    private static android.app.Notification buildNotificationBody(Context ctx, int cur) {
        Notification.Builder nb;
        if (Build.VERSION.SDK_INT >= 26) nb = new Notification.Builder(ctx, CH_ID);
        else nb = new Notification.Builder(ctx).setPriority(Notification.PRIORITY_LOW);
        nb.setContentTitle("E-ink 刷新: " + nameOf(cur) + " (" + cur + ")")
          .setContentText("点模式切换 · 长按通知设置")
          .setSmallIcon(android.R.drawable.ic_menu_rotate)
          .setOngoing(true)
          .setShowWhen(false);
        for (int i = 0; i < MODES.length; i++) {
            Intent it = new Intent(ctx, RefreshService.class);
            it.setAction("MODE_" + MODES[i]);
            int fl = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) fl |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getService(ctx, MODES[i], it, fl);
            nb.addAction(new Notification.Action.Builder(
                    null, NAMES[i] + (cur == MODES[i] ? " ✓" : ""), pi).build());
        }
        Intent fullIt = new Intent(ctx, RefreshService.class);
        fullIt.setAction("FULL");
        int ff = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) ff |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent fullPi = PendingIntent.getService(ctx, 0, fullIt, ff);
        nb.addAction(new Notification.Action.Builder(null, "⚡全刷", fullPi).build());
        return nb.build();
    }

    static String nameOf(int mode) {
        for (int i = 0; i < MODES.length; i++) if (MODES[i] == mode) return NAMES[i];
        return "?";
    }

    private void showNotification() {
        Notification.Builder nb;
        if (Build.VERSION.SDK_INT >= 26) nb = new Notification.Builder(this, CH_ID);
        else nb = new Notification.Builder(this).setPriority(Notification.PRIORITY_LOW);
        int cur = EinkControl.getMode();
        nb.setContentTitle("E-ink 刷新: " + nameOf(cur) + " (" + cur + ")")
          .setContentText("点模式切换 · 长按通知设置")
          .setSmallIcon(android.R.drawable.ic_menu_rotate)
          .setOngoing(true)
          .setShowWhen(false);
        for (int i = 0; i < MODES.length; i++) {
            Intent it = new Intent(this, RefreshService.class);
            it.setAction("MODE_" + MODES[i]);
            int fl = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) fl |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getService(this, MODES[i], it, fl);
            nb.addAction(new Notification.Action.Builder(
                    null, NAMES[i] + (cur == MODES[i] ? " ✓" : ""), pi).build());
        }
        Intent fullIt = new Intent(this, RefreshService.class);
        fullIt.setAction("FULL");
        int ff = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) ff |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent fullPi = PendingIntent.getService(this, 0, fullIt, ff);
        nb.addAction(new Notification.Action.Builder(null, "⚡全刷", fullPi).build());
        startForeground(NOTIF_ID, nb.build());
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
