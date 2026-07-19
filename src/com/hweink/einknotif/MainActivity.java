package com.hweink.einknotif;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.CheckBox;
import android.widget.SeekBar;
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
        addTitle("色彩参数(当前应用)");
        addColorControls();
        addSeparator();
        addTitle("设置");
        addFlipClearSide();
        addSwitch("常驻通知", ms.isNotifOn(), b -> { ms.setNotifOn(b); RefreshService.refreshNotification(this); });
        addSwitch("开机恢复模式", ms.isBootRestore(), b -> ms.setBootRestore(b));
        addSwitch("per-app 自动切换", ms.isPerAppOn(), b -> ms.setPerAppOn(b));
        addSwitch("切到清晰侧自动全刷", ms.isAutoFullOnClear(), b -> ms.setAutoFullOnClear(b));
        addSwitch("256灰阶平滑", EinkControl.isGray256(), b -> {
            EinkControl.setGray256(b);
            EinkControl.triggerFullRefresh();   // 立即全刷让抖动/非抖动可见
            try {
                android.service.quicksettings.TileService.requestListeningState(this,
                    new android.content.ComponentName(this, Gray256TileService.class));
            } catch (Throwable ignore) {}
        });
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
            Button b = new Button(this, null, 0, R.style.Widget_Eink_Button);
            b.setText(RefreshService.ALL_NAMES[i]);
            // 当前模式:实心黑底白字(setActivated 触发 btn_eink 的 activated 态)
            b.setActivated(cur == mode);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> {
                NavbarAction.setMode(this, mode);
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

    private void addColorControls() {
        addColorSlider("色深", NavbarAction.COLOR_DEP, EinkControl.getColorDep());
        addColorSlider("对比度", NavbarAction.CONTRAST, EinkControl.getContrast());
        addColorSlider("饱和度", NavbarAction.SATURATION, EinkControl.getSaturation());
        addColorSlider("亮度", NavbarAction.BRIGHTNESS, EinkControl.getBrightness());
        Button reset = new Button(this);
        reset.setText("重置色彩参数为 64");
        reset.setOnClickListener(v -> {
            NavbarAction.setColor(this, NavbarAction.COLOR_DEP, EinkControl.COLOR_DEFAULT);
            NavbarAction.setColor(this, NavbarAction.CONTRAST, EinkControl.COLOR_DEFAULT);
            NavbarAction.setColor(this, NavbarAction.SATURATION, EinkControl.COLOR_DEFAULT);
            NavbarAction.setColor(this, NavbarAction.BRIGHTNESS, EinkControl.COLOR_DEFAULT);
            EinkControl.triggerFullRefresh();
            recreate();
        });
        root.addView(reset);
    }

    private void addColorSlider(String label, String color, int initial) {
        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.HORIZONTAL);
        TextView name = new TextView(this);
        name.setText(label);
        name.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView value = new TextView(this);
        value.setText(String.valueOf(initial));
        title.addView(name);
        title.addView(value);
        root.addView(title);

        SeekBar slider = new SeekBar(this);
        slider.setMax(128);
        slider.setProgress(initial);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                value.setText(String.valueOf(progress));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                NavbarAction.setColor(MainActivity.this, color, seekBar.getProgress());
            }
        });
        root.addView(slider);
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
        // 用 CheckBox 承载开关 drawable(button=switch_eink selector);静态拨杆,无动画。
        CheckBox sw = new CheckBox(this, null, 0, R.style.Widget_Eink_Switch);
        sw.setText(label);
        sw.setChecked(init);
        sw.setOnCheckedChangeListener((v, c) -> cb.on(c));
        root.addView(sw);
    }

    private void addSeparator() {
        View v = new View(this);
        v.setBackgroundColor(0xFF000000);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
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
            tv.setText(e.pkg + "  → " + ModeStore.nameOf(e.mode)
                    + " / 色" + e.colorDep + " 对" + e.contrast
                    + " 饱" + e.saturation + " 亮" + e.brightness);
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
