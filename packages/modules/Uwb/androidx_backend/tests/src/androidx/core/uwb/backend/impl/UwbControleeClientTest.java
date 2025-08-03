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

package androidx.core.uwb.backend.impl;

import static androidx.core.uwb.backend.impl.internal.RangingCapabilities.DEFAULT_SUPPORTED_RANGING_UPDATE_RATE;
import static androidx.core.uwb.backend.impl.internal.RangingCapabilities.DEFAULT_SUPPORTED_SLOT_DURATIONS;
import static androidx.core.uwb.backend.impl.internal.RangingCapabilities.DEFAULT_SUPPORTS_RANGING_INTERVAL_RECONFIGURE;
import static androidx.core.uwb.backend.impl.internal.RangingCapabilities.FIRA_DEFAULT_RANGING_INTERVAL_MS;
import static androidx.core.uwb.backend.impl.internal.RangingCapabilities.FIRA_DEFAULT_SUPPORTED_CHANNEL;
import static androidx.core.uwb.backend.impl.internal.RangingCapabilities.FIRA_DEFAULT_SUPPORTED_CONFIG_IDS;
import static androidx.core.uwb.backend.impl.internal.Utils.RANGE_DATA_NTF_DISABLE;
import static androidx.core.uwb.backend.impl.internal.Utils.RANGE_DATA_NTF_ENABLE;
import static androidx.core.uwb.backend.impl.internal.Utils.RANGE_DATA_NTF_ENABLE_PROXIMITY_EDGE_TRIG;
import static androidx.core.uwb.backend.impl.internal.Utils.RANGE_DATA_NTF_ENABLE_PROXIMITY_LEVEL_TRIG;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;

import androidx.core.uwb.backend.IRangingSessionCallback;
import androidx.core.uwb.backend.RangingParameters;
import androidx.core.uwb.backend.UwbComplexChannel;
import androidx.core.uwb.backend.UwbRangeDataNtfConfig;
import androidx.core.uwb.backend.impl.internal.RangingCapabilities;
import androidx.core.uwb.backend.impl.internal.RangingControlee;
import androidx.core.uwb.backend.impl.internal.RangingSessionCallback;
import androidx.core.uwb.backend.impl.internal.UwbServiceImpl;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class UwbControleeClientTest {

    @Mock private RangingControlee mRangingControlee;
    @Mock private UwbServiceImpl mUwbService;
    @Mock private IRangingSessionCallback mRangingSessionCallback;
    private UwbControleeClient mUwbControleeClient;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mUwbControleeClient = new UwbControleeClient(mRangingControlee, mUwbService);
    }

    @Test
    public void testStartRanging() throws RemoteException {
        RangingParameters params = new RangingParameters();
        params.complexChannel = new UwbComplexChannel();
        params.complexChannel.channel = 9;
        params.complexChannel.preambleIndex = 9;
        params.peerDevices = new ArrayList<>();
        params.uwbRangeDataNtfConfig = new UwbRangeDataNtfConfig();
        params.uwbRangeDataNtfConfig.rangeDataNtfConfigType = RANGE_DATA_NTF_DISABLE;
        params.slotDuration = 1;
        mUwbControleeClient.startRanging(params, mRangingSessionCallback);
        verify(mRangingControlee).setRangingParameters(any());
        verify(mRangingControlee).startRanging(
                any(RangingSessionCallback.class), any(ExecutorService.class));
    }

    @Test
    public void testGetRangingCapabilities() throws RemoteException {
        List<Integer> supportedRangeDataNtfConfigs = new ArrayList<>();
        supportedRangeDataNtfConfigs.add(RANGE_DATA_NTF_DISABLE);
        supportedRangeDataNtfConfigs.add(RANGE_DATA_NTF_ENABLE);
        supportedRangeDataNtfConfigs.add(RANGE_DATA_NTF_ENABLE_PROXIMITY_LEVEL_TRIG);
        supportedRangeDataNtfConfigs.add(RANGE_DATA_NTF_ENABLE_PROXIMITY_EDGE_TRIG);
        RangingCapabilities cap = new RangingCapabilities(
                /* Supports distance */ true,
                /* Supports Azimuth angle */ true,
                /* Supports Elevation angle */ true,
                DEFAULT_SUPPORTS_RANGING_INTERVAL_RECONFIGURE,
                FIRA_DEFAULT_RANGING_INTERVAL_MS,
                new ArrayList<>(FIRA_DEFAULT_SUPPORTED_CHANNEL),
                supportedRangeDataNtfConfigs,
                FIRA_DEFAULT_SUPPORTED_CONFIG_IDS,
                DEFAULT_SUPPORTED_SLOT_DURATIONS,
                DEFAULT_SUPPORTED_RANGING_UPDATE_RATE,
                /* Supports background ranging */false
        );
        when(mUwbService.getRangingCapabilities()).thenReturn(cap);
        androidx.core.uwb.backend.RangingCapabilities rangingCapabilities =
                mUwbControleeClient.getRangingCapabilities();

        assertTrue(rangingCapabilities.supportsDistance);
        assertTrue(rangingCapabilities.supportsAzimuthalAngle);
        assertTrue(rangingCapabilities.supportsElevationAngle);
        assertArrayEquals(rangingCapabilities.supportedNtfConfigs,
                supportedRangeDataNtfConfigs.stream().mapToInt(e -> e).toArray());
    }

    @Test
    public void testStopRanging() throws RemoteException {
        mUwbControleeClient.stopRanging(mRangingSessionCallback);
        verify(mRangingControlee).stopRanging();
    }
}
