package com.gammaos.secondaryhome;

import android.content.ContentResolver;
import android.content.Context;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.SystemProperties;
import android.provider.Settings;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Live state and actions of the control centre, the Android-mode twin of the nano Control
 * Centre (frameworks/base/cmds/gammaos-nano/NanoControlCenter.cpp). Same props, sysfs nodes and
 * polling cadence: CPU/GPU every 500 ms, temps/RAM/battery/backlight every second, volume and
 * Wi-Fi every 2 s. The app runs as the system uid, so the backlight nodes (system:system 0644)
 * are writable and every read below is allowed.
 */
final class CcState {
    static final String PROP_PERF = "persist.gammaos.performance_mode";
    static final String PROP_SPLIT = "persist.gammaos.multidisplay.split_brightness";
    static final String PROP_SHADER = "persist.gammaos.shader.enable";
    static final String PROP_EQ = "persist.sys.gammaeq.enable";
    static final String PROP_MOUSE = "sys.gammaos.gamepad.mouse_active";
    static final String PROP_D1_OVERRIDE = "sys.gammaos.multidisplay.split_brightness.d1.override";
    static final String PROP_D1_LAST = "persist.gammaos.multidisplay.split_brightness.d1.last";
    static final String PROP_D1_IS_TOP = "persist.gammaos.nano.cc.d1_is_top";
    static final String PROP_MULTIVOL = "persist.gammaos.audio.multivolume";
    static final String PROP_DUALSTACK = "sys.gammaos.dualstack.active";
    static final String PROP_DOUBLETAP = "persist.gammaos.nano.cc.doubletapwake";
    static final String PROP_TIMEOUT = "persist.gammaos.nano.cc.sleeptimeout";
    static final String VOLUME_MAP = "gammaos_audio_display_volume_map";

    // ---- live values (written on the poll thread, read on the UI thread; plain ints are fine
    // ---- for display values, a torn frame only shows one stale number) ----
    volatile int cpuMhz, cpuMaxMhz = 2000, cpuPct;
    volatile int gpuMhz, gpuMaxMhz = 1, gpuPct;
    volatile int socTempC, gpuTempC;
    volatile int ramUsedMb, ramTotalMb;
    volatile int battPct;
    volatile float watts;
    volatile boolean charging;
    volatile int briTop, briTopMax = 255, briBot, briBotMax = 255;
    volatile int volCur, volMin, volMax = 15, volTop, volBot;
    volatile boolean multiVol;
    volatile boolean wifiOn;

    private long mPrevTotal, mPrevIdle;
    private long mLastSlowMs, mLastVolMs;
    private boolean mPrimed;
    private final String mBlTop, mBlBot;
    private int mKgsl = -1;
    private long mGpuHwMax;

    private final Context mContext;
    private final AudioManager mAudio;
    private final WifiManager mWifi;

    /** Display ids of the panel this home is on and of the other (content) panel. */
    int ownDisplay = 1, contentDisplay = 0;
    /** True while a slider is held: the poller then leaves the dragged value alone. */
    volatile int heldSlider = -1;

    CcState(Context ctx) {
        mContext = ctx.getApplicationContext();
        mAudio = mContext.getSystemService(AudioManager.class);
        mWifi = mContext.getSystemService(WifiManager.class);
        final String b = SystemProperties.get("persist.gammaos.nano.cc.bl_bottom", "backlight");
        final String t = SystemProperties.get("persist.gammaos.nano.cc.bl_top", "backlight1");
        mBlBot = "/sys/class/backlight/" + b;
        mBlTop = "/sys/class/backlight/" + t;
    }

    String blBri(boolean bottom) { return (bottom ? mBlBot : mBlTop) + "/brightness"; }
    String blMax(boolean bottom) { return (bottom ? mBlBot : mBlTop) + "/max_brightness"; }

    // ------------------------------------------------------------------ polling

    /** One poll step; call every 500 ms from a worker thread. */
    void poll() {
        final long t = android.os.SystemClock.uptimeMillis();
        readCpu();
        readGpu();
        if (!mPrimed || t - mLastSlowMs >= 1000) {
            socTempC = thermalAny(new String[] {"soc-thermal", "cpuss-0", "cpu-1-0", "cpu-0-0",
                    "cpu0-thermal", "apss-0"});
            gpuTempC = thermalAny(new String[] {"gpu-thermal", "gpu", "gpuss-0", "gpu0-thermal"});
            readRam();
            readBattery();
            if (heldSlider < 0) readBrightness();
            mLastSlowMs = t;
        }
        if ((!mPrimed || t - mLastVolMs > 2000) && heldSlider < 0) {
            readVolume();
            wifiOn = mWifi != null && mWifi.isWifiEnabled();
            mLastVolMs = t;
        }
        mPrimed = true;
    }

    private void readCpu() {
        int mx = 0;
        for (int c = 0; c < 8; c++) {
            final int khz = readInt("/sys/devices/system/cpu/cpu" + c + "/cpufreq/scaling_cur_freq", 0);
            if (khz > mx) mx = khz;
        }
        if (mx > 0) cpuMhz = mx / 1000;
        final int maxk = readInt("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq", 2160000);
        if (maxk > 0) cpuMaxMhz = maxk / 1000;
        final String line = readLine("/proc/stat");
        if (line == null || !line.startsWith("cpu ")) return;
        final String[] f = line.trim().split("\\s+");
        if (f.length < 5) return;
        long tot = 0;
        final long[] v = new long[8];
        for (int i = 0; i < 8 && i + 1 < f.length; i++) {
            try { v[i] = Long.parseLong(f[i + 1]); } catch (NumberFormatException e) { v[i] = 0; }
            tot += v[i];
        }
        final long idl = v[3] + v[4];
        if (mPrevTotal != 0 && tot > mPrevTotal) {
            final long dt = tot - mPrevTotal;
            final long di = idl > mPrevIdle ? idl - mPrevIdle : 0;
            cpuPct = clamp((int) (100.0 * (dt - di) / dt), 0, 100);
        }
        mPrevTotal = tot;
        mPrevIdle = idl;
    }

    private void readGpu() {
        // Qualcomm (RP Duo Lite) exposes kgsl nodes; Rockchip (RG DS) a devfreq dir.
        if (mKgsl < 0) {
            mKgsl = (new File("/sys/class/kgsl/kgsl-3d0/gpuclk").canRead()
                    && !new File("/sys/class/devfreq/fde60000.gpu/cur_freq").canRead()) ? 1 : 0;
        }
        if (mKgsl == 1) {
            final long cur = readLong("/sys/class/kgsl/kgsl-3d0/gpuclk", 0);
            if (mGpuHwMax <= 0) mGpuHwMax = readLong("/sys/class/kgsl/kgsl-3d0/max_gpuclk", 0);
            if (cur > 0) gpuMhz = (int) (cur / 1000000);
            if (mGpuHwMax > 0) gpuMaxMhz = (int) (mGpuHwMax / 1000000);
            gpuPct = clamp(readInt("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage", gpuPct), 0, 100);
            return;
        }
        final long cur = readLong("/sys/class/devfreq/fde60000.gpu/cur_freq", 0);
        // Ring = clock against the highest OPP, not the governor cap the performance mode sets.
        if (mGpuHwMax <= 0) {
            final String opps = readLine("/sys/class/devfreq/fde60000.gpu/available_frequencies");
            if (opps != null) {
                for (String s : opps.trim().split("\\s+")) {
                    try { mGpuHwMax = Math.max(mGpuHwMax, Long.parseLong(s)); }
                    catch (NumberFormatException ignored) { }
                }
            }
            if (mGpuHwMax <= 0) {
                mGpuHwMax = readLong("/sys/class/devfreq/fde60000.gpu/max_freq", 900000000L);
            }
        }
        if (cur > 0) gpuMhz = (int) (cur / 1000000);
        if (mGpuHwMax > 0) gpuMaxMhz = (int) (mGpuHwMax / 1000000);
        final String load = readLine("/sys/class/devfreq/fde60000.gpu/load");
        if (load != null) {
            final String n = load.trim().split("[@\\s]")[0];
            try { gpuPct = clamp(Integer.parseInt(n), 0, 100); } catch (NumberFormatException ignored) { }
        }
    }

    /** First thermal zone whose type matches one of the names, in order of preference. */
    private static int thermalAny(String[] want) {
        for (String w : want) {
            for (int z = 0; z < 64; z++) {
                final String type = readLine("/sys/class/thermal/thermal_zone" + z + "/type");
                if (type == null) break;
                if (type.trim().equals(w)) {
                    return readInt("/sys/class/thermal/thermal_zone" + z + "/temp", 0) / 1000;
                }
            }
        }
        return 0;
    }

    private void readRam() {
        long total = 0, avail = 0;
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String l;
            while ((l = r.readLine()) != null) {
                if (l.startsWith("MemTotal:")) total = kb(l);
                else if (l.startsWith("MemAvailable:")) avail = kb(l);
                if (total > 0 && avail > 0) break;
            }
        } catch (IOException ignored) {
            return;
        }
        if (total > 0) {
            ramTotalMb = (int) (total / 1024);
            ramUsedMb = (int) ((total - avail) / 1024);
        }
    }

    private static long kb(String l) {
        final String[] f = l.trim().split("\\s+");
        try { return Long.parseLong(f[1]); } catch (RuntimeException e) { return 0; }
    }

    private void readBattery() {
        final String ps = "/sys/class/power_supply/battery/";
        battPct = readInt(ps + "capacity", battPct);
        final long uv = readLong(ps + "voltage_now", 0);
        final long ua = Math.abs(readLong(ps + "current_now", 0));
        watts = (float) ((uv / 1e6) * (ua / 1e6));
        final String s = readLine(ps + "status");
        charging = s != null && (s.startsWith("Charging") || s.startsWith("Full"));
    }

    void readBrightness() {
        briTop = readInt(blBri(false), briTop);
        briTopMax = readInt(blMax(false), 255);
        briBot = readInt(blBri(true), briBot);
        briBotMax = readInt(blMax(true), 255);
    }

    private void readVolume() {
        if (mAudio == null) return;
        final int cur = mAudio.getStreamVolume(AudioManager.STREAM_MUSIC);
        final int lo = mAudio.getStreamMinVolume(AudioManager.STREAM_MUSIC);
        final int hi = mAudio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        if (hi > lo && cur >= lo) {
            volCur = cur;
            volMin = lo;
            volMax = hi;
        }
        // Per-display media volume (AudioService applies the Settings.Global map live). Only when
        // the vendor enables it and no dual-stack app spans both panels, like the nano CC.
        final boolean mv = SystemProperties.getBoolean(PROP_MULTIVOL, false)
                && !SystemProperties.getBoolean(PROP_DUALSTACK, false);
        int bot = volCur, top = volCur;
        if (mv) {
            final String map = Settings.Global.getString(mContext.getContentResolver(), VOLUME_MAP);
            if (map != null) {
                for (String tok : map.split("[;\\s]")) {
                    final int eq = tok.indexOf('=');
                    if (eq <= 0) continue;
                    try {
                        final int id = Integer.parseInt(tok.substring(0, eq).trim());
                        final int v = Integer.parseInt(tok.substring(eq + 1).trim());
                        if (id == ownDisplay) bot = v;
                        else if (id == contentDisplay) top = v;
                    } catch (NumberFormatException ignored) { }
                }
            }
        }
        volTop = top;
        volBot = bot;
        multiVol = mv;
    }

    // ------------------------------------------------------------------ actions

    /** 0 = stock, 1 = powersave, 2 = max. */
    static int perfState() {
        final String v = SystemProperties.get(PROP_PERF, "stock");
        if ("powersave".equals(v)) return 1;
        if ("max".equals(v)) return 2;
        return 0;
    }

    static void cyclePerf() {
        final String v = SystemProperties.get(PROP_PERF, "stock");
        final String next = "stock".equals(v) ? "powersave" : "powersave".equals(v) ? "max" : "stock";
        SystemProperties.set(PROP_PERF, next);
    }

    static boolean propOn(String prop) {
        return SystemProperties.getInt(prop, 0) != 0;
    }

    static void toggleProp(String prop) {
        SystemProperties.set(prop, propOn(prop) ? "0" : "1");
    }

    void toggleWifi() {
        final boolean on = !wifiOn;
        wifiOn = on;
        if (mWifi != null) mWifi.setWifiEnabled(on);
    }

    void setMasterVolume(int v) {
        v = clamp(v, volMin, volMax);
        if (mAudio != null) mAudio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0);
    }

    void setDisplayVolumeMap(int bot, int top) {
        bot = clamp(bot, volMin, volMax);
        top = clamp(top, volMin, volMax);
        final ContentResolver cr = mContext.getContentResolver();
        Settings.Global.putString(cr, VOLUME_MAP,
                ownDisplay + "=" + bot + ";" + contentDisplay + "=" + top);
    }

    /**
     * Persist a released brightness slider so it survives sleep/wake, as the nano CC does on
     * release: the panel on the framework's split slot d1 goes through the d1 override (when
     * split brightness is on), the other through the system brightness setting.
     */
    void persistBrightness(int slider) {
        final boolean d1IsTop = SystemProperties.getBoolean(PROP_D1_IS_TOP, true);
        final int d1Slider = d1IsTop ? 0 : 1;
        final int v = slider == 0 ? briTop : briBot;
        if (slider == d1Slider && SystemProperties.getBoolean(PROP_SPLIT, false)) {
            SystemProperties.set(PROP_D1_OVERRIDE, Integer.toString(v));
            SystemProperties.set(PROP_D1_LAST, Integer.toString(v));
            return;
        }
        final int mx = slider == 0 ? briTopMax : briBotMax;
        final int lvl = clamp(mx > 0 ? Math.round(v * 255f / mx) : v, 1, 255);
        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        Settings.System.putInt(mContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, lvl);
    }

    // ------------------------------------------------------------------ sysfs helpers

    static String readLine(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path), 256)) {
            return r.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    static int readInt(String path, int def) {
        final String s = readLine(path);
        if (s == null) return def;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    static long readLong(String path, long def) {
        final String s = readLine(path);
        if (s == null) return def;
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    static boolean writeInt(String path, int v) {
        try (FileOutputStream o = new FileOutputStream(path)) {
            o.write(Integer.toString(v).getBytes(StandardCharsets.US_ASCII));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
