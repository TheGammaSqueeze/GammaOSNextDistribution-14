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

import static com.android.server.remoteauth.ranging.RangingCapabilities.RANGING_METHOD_UWB;
import static com.android.server.remoteauth.ranging.SessionParameters.DEVICE_ROLE_INITIATOR;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.remoteauth.ranging.RangingCapabilities.RangingMethod;
import com.android.server.remoteauth.ranging.SessionParameters.DeviceRole;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link SessionParameters}. */
@RunWith(AndroidJUnit4.class)
public class SessionParametersTest {

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

    @Test
    public void testBuildingSessionParameters_success() {
        final SessionParameters sessionParameters =
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

        assertEquals(sessionParameters.getDeviceId(), TEST_DEVICE_ID);
        assertEquals(sessionParameters.getRangingMethod(), TEST_RANGING_METHOD);
        assertEquals(
                sessionParameters.getLowerProximityBoundaryM(),
                TEST_LOWER_PROXIMITY_BOUNDARY_M,
                0.0f);
        assertEquals(
                sessionParameters.getUpperProximityBoundaryM(),
                TEST_UPPER_PROXIMITY_BOUNDARY_M,
                0.0f);
        assertEquals(sessionParameters.getAutoDeriveParams(), TEST_AUTO_DERIVE_PARAMS);
        assertArrayEquals(sessionParameters.getBaseKey(), TEST_BASE_KEY);
        assertArrayEquals(sessionParameters.getSyncData(), TEST_SYNC_DATA);
    }

    @Test
    public void testBuildingSessionParameters_invalidDeviceId() {
        final SessionParameters.Builder builder =
                new SessionParameters.Builder()
                        .setRangingMethod(TEST_RANGING_METHOD)
                        .setDeviceRole(TEST_DEVICE_ROLE)
                        .setLowerProximityBoundaryM(TEST_LOWER_PROXIMITY_BOUNDARY_M)
                        .setUpperProximityBoundaryM(TEST_UPPER_PROXIMITY_BOUNDARY_M)
                        .setBaseKey(TEST_BASE_KEY)
                        .setSyncData(TEST_SYNC_DATA);

        assertThrows(IllegalArgumentException.class, () -> builder.build());
    }

    @Test
    public void testBuildingSessionParameters_invalidRangingMethod() {
        final SessionParameters.Builder builder =
                new SessionParameters.Builder()
                        .setDeviceId(TEST_DEVICE_ID)
                        .setDeviceRole(TEST_DEVICE_ROLE)
                        .setLowerProximityBoundaryM(TEST_LOWER_PROXIMITY_BOUNDARY_M)
                        .setUpperProximityBoundaryM(TEST_UPPER_PROXIMITY_BOUNDARY_M)
                        .setBaseKey(TEST_BASE_KEY)
                        .setSyncData(TEST_SYNC_DATA);

        assertThrows(IllegalArgumentException.class, () -> builder.build());
    }

    @Test
    public void testBuildingSessionParameters_invalidDeviceRole() {
        final SessionParameters.Builder builder =
                new SessionParameters.Builder()
                        .setDeviceId(TEST_DEVICE_ID)
                        .setRangingMethod(TEST_RANGING_METHOD)
                        .setLowerProximityBoundaryM(TEST_LOWER_PROXIMITY_BOUNDARY_M)
                        .setUpperProximityBoundaryM(TEST_UPPER_PROXIMITY_BOUNDARY_M)
                        .setBaseKey(TEST_BASE_KEY)
                        .setSyncData(TEST_SYNC_DATA);

        assertThrows(IllegalArgumentException.class, () -> builder.build());
    }

    @Test
    public void testBuildingSessionParameters_invalidLowerProximityBoundaryM() {
        final SessionParameters.Builder builder =
                new SessionParameters.Builder()
                        .setDeviceId(TEST_DEVICE_ID)
                        .setRangingMethod(TEST_RANGING_METHOD)
                        .setDeviceRole(TEST_DEVICE_ROLE)
                        .setLowerProximityBoundaryM(-1.0f)
                        .setUpperProximityBoundaryM(TEST_UPPER_PROXIMITY_BOUNDARY_M)
                        .setBaseKey(TEST_BASE_KEY)
                        .setSyncData(TEST_SYNC_DATA);

        assertThrows(IllegalArgumentException.class, () -> builder.build());
    }

    @Test
    public void testBuildingSessionParameters_invalidUpperProximityBoundaryM() {
        final SessionParameters.Builder builder =
                new SessionParameters.Builder()
                        .setDeviceId(TEST_DEVICE_ID)
                        .setRangingMethod(TEST_RANGING_METHOD)
                        .setDeviceRole(TEST_DEVICE_ROLE)
                        .setLowerProximityBoundaryM(TEST_LOWER_PROXIMITY_BOUNDARY_M)
                        .setUpperProximityBoundaryM(TEST_LOWER_PROXIMITY_BOUNDARY_M - 0.1f)
                        .setBaseKey(TEST_BASE_KEY)
                        .setSyncData(TEST_SYNC_DATA);

        assertThrows(IllegalArgumentException.class, () -> builder.build());
    }

    @Test
    public void testBuildingSessionParameters_disableAutoDeriveParams() {
        final boolean autoDeriveParams = false;
        final SessionParameters sessionParameters =
                new SessionParameters.Builder()
                        .setDeviceId(TEST_DEVICE_ID)
                        .setRangingMethod(TEST_RANGING_METHOD)
                        .setDeviceRole(TEST_DEVICE_ROLE)
                        .setLowerProximityBoundaryM(TEST_LOWER_PROXIMITY_BOUNDARY_M)
                        .setUpperProximityBoundaryM(TEST_UPPER_PROXIMITY_BOUNDARY_M)
                        .setAutoDeriveParams(autoDeriveParams)
                        .build();

        assertEquals(sessionParameters.getAutoDeriveParams(), autoDeriveParams);
        assertArrayEquals(sessionParameters.getBaseKey(), new byte[] {});
        assertArrayEquals(sessionParameters.getSyncData(), new byte[] {});
    }

    @Test
    public void testBuildingSessionParameters_emptyBaseKey() {
        final SessionParameters.Builder builder =
                new SessionParameters.Builder()
                        .setDeviceId(TEST_DEVICE_ID)
                        .setRangingMethod(TEST_RANGING_METHOD)
                        .setDeviceRole(TEST_DEVICE_ROLE)
                        .setLowerProximityBoundaryM(TEST_LOWER_PROXIMITY_BOUNDARY_M)
                        .setUpperProximityBoundaryM(TEST_UPPER_PROXIMITY_BOUNDARY_M)
                        .setAutoDeriveParams(TEST_AUTO_DERIVE_PARAMS)
                        .setSyncData(TEST_SYNC_DATA);

        assertThrows(IllegalArgumentException.class, () -> builder.build());
    }

    @Test
    public void testBuildingSessionParameters_invalidBaseKey() {
        final SessionParameters.Builder builder =
                new SessionParameters.Builder()
                        .setDeviceId(TEST_DEVICE_ID)
                        .setRangingMethod(TEST_RANGING_METHOD)
                        .setDeviceRole(TEST_DEVICE_ROLE)
                        .setLowerProximityBoundaryM(TEST_LOWER_PROXIMITY_BOUNDARY_M)
                        .setUpperProximityBoundaryM(TEST_UPPER_PROXIMITY_BOUNDARY_M)
                        .setAutoDeriveParams(TEST_AUTO_DERIVE_PARAMS)
                        .setBaseKey(new byte[] {0x00, 0x01, 0x02, 0x13})
                        .setSyncData(TEST_SYNC_DATA);

        assertThrows(IllegalArgumentException.class, () -> builder.build());
    }

    @Test
    public void testBuildingSessionParameters_emptySyncData() {
        final SessionParameters.Builder builder =
                new SessionParameters.Builder()
                        .setDeviceId(TEST_DEVICE_ID)
                        .setRangingMethod(TEST_RANGING_METHOD)
                        .setDeviceRole(TEST_DEVICE_ROLE)
                        .setLowerProximityBoundaryM(TEST_LOWER_PROXIMITY_BOUNDARY_M)
                        .setUpperProximityBoundaryM(TEST_UPPER_PROXIMITY_BOUNDARY_M)
                        .setAutoDeriveParams(TEST_AUTO_DERIVE_PARAMS)
                        .setBaseKey(TEST_BASE_KEY);

        assertThrows(IllegalArgumentException.class, () -> builder.build());
    }

    @Test
    public void testBuildingSessionParameters_invalidSyncData() {
        final SessionParameters.Builder builder =
                new SessionParameters.Builder()
                        .setDeviceId(TEST_DEVICE_ID)
                        .setRangingMethod(TEST_RANGING_METHOD)
                        .setDeviceRole(TEST_DEVICE_ROLE)
                        .setLowerProximityBoundaryM(TEST_LOWER_PROXIMITY_BOUNDARY_M)
                        .setUpperProximityBoundaryM(TEST_UPPER_PROXIMITY_BOUNDARY_M)
                        .setAutoDeriveParams(TEST_AUTO_DERIVE_PARAMS)
                        .setBaseKey(TEST_BASE_KEY)
                        .setSyncData(new byte[] {0x00, 0x01, 0x02, 0x13});

        assertThrows(IllegalArgumentException.class, () -> builder.build());
    }
}
