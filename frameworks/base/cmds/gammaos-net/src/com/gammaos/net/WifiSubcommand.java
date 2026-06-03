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
import android.net.ConnectivityManager;
import android.net.IpConfiguration;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.ProxyInfo;
import android.net.RouteInfo;
import android.net.StaticIpConfiguration;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            case "configure":
                return configure(ctx, args);
            default:
                System.err.println("gammaos-net wifi: unknown action "
                        + args[1]);
                return 2;
        }
    }

    // configure <ssid> <security> [key] [--ip A --subnet M --gw G --dns1 D1
    //           --dns2 D2 --proxy-host H --proxy-port P --mtu N --dns-only 1]
    //
    // Builds (or updates) the WifiConfiguration for <ssid> with the chosen
    // security, applies a static IpConfiguration (IP/gateway/DNS) and/or an HTTP
    // proxy, connects, and optionally sets the interface MTU. --dns-only means
    // the user picked DHCP for the address but manual DNS: we connect with DHCP,
    // read the lease, then re-pin it as a static config that keeps the leased
    // address/gateway but overrides the DNS servers (Android has no DHCP-address
    // + manual-DNS mode, so this is the faithful way to honour manual DNS).
    private static int configure(Context ctx, String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: gammaos-net wifi configure <ssid> "
                    + "<open|wep|wpa2|wpa3|owe> [key] [--ip .. --subnet .. "
                    + "--gw .. --dns1 .. --dns2 .. --proxy-host .. --proxy-port "
                    + ".. --mtu .. --dns-only 1]");
            return 2;
        }
        String ssid = args[2];
        String sec = args[3];
        String key = null;
        int i = 4;
        if (i < args.length && !args[i].startsWith("--")) { key = args[i]; i++; }
        Map<String, String> opt = new HashMap<>();
        for (; i + 1 < args.length; i += 2) {
            if (args[i].startsWith("--")) opt.put(args[i].substring(2), args[i + 1]);
        }

        WifiManager wm = ctx.getSystemService(WifiManager.class);
        if (wm == null) { System.err.println("gammaos-net wifi: no WifiManager"); return 3; }
        if (!wm.isWifiEnabled()) {
            wm.setWifiEnabled(true);
            for (int t = 0; t < 40 && !wm.isWifiEnabled(); t++) Thread.sleep(250);
        }

        boolean dnsOnly = "1".equals(opt.get("dns-only"));
        boolean hasStaticIp = opt.containsKey("ip") && opt.containsKey("gw");
        boolean hasDns = opt.containsKey("dns1");
        boolean hasProxy = opt.containsKey("proxy-host");

        // Build the base config (SSID + security).
        WifiConfiguration cfg = findOrNew(wm, ssid);
        cfg.SSID = "\"" + ssid + "\"";
        applySecurity(cfg, sec, key);

        // IpConfiguration: STATIC when the user gave an address; DHCP otherwise.
        IpConfiguration ipc = new IpConfiguration();
        if (hasStaticIp) {
            ipc.setIpAssignment(IpConfiguration.IpAssignment.STATIC);
            ipc.setStaticIpConfiguration(buildStatic(opt.get("ip"),
                    opt.get("subnet"), opt.get("gw"),
                    opt.get("dns1"), opt.get("dns2")));
        } else {
            ipc.setIpAssignment(IpConfiguration.IpAssignment.DHCP);
        }
        applyProxy(ipc, opt);
        cfg.setIpConfiguration(ipc);

        int netId = saveAndConnect(ctx, wm, cfg, ssid);
        if (netId < 0) return 5;

        // DHCP address + manual DNS: read the lease, re-pin as static with the
        // leased address/gateway but the manual DNS servers.
        if (!hasStaticIp && dnsOnly && hasDns) {
            StaticIpConfiguration leased = staticFromLease(ctx, wm,
                    opt.get("dns1"), opt.get("dns2"));
            if (leased != null) {
                WifiConfiguration upd = findOrNew(wm, ssid);
                upd.SSID = "\"" + ssid + "\"";
                applySecurity(upd, sec, key);
                IpConfiguration sipc = new IpConfiguration();
                sipc.setIpAssignment(IpConfiguration.IpAssignment.STATIC);
                sipc.setStaticIpConfiguration(leased);
                applyProxy(sipc, opt);
                upd.setIpConfiguration(sipc);
                saveAndConnect(ctx, wm, upd, ssid);
            } else {
                System.err.println("gammaos-net wifi: could not read DHCP lease "
                        + "for manual DNS; left on DHCP DNS");
            }
        }

        // MTU is interface-level, not part of the WifiConfiguration.
        if (opt.containsKey("mtu")) applyMtu(opt.get("mtu"));

        System.out.println("OK netId=" + netId);
        return 0;
    }

    private static WifiConfiguration findOrNew(WifiManager wm, String ssid) {
        List<WifiConfiguration> saved = wm.getConfiguredNetworks();
        if (saved != null) {
            for (WifiConfiguration c : saved) {
                if (ssid.equals(stripQuotes(c.SSID))) return c;
            }
        }
        return new WifiConfiguration();
    }

    private static void applySecurity(WifiConfiguration cfg, String sec, String key) {
        cfg.allowedKeyManagement.clear();
        cfg.allowedAuthAlgorithms.clear();
        cfg.preSharedKey = null;
        for (int k = 0; k < cfg.wepKeys.length; k++) cfg.wepKeys[k] = null;
        switch (sec) {
            case "wep":
                cfg.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                if (key != null) {
                    boolean hex = key.matches("[0-9A-Fa-f]+")
                            && (key.length() == 10 || key.length() == 26
                                || key.length() == 58);
                    cfg.wepKeys[0] = hex ? key : "\"" + key + "\"";
                    cfg.wepTxKeyIndex = 0;
                }
                break;
            case "wpa3":
                cfg.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.SAE);
                cfg.requirePmf = true;
                if (key != null) cfg.preSharedKey = "\"" + key + "\"";
                break;
            case "owe":
                cfg.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.OWE);
                cfg.requirePmf = true;
                break;
            case "open":
                cfg.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                break;
            case "wpa2":
            default:
                cfg.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                if (key != null) cfg.preSharedKey = "\"" + key + "\"";
                break;
        }
    }

    private static StaticIpConfiguration buildStatic(String ip, String subnet,
            String gw, String dns1, String dns2) throws Exception {
        int prefix = maskToPrefix(subnet);
        StaticIpConfiguration.Builder b = new StaticIpConfiguration.Builder();
        b.setIpAddress(new LinkAddress(InetAddress.getByName(ip), prefix));
        if (gw != null && !gw.isEmpty()) b.setGateway(InetAddress.getByName(gw));
        List<InetAddress> dns = new ArrayList<>();
        if (dns1 != null && !dns1.isEmpty()) dns.add(InetAddress.getByName(dns1));
        if (dns2 != null && !dns2.isEmpty()) dns.add(InetAddress.getByName(dns2));
        if (!dns.isEmpty()) b.setDnsServers(dns);
        return b.build();
    }

    private static void applyProxy(IpConfiguration ipc, Map<String, String> opt) {
        String host = opt.get("proxy-host");
        if (host != null && !host.isEmpty()) {
            int port = 8080;
            try { port = Integer.parseInt(opt.get("proxy-port")); }
            catch (Exception ignored) { }
            ipc.setProxySettings(IpConfiguration.ProxySettings.STATIC);
            ipc.setHttpProxy(ProxyInfo.buildDirectProxy(host, port));
        } else {
            ipc.setProxySettings(IpConfiguration.ProxySettings.NONE);
            ipc.setHttpProxy(null);
        }
    }

    // Save the config (add or update by SSID), make it the only auto-join
    // candidate, and bring the association up. Returns the netId or -1.
    private static int saveAndConnect(Context ctx, WifiManager wm,
            WifiConfiguration cfg, String ssid) throws Exception {
        int existing = -1;
        List<WifiConfiguration> saved = wm.getConfiguredNetworks();
        if (saved != null) {
            for (WifiConfiguration c : saved) {
                if (ssid.equals(stripQuotes(c.SSID))) { existing = c.networkId; break; }
            }
        }
        int netId;
        if (existing >= 0) {
            cfg.networkId = existing;
            netId = wm.updateNetwork(cfg);
            if (netId < 0) netId = existing;
        } else {
            netId = wm.addNetwork(cfg);
        }
        if (netId < 0) {
            System.err.println("gammaos-net wifi: addNetwork/updateNetwork failed");
            return -1;
        }
        try { wm.disconnect(); } catch (Throwable ignored) { }
        wm.enableNetwork(netId, true);
        wm.reconnect();
        waitForSsid(ctx, wm, ssid, 25_000);
        return netId;
    }

    // Freeze the current DHCP lease (address/prefix/gateway) into a static
    // config but substitute the manual DNS servers.
    private static StaticIpConfiguration staticFromLease(Context ctx,
            WifiManager wm, String dns1, String dns2) throws Exception {
        ConnectivityManager cm = ctx.getSystemService(ConnectivityManager.class);
        if (cm == null) return null;
        LinkProperties lp = null;
        for (int t = 0; t < 30 && lp == null; t++) {
            Network[] nets = cm.getAllNetworks();
            if (nets != null) {
                for (Network n : nets) {
                    android.net.NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                    if (nc != null && nc.hasTransport(
                            android.net.NetworkCapabilities.TRANSPORT_WIFI)) {
                        LinkProperties cand = cm.getLinkProperties(n);
                        if (cand != null) {
                            for (LinkAddress la : cand.getLinkAddresses()) {
                                if (la.getAddress() instanceof Inet4Address) {
                                    lp = cand; break;
                                }
                            }
                        }
                    }
                    if (lp != null) break;
                }
            }
            if (lp == null) Thread.sleep(500);
        }
        if (lp == null) return null;
        LinkAddress v4 = null;
        for (LinkAddress la : lp.getLinkAddresses()) {
            if (la.getAddress() instanceof Inet4Address) { v4 = la; break; }
        }
        if (v4 == null) return null;
        InetAddress gw = null;
        for (RouteInfo ri : lp.getRoutes()) {
            if (ri.isDefaultRoute() && ri.getGateway() instanceof Inet4Address) {
                gw = ri.getGateway(); break;
            }
        }
        StaticIpConfiguration.Builder b = new StaticIpConfiguration.Builder();
        b.setIpAddress(new LinkAddress(v4.getAddress(), v4.getPrefixLength()));
        if (gw != null) b.setGateway(gw);
        List<InetAddress> dns = new ArrayList<>();
        if (dns1 != null && !dns1.isEmpty()) dns.add(InetAddress.getByName(dns1));
        if (dns2 != null && !dns2.isEmpty()) dns.add(InetAddress.getByName(dns2));
        if (!dns.isEmpty()) b.setDnsServers(dns);
        return b.build();
    }

    // Subnet mask (255.255.255.0) or a bare prefix length ("24") -> prefix.
    private static int maskToPrefix(String subnet) {
        if (subnet == null || subnet.isEmpty()) return 24;
        if (!subnet.contains(".")) {
            try { int p = Integer.parseInt(subnet); if (p >= 0 && p <= 32) return p; }
            catch (Exception ignored) { }
            return 24;
        }
        String[] o = subnet.split("\\.");
        if (o.length != 4) return 24;
        int bits = 0;
        try {
            long m = 0;
            for (int k = 0; k < 4; k++) m = (m << 8) | (Integer.parseInt(o[k]) & 0xFF);
            bits = Long.bitCount(m & 0xFFFFFFFFL);
        } catch (Exception e) { return 24; }
        return bits;
    }

    private static void applyMtu(String mtuStr) {
        int mtu;
        try { mtu = Integer.parseInt(mtuStr); } catch (Exception e) { return; }
        if (mtu < 576 || mtu > 9000) return;
        // ndc talks to netd; it is the only path available without CAP_NET_ADMIN
        // in this process. Best effort - log and continue if it is not permitted.
        try {
            Process p = new ProcessBuilder("ndc", "interface", "setmtu",
                    "wlan0", String.valueOf(mtu)).redirectErrorStream(true).start();
            p.waitFor(4, TimeUnit.SECONDS);
        } catch (Throwable t) {
            System.err.println("gammaos-net wifi: setmtu failed: " + t);
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
