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
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyStore;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Date;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
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
        // The requestNetwork callback can fail to fire onAvailable even when the
        // device already has a usable connection (e.g. the network was up and
        // validated before we asked). Fall back to the active network, then to
        // any INTERNET-capable network, before giving up.
        if (net == null) {
            net = cm.getActiveNetwork();
        }
        if (net == null) {
            try {
                for (Network n : cm.getAllNetworks()) {
                    NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                    if (nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        net = n;
                        break;
                    }
                }
            } catch (Throwable ignored) {}
        }
        if (net == null) {
            System.err.println("download: no internet network");
            rc = 4;
        } else {
            try {
                rc = fetch(net, urlStr, dest, 6);
            } catch (Throwable t) {
                // A console whose clock was reset (a flat RTC battery, no time sync yet) sees every
                // server certificate as "not yet valid". When the clock is behind the date this
                // system was built, check the chain as of the build date instead (still against
                // the system's trusted roots, still verifying the host name) and try once more.
                if (clockBehindBuild() && isCertTimeError(t)) {
                    System.err.println("download: clock is behind the build date, checking the "
                            + "certificate as of the build date");
                    try {
                        sBuildDateSockets = buildDateSocketFactory();
                        rc = fetch(net, urlStr, dest, 6);
                    } catch (Throwable t2) {
                        System.err.println("download: " + t2);
                        rc = 5;
                    }
                } else {
                    System.err.println("download: " + t);
                    rc = 5;
                }
            }
        }
        try { cm.unregisterNetworkCallback(cb); } catch (Throwable ignored) {}
        ht.quitSafely();
        return rc;
    }

    // Set once the clock was found to be behind the build date (see run).
    private static SSLSocketFactory sBuildDateSockets;

    private static boolean clockBehindBuild() {
        return Build.TIME > 0 && System.currentTimeMillis() < Build.TIME - 24L * 3600L * 1000L;
    }

    private static boolean isCertTimeError(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            String n = c.getClass().getName();
            String m = String.valueOf(c.getMessage());
            if (n.endsWith("CertificateNotYetValidException") || n.endsWith("CertificateExpiredException")
                    || n.endsWith("CertPathValidatorException") || n.endsWith("SSLHandshakeException")
                    || m.contains("not yet valid") || m.contains("expired")) {
                return true;
            }
            if (c.getCause() == c) break;
        }
        return false;
    }

    // TLS whose server chains are validated with PKIX against the system CA store as of the date
    // this system was built rather than "now". Host names are still checked by the connection.
    private static SSLSocketFactory buildDateSocketFactory() throws Exception {
        final KeyStore roots = KeyStore.getInstance("AndroidCAStore");
        roots.load(null, null);
        final Date asOf = new Date(Build.TIME);
        X509TrustManager tm = new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                throw new CertificateException("client certificates are not used here");
            }
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                if (chain == null || chain.length == 0) throw new CertificateException("no chain");
                // A server may send its root as the last certificate; the path to validate ends
                // below the trust anchor, so leave out trailing self-issued certificates.
                int n = chain.length;
                while (n > 1 && chain[n - 1].getSubjectX500Principal().equals(chain[n - 1].getIssuerX500Principal())) n--;
                try {
                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                    CertPath path = cf.generateCertPath(Arrays.asList(chain).subList(0, n));
                    PKIXParameters params = new PKIXParameters(roots);
                    params.setRevocationEnabled(false);
                    params.setDate(asOf);
                    CertPathValidator.getInstance("PKIX").validate(path, params);
                } catch (CertificateException e) {
                    throw e;
                } catch (Exception e) {
                    throw new CertificateException(e);
                }
            }
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new TrustManager[] {tm}, null);
        return ctx.getSocketFactory();
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
        if (sBuildDateSockets != null && h instanceof HttpsURLConnection) {
            ((HttpsURLConnection) h).setSSLSocketFactory(sBuildDateSockets);
        }
        h.setConnectTimeout(20000);
        h.setReadTimeout(60000);
        h.setInstanceFollowRedirects(false);
        // Some hosts (GitHub/GitLab archive endpoints, CDNs) reject the default
        // "Java/<ver>" User-Agent with a 4xx, which surfaces to the caller as a
        // failed download. Send a normal UA and accept anything.
        h.setRequestProperty("User-Agent", "gammaos-nano");
        h.setRequestProperty("Accept", "*/*");
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
