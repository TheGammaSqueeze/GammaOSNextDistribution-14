/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.net.wifi.twt;

import static android.net.wifi.MloLink.MAX_MLO_LINK_ID;
import static android.net.wifi.MloLink.MIN_MLO_LINK_ID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/**
 * Unit tests for {@link TwtRequest}
 */
@SmallTest
public class TwtRequestTest {
    @Test
    public void testTwtRequest() {

        final int testMinWakeDuration = 100;
        final int testMaxWakeDuration = 1000;
        final int testMinWakeInterval = 9999;
        final int testMaxWakeInterval = 999999;
        final int testMloLinkId = 2;

        TwtRequest.Builder builder = new TwtRequest.Builder(testMinWakeDuration,
                testMaxWakeDuration, testMinWakeInterval, testMaxWakeInterval);

        assertThrows(IllegalArgumentException.class, () -> builder.setLinkId(MIN_MLO_LINK_ID - 1));
        assertThrows(IllegalArgumentException.class, () -> builder.setLinkId(MAX_MLO_LINK_ID + 1));

        builder.setLinkId(testMloLinkId);
        TwtRequest twtRequest = builder.build();
        assertEquals(testMinWakeDuration, twtRequest.getMinWakeDurationMicros());
        assertEquals(testMaxWakeDuration, twtRequest.getMaxWakeDurationMicros());
        assertEquals(testMinWakeInterval, twtRequest.getMinWakeIntervalMicros());
        assertEquals(testMaxWakeInterval, twtRequest.getMaxWakeIntervalMicros());
        assertEquals(testMloLinkId, twtRequest.getLinkId());
    }
}
