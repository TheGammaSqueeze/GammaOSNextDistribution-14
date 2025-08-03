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
import static android.net.vcn.VcnUnderlyingNetworkTemplate.MATCH_REQUIRED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.net.vcn.VcnCellUnderlyingNetworkTemplate;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class VcnCellUnderlyingNetworkTemplateTest extends VcnUnderlyingNetworkTemplateTestBase {
    private static final Set<String> ALLOWED_PLMN_IDS = Set.of("123456", "12345");
    private static final Set<Integer> ALLOWED_CARRIER_IDS = Set.of(100, 101);

    private static VcnCellUnderlyingNetworkTemplate.Builder getTestNetworkTemplateBuilder() {
        return new VcnCellUnderlyingNetworkTemplate.Builder()
                .setMetered(MATCH_REQUIRED)
                .setRoaming(MATCH_REQUIRED)
                .setOpportunistic(MATCH_FORBIDDEN)
                .setOperatorPlmnIds(ALLOWED_PLMN_IDS)
                .setSimSpecificCarrierIds(ALLOWED_CARRIER_IDS)
                .setMinDownstreamBandwidthKbps(
                        MIN_ENTRY_DOWN_BANDWIDTH_KBPS, MIN_EXIT_DOWN_BANDWIDTH_KBPS)
                .setMinUpstreamBandwidthKbps(
                        MIN_ENTRY_UP_BANDWIDTH_KBPS, MIN_EXIT_UP_BANDWIDTH_KBPS);
    }

    // Package private for use in VcnGatewayConnectionConfigTest
    static VcnCellUnderlyingNetworkTemplate getTestNetworkTemplate() {
        return getTestNetworkTemplateBuilder().build();
    }

    private static void setAllCapabilities(
            VcnCellUnderlyingNetworkTemplate.Builder builder, int matchCriteria) {
        builder.setCbs(matchCriteria);
        builder.setDun(matchCriteria);
        builder.setIms(matchCriteria);
        builder.setInternet(matchCriteria);
        builder.setMms(matchCriteria);
        builder.setRcs(matchCriteria);
    }

    private static void verifyAllCapabilities(
            VcnCellUnderlyingNetworkTemplate template,
            int expectMatchCriteriaforNonInternet,
            int expectMatchCriteriaforInternet) {
        assertEquals(expectMatchCriteriaforNonInternet, template.getCbs());
        assertEquals(expectMatchCriteriaforNonInternet, template.getDun());
        assertEquals(expectMatchCriteriaforNonInternet, template.getIms());
        assertEquals(expectMatchCriteriaforNonInternet, template.getMms());
        assertEquals(expectMatchCriteriaforNonInternet, template.getRcs());

        assertEquals(expectMatchCriteriaforInternet, template.getInternet());
    }

    @Test
    public void testBuilderAndGetters() {
        final VcnCellUnderlyingNetworkTemplate.Builder builder = getTestNetworkTemplateBuilder();
        setAllCapabilities(builder, MATCH_REQUIRED);

        final VcnCellUnderlyingNetworkTemplate networkTemplate = builder.build();

        assertEquals(MATCH_REQUIRED, networkTemplate.getMetered());
        assertEquals(MATCH_REQUIRED, networkTemplate.getRoaming());
        assertEquals(MATCH_FORBIDDEN, networkTemplate.getOpportunistic());
        assertEquals(ALLOWED_PLMN_IDS, networkTemplate.getOperatorPlmnIds());
        assertEquals(ALLOWED_CARRIER_IDS, networkTemplate.getSimSpecificCarrierIds());
        verifyBandwidthsWithTestValues(networkTemplate);
        verifyAllCapabilities(networkTemplate, MATCH_REQUIRED, MATCH_REQUIRED);
    }

    @Test
    public void testBuilderAndGettersForDefaultValues() {
        final VcnCellUnderlyingNetworkTemplate networkTemplate =
                new VcnCellUnderlyingNetworkTemplate.Builder().build();
        assertEquals(MATCH_ANY, networkTemplate.getMetered());
        assertEquals(MATCH_ANY, networkTemplate.getRoaming());
        assertEquals(MATCH_ANY, networkTemplate.getOpportunistic());
        assertEquals(new HashSet<String>(), networkTemplate.getOperatorPlmnIds());
        assertEquals(new HashSet<Integer>(), networkTemplate.getSimSpecificCarrierIds());
        verifyBandwidthsWithDefaultValues(networkTemplate);
        verifyAllCapabilities(networkTemplate, MATCH_ANY, MATCH_REQUIRED);
    }

    @Test
    public void testBuildWithEmptySets() {
        final VcnCellUnderlyingNetworkTemplate networkTemplate =
                new VcnCellUnderlyingNetworkTemplate.Builder()
                        .setOperatorPlmnIds(new HashSet<String>())
                        .setSimSpecificCarrierIds(new HashSet<Integer>())
                        .build();
        assertEquals(new HashSet<String>(), networkTemplate.getOperatorPlmnIds());
        assertEquals(new HashSet<Integer>(), networkTemplate.getSimSpecificCarrierIds());
    }

    @Test
    public void testBuildWithNullOperatorPlmnIds() {
        try {
            new VcnCellUnderlyingNetworkTemplate.Builder().setOperatorPlmnIds(null);
            fail("Expect to fail due to the null argument");
        } catch (Exception expected) {
        }
    }

    private static void verifyBuildWithInvalidOperatorPlmnIds(String plmnId) {
        try {
            new VcnCellUnderlyingNetworkTemplate.Builder().setOperatorPlmnIds(Set.of(plmnId));
            fail("Expect to fail due to the invalid PLMN ID");
        } catch (Exception expected) {
        }
    }

    @Test
    public void testBuildWithInvalidOperatorPlmnIds() {
        verifyBuildWithInvalidOperatorPlmnIds("1234567");
        verifyBuildWithInvalidOperatorPlmnIds("1234");
        verifyBuildWithInvalidOperatorPlmnIds("abcde");
    }

    @Test
    public void testBuildWithNullCarrierIds() {
        try {
            new VcnCellUnderlyingNetworkTemplate.Builder().setSimSpecificCarrierIds(null);
            fail("Expect to fail due to the null argument");
        } catch (Exception expected) {
        }
    }

    @Test
    public void testBuildWithInvalidDownstreamBandwidth() {
        try {
            new VcnCellUnderlyingNetworkTemplate.Builder()
                    .setMinDownstreamBandwidthKbps(
                            MIN_ENTRY_DOWN_BANDWIDTH_KBPS, MIN_ENTRY_DOWN_BANDWIDTH_KBPS + 1);
            fail("Expect to fail because entry bandwidth is smaller than exit bandwidth");
        } catch (Exception expected) {
        }
    }

    @Test
    public void testBuildWithInvalidUpstreamBandwidth() {
        try {
            new VcnCellUnderlyingNetworkTemplate.Builder()
                    .setMinUpstreamBandwidthKbps(
                            MIN_ENTRY_UP_BANDWIDTH_KBPS, MIN_ENTRY_UP_BANDWIDTH_KBPS + 1);
            fail("Expect to fail because entry bandwidth is smaller than exit bandwidth");
        } catch (Exception expected) {
        }
    }
}
