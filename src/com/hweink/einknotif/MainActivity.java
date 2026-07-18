package com.hweink.einknotif;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * 设置页。一屏:
 *  - 当前模式 + 5 模式单选(点即切)
 *  - 全刷按钮
 *  - flip 清晰侧下拉
 *  - 开关:常驻通知 / 开机恢复 / per-app / 切清晰侧自动全刷
 *  - per-app 列表(自动学习的 pkg)
 *  - 授权引导(usage access / 通知)
 */
public class MainActivity extends Activity {
    private ModeStore ms;
    private PerAppStore perApp;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        ms = new ModeStore(this);
        perApp = new PerAppStore(this);

        // 启动服务
        Intent svc = new Intent(this, RefreshService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
        else startService(svc);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 40, 40, 40);

        addTitle("E-ink 刷新控制");
        addCurrentMode();
        addModeButtons();
        addFullRefreshButton();
        addSeparator();
        addTitle("设置");
        addFlipClearSide();
        addSwitch("常驻通知", ms.isNotifOn(), b -> { ms.setNotifOn(b); RefreshService.refreshNotification(this); });
        addSwitch("开机恢复模式", ms.isBootRestore(), b -> ms.setBootRestore(b));
        addSwitch("per-app 自动切换", ms.isPerAppOn(), b -> ms.setPerAppOn(b));
        addSwitch("切到清晰侧自动全刷", ms.isAutoFullOnClear(), b -> ms.setAutoFullOnClear(b));
        addSeparator();
        addTitle("per-app 记忆(自动学习)");
        addPerAppList();
        addSeparator();
        addTitle("授权(按需)");
        addAuthButtons();

        android.widget.ScrollView sc = new android.widget.ScrollView(this);
        sc.addView(root);
        setContentView(sc);
    }

    private void addTitle(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(18);
        tv.setPadding(0, 30, 0, 10);
        root.addView(tv);
    }

    private void addCurrentMode() {
        int cur = EinkControl.getMode();
        TextView tv = new TextView(this);
        tv.setText("当前模式: " + ModeStore.nameOf(cur) + " (" + cur + ")");
        tv.setTextSize(16);
        tv.setPadding(0, 10, 0, 10);
        root.addView(tv);
    }

    private void addModeButtons() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int cur = EinkControl.getMode();
        for (int i = 0; i < RefreshService.ALL_MODES.length; i++) {
            final int mode = RefreshService.ALL_MODES[i];
            Button b = new Button(this);
            b.setText(RefreshService.ALL_NAMES[i] + (cur == mode ? " ✓" : ""));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> {
                EinkControl.setMode(mode);
                ms.setLastMode(mode);
                ForegroundWatcher.markDirty();
                if (ms.isPerAppOn()) {
                    String pkg = ForegroundWatcher.currentPkg();
                    if (pkg != null) perApp.put(pkg, mode);
                }
                if (mode != ModeStore.MODE_A2 && ms.isAutoFullOnClear()) EinkControl.triggerFullRefresh();
                RefreshService.refreshNotification(this);
                recreate();
            });
            row.addView(b);
        }
        root.addView(row);
    }

    private void addFullRefreshButton() {
        Button b = new Button(this);
        b.setText("⚡ 立即全刷");
        b.setOnClickListener(v -> EinkControl.triggerFullRefresh());
        root.addView(b);
    }

    private void addFlipClearSide() {
        Button b = new Button(this);
        b.setText("flip 清晰侧: " + ModeStore.nameOf(ms.getFlipClearSide()));
        b.setOnClickListener(v -> {
            int[] opts = {ModeStore.MODE_CLEAR, ModeStore.MODE_NORMAL};
            String[] names = {"清晰 (9)", "普通 (7)"};
            new AlertDialog.Builder(this)
                .setTitle("flip 清晰侧")
                .setItems(names, (d, which) -> {
                    ms.setFlipClearSide(opts[which]);
                    recreate();
                }).show();
        });
        root.addView(b);
    }

    private interface CB { void on(boolean b); }
    private void addSwitch(String label, boolean init, CB cb) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setChecked(init);
        sw.setOnCheckedChangeListener((v, c) -> cb.on(c));
        root.addView(sw);
    }

    private void addSeparator() {
        View v = new View(this);
        v.setBackgroundColor(0xFF888888);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 2);
        lp.setMargins(0, 30, 0, 10);
        v.setLayoutParams(lp);
        root.addView(v);
    }

    private void addPerAppList() {
        List<PerAppStore.Entry> all = perApp.allSorted();
        if (all.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("(尚无。在各 app 里用通知/磁贴切模式即自动记录)");
            tv.setPadding(0, 10, 0, 10);
            root.addView(tv);
            return;
        }
        for (PerAppStore.Entry e : all) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView tv = new TextView(this);
            tv.setText(e.pkg + "  → " + ModeStore.nameOf(e.mode));
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            Button del = new Button(this);
            del.setText("删除");
            del.setOnClickListener(v -> { perApp.remove(e.pkg); recreate(); });
            row.addView(tv);
            row.addView(del);
            root.addView(row);
        }
    }

    private void addAuthButtons() {
        Button notif = new Button(this);
        notif.setText("授权通知(Android 13+)");
        notif.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 33) {
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            }
        });
        root.addView(notif);

        Button usage = new Button(this);
        usage.setText("授权 Usage Access(per-app 用)");
        usage.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        root.addView(usage);
    }
}
