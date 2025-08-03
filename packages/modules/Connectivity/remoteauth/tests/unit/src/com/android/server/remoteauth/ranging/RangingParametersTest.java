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

import static androidx.core.uwb.backend.impl.internal.RangingDevice.SESSION_ID_UNSET;
import static androidx.core.uwb.backend.impl.internal.Utils.CONFIG_PROVISIONED_UNICAST_DS_TWR;
import static androidx.core.uwb.backend.impl.internal.Utils.DURATION_1_MS;
import static androidx.core.uwb.backend.impl.internal.Utils.NORMAL;

import static com.google.uwb.support.fira.FiraParams.UWB_CHANNEL_9;

import static org.junit.Assert.assertEquals;

import androidx.core.uwb.backend.impl.internal.UwbAddress;
import androidx.core.uwb.backend.impl.internal.UwbComplexChannel;
import androidx.core.uwb.backend.impl.internal.UwbRangeDataNtfConfig;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/** Unit test for {@link RangingParameters}. */
@RunWith(AndroidJUnit4.class)
public class RangingParametersTest {

    private static final UwbAddress TEST_UWB_LOCAL_ADDRESS =
            UwbAddress.fromBytes(new byte[] {0x00, 0x01});
    private static final androidx.core.uwb.backend.impl.internal.RangingParameters
            TEST_UWB_RANGING_PARAMETERS =
                    new androidx.core.uwb.backend.impl.internal.RangingParameters(
                            CONFIG_PROVISIONED_UNICAST_DS_TWR,
                            /* sessionId= */ SESSION_ID_UNSET,
                            /* subSessionId= */ SESSION_ID_UNSET,
                            /* SessionInfo= */ new byte[] {},
                            /* subSessionInfo= */ new byte[] {},
                            new UwbComplexChannel(UWB_CHANNEL_9, /* preambleIndex= */ 9),
                            List.of(UwbAddress.fromBytes(new byte[] {0x00, 0x02})),
                            /* rangingUpdateRate= */ NORMAL,
                            new UwbRangeDataNtfConfig.Builder().build(),
                            /* slotDuration= */ DURATION_1_MS,
                            /* isAoaDisabled= */ false);

    @Test
    public void testBuildingRangingParameters_success() {
        final RangingParameters rangingParameters =
                new RangingParameters.Builder()
                        .setUwbLocalAddress(TEST_UWB_LOCAL_ADDRESS)
                        .setUwbRangingParameters(TEST_UWB_RANGING_PARAMETERS)
                        .build();

        assertEquals(rangingParameters.getUwbLocalAddress(), TEST_UWB_LOCAL_ADDRESS);
        assertEquals(rangingParameters.getUwbRangingParameters(), TEST_UWB_RANGING_PARAMETERS);
    }
}
