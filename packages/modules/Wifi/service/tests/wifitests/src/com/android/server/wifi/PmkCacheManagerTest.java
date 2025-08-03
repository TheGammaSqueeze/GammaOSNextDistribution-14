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

package com.android.server.wifi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.MacAddress;
import android.os.Handler;
import android.text.TextUtils;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.util.NativeUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for {@link com.android.server.wifi.PmkCacheManager}.
 */
@SmallTest
public class PmkCacheManagerTest extends WifiBaseTest {

    private static final MacAddress TEST_MAC_ADDRESS =
            MacAddress.fromString("aa:bb:cc:dd:ee:ff");
    private static final MacAddress TEST_MAC_ADDRESS_2 =
            MacAddress.fromString("aa:bb:cc:dd:ee:00");
    private static final MacAddress TEST_BSSID =
            MacAddress.fromString("01:02:03:04:05:06");

    @Mock private Clock mClock;
    @Mock private Handler mHandler;

    private PmkCacheManager mPmkCacheManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mPmkCacheManager = new PmkCacheManager(mClock, mHandler);

        doNothing().when(mHandler).removeCallbacksAndMessages(any());

        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);
    }

    private void preparePmkCache() throws Exception {
        mPmkCacheManager.add(TEST_MAC_ADDRESS, 0, null, 1500,
                generatePmkDataFromString("Cache"));

        mPmkCacheManager.add(TEST_MAC_ADDRESS, 1, null, 1000,
                generatePmkDataFromString("HelloWorld"));
        mPmkCacheManager.add(TEST_MAC_ADDRESS, 1, null, 1500,
                generatePmkDataFromString("HelloWorld2"));
        mPmkCacheManager.add(TEST_MAC_ADDRESS, 1, null, 3000,
                generatePmkDataFromString("HelloWorld3"));

        mPmkCacheManager.add(TEST_MAC_ADDRESS, 2, null, 1000,
                generatePmkDataFromString("HelloWorld"));
        mPmkCacheManager.add(TEST_MAC_ADDRESS, 2, null, 1500,
                generatePmkDataFromString("HelloWorld2"));
        mPmkCacheManager.add(TEST_MAC_ADDRESS_2, 2, null, 3000,
                generatePmkDataFromString("HelloWorld3"));
    }

    private void preparePmkCacheWithBssid() throws Exception {
        mPmkCacheManager.add(TEST_MAC_ADDRESS, 0, TEST_BSSID, 1500,
                generatePmkDataFromString("Cache"));

        mPmkCacheManager.add(TEST_MAC_ADDRESS, 1, TEST_BSSID, 1000,
                generatePmkDataFromString("HelloWorld"));
        mPmkCacheManager.add(TEST_MAC_ADDRESS, 1, TEST_BSSID, 1500,
                generatePmkDataFromString("HelloWorld2"));
        mPmkCacheManager.add(TEST_MAC_ADDRESS, 1, TEST_BSSID, 3000,
                generatePmkDataFromString("HelloWorld3"));

        mPmkCacheManager.add(TEST_MAC_ADDRESS, 2, TEST_BSSID, 1000,
                generatePmkDataFromString("HelloWorld"));
        mPmkCacheManager.add(TEST_MAC_ADDRESS, 2, TEST_BSSID, 1500,
                generatePmkDataFromString("HelloWorld2"));
        mPmkCacheManager.add(TEST_MAC_ADDRESS_2, 2, TEST_BSSID, 3000,
                generatePmkDataFromString("HelloWorld3"));
    }

    @After
    public void cleanUp() throws Exception {
    }

    @Test
    public void testGet() throws Exception {
        preparePmkCache();

        List<ArrayList<Byte>> pmkDataList;

        pmkDataList = mPmkCacheManager.get(0);
        assertEquals(1, pmkDataList.size());

        pmkDataList = mPmkCacheManager.get(1);
        assertEquals(3, pmkDataList.size());

        // No PMK cache for this network
        pmkDataList = mPmkCacheManager.get(99);
        assertNull(pmkDataList);
    }

    @Test
    public void testGetWithBssid() throws Exception {
        preparePmkCacheWithBssid();

        List<ArrayList<Byte>> pmkDataList;

        pmkDataList = mPmkCacheManager.get(0);
        assertEquals(1, pmkDataList.size());

        pmkDataList = mPmkCacheManager.get(1);
        assertEquals(1, pmkDataList.size());

        // No PMK cache for this network
        pmkDataList = mPmkCacheManager.get(99);
        assertNull(pmkDataList);
    }

    @Test
    public void testRemove() throws Exception {
        preparePmkCache();

        mPmkCacheManager.remove(1);
        List<ArrayList<Byte>> pmkDataList = mPmkCacheManager.get(1);
        assertNull(pmkDataList);

        // Remove non-existent cache should not crash.
        mPmkCacheManager.remove(99);
    }

    @Test
    public void testRemoveWithBssid() throws Exception {
        preparePmkCacheWithBssid();

        mPmkCacheManager.remove(1);
        List<ArrayList<Byte>> pmkDataList = mPmkCacheManager.get(1);
        assertNull(pmkDataList);

        // Remove non-existent cache should not crash.
        mPmkCacheManager.remove(99);
    }

    @Test
    public void testRemoveIfNeeded() throws Exception {
        preparePmkCache();

        List<ArrayList<Byte>> pmkDataList;

        // MAC address is not changed, do nothing.
        pmkDataList = mPmkCacheManager.get(1);
        assertEquals(3, pmkDataList.size());
        mPmkCacheManager.remove(1, TEST_MAC_ADDRESS);
        pmkDataList = mPmkCacheManager.get(1);
        assertEquals(3, pmkDataList.size());

        // MAC address is changed and all entries are associated with this MAC address.
        mPmkCacheManager.remove(1, TEST_MAC_ADDRESS_2);
        pmkDataList = mPmkCacheManager.get(1);
        assertNull(pmkDataList);

        // MAC address is changed and partial entries are associated with this MAC address.
        pmkDataList = mPmkCacheManager.get(2);
        assertEquals(3, pmkDataList.size());
        mPmkCacheManager.remove(2, TEST_MAC_ADDRESS_2);
        pmkDataList = mPmkCacheManager.get(2);
        assertEquals(1, pmkDataList.size());
    }

    @Test
    public void testRemoveIfNeededWithBssid() throws Exception {
        preparePmkCacheWithBssid();

        List<ArrayList<Byte>> pmkDataList;

        // MAC address is not changed, do nothing.
        pmkDataList = mPmkCacheManager.get(1);
        assertEquals(1, pmkDataList.size());
        mPmkCacheManager.remove(1, TEST_MAC_ADDRESS);
        pmkDataList = mPmkCacheManager.get(1);
        assertEquals(1, pmkDataList.size());

        // MAC address is changed and all entries are associated with this MAC address.
        mPmkCacheManager.remove(1, TEST_MAC_ADDRESS_2);
        pmkDataList = mPmkCacheManager.get(1);
        assertNull(pmkDataList);

        // MAC address is changed and partial entries are associated with this MAC address.
        pmkDataList = mPmkCacheManager.get(2);
        assertEquals(1, pmkDataList.size());
        mPmkCacheManager.remove(2, TEST_MAC_ADDRESS_2);
        pmkDataList = mPmkCacheManager.get(2);
        assertEquals(1, pmkDataList.size());
    }

    @Test
    public void testPmkCacheExpirationUpdate() throws Exception {

        final long testStartSeconds = 100;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(testStartSeconds * 1000L);
        // Add the first entry, the next updating time is the expiration of the first entry.
        mPmkCacheManager.add(TEST_MAC_ADDRESS, 0, null, 1500,
                generatePmkDataFromString("Cache"));
        verify(mHandler).postDelayed(
                /* private listener */ any(),
                eq(PmkCacheManager.PMK_CACHE_EXPIRATION_ALARM_TAG),
                eq((1500 - testStartSeconds) * 1000));

        // The expiration of the second one is smaller, and the next updating time is changed.
        reset(mHandler);
        mPmkCacheManager.add(TEST_MAC_ADDRESS, 1, null, 1000,
                generatePmkDataFromString("HelloWorld"));
        verify(mHandler).postDelayed(
                /* private listener */ any(),
                eq(PmkCacheManager.PMK_CACHE_EXPIRATION_ALARM_TAG),
                eq((1000 - testStartSeconds) * 1000));

        // The expiration of the third one is greater, and the next updating time is not changed.
        reset(mHandler);
        mPmkCacheManager.add(TEST_MAC_ADDRESS, 2, null, 3000,
                generatePmkDataFromString("HelloWorld3"));
        verify(mHandler).postDelayed(
                /* private listener */ any(),
                eq(PmkCacheManager.PMK_CACHE_EXPIRATION_ALARM_TAG),
                eq((1000 - testStartSeconds) * 1000));
    }

    @Test
    public void testPmkCacheExpirationUpdateWithBssid() throws Exception {

        final long testStartSeconds = 100;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(testStartSeconds * 1000L);
        // Add the first entry, the next updating time is the expiration of the first entry.
        mPmkCacheManager.add(TEST_MAC_ADDRESS, 0, TEST_BSSID, 1500,
                generatePmkDataFromString("Cache"));
        verify(mHandler).postDelayed(
                /* private listener */ any(),
                eq(PmkCacheManager.PMK_CACHE_EXPIRATION_ALARM_TAG),
                eq((1500 - testStartSeconds) * 1000));

        // The expiration of the second one is smaller, and the next updating time is changed.
        reset(mHandler);
        mPmkCacheManager.add(TEST_MAC_ADDRESS, 1, TEST_BSSID, 1000,
                generatePmkDataFromString("HelloWorld"));
        verify(mHandler).postDelayed(
                /* private listener */ any(),
                eq(PmkCacheManager.PMK_CACHE_EXPIRATION_ALARM_TAG),
                eq((1000 - testStartSeconds) * 1000));

        // The expiration of the third one is greater, and the next updating time is not changed.
        reset(mHandler);
        mPmkCacheManager.add(TEST_MAC_ADDRESS, 2, TEST_BSSID, 3000,
                generatePmkDataFromString("HelloWorld3"));
        verify(mHandler).postDelayed(
                /* private listener */ any(),
                eq(PmkCacheManager.PMK_CACHE_EXPIRATION_ALARM_TAG),
                eq((1000 - testStartSeconds) * 1000));
    }

    @Test
    public void testPmkCacheExpiration() throws Exception {

        List<ArrayList<Byte>> pmkDataList;

        mPmkCacheManager.add(TEST_MAC_ADDRESS, 0, null, 1500,
                generatePmkDataFromString("Cache"));

        mPmkCacheManager.add(TEST_MAC_ADDRESS, 1, null, 1000,
                generatePmkDataFromString("HelloWorld"));
        mPmkCacheManager.add(TEST_MAC_ADDRESS, 1, null, 1500,
                generatePmkDataFromString("HelloWorld2"));
        mPmkCacheManager.add(TEST_MAC_ADDRESS, 1, null, 3000,
                generatePmkDataFromString("HelloWorld3"));
        pmkDataList = mPmkCacheManager.get(0);
        assertEquals(1, pmkDataList.size());
        pmkDataList = mPmkCacheManager.get(1);
        assertEquals(3, pmkDataList.size());

        // Advance to 1000s, one entry of network ID 1 should be removed.
        when(mClock.getElapsedSinceBootMillis()).thenReturn(1000 * 1000L);
        mPmkCacheManager.updatePmkCacheExpiration();
        pmkDataList = mPmkCacheManager.get(0);
        assertEquals(1, pmkDataList.size());
        pmkDataList = mPmkCacheManager.get(1);
        assertEquals(2, pmkDataList.size());

        // Advance to 1500s, network ID 0 should be removed
        // and only one entry is left for network ID 1.
        when(mClock.getElapsedSinceBootMillis()).thenReturn(1500 * 1000L);
        mPmkCacheManager.updatePmkCacheExpiration();
        pmkDataList = mPmkCacheManager.get(0);
        assertNull(pmkDataList);
        pmkDataList = mPmkCacheManager.get(1);
        assertEquals(1, pmkDataList.size());

    }

    @Test
    public void testPmkCacheExpirationWithBssid() throws Exception {

        List<ArrayList<Byte>> pmkDataList;

        mPmkCacheManager.add(TEST_MAC_ADDRESS, 0, TEST_BSSID, 1500,
                generatePmkDataFromString("Cache"));

        //With new PMKCache changes i.e. with BSSID, PMKCache should be always the latest one
        mPmkCacheManager.add(TEST_MAC_ADDRESS, 1, TEST_BSSID, 1000,
                generatePmkDataFromString("HelloWorld"));
        mPmkCacheManager.add(TEST_MAC_ADDRESS, 1, TEST_BSSID, 1500,
                generatePmkDataFromString("HelloWorld2"));
        mPmkCacheManager.add(TEST_MAC_ADDRESS, 1, TEST_BSSID, 3000,
                generatePmkDataFromString("HelloWorld3"));
        pmkDataList = mPmkCacheManager.get(0);
        assertEquals(1, pmkDataList.size());
        pmkDataList = mPmkCacheManager.get(1);
        assertEquals(1, pmkDataList.size());

        // Advance to 1500s, the entry of network ID 0 should be removed.
        when(mClock.getElapsedSinceBootMillis()).thenReturn(1500 * 1000L);
        mPmkCacheManager.updatePmkCacheExpiration();
        pmkDataList = mPmkCacheManager.get(0);
        assertNull(pmkDataList);
        pmkDataList = mPmkCacheManager.get(1);
        assertEquals(1, pmkDataList.size());

        // Advance to 3000s, the entry of network ID 1 should be removed.
        when(mClock.getElapsedSinceBootMillis()).thenReturn(3000 * 1000L);
        mPmkCacheManager.updatePmkCacheExpiration();
        pmkDataList = mPmkCacheManager.get(0);
        assertNull(pmkDataList);
        pmkDataList = mPmkCacheManager.get(1);
        assertNull(pmkDataList);
    }

    private ArrayList<Byte> generatePmkDataFromString(String dataStr) {
        if (TextUtils.isEmpty(dataStr)) return new ArrayList<Byte>();
        return NativeUtil.byteArrayToArrayList(dataStr.getBytes());
    }
}
