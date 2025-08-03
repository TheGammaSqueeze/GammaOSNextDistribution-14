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

package com.android.cts.delegate;

import static com.android.cts.delegate.DelegateTestUtils.assertExpectException;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.fail;

import android.app.admin.ConnectEvent;
import android.app.admin.DnsEvent;
import android.app.admin.NetworkEvent;
import android.util.Log;

import com.google.common.truth.Truth;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class WorkProfileNetworkLoggingDelegateTest extends BaseTestCase{

    private static final String TAG = "WorkProfileNetworkLoggingDelegateTest";
    private static final String CTS_APP_PACKAGE_NAME = "com.android.cts.delegate";

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

    @Before
    public void setUp() {
        DelegateTestUtils.DelegatedLogsReceiver.sBatchCountDown = new CountDownLatch(1);
    }

    @Test
    public void testCannotAccessApis() {
        assertExpectException(SecurityException.class, null,
                () -> mDpm.isNetworkLoggingEnabled(null));

        assertExpectException(SecurityException.class, null,
                () -> mDpm.setNetworkLoggingEnabled(null, true));

        assertExpectException(SecurityException.class, null,
                () -> mDpm.retrieveNetworkLogs(null, 0));
    }

    @Test
    public void testSetNetworkLogsEnabled_true() {
        mDpm.setNetworkLoggingEnabled(null, true);

        assertThat(mDpm.isNetworkLoggingEnabled(null)).isTrue();
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
        mDevice.executeShellCommand("dpm force-network-logs");
        DelegateTestUtils.DelegatedLogsReceiver.waitForBroadcast();

        verifyNetworkLogs(DelegateTestUtils.DelegatedLogsReceiver.getNetworkEvents());
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
        mDpm.setNetworkLoggingEnabled(null, false);

        assertThat(mDpm.isNetworkLoggingEnabled(null)).isFalse();
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
}
