/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.net.cts;

import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.Manifest.permission.NETWORK_SETTINGS;

import static com.android.testutils.TestPermissionUtil.runAsShell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.PacProxyManager;
import android.net.Proxy;
import android.net.ProxyInfo;
import android.net.Uri;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;
import android.util.Range;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.RequiredFeatureRule;

import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;
import com.android.testutils.DevSdkIgnoreRunner;
import com.android.testutils.TestHttpServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import fi.iki.elonen.NanoHTTPD.Response.Status;

@IgnoreUpTo(Build.VERSION_CODES.R)
@RunWith(DevSdkIgnoreRunner.class)
public final class PacProxyManagerTest {
    private static final String TAG = PacProxyManagerTest.class.getSimpleName();
    private static final int PROXY_CHANGED_BROADCAST_TIMEOUT_MS = 5000;
    private static final int CALLBACK_TIMEOUT_MS = 3 * 1000;

    private Context mContext;
    private TestHttpServer mServer;
    private ConnectivityManager mCm;
    private PacProxyManager mPacProxyManager;
    private ServerSocket mServerSocket;
    private Instrumentation mInstrumentation;

    // Devices without WebView/JavaScript cannot support PAC proxies.
    @Rule
    public RequiredFeatureRule mRequiredWebviewFeatureRule =
        new RequiredFeatureRule(PackageManager.FEATURE_WEBVIEW);

    private static final String PAC_FILE = "function FindProxyForURL(url, host)"
            + "{"
            + "  return \"PROXY 192.168.0.1:9091\";"
            + "}";

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getContext();

        mCm = mContext.getSystemService(ConnectivityManager.class);
        mPacProxyManager = (PacProxyManager) mContext.getSystemService(PacProxyManager.class);
        mServer = new TestHttpServer();
        mServer.start();
    }

    @After
    public void tearDown() throws Exception {
        if (mServer != null) {
            mServer.stop();
            mServer = null;
        }
    }

    private class TestPacProxyInstalledListener implements
            PacProxyManager.PacProxyInstalledListener {
        private final CountDownLatch mLatch = new CountDownLatch(1);

        public void onPacProxyInstalled(Network network, ProxyInfo proxy) {
            Log.e(TAG, "onPacProxyInstalled is called.");
            mLatch.countDown();
        }

        public boolean waitForCallback() throws Exception {
            final boolean result = mLatch.await(CALLBACK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return result;
        }
    }

    private class ProxyBroadcastReceiver extends BroadcastReceiver {
        private final CountDownLatch mLatch = new CountDownLatch(1);
        private final ProxyInfo mProxy;

        ProxyBroadcastReceiver(ProxyInfo proxy) {
            mProxy = proxy;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final ProxyInfo proxy = (ProxyInfo) intent.getExtra(Proxy.EXTRA_PROXY_INFO,
                    ProxyInfo.buildPacProxy(Uri.EMPTY));
            // ProxyTracker sends sticky broadcast which will receive the last broadcast while
            // register the intent receiver. That is, if system never receives the intent then
            // it won't receive an intent when register the receiver. How many intents will be
            // received in the test is unpredictable so here counts down the latch when the PAC
            // file in the intent is the same as the one at registration.
            if (mProxy.getPacFileUrl().equals(proxy.getPacFileUrl())) {
                // Host/Port represent a local proxy server that redirects to the PAC-configured
                // server. Host should be "localhost" and the port should be a value which is
                // between 0 and 65535.
                assertEquals(proxy.getHost(), "localhost");
                assertInRange(proxy.getPort(), 0 /* lower */, 65535 /* upper */);
                mLatch.countDown();
            }
        }

        public boolean waitForProxyChanged() throws Exception {
            final boolean result = mLatch.await(PROXY_CHANGED_BROADCAST_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS);
            return result;
        }
    }

    @AppModeFull(reason = "Instant apps can't bind sockets to localhost for a test proxy server")
    @Test
    public void testSetCurrentProxyScriptUrl() throws Exception {
        // Register a PacProxyInstalledListener
        final TestPacProxyInstalledListener listener = new TestPacProxyInstalledListener();
        final Executor executor = (Runnable r) -> r.run();

        runAsShell(NETWORK_SETTINGS, () -> {
            mPacProxyManager.addPacProxyInstalledListener(executor, listener);
        });

        final Map<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Type", "application/x-ns-proxy-autoconfig");
        final Uri pacProxyUrl = Uri.parse("http://localhost:"
                + mServer.getListeningPort() + "/proxy.pac");
        mServer.addResponse(pacProxyUrl, Status.OK, headers, PAC_FILE);

        final ProxyInfo proxy = ProxyInfo.buildPacProxy(pacProxyUrl);
        final ProxyBroadcastReceiver receiver = new ProxyBroadcastReceiver(proxy);
        mContext.registerReceiver(receiver, new IntentFilter(Proxy.PROXY_CHANGE_ACTION));

        // Call setCurrentProxyScriptUrl with the URL of the pac file.
        // Note that the proxy script is global to device, and setting it from a different user
        // should require INTERACT_ACROSS_USERS_FULL permission which the Settings app has.
        runAsShell(NETWORK_SETTINGS, INTERACT_ACROSS_USERS_FULL, () -> {
            mPacProxyManager.setCurrentProxyScriptUrl(proxy);
        });

        // Make sure the listener was called and testing the intent is received.
        try {
            assertTrue("Didn't receive PROXY_CHANGE_ACTION broadcast.",
                    receiver.waitForProxyChanged());
            assertTrue("Did not receive onPacProxyInstalled callback.",
                    listener.waitForCallback());
        } finally {
            runAsShell(NETWORK_SETTINGS, () -> {
                mPacProxyManager.removePacProxyInstalledListener(listener);
            });
            mContext.unregisterReceiver(receiver);
        }
    }

    private void assertInRange(int value, int lower, int upper) {
        final Range range = new Range(lower, upper);
        assertTrue(value + "is not within range [" + lower + ", " + upper + "]",
                range.contains(value));
    }
}
