/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.cts.deviceowner;

import static com.android.bedstead.dpmwrapper.TestAppHelper.registerTestCaseReceiver;
import static com.android.bedstead.dpmwrapper.TestAppHelper.unregisterTestCaseReceiver;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.admin.ConnectEvent;
import android.app.admin.DnsEvent;
import android.app.admin.NetworkEvent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Parcel;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class NetworkLoggingTest extends BaseDeviceOwnerTest {

    private static final String TAG = "NetworkLoggingTest";
    private static final boolean VERBOSE = false;

    private static final String ARG_BATCH_COUNT = "batchCount";
    private static final int FAKE_BATCH_TOKEN = -666; // real batch tokens are always non-negative
    private static final int FULL_LOG_BATCH_SIZE = 1200;
    private static final String CTS_APP_PACKAGE_NAME = "com.android.cts.deviceowner";
    private static final int MAX_IP_ADDRESSES_LOGGED = 10;

    private static final int CONNECTION_TIMEOUT_MS = 2_000;

    private static final int TIMEOUT_PER_BATCH_MS = 3 * 60_000; // 3 minutes

    /** How often events should be logged, when {@link #VERBOSE} is {@code false}. */
    private static final int LOGGING_FREQUENCY = FULL_LOG_BATCH_SIZE / 4;

    private static final String[] NOT_LOGGED_URLS_LIST = {
            "wikipedia.org",
            "google.pl"
    };

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

    private final BroadcastReceiver mNetworkLogsReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!BasicAdminReceiver.ACTION_NETWORK_LOGS_AVAILABLE.equals(intent.getAction())) {
                Log.w(TAG, "Received unexpected intent: " + intent);
                return;
            }
            final long token =
                    intent.getLongExtra(BasicAdminReceiver.EXTRA_NETWORK_LOGS_BATCH_TOKEN,
                            FAKE_BATCH_TOKEN);
            final long latchCount = mBatchCountDown.getCount();
            Log.d(TAG, "Received " + intent + ": token=" + token + ", latch= " + latchCount);
            // Retrieve network logs.
            final List<NetworkEvent> events = mDevicePolicyManager.retrieveNetworkLogs(getWho(),
                    token);
            Log.d(TAG, "Number of events: " + events.size());
            if (VERBOSE) Log.v(TAG, "Events: " + events);
            if (events == null) {
                fail("Failed to retrieve batch of network logs with batch token " + token);
                return;
            }
            if (latchCount > 0) {
                mNetworkEvents.addAll(events);
            } else {
                Log.e(TAG, "didn't receive any event");
            }
            Log.d(TAG, "Counting down latch");
            mBatchCountDown.countDown();
        }
    };

    private CountDownLatch mBatchCountDown;
    private final ArrayList<NetworkEvent> mNetworkEvents = new ArrayList<>();
    private int mBatchesRequested = 1;

    @Override
    protected void tearDown() throws Exception {
        // NOTE: if this was a "pure" device-side test, it should not throw an exception on
        // tearDown()
        setNetworkLoggingEnabled(false);

        super.tearDown();
    }

    /**
     * Test: retrieving network logs can only be done if there's one user on the device or all
     * secondary users / profiles are affiliated.
     */
    public void testRetrievingNetworkLogsThrowsSecurityException() {
        setNetworkLoggingEnabled(true);

        try {
            mDevicePolicyManager.retrieveNetworkLogs(getWho(), FAKE_BATCH_TOKEN);
            fail("did not throw expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Test: when a wrong batch token id (not a token of the current batch) is provided, null should
     * be returned.
     */
    public void testProvidingWrongBatchTokenReturnsNull() {
        setNetworkLoggingEnabled(true);

        assertNull(mDevicePolicyManager.retrieveNetworkLogs(getWho(), FAKE_BATCH_TOKEN));
    }

    /**
     * Test: test that the actual logging happens when the network logging is enabled and doesn't
     * happen before it's enabled; for this test to work we need to generate enough internet
     * traffic, so that the batch of logs is created
     */
    public void testNetworkLoggingAndRetrieval() throws Exception {
        mBatchesRequested =
                Integer.parseInt(
                        InstrumentationRegistry.getArguments().getString(ARG_BATCH_COUNT, "1"));
        Log.d(TAG, "batches requested:" + mBatchesRequested);
        mBatchCountDown = new CountDownLatch(mBatchesRequested);
        // register a receiver that listens for DeviceAdminReceiver#onNetworkLogsAvailable()
        final IntentFilter filterNetworkLogsAvailable = new IntentFilter(
                BasicAdminReceiver.ACTION_NETWORK_LOGS_AVAILABLE);

        registerTestCaseReceiver(mContext, mNetworkLogsReceiver, filterNetworkLogsAvailable);

        // visit websites that shouldn't be logged as network logging isn't enabled yet
        for (int i = 0; i < NOT_LOGGED_URLS_LIST.length; i++) {
            connectToWebsite(NOT_LOGGED_URLS_LIST[i], shouldLog(i));
        }

        // enable network logging and start the logging scenario
        setNetworkLoggingEnabled(true);

        // TODO: here test that facts about logging are shown in the UI

        // Fetch and verify the batches of events.
        generateBatches();
    }

    private boolean shouldLog(int sample) {
        return sample % LOGGING_FREQUENCY == 0;
    }

    private void generateBatches() throws Exception {
        // visit websites to verify their dns lookups are logged
        for (int i = 0; i < LOGGED_URLS_LIST.length; i++) {
            connectToWebsite(LOGGED_URLS_LIST[i], shouldLog(i));
        }

        // generate enough traffic to fill the batches.
        int fakeReqNo = 0;
        for (int i = 0; i < mBatchesRequested; i++) {
            fakeReqNo += generateFakeTraffic();
        }

        // if DeviceAdminReceiver#onNetworkLogsAvailable() hasn't been triggered yet, wait for up to
        // 3 minutes per batch just in case
        final int timeoutMs = TIMEOUT_PER_BATCH_MS * mBatchesRequested;
        Log.d(TAG, "Waiting up to " + timeoutMs + "ms for " + mBatchesRequested + " batches");
        if (!mBatchCountDown.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            Log.e(TAG, "Timed out!");
        }

        unregisterTestCaseReceiver(mContext, mNetworkLogsReceiver);

        if (mBatchCountDown.getCount() > 0) {
            fail("Generated events for " + mBatchesRequested + " batches and waited for "
                    + timeoutMs + " ms, but still didn't get"
                    + " DeviceAdminReceiver#onNetworkLogsAvailable() callback");
        }

        // Verify network logs.
        assertWithMessage("network events").that(mNetworkEvents).isNotEmpty();
        assertWithMessage("first event id").that(mNetworkEvents.get(0).getId()).isEqualTo(0L);
        // For each of the real URLs we have two events: one DNS and one connect. Fake requests
        // don't require DNS queries.
        final int eventsExpected =
                Math.min(FULL_LOG_BATCH_SIZE * mBatchesRequested,
                        2 * LOGGED_URLS_LIST.length + fakeReqNo);
        verifyNetworkLogs(mNetworkEvents, eventsExpected);
    }

    private void verifyDnsEvent(DnsEvent dnsEvent) {
        // Verify that we didn't log a hostname lookup when network logging was disabled.
        if (dnsEvent.getHostname().contains(NOT_LOGGED_URLS_LIST[0])
                || dnsEvent.getHostname().contains(NOT_LOGGED_URLS_LIST[1])) {
            fail("A hostname that was looked-up when network logging was disabled"
                    + " was logged.");
        }

        // Verify that as many IP addresses were logged as were reported (max 10).
        final List<InetAddress> ips = dnsEvent.getInetAddresses();
        assertThat(ips.size()).isAtMost(MAX_IP_ADDRESSES_LOGGED);
        final int expectedAddressCount = Math.min(MAX_IP_ADDRESSES_LOGGED,
                dnsEvent.getTotalResolvedAddressCount());
        assertThat(expectedAddressCount).isEqualTo(ips.size());

        // Verify the IP addresses are valid IPv4 or IPv6 addresses.
        for (final InetAddress ipAddress : ips) {
            assertTrue(isIpv4OrIpv6Address(ipAddress));
        }

        //Verify writeToParcel.
        Parcel parcel = Parcel.obtain();
        try {
            dnsEvent.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            final DnsEvent dnsEventOut = DnsEvent.CREATOR.createFromParcel(parcel);
            assertThat(dnsEventOut).isNotNull();
            verifyDnsEventsEqual(dnsEvent, dnsEventOut);
        } finally {
            parcel.recycle();
        }
    }

    private void verifyDnsEventsEqual(DnsEvent event1, DnsEvent event2) {
        assertThat(event1.getHostname()).isEqualTo(event2.getHostname());
        assertThat(new HashSet<InetAddress>(event1.getInetAddresses())).isEqualTo(
                        new HashSet<InetAddress>(event2.getInetAddresses()));
        assertThat(event1.getTotalResolvedAddressCount()).isEqualTo(
                event2.getTotalResolvedAddressCount());
        assertThat(event1.getPackageName()).isEqualTo(event2.getPackageName());
        assertThat(event1.getTimestamp()).isEqualTo(event2.getTimestamp());
        assertThat(event1.getId()).isEqualTo(event2.getId());
    }

    private void verifyConnectEvent(ConnectEvent connectEvent) {
        // Verify the IP address is a valid IPv4 or IPv6 address.
        final InetAddress ip = connectEvent.getInetAddress();
        assertThat(isIpv4OrIpv6Address(ip)).isTrue();

        // Verify that the port is a valid port.
        assertThat(connectEvent.getPort()).isAtLeast(0);
        assertThat(connectEvent.getPort()).isAtMost(65535);

        // Verify writeToParcel.
        Parcel parcel = Parcel.obtain();
        try {
            connectEvent.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            final ConnectEvent connectEventOut = ConnectEvent.CREATOR.createFromParcel(parcel);
            assertThat(connectEventOut).isNotNull();
            verifyConnectEventsEqual(connectEvent, connectEventOut);
        } finally {
             parcel.recycle();
        }
    }

    private void verifyConnectEventsEqual(ConnectEvent event1, ConnectEvent event2) {
        assertThat(event1.getInetAddress()).isEqualTo(event2.getInetAddress());
        assertThat(event1.getPort()).isEqualTo(event2.getPort());
        assertThat(event1.getPackageName()).isEqualTo(event2.getPackageName());
        assertThat(event1.getTimestamp()).isEqualTo(event2.getTimestamp());
        assertThat(event1.getId()).isEqualTo(event2.getId());
    }

    private void verifyNetworkLogs(List<NetworkEvent> networkEvents, int eventsExpected) {
        Log.d(TAG, "verifyNetworkLogs(): expected " + eventsExpected + ", got "
                + ((networkEvents == null) ? "null" : String.valueOf(networkEvents.size())));
        // allow a batch to be slightly smaller or larger.
        assertTrue(Math.abs(eventsExpected - networkEvents.size()) <= 150);
        int ctsPackageNameCounter = 0;
        // allow a small down margin for verification, to avoid flakiness
        final int eventsExpectedWithMargin = eventsExpected - 50;
        final boolean[] visited = new boolean[LOGGED_URLS_LIST.length];

        for (int i = 0; i < networkEvents.size(); i++) {
            final NetworkEvent currentEvent = networkEvents.get(i);
            // verify that the events are in chronological order
            if (i > 0) {
                assertTrue(currentEvent.getTimestamp() >= networkEvents.get(i - 1).getTimestamp());
            }
            // verify that the event IDs are monotonically increasing
            if (i > 0) {
                assertTrue(currentEvent.getId() == (networkEvents.get(i - 1).getId() + 1));
            }
            // count how many events come from the CTS app
            if (CTS_APP_PACKAGE_NAME.equals(currentEvent.getPackageName())) {
                ctsPackageNameCounter++;
                if (currentEvent instanceof DnsEvent) {
                    final DnsEvent dnsEvent = (DnsEvent) currentEvent;
                    // Mark which addresses from LOGGED_URLS_LIST were visited.
                    for (int j = 0; j < LOGGED_URLS_LIST.length; j++) {
                        if (dnsEvent.getHostname().contains(LOGGED_URLS_LIST[j])) {
                            visited[j] = true;
                            break;
                        }
                    }

                    verifyDnsEvent(dnsEvent);
                } else if (currentEvent instanceof ConnectEvent) {
                    final ConnectEvent connectEvent = (ConnectEvent) currentEvent;
                    verifyConnectEvent(connectEvent);
                } else {
                    fail("An unknown NetworkEvent type logged: "
                            + currentEvent.getClass().getName());
                }
            }
        }

        // verify that each hostname from LOGGED_URLS_LIST was looked-up
        for (int i = 0; i < 10; i++) {
            assertTrue(LOGGED_URLS_LIST[i] + " wasn't visited", visited[i]);
        }
        // verify that sufficient iterations done by the CTS app were logged
        assertTrue(ctsPackageNameCounter >= eventsExpectedWithMargin);
    }

    private void connectToWebsite(String server, boolean shouldLog) {
        HttpURLConnection urlConnection = null;
        try {
            final URL url = new URL("http://" + server);
            if (shouldLog || VERBOSE) {
                Log.d(TAG, "Connecting to " + server + " with " + CONNECTION_TIMEOUT_MS
                        + "ms timeout");
            }
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
            urlConnection.setReadTimeout(CONNECTION_TIMEOUT_MS);
            final int responseCode = urlConnection.getResponseCode();
            if (shouldLog || VERBOSE) {
                Log.d(TAG, "Got response code: " + responseCode);
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to connect to " + server, e);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    /** Quickly generate loads of events by repeatedly connecting to a local server. */
    private int generateFakeTraffic() throws IOException, InterruptedException {
        final ServerSocket serverSocket = new ServerSocket(0);
        final Thread serverThread = startFakeServer(serverSocket);

        final int reqNo = makeFakeRequests(serverSocket.getLocalPort());

        serverSocket.close();
        serverThread.join();

        return reqNo;
    }

    private int makeFakeRequests(int port) {
        int reqNo;
        final String FAKE_SERVER = "127.0.0.1:" + port;
        Log.d(TAG, "Making a fake request to " + FAKE_SERVER);
        for (reqNo = 0; reqNo < FULL_LOG_BATCH_SIZE && mBatchCountDown.getCount() > 0; reqNo++) {
            connectToWebsite(FAKE_SERVER, shouldLog(reqNo));
            // Just to prevent choking the server.
            sleep(10);
        }
        Log.d(TAG, "Returning reqNo=" + reqNo);
        return reqNo;
    }

    private Thread startFakeServer(ServerSocket serverSocket) throws InterruptedException {
        final Thread serverThread = new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    final Socket socket = serverSocket.accept();
                    // Consume input.
                    final BufferedReader input =
                            new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String line;
                    do {
                        line = input.readLine();
                    } while (line != null && !line.equals(""));
                    // Return minimum valid response.
                    final PrintStream output = new PrintStream(socket.getOutputStream());
                    output.println("HTTP/1.0 200 OK");
                    output.println("Content-Length: 0");
                    output.println();
                    output.flush();
                    output.close();
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        Log.w(TAG, "Failed to serve connection", e);
                    }
                }
            }
            Log.i(TAG, "Fake server closed");
        }, "FakeServerThread");
        Log.i(TAG, "starting a fake server (" + serverSocket + ") on thread " + serverThread);
        serverThread.start();

        // Allow the server to start accepting.
        sleep(1_000);

        return serverThread;
    }

    private boolean isIpv4OrIpv6Address(InetAddress addr) {
        return ((addr instanceof Inet4Address) || (addr instanceof Inet6Address));
    }

    private void sleep(int timeMs) {
        if (VERBOSE) Log.v(TAG, "Sleeping for " + timeMs + "ms");
        SystemClock.sleep(timeMs);
        if (VERBOSE) Log.v(TAG, "Woke up");
    }

    private void setNetworkLoggingEnabled(boolean enabled) {
        ComponentName admin = getWho();
        Log.d(TAG, "Calling setNetworkLoggingEnabled(" + enabled + ") for " + admin);
        mDevicePolicyManager.setNetworkLoggingEnabled(admin, enabled);
        boolean reallyEnabled = mDevicePolicyManager.isNetworkLoggingEnabled(admin);
        Log.d(TAG, "getNetworkLoggingEnabled() result:" + reallyEnabled);
        assertWithMessage("network logging enabled for %s", admin).that(reallyEnabled)
                .isEqualTo(enabled);
    }
}
