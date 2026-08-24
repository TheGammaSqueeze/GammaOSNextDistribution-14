/*
 * DownloadSubcommand - fetch a URL to a file over the framework's internet network.
 *
 * The gammaos-nano home runs as an init-started native daemon with no default network, so a curl
 * it forks fails DNS, and an app_process forked from it also has no bound network (getActiveNetwork
 * is null and, on Android 14, getAllNetworks returns 0 for a caller with no network access). So we
 * actively requestNetwork() an INTERNET-capable network and download over the Network the callback
 * hands back via Network.openConnection(), which binds each request to that specific network. Used
 * by the ES-DE theme downloader (themes list + theme zip archives).
 */
package com.gammaos.net;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.HandlerThread;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class DownloadSubcommand {

    // args: download <url> <dest>
    public static int run(Context ctx, String[] args) {
        if (args.length < 3) {
            System.err.println("usage: gammaos-net download <url> <dest>");
            return 2;
        }
        final String urlStr = args[1];
        final String dest = args[2];

        ConnectivityManager cm = ctx.getSystemService(ConnectivityManager.class);
        HandlerThread ht = new HandlerThread("gnet-dl");
        ht.start();
        Handler handler = new Handler(ht.getLooper());

        final AtomicReference<Network> netRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        NetworkRequest req = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        ConnectivityManager.NetworkCallback cb = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network n) { netRef.compareAndSet(null, n); latch.countDown(); }
        };

        int rc;
        try {
            cm.requestNetwork(req, cb, handler);
        } catch (Throwable t) {
            System.err.println("download: requestNetwork: " + t);
            // Fall back to whatever the caller can already see.
            Network active = cm.getActiveNetwork();
            if (active != null) { netRef.set(active); latch.countDown(); }
        }
        try {
            latch.await(20, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}

        Network net = netRef.get();
        if (net == null) {
            System.err.println("download: no internet network");
            rc = 4;
        } else {
            try {
                rc = fetch(net, urlStr, dest, 6);
            } catch (Throwable t) {
                System.err.println("download: " + t);
                rc = 5;
            }
        }
        try { cm.unregisterNetworkCallback(cb); } catch (Throwable ignored) {}
        ht.quitSafely();
        return rc;
    }

    // Download over the given network, following redirects manually (so each hop stays on the same
    // bound network). Writes to <dest>.part then renames on success.
    private static int fetch(Network net, String urlStr, String dest, int redirects)
            throws Exception {
        URL url = new URL(urlStr);
        URLConnection uc = net.openConnection(url);
        if (!(uc instanceof HttpURLConnection)) {
            System.err.println("download: unsupported url");
            return 7;
        }
        HttpURLConnection h = (HttpURLConnection) uc;
        h.setConnectTimeout(20000);
        h.setReadTimeout(60000);
        h.setInstanceFollowRedirects(false);
        int code = h.getResponseCode();
        if (code >= 300 && code < 400 && redirects > 0) {
            String loc = h.getHeaderField("Location");
            h.disconnect();
            if (loc == null) { System.err.println("download: redirect without location"); return 8; }
            return fetch(net, new URL(url, loc).toString(), dest, redirects - 1);
        }
        if (code != 200) {
            h.disconnect();
            System.err.println("download: http " + code);
            return 6;
        }
        File part = new File(dest + ".part");
        try (InputStream in = h.getInputStream();
             OutputStream out = new FileOutputStream(part)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            h.disconnect();
        }
        File out = new File(dest);
        out.delete();
        if (!part.renameTo(out)) {
            System.err.println("download: rename failed");
            return 9;
        }
        return 0;
    }
}
