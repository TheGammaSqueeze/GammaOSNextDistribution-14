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

package android.net.vcn.cts;

import static org.junit.Assert.assertEquals;

import android.net.vcn.VcnUnderlyingNetworkTemplate;

public class VcnUnderlyingNetworkTemplateTestBase {
    protected static final int MIN_ENTRY_DOWN_BANDWIDTH_KBPS = 4000;
    protected static final int MIN_EXIT_DOWN_BANDWIDTH_KBPS = 3000;
    protected static final int MIN_ENTRY_UP_BANDWIDTH_KBPS = 2000;
    protected static final int MIN_EXIT_UP_BANDWIDTH_KBPS = 1000;

    protected static void verifyBandwidthsWithTestValues(
            VcnUnderlyingNetworkTemplate networkTemplate) {
        assertEquals(
                MIN_ENTRY_DOWN_BANDWIDTH_KBPS,
                networkTemplate.getMinEntryDownstreamBandwidthKbps());
        assertEquals(
                MIN_EXIT_DOWN_BANDWIDTH_KBPS, networkTemplate.getMinExitDownstreamBandwidthKbps());
        assertEquals(
                MIN_ENTRY_UP_BANDWIDTH_KBPS, networkTemplate.getMinEntryUpstreamBandwidthKbps());
        assertEquals(MIN_EXIT_UP_BANDWIDTH_KBPS, networkTemplate.getMinExitUpstreamBandwidthKbps());
    }

    protected static void verifyBandwidthsWithDefaultValues(
            VcnUnderlyingNetworkTemplate networkTemplate) {
        assertEquals(0, networkTemplate.getMinEntryDownstreamBandwidthKbps());
        assertEquals(0, networkTemplate.getMinEntryDownstreamBandwidthKbps());
        assertEquals(0, networkTemplate.getMinEntryUpstreamBandwidthKbps());
        assertEquals(0, networkTemplate.getMinEntryUpstreamBandwidthKbps());
    }
}
