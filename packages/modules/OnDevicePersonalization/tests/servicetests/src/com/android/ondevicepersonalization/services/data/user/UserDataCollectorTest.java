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

package com.android.ondevicepersonalization.services.data.user;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.NetworkCapabilities;
import android.text.TextUtils;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

@RunWith(JUnit4.class)
public class UserDataCollectorTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private UserDataCollector mCollector;
    private RawUserData mUserData;

    @Before
    public void setup() {
        mCollector = UserDataCollector.getInstanceForTest(mContext);
        mUserData = RawUserData.getInstance();
        TimeZone pstTime = TimeZone.getTimeZone("GMT-08:00");
        TimeZone.setDefault(pstTime);
    }

    @Test
    public void testUpdateUserData() throws Exception {
        mCollector.updateUserData(mUserData);

        // Test initial collection.
        // TODO(b/261748573): Add manual tests for histogram updates
        assertNotEquals(0, mUserData.utcOffset);
        assertTrue(mUserData.availableStorageBytes >= 0);
        assertTrue(mUserData.batteryPercentage >= 0);
        assertTrue(mUserData.batteryPercentage <= 100);
        assertNotNull(mUserData.networkCapabilities);
        assertTrue(UserDataCollector.ALLOWED_NETWORK_TYPE.contains(mUserData.dataNetworkType));

        List<AppInfo> appsInfo = new ArrayList();
        mCollector.getInstalledApps(appsInfo);
        assertTrue(mUserData.appsInfo.size() > 0);
        assertEquals(mUserData.appsInfo.size(), appsInfo.size());
        for (int i = 0; i < mUserData.appsInfo.size(); ++i) {
            assertFalse(TextUtils.isEmpty(mUserData.appsInfo.get(i).packageName));
            assertEquals(mUserData.appsInfo.get(i).packageName, appsInfo.get(i).packageName);
            assertEquals(mUserData.appsInfo.get(i).installed, appsInfo.get(i).installed);
        }
    }

    @Test
    public void testRealTimeUpdate() {
        // TODO (b/307176787): test orientation modification.
        mCollector.updateUserData(mUserData);
        TimeZone tzGmt4 = TimeZone.getTimeZone("GMT+04:00");
        TimeZone.setDefault(tzGmt4);
        mCollector.getRealTimeData(mUserData);
        assertEquals(mUserData.utcOffset, 240);
    }

    @Test
    public void testFilterNetworkCapabilities() {
        NetworkCapabilities cap = new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                .setLinkDownstreamBandwidthKbps(100)
                .setLinkUpstreamBandwidthKbps(10)
                .setSsid("myssid")
                .build();
        NetworkCapabilities filteredCap = UserDataCollector.getFilteredNetworkCapabilities(cap);
        assertEquals(100, filteredCap.getLinkDownstreamBandwidthKbps());
        assertEquals(10, filteredCap.getLinkUpstreamBandwidthKbps());
        assertNull(filteredCap.getSsid());
        assertArrayEquals(
                new int[]{NetworkCapabilities.NET_CAPABILITY_NOT_METERED},
                filteredCap.getCapabilities());
    }

    @After
    public void cleanUp() {
        mCollector.clearUserData(mUserData);
        mCollector.clearMetadata();
    }
}
