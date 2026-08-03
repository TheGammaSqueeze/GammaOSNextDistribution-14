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
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemProperties;
import android.util.Slog;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NanoNetBridge {
    private static final String TAG = "NanoNetBridge";
    private static final String STATE_FILE = "/data/system/nano_net_state.txt";
    private static final String GEN_PROP = "sys.gammaos.nano.net_generation";
    private static final String WIFI_FILE = "/data/system/nano_wifi_list.txt";
    private static final String BT_FILE = "/data/system/nano_bt_list.txt";
    private static final String WIFI_GEN_PROP = "sys.gammaos.nano.wifi_generation";
    private static final String BT_GEN_PROP = "sys.gammaos.nano.bt_generation";
    private static final String CMD_PROP = "sys.gammaos.nano.net_cmd";
    private static final long HEARTBEAT_MS = 8000;
    private static final long CMD_POLL_MS = 200;

    // Keep a static reference so the receiver-owning instance is never GC'd.
    private static NanoNetBridge sInstance;

    private final Context mContext;
    private WifiManager mWifi;
    private BluetoothAdapter mBt;
    private Handler mHandler;
    private int mGen = 0;
    private int mWifiGen = 0;
    private int mBtGen = 0;
    private String mLastPayload = "";
    private String mLastWifi = "";
    private String mLastBt = "";
    private Method mIsConnected;   // BluetoothDevice.isConnected() (@hide) via reflection
    private long mLastCmdSeq = -1; // last executed nano command sequence (dedup)
    // Devices seen during an active discovery: MAC -> {name, rssi, cod}. Published as the
    // non-bonded ("bonded=0") rows of nano_bt_list.txt so nano's scan UI can list them.
    private final LinkedHashMap<String, String[]> mDiscovered = new LinkedHashMap<>();

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
        // Wi-Fi: radio on/off, association changes, live RSSI, scan results, saved-config edits.
        f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.RSSI_CHANGED_ACTION);
        f.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        f.addAction(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION);
        // Bluetooth: radio on/off, per-device connect/disconnect, bonding.
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        f.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        // Bluetooth discovery (inquiry) for the "scan for devices" UI.
        f.addAction(BluetoothDevice.ACTION_FOUND);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        try {
            // These are all protected system broadcasts (no untrusted app can forge them),
            // but some - notably BluetoothDevice.ACTION_FOUND / ACL / bond - are sent by the
            // com.android.bluetooth process (a different uid than system_server), so the
            // receiver must be EXPORTED to receive them. NOT_EXPORTED silently drops those
            // cross-process broadcasts (only same-process WifiService/BluetoothManagerService
            // broadcasts would arrive), which is why device discovery results never appeared.
            mContext.registerReceiver(mReceiver, f, Context.RECEIVER_EXPORTED);
        } catch (Throwable t) {
            Slog.w(TAG, "registerReceiver failed", t);
        }

        // Do not replay a command left in the prop from a previous session.
        mLastCmdSeq = parseSeq(SystemProperties.get(CMD_PROP, ""));

        // Initial publish + heartbeat recompute (catches broadcasts we might miss) +
        // command poll (nano -> bridge control channel; addChangeCallback is unreliable
        // on this build so we poll).
        mHandler.post(this::publishAll);
        mHandler.postDelayed(mHeartbeat, HEARTBEAT_MS);
        mHandler.postDelayed(mCmdPoll, CMD_POLL_MS);
        Slog.i(TAG, "GammaOS Nano: net bridge started");
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            final String action = i.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                try {
                    BluetoothDevice d = i.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                    if (d != null && d.getAddress() != null) {
                        short rssi = i.getShortExtra(BluetoothDevice.EXTRA_RSSI, (short) -127);
                        String name = null;
                        try { name = d.getName(); } catch (Throwable t) {}
                        int cod = 0;
                        try {
                            BluetoothClass bc = d.getBluetoothClass();
                            if (bc != null) cod = bc.getDeviceClass();
                        } catch (Throwable t) {}
                        synchronized (mDiscovered) {
                            mDiscovered.put(d.getAddress(), new String[] {
                                    name == null ? "" : name,
                                    Integer.toString(rssi), Integer.toString(cod) });
                        }
                    }
                } catch (Throwable t) {}
            }
            // Coalesce a burst of broadcasts (e.g. RSSI storms / ACTION_FOUND bursts)
            // into one recompute+publish.
            mHandler.removeCallbacks(mPublish);
            mHandler.postDelayed(mPublish, 150);
        }
    };

    // nano -> bridge command channel. nano sets CMD_PROP = "<seq>|<verb>|<arg>"; we poll,
    // dedup by seq, and drive the live WifiManager/BluetoothAdapter. Results flow back
    // through the normal published state/list files.
    private final Runnable mCmdPoll = new Runnable() {
        @Override
        public void run() {
            try {
                String v = SystemProperties.get(CMD_PROP, "");
                long seq = parseSeq(v);
                if (seq != -1 && seq != mLastCmdSeq) {
                    mLastCmdSeq = seq;
                    int p1 = v.indexOf('|');
                    String rest = (p1 >= 0) ? v.substring(p1 + 1) : "";
                    int p2 = rest.indexOf('|');
                    String verb = (p2 >= 0) ? rest.substring(0, p2) : rest;
                    String arg = (p2 >= 0) ? rest.substring(p2 + 1) : "";
                    executeCommand(verb.trim(), arg.trim());
                }
            } catch (Throwable t) {
                Slog.w(TAG, "cmd poll", t);
            }
            mHandler.postDelayed(this, CMD_POLL_MS);
        }
    };

    private static long parseSeq(String v) {
        if (v == null || v.isEmpty()) return -1;
        int p = v.indexOf('|');
        String s = (p >= 0) ? v.substring(0, p) : v;
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return -1; }
    }

    private void executeCommand(String verb, String arg) {
        Slog.i(TAG, "cmd: " + verb + " " + arg);
        try {
            switch (verb) {
                case "wifi_scan":
                    if (mWifi != null) mWifi.startScan();
                    break;
                case "wifi_enable":
                    if (mWifi != null) mWifi.setWifiEnabled(true);
                    break;
                case "wifi_disable":
                    if (mWifi != null) mWifi.setWifiEnabled(false);
                    break;
                case "wifi_forget": {
                    // removeNetwork()+saveConfiguration() are @Deprecated no-ops on the
                    // modern Wi-Fi stack (removeNetwork returns false, saveConfiguration
                    // does nothing), so the old pair silently failed to forget. forget()
                    // is the working replacement (the same call cmd wifi forget-network
                    // makes via mWifiService.forget). The nano UI now always uses the
                    // shell path; this keeps the bridge correct for any direct caller.
                    int id = parseInt(arg, -1);
                    if (mWifi != null && id >= 0) {
                        try { mWifi.forget(id, null); }
                        catch (Throwable t) { Slog.w(TAG, "wifi forget", t); }
                    }
                    break;
                }
                case "wifi_connect_saved": {
                    int id = parseInt(arg, -1);
                    if (mWifi != null && id >= 0) {
                        try { mWifi.disconnect(); } catch (Throwable ignore) {}
                        mWifi.enableNetwork(id, true /* disableOthers */);
                        try { mWifi.reconnect(); } catch (Throwable ignore) {}
                    }
                    break;
                }
                case "bt_enable":
                    if (mBt != null) mBt.enable();
                    break;
                case "bt_disable":
                    if (mBt != null) mBt.disable();
                    break;
                case "bt_scan_start":
                    startBtDiscovery();
                    break;
                case "bt_scan_stop":
                    stopBtDiscovery();
                    break;
                case "bt_pair":
                    btPair(arg);
                    break;
                case "bt_unpair":
                    btDeviceMethod(arg, "removeBond");
                    break;
                case "bt_connect":
                    btDeviceMethod(arg, "connect");
                    break;
                case "bt_disconnect":
                    btDeviceMethod(arg, "disconnect");
                    break;
                case "bt_confirm": {
                    String[] parts = arg.split("\\s+");
                    if (parts.length >= 2) btConfirm(parts[0], "accept".equals(parts[1]));
                    break;
                }
                default:
                    Slog.w(TAG, "unknown cmd verb: " + verb);
            }
        } catch (Throwable t) {
            Slog.w(TAG, "cmd " + verb + " failed", t);
        }
        // Reflect the result (list/state change) promptly.
        mHandler.removeCallbacks(mPublish);
        mHandler.postDelayed(mPublish, 300);
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private BluetoothDevice deviceFor(String mac) {
        if (mBt == null || mac == null || mac.isEmpty()) return null;
        try { return mBt.getRemoteDevice(mac); } catch (Throwable t) { return null; }
    }

    private void startBtDiscovery() {
        if (mBt == null) return;
        synchronized (mDiscovered) { mDiscovered.clear(); }
        try { if (!mBt.isEnabled()) mBt.enable(); } catch (Throwable ignore) {}
        try { if (mBt.isDiscovering()) mBt.cancelDiscovery(); } catch (Throwable ignore) {}
        try { mBt.startDiscovery(); } catch (Throwable t) { Slog.w(TAG, "startDiscovery", t); }
    }

    private void stopBtDiscovery() {
        if (mBt == null) return;
        try { if (mBt.isDiscovering()) mBt.cancelDiscovery(); } catch (Throwable ignore) {}
    }

    private void btPair(String mac) {
        BluetoothDevice d = deviceFor(mac);
        if (d == null) return;
        try { if (mBt.isDiscovering()) mBt.cancelDiscovery(); } catch (Throwable ignore) {}
        // An already-registered device cannot be re-bonded - AdapterService.createBond()
        // refuses anything that is not BOND_NONE, so createBond() here would be a silent
        // no-op. Kick a profile connect instead so a bonded-but-idle device links up.
        try {
            if (d.getBondState() == BluetoothDevice.BOND_BONDED) {
                BluetoothDevice.class.getMethod("connect").invoke(d);
                return;
            }
        } catch (Throwable ignore) {}
        try { d.createBond(); } catch (Throwable t) { Slog.w(TAG, "createBond", t); }
    }

    /** Invoke a no-arg @hide BluetoothDevice method (removeBond/connect/disconnect) by reflection. */
    private void btDeviceMethod(String mac, String method) {
        BluetoothDevice d = deviceFor(mac);
        if (d == null) return;
        try {
            Method m = BluetoothDevice.class.getMethod(method);
            m.invoke(d);
        } catch (Throwable t) {
            Slog.w(TAG, "bt " + method + " failed", t);
        }
    }

    private void btConfirm(String mac, boolean accept) {
        BluetoothDevice d = deviceFor(mac);
        if (d == null) return;
        try {
            Method m = BluetoothDevice.class.getMethod("setPairingConfirmation", boolean.class);
            m.invoke(d, accept);
        } catch (Throwable t) {
            Slog.w(TAG, "setPairingConfirmation", t);
        }
    }

    private final Runnable mPublish = this::publishAll;

    private final Runnable mHeartbeat = new Runnable() {
        @Override
        public void run() {
            publishAll();
            mHandler.postDelayed(this, HEARTBEAT_MS);
        }
    };

    private void publishAll() {
        publish();          // compact HUD state
        publishWifiList();  // merged saved+scan list for the Wi-Fi settings screen
        publishBtList();    // bonded devices + radio state for the BT settings screen
    }

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

    /** One merged Wi-Fi network row for the settings screen (matches nano's WifiNetEntry). */
    private static final class WEntry {
        String ssid = "";
        String bssid = "";
        int rssi = -127;
        int security = 0;   // 0 none, 1 wep, 2 wpa/wpa2, 3 wpa3, 4 owe
        int netId = -1;     // saved network id, -1 if not saved
        boolean connected = false;
    }

    /**
     * Publish the merged saved+scan Wi-Fi list (one "ssid|bssid|rssi|security|savedNetId|
     * connected" line each, plus a "#radio=" header) so nano's Wi-Fi settings screen reads
     * a file instead of parsing `cmd wifi list-networks` + `cmd wifi list-scan-results`.
     */
    private void publishWifiList() {
        StringBuilder sb = new StringBuilder(256);
        boolean radio = false;
        try { radio = mWifi != null && mWifi.isWifiEnabled(); } catch (Throwable ignore) {}
        sb.append("#radio=").append(radio ? 1 : 0).append('\n');
        if (radio && mWifi != null) {
            LinkedHashMap<String, WEntry> map = new LinkedHashMap<>();
            String curSsid = "";
            try {
                WifiInfo wi = mWifi.getConnectionInfo();
                if (wi != null && wi.getNetworkId() != -1) curSsid = stripQuotes(wi.getSSID());
            } catch (Throwable ignore) {}
            try {
                List<ScanResult> scans = mWifi.getScanResults();
                if (scans != null) {
                    for (ScanResult sr : scans) {
                        if (sr == null || sr.SSID == null || sr.SSID.isEmpty()) continue;
                        String ssid = sanitize(sr.SSID);
                        WEntry e = map.get(ssid);
                        if (e == null) { e = new WEntry(); e.ssid = ssid; map.put(ssid, e); }
                        e.bssid = sr.BSSID != null ? sr.BSSID : "";
                        e.rssi = sr.level;
                        e.security = securityFromCaps(sr.capabilities);
                    }
                }
            } catch (Throwable ignore) {}
            try {
                List<WifiConfiguration> cfgs = mWifi.getConfiguredNetworks();
                if (cfgs != null) {
                    for (WifiConfiguration wc : cfgs) {
                        if (wc == null || wc.SSID == null) continue;
                        String ssid = sanitize(stripQuotes(wc.SSID));
                        if (ssid.isEmpty()) continue;
                        WEntry e = map.get(ssid);
                        if (e == null) {
                            e = new WEntry();
                            e.ssid = ssid;
                            e.security = securityFromConfig(wc);
                            map.put(ssid, e);
                        }
                        e.netId = wc.networkId;
                    }
                }
            } catch (Throwable ignore) {}
            ArrayList<WEntry> list = new ArrayList<>(map.values());
            for (WEntry e : list) {
                e.connected = !curSsid.isEmpty() && e.ssid.equals(curSsid);
            }
            // Connected first, then by signal strength descending.
            Collections.sort(list, (a, b) -> {
                if (a.connected != b.connected) return a.connected ? -1 : 1;
                return Integer.compare(b.rssi, a.rssi);
            });
            for (WEntry e : list) {
                sb.append(e.ssid).append('|').append(e.bssid).append('|')
                  .append(e.rssi).append('|').append(e.security).append('|')
                  .append(e.netId).append('|').append(e.connected ? 1 : 0).append('\n');
            }
        }
        String payload = sb.toString();
        if (payload.equals(mLastWifi)) return;
        mLastWifi = payload;
        if (writeAtomic(WIFI_FILE, payload)) {
            mWifiGen++;
            SystemProperties.set(WIFI_GEN_PROP, Integer.toString(mWifiGen));
        }
    }

    /**
     * Publish the bonded Bluetooth devices (one "name|address|bonded|connected|cod" line
     * each, plus a real "#radio=" header) so nano's BT settings screen reads a file instead
     * of forking `gammaos-net bt list-bonded` and mis-reading the stale bluetooth_on setting.
     */
    private void publishBtList() {
        StringBuilder sb = new StringBuilder(256);
        boolean on = false;
        try { on = mBt != null && mBt.getState() == BluetoothAdapter.STATE_ON; } catch (Throwable ignore) {}
        sb.append("#radio=").append(on ? 1 : 0).append('\n');
        HashSet<String> bondedAddrs = new HashSet<>();
        if (on && mBt != null) {
            try {
                Set<BluetoothDevice> bonded = mBt.getBondedDevices();
                if (bonded != null) {
                    for (BluetoothDevice d : bonded) {
                        if (d == null) continue;
                        String addr = d.getAddress();
                        if (addr == null || addr.isEmpty()) continue;
                        bondedAddrs.add(addr);
                        String name;
                        try { name = d.getName(); } catch (Throwable t) { name = null; }
                        if (name == null || name.isEmpty()) name = addr;
                        name = sanitize(name);
                        boolean conn = false;
                        if (mIsConnected != null) {
                            try {
                                Object r = mIsConnected.invoke(d);
                                conn = (r instanceof Boolean) && (Boolean) r;
                            } catch (Throwable ignore) {}
                        }
                        int cod = 0;
                        try {
                            BluetoothClass bc = d.getBluetoothClass();
                            if (bc != null) cod = bc.getDeviceClass();
                        } catch (Throwable ignore) {}
                        sb.append(name).append('|').append(addr).append('|')
                          .append(1).append('|').append(conn ? 1 : 0).append('|')
                          .append(cod).append('\n');
                    }
                }
            } catch (Throwable ignore) {}
            // Discovered (non-bonded) devices from the current/last scan, so nano's scan UI
            // can list new devices to pair with. name|address|bonded=0|connected=0|cod
            synchronized (mDiscovered) {
                for (Map.Entry<String, String[]> e : mDiscovered.entrySet()) {
                    String addr = e.getKey();
                    if (addr == null || addr.isEmpty() || bondedAddrs.contains(addr)) continue;
                    String[] v = e.getValue();
                    String name = (v != null && v.length > 0 && !v[0].isEmpty())
                            ? sanitize(v[0]) : addr;
                    String cod = (v != null && v.length > 2) ? v[2] : "0";
                    sb.append(name).append('|').append(addr).append('|')
                      .append(0).append('|').append(0).append('|').append(cod).append('\n');
                }
            }
        }
        String payload = sb.toString();
        if (payload.equals(mLastBt)) return;
        mLastBt = payload;
        if (writeAtomic(BT_FILE, payload)) {
            mBtGen++;
            SystemProperties.set(BT_GEN_PROP, Integer.toString(mBtGen));
        }
    }

    private static String stripQuotes(String s) {
        if (s == null) return "";
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /** Strip the field delimiter and newlines so a stray SSID/name can't corrupt a line. */
    private static String sanitize(String s) {
        if (s == null) return "";
        return s.replace('|', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    /** Map a ScanResult.capabilities string to nano's security enum. */
    private static int securityFromCaps(String caps) {
        if (caps == null) return 0;
        if (caps.contains("SAE")) return 3;         // WPA3-Personal
        if (caps.contains("OWE")) return 4;         // Enhanced Open
        if (caps.contains("PSK") || caps.contains("EAP") || caps.contains("WPA")) return 2;
        if (caps.contains("WEP")) return 1;
        return 0;                                   // open
    }

    /** Map a saved WifiConfiguration to nano's security enum (for out-of-range saved APs). */
    private static int securityFromConfig(WifiConfiguration wc) {
        try {
            BitSet km = wc.allowedKeyManagement;
            if (km != null) {
                if (km.get(WifiConfiguration.KeyMgmt.SAE)) return 3;
                if (km.get(WifiConfiguration.KeyMgmt.OWE)) return 4;
                if (km.get(WifiConfiguration.KeyMgmt.WPA_PSK)
                        || km.get(WifiConfiguration.KeyMgmt.WPA_EAP)
                        || km.get(WifiConfiguration.KeyMgmt.IEEE8021X)) return 2;
            }
            if (wc.wepKeys != null && wc.wepKeys.length > 0 && wc.wepKeys[0] != null) return 1;
        } catch (Throwable ignore) {}
        return 0;
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
