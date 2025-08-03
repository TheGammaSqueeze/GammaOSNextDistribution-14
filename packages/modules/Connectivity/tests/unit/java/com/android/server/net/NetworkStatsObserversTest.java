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

package com.android.server.net;

import static android.net.ConnectivityManager.TYPE_MOBILE;
import static android.net.NetworkIdentity.OEM_NONE;
import static android.net.NetworkStats.DEFAULT_NETWORK_NO;
import static android.net.NetworkStats.DEFAULT_NETWORK_YES;
import static android.net.NetworkStats.METERED_NO;
import static android.net.NetworkStats.METERED_YES;
import static android.net.NetworkStats.ROAMING_NO;
import static android.net.NetworkStats.SET_DEFAULT;
import static android.net.NetworkStats.TAG_NONE;
import static android.net.NetworkTemplate.MATCH_MOBILE;
import static android.net.NetworkTemplate.MATCH_WIFI;
import static android.net.TrafficStats.MB_IN_BYTES;
import static android.text.format.DateUtils.MINUTE_IN_MILLIS;

import static com.android.testutils.DevSdkIgnoreRuleKt.SC_V2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;

import android.content.Context;
import android.net.DataUsageRequest;
import android.net.NetworkIdentity;
import android.net.NetworkIdentitySet;
import android.net.NetworkStats;
import android.net.NetworkStatsAccess;
import android.net.NetworkTemplate;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.telephony.TelephonyManager;
import android.util.ArrayMap;

import androidx.test.filters.SmallTest;

import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;
import com.android.testutils.HandlerUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;

/**
 * Tests for {@link NetworkStatsObservers}.
 */
@RunWith(DevSdkIgnoreRunner.class)
@SmallTest
@DevSdkIgnoreRule.IgnoreUpTo(SC_V2) // TODO: Use to Build.VERSION_CODES.SC_V2 when available
public class NetworkStatsObserversTest {
    private static final String TEST_IFACE = "test0";
    private static final String TEST_IFACE2 = "test1";
    private static final long TEST_START = 1194220800000L;

    private static final String IMSI_1 = "310004";
    private static final String IMSI_2 = "310260";
    private static final int SUBID_1 = 1;
    private static final String TEST_SSID = "AndroidAP";

    private static NetworkTemplate sTemplateWifi = new NetworkTemplate.Builder(MATCH_WIFI).build();
    private static NetworkTemplate sTemplateImsi1 = new NetworkTemplate.Builder(MATCH_MOBILE)
            .setSubscriberIds(Set.of(IMSI_1))
            .setMeteredness(METERED_YES).build();
    private static NetworkTemplate sTemplateImsi2 = new NetworkTemplate.Builder(MATCH_MOBILE)
            .setSubscriberIds(Set.of(IMSI_2))
            .setMeteredness(METERED_YES).build();
    private static final int PID_SYSTEM = 1234;
    private static final int PID_RED = 1235;
    private static final int PID_BLUE = 1236;

    private static final int UID_RED = UserHandle.PER_USER_RANGE + 1;
    private static final int UID_BLUE = UserHandle.PER_USER_RANGE + 2;
    private static final int UID_GREEN = UserHandle.PER_USER_RANGE + 3;
    private static final int UID_ANOTHER_USER = 2 * UserHandle.PER_USER_RANGE + 4;

    private static final String PACKAGE_SYSTEM = "android";
    private static final String PACKAGE_RED = "RED";
    private static final String PACKAGE_BLUE = "BLUE";

    private static final long WAIT_TIMEOUT_MS = 500;
    private static final long THRESHOLD_BYTES = 2 * MB_IN_BYTES;
    private static final long BASE_BYTES = 7 * MB_IN_BYTES;

    private HandlerThread mObserverHandlerThread;

    private NetworkStatsObservers mStatsObservers;
    private ArrayMap<String, NetworkIdentitySet> mActiveIfaces;
    private ArrayMap<String, NetworkIdentitySet> mActiveUidIfaces;

    @Mock private IBinder mUsageCallbackBinder;
    private TestableUsageCallback mUsageCallback;
    @Mock private Context mContext;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mObserverHandlerThread = new HandlerThread("NetworkStatsObserversTest");
        mObserverHandlerThread.start();
        final Looper observerLooper = mObserverHandlerThread.getLooper();
        mStatsObservers = new NetworkStatsObservers() {
            @Override
            protected Looper getHandlerLooperLocked() {
                return observerLooper;
            }
        };

        mActiveIfaces = new ArrayMap<>();
        mActiveUidIfaces = new ArrayMap<>();
        mUsageCallback = new TestableUsageCallback(mUsageCallbackBinder);
    }

    @After
    public void tearDown() throws Exception {
        if (mObserverHandlerThread != null) {
            mObserverHandlerThread.quitSafely();
            mObserverHandlerThread.join();
        }
    }

    @Test
    public void testRegister_thresholdTooLow_setsDefaultThreshold() throws Exception {
        final long thresholdTooLowBytes = 1L;
        final DataUsageRequest inputRequest = new DataUsageRequest(
                DataUsageRequest.REQUEST_ID_UNSET, sTemplateWifi, thresholdTooLowBytes);

        final DataUsageRequest requestByApp = mStatsObservers.register(mContext, inputRequest,
                mUsageCallback, PID_RED , UID_RED, PACKAGE_RED, NetworkStatsAccess.Level.DEVICE);
        assertTrue(requestByApp.requestId > 0);
        assertTrue(Objects.equals(sTemplateWifi, requestByApp.template));
        assertEquals(thresholdTooLowBytes, requestByApp.thresholdInBytes);

        // Verify the threshold requested by system uid won't be overridden.
        final DataUsageRequest requestBySystem = mStatsObservers.register(mContext, inputRequest,
                mUsageCallback, PID_SYSTEM, Process.SYSTEM_UID, PACKAGE_SYSTEM,
                NetworkStatsAccess.Level.DEVICE);
        assertTrue(requestBySystem.requestId > 0);
        assertTrue(Objects.equals(sTemplateWifi, requestBySystem.template));
        assertEquals(1, requestBySystem.thresholdInBytes);
    }

    @Test
    public void testRegister_highThreshold_accepted() throws Exception {
        long highThresholdBytes = 2 * THRESHOLD_BYTES;
        DataUsageRequest inputRequest = new DataUsageRequest(
                DataUsageRequest.REQUEST_ID_UNSET, sTemplateWifi, highThresholdBytes);

        DataUsageRequest request = mStatsObservers.register(mContext, inputRequest, mUsageCallback,
                PID_SYSTEM, Process.SYSTEM_UID, PACKAGE_SYSTEM, NetworkStatsAccess.Level.DEVICE);
        assertTrue(request.requestId > 0);
        assertTrue(Objects.equals(sTemplateWifi, request.template));
        assertEquals(highThresholdBytes, request.thresholdInBytes);
    }

    @Test
    public void testRegister_twoRequests_twoIds() throws Exception {
        DataUsageRequest inputRequest = new DataUsageRequest(
                DataUsageRequest.REQUEST_ID_UNSET, sTemplateWifi, THRESHOLD_BYTES);

        DataUsageRequest request1 = mStatsObservers.register(mContext, inputRequest, mUsageCallback,
                PID_SYSTEM, Process.SYSTEM_UID, PACKAGE_SYSTEM, NetworkStatsAccess.Level.DEVICE);
        assertTrue(request1.requestId > 0);
        assertTrue(Objects.equals(sTemplateWifi, request1.template));
        assertEquals(THRESHOLD_BYTES, request1.thresholdInBytes);

        DataUsageRequest request2 = mStatsObservers.register(mContext, inputRequest, mUsageCallback,
                PID_SYSTEM, Process.SYSTEM_UID, PACKAGE_SYSTEM, NetworkStatsAccess.Level.DEVICE);
        assertTrue(request2.requestId > request1.requestId);
        assertTrue(Objects.equals(sTemplateWifi, request2.template));
        assertEquals(THRESHOLD_BYTES, request2.thresholdInBytes);
    }

    @Test
    public void testRegister_limit() throws Exception {
        final DataUsageRequest inputRequest = new DataUsageRequest(
                DataUsageRequest.REQUEST_ID_UNSET, sTemplateWifi, THRESHOLD_BYTES);

        // Register maximum requests for red.
        final ArrayList<DataUsageRequest> redRequests = new ArrayList<>();
        for (int i = 0; i < NetworkStatsObservers.MAX_REQUESTS_PER_UID; i++) {
            final DataUsageRequest returnedRequest =
                    mStatsObservers.register(mContext, inputRequest, mUsageCallback,
                            PID_RED, UID_RED, PACKAGE_RED, NetworkStatsAccess.Level.DEVICE);
            redRequests.add(returnedRequest);
            assertTrue(returnedRequest.requestId > 0);
        }

        // Verify request exceeds the limit throws.
        assertThrows(IllegalStateException.class, () ->
                mStatsObservers.register(mContext, inputRequest, mUsageCallback,
                    PID_RED, UID_RED, PACKAGE_RED, NetworkStatsAccess.Level.DEVICE));

        // Verify another uid is not affected.
        final ArrayList<DataUsageRequest> blueRequests = new ArrayList<>();
        for (int i = 0; i < NetworkStatsObservers.MAX_REQUESTS_PER_UID; i++) {
            final DataUsageRequest returnedRequest =
                    mStatsObservers.register(mContext, inputRequest, mUsageCallback,
                            PID_BLUE, UID_BLUE, PACKAGE_BLUE, NetworkStatsAccess.Level.DEVICE);
            blueRequests.add(returnedRequest);
            assertTrue(returnedRequest.requestId > 0);
        }

        // Again, verify request exceeds the limit throws for the 2nd uid.
        assertThrows(IllegalStateException.class, () ->
                mStatsObservers.register(mContext, inputRequest, mUsageCallback,
                        PID_RED, UID_RED, PACKAGE_RED, NetworkStatsAccess.Level.DEVICE));

        // Unregister all registered requests. Note that exceptions cannot be tested since
        // unregister is handled in the handler thread.
        for (final DataUsageRequest request : redRequests) {
            mStatsObservers.unregister(request, UID_RED);
        }
        for (final DataUsageRequest request : blueRequests) {
            mStatsObservers.unregister(request, UID_BLUE);
        }
    }

    @Test
    public void testUnregister_unknownRequest_noop() throws Exception {
        DataUsageRequest unknownRequest = new DataUsageRequest(
                123456 /* id */, sTemplateWifi, THRESHOLD_BYTES);

        mStatsObservers.unregister(unknownRequest, UID_RED);
    }

    @Test
    public void testUnregister_knownRequest_releasesCaller() throws Exception {
        DataUsageRequest inputRequest = new DataUsageRequest(
                DataUsageRequest.REQUEST_ID_UNSET, sTemplateImsi1, THRESHOLD_BYTES);

        DataUsageRequest request = mStatsObservers.register(mContext, inputRequest, mUsageCallback,
                PID_SYSTEM, Process.SYSTEM_UID, PACKAGE_SYSTEM, NetworkStatsAccess.Level.DEVICE);
        assertTrue(request.requestId > 0);
        assertTrue(Objects.equals(sTemplateImsi1, request.template));
        assertEquals(THRESHOLD_BYTES, request.thresholdInBytes);
        Mockito.verify(mUsageCallbackBinder).linkToDeath(any(IBinder.DeathRecipient.class),
                anyInt());

        mStatsObservers.unregister(request, Process.SYSTEM_UID);
        waitForObserverToIdle();

        Mockito.verify(mUsageCallbackBinder).unlinkToDeath(any(IBinder.DeathRecipient.class),
                anyInt());
    }

    @Test
    public void testUnregister_knownRequest_invalidUid_doesNotUnregister() throws Exception {
        DataUsageRequest inputRequest = new DataUsageRequest(
                DataUsageRequest.REQUEST_ID_UNSET, sTemplateImsi1, THRESHOLD_BYTES);

        DataUsageRequest request = mStatsObservers.register(mContext, inputRequest, mUsageCallback,
                PID_RED, UID_RED, PACKAGE_RED, NetworkStatsAccess.Level.DEVICE);
        assertTrue(request.requestId > 0);
        assertTrue(Objects.equals(sTemplateImsi1, request.template));
        assertEquals(THRESHOLD_BYTES, request.thresholdInBytes);
        Mockito.verify(mUsageCallbackBinder)
                .linkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        mStatsObservers.unregister(request, UID_BLUE);
        waitForObserverToIdle();
        Mockito.verifyZeroInteractions(mUsageCallbackBinder);

        // Verify that system uid can unregister for other uids.
        mStatsObservers.unregister(request, Process.SYSTEM_UID);
        waitForObserverToIdle();
        mUsageCallback.expectOnCallbackReleased(request);
    }

    private NetworkIdentitySet makeTestIdentSet() {
        NetworkIdentitySet identSet = new NetworkIdentitySet();
        identSet.add(new NetworkIdentity(
                TYPE_MOBILE, TelephonyManager.NETWORK_TYPE_UNKNOWN,
                IMSI_1, null /* networkId */, false /* roaming */, true /* metered */,
                true /* defaultNetwork */, OEM_NONE, SUBID_1));
        return identSet;
    }

    @Test
    public void testUpdateStats_initialSample_doesNotNotify() throws Exception {
        DataUsageRequest inputRequest = new DataUsageRequest(
                DataUsageRequest.REQUEST_ID_UNSET, sTemplateImsi1, THRESHOLD_BYTES);

        DataUsageRequest request = mStatsObservers.register(mContext, inputRequest, mUsageCallback,
                PID_SYSTEM, Process.SYSTEM_UID, PACKAGE_SYSTEM, NetworkStatsAccess.Level.DEVICE);
        assertTrue(request.requestId > 0);
        assertTrue(Objects.equals(sTemplateImsi1, request.template));
        assertEquals(THRESHOLD_BYTES, request.thresholdInBytes);

        NetworkIdentitySet identSet = makeTestIdentSet();
        mActiveIfaces.put(TEST_IFACE, identSet);

        // Baseline
        NetworkStats xtSnapshot = new NetworkStats(TEST_START, 1 /* initialSize */)
                .insertEntry(TEST_IFACE, BASE_BYTES, 8L, BASE_BYTES, 16L);
        NetworkStats uidSnapshot = null;

        mStatsObservers.updateStats(
                xtSnapshot, uidSnapshot, mActiveIfaces, mActiveUidIfaces, TEST_START);
        waitForObserverToIdle();
    }

    @Test
    public void testUpdateStats_belowThreshold_doesNotNotify() throws Exception {
        DataUsageRequest inputRequest = new DataUsageRequest(
                DataUsageRequest.REQUEST_ID_UNSET, sTemplateImsi1, THRESHOLD_BYTES);

        DataUsageRequest request = mStatsObservers.register(mContext, inputRequest, mUsageCallback,
                PID_SYSTEM, Process.SYSTEM_UID, PACKAGE_SYSTEM, NetworkStatsAccess.Level.DEVICE);
        assertTrue(request.requestId > 0);
        assertTrue(Objects.equals(sTemplateImsi1, request.template));
        assertEquals(THRESHOLD_BYTES, request.thresholdInBytes);

        NetworkIdentitySet identSet = makeTestIdentSet();
        mActiveIfaces.put(TEST_IFACE, identSet);

        // Baseline
        NetworkStats xtSnapshot = new NetworkStats(TEST_START, 1 /* initialSize */)
                .insertEntry(TEST_IFACE, BASE_BYTES, 8L, BASE_BYTES, 16L);
        NetworkStats uidSnapshot = null;
        mStatsObservers.updateStats(
                xtSnapshot, uidSnapshot, mActiveIfaces, mActiveUidIfaces, TEST_START);

        // Delta
        xtSnapshot = new NetworkStats(TEST_START, 1 /* initialSize */)
                .insertEntry(TEST_IFACE, BASE_BYTES + 1024L, 10L, BASE_BYTES + 2048L, 20L);
        mStatsObservers.updateStats(
                xtSnapshot, uidSnapshot, mActiveIfaces, mActiveUidIfaces, TEST_START);
        waitForObserverToIdle();
    }


    @Test
    public void testUpdateStats_deviceAccess_notifies() throws Exception {
        DataUsageRequest inputRequest = new DataUsageRequest(
                DataUsageRequest.REQUEST_ID_UNSET, sTemplateImsi1, THRESHOLD_BYTES);

        DataUsageRequest request = mStatsObservers.register(mContext, inputRequest, mUsageCallback,
                PID_SYSTEM, Process.SYSTEM_UID, PACKAGE_SYSTEM, NetworkStatsAccess.Level.DEVICE);
        assertTrue(request.requestId > 0);
        assertTrue(Objects.equals(sTemplateImsi1, request.template));
        assertEquals(THRESHOLD_BYTES, request.thresholdInBytes);

        NetworkIdentitySet identSet = makeTestIdentSet();
        mActiveIfaces.put(TEST_IFACE, identSet);

        // Baseline
        NetworkStats xtSnapshot = new NetworkStats(TEST_START, 1 /* initialSize */)
                .insertEntry(TEST_IFACE, BASE_BYTES, 8L, BASE_BYTES, 16L);
        NetworkStats uidSnapshot = null;
        mStatsObservers.updateStats(
                xtSnapshot, uidSnapshot, mActiveIfaces, mActiveUidIfaces, TEST_START);

        // Delta
        xtSnapshot = new NetworkStats(TEST_START + MINUTE_IN_MILLIS, 1 /* initialSize */)
                .insertEntry(TEST_IFACE, BASE_BYTES + THRESHOLD_BYTES, 12L,
                        BASE_BYTES + THRESHOLD_BYTES, 22L);
        mStatsObservers.updateStats(
                xtSnapshot, uidSnapshot, mActiveIfaces, mActiveUidIfaces, TEST_START);
        waitForObserverToIdle();
        mUsageCallback.expectOnThresholdReached(request);
    }

    @Test
    public void testUpdateStats_defaultAccess_notifiesSameUid() throws Exception {
        DataUsageRequest inputRequest = new DataUsageRequest(
                DataUsageRequest.REQUEST_ID_UNSET, sTemplateImsi1, THRESHOLD_BYTES);

        DataUsageRequest request = mStatsObservers.register(mContext, inputRequest, mUsageCallback,
                PID_RED, UID_RED, PACKAGE_SYSTEM , NetworkStatsAccess.Level.DEFAULT);
        assertTrue(request.requestId > 0);
        assertTrue(Objects.equals(sTemplateImsi1, request.template));
        assertEquals(THRESHOLD_BYTES, request.thresholdInBytes);

        NetworkIdentitySet identSet = makeTestIdentSet();
        mActiveUidIfaces.put(TEST_IFACE, identSet);

        // Baseline
        NetworkStats xtSnapshot = null;
        NetworkStats uidSnapshot = new NetworkStats(TEST_START, 2 /* initialSize */)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE, METERED_NO, ROAMING_NO,
                        DEFAULT_NETWORK_YES, BASE_BYTES, 2L, BASE_BYTES, 2L, 0L);
        mStatsObservers.updateStats(
                xtSnapshot, uidSnapshot, mActiveIfaces, mActiveUidIfaces, TEST_START);

        // Delta
        uidSnapshot = new NetworkStats(TEST_START + 2 * MINUTE_IN_MILLIS, 2 /* initialSize */)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE, METERED_NO, ROAMING_NO,
                        DEFAULT_NETWORK_NO, BASE_BYTES + THRESHOLD_BYTES, 2L,
                        BASE_BYTES + THRESHOLD_BYTES, 2L, 0L);
        mStatsObservers.updateStats(
                xtSnapshot, uidSnapshot, mActiveIfaces, mActiveUidIfaces, TEST_START);
        waitForObserverToIdle();
        mUsageCallback.expectOnThresholdReached(request);
    }

    @Test
    public void testUpdateStats_defaultAccess_usageOtherUid_doesNotNotify() throws Exception {
        DataUsageRequest inputRequest = new DataUsageRequest(
                DataUsageRequest.REQUEST_ID_UNSET, sTemplateImsi1, THRESHOLD_BYTES);

        DataUsageRequest request = mStatsObservers.register(mContext, inputRequest, mUsageCallback,
                PID_BLUE, UID_BLUE, PACKAGE_BLUE, NetworkStatsAccess.Level.DEFAULT);
        assertTrue(request.requestId > 0);
        assertTrue(Objects.equals(sTemplateImsi1, request.template));
        assertEquals(THRESHOLD_BYTES, request.thresholdInBytes);

        NetworkIdentitySet identSet = makeTestIdentSet();
        mActiveUidIfaces.put(TEST_IFACE, identSet);

        // Baseline
        NetworkStats xtSnapshot = null;
        NetworkStats uidSnapshot = new NetworkStats(TEST_START, 2 /* initialSize */)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE, METERED_NO, ROAMING_NO,
                        DEFAULT_NETWORK_NO, BASE_BYTES, 2L, BASE_BYTES, 2L, 0L);
        mStatsObservers.updateStats(
                xtSnapshot, uidSnapshot, mActiveIfaces, mActiveUidIfaces, TEST_START);

        // Delta
        uidSnapshot = new NetworkStats(TEST_START + 2 * MINUTE_IN_MILLIS, 2 /* initialSize */)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE, METERED_NO, ROAMING_NO,
                        DEFAULT_NETWORK_NO, BASE_BYTES + THRESHOLD_BYTES, 2L,
                        BASE_BYTES + THRESHOLD_BYTES, 2L, 0L);
        mStatsObservers.updateStats(
                xtSnapshot, uidSnapshot, mActiveIfaces, mActiveUidIfaces, TEST_START);
        waitForObserverToIdle();
    }

    @Test
    public void testUpdateStats_userAccess_usageSameUser_notifies() throws Exception {
        DataUsageRequest inputRequest = new DataUsageRequest(
                DataUsageRequest.REQUEST_ID_UNSET, sTemplateImsi1, THRESHOLD_BYTES);

        DataUsageRequest request = mStatsObservers.register(mContext, inputRequest, mUsageCallback,
                PID_BLUE, UID_BLUE, PACKAGE_BLUE, NetworkStatsAccess.Level.USER);
        assertTrue(request.requestId > 0);
        assertTrue(Objects.equals(sTemplateImsi1, request.template));
        assertEquals(THRESHOLD_BYTES, request.thresholdInBytes);

        NetworkIdentitySet identSet = makeTestIdentSet();
        mActiveUidIfaces.put(TEST_IFACE, identSet);

        // Baseline
        NetworkStats xtSnapshot = null;
        NetworkStats uidSnapshot = new NetworkStats(TEST_START, 2 /* initialSize */)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE, METERED_NO, ROAMING_NO,
                        DEFAULT_NETWORK_YES, BASE_BYTES, 2L, BASE_BYTES, 2L, 0L);
        mStatsObservers.updateStats(
                xtSnapshot, uidSnapshot, mActiveIfaces, mActiveUidIfaces, TEST_START);

        // Delta
        uidSnapshot = new NetworkStats(TEST_START + 2 * MINUTE_IN_MILLIS, 2 /* initialSize */)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE, METERED_NO, ROAMING_NO,
                        DEFAULT_NETWORK_YES, BASE_BYTES + THRESHOLD_BYTES, 2L,
                        BASE_BYTES + THRESHOLD_BYTES, 2L, 0L);
        mStatsObservers.updateStats(
                xtSnapshot, uidSnapshot, mActiveIfaces, mActiveUidIfaces, TEST_START);
        waitForObserverToIdle();
        mUsageCallback.expectOnThresholdReached(request);
    }

    @Test
    public void testUpdateStats_userAccess_usageAnotherUser_doesNotNotify() throws Exception {
        DataUsageRequest inputRequest = new DataUsageRequest(
                DataUsageRequest.REQUEST_ID_UNSET, sTemplateImsi1, THRESHOLD_BYTES);

        DataUsageRequest request = mStatsObservers.register(mContext, inputRequest, mUsageCallback,
                PID_RED, UID_RED, PACKAGE_RED, NetworkStatsAccess.Level.USER);
        assertTrue(request.requestId > 0);
        assertTrue(Objects.equals(sTemplateImsi1, request.template));
        assertEquals(THRESHOLD_BYTES, request.thresholdInBytes);

        NetworkIdentitySet identSet = makeTestIdentSet();
        mActiveUidIfaces.put(TEST_IFACE, identSet);

        // Baseline
        NetworkStats xtSnapshot = null;
        NetworkStats uidSnapshot = new NetworkStats(TEST_START, 2 /* initialSize */)
                .insertEntry(TEST_IFACE, UID_ANOTHER_USER, SET_DEFAULT, TAG_NONE, METERED_NO,
                        ROAMING_NO, DEFAULT_NETWORK_YES, BASE_BYTES, 2L, BASE_BYTES, 2L, 0L);
        mStatsObservers.updateStats(
                xtSnapshot, uidSnapshot, mActiveIfaces, mActiveUidIfaces, TEST_START);

        // Delta
        uidSnapshot = new NetworkStats(TEST_START + 2 * MINUTE_IN_MILLIS, 2 /* initialSize */)
                .insertEntry(TEST_IFACE, UID_ANOTHER_USER, SET_DEFAULT, TAG_NONE, METERED_NO,
                        ROAMING_NO, DEFAULT_NETWORK_NO, BASE_BYTES + THRESHOLD_BYTES, 2L,
                        BASE_BYTES + THRESHOLD_BYTES, 2L, 0L);
        mStatsObservers.updateStats(
                xtSnapshot, uidSnapshot, mActiveIfaces, mActiveUidIfaces, TEST_START);
        waitForObserverToIdle();
    }

    private void waitForObserverToIdle() {
        HandlerUtils.waitForIdle(mObserverHandlerThread, WAIT_TIMEOUT_MS);
    }
}
