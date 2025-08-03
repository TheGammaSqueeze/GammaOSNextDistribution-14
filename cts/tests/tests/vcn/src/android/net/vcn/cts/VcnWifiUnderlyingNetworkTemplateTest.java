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

import static android.net.vcn.VcnUnderlyingNetworkTemplate.MATCH_ANY;
import static android.net.vcn.VcnUnderlyingNetworkTemplate.MATCH_FORBIDDEN;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.net.vcn.VcnWifiUnderlyingNetworkTemplate;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class VcnWifiUnderlyingNetworkTemplateTest extends VcnUnderlyingNetworkTemplateTestBase {
    private static final Set<String> SSIDS = Set.of("TestWifi");

    // Package private for use in VcnGatewayConnectionConfigTest
    static VcnWifiUnderlyingNetworkTemplate getTestNetworkTemplate() {
        return new VcnWifiUnderlyingNetworkTemplate.Builder()
                .setMetered(MATCH_FORBIDDEN)
                .setSsids(SSIDS)
                .setMinDownstreamBandwidthKbps(
                        MIN_ENTRY_DOWN_BANDWIDTH_KBPS, MIN_EXIT_DOWN_BANDWIDTH_KBPS)
                .setMinUpstreamBandwidthKbps(
                        MIN_ENTRY_UP_BANDWIDTH_KBPS, MIN_EXIT_UP_BANDWIDTH_KBPS)
                .build();
    }

    @Test
    public void testBuilderAndGetters() {
        final VcnWifiUnderlyingNetworkTemplate networkTemplate = getTestNetworkTemplate();
        assertEquals(MATCH_FORBIDDEN, networkTemplate.getMetered());
        assertEquals(SSIDS, networkTemplate.getSsids());
        verifyBandwidthsWithTestValues(networkTemplate);
    }

    @Test
    public void testBuilderAndGettersForDefaultValues() {
        final VcnWifiUnderlyingNetworkTemplate networkTemplate =
                new VcnWifiUnderlyingNetworkTemplate.Builder().build();
        assertEquals(MATCH_ANY, networkTemplate.getMetered());
        assertEquals(new HashSet<String>(), networkTemplate.getSsids());
        verifyBandwidthsWithDefaultValues(networkTemplate);
    }

    @Test
    public void testBuildWithEmptySets() {
        final VcnWifiUnderlyingNetworkTemplate networkTemplate =
                new VcnWifiUnderlyingNetworkTemplate.Builder()
                        .setSsids(new HashSet<String>())
                        .build();
        assertEquals(new HashSet<String>(), networkTemplate.getSsids());
    }

    @Test
    public void testBuildWithNullSsids() {
        try {
            new VcnWifiUnderlyingNetworkTemplate.Builder().setSsids(null);
            fail("Expect to fail due to the null argument");
        } catch (Exception expected) {
        }
    }

    @Test
    public void testBuildWithInvalidDownstreamBandwidth() {
        try {
            new VcnWifiUnderlyingNetworkTemplate.Builder()
                    .setMinDownstreamBandwidthKbps(
                            MIN_ENTRY_DOWN_BANDWIDTH_KBPS, MIN_ENTRY_DOWN_BANDWIDTH_KBPS + 1);
            fail("Expect to fail because entry bandwidth is smaller than exit bandwidth");
        } catch (Exception expected) {
        }
    }

    @Test
    public void testBuildWithInvalidUpstreamBandwidth() {
        try {
            new VcnWifiUnderlyingNetworkTemplate.Builder()
                    .setMinUpstreamBandwidthKbps(
                            MIN_ENTRY_UP_BANDWIDTH_KBPS, MIN_ENTRY_UP_BANDWIDTH_KBPS + 1);
            fail("Expect to fail because entry bandwidth is smaller than exit bandwidth");
        } catch (Exception expected) {
        }
    }
}
