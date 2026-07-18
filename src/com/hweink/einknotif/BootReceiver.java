package com.hweink.einknotif;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 开机恢复模式 + 启动常驻通知服务。
 * 若 isBootRestore 开:把 last_mode 设回 sys.ebook.mode(sys. 不持久,需 app 恢复)。
 * 同时尝试设 hidden_api_policy=1(Magisk su 可用则直接设,否则跳过)。
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        ModeStore ms = new ModeStore(ctx);
        if (ms.isBootRestore()) {
            int m = ms.getLastMode();
            EinkControl.setMode(m);
            Log.i("EinkBoot", "restored mode " + ModeStore.nameOf(m));
        }
        // 设 hidden_api_policy=1(让 TaskStackListener 反射可用)。Magisk su 直接设。
        ensureHiddenApiPolicy();
        // 启动常驻通知服务(它内部启动 ForegroundWatcher)
        Intent svc = new Intent(ctx, RefreshService.class);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(svc);
        else ctx.startService(svc);
    }

    /** 尝试通过 Magisk su 设 hidden_api_policy=1。失败则忽略(app 仍可降级轮询)。 */
    static boolean ensureHiddenApiPolicy() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c",
                "settings put global hidden_api_policy 1"});
            int rc = p.waitFor();
            Log.i("EinkBoot", "hidden_api_policy set rc=" + rc);
            return rc == 0;
        } catch (Throwable t) {
            Log.w("EinkBoot", "su not available: " + t.getMessage());
            return false;
        }
    }
}

