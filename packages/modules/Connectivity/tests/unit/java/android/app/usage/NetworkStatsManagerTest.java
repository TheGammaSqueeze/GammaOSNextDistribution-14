/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.app.usage;

import static android.net.ConnectivityManager.TYPE_MOBILE;
import static android.net.ConnectivityManager.TYPE_WIFI;
import static android.net.NetworkStats.DEFAULT_NETWORK_NO;
import static android.net.NetworkStats.METERED_NO;
import static android.net.NetworkStats.METERED_YES;
import static android.net.NetworkStats.ROAMING_NO;
import static android.net.NetworkStats.SET_ALL;
import static android.net.NetworkStats.SET_DEFAULT;
import static android.net.NetworkStats.TAG_NONE;
import static android.net.NetworkStatsHistory.FIELD_ALL;
import static android.net.NetworkTemplate.MATCH_MOBILE;
import static android.net.NetworkTemplate.MATCH_WIFI;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.INetworkStatsService;
import android.net.INetworkStatsSession;
import android.net.NetworkStats.Entry;
import android.net.NetworkStatsHistory;
import android.net.NetworkTemplate;
import android.os.Build;
import android.os.RemoteException;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;

import java.util.Set;

@RunWith(DevSdkIgnoreRunner.class)
@SmallTest
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.S_V2)
public class NetworkStatsManagerTest {
    private static final String TEST_SUBSCRIBER_ID = "subid";

    private @Mock INetworkStatsService mService;
    private @Mock INetworkStatsSession mStatsSession;

    private NetworkStatsManager mManager;

    // TODO: change to NetworkTemplate.MATCH_MOBILE once internal constant rename is merged to aosp.
    private static final int MATCH_MOBILE_ALL = 1;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mManager = new NetworkStatsManager(InstrumentationRegistry.getContext(), mService);
    }

    @Test
    public void testQueryDetails() throws RemoteException {
        final long startTime = 1;
        final long endTime = 100;
        final int uid1 = 10001;
        final int uid2 = 10002;
        final int uid3 = 10003;

        Entry uid1Entry1 = new Entry("if1", uid1, SET_DEFAULT, TAG_NONE, METERED_NO, ROAMING_NO,
                DEFAULT_NETWORK_NO, 100, 10, 200, 20, 0);

        Entry uid1Entry2 = new Entry("if2", uid1, SET_DEFAULT, TAG_NONE, METERED_NO, ROAMING_NO,
                DEFAULT_NETWORK_NO, 100, 10, 200, 20, 0);

        Entry uid2Entry1 = new Entry("if1", uid2, SET_DEFAULT, TAG_NONE, METERED_NO, ROAMING_NO,
                DEFAULT_NETWORK_NO, 150, 10, 250, 20, 0);

        Entry uid2Entry2 = new Entry("if2", uid2, SET_DEFAULT, TAG_NONE, METERED_NO, ROAMING_NO,
                DEFAULT_NETWORK_NO, 150, 10, 250, 20, 0);

        NetworkStatsHistory history1 = new NetworkStatsHistory(10, 2);
        history1.recordData(10, 20, uid1Entry1);
        history1.recordData(20, 30, uid1Entry2);

        NetworkStatsHistory history2 = new NetworkStatsHistory(10, 2);
        history1.recordData(30, 40, uid2Entry1);
        history1.recordData(35, 45, uid2Entry2);


        when(mService.openSessionForUsageStats(anyInt(), anyString())).thenReturn(mStatsSession);
        when(mStatsSession.getRelevantUids()).thenReturn(new int[] { uid1, uid2, uid3 });

        when(mStatsSession.getHistoryIntervalForUid(any(NetworkTemplate.class),
                eq(uid1), eq(SET_ALL), eq(TAG_NONE),
                eq(FIELD_ALL), eq(startTime), eq(endTime)))
                .then((InvocationOnMock inv) -> {
                    NetworkTemplate template = inv.getArgument(0);
                    assertEquals(MATCH_MOBILE_ALL, template.getMatchRule());
                    assertEquals(TEST_SUBSCRIBER_ID, template.getSubscriberId());
                    return history1;
                });

        when(mStatsSession.getHistoryIntervalForUid(any(NetworkTemplate.class),
                eq(uid2), eq(SET_ALL), eq(TAG_NONE),
                eq(FIELD_ALL), eq(startTime), eq(endTime)))
                .then((InvocationOnMock inv) -> {
                    NetworkTemplate template = inv.getArgument(0);
                    assertEquals(MATCH_MOBILE_ALL, template.getMatchRule());
                    assertEquals(TEST_SUBSCRIBER_ID, template.getSubscriberId());
                    return history2;
                });


        NetworkStats stats = mManager.queryDetails(
                TYPE_MOBILE, TEST_SUBSCRIBER_ID, startTime, endTime);

        NetworkStats.Bucket bucket = new NetworkStats.Bucket();

        // First 2 buckets exactly match entry timings
        assertTrue(stats.getNextBucket(bucket));
        assertEquals(10, bucket.getStartTimeStamp());
        assertEquals(20, bucket.getEndTimeStamp());
        assertBucketMatches(uid1Entry1, bucket);

        assertTrue(stats.getNextBucket(bucket));
        assertEquals(20, bucket.getStartTimeStamp());
        assertEquals(30, bucket.getEndTimeStamp());
        assertBucketMatches(uid1Entry2, bucket);

        // 30 -> 40: contains uid2Entry1 and half of uid2Entry2
        assertTrue(stats.getNextBucket(bucket));
        assertEquals(30, bucket.getStartTimeStamp());
        assertEquals(40, bucket.getEndTimeStamp());
        assertEquals(225, bucket.getRxBytes());
        assertEquals(15, bucket.getRxPackets());
        assertEquals(375, bucket.getTxBytes());
        assertEquals(30, bucket.getTxPackets());

        // 40 -> 50: contains half of uid2Entry2
        assertTrue(stats.getNextBucket(bucket));
        assertEquals(40, bucket.getStartTimeStamp());
        assertEquals(50, bucket.getEndTimeStamp());
        assertEquals(75, bucket.getRxBytes());
        assertEquals(5, bucket.getRxPackets());
        assertEquals(125, bucket.getTxBytes());
        assertEquals(10, bucket.getTxPackets());

        assertFalse(stats.hasNextBucket());
    }

    private void runQueryDetailsAndCheckTemplate(int networkType, String subscriberId,
            NetworkTemplate expectedTemplate) throws RemoteException {
        final long startTime = 1;
        final long endTime = 100;
        final int uid1 = 10001;
        final int uid2 = 10002;

        reset(mStatsSession);
        when(mService.openSessionForUsageStats(anyInt(), anyString())).thenReturn(mStatsSession);
        when(mStatsSession.getRelevantUids()).thenReturn(new int[] { uid1, uid2 });
        when(mStatsSession.getHistoryIntervalForUid(any(NetworkTemplate.class),
                anyInt(), anyInt(), anyInt(), anyInt(), anyLong(), anyLong()))
                .thenReturn(new NetworkStatsHistory(10, 0));
        NetworkStats stats = mManager.queryDetails(
                networkType, subscriberId, startTime, endTime);

        verify(mStatsSession, times(1)).getHistoryIntervalForUid(
                eq(expectedTemplate),
                eq(uid1), eq(SET_ALL),
                eq(TAG_NONE),
                eq(FIELD_ALL), eq(startTime), eq(endTime));

        verify(mStatsSession, times(1)).getHistoryIntervalForUid(
                eq(expectedTemplate),
                eq(uid2), eq(SET_ALL),
                eq(TAG_NONE),
                eq(FIELD_ALL), eq(startTime), eq(endTime));

        assertFalse(stats.hasNextBucket());
    }

    @Test
    public void testNetworkTemplateWhenRunningQueryDetails_NoSubscriberId() throws RemoteException {
        runQueryDetailsAndCheckTemplate(TYPE_MOBILE, null /* subscriberId */,
                new NetworkTemplate.Builder(MATCH_MOBILE).setMeteredness(METERED_YES).build());
        runQueryDetailsAndCheckTemplate(TYPE_WIFI, "" /* subscriberId */,
                new NetworkTemplate.Builder(MATCH_WIFI).build());
        runQueryDetailsAndCheckTemplate(TYPE_WIFI, null /* subscriberId */,
                new NetworkTemplate.Builder(MATCH_WIFI).build());
    }

    @Test
    public void testNetworkTemplateWhenRunningQueryDetails_MergedCarrierWifi()
            throws RemoteException {
        runQueryDetailsAndCheckTemplate(TYPE_WIFI, TEST_SUBSCRIBER_ID,
                new NetworkTemplate.Builder(MATCH_WIFI)
                        .setSubscriberIds(Set.of(TEST_SUBSCRIBER_ID)).build());
    }

    @Test
    public void testQueryTaggedSummary() throws Exception {
        final long startTime = 1;
        final long endTime = 100;

        reset(mStatsSession);
        when(mService.openSessionForUsageStats(anyInt(), anyString())).thenReturn(mStatsSession);
        when(mStatsSession.getTaggedSummaryForAllUid(any(NetworkTemplate.class),
                anyLong(), anyLong()))
                .thenReturn(new android.net.NetworkStats(0, 0));
        final NetworkTemplate template = new NetworkTemplate.Builder(MATCH_MOBILE)
                .setMeteredness(NetworkStats.Bucket.METERED_YES).build();
        NetworkStats stats = mManager.queryTaggedSummary(template, startTime, endTime);

        verify(mStatsSession, times(1)).getTaggedSummaryForAllUid(
                eq(template), eq(startTime), eq(endTime));

        assertFalse(stats.hasNextBucket());
    }


    @Test
    public void testQueryDetailsForDevice() throws Exception {
        final long startTime = 1;
        final long endTime = 100;

        reset(mStatsSession);
        when(mService.openSessionForUsageStats(anyInt(), anyString())).thenReturn(mStatsSession);
        when(mStatsSession.getHistoryIntervalForNetwork(any(NetworkTemplate.class),
                anyInt(), anyLong(), anyLong()))
                .thenReturn(new NetworkStatsHistory(10, 0));
        final NetworkTemplate template = new NetworkTemplate.Builder(MATCH_MOBILE)
                .setMeteredness(NetworkStats.Bucket.METERED_YES).build();
        NetworkStats stats = mManager.queryDetailsForDevice(template, startTime, endTime);

        verify(mStatsSession, times(1)).getHistoryIntervalForNetwork(
                eq(template), eq(FIELD_ALL), eq(startTime), eq(endTime));

        assertFalse(stats.hasNextBucket());
    }

    private void assertBucketMatches(Entry expected, NetworkStats.Bucket actual) {
        assertEquals(expected.uid, actual.getUid());
        assertEquals(expected.rxBytes, actual.getRxBytes());
        assertEquals(expected.rxPackets, actual.getRxPackets());
        assertEquals(expected.txBytes, actual.getTxBytes());
        assertEquals(expected.txPackets, actual.getTxPackets());
    }
}
