/*
 * BtSubcommand - `gammaos-net bt <action>`.
 *
 * Uses BluetoothAdapter / BluetoothDevice directly, same as Settings'
 * Bluetooth pickers. AOSP 14's `cmd bluetooth_manager` only exposes
 * enable/disable, so scanning, pairing, and unpairing have to go
 * through the adapter APIs from a platform-privileged process.
 */
package com.gammaos.net;

import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class BtSubcommand {

    public static int run(Context ctx, String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("gammaos-net bt: missing action");
            return 2;
        }
        BluetoothManager bm = ctx.getSystemService(BluetoothManager.class);
        if (bm == null) {
            System.err.println("gammaos-net bt: no BluetoothManager");
            return 3;
        }
        BluetoothAdapter adapter = bm.getAdapter();
        if (adapter == null) {
            System.err.println("gammaos-net bt: no BluetoothAdapter");
            return 3;
        }
        if (!adapter.isEnabled()) {
            adapter.enable();
            // Give the stack a moment to come up - discovery / pairing
            // fail silently if we call them while the adapter is still
            // turning on.
            for (int i = 0; i < 40 && !adapter.isEnabled(); i++) {
                Thread.sleep(250);
            }
        }
        switch (args[1]) {
            case "scan":
                return scan(ctx, adapter, args);
            case "pair":
                return pair(ctx, adapter, args);
            case "unpair":
                return unpair(adapter, args);
            case "connect":
                return connect(adapter, args);
            case "disconnect":
                return disconnect(adapter, args);
            case "discoverable":
                return discoverable(adapter, args);
            case "confirm":
                return confirm(adapter, args);
            case "list-bonded":
                return listBonded(adapter);
            default:
                System.err.println("gammaos-net bt: unknown action " + args[1]);
                return 2;
        }
    }

    // ---------------------------------------------------------------------
    // scan
    // ---------------------------------------------------------------------

    private static int scan(Context ctx, BluetoothAdapter adapter,
                            String[] args) throws Exception {
        int seconds = 8;
        if (args.length >= 3) {
            try {
                seconds = Math.max(1, Math.min(30, Integer.parseInt(args[2])));
            } catch (NumberFormatException ignored) { }
        }
        if (adapter.isDiscovering()) {
            adapter.cancelDiscovery();
        }
        // Keep insertion order so callers see devices in the order they
        // were first seen; update RSSI if the same device is reported
        // multiple times so the final list has the freshest value.
        final Map<String, String[]> found = new LinkedHashMap<>();

        // Two parallel discovery paths, results merged into 'found':
        //
        // 1. adapter.startDiscovery() + ACTION_FOUND broadcast - classic
        //    BR/EDR inquiry. The broadcast fires from AdapterService so
        //    it has to flow through ActivityManager; gammaos-net runs
        //    via app_process + ActivityThread.systemMain() which does
        //    NOT call IActivityManager.attachApplication(), so our
        //    process has no ProcessRecord. The only way any broadcast
        //    still reaches us is to give registerReceiver an explicit
        //    Handler that runs on a Looper we control - the default
        //    dispatch path ties itself to Looper.getMainLooper() which
        //    is prepared-but-never-looped (and can't be safely looped,
        //    prepareMainLooper() sets quitAllowed=false).
        //
        // 2. BluetoothLeScanner.startScan(ScanCallback) - catches pure
        //    BLE peripherals that advertise in pairing mode. Best-effort
        //    because BluetoothLeScanner's internal dispatch Handler is
        //    hard-wired to Looper.getMainLooper() in its constructor
        //    (we cannot pass our own Handler through the public API).
        //    We replace that field reflectively with a Handler on our
        //    HandlerThread; if the reflection fails, we skip BLE rather
        //    than silently dropping results on the un-looped main.
        //
        // On some MediaTek stacks BLE scan_filt_param_cfg fails (see
        // bt_btm_ble: btm_ble_scan_filt_param_cfg_evt: 4 in logcat),
        // which short-circuits BLE advertisement delivery. Classic
        // inquiry still works there, so dual-path is still worthwhile.

        HandlerThread ht = new HandlerThread("gammaos-net-bt-scan");
        ht.start();
        Handler callbackHandler = new Handler(ht.getLooper());

        BluetoothLeScanner leScanner = adapter.getBluetoothLeScanner();
        ScanCallback leCb = null;
        if (leScanner != null) {
            boolean handlerSwapped = false;
            try {
                java.lang.reflect.Field f1 =
                        BluetoothLeScanner.class.getDeclaredField("mHandler");
                f1.setAccessible(true);
                f1.set(leScanner, callbackHandler);
                handlerSwapped = true;
            } catch (Throwable t) {
                System.err.println("gammaos-net bt: could not retarget"
                        + " BluetoothLeScanner handler: " + t);
            }
            if (handlerSwapped) {
                leCb = new ScanCallback() {
                    @Override public void onScanResult(int callbackType,
                                                       ScanResult r) {
                        recordLeResult(found, r);
                    }
                    @Override public void onBatchScanResults(
                            List<ScanResult> rs) {
                        if (rs == null) return;
                        for (ScanResult r : rs) recordLeResult(found, r);
                    }
                    @Override public void onScanFailed(int errorCode) {
                        System.err.println(
                                "gammaos-net bt: BLE scan failed errorCode="
                                        + errorCode);
                    }
                };
                ScanSettings settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setCallbackType(
                                ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                        .build();
                try {
                    // Empty filter list = match everything. We avoid
                    // filters=null because some AOSP builds treat null
                    // as opportunistic scan (only fires when someone
                    // else is also scanning).
                    leScanner.startScan(new ArrayList<ScanFilter>(),
                                        settings, leCb);
                } catch (Throwable t) {
                    System.err.println(
                            "gammaos-net bt: BLE startScan failed: " + t);
                    leCb = null;
                }
            }
        }

        // Register for ACTION_FOUND by calling AMS.registerReceiverWithFeature
        // directly with caller=null. Context.registerReceiver always passes
        // ActivityThread.getApplicationThread() as caller, which AMS can't
        // resolve to a ProcessRecord for us (we were launched by init, not
        // by Process.start()), so the normal path returns null without
        // actually registering. A matching GammaOS patch in AMS lets
        // caller=null through when Binder.getCallingUid()==0. We also
        // have to do our own dispatch: the binder callback lands on the
        // process-global binder thread pool, so we hop onto our
        // HandlerThread before touching state.
        IIntentReceiver rd = new IIntentReceiver.Stub() {
            @Override
            public void performReceive(Intent intent, int resultCode,
                                       String data, Bundle extras,
                                       boolean ordered, boolean sticky,
                                       int sendingUser) {
                callbackHandler.post(() -> handleScanIntent(found, intent));
            }
        };
        IntentFilter f = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        // ACTION_NAME_CHANGED is fired when the native stack resolves a
        // remote name via a Remote Name Request (issued automatically
        // after an inquiry for devices that didn't include the name in
        // their EIR). Without it, classic peripherals that only include
        // their name in the response phase show up with blank names.
        f.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
        // RemoteDevices.deviceFoundCallback() sets intent.setPackage()
        // to the package name that called startDiscovery() (from the
        // AttributionSource). Dynamic-receiver matching in AMS only
        // dispatches a package-targeted broadcast to receivers whose
        // BroadcastFilter.packageName equals that package. Our
        // startDiscovery() call goes through BluetoothAdapter's
        // mAttributionSource, which comes from the system Context --
        // so we must register the receiver under the same package
        // name or the broadcasts silently bypass us.
        String pkg = ctx.getOpPackageName();
        boolean receiverRegistered = false;
        try {
            ActivityManager.getService().registerReceiverWithFeature(
                    null, pkg, null, null, rd, f, null,
                    UserHandle.USER_ALL, Context.RECEIVER_EXPORTED);
            receiverRegistered = true;
        } catch (Throwable t) {
            System.err.println("gammaos-net bt: direct AMS registerReceiver"
                    + " failed: " + t);
        }

        try {
            if (!adapter.startDiscovery()) {
                System.err.println("gammaos-net bt: startDiscovery() returned"
                        + " false - continuing with BLE scan only");
            }
            // Both dispatch paths now run on callbackHandler's looper
            // (HandlerThread), so the main thread can just block. We
            // can't Looper.loop() the main looper - prepareMainLooper()
            // sets quitAllowed=false and postDelayed(quitSafely) throws
            // IllegalStateException("Main thread not allowed to quit.").
            // We don't stop early on ACTION_DISCOVERY_FINISHED because
            // BLE advertisement intervals can be 1-2 s and we want to
            // hear at least one advert per device in range.
            Thread.sleep(seconds * 1000L);
            try { adapter.cancelDiscovery(); } catch (Throwable ignored) { }
            if (leScanner != null && leCb != null) {
                try { leScanner.stopScan(leCb); } catch (Throwable ignored) { }
                leCb = null;
            }
            // Name-resolution window. After cancelling discovery the BT
            // stack runs Remote Name Requests for devices that didn't
            // include their name in the EIR, and each resolved name
            // comes in as a separate ACTION_NAME_CHANGED broadcast.
            // Give it several seconds to land before we print the list,
            // and for any entry still nameless kick an asynchronous
            // refresh that will update the cache for a future scan. A
            // longer window resolves more classic-device friendly names
            // (Remote Name Request + SDP can be slow on a busy radio).
            resolveNames(adapter, found, 6000);
        } finally {
            try { adapter.cancelDiscovery(); } catch (Throwable ignored) { }
            if (leScanner != null && leCb != null) {
                try { leScanner.stopScan(leCb); } catch (Throwable ignored) { }
            }
            if (receiverRegistered) {
                try {
                    ActivityManager.getService().unregisterReceiver(rd);
                } catch (Throwable ignored) { }
            }
            try { ht.quitSafely(); } catch (Throwable ignored) { }
        }
        StringBuilder sb = new StringBuilder();
        synchronized (found) {
            for (Map.Entry<String, String[]> e : found.entrySet()) {
                String[] v = e.getValue();
                sb.append(e.getKey()).append('\t')
                  .append(sanitize(v[0])).append('\t')
                  .append(v[1]).append('\t')
                  .append(v[2]).append('\t')
                  .append(v[3]).append('\n');
            }
        }
        System.out.print(sb.toString());
        return 0;
    }

    private static void handleScanIntent(Map<String, String[]> found,
                                         Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        BluetoothDevice dev = intent.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE);
        if (dev == null) return;
        String addr = dev.getAddress();
        if (addr == null) return;
        String name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME);
        if (name == null || name.isEmpty()) {
            try { name = dev.getName(); } catch (SecurityException ignored) { }
        }
        if (name == null) name = "";

        if (BluetoothDevice.ACTION_NAME_CHANGED.equals(action)) {
            // Name-only update: refresh the entry's name but keep the
            // cached RSSI / COD / bond state from the original sighting.
            // If the device isn't in 'found' yet (e.g. name came in
            // between advert bursts), the entry will be created with
            // sentinel rssi/cod and corrected on the next ACTION_FOUND.
            if (!name.isEmpty()) {
                updateName(found, addr, name);
            }
            return;
        }

        if (!BluetoothDevice.ACTION_FOUND.equals(action)) return;
        short rssi = intent.getShortExtra(
                BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
        BluetoothClass cls = intent.getParcelableExtra(
                BluetoothDevice.EXTRA_CLASS);
        int cod = cls != null ? cls.getDeviceClass() : 0;
        int bondState;
        try {
            bondState = dev.getBondState();
        } catch (SecurityException e) {
            bondState = BluetoothDevice.BOND_NONE;
        }
        recordResult(found, addr, name, (int) rssi, cod, bondState);
    }

    private static void updateName(Map<String, String[]> found,
                                   String addr, String name) {
        synchronized (found) {
            String[] prev = found.get(addr);
            if (prev == null) {
                // Synthesize a row so late-arriving names still show up
                // if the original advert was missed (rare but possible
                // when the LE scanner dropped an event). RSSI/COD get
                // sentinel values until a real sighting fills them in.
                found.put(addr, new String[] { name,
                        Integer.toString(Short.MIN_VALUE), "0",
                        Integer.toString(BluetoothDevice.BOND_NONE) });
            } else {
                prev[0] = name;
            }
        }
    }

    private static void resolveNames(BluetoothAdapter adapter,
                                     Map<String, String[]> found,
                                     int windowMs) {
        // Step 1: for any entry missing a name, ask the stack for the
        // cached name synchronously. If the native inquiry-done callback
        // already wrote the name into RemoteDevices (via a Remote Name
        // Request completed between ACTION_FOUND and now), this returns
        // it immediately without waiting for an ACTION_NAME_CHANGED.
        List<String> stillMissing = new ArrayList<>();
        synchronized (found) {
            for (Map.Entry<String, String[]> e : found.entrySet()) {
                if (!e.getValue()[0].isEmpty()) continue;
                String cached = null;
                try {
                    cached = adapter.getRemoteDevice(e.getKey()).getName();
                } catch (Throwable ignored) { }
                if (cached != null && !cached.isEmpty()) {
                    e.getValue()[0] = cached;
                } else {
                    stillMissing.add(e.getKey());
                }
            }
        }

        // Step 2: trigger a best-effort SDP fetch on any entry that is
        // still nameless. fetchUuidsWithSdp() on AOSP classic devices
        // also refreshes the friendly name and fires ACTION_NAME_CHANGED
        // when it completes; on BLE-only peripherals it's a no-op.
        for (String addr : stillMissing) {
            try {
                BluetoothDevice d = adapter.getRemoteDevice(addr);
                d.fetchUuidsWithSdp();
            } catch (Throwable ignored) { }
        }

        // Step 3: wait the full window so ACTION_NAME_CHANGED broadcasts
        // from auto Remote Name Requests (queued by the stack after
        // inquiry) and our own fetchUuidsWithSdp() can reach the
        // receiver. Poll the cached name periodically as a fallback in
        // case the name update path doesn't broadcast.
        long deadline = System.currentTimeMillis() + windowMs;
        while (System.currentTimeMillis() < deadline) {
            boolean anyMissing = false;
            synchronized (found) {
                for (String[] v : found.values()) {
                    if (v[0].isEmpty()) { anyMissing = true; break; }
                }
            }
            if (!anyMissing) break;
            try { Thread.sleep(250); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
            // Re-poll getName() on each iteration in case a name landed
            // in the cache but the broadcast was missed.
            synchronized (found) {
                for (Map.Entry<String, String[]> e : found.entrySet()) {
                    if (!e.getValue()[0].isEmpty()) continue;
                    try {
                        String n = adapter.getRemoteDevice(e.getKey()).getName();
                        if (n != null && !n.isEmpty()) {
                            e.getValue()[0] = n;
                        }
                    } catch (Throwable ignored) { }
                }
            }
        }
    }

    private static void recordLeResult(Map<String, String[]> found,
                                       ScanResult r) {
        if (r == null) return;
        BluetoothDevice dev = r.getDevice();
        if (dev == null) return;
        String name = null;
        if (r.getScanRecord() != null) {
            name = r.getScanRecord().getDeviceName();
        }
        if (name == null || name.isEmpty()) {
            try { name = dev.getName(); } catch (SecurityException ignored) { }
        }
        if (name == null) name = "";
        int cod = 0;
        try {
            BluetoothClass cls = dev.getBluetoothClass();
            if (cls != null) cod = cls.getDeviceClass();
        } catch (SecurityException ignored) { }
        int bondState;
        try {
            bondState = dev.getBondState();
        } catch (SecurityException e) {
            bondState = BluetoothDevice.BOND_NONE;
        }
        recordResult(found, dev.getAddress(), name, r.getRssi(), cod,
                     bondState);
    }

    private static void recordResult(Map<String, String[]> found,
                                     String addr, String name, int rssi,
                                     int cod, int bondState) {
        if (addr == null) return;
        synchronized (found) {
            String[] prev = found.get(addr);
            // Prefer whichever path reports a non-empty name; otherwise
            // keep the freshest RSSI.
            String keepName = name != null ? name : "";
            if (prev != null && (keepName.isEmpty())
                    && !prev[0].isEmpty()) {
                keepName = prev[0];
            }
            int keepCod = cod;
            if (prev != null && keepCod == 0) {
                try { keepCod = Integer.parseInt(prev[2]); }
                catch (NumberFormatException ignored) { }
            }
            found.put(addr, new String[] {
                    keepName,
                    Integer.toString(rssi),
                    Integer.toString(keepCod),
                    Integer.toString(bondState),
            });
        }
    }

    // ---------------------------------------------------------------------
    // pair / unpair
    // ---------------------------------------------------------------------

    private static int pair(Context ctx, BluetoothAdapter adapter,
                            String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: gammaos-net bt pair <address>");
            return 2;
        }
        String addr = args[2].toUpperCase();
        if (!BluetoothAdapter.checkBluetoothAddress(addr)) {
            System.err.println("gammaos-net bt: invalid address " + addr);
            return 2;
        }
        // Discovery and pairing conflict; cancel any ongoing scan so
        // createBond() can grab the radio immediately.
        if (adapter.isDiscovering()) adapter.cancelDiscovery();
        final BluetoothDevice dev = adapter.getRemoteDevice(addr);
        if (dev.getBondState() == BluetoothDevice.BOND_BONDED) {
            System.out.println("OK");
            return 0;
        }
        // Optional user-supplied PIN for classic PIN pairing (keyboards etc.);
        // null falls back to 0000. Clear any stale passkey we may have published
        // so the UI doesn't show an old one before this bond's request lands.
        final String pin = (args.length >= 4 && !args[3].isEmpty()) ? args[3] : null;
        android.os.SystemProperties.set("sys.gammaos.bt.passkey", "");
        // The latch holder is shared between the broadcast receiver and
        // the main pair flow so we can re-arm it for a second (LE)
        // attempt without having to tear down and re-register the
        // receiver. Array of length 1 is the classic "effectively final
        // mutable" trick for lambda/inner-class capture.
        final CountDownLatch[] doneHolder = { new CountDownLatch(1) };
        final int[] finalBond = { dev.getBondState() };

        // Same AMS constraint as scan(): ctx.registerReceiver() goes
        // through ContextImpl.registerReceiver -> AMS with a non-null
        // caller, but AMS has no ProcessRecord for gammaos-net (we were
        // launched by init/shell, not via Process.start) and returns
        // null, so the receiver is never actually registered. Use the
        // direct IIntentReceiver.Stub path like scan() does.
        HandlerThread ht = new HandlerThread("gammaos-net-bt-pair");
        ht.start();
        final Handler callbackHandler = new Handler(ht.getLooper());
        IIntentReceiver rx = new IIntentReceiver.Stub() {
            @Override
            public void performReceive(Intent intent, int resultCode,
                                       String data, Bundle extras,
                                       boolean ordered, boolean sticky,
                                       int sendingUser) {
                callbackHandler.post(() -> handlePairIntent(
                        intent, addr, finalBond, doneHolder[0],
                        callbackHandler, pin));
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        // PAIRING_REQUEST and BOND_STATE_CHANGED aren't package-targeted
        // (BondStateMachine uses sendOrderedBroadcast/sendBroadcastAsUser
        // without setPackage), so the package filter doesn't strictly
        // need to match, but we pass the system package for consistency.
        String pkg = ctx.getOpPackageName();
        boolean receiverRegistered = false;
        try {
            ActivityManager.getService().registerReceiverWithFeature(
                    null, pkg, null, null, rx, f, null,
                    UserHandle.USER_ALL, Context.RECEIVER_EXPORTED);
            receiverRegistered = true;
        } catch (Throwable t) {
            System.err.println("gammaos-net bt: pair registerReceiver"
                    + " failed: " + t);
            return 4;
        }
        // Signal to the BT stack that Settings' pairing dialog should be
        // suppressed and common variants auto-accepted. BondStateMachine
        // checks this property in sendDisplayPinIntent. Without it the
        // ordered ACTION_PAIRING_REQUEST broadcast reaches Settings and
        // it starts BluetoothPairingDialog on display 0, which in the
        // Nano UI's fullscreen DRM scanout is both visually wrong and
        // unreachable (no input method, no gamepad focus).
        android.os.SystemProperties.set("sys.gammaos.bt_autopair", "1");
        try {
            // Pick the transport based on what the stack thinks the device
            // is. Trust getType(): if the LE scanner saw the peripheral,
            // getType() is DEVICE_TYPE_LE and we must pair over LE, not
            // BR/EDR - forcing BR/EDR on an LE-only peer ends in
            // HCI_ERR_PAGE_TIMEOUT after ~5 s, and worse on dual-mode
            // devices that listen in pairing mode only briefly (Xbox
            // Wireless Controller): the BR/EDR page round-trip kicks the
            // controller out of pairing mode so the follow-up LE pair
            // times out waiting for SSP response from a peer that has
            // stopped accepting new bonds.
            //
            // The HID/A2DP profile attach happens regardless of bond
            // transport: classic HID over SDP on BR/EDR, HID over GATT
            // (HOGP) on LE. Both end up producing UHID on /dev/input.
            // A2DP is BR/EDR-only but headsets are cached as CLASSIC or
            // DUAL, so A2DP bonds still run BR/EDR.
            int devType;
            try { devType = dev.getType(); }
            catch (Throwable t) { devType = BluetoothDevice.DEVICE_TYPE_UNKNOWN; }
            int firstTransport;
            int fallbackTransport;
            switch (devType) {
                case BluetoothDevice.DEVICE_TYPE_LE:
                    firstTransport = 2; // TRANSPORT_LE
                    fallbackTransport = 0; // none
                    break;
                case BluetoothDevice.DEVICE_TYPE_CLASSIC:
                    firstTransport = 1; // TRANSPORT_BREDR
                    fallbackTransport = 0; // none
                    break;
                case BluetoothDevice.DEVICE_TYPE_DUAL:
                    // True dual-mode: BR/EDR first (A2DP + classic HID),
                    // LE retry if the peer isn't answering BR/EDR paging.
                    firstTransport = 1;
                    fallbackTransport = 2;
                    break;
                case BluetoothDevice.DEVICE_TYPE_UNKNOWN:
                default:
                    // No cached type - fall through to the legacy
                    // BR/EDR-first flow (covers stale cache from a prior
                    // classic inquiry).
                    firstTransport = 1;
                    fallbackTransport = 2;
                    break;
            }
            int bondResult = attemptBond(dev, firstTransport,
                    doneHolder[0], finalBond);
            if (bondResult != BluetoothDevice.BOND_BONDED
                    && fallbackTransport != 0) {
                System.err.println("gammaos-net bt: transport " + firstTransport
                        + " bond failed (final=" + bondResult + "), retrying transport "
                        + fallbackTransport);
                try { dev.removeBond(); } catch (Throwable ignored) { }
                Thread.sleep(500);
                doneHolder[0] = new CountDownLatch(1);
                finalBond[0] = dev.getBondState();
                attemptBond(dev, fallbackTransport, doneHolder[0], finalBond);
            }
            // After a successful bond, kick all enabled profiles. AOSP's
            // HidHostService (classic HID) and the GATT-side HOGP path
            // both need an explicit connect to bring up /dev/input/eventX;
            // without this call the bond succeeds but gamepad inputs
            // never show up. BluetoothDevice.connect() is @SystemApi
            // hidden on AOSP 14 - reflect into it. Fails silently on
            // devices where no profile auto-connects.
            if (finalBond[0] == BluetoothDevice.BOND_BONDED) {
                try {
                    BluetoothDevice.class.getMethod("connect").invoke(dev);
                } catch (Throwable t) {
                    System.err.println("gammaos-net bt: post-bond connect()"
                            + " unavailable: " + t);
                }
            }
        } finally {
            try {
                android.os.SystemProperties.set("sys.gammaos.bt_autopair", "0");
                android.os.SystemProperties.set("sys.gammaos.bt.passkey", "");
            } catch (Throwable ignored) { }
            if (receiverRegistered) {
                try {
                    ActivityManager.getService().unregisterReceiver(rx);
                } catch (Throwable ignored) { }
            }
            try { ht.quitSafely(); } catch (Throwable ignored) { }
        }
        if (finalBond[0] == BluetoothDevice.BOND_BONDED) {
            System.out.println("OK");
            return 0;
        }
        System.err.println("gammaos-net bt: pair did not complete, final state="
                + finalBond[0]);
        return 5;
    }

    /**
     * Kick off a bond on the given transport and wait for it to finish.
     * Returns finalBond[0] after the latch resolves so callers can
     * inspect whether they should fall back to a different transport.
     * Timeout is 30 s for the first attempt (matches Settings) which
     * covers both the fast BR/EDR page-timeout case (~5 s) and the
     * slower LE SMP numeric-comparison case.
     */
    private static int attemptBond(BluetoothDevice dev, int transport,
                                   CountDownLatch done, int[] finalBond)
            throws InterruptedException {
        boolean started = false;
        try {
            started = (boolean) BluetoothDevice.class.getMethod(
                    "createBond", int.class).invoke(dev, transport);
        } catch (Throwable t) {
            System.err.println("gammaos-net bt: createBond(" + transport
                    + ") unavailable (" + t + "), using default createBond()");
        }
        if (!started) {
            if (!dev.createBond()) {
                System.err.println("gammaos-net bt: createBond() returned false"
                        + " for transport=" + transport);
                return finalBond[0];
            }
        }
        done.await(30, TimeUnit.SECONDS);
        return finalBond[0];
    }

    private static void handlePairIntent(Intent intent, String addr,
                                         int[] finalBond,
                                         CountDownLatch done,
                                         Handler callbackHandler,
                                         String pin) {
        BluetoothDevice d = intent.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE);
        if (d == null || !addr.equals(d.getAddress())) return;
        String action = intent.getAction();
        if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(action)) {
            int variant = intent.getIntExtra(
                    BluetoothDevice.EXTRA_PAIRING_VARIANT, -1);
            int key = intent.getIntExtra(
                    BluetoothDevice.EXTRA_PAIRING_KEY, -1);
            // Surface the system-generated passkey to the UI so the user can
            // verify it (confirmation) or type it on the remote (display).
            publishPasskey(variant, key);
            // Auto-accept the kinds of pairing that Settings'
            // BluetoothPairingController handles without user input, and use
            // the user-supplied PIN (falling back to 0000) for classic PIN
            // pairing. Xbox wireless controllers do just-works consent (3).
            try {
                switch (variant) {
                    case 2: // PASSKEY_CONFIRMATION (both ends show the same code)
                    case 3: // CONSENT (just works)
                    case 6: // OOB_CONSENT
                    case 4: // DISPLAY_PASSKEY (user types it on the remote)
                    case 5: // DISPLAY_PIN
                        d.setPairingConfirmation(true);
                        break;
                    case 0: // PIN (we provide a PIN; the remote must match it)
                        d.setPin((pin != null ? pin : "0000")
                                .getBytes(StandardCharsets.UTF_8));
                        break;
                    case 1: // PASSKEY entry (the remote shows a code we type)
                        if (pin != null) {
                            try {
                                BluetoothDevice.class.getMethod("setPasskey", int.class)
                                        .invoke(d, Integer.parseInt(pin.trim()));
                            } catch (Throwable t) {
                                System.err.println("pair: setPasskey failed: " + t);
                            }
                        }
                        break;
                    default:
                        break;
                }
            } catch (Throwable t) {
                System.err.println("pair: confirm variant=" + variant
                        + " failed: " + t);
            }
        } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(
                action)) {
            int state = intent.getIntExtra(
                    BluetoothDevice.EXTRA_BOND_STATE,
                    BluetoothDevice.BOND_NONE);
            finalBond[0] = state;
            if (state == BluetoothDevice.BOND_BONDED
                    || state == BluetoothDevice.BOND_NONE) {
                // Give the framework a beat to finish writing the bond
                // to storage before we tear down. Handler lives on our
                // HandlerThread, not the main looper (main is prepared
                // but never looped in gammaos-net).
                callbackHandler.postDelayed(done::countDown, 200);
            }
        }
    }

    private static int unpair(BluetoothAdapter adapter, String[] args) {
        if (args.length < 3) {
            System.err.println("usage: gammaos-net bt unpair <address>");
            return 2;
        }
        String addr = args[2].toUpperCase();
        if (!BluetoothAdapter.checkBluetoothAddress(addr)) {
            System.err.println("gammaos-net bt: invalid address " + addr);
            return 2;
        }
        BluetoothDevice dev = adapter.getRemoteDevice(addr);
        if (dev.getBondState() == BluetoothDevice.BOND_NONE) {
            System.out.println("OK");
            return 0;
        }
        if (!dev.removeBond()) {
            System.err.println("gammaos-net bt: removeBond() returned false");
            return 4;
        }
        System.out.println("OK");
        return 0;
    }

    // ---------------------------------------------------------------------
    // connect / disconnect
    // ---------------------------------------------------------------------

    // BluetoothDevice.connect() / .disconnect() are @SystemApi hidden on
    // AOSP 14 (the same connect() the pair flow reflects into post-bond).
    // They bring up / tear down every enabled profile on the device (A2DP,
    // HID, HEADSET, ...). The actual link transition is asynchronous, so
    // we just kick it and return; the caller refreshes the bonded list to
    // observe the new connected state.
    private static int connect(BluetoothAdapter adapter, String[] args) {
        if (args.length < 3) {
            System.err.println("usage: gammaos-net bt connect <address>");
            return 2;
        }
        String addr = args[2].toUpperCase();
        if (!BluetoothAdapter.checkBluetoothAddress(addr)) {
            System.err.println("gammaos-net bt: invalid address " + addr);
            return 2;
        }
        if (adapter.isDiscovering()) adapter.cancelDiscovery();
        BluetoothDevice dev = adapter.getRemoteDevice(addr);
        if (dev.getBondState() != BluetoothDevice.BOND_BONDED) {
            System.err.println("gammaos-net bt: connect requires a bonded device");
            return 5;
        }
        try {
            BluetoothDevice.class.getMethod("connect").invoke(dev);
            System.out.println("OK");
            return 0;
        } catch (Throwable t) {
            System.err.println("gammaos-net bt: connect() failed: " + t);
            return 5;
        }
    }

    private static int disconnect(BluetoothAdapter adapter, String[] args) {
        if (args.length < 3) {
            System.err.println("usage: gammaos-net bt disconnect <address>");
            return 2;
        }
        String addr = args[2].toUpperCase();
        if (!BluetoothAdapter.checkBluetoothAddress(addr)) {
            System.err.println("gammaos-net bt: invalid address " + addr);
            return 2;
        }
        BluetoothDevice dev = adapter.getRemoteDevice(addr);
        try {
            BluetoothDevice.class.getMethod("disconnect").invoke(dev);
            System.out.println("OK");
            return 0;
        } catch (Throwable t) {
            System.err.println("gammaos-net bt: disconnect() failed: " + t);
            return 5;
        }
    }

    // ---------------------------------------------------------------------
    // discoverable / confirm (inbound pairing) + passkey publish
    // ---------------------------------------------------------------------

    // Publish a pairing passkey/PIN for the UI to display. Variants 2/4/5 carry a
    // 6-digit numeric key (confirmation / display); the others have none.
    private static void publishPasskey(int variant, int key) {
        String s = "";
        if (key >= 0 && (variant == 2 || variant == 4 || variant == 5)) {
            s = String.format("%06d", key);
        }
        try { android.os.SystemProperties.set("sys.gammaos.bt.passkey", s); }
        catch (Throwable ignored) { }
    }

    // Make the adapter connectable + discoverable for <secs> (0 = back to
    // connectable-only). setScanMode / setDiscoverableTimeout are @SystemApi
    // hidden on AOSP 14, reached reflectively.
    private static void setDiscoverable(BluetoothAdapter adapter, int secs) {
        final int SCAN_MODE_CONNECTABLE = 21;
        final int SCAN_MODE_CONNECTABLE_DISCOVERABLE = 23;
        try {
            if (secs > 0) {
                try {
                    BluetoothAdapter.class.getMethod("setDiscoverableTimeout",
                            java.time.Duration.class)
                            .invoke(adapter, java.time.Duration.ofSeconds(secs));
                } catch (Throwable t) {
                    try { BluetoothAdapter.class.getMethod("setDiscoverableTimeout", int.class)
                            .invoke(adapter, secs); } catch (Throwable ignored) { }
                }
            }
            BluetoothAdapter.class.getMethod("setScanMode", int.class)
                    .invoke(adapter, secs > 0 ? SCAN_MODE_CONNECTABLE_DISCOVERABLE
                                              : SCAN_MODE_CONNECTABLE);
        } catch (Throwable t) {
            System.err.println("gammaos-net bt: setScanMode/discoverable failed: " + t);
        }
    }

    private static int discoverable(BluetoothAdapter adapter, String[] args) {
        int secs = 120;
        if (args.length >= 3) {
            try { secs = Math.max(0, Math.min(3600, Integer.parseInt(args[2]))); }
            catch (NumberFormatException ignored) { }
        }
        setDiscoverable(adapter, secs);
        System.out.println("OK");
        return 0;
    }

    // Apply the Nano UI's decision to an inbound pairing request that the
    // BondStateMachine patch surfaced via sys.gammaos.bt.inbound (and left
    // pending, suppressing Settings' dialog). accept[:pin] / reject.
    private static int confirm(BluetoothAdapter adapter, String[] args) {
        if (args.length < 4) {
            System.err.println("usage: gammaos-net bt confirm <address> accept|reject [pin]");
            return 2;
        }
        String addr = args[2].toUpperCase();
        if (!BluetoothAdapter.checkBluetoothAddress(addr)) {
            System.err.println("gammaos-net bt: invalid address " + addr);
            return 2;
        }
        BluetoothDevice dev = adapter.getRemoteDevice(addr);
        boolean accept = "accept".equalsIgnoreCase(args[3]);
        String pin = (args.length >= 5) ? args[4] : null;
        try {
            if (accept) {
                int variant = -1;
                // Use the PIN path only when the request was a classic PIN; we
                // do not know the variant here, so try setPin if a PIN was given
                // and otherwise confirm.
                if (pin != null && !pin.isEmpty()) {
                    dev.setPin(pin.getBytes(StandardCharsets.UTF_8));
                } else {
                    dev.setPairingConfirmation(true);
                }
            } else {
                try { BluetoothDevice.class.getMethod("cancelPairing").invoke(dev); }
                catch (Throwable t) {
                    try { dev.setPairingConfirmation(false); } catch (Throwable ignored) { }
                }
            }
            System.out.println("OK");
            return 0;
        } catch (Throwable t) {
            System.err.println("gammaos-net bt: confirm failed: " + t);
            return 5;
        }
    }

    // ---------------------------------------------------------------------
    // list-bonded
    // ---------------------------------------------------------------------

    private static int listBonded(BluetoothAdapter adapter) {
        Set<BluetoothDevice> bonded;
        try {
            bonded = adapter.getBondedDevices();
        } catch (SecurityException e) {
            System.err.println("gammaos-net bt: getBondedDevices denied: " + e);
            return 3;
        }
        if (bonded == null) return 0;
        // BluetoothDevice.isConnected() is @UnsupportedAppUsage on AOSP 14
        // but always present; it returns whether the ACL link is up at the
        // HCI level, which is the "live" state the UI needs to show
        // Connected vs Paired. No stable public alternative — getConnectedDevices()
        // requires a profile proxy, which is async to acquire.
        java.lang.reflect.Method isConnectedM = null;
        try {
            isConnectedM = BluetoothDevice.class.getDeclaredMethod("isConnected");
            isConnectedM.setAccessible(true);
        } catch (Throwable ignored) { }
        StringBuilder sb = new StringBuilder();
        for (BluetoothDevice d : bonded) {
            String name = "";
            try { name = d.getName(); } catch (SecurityException ignored) { }
            if (name == null) name = "";
            int cod = 0;
            try {
                BluetoothClass cls = d.getBluetoothClass();
                if (cls != null) cod = cls.getDeviceClass();
            } catch (SecurityException ignored) { }
            int connected = 0;
            if (isConnectedM != null) {
                try {
                    Object r = isConnectedM.invoke(d);
                    if (r instanceof Boolean && (Boolean) r) connected = 1;
                } catch (Throwable ignored) { }
            }
            sb.append(d.getAddress()).append('\t')
              .append(sanitize(name)).append('\t')
              .append(cod).append('\t')
              .append(connected).append('\n');
        }
        System.out.print(sb.toString());
        return 0;
    }

    // ---------------------------------------------------------------------

    private static String sanitize(String s) {
        if (s == null) return "";
        // Strip tabs / newlines so our TSV stays parseable even if a
        // device advertises a name with a literal \t or \n in it.
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\t' || c == '\n' || c == '\r') out.append(' ');
            else out.append(c);
        }
        return out.toString();
    }
}
