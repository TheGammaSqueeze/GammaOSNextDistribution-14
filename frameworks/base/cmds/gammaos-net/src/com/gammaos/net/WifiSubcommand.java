/*
 * WifiSubcommand - `gammaos-net wifi <action>`.
 *
 * connect-saved <netId>
 *     Force-switch to the saved network with the given id. Calling
 *     WifiManager.connect(netId, ActionListener) alone is not enough:
 *     its onSuccess() fires as soon as WifiConfigManager marks the
 *     network selected, before association actually moves. If Wi-Fi is
 *     already associated to another saved network with a strong signal
 *     the supplicant often stays put. We reproduce the Settings
 *     NetworkProviderSettings behaviour plus an explicit
 *     disconnect + enableNetwork(disableOthers=true) + reconnect so the
 *     switch lands even between two in-range saved networks.
 */
package com.gammaos.net;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class WifiSubcommand {

    public static int run(Context ctx, String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("gammaos-net wifi: missing action");
            return 2;
        }
        switch (args[1]) {
            case "connect-saved":
                return connectSaved(ctx, args);
            default:
                System.err.println("gammaos-net wifi: unknown action "
                        + args[1]);
                return 2;
        }
    }

    private static int connectSaved(Context ctx, String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: gammaos-net wifi connect-saved <netId>");
            return 2;
        }
        final int netId;
        try {
            netId = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            System.err.println("gammaos-net wifi: bad netId '" + args[2] + "'");
            return 2;
        }
        WifiManager wm = ctx.getSystemService(WifiManager.class);
        if (wm == null) {
            System.err.println("gammaos-net wifi: no WifiManager");
            return 3;
        }
        if (!wm.isWifiEnabled()) {
            wm.setWifiEnabled(true);
            for (int i = 0; i < 40 && !wm.isWifiEnabled(); i++) {
                Thread.sleep(250);
            }
        }
        // Verify the target id is actually a saved network. connect() on a
        // non-existent id fails silently on some vendor stacks.
        List<WifiConfiguration> saved = wm.getConfiguredNetworks();
        boolean found = false;
        String targetSsid = null;
        if (saved != null) {
            for (WifiConfiguration c : saved) {
                if (c.networkId == netId) {
                    found = true;
                    targetSsid = stripQuotes(c.SSID);
                    break;
                }
            }
        }
        if (!found) {
            System.err.println("gammaos-net wifi: no saved network with id "
                    + netId);
            return 4;
        }

        // Prime the supplicant scan cache with the target SSID before
        // attempting CONNECT. When the current association is on a
        // different band (e.g. 5 GHz AP while the destination is
        // 2.4 GHz), the supplicant's scan cache only contains the
        // current band and START_CONNECT fails with NETWORK_NOT_FOUND.
        // wm.startScan() runs a full-spectrum scan and
        // SCAN_RESULTS_AVAILABLE_ACTION fires when it completes; we
        // wait up to 5 s for the target SSID to appear. If it never
        // does we still proceed -- the framework will retry internally
        // during CONNECT and either succeed or fail with a clearer
        // reason than NETWORK_NOT_FOUND.
        if (targetSsid != null && !targetSsid.isEmpty()
                && !scanResultsContain(wm, targetSsid)) {
            waitForScanResult(ctx, wm, targetSsid, 5_000);
        }

        // Kick the framework out of its current association first so the
        // follow-up enableNetwork + reconnect isn't racing against an
        // already-satisfied supplicant.
        try { wm.disconnect(); } catch (Throwable ignored) { }

        // Mark the target as the only auto-join candidate for this switch.
        // enableNetwork(netId, true) disables every other saved config at
        // the framework layer so the supplicant has exactly one choice.
        if (!wm.enableNetwork(netId, true)) {
            System.err.println("gammaos-net wifi: enableNetwork(" + netId
                    + ", disableOthers=true) returned false");
        }

        // Also run connect(netId, listener): it updates the last-user-selected
        // network marker that WifiConfigManager uses when scoring, and it
        // fires the internal CMD_CONNECT_NETWORK path with the right uid.
        final CountDownLatch selected = new CountDownLatch(1);
        final int[] selectRc = { -1 };
        wm.connect(netId, new WifiManager.ActionListener() {
            @Override public void onSuccess() {
                selectRc[0] = 0;
                selected.countDown();
            }
            @Override public void onFailure(int reason) {
                selectRc[0] = reason == 0 ? 1 : reason;
                System.err.println("gammaos-net wifi: connect listener failed ("
                        + reason + ")");
                selected.countDown();
            }
        });
        selected.await(3, TimeUnit.SECONDS);

        // Finally, issue a reconnect(). enableNetwork alone doesn't always
        // knock the framework off a sticky association; reconnect() forces
        // it to re-evaluate and bring the newly-selected net up.
        wm.reconnect();

        // Wait for the association to actually move to the target SSID.
        // Timeout is generous because on AOSP 14 the framework's
        // WifiNetworkSelector can briefly attach to whichever BSSID was
        // highest-scored in the pre-disconnect scan (often the network
        // we're trying to leave) before REASON_FRAMEWORK_DISCONNECT_FAST_
        // RECONNECT kicks in and it retries the next WNS candidate. The
        // full "connect-old -> blocklist -> retry -> connect-target"
        // round-trip takes 15-25 s on congested 5 GHz, so we budget 30 s.
        boolean moved = waitForSsid(ctx, wm, targetSsid, 30_000);
        if (!moved) {
            // Last resort: one more reconnect in case the first one raced
            // with the old disconnect. Some stacks need two nudges.
            wm.reconnect();
            moved = waitForSsid(ctx, wm, targetSsid, 5_000);
        }
        if (moved) {
            System.out.println("OK");
            return 0;
        }
        System.err.println("gammaos-net wifi: association did not move to \""
                + targetSsid + "\" within timeout"
                + " (listener rc=" + selectRc[0] + ")");
        return 5;
    }

    private static boolean waitForSsid(Context ctx, WifiManager wm,
                                       String targetSsid, long timeoutMs)
            throws InterruptedException {
        if (targetSsid == null || targetSsid.isEmpty()) {
            Thread.sleep(Math.min(timeoutMs, 2000));
            return true;
        }
        if (isConnectedTo(wm, targetSsid)) return true;
        final CountDownLatch done = new CountDownLatch(1);
        BroadcastReceiver rx = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                String action = intent.getAction();
                if (!WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
                    return;
                }
                NetworkInfo ni = intent.getParcelableExtra(
                        WifiManager.EXTRA_NETWORK_INFO);
                if (ni == null || !ni.isConnected()) return;
                WifiInfo wi = intent.getParcelableExtra(
                        WifiManager.EXTRA_WIFI_INFO);
                String s = wi != null ? stripQuotes(wi.getSSID()) : null;
                if (s != null && s.equals(targetSsid)) {
                    done.countDown();
                }
            }
        };
        IntentFilter f = new IntentFilter(
                WifiManager.NETWORK_STATE_CHANGED_ACTION);
        ctx.registerReceiver(rx, f, Context.RECEIVER_EXPORTED);
        try {
            if (isConnectedTo(wm, targetSsid)) return true;
            return done.await(timeoutMs, TimeUnit.MILLISECONDS)
                    || isConnectedTo(wm, targetSsid);
        } finally {
            try { ctx.unregisterReceiver(rx); } catch (Throwable ignored) { }
        }
    }

    private static boolean scanResultsContain(WifiManager wm, String targetSsid) {
        List<ScanResult> results = wm.getScanResults();
        if (results == null) return false;
        for (ScanResult r : results) {
            if (r == null) continue;
            String s = stripQuotes(r.SSID);
            if (s != null && s.equals(targetSsid)) return true;
        }
        return false;
    }

    private static void waitForScanResult(Context ctx, WifiManager wm,
                                          String targetSsid, long timeoutMs)
            throws InterruptedException {
        final CountDownLatch done = new CountDownLatch(1);
        BroadcastReceiver rx = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                if (!WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(
                        intent.getAction())) {
                    return;
                }
                if (scanResultsContain(wm, targetSsid)) {
                    done.countDown();
                }
            }
        };
        IntentFilter f = new IntentFilter(
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        ctx.registerReceiver(rx, f, Context.RECEIVER_EXPORTED);
        try {
            try { wm.startScan(); } catch (Throwable ignored) { }
            if (scanResultsContain(wm, targetSsid)) return;
            done.await(timeoutMs, TimeUnit.MILLISECONDS);
        } finally {
            try { ctx.unregisterReceiver(rx); } catch (Throwable ignored) { }
        }
    }

    private static boolean isConnectedTo(WifiManager wm, String targetSsid) {
        WifiInfo wi = wm.getConnectionInfo();
        if (wi == null) return false;
        String s = stripQuotes(wi.getSSID());
        return s != null && s.equals(targetSsid)
                && wi.getSupplicantState() != null
                && wi.getSupplicantState().toString().equals("COMPLETED");
    }

    private static String stripQuotes(String s) {
        if (s == null) return null;
        if (s.length() >= 2 && s.charAt(0) == '"'
                && s.charAt(s.length() - 1) == '"') {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
