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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.remoteauth.ranging.RangingCapabilities.RangingMethod;
import com.android.server.remoteauth.ranging.SessionParameters.DeviceRole;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;

/** Unit test for {@link RangingSession}. */
@RunWith(AndroidJUnit4.class)
public class RangingSessionTest {

    private static final String TEST_DEVICE_ID = "test_device_id";
    @RangingMethod private static final int TEST_RANGING_METHOD = RANGING_METHOD_UWB;
    @DeviceRole private static final int TEST_DEVICE_ROLE = DEVICE_ROLE_INITIATOR;
    private static final float TEST_LOWER_PROXIMITY_BOUNDARY_M = 1.0f;
    private static final float TEST_UPPER_PROXIMITY_BOUNDARY_M = 2.5f;
    private static final byte[] TEST_BASE_KEY =
            new byte[] {
                0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d,
                0x0e, 0x0f
            };
    private static final byte[] TEST_BASE_KEY2 =
            new byte[] {
                0x0, 0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x0, 0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x0,
                0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x0, 0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7
            };
    private static final byte[] TEST_SYNC_DATA =
            new byte[] {
                0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e,
                0x0f, 0x00
            };
    private static final byte[] TEST_SYNC_DATA2 =
            new byte[] {
                0x00, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e,
                0x0f, 0x00
            };

    private static final SessionParameters TEST_SESSION_PARAMETER_WITH_AD =
            new SessionParameters.Builder()
                    .setDeviceId(TEST_DEVICE_ID)
                    .setRangingMethod(TEST_RANGING_METHOD)
                    .setDeviceRole(TEST_DEVICE_ROLE)
                    .setLowerProximityBoundaryM(TEST_LOWER_PROXIMITY_BOUNDARY_M)
                    .setUpperProximityBoundaryM(TEST_UPPER_PROXIMITY_BOUNDARY_M)
                    .setAutoDeriveParams(true)
                    .setBaseKey(TEST_BASE_KEY)
                    .setSyncData(TEST_SYNC_DATA)
                    .build();
    private static final SessionParameters TEST_SESSION_PARAMETER_WO_AD =
            new SessionParameters.Builder()
                    .setDeviceId(TEST_DEVICE_ID)
                    .setRangingMethod(TEST_RANGING_METHOD)
                    .setDeviceRole(TEST_DEVICE_ROLE)
                    .setLowerProximityBoundaryM(TEST_LOWER_PROXIMITY_BOUNDARY_M)
                    .setUpperProximityBoundaryM(TEST_UPPER_PROXIMITY_BOUNDARY_M)
                    .setAutoDeriveParams(false)
                    .setBaseKey(TEST_BASE_KEY)
                    .setSyncData(TEST_SYNC_DATA)
                    .build();
    private static final int TEST_DERIVE_DATA_LENGTH = 40;

    /** Wrapper class for testing {@link RangingSession}. */
    public static class RangingSessionWrapper extends RangingSession {
        public RangingSessionWrapper(
                Context context, SessionParameters sessionParameters, int derivedDataLength) {
            super(context, sessionParameters, derivedDataLength);
        }

        @Override
        public void start(
                RangingParameters rangingParameters,
                Executor executor,
                RangingCallback rangingCallback) {}

        @Override
        public void stop() {}

        @Override
        public boolean updateDerivedData() {
            return super.updateDerivedData();
        }

        public byte[] baseKey() {
            return mBaseKey;
        }

        public byte[] syncData() {
            return mSyncData;
        }

        public byte[] derivedData() {
            return mDerivedData;
        }

        public int syncCounter() {
            return mSyncCounter;
        }
    }

    @Mock private Context mContext;

    private RangingSessionWrapper mRangingSessionWithAD;
    private RangingSessionWrapper mRangingSessionWithoutAD;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mRangingSessionWithAD =
                new RangingSessionWrapper(
                        mContext, TEST_SESSION_PARAMETER_WITH_AD, TEST_DERIVE_DATA_LENGTH);
        mRangingSessionWithoutAD =
                new RangingSessionWrapper(mContext, TEST_SESSION_PARAMETER_WO_AD, 0);
    }

    @Test
    public void testResetBaseKey_autoDeriveDisabled() {
        assertNull(mRangingSessionWithoutAD.baseKey());
        mRangingSessionWithoutAD.resetBaseKey(TEST_BASE_KEY2);
        assertNull(mRangingSessionWithoutAD.baseKey());
    }

    @Test
    public void testResetBaseKey_nullBaseKey() {
        assertThrows(NullPointerException.class, () -> mRangingSessionWithAD.resetBaseKey(null));
    }

    @Test
    public void testResetBaseKey_invalidBaseKey() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mRangingSessionWithAD.resetBaseKey(new byte[] {0x1, 0x2, 0x3, 0x4}));
    }

    @Test
    public void testResetBaseKey_success() {
        mRangingSessionWithAD.resetBaseKey(TEST_BASE_KEY2);
        assertArrayEquals(mRangingSessionWithAD.baseKey(), TEST_BASE_KEY2);
        assertEquals(mRangingSessionWithAD.syncCounter(), 2);

        mRangingSessionWithAD.resetBaseKey(TEST_BASE_KEY);
        assertArrayEquals(mRangingSessionWithAD.baseKey(), TEST_BASE_KEY);
        assertEquals(mRangingSessionWithAD.syncCounter(), 3);
    }

    @Test
    public void testResetSyncData_autoDeriveDisabled() {
        assertNull(mRangingSessionWithoutAD.syncData());
        mRangingSessionWithoutAD.resetSyncData(TEST_SYNC_DATA2);
        assertNull(mRangingSessionWithoutAD.syncData());
    }

    @Test
    public void testResetSyncData_nullSyncData() {
        assertThrows(NullPointerException.class, () -> mRangingSessionWithAD.resetSyncData(null));
    }

    @Test
    public void testResetSyncData_invalidSyncData() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mRangingSessionWithAD.resetSyncData(new byte[] {0x1, 0x2, 0x3, 0x4}));
    }

    @Test
    public void testResetSyncData_success() {
        mRangingSessionWithAD.resetSyncData(TEST_SYNC_DATA2);
        assertArrayEquals(mRangingSessionWithAD.syncData(), TEST_SYNC_DATA2);
        assertEquals(mRangingSessionWithAD.syncCounter(), 1);

        mRangingSessionWithAD.resetSyncData(TEST_SYNC_DATA);
        assertArrayEquals(mRangingSessionWithAD.syncData(), TEST_SYNC_DATA);
        assertEquals(mRangingSessionWithAD.syncCounter(), 1);
    }

    @Test
    public void testUpdateDerivedData_autoDeriveDisabled() {
        assertFalse(mRangingSessionWithoutAD.updateDerivedData());
        assertEquals(mRangingSessionWithoutAD.syncCounter(), 0);
    }

    @Test
    public void testUpdateDerivedData_hkdfFailed() {
        // Max derivedDataLength is 32*255
        RangingSessionWrapper rangingSession =
                new RangingSessionWrapper(
                        mContext, TEST_SESSION_PARAMETER_WITH_AD, /* derivedDataLength= */ 10000);
        assertNull(rangingSession.derivedData());
        assertFalse(rangingSession.updateDerivedData());
        assertEquals(rangingSession.syncCounter(), 0);
        assertNull(rangingSession.derivedData());
    }

    @Test
    public void testUpdateDerivedData_success() {
        assertNotNull(mRangingSessionWithAD.derivedData());
        assertTrue(mRangingSessionWithAD.updateDerivedData());
        assertEquals(mRangingSessionWithAD.syncCounter(), 2);
        assertNotNull(mRangingSessionWithAD.derivedData());
    }
}
