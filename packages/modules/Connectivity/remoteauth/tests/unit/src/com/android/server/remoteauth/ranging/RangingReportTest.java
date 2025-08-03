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

import static com.android.server.remoteauth.ranging.RangingReport.PROXIMITY_STATE_INSIDE;

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.remoteauth.ranging.RangingReport.ProximityState;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link RangingReport}. */
@RunWith(AndroidJUnit4.class)
public class RangingReportTest {

    private static final float TEST_DISTANCE_M = 1.5f;
    @ProximityState private static final int TEST_PROXIMITY_STATE = PROXIMITY_STATE_INSIDE;

    @Test
    public void testBuildingRangingReport_success() {
        final RangingReport rangingReport =
                new RangingReport.Builder()
                        .setDistanceM(TEST_DISTANCE_M)
                        .setProximityState(TEST_PROXIMITY_STATE)
                        .build();

        assertEquals(rangingReport.getDistanceM(), TEST_DISTANCE_M, 0.0f);
        assertEquals(rangingReport.getProximityState(), TEST_PROXIMITY_STATE);
    }
}
