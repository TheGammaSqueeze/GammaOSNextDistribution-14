/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.remoteauth.ranging;

import static android.content.pm.PackageManager.FEATURE_UWB;
import static android.uwb.UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE;

import static androidx.core.uwb.backend.impl.internal.RangingCapabilities.FIRA_DEFAULT_SUPPORTED_CONFIG_IDS;
import static androidx.core.uwb.backend.impl.internal.Utils.CONFIG_PROVISIONED_INDIVIDUAL_MULTICAST_DS_TWR;
import static androidx.core.uwb.backend.impl.internal.Utils.CONFIG_PROVISIONED_MULTICAST_DS_TWR;
import static androidx.core.uwb.backend.impl.internal.Utils.CONFIG_PROVISIONED_UNICAST_DS_TWR;
import static androidx.core.uwb.backend.impl.internal.Utils.CONFIG_PROVISIONED_UNICAST_DS_TWR_NO_AOA;

import static com.android.server.remoteauth.ranging.RangingCapabilities.RANGING_METHOD_UWB;
import static com.android.server.remoteauth.ranging.SessionParameters.DEVICE_ROLE_INITIATOR;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.uwb.UwbManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.remoteauth.ranging.RangingCapabilities.RangingMethod;
import com.android.server.remoteauth.ranging.SessionParameters.DeviceRole;

import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraSpecificationParams;
import com.google.uwb.support.generic.GenericSpecificationParams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Unit test for {@link RangingManager}. */
@RunWith(AndroidJUnit4.class)
public class RangingManagerTest {
    private static final List<Integer> TEST_UWB_SUPPORTED_CHANNELS = List.of(8, 9);
    private static final FiraSpecificationParams TEST_FIRA_SPEC =
            new FiraSpecificationParams.Builder()
                    .setSupportedChannels(TEST_UWB_SUPPORTED_CHANNELS)
                    .setStsCapabilities(EnumSet.allOf(FiraParams.StsCapabilityFlag.class))
                    .build();
    private static final GenericSpecificationParams TEST_GENERIC_SPEC =
            new GenericSpecificationParams.Builder()
                    .setFiraSpecificationParams(TEST_FIRA_SPEC)
                    .build();
    private static final String TEST_DEVICE_ID = "test_device_id";
    @RangingMethod private static final int TEST_RANGING_METHOD = RANGING_METHOD_UWB;
    @DeviceRole private static final int TEST_DEVICE_ROLE = DEVICE_ROLE_INITIATOR;
    private static final float TEST_LOWER_PROXIMITY_BOUNDARY_M = 1.0f;
    private static final float TEST_UPPER_PROXIMITY_BOUNDARY_M = 2.5f;
    private static final boolean TEST_AUTO_DERIVE_PARAMS = true;
    private static final byte[] TEST_BASE_KEY =
            new byte[] {
                0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d,
                0x0e, 0x0f
            };
    private static final byte[] TEST_SYNC_DATA =
            new byte[] {
                0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e,
                0x0f, 0x00
            };
    private static final SessionParameters TEST_SESSION_PARAMETER =
            new SessionParameters.Builder()
                    .setDeviceId(TEST_DEVICE_ID)
                    .setRangingMethod(TEST_RANGING_METHOD)
                    .setDeviceRole(TEST_DEVICE_ROLE)
                    .setLowerProximityBoundaryM(TEST_LOWER_PROXIMITY_BOUNDARY_M)
                    .setUpperProximityBoundaryM(TEST_UPPER_PROXIMITY_BOUNDARY_M)
                    .setAutoDeriveParams(TEST_AUTO_DERIVE_PARAMS)
                    .setBaseKey(TEST_BASE_KEY)
                    .setSyncData(TEST_SYNC_DATA)
                    .build();

    @Mock private Context mContext;
    @Mock private PackageManager mPackageManager;
    @Mock private UwbManager mUwbManager;

    private RangingManager mRangingManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mContext.getSystemService(UwbManager.class)).thenReturn(mUwbManager);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.hasSystemFeature(FEATURE_UWB)).thenReturn(false);
        when(mUwbManager.getAdapterState()).thenReturn(STATE_ENABLED_INACTIVE);
        when(mUwbManager.getSpecificationInfo()).thenReturn(TEST_GENERIC_SPEC.toBundle());
    }

    @Test
    public void testConstruction() {
        mRangingManager = new RangingManager(mContext);
        verifyZeroInteractions(mUwbManager);
    }

    @Test
    public void testConstruction_withUwbEnabled() {
        when(mPackageManager.hasSystemFeature(FEATURE_UWB)).thenReturn(true);

        mRangingManager = new RangingManager(mContext);

        verify(mUwbManager).getAdapterState();
        verify(mUwbManager).registerAdapterStateCallback(any(), any());
    }

    @Test
    public void testShutdown_withUwbEnabled() {
        when(mPackageManager.hasSystemFeature(FEATURE_UWB)).thenReturn(true);

        mRangingManager = new RangingManager(mContext);
        mRangingManager.shutdown();

        verify(mUwbManager).registerAdapterStateCallback(any(), any());
        verify(mUwbManager).unregisterAdapterStateCallback(any());
    }

    @Test
    public void testGetRangingCapabilities() {
        mRangingManager = new RangingManager(mContext);
        RangingCapabilities capabilities = mRangingManager.getRangingCapabilities();

        assertEquals(capabilities.getSupportedRangingMethods().size(), 0);
        assertEquals(capabilities.getUwbRangingCapabilities(), null);
    }

    @Test
    public void testGetRangingCapabilities_withUwbEnabled() {
        when(mPackageManager.hasSystemFeature(FEATURE_UWB)).thenReturn(true);

        mRangingManager = new RangingManager(mContext);
        RangingCapabilities capabilities = mRangingManager.getRangingCapabilities();

        List<Integer> supportedConfigIds = new ArrayList<>(FIRA_DEFAULT_SUPPORTED_CONFIG_IDS);
        supportedConfigIds.add(CONFIG_PROVISIONED_UNICAST_DS_TWR);
        supportedConfigIds.add(CONFIG_PROVISIONED_MULTICAST_DS_TWR);
        supportedConfigIds.add(CONFIG_PROVISIONED_UNICAST_DS_TWR_NO_AOA);
        supportedConfigIds.add(CONFIG_PROVISIONED_INDIVIDUAL_MULTICAST_DS_TWR);

        verify(mUwbManager, times(1)).getSpecificationInfo();
        assertEquals(capabilities.getSupportedRangingMethods().size(), 1);
        assertEquals((int) capabilities.getSupportedRangingMethods().get(0), RANGING_METHOD_UWB);
        androidx.core.uwb.backend.impl.internal.RangingCapabilities uwbCapabilities =
                capabilities.getUwbRangingCapabilities();
        assertNotNull(uwbCapabilities);
        assertArrayEquals(
                uwbCapabilities.getSupportedChannels().toArray(),
                TEST_UWB_SUPPORTED_CHANNELS.toArray());
        assertArrayEquals(
                uwbCapabilities.getSupportedConfigIds().toArray(), supportedConfigIds.toArray());
    }

    @Test
    public void testGetRangingCapabilities_multipleCalls() {
        when(mPackageManager.hasSystemFeature(FEATURE_UWB)).thenReturn(true);

        mRangingManager = new RangingManager(mContext);
        RangingCapabilities capabilities1 = mRangingManager.getRangingCapabilities();
        RangingCapabilities capabilities2 = mRangingManager.getRangingCapabilities();
        RangingCapabilities capabilities3 = mRangingManager.getRangingCapabilities();

        verify(mUwbManager, times(1)).getSpecificationInfo();
        assertEquals(capabilities1, capabilities2);
        assertEquals(capabilities2, capabilities3);
    }

    @Test
    public void testCreateSession_nullSessionParameters() {
        mRangingManager = new RangingManager(mContext);

        assertThrows(NullPointerException.class, () -> mRangingManager.createSession(null));
    }

    @Test
    public void testCreateSession_uwbSessionWithUwbDisabled() {
        mRangingManager = new RangingManager(mContext);

        assertNull(mRangingManager.createSession(TEST_SESSION_PARAMETER));
    }

    @Test
    public void testCreateSession_uwbSession() {
        when(mPackageManager.hasSystemFeature(FEATURE_UWB)).thenReturn(true);
        mRangingManager = new RangingManager(mContext);

        assertNotNull(mRangingManager.createSession(TEST_SESSION_PARAMETER));
    }
}
