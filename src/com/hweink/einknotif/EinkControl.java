package com.hweink.einknotif;

import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

/**
 * 作业帮 IEbookManager binder 控制 (同 einkrefresh/inkOS 的 ZuoyebangEink)。
 * EbookService.setProperty 无 enforceCallingPermission, 任何 app 可调。
 */
final class EinkControl {
    private static final String TAG = "EinkControl";
    private static final String DESCRIPTOR = "android.os.IEbookManager";
    private static final int TRANSACTION_setProperty = 2;
    private static volatile int sFull = 1;

    private EinkControl() {}

    private static IBinder getService() {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            return (IBinder) sm.getMethod("getService", String.class).invoke(null, "ebook");
        } catch (Throwable t) {
            Log.w(TAG, "getService: " + t);
            return null;
        }
    }

    static boolean setProp(String key, String val) {
        IBinder b = getService();
        if (b == null) return false;
        Parcel d = Parcel.obtain(), r = Parcel.obtain();
        try {
            d.writeInterfaceToken(DESCRIPTOR);
            d.writeString(key); d.writeString(val);
            b.transact(TRANSACTION_setProperty, d, r, 0);
            r.readException();
            return true;
        } catch (Throwable t) { Log.w(TAG, "setProp: " + t); return false; }
        finally { d.recycle(); r.recycle(); }
    }

    static String getProp(String key, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            return (String) sp.getMethod("get", String.class, String.class).invoke(null, key, def);
        } catch (Throwable t) { return def; }
    }

    static int getMode() {
        try { return Integer.parseInt(getProp("sys.ebook.mode", "9")); }
        catch (NumberFormatException e) { return 9; }
    }

    static boolean setMode(int mode) {
        return setProp("sys.ebook.mode", String.valueOf(mode));
    }

    static boolean triggerFullRefresh() {
        int n = ++sFull;
        if (n > 2147483547) { sFull = 1; n = 1; }
        return setProp("sys.ebook.one_full_mode_timeline", String.valueOf(n));
    }
}
