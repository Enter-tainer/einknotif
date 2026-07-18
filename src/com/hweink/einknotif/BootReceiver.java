package com.hweink.einknotif;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * 开机恢复模式 + 启动常驻通知服务。
 * 若 isBootRestore 开:把 last_mode 设回 sys.ebook.mode(sys. 不持久,需 app 恢复)。
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
        // 启动常驻通知服务(它内部启动 ForegroundWatcher)
        Intent svc = new Intent(ctx, RefreshService.class);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(svc);
        else ctx.startService(svc);
    }
}
