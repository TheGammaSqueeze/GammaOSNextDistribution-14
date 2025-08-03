/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.captiveportallogin;

import static android.Manifest.permission.MANAGE_TEST_NETWORKS;
import static android.app.Activity.RESULT_OK;
import static android.content.Intent.ACTION_CREATE_DOCUMENT;
import static android.net.ConnectivityManager.ACTION_CAPTIVE_PORTAL_SIGN_IN;
import static android.net.ConnectivityManager.EXTRA_CAPTIVE_PORTAL;
import static android.net.ConnectivityManager.EXTRA_CAPTIVE_PORTAL_URL;
import static android.net.ConnectivityManager.EXTRA_CAPTIVE_PORTAL_USER_AGENT;
import static android.net.ConnectivityManager.EXTRA_NETWORK;
import static android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED;
import static android.view.accessibility.AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;

import static androidx.lifecycle.Lifecycle.State.DESTROYED;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.intent.matcher.IntentMatchers.isInternal;
import static androidx.test.espresso.web.sugar.Web.onWebView;
import static androidx.test.espresso.web.webdriver.DriverAtoms.findElement;
import static androidx.test.espresso.web.webdriver.DriverAtoms.webClick;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.captiveportallogin.DownloadService.DOWNLOAD_ABORTED_REASON_FILE_TOO_LARGE;
import static com.android.testutils.TestNetworkTrackerKt.initTestNetwork;
import static com.android.testutils.TestPermissionUtil.runAsShell;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;

import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Instrumentation.ActivityResult;
import android.app.KeyguardManager;
import android.app.UiAutomation;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.CaptivePortal;
import android.net.CaptivePortalData;
import android.net.ConnectivityManager;
import android.net.InetAddresses;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.intent.Intents;
import androidx.test.espresso.web.webdriver.Locator;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiSelector;

import com.android.testutils.SkipPresubmit;
import com.android.testutils.TestNetworkTracker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import fi.iki.elonen.NanoHTTPD;

@RunWith(AndroidJUnit4.class)
@SmallTest
@SkipPresubmit(reason = "Temporary skip for very flaky tests: b/242680995")
public class CaptivePortalLoginActivityTest {
    private static final String TEST_URL = "http://android.test.com";
    private static final int TEST_NETID = 1234;
    private static final String TEST_NC_SSID = "Test NetworkCapabilities SSID";
    private static final String TEST_WIFIINFO_SSID = "Test Other SSID";
    private static final String TEST_URL_QUERY = "testquery";
    private static final long TEST_TIMEOUT_MS = 10_000L;
    private static final LinkAddress TEST_LINKADDR = new LinkAddress(
            InetAddresses.parseNumericAddress("2001:db8::8"), 64);
    private static final String TEST_USERAGENT = "Test/42.0 Unit-test";
    private static final String TEST_FRIENDLY_NAME = "Network friendly name";
    private static final String TEST_PORTAL_HOSTNAME = "localhost";
    private static final String TEST_WIFI_CONFIG_TYPE = "application/x-wifi-config";
    private ActivityScenario<InstrumentedCaptivePortalLoginActivity> mActivityScenario;
    private Network mNetwork = new Network(TEST_NETID);
    private TestNetworkTracker mTestNetworkTracker;

    private @Spy DownloadService mDownloadService = new DownloadService();

    private static ConnectivityManager sConnectivityManager;
    private static DevicePolicyManager sMockDevicePolicyManager;
    private static DownloadService.DownloadServiceBinder sDownloadServiceBinder;

    public static class InstrumentedCaptivePortalLoginActivity extends CaptivePortalLoginActivity {
        private final ConditionVariable mDestroyedCv = new ConditionVariable(false);
        private final CompletableFuture<Intent> mForegroundServiceStart = new CompletableFuture<>();
        // Workaround for https://github.com/android/android-test/issues/1119
        private final CompletableFuture<Intent> mOpenInBrowserIntent =
                new CompletableFuture<>();
        private Intent mServiceIntent = new Intent();
        private final CompletableFuture<ServiceConnection> mServiceBound =
                new CompletableFuture<>();
        private final ConditionVariable mDlServiceunbindCv = new ConditionVariable(false);

        @Override
        public Object getSystemService(String name) {
            switch (name) {
                case Context.CONNECTIVITY_SERVICE:
                    return sConnectivityManager;
                case Context.DEVICE_POLICY_SERVICE:
                    return sMockDevicePolicyManager;
                default:
                    return super.getSystemService(name);
            }
        }

        @Override
        WifiInfo getWifiConnectionInfo() {
            return makeWifiInfo();
        }

        @Override
        public ComponentName startForegroundService(Intent service) {
            assertTrue("Multiple foreground services were started during the test",
                    mForegroundServiceStart.complete(service));
            // Do not actually start the service
            return service.getComponent();
        }

        @Override
        public ComponentName startService(Intent service) {
            mServiceIntent = service;
            // Do not actually start the service
            return service.getComponent();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            mDestroyedCv.open();
        }

        @Override
        public boolean bindService(Intent service, ServiceConnection conn, int flags) {
            assertTrue("Multiple foreground services were bound during the test",
                    mServiceBound.complete(conn));
            getMainThreadHandler().post(() -> conn.onServiceConnected(
                    getInstrumentation().getComponentName(), sDownloadServiceBinder));

            return true;
        }

        @Override
        public void unbindService(ServiceConnection conn) {
            mDlServiceunbindCv.open();
        }

        @Override
        public void startActivity(Intent intent) {
            if (Intent.ACTION_VIEW.equals(intent.getAction())
                    && intent.getData() != null
                    && intent.getData().getAuthority().startsWith(TEST_PORTAL_HOSTNAME)) {
                mOpenInBrowserIntent.complete(intent);
                return;
            }
            super.startActivity(intent);
        }

        @Override
        String getFileProviderAuthority() {
            // Matches the test provider in the test app manifest
            return "com.android.captiveportallogin.tests.fileprovider";
        }
    }

    /** Class to replace CaptivePortal to prevent mock object is updated and replaced by parcel. */
    public static class MockCaptivePortal extends CaptivePortal {
        int mDismissTimes;
        int mIgnoreTimes;
        int mUseTimes;

        private MockCaptivePortal() {
            this(0, 0, 0);
        }
        private MockCaptivePortal(int dismissTimes, int ignoreTimes, int useTimes) {
            super(null);
            mDismissTimes = dismissTimes;
            mIgnoreTimes = ignoreTimes;
            mUseTimes = useTimes;
        }
        @Override
        public void reportCaptivePortalDismissed() {
            mDismissTimes++;
        }

        @Override
        public void ignoreNetwork() {
            mIgnoreTimes++;
        }

        @Override
        public void useNetwork() {
            mUseTimes++;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(mDismissTimes);
            out.writeInt(mIgnoreTimes);
            out.writeInt(mUseTimes);
        }

        public static final Parcelable.Creator<MockCaptivePortal> CREATOR =
                new Parcelable.Creator<MockCaptivePortal>() {
                @Override
                public MockCaptivePortal createFromParcel(Parcel in) {
                    return new MockCaptivePortal(in.readInt(), in.readInt(), in.readInt());
                }

                @Override
                public MockCaptivePortal[] newArray(int size) {
                    return new MockCaptivePortal[size];
                }
        };
    }

    @Before
    public void setUp() throws Exception {
        final Context context = getInstrumentation().getContext();
        sConnectivityManager = spy(context.getSystemService(ConnectivityManager.class));
        sMockDevicePolicyManager = mock(DevicePolicyManager.class);
        sDownloadServiceBinder = mock(DownloadService.DownloadServiceBinder.class);

        MockitoAnnotations.initMocks(this);
        // Use a real (but test) network for the application. The application will pass this
        // network to ConnectivityManager#bindProcessToNetwork, so it needs to be a real, existing
        // network on the device but otherwise has no functional use at all. The http server set up
        // by this test will run on the loopback interface and will not use this test network.
        final UiAutomation automation = getInstrumentation().getUiAutomation();
        automation.adoptShellPermissionIdentity(MANAGE_TEST_NETWORKS);
        try {
            mTestNetworkTracker = initTestNetwork(
                    getInstrumentation().getContext(), TEST_LINKADDR, TEST_TIMEOUT_MS);
        } finally {
            automation.dropShellPermissionIdentity();
        }
        mNetwork = mTestNetworkTracker.getNetwork();
    }

    private static WifiInfo makeWifiInfo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            fail("Only Q should be using WifiInfo; R+ gets the wifi SSID via NetworkCapabilities");
        }

        // WifiInfo did not have a builder before R. Use non-public APIs on Q to set SSID.
        try {
            final WifiInfo info = WifiInfo.class.getConstructor().newInstance();
            final Class<?> wifiSsidClass = Class.forName("android.net.wifi.WifiSsid");
            final Object wifiSsid = wifiSsidClass.getMethod("createFromAsciiEncoded",
                    String.class).invoke(null, TEST_WIFIINFO_SSID);
            WifiInfo.class.getMethod("setSSID", wifiSsidClass).invoke(info, wifiSsid);
            return info;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to create WifiInfo on Q", e);
        }
    }

    @After
    public void tearDown() throws Exception {
        if (mActivityScenario != null) {
            // Note this may sometimes block for 45 seconds until
            // https://github.com/android/android-test/issues/676 is fixed
            mActivityScenario.close();
            Intents.release();
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }
        getInstrumentation().getUiAutomation().setOnAccessibilityEventListener(null);
        getInstrumentation().getContext().getSystemService(ConnectivityManager.class)
                .bindProcessToNetwork(null);
        if (mTestNetworkTracker != null) {
            runAsShell(MANAGE_TEST_NETWORKS, mTestNetworkTracker::teardown);
        }
    }

    private void initActivity(String url) {
        final Context ctx = getInstrumentation().getContext();
        mActivityScenario = ActivityScenario.launch(
                new Intent(ctx, InstrumentedCaptivePortalLoginActivity.class)
                        .setAction(ACTION_CAPTIVE_PORTAL_SIGN_IN)
                        .putExtra(EXTRA_CAPTIVE_PORTAL_URL, url)
                        .putExtra(EXTRA_NETWORK, mNetwork)
                        .putExtra(EXTRA_CAPTIVE_PORTAL_USER_AGENT, TEST_USERAGENT)
                        .putExtra(EXTRA_CAPTIVE_PORTAL, new MockCaptivePortal()));
        mActivityScenario.onActivity(activity -> {
            getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                    android.Manifest.permission.POST_NOTIFICATIONS);
            ctx.getSystemService(KeyguardManager.class).requestDismissKeyguard(activity, null);
            // Dismiss dialogs or notification shade, so the test can interact with the activity.
            activity.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        });
        getInstrumentation().waitForIdleSync();

        // Initialize intent capturing after launching the activity to avoid capturing extra intents
        Intents.init();
    }

    @Test
    public void testonCreateWithNullCaptivePortal() throws Exception {
        final Context ctx = getInstrumentation().getContext();
        final Intent intent = new Intent(ctx, InstrumentedCaptivePortalLoginActivity.class)
                .setAction(ACTION_CAPTIVE_PORTAL_SIGN_IN)
                .putExtra(EXTRA_CAPTIVE_PORTAL_URL, TEST_URL)
                .putExtra(EXTRA_NETWORK, mNetwork)
                .putExtra(EXTRA_CAPTIVE_PORTAL_USER_AGENT, TEST_USERAGENT)
                .putExtra(EXTRA_CAPTIVE_PORTAL, (Bundle) null);
        try (ActivityScenario<InstrumentedCaptivePortalLoginActivity> scenario =
                     ActivityScenario.launch(intent)) {
            getInstrumentation().waitForIdleSync();
            // Verify that activity calls finish() immediately in its onCreate
            assertEquals(DESTROYED, scenario.getState());
        }
    }

    /**
     * Get the activity MockCaptivePortal.
     *
     * The activity may use a different MockCaptivePortal instance after being recreated, so the
     * MockCaptivePortal should not be kept across possible activity recreation.
     */
    private MockCaptivePortal getCaptivePortal() {
        final AtomicReference<MockCaptivePortal> portalRef = new AtomicReference<>();
        mActivityScenario.onActivity(a -> portalRef.set((MockCaptivePortal) a.mCaptivePortal));
        return portalRef.get();
    }

    private void configNonVpnNetwork() {
        final Network[] networks = new Network[] {new Network(mNetwork)};
        doReturn(networks).when(sConnectivityManager).getAllNetworks();
        final NetworkCapabilities nonVpnCapabilities;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // SSID and NetworkCapabilities builder was added in R
            nonVpnCapabilities = new NetworkCapabilities.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .setSsid(TEST_NC_SSID)
                    .build();
        } else {
            nonVpnCapabilities = new NetworkCapabilities()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        }
        doReturn(nonVpnCapabilities).when(sConnectivityManager).getNetworkCapabilities(
                mNetwork);
    }

    private void configVpnNetwork() {
        final Network network1 = new Network(TEST_NETID + 1);
        final Network network2 = new Network(TEST_NETID + 2);
        final Network[] networks = new Network[] {network1, network2};
        doReturn(networks).when(sConnectivityManager).getAllNetworks();
        final NetworkCapabilities underlyingCapabilities = new NetworkCapabilities()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        final NetworkCapabilities vpnCapabilities = new NetworkCapabilities(underlyingCapabilities)
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN);
        doReturn(underlyingCapabilities).when(sConnectivityManager).getNetworkCapabilities(
                network1);
        doReturn(vpnCapabilities).when(sConnectivityManager).getNetworkCapabilities(network2);
    }

    @Test
    public void testHasVpnNetwork() throws Exception {
        initActivity(TEST_URL);
        // Test non-vpn case.
        configNonVpnNetwork();
        mActivityScenario.onActivity(activity -> assertFalse(activity.hasVpnNetwork()));

        // Test vpn case.
        configVpnNetwork();
        mActivityScenario.onActivity(activity -> assertTrue(activity.hasVpnNetwork()));
    }

    @Test
    public void testIsAlwaysOnVpnEnabled() throws Exception {
        initActivity(TEST_URL);
        doReturn(false).when(sMockDevicePolicyManager).isAlwaysOnVpnLockdownEnabled(any());
        mActivityScenario.onActivity(activity -> assertFalse(activity.isAlwaysOnVpnEnabled()));

        doReturn(true).when(sMockDevicePolicyManager).isAlwaysOnVpnLockdownEnabled(any());
        mActivityScenario.onActivity(activity -> assertTrue(activity.isAlwaysOnVpnEnabled()));
    }

    private void runVpnMsgOrLinkToBrowser(boolean useVpnMatcher) {
        initActivity(TEST_URL);
        // Test non-vpn case.
        configNonVpnNetwork();
        doReturn(false).when(sMockDevicePolicyManager).isAlwaysOnVpnLockdownEnabled(any());
        final String linkMatcher = ".*<a[^>]+href.*";
        mActivityScenario.onActivity(act ->
                assertTrue(act.getWebViewClient().getVpnMsgOrLinkToBrowser().matches(linkMatcher)));

        // Test has vpn case.
        configVpnNetwork();
        final String vpnMatcher = ".*<div.*vpnwarning.*";
        mActivityScenario.onActivity(act ->
                assertTrue(act.getWebViewClient().getVpnMsgOrLinkToBrowser().matches(vpnMatcher)));

        // Test always-on vpn case.
        configNonVpnNetwork();
        doReturn(true).when(sMockDevicePolicyManager).isAlwaysOnVpnLockdownEnabled(any());
        mActivityScenario.onActivity(act ->
                assertTrue(act.getWebViewClient().getVpnMsgOrLinkToBrowser().matches(
                        (useVpnMatcher ? vpnMatcher : linkMatcher))));
    }

    @Test @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.Q)
    public void testVpnMsgOrLinkToBrowser_BeforeR() throws Exception {
        // Before Android R, CaptivePortalLogin cannot call isAlwaysOnVpnLockdownEnabled() due to
        // permission denied. So CaptivePortalLogin doesn't know the status of VPN always-on, and it
        // simply provides a link for user to open the browser as usual.
        runVpnMsgOrLinkToBrowser(false /* useVpnMatcher */);
    }

    @Test @SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
    public void testVpnMsgOrLinkToBrowser() throws Exception {
        // After Android R(including), DevicePolicyManager allows the caller who has the
        // PERMISSION_MAINLINE_NETWORK_STACK can call the isAlwaysOnVpnLockdownEnabled() to get the
        // status of VPN always-on. So the CaptivePortalLogin could know the status of VPN always-on
        // and show the related warning message to the user.
        runVpnMsgOrLinkToBrowser(true /* useVpnMatcher */);
    }

    private void notifyCapabilitiesChanged(final NetworkCapabilities nc) {
        mActivityScenario.onActivity(a -> a.handleCapabilitiesChanged(mNetwork, nc));
        getInstrumentation().waitForIdleSync();
    }

    private void notifyValidatedChangedAndDismissed(final NetworkCapabilities nc) {
        // Get the MockCaptivePortal before the activity destroys itself
        final MockCaptivePortal cp = getCaptivePortal();
        notifyCapabilitiesChanged(nc);

        assertEquals(cp.mDismissTimes, 1);
        assertEquals(cp.mIgnoreTimes, 0);
        assertEquals(cp.mUseTimes, 0);
    }

    private void verifyNotDone() {
        final MockCaptivePortal cp = getCaptivePortal();
        assertEquals(cp.mDismissTimes, 0);
        assertEquals(cp.mIgnoreTimes, 0);
        assertEquals(cp.mUseTimes, 0);
    }

    private void notifyValidatedChangedNotDone(final NetworkCapabilities nc) {
        notifyCapabilitiesChanged(nc);
        verifyNotDone();
    }

    void waitForDestroyedState() throws Exception {
        final long startTimeMs = System.currentTimeMillis();
        long currentTimeMs = startTimeMs;
        while (mActivityScenario.getState() != DESTROYED
                && (currentTimeMs - startTimeMs) < TEST_TIMEOUT_MS) {
            Thread.sleep(50);
            currentTimeMs = System.currentTimeMillis();
        }
    }

    @Test @SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
    public void testNetworkCapabilitiesUpdate_RAndLater() throws Exception {
        initActivity(TEST_URL);
        // NetworkCapabilities updates w/o NET_CAPABILITY_VALIDATED.
        final NetworkCapabilities nc = new NetworkCapabilities();
        notifyValidatedChangedNotDone(nc);

        // NetworkCapabilities updates w/ NET_CAPABILITY_VALIDATED.
        nc.setCapability(NET_CAPABILITY_VALIDATED, true);
        notifyValidatedChangedAndDismissed(nc);

        // Workaround to deflake the test. The problem may be caused by a race with lock inside
        // InstrumentationActivityInvoker.
        // TODO: Remove it once https://github.com/android/android-test/issues/676 is fixed.
        waitForDestroyedState();
    }

    @Test @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.Q)
    public void testNetworkCapabilitiesUpdate_Q() throws Exception {
        initActivity(TEST_URL);
        final NetworkCapabilities nc = new NetworkCapabilities();
        nc.setCapability(NET_CAPABILITY_VALIDATED, true);
        // Auto-dismiss should not happen.
        notifyValidatedChangedNotDone(nc);
    }

    private HttpServer runCustomSchemeTest(String linkUri) throws Exception {
        final HttpServer server = new HttpServer();
        server.setResponseBody(TEST_URL_QUERY,
                "<a id='tst_link' href='" + linkUri + "'>Test link</a>");

        server.start();
        ActivityScenario.launch(RequestDismissKeyguardActivity.class);
        initActivity(server.makeUrl(TEST_URL_QUERY));
        // Mock all external intents
        intending(not(isInternal())).respondWith(new ActivityResult(RESULT_OK, null));

        onWebView().withElement(findElement(Locator.ID, "tst_link")).perform(webClick());
        getInstrumentation().waitForIdleSync();
        return server;
    }

    @Test
    public void testTelScheme() throws Exception {
        final String telUri = "tel:0123456789";
        final HttpServer server = runCustomSchemeTest(telUri);

        final Intent sentIntent = Intents.getIntents().get(0);
        assertEquals(Intent.ACTION_DIAL, sentIntent.getAction());
        assertEquals(Uri.parse(telUri), sentIntent.getData());

        server.stop();
    }

    @Test
    public void testSmsScheme() throws Exception {
        final String telUri = "sms:0123456789";
        final HttpServer server = runCustomSchemeTest(telUri);

        final Intent sentIntent = Intents.getIntents().get(0);
        assertEquals(Intent.ACTION_SENDTO, sentIntent.getAction());
        assertEquals(Uri.parse(telUri), sentIntent.getData());

        server.stop();
    }

    @Test
    public void testUnsupportedScheme() throws Exception {
        final HttpServer server = runCustomSchemeTest("mailto:test@example.com");
        assertEquals(0, Intents.getIntents().size());

        // Mockito intents cannot be used for an intent sent in onDestroy, due to
        // https://github.com/android/android-test/issues/1119
        final CompletableFuture<Intent> viewIntent = new CompletableFuture<>();
        mActivityScenario.onActivity(a -> a.mOpenInBrowserIntent.thenAccept(viewIntent::complete));

        final MockCaptivePortal cp = getCaptivePortal();
        onWebView().withElement(findElement(Locator.ID, "continue_link"))
                .perform(webClick());

        try {
            viewIntent.get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new AssertionError(
                    "Open in browser intent not received after " + TEST_TIMEOUT_MS + "ms", e);
        }

        getInstrumentation().waitForIdleSync();
        assertEquals(DESTROYED, mActivityScenario.getState());

        assertEquals(cp.mDismissTimes, 0);
        assertEquals(cp.mIgnoreTimes, 0);
        assertEquals(cp.mUseTimes, 1);
        server.stop();
    }

    @Test
    public void testDownload() throws Exception {
        // Setup the server with a single link on the portal page, leading to a download
        final HttpServer server = new HttpServer();
        final String linkIdDownload = "download";
        final String downloadQuery = "dl";
        final String filename = "testfile.png";
        final String mimetype = "image/png";
        server.setResponseBody(TEST_URL_QUERY,
                "<a id='" + linkIdDownload + "' href='?" + downloadQuery + "'>Download</a>");
        server.setResponse(downloadQuery, "This is a test file", mimetype, Collections.singletonMap(
                "Content-Disposition", "attachment; filename=\"" + filename + "\""));
        server.start();

        ActivityScenario.launch(RequestDismissKeyguardActivity.class);
        initActivity(server.makeUrl(TEST_URL_QUERY));

        // Create a mock file to be returned when mocking the file chooser
        final Intent mockFileResponse = new Intent();
        final Uri mockFile = Uri.parse("content://mockdata");
        mockFileResponse.setData(mockFile);

        // Mock file chooser and DownloadService intents
        intending(hasAction(ACTION_CREATE_DOCUMENT)).respondWith(
                new ActivityResult(RESULT_OK, mockFileResponse));
        // No intent fired yet
        assertEquals(0, Intents.getIntents().size());

        onWebView().withElement(findElement(Locator.ID, linkIdDownload))
                .perform(webClick());

        // The create file intent should be fired when the download starts
        assertTrue("Create file intent not received within timeout",
                isEventually(() -> Intents.getIntents().size() == 1, TEST_TIMEOUT_MS));

        final Intent fileIntent = Intents.getIntents().get(0);
        assertEquals(ACTION_CREATE_DOCUMENT, fileIntent.getAction());
        assertEquals(mimetype, fileIntent.getType());
        assertEquals(filename, fileIntent.getStringExtra(Intent.EXTRA_TITLE));

        // The download intent should be fired after the create file result is received
        final CompletableFuture<Intent> dlIntentFuture = new CompletableFuture<>();
        mActivityScenario.onActivity(a ->
                a.mForegroundServiceStart.thenAccept(dlIntentFuture::complete));

        final String expectedUrl = server.makeUrl(downloadQuery);
        verify(sDownloadServiceBinder, times(1)).requestDownload(eq(mNetwork),
                any() /* userAgent */, eq(expectedUrl), eq(filename),
                eq(mockFile), any() /* context */, eq(mimetype));

        server.stop();
    }

    @Test
    public void testVenueFriendlyNameTitle() throws Exception {
        assumeTrue(isAtLeastS());
        final LinkProperties linkProperties = new LinkProperties();
        CaptivePortalData.Builder captivePortalDataBuilder = new CaptivePortalData.Builder();
        // TODO: Use reflection for setVenueFriendlyName until shims are available
        final Class captivePortalDataBuilderClass = captivePortalDataBuilder.getClass();
        final Method setVenueFriendlyNameMethod;

        setVenueFriendlyNameMethod = captivePortalDataBuilderClass.getDeclaredMethod(
                "setVenueFriendlyName", CharSequence.class);

        captivePortalDataBuilder = (CaptivePortalData.Builder)
                setVenueFriendlyNameMethod.invoke(captivePortalDataBuilder, TEST_FRIENDLY_NAME);

        final CaptivePortalData captivePortalData = captivePortalDataBuilder.build();
        linkProperties.setCaptivePortalData(captivePortalData);

        when(sConnectivityManager.getLinkProperties(mNetwork)).thenReturn(linkProperties);
        configNonVpnNetwork();
        initActivity("https://tc.example.com/");

        // Verify that the correct venue friendly name is used
        mActivityScenario.onActivity(activity ->
                assertEquals(getInstrumentation().getContext().getString(R.string.action_bar_title,
                        TEST_FRIENDLY_NAME), activity.getActionBar().getTitle()));
    }

    @Test @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.Q)
    public void testWifiSsid_Q() throws Exception {
        configNonVpnNetwork();
        initActivity("https://portal.example.com/");
        mActivityScenario.onActivity(activity ->
                assertEquals(activity.getActionBar().getTitle(),
                        getInstrumentation().getContext().getString(R.string.action_bar_title,
                                TEST_WIFIINFO_SSID)));

    }

    @Test @SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
    public void testWifiSsid() throws Exception {
        configNonVpnNetwork();
        initActivity("https://portal.example.com/");
        mActivityScenario.onActivity(activity ->
                assertEquals(activity.getActionBar().getTitle(),
                        getInstrumentation().getContext().getString(R.string.action_bar_title,
                                TEST_NC_SSID)));
    }

    /**
     * Check whether the device release or development API level is strictly higher than the passed
     * in level.
     *
     * @return True if the device supports an SDK that has or will have a higher version number,
     *         even if still in development.
     */
    private static boolean isReleaseOrDevelopmentApiAbove(int apiLevel) {
        // In-development API after n may have SDK_INT == n and CODENAME != REL
        // Stable API n has SDK_INT == n and CODENAME == REL.
        final int devApiLevel = Build.VERSION.SDK_INT
                + ("REL".equals(Build.VERSION.CODENAME) ? 0 : 1);
        return devApiLevel > apiLevel;
    }

    /**
     * Check whether the device supports in-development or final S networking APIs.
     */
    private static boolean isAtLeastS() {
        return isReleaseOrDevelopmentApiAbove(Build.VERSION_CODES.R);
    }

    private static boolean isEventually(BooleanSupplier condition, long timeout)
            throws InterruptedException {
        final long start = System.currentTimeMillis();
        do {
            if (condition.getAsBoolean()) return true;
            Thread.sleep(10);
        } while ((System.currentTimeMillis() - start) < timeout);

        return false;
    }

    private static class HttpServer extends NanoHTTPD {
        private final ServerSocket mSocket;
        // Responses per URL query
        private final HashMap<String, MockResponse> mResponses = new HashMap<>();

        private static final class MockResponse {
            private final String mBody;
            private final String mMimetype;
            private final Map<String, String> mHeaders;

            MockResponse(String body, String mimetype, Map<String, String> headers) {
                this.mBody = body;
                this.mMimetype = mimetype;
                this.mHeaders = Collections.unmodifiableMap(new HashMap<>(headers));
            }
        }

        HttpServer() throws IOException {
            this(new ServerSocket());
        }

        private HttpServer(ServerSocket socket) {
            // 0 as port for picking a port automatically
            super(TEST_PORTAL_HOSTNAME, 0);
            mSocket = socket;
        }

        @Override
        public ServerSocketFactory getServerSocketFactory() {
            return () -> mSocket;
        }

        private String makeUrl(String query) {
            return new Uri.Builder()
                    .scheme("http")
                    .encodedAuthority(TEST_PORTAL_HOSTNAME + ":" + mSocket.getLocalPort())
                    // Explicitly specify an empty path to match the format of URLs returned by
                    // WebView (for example in onDownloadStart)
                    .path("/")
                    .query(query)
                    .build()
                    .toString();
        }

        private void setResponseBody(String query, String body) {
            setResponse(query, body, NanoHTTPD.MIME_HTML, Collections.emptyMap());
        }

        private void setResponse(String query, String body, String mimetype,
                Map<String, String> headers) {
            mResponses.put(query, new MockResponse(body, mimetype, headers));
        }

        @Override
        public Response serve(IHTTPSession session) {
            final MockResponse mockResponse = mResponses.get(session.getQueryParameterString());
            if (mockResponse == null) {
                // Default response is a 404
                return super.serve(session);
            }

            final Response response = newFixedLengthResponse(Response.Status.OK,
                    mockResponse.mMimetype,
                    "<!doctype html>"
                    + "<html>"
                    + "<head><title>Test portal</title></head>"
                    + "<body>" + mockResponse.mBody + "</body>"
                    + "</html>");
            mockResponse.mHeaders.forEach(response::addHeader);
            return response;
        }
    }

    private HttpServer prepareTestDirectlyOpen(String linkIdDownload, String downloadQuery,
            String filename, String mimetype) throws Exception {
        // Setup the server with a single link on the portal page, leading to a download
        final HttpServer server = new HttpServer();
        server.setResponseBody(TEST_URL_QUERY,
                "<a id='" + linkIdDownload + "' href='?" + downloadQuery + "'>Download</a>");
        server.setResponse(downloadQuery, "This is a test file", mimetype, Collections.singletonMap(
                "Content-Disposition", "attachment; filename=\"" + filename + "\""));
        server.start();

        ActivityScenario.launch(RequestDismissKeyguardActivity.class);
        initActivity(server.makeUrl(TEST_URL_QUERY));
        return server;
    }

    private UiObject getUiSpinner() {
        final String resourceId = getInstrumentation().getContext().getResources()
                .getResourceName(R.id.download_in_progress);
        final UiSelector selector = new UiSelector().resourceId(resourceId);
        return UiDevice.getInstance(getInstrumentation()).findObject(selector);
    }

    private CompletableFuture<Boolean> initToastListener(String expectedMsg) {
        final CompletableFuture<Boolean> messageFuture = new CompletableFuture<>();
        getInstrumentation().getUiAutomation().setOnAccessibilityEventListener(
                new UiAutomation.OnAccessibilityEventListener() {
                    @Override
                    public void onAccessibilityEvent(AccessibilityEvent event) {
                        // Toast is contained in notification state change. Ignore other types.
                        if (event.getEventType() != TYPE_NOTIFICATION_STATE_CHANGED) {
                            return;
                        }

                        // Skip empty text events.
                        if (event.getText().size() == 0) return;

                        final String msg = (String) event.getText().get(0);
                        // The event class name in older SDK platform will be
                        // "android.widget.Toast$TN" instead of "android.widget.Toast".
                        if (event.getClassName().toString().contains(Toast.class.getName())
                                && expectedMsg.equals(msg)) {
                            messageFuture.complete(true);
                        }
                    }
                });
        return messageFuture;
    }

    @Test
    public void testDirectlyOpen_onCreateDeleteFile() throws Exception {
        final String linkIdDownload = "download";
        final HttpServer server = prepareTestDirectlyOpen(linkIdDownload, "dl",
                "test.wificonfig", TEST_WIFI_CONFIG_TYPE);
        final UiObject spinner = getUiSpinner();
        final File downloadPath = new File(getInstrumentation().getContext().getFilesDir(),
                CaptivePortalLoginActivity.FILE_PROVIDER_DOWNLOAD_PATH);

        assertNull(downloadPath.listFiles());

        onWebView().withElement(findElement(Locator.ID, linkIdDownload)).perform(webClick());
        assertTrue(spinner.waitForExists(TEST_TIMEOUT_MS));

        // The download file should be created.
        assertNotNull(downloadPath.listFiles());

        mActivityScenario.recreate();
        // OnCreate should clean the previous created files.
        assertNull(downloadPath.listFiles());
        server.stop();
    }

    @Test
    public void testDirectlyOpen_onDownloadAborted() throws Exception {
        initActivity(TEST_URL);
        final Uri mockFile = Uri.parse("content://mockdata");
        final String expectMsg = getInstrumentation().getContext().getString(
                R.string.file_too_large_cancel_download);
        final CompletableFuture<Boolean> toastFuture = initToastListener(expectMsg);

        mActivityScenario.onActivity(a -> a.mProgressCallback.onDownloadAborted(
                1, DOWNLOAD_ABORTED_REASON_FILE_TOO_LARGE));

        assertTrue(toastFuture.get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));

    }

    @Test
    public void testDirectlyOpen_taskCancelToast() throws Exception {
        final String linkIdDownload = "download";
        final String expectMsg = getInstrumentation().getContext().getString(
                R.string.cancel_pending_downloads);

        final HttpServer server = prepareTestDirectlyOpen(linkIdDownload, "dl",
                "test.wificonfig", TEST_WIFI_CONFIG_TYPE);
        onWebView().withElement(findElement(Locator.ID, linkIdDownload)).perform(webClick());

        final UiObject spinner = getUiSpinner();
        // Expect to see the spinner
        assertTrue(spinner.waitForExists(TEST_TIMEOUT_MS));
        final CompletableFuture<Boolean> toastFuture = initToastListener(expectMsg);
        mActivityScenario.onActivity(a -> a.cancelPendingTask());
        assertTrue(toastFuture.get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        server.stop();
    }

    @Test
    public void testDirectlyOpen_cancelPendingTask() throws Exception {
        final String linkIdDownload = "download";
        final String downloadQuery = "dl";
        final String filename = "test.wificonfig";
        final String mimeType = TEST_WIFI_CONFIG_TYPE;
        final int requestId = 123;
        final HttpServer server = prepareTestDirectlyOpen(linkIdDownload, downloadQuery,
                filename, mimeType);

        final UiObject spinner = getUiSpinner();
        // Verify no spinner first.
        assertFalse(spinner.exists());
        doReturn(requestId).when(sDownloadServiceBinder)
                .requestDownload(any(), any(), any(), any(), any(), any(), eq(mimeType));
        onWebView().withElement(findElement(Locator.ID, linkIdDownload)).perform(webClick());
        // Expect to see the spinner
        assertTrue(spinner.waitForExists(TEST_TIMEOUT_MS));
        final ArgumentCaptor<Uri> outfileCaptor = ArgumentCaptor.forClass(Uri.class);
        verify(sDownloadServiceBinder).requestDownload(any(), any(),
                eq(server.makeUrl(downloadQuery)),
                eq(filename),
                outfileCaptor.capture(), any(), eq(mimeType));

        // Cancel pending task.
        mActivityScenario.onActivity(a -> a.cancelPendingTask());
        verify(sDownloadServiceBinder).cancelTask(anyInt());
        // Callback with target task should hide the spinner.
        mActivityScenario.onActivity(a -> a.mProgressCallback.onDownloadComplete(
                outfileCaptor.getValue(), mimeType, requestId, false));
        assertTrue(spinner.waitUntilGone(TEST_TIMEOUT_MS));

        server.stop();
    }

    @Test
    public void testDirectlyOpen_successfullyDownload() throws Exception {
        final String linkIdDownload = "download";
        final String mimeType = TEST_WIFI_CONFIG_TYPE;
        final String filename = "test.wificonfig";
        final Uri mockFile = Uri.parse("content://mockdata");
        final Uri otherFile = Uri.parse("content://otherdata");
        final int downloadId = 123;
        final HttpServer server = prepareTestDirectlyOpen(linkIdDownload, "dl",
                filename, mimeType);

        final UiObject spinner = getUiSpinner();
        // Verify no spinner first.
        assertFalse(spinner.exists());

        onWebView().withElement(findElement(Locator.ID, linkIdDownload)).perform(webClick());

        // Expect to see the spinner
        assertTrue(spinner.waitForExists(TEST_TIMEOUT_MS));
        // File does not start a create file intent, i.e. no file picker
        assertEquals(0, Intents.getIntents().size());
        // Trigger callback with negative result with other undesired other download file.
        mActivityScenario.onActivity(a ->
                a.mProgressCallback.onDownloadComplete(otherFile, mimeType, downloadId, false));
        // Verify spinner is still visible and no intent to open the target file.
        assertTrue(spinner.exists());
        assertEquals(0, Intents.getIntents().size());

        // Trigger callback with positive result
        mActivityScenario.onActivity(a -> a.mProgressCallback.onDownloadComplete(
                mockFile, mimeType, downloadId, true));
        // Verify intent sent to open the target file
        final Intent sentIntent = Intents.getIntents().get(0);
        assertEquals(Intent.ACTION_VIEW, sentIntent.getAction());
        assertEquals(mimeType, sentIntent.getType());
        assertEquals(mockFile, sentIntent.getData());
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION, sentIntent.getFlags());
        // Spinner should become invisible.
        assertTrue(spinner.waitUntilGone(TEST_TIMEOUT_MS));

        server.stop();
    }
}
