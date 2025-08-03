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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.remoteauth.ranging.RangingCapabilities.RangingMethod;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link SessionInfo}. */
@RunWith(AndroidJUnit4.class)
public class SessionInfoTest {

    private static final String TEST_DEVICE_ID = new String("test_device_id");
    private static final @RangingMethod int TEST_RANGING_METHOD = RANGING_METHOD_UWB;

    @Test
    public void testBuildingSessionInfo_success() {
        final SessionInfo sessionInfo =
                new SessionInfo.Builder()
                        .setDeviceId(TEST_DEVICE_ID)
                        .setRangingMethod(TEST_RANGING_METHOD)
                        .build();

        assertEquals(sessionInfo.getDeviceId(), TEST_DEVICE_ID);
        assertEquals(sessionInfo.getRangingMethod(), TEST_RANGING_METHOD);
    }

    @Test
    public void testBuildingSessionInfo_invalidDeviceId() {
        final SessionInfo.Builder builder =
                new SessionInfo.Builder().setRangingMethod(TEST_RANGING_METHOD);

        assertThrows(IllegalArgumentException.class, () -> builder.build());
    }

    @Test
    public void testBuildingSessionInfo_invalidRangingMethod() {
        final SessionInfo.Builder builder = new SessionInfo.Builder().setDeviceId(TEST_DEVICE_ID);

        assertThrows(IllegalArgumentException.class, () -> builder.build());
    }
}
