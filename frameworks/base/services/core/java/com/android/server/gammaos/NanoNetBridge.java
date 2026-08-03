/*
 * GammaOS Nano Network Bridge
 *
 * The native nano launcher runs in the bootanim SELinux domain and cannot bind the
 * WifiManager / BluetoothAdapter framework APIs directly, so historically it shelled
 * out to `cmd wifi status` / `dumpsys bluetooth_manager` on a background poll and
 * hand-parsed the free-form text. That is fragile (the parsers track a specific
 * AOSP build's wording) and costly (two popen() forks per poll tick off the
 * compressed system image).
 *
 * This bridge holds WifiManager/BluetoothAdapter live inside the warm system_server
 * JVM, listens to the real framework broadcasts, and publishes a compact,
 * machine-readable HUD state to /data/system/nano_net_state.txt (atomic temp+rename)
 * plus a monotonic generation prop (sys.gammaos.nano.net_generation). nano reads that
 * file instead of forking - event-driven, no text-parsing of version-specific dumps,
 * no per-tick forks. It publishes only when the computed state actually changes
 * (debounced), with an ~8s heartbeat recompute so a missed broadcast still self-heals.
 *
 * Commands (connect/pair/toggle) still go through the existing native gammaos-net
 * helper; this bridge is the read/state path only. It is safe in full Android (nano
 * is not running to read the file) and self-gates on minimal_boot anyway.
 */

package com.android.server.gammaos;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemProperties;
import android.util.Slog;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.util.Set;

public final class NanoNetBridge {
    private static final String TAG = "NanoNetBridge";
    private static final String STATE_FILE = "/data/system/nano_net_state.txt";
    private static final String GEN_PROP = "sys.gammaos.nano.net_generation";
    private static final long HEARTBEAT_MS = 8000;

    // Keep a static reference so the receiver-owning instance is never GC'd.
    private static NanoNetBridge sInstance;

    private final Context mContext;
    private WifiManager mWifi;
    private BluetoothAdapter mBt;
    private Handler mHandler;
    private int mGen = 0;
    private String mLastPayload = "";
    private Method mIsConnected;   // BluetoothDevice.isConnected() (@hide) via reflection

    public NanoNetBridge(Context context) {
        mContext = context;
    }

    /** Called from SystemServer's minimal-boot bring-up thread (after boot_completed). */
    public static synchronized void start(Context context) {
        if (sInstance != null) return;
        if (!SystemProperties.getBoolean("sys.gammaos.minimal_boot", false)) {
            return; // only the nano home consumes this
        }
        sInstance = new NanoNetBridge(context);
        sInstance.init();
    }

    private void init() {
        try {
            mWifi = mContext.getSystemService(WifiManager.class);
        } catch (Throwable t) {
            Slog.w(TAG, "no WifiManager", t);
        }
        try {
            BluetoothManager bm = mContext.getSystemService(BluetoothManager.class);
            if (bm != null) mBt = bm.getAdapter();
        } catch (Throwable t) {
            Slog.w(TAG, "no BluetoothAdapter", t);
        }
        try {
            mIsConnected = BluetoothDevice.class.getMethod("isConnected");
        } catch (Throwable t) {
            mIsConnected = null; // fall back to count 0 rather than crash
        }

        HandlerThread ht = new HandlerThread("NanoNetBridge");
        ht.setDaemon(true);
        ht.start();
        mHandler = new Handler(ht.getLooper());

        IntentFilter f = new IntentFilter();
        // Wi-Fi: radio on/off, association changes, live RSSI.
        f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.RSSI_CHANGED_ACTION);
        // Bluetooth: radio on/off, per-device connect/disconnect, bonding.
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        f.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        try {
            // All of the above are protected system broadcasts; register non-exported.
            mContext.registerReceiver(mReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } catch (Throwable t) {
            Slog.w(TAG, "registerReceiver failed", t);
        }

        // Initial publish + heartbeat recompute (catches broadcasts we might miss).
        mHandler.post(this::publish);
        mHandler.postDelayed(mHeartbeat, HEARTBEAT_MS);
        Slog.i(TAG, "GammaOS Nano: net bridge started");
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            // Coalesce a burst of broadcasts (e.g. RSSI storms) into one recompute.
            mHandler.removeCallbacks(mPublish);
            mHandler.postDelayed(mPublish, 150);
        }
    };

    private final Runnable mPublish = this::publish;

    private final Runnable mHeartbeat = new Runnable() {
        @Override
        public void run() {
            publish();
            mHandler.postDelayed(this, HEARTBEAT_MS);
        }
    };

    private void publish() {
        boolean wifiOn = false, wifiConn = false;
        int wifiBars = 0;
        String ssid = "";
        try {
            if (mWifi != null) {
                wifiOn = mWifi.isWifiEnabled();
                if (wifiOn) {
                    WifiInfo wi = mWifi.getConnectionInfo();
                    if (wi != null && wi.getNetworkId() != -1) {
                        String s = wi.getSSID();
                        if (s != null && !s.isEmpty()
                                && !s.equals(WifiManager.UNKNOWN_SSID)
                                && !s.equals("<unknown ssid>")) {
                            if (s.length() >= 2 && s.charAt(0) == '"'
                                    && s.charAt(s.length() - 1) == '"') {
                                s = s.substring(1, s.length() - 1);
                            }
                            s = s.replace('\n', ' ').replace('\r', ' ');
                            if (!s.isEmpty()) {
                                wifiConn = true;
                                ssid = s;
                                wifiBars = barsFromRssi(wi.getRssi());
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // leave wifi as off/unknown
        }

        boolean btOn = false;
        int btCount = 0;
        try {
            if (mBt != null) {
                btOn = mBt.getState() == BluetoothAdapter.STATE_ON;
                if (btOn && mIsConnected != null) {
                    Set<BluetoothDevice> bonded = mBt.getBondedDevices();
                    if (bonded != null) {
                        // Count DISTINCT connected devices (fixes the old per-profile
                        // double-count in the text-parse path).
                        for (BluetoothDevice d : bonded) {
                            if (d == null) continue;
                            try {
                                Object r = mIsConnected.invoke(d);
                                if (r instanceof Boolean && (Boolean) r) btCount++;
                            } catch (Throwable ignore) {
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // leave bt as off/unknown
        }

        StringBuilder sb = new StringBuilder(96);
        sb.append("wifi_on=").append(wifiOn ? 1 : 0).append('\n');
        sb.append("wifi_conn=").append(wifiConn ? 1 : 0).append('\n');
        sb.append("wifi_bars=").append(wifiBars).append('\n');
        sb.append("wifi_ssid=").append(ssid).append('\n');
        sb.append("bt_on=").append(btOn ? 1 : 0).append('\n');
        sb.append("bt_count=").append(btCount).append('\n');
        String payload = sb.toString();

        if (payload.equals(mLastPayload)) {
            return; // debounce: nothing changed, no file write, no gen bump
        }
        mLastPayload = payload;

        if (!writeAtomic(STATE_FILE, payload)) {
            return; // keep the generation stable if the write failed
        }
        mGen++;
        SystemProperties.set(GEN_PROP, Integer.toString(mGen));
    }

    /** rssi -> 0..4 bars, matching nano's rssiToBars thresholds exactly. */
    private static int barsFromRssi(int rssi) {
        if (rssi >= -55) return 4;
        if (rssi >= -66) return 3;
        if (rssi >= -77) return 2;
        if (rssi >= -88) return 1;
        return 0;
    }

    /** Write temp+rename so nano never reads a half-written file; world-readable. */
    private static boolean writeAtomic(String path, String data) {
        File dst = new File(path);
        File tmp = new File(path + ".tmp");
        try {
            FileWriter fw = new FileWriter(tmp);
            fw.write(data);
            fw.close();
            tmp.setReadable(true, false);
            return tmp.renameTo(dst);
        } catch (Throwable t) {
            Slog.w(TAG, "writeAtomic failed for " + path, t);
            try { tmp.delete(); } catch (Throwable ignore) {}
            return false;
        }
    }
}
