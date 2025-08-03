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

package com.android.cts.deviceandprofileowner;

import static com.android.cts.deviceandprofileowner.BaseDeviceAdminTest.ADMIN_RECEIVER_COMPONENT;
import static com.android.cts.deviceandprofileowner.BaseDeviceAdminTest.BasicAdminReceiver.ACTION_NETWORK_LOGS_AVAILABLE;
import static com.android.cts.deviceandprofileowner.BaseDeviceAdminTest.BasicAdminReceiver.EXTRA_NETWORK_LOGS_BATCH_TOKEN;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.fail;

import android.app.admin.ConnectEvent;
import android.app.admin.DevicePolicyManager;
import android.app.admin.DnsEvent;
import android.app.admin.NetworkEvent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.google.common.truth.Truth;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class NetworkLoggingTest {

    private static final String TAG = "NetworkLoggingTest";
    private static final String CTS_APP_PACKAGE_NAME = "com.android.cts.deviceandprofileowner";
    private static final int FAKE_BATCH_TOKEN = -666;
    private static final String DELEGATE_APP_PKG = "com.android.cts.delegate";
    private static final String DELEGATION_NETWORK_LOGGING = "delegation-network-logging";

    // Should not be added to the list of network events.
    private static final String[] NOT_LOGGED_URLS_LIST = {
            "wikipedia.org",
            "wikipedia.com",
            "google.pl",
    };

    // Should be added to the list of network events.
    private static final String[] LOGGED_URLS_LIST = {
            "example.com",
            "example.net",
            "example.org",
            "example.edu",
            "ipv6.google.com",
            "google.co.jp",
            "google.fr",
            "google.com.br",
            "google.com.tr",
            "google.co.uk",
            "google.de"
    };

    private final ArrayList<NetworkEvent> mNetworkEvents = new ArrayList<>();
    private final NetworkLogsReceiver mReceiver = new NetworkLogsReceiver();
    private Context mContext;
    private DevicePolicyManager mDpm;
    private UiDevice mDevice;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getContext();
        mDpm = mContext.getSystemService(DevicePolicyManager.class);
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    @Test
    public void testSetNetworkLogsEnabled_true() {
        mDpm.setNetworkLoggingEnabled(ADMIN_RECEIVER_COMPONENT, true);

        assertThat(mDpm.isNetworkLoggingEnabled(ADMIN_RECEIVER_COMPONENT)).isTrue();
    }

    @Test
    public void testConnectToWebsites_shouldBeLogged() {
        for (final String url : LOGGED_URLS_LIST) {
            connectToWebsite(url);
        }
    }

    @Test
    public void testConnectToWebsites_shouldNotBeLogged() {
        for (final String url : NOT_LOGGED_URLS_LIST) {
            connectToWebsite(url);
        }
    }

    @Test
    public void testRetrieveNetworkLogs_forceNetworkLogs_receiveNetworkLogs() throws Exception {
        mContext.registerReceiver(mReceiver, new IntentFilter(ACTION_NETWORK_LOGS_AVAILABLE),
                Context.RECEIVER_EXPORTED_UNAUDITED);
        mDevice.executeShellCommand("dpm force-network-logs");
        mReceiver.waitForBroadcast();

        verifyNetworkLogs(mNetworkEvents);

        mContext.unregisterReceiver(mReceiver);
    }

    private void verifyNetworkLogs(List<NetworkEvent> networkEvents) {
        int receivedEventsFromLoggedUrlsList = 0;

        for (final NetworkEvent currentEvent : networkEvents) {
            if (CTS_APP_PACKAGE_NAME.equals(currentEvent.getPackageName())) {
                if (currentEvent instanceof DnsEvent) {
                    final DnsEvent dnsEvent = (DnsEvent) currentEvent;
                    if (Arrays.asList(LOGGED_URLS_LIST).contains(dnsEvent.getHostname())) {
                        receivedEventsFromLoggedUrlsList++;
                    // Verify all hostnames looked-up from the personal profile were not logged.
                    } else {
                        Truth.assertWithMessage("A hostname that was looked-up from "
                                + "the personal profile was logged.")
                                .that(Arrays.asList(NOT_LOGGED_URLS_LIST))
                                .doesNotContain(dnsEvent.getHostname());
                    }

                } else if (currentEvent instanceof ConnectEvent) {
                    final ConnectEvent connectEvent = (ConnectEvent) currentEvent;
                    final InetAddress ip = connectEvent.getInetAddress();
                    assertThat(isIpv4OrIpv6Address(ip)).isTrue();

                } else {
                    fail("An unknown NetworkEvent type logged: "
                            + currentEvent.getClass().getName());
                }
            }
        }
        assertThat(receivedEventsFromLoggedUrlsList).isEqualTo(LOGGED_URLS_LIST.length);
    }

    private boolean isIpv4OrIpv6Address(InetAddress addr) {
        return ((addr instanceof Inet4Address) || (addr instanceof Inet6Address));
    }

    @Test
    public void testSetNetworkLogsEnabled_false() {
        mDpm.setNetworkLoggingEnabled(ADMIN_RECEIVER_COMPONENT, false);

        assertThat(mDpm.isNetworkLoggingEnabled(ADMIN_RECEIVER_COMPONENT)).isFalse();
    }

    @Test
    public void testSetDelegateScope_delegationNetworkLogging() {
        setDelegatedScopes(DELEGATE_APP_PKG, Arrays.asList(DELEGATION_NETWORK_LOGGING));

        assertThat(mDpm.getDelegatedScopes(ADMIN_RECEIVER_COMPONENT, DELEGATE_APP_PKG))
                .contains(DELEGATION_NETWORK_LOGGING);
    }

    @Test
    public void testSetDelegateScope_noDelegation() {
        setDelegatedScopes(DELEGATE_APP_PKG, Arrays.asList());

        assertThat(mDpm.getDelegatedScopes(ADMIN_RECEIVER_COMPONENT, DELEGATE_APP_PKG))
                .doesNotContain(DELEGATION_NETWORK_LOGGING);
    }

    private void connectToWebsite(String server) {
        HttpURLConnection urlConnection = null;
        try {
            final URL url = new URL("https://" + server);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setConnectTimeout(2000);
            urlConnection.setReadTimeout(2000);
            urlConnection.getResponseCode();
        } catch (IOException e) {
            Log.w(TAG, "Failed to connect to " + server, e);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    protected void setDelegatedScopes(String delegatePackage, List<String> scopes) {
        Log.v(TAG, "Calling setDelegatedScopes(" + ADMIN_RECEIVER_COMPONENT.flattenToShortString()
                + ", " + delegatePackage + ", " + scopes + ") using " + mDpm);
        mDpm.setDelegatedScopes(ADMIN_RECEIVER_COMPONENT, delegatePackage, scopes);
    }

    private class NetworkLogsReceiver extends BroadcastReceiver {

        private final CountDownLatch mBatchCountDown = new CountDownLatch(1);

        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_NETWORK_LOGS_AVAILABLE.equals(intent.getAction())) {
                final long token =
                        intent.getLongExtra(EXTRA_NETWORK_LOGS_BATCH_TOKEN, FAKE_BATCH_TOKEN);
                final List<NetworkEvent> events =
                        mDpm.retrieveNetworkLogs(ADMIN_RECEIVER_COMPONENT, token);
                if (events == null) {
                    fail("Failed to retrieve batch of network logs with batch token " + token);
                } else {
                    mNetworkEvents.addAll(events);
                    mBatchCountDown.countDown();
                }
            }
        }

        private void waitForBroadcast() throws InterruptedException {
            mReceiver.mBatchCountDown.await(3, TimeUnit.MINUTES);
            if (mReceiver.mBatchCountDown.getCount() > 0) {
                fail("Did not get DeviceAdminReceiver#onNetworkLogsAvailable callback");
            }
        }
    }
}
