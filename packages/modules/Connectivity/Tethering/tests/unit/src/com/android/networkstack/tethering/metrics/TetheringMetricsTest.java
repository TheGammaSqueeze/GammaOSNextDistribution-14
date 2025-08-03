/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.networkstack.tethering.metrics;

import static android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_ETHERNET;
import static android.net.NetworkCapabilities.TRANSPORT_LOWPAN;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI_AWARE;
import static android.net.TetheringManager.TETHERING_BLUETOOTH;
import static android.net.TetheringManager.TETHERING_ETHERNET;
import static android.net.TetheringManager.TETHERING_NCM;
import static android.net.TetheringManager.TETHERING_USB;
import static android.net.TetheringManager.TETHERING_WIFI;
import static android.net.TetheringManager.TETHERING_WIFI_P2P;
import static android.net.TetheringManager.TETHER_ERROR_DHCPSERVER_ERROR;
import static android.net.TetheringManager.TETHER_ERROR_DISABLE_FORWARDING_ERROR;
import static android.net.TetheringManager.TETHER_ERROR_ENABLE_FORWARDING_ERROR;
import static android.net.TetheringManager.TETHER_ERROR_ENTITLEMENT_UNKNOWN;
import static android.net.TetheringManager.TETHER_ERROR_IFACE_CFG_ERROR;
import static android.net.TetheringManager.TETHER_ERROR_INTERNAL_ERROR;
import static android.net.TetheringManager.TETHER_ERROR_NO_ACCESS_TETHERING_PERMISSION;
import static android.net.TetheringManager.TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION;
import static android.net.TetheringManager.TETHER_ERROR_NO_ERROR;
import static android.net.TetheringManager.TETHER_ERROR_PROVISIONING_FAILED;
import static android.net.TetheringManager.TETHER_ERROR_SERVICE_UNAVAIL;
import static android.net.TetheringManager.TETHER_ERROR_TETHER_IFACE_ERROR;
import static android.net.TetheringManager.TETHER_ERROR_UNAVAIL_IFACE;
import static android.net.TetheringManager.TETHER_ERROR_UNKNOWN_IFACE;
import static android.net.TetheringManager.TETHER_ERROR_UNKNOWN_TYPE;
import static android.net.TetheringManager.TETHER_ERROR_UNSUPPORTED;
import static android.net.TetheringManager.TETHER_ERROR_UNTETHER_IFACE_ERROR;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.net.NetworkCapabilities;
import android.stats.connectivity.DownstreamType;
import android.stats.connectivity.ErrorCode;
import android.stats.connectivity.UpstreamType;
import android.stats.connectivity.UserType;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.networkstack.tethering.UpstreamNetworkState;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class TetheringMetricsTest {
    private static final String TEST_CALLER_PKG = "com.test.caller.pkg";
    private static final String SETTINGS_PKG = "com.android.settings";
    private static final String SYSTEMUI_PKG = "com.android.systemui";
    private static final String GMS_PKG = "com.google.android.gms";
    private static final long TEST_START_TIME = 1670395936033L;
    private static final long SECOND_IN_MILLIS = 1_000L;

    private TetheringMetrics mTetheringMetrics;
    private final NetworkTetheringReported.Builder mStatsBuilder =
            NetworkTetheringReported.newBuilder();

    private long mElapsedRealtime;

    private class MockTetheringMetrics extends TetheringMetrics {
        @Override
        public void write(final NetworkTetheringReported reported) {}
        @Override
        public long timeNow() {
            return currentTimeMillis();
        }
    }

    private long currentTimeMillis() {
        return TEST_START_TIME + mElapsedRealtime;
    }

    private void incrementCurrentTime(final long duration) {
        mElapsedRealtime += duration;
        mTetheringMetrics.timeNow();
    }

    private long getElapsedRealtime() {
        return mElapsedRealtime;
    }

    private void clearElapsedRealtime() {
        mElapsedRealtime = 0;
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mTetheringMetrics = spy(new MockTetheringMetrics());
        mElapsedRealtime = 0L;
    }

    private void verifyReport(final DownstreamType downstream, final ErrorCode error,
            final UserType user, final UpstreamEvents.Builder upstreamEvents, final long duration)
            throws Exception {
        final NetworkTetheringReported expectedReport =
                mStatsBuilder.setDownstreamType(downstream)
                .setUserType(user)
                .setUpstreamType(UpstreamType.UT_UNKNOWN)
                .setErrorCode(error)
                .setUpstreamEvents(upstreamEvents)
                .setDurationMillis(duration)
                .build();
        verify(mTetheringMetrics).write(expectedReport);
    }

    private void updateErrorAndSendReport(final int downstream, final int error) {
        mTetheringMetrics.updateErrorCode(downstream, error);
        mTetheringMetrics.sendReport(downstream);
    }

    private static NetworkCapabilities buildUpstreamCapabilities(final int[] transports) {
        final NetworkCapabilities nc = new NetworkCapabilities();
        for (int type: transports) {
            nc.addTransportType(type);
        }
        return nc;
    }

    private static UpstreamNetworkState buildUpstreamState(final int... transports) {
        return new UpstreamNetworkState(
                null,
                buildUpstreamCapabilities(transports),
                null);
    }

    private void addUpstreamEvent(UpstreamEvents.Builder upstreamEvents,
            final UpstreamType expectedResult, final long duration, final long txBytes,
                    final long rxBytes) {
        UpstreamEvent.Builder upstreamEvent = UpstreamEvent.newBuilder()
                .setUpstreamType(expectedResult)
                .setDurationMillis(duration)
                .setTxBytes(txBytes)
                .setRxBytes(rxBytes);
        upstreamEvents.addUpstreamEvent(upstreamEvent);
    }

    private void runDownstreamTypesTest(final int type, final DownstreamType expectedResult)
            throws Exception {
        mTetheringMetrics.createBuilder(type, TEST_CALLER_PKG);
        final long duration = 2 * SECOND_IN_MILLIS;
        incrementCurrentTime(duration);
        UpstreamEvents.Builder upstreamEvents = UpstreamEvents.newBuilder();
        // Set UpstreamType as NO_NETWORK because the upstream type has not been changed.
        addUpstreamEvent(upstreamEvents, UpstreamType.UT_NO_NETWORK, duration, 0L, 0L);
        updateErrorAndSendReport(type, TETHER_ERROR_NO_ERROR);

        verifyReport(expectedResult, ErrorCode.EC_NO_ERROR, UserType.USER_UNKNOWN,
                upstreamEvents, getElapsedRealtime());
        reset(mTetheringMetrics);
        clearElapsedRealtime();
        mTetheringMetrics.cleanup();
    }

    @Test
    public void testDownstreamTypes() throws Exception {
        runDownstreamTypesTest(TETHERING_WIFI, DownstreamType.DS_TETHERING_WIFI);
        runDownstreamTypesTest(TETHERING_WIFI_P2P, DownstreamType.DS_TETHERING_WIFI_P2P);
        runDownstreamTypesTest(TETHERING_BLUETOOTH, DownstreamType.DS_TETHERING_BLUETOOTH);
        runDownstreamTypesTest(TETHERING_USB, DownstreamType.DS_TETHERING_USB);
        runDownstreamTypesTest(TETHERING_NCM, DownstreamType.DS_TETHERING_NCM);
        runDownstreamTypesTest(TETHERING_ETHERNET, DownstreamType.DS_TETHERING_ETHERNET);
    }

    private void runErrorCodesTest(final int errorCode, final ErrorCode expectedResult)
            throws Exception {
        mTetheringMetrics.createBuilder(TETHERING_WIFI, TEST_CALLER_PKG);
        mTetheringMetrics.maybeUpdateUpstreamType(buildUpstreamState(TRANSPORT_WIFI));
        final long duration = 2 * SECOND_IN_MILLIS;
        incrementCurrentTime(duration);
        updateErrorAndSendReport(TETHERING_WIFI, errorCode);

        UpstreamEvents.Builder upstreamEvents = UpstreamEvents.newBuilder();
        addUpstreamEvent(upstreamEvents, UpstreamType.UT_WIFI, duration, 0L, 0L);
        verifyReport(DownstreamType.DS_TETHERING_WIFI, expectedResult, UserType.USER_UNKNOWN,
                    upstreamEvents, getElapsedRealtime());
        reset(mTetheringMetrics);
        clearElapsedRealtime();
        mTetheringMetrics.cleanup();
    }

    @Test
    public void testErrorCodes() throws Exception {
        runErrorCodesTest(TETHER_ERROR_NO_ERROR, ErrorCode.EC_NO_ERROR);
        runErrorCodesTest(TETHER_ERROR_UNKNOWN_IFACE, ErrorCode.EC_UNKNOWN_IFACE);
        runErrorCodesTest(TETHER_ERROR_SERVICE_UNAVAIL, ErrorCode.EC_SERVICE_UNAVAIL);
        runErrorCodesTest(TETHER_ERROR_UNSUPPORTED, ErrorCode.EC_UNSUPPORTED);
        runErrorCodesTest(TETHER_ERROR_UNAVAIL_IFACE, ErrorCode.EC_UNAVAIL_IFACE);
        runErrorCodesTest(TETHER_ERROR_INTERNAL_ERROR, ErrorCode.EC_INTERNAL_ERROR);
        runErrorCodesTest(TETHER_ERROR_TETHER_IFACE_ERROR, ErrorCode.EC_TETHER_IFACE_ERROR);
        runErrorCodesTest(TETHER_ERROR_UNTETHER_IFACE_ERROR, ErrorCode.EC_UNTETHER_IFACE_ERROR);
        runErrorCodesTest(TETHER_ERROR_ENABLE_FORWARDING_ERROR,
                ErrorCode.EC_ENABLE_FORWARDING_ERROR);
        runErrorCodesTest(TETHER_ERROR_DISABLE_FORWARDING_ERROR,
                ErrorCode.EC_DISABLE_FORWARDING_ERROR);
        runErrorCodesTest(TETHER_ERROR_IFACE_CFG_ERROR, ErrorCode.EC_IFACE_CFG_ERROR);
        runErrorCodesTest(TETHER_ERROR_PROVISIONING_FAILED, ErrorCode.EC_PROVISIONING_FAILED);
        runErrorCodesTest(TETHER_ERROR_DHCPSERVER_ERROR, ErrorCode.EC_DHCPSERVER_ERROR);
        runErrorCodesTest(TETHER_ERROR_ENTITLEMENT_UNKNOWN, ErrorCode.EC_ENTITLEMENT_UNKNOWN);
        runErrorCodesTest(TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION,
                ErrorCode.EC_NO_CHANGE_TETHERING_PERMISSION);
        runErrorCodesTest(TETHER_ERROR_NO_ACCESS_TETHERING_PERMISSION,
                ErrorCode.EC_NO_ACCESS_TETHERING_PERMISSION);
        runErrorCodesTest(TETHER_ERROR_UNKNOWN_TYPE, ErrorCode.EC_UNKNOWN_TYPE);
    }

    private void runUserTypesTest(final String callerPkg, final UserType expectedResult)
            throws Exception {
        mTetheringMetrics.createBuilder(TETHERING_WIFI, callerPkg);
        final long duration = 1 * SECOND_IN_MILLIS;
        incrementCurrentTime(duration);
        updateErrorAndSendReport(TETHERING_WIFI, TETHER_ERROR_NO_ERROR);

        UpstreamEvents.Builder upstreamEvents = UpstreamEvents.newBuilder();
        // Set UpstreamType as NO_NETWORK because the upstream type has not been changed.
        addUpstreamEvent(upstreamEvents, UpstreamType.UT_NO_NETWORK, duration, 0L, 0L);
        verifyReport(DownstreamType.DS_TETHERING_WIFI, ErrorCode.EC_NO_ERROR, expectedResult,
                    upstreamEvents, getElapsedRealtime());
        reset(mTetheringMetrics);
        clearElapsedRealtime();
        mTetheringMetrics.cleanup();
    }

    @Test
    public void testUserTypes() throws Exception {
        runUserTypesTest(TEST_CALLER_PKG, UserType.USER_UNKNOWN);
        runUserTypesTest(SETTINGS_PKG, UserType.USER_SETTINGS);
        runUserTypesTest(SYSTEMUI_PKG, UserType.USER_SYSTEMUI);
        runUserTypesTest(GMS_PKG, UserType.USER_GMS);
    }

    private void runUpstreamTypesTest(final UpstreamNetworkState ns,
            final UpstreamType expectedResult) throws Exception {
        mTetheringMetrics.createBuilder(TETHERING_WIFI, TEST_CALLER_PKG);
        mTetheringMetrics.maybeUpdateUpstreamType(ns);
        final long duration = 2 * SECOND_IN_MILLIS;
        incrementCurrentTime(duration);
        updateErrorAndSendReport(TETHERING_WIFI, TETHER_ERROR_NO_ERROR);

        UpstreamEvents.Builder upstreamEvents = UpstreamEvents.newBuilder();
        addUpstreamEvent(upstreamEvents, expectedResult, duration, 0L, 0L);
        verifyReport(DownstreamType.DS_TETHERING_WIFI, ErrorCode.EC_NO_ERROR,
                UserType.USER_UNKNOWN, upstreamEvents, getElapsedRealtime());
        reset(mTetheringMetrics);
        clearElapsedRealtime();
        mTetheringMetrics.cleanup();
    }

    @Test
    public void testUpstreamTypes() throws Exception {
        runUpstreamTypesTest(null , UpstreamType.UT_NO_NETWORK);
        runUpstreamTypesTest(buildUpstreamState(TRANSPORT_CELLULAR), UpstreamType.UT_CELLULAR);
        runUpstreamTypesTest(buildUpstreamState(TRANSPORT_WIFI), UpstreamType.UT_WIFI);
        runUpstreamTypesTest(buildUpstreamState(TRANSPORT_BLUETOOTH), UpstreamType.UT_BLUETOOTH);
        runUpstreamTypesTest(buildUpstreamState(TRANSPORT_ETHERNET), UpstreamType.UT_ETHERNET);
        runUpstreamTypesTest(buildUpstreamState(TRANSPORT_WIFI_AWARE), UpstreamType.UT_WIFI_AWARE);
        runUpstreamTypesTest(buildUpstreamState(TRANSPORT_LOWPAN), UpstreamType.UT_LOWPAN);
        runUpstreamTypesTest(buildUpstreamState(TRANSPORT_CELLULAR, TRANSPORT_WIFI,
                TRANSPORT_BLUETOOTH), UpstreamType.UT_UNKNOWN);
    }

    @Test
    public void testMultiBuildersCreatedBeforeSendReport() throws Exception {
        mTetheringMetrics.createBuilder(TETHERING_WIFI, SETTINGS_PKG);
        final long wifiTetheringStartTime = currentTimeMillis();
        incrementCurrentTime(1 * SECOND_IN_MILLIS);
        mTetheringMetrics.createBuilder(TETHERING_USB, SYSTEMUI_PKG);
        final long usbTetheringStartTime = currentTimeMillis();
        incrementCurrentTime(2 * SECOND_IN_MILLIS);
        mTetheringMetrics.createBuilder(TETHERING_BLUETOOTH, GMS_PKG);
        final long bluetoothTetheringStartTime = currentTimeMillis();
        incrementCurrentTime(3 * SECOND_IN_MILLIS);
        updateErrorAndSendReport(TETHERING_WIFI, TETHER_ERROR_DHCPSERVER_ERROR);

        UpstreamEvents.Builder wifiTetheringUpstreamEvents = UpstreamEvents.newBuilder();
        addUpstreamEvent(wifiTetheringUpstreamEvents, UpstreamType.UT_NO_NETWORK,
                currentTimeMillis() - wifiTetheringStartTime, 0L, 0L);
        verifyReport(DownstreamType.DS_TETHERING_WIFI, ErrorCode.EC_DHCPSERVER_ERROR,
                UserType.USER_SETTINGS, wifiTetheringUpstreamEvents,
                currentTimeMillis() - wifiTetheringStartTime);
        incrementCurrentTime(1 * SECOND_IN_MILLIS);
        updateErrorAndSendReport(TETHERING_USB, TETHER_ERROR_ENABLE_FORWARDING_ERROR);

        UpstreamEvents.Builder usbTetheringUpstreamEvents = UpstreamEvents.newBuilder();
        addUpstreamEvent(usbTetheringUpstreamEvents, UpstreamType.UT_NO_NETWORK,
                currentTimeMillis() - usbTetheringStartTime, 0L, 0L);

        verifyReport(DownstreamType.DS_TETHERING_USB, ErrorCode.EC_ENABLE_FORWARDING_ERROR,
                UserType.USER_SYSTEMUI, usbTetheringUpstreamEvents,
                currentTimeMillis() - usbTetheringStartTime);
        incrementCurrentTime(1 * SECOND_IN_MILLIS);
        updateErrorAndSendReport(TETHERING_BLUETOOTH, TETHER_ERROR_TETHER_IFACE_ERROR);

        UpstreamEvents.Builder bluetoothTetheringUpstreamEvents = UpstreamEvents.newBuilder();
        addUpstreamEvent(bluetoothTetheringUpstreamEvents, UpstreamType.UT_NO_NETWORK,
                currentTimeMillis() - bluetoothTetheringStartTime, 0L, 0L);
        verifyReport(DownstreamType.DS_TETHERING_BLUETOOTH, ErrorCode.EC_TETHER_IFACE_ERROR,
                UserType.USER_GMS, bluetoothTetheringUpstreamEvents,
                currentTimeMillis() - bluetoothTetheringStartTime);
    }

    @Test
    public void testUpstreamsWithMultipleDownstreams() throws Exception {
        mTetheringMetrics.createBuilder(TETHERING_WIFI, SETTINGS_PKG);
        final long wifiTetheringStartTime = currentTimeMillis();
        incrementCurrentTime(1 * SECOND_IN_MILLIS);
        mTetheringMetrics.maybeUpdateUpstreamType(buildUpstreamState(TRANSPORT_WIFI));
        final long wifiUpstreamStartTime = currentTimeMillis();
        incrementCurrentTime(5 * SECOND_IN_MILLIS);
        mTetheringMetrics.createBuilder(TETHERING_USB, SYSTEMUI_PKG);
        final long usbTetheringStartTime = currentTimeMillis();
        incrementCurrentTime(5 * SECOND_IN_MILLIS);
        updateErrorAndSendReport(TETHERING_USB, TETHER_ERROR_NO_ERROR);

        UpstreamEvents.Builder usbTetheringUpstreamEvents = UpstreamEvents.newBuilder();
        addUpstreamEvent(usbTetheringUpstreamEvents, UpstreamType.UT_WIFI,
                currentTimeMillis() - usbTetheringStartTime, 0L, 0L);
        verifyReport(DownstreamType.DS_TETHERING_USB, ErrorCode.EC_NO_ERROR,
                UserType.USER_SYSTEMUI, usbTetheringUpstreamEvents,
                currentTimeMillis() - usbTetheringStartTime);
        incrementCurrentTime(7 * SECOND_IN_MILLIS);
        updateErrorAndSendReport(TETHERING_WIFI, TETHER_ERROR_NO_ERROR);

        UpstreamEvents.Builder wifiTetheringUpstreamEvents = UpstreamEvents.newBuilder();
        addUpstreamEvent(wifiTetheringUpstreamEvents, UpstreamType.UT_WIFI,
                currentTimeMillis() - wifiUpstreamStartTime, 0L, 0L);
        verifyReport(DownstreamType.DS_TETHERING_WIFI, ErrorCode.EC_NO_ERROR,
                UserType.USER_SETTINGS, wifiTetheringUpstreamEvents,
                currentTimeMillis() - wifiTetheringStartTime);
    }

    @Test
    public void testSwitchingMultiUpstreams() throws Exception {
        mTetheringMetrics.createBuilder(TETHERING_WIFI, SETTINGS_PKG);
        final long wifiTetheringStartTime = currentTimeMillis();
        incrementCurrentTime(1 * SECOND_IN_MILLIS);
        mTetheringMetrics.maybeUpdateUpstreamType(buildUpstreamState(TRANSPORT_WIFI));
        final long wifiDuration = 5 * SECOND_IN_MILLIS;
        incrementCurrentTime(wifiDuration);
        mTetheringMetrics.maybeUpdateUpstreamType(buildUpstreamState(TRANSPORT_BLUETOOTH));
        final long bluetoothDuration = 15 * SECOND_IN_MILLIS;
        incrementCurrentTime(bluetoothDuration);
        mTetheringMetrics.maybeUpdateUpstreamType(buildUpstreamState(TRANSPORT_CELLULAR));
        final long celltoothDuration = 20 * SECOND_IN_MILLIS;
        incrementCurrentTime(celltoothDuration);
        updateErrorAndSendReport(TETHERING_WIFI, TETHER_ERROR_NO_ERROR);

        UpstreamEvents.Builder upstreamEvents = UpstreamEvents.newBuilder();
        addUpstreamEvent(upstreamEvents, UpstreamType.UT_WIFI, wifiDuration, 0L, 0L);
        addUpstreamEvent(upstreamEvents, UpstreamType.UT_BLUETOOTH, bluetoothDuration, 0L, 0L);
        addUpstreamEvent(upstreamEvents, UpstreamType.UT_CELLULAR, celltoothDuration, 0L, 0L);

        verifyReport(DownstreamType.DS_TETHERING_WIFI, ErrorCode.EC_NO_ERROR,
                UserType.USER_SETTINGS, upstreamEvents,
                currentTimeMillis() - wifiTetheringStartTime);
    }
}
