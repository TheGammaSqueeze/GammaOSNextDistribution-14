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

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link RangingCapabilities}. */
@RunWith(AndroidJUnit4.class)
public class RangingCapabilitiesTest {
    private static final androidx.core.uwb.backend.impl.internal.RangingCapabilities
            TEST_UWB_RANGING_CAPABILITIES =
                    new androidx.core.uwb.backend.impl.internal.RangingCapabilities(
                            /* supportsDistance= */ true,
                            /* supportsAzimuthalAngle= */ true,
                            /* supportsElevationAngle= */ true);

    @Test
    public void testBuildingRangingCapabilities_success() {
        final RangingCapabilities rangingCapabilities =
                new RangingCapabilities.Builder()
                        .addSupportedRangingMethods(RANGING_METHOD_UWB)
                        .setUwbRangingCapabilities(TEST_UWB_RANGING_CAPABILITIES)
                        .build();

        assertEquals(rangingCapabilities.getSupportedRangingMethods().size(), 1);
        assertEquals(
                (int) rangingCapabilities.getSupportedRangingMethods().get(0), RANGING_METHOD_UWB);
        assertEquals(
                rangingCapabilities.getUwbRangingCapabilities(), TEST_UWB_RANGING_CAPABILITIES);
    }
}
