/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.net.NetworkCapabilities.NET_CAPABILITY_DUN;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.ipsec.ike.IkeSessionParams.IKE_OPTION_MOBIKE;
import static android.net.vcn.VcnGatewayConnectionConfig.VCN_GATEWAY_OPTION_ENABLE_DATA_STALL_RECOVERY_WITH_MOBILITY;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.net.ipsec.ike.IkeSessionParams;
import android.net.ipsec.ike.IkeTunnelConnectionParams;
import android.net.vcn.VcnGatewayConnectionConfig;
import android.net.vcn.VcnUnderlyingNetworkTemplate;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class VcnGatewayConnectionConfigTest extends VcnTestBase {
    private static final String VCN_GATEWAY_CONNECTION_NAME = "test-vcn-gateway-connection";
    private static final long[] RETRY_INTERNAL_MILLIS =
            new long[] {
                TimeUnit.SECONDS.toMillis(1),
                TimeUnit.MINUTES.toMillis(1),
                TimeUnit.HOURS.toMillis(1)
            };
    private static final int MAX_MTU = 1360;

    private static final List<VcnUnderlyingNetworkTemplate> UNDERLYING_NETWORK_TEMPLATES;

    static {
        List<VcnUnderlyingNetworkTemplate> nwTemplates = new ArrayList<>();
        nwTemplates.add(VcnCellUnderlyingNetworkTemplateTest.getTestNetworkTemplate());
        nwTemplates.add(VcnWifiUnderlyingNetworkTemplateTest.getTestNetworkTemplate());
        UNDERLYING_NETWORK_TEMPLATES = Collections.unmodifiableList(nwTemplates);
    }

    private static final Set<Integer> GATEWAY_OPTIONS =
            Collections.singleton(VCN_GATEWAY_OPTION_ENABLE_DATA_STALL_RECOVERY_WITH_MOBILITY);

    public static VcnGatewayConnectionConfig.Builder buildVcnGatewayConnectionConfigBase() {
        return new VcnGatewayConnectionConfig.Builder(
                        VCN_GATEWAY_CONNECTION_NAME, buildTunnelConnectionParams())
                .addExposedCapability(NET_CAPABILITY_INTERNET)
                .setRetryIntervalsMillis(RETRY_INTERNAL_MILLIS)
                .setMaxMtu(MAX_MTU)
                .setVcnUnderlyingNetworkPriorities(UNDERLYING_NETWORK_TEMPLATES);
    }

    // Package private for use in VcnConfigTest
    static VcnGatewayConnectionConfig buildVcnGatewayConnectionConfig() {
        return buildVcnGatewayConnectionConfigBase().build();
    }

    @Test
    public void testBuildVcnGatewayConnectionConfig() throws Exception {
        final VcnGatewayConnectionConfig gatewayConnConfig = buildVcnGatewayConnectionConfig();

        assertEquals(VCN_GATEWAY_CONNECTION_NAME, gatewayConnConfig.getGatewayConnectionName());
        assertEquals(buildTunnelConnectionParams(), gatewayConnConfig.getTunnelConnectionParams());
        assertArrayEquals(
                new int[] {NET_CAPABILITY_INTERNET}, gatewayConnConfig.getExposedCapabilities());
        assertEquals(
                UNDERLYING_NETWORK_TEMPLATES,
                gatewayConnConfig.getVcnUnderlyingNetworkPriorities());
        assertArrayEquals(RETRY_INTERNAL_MILLIS, gatewayConnConfig.getRetryIntervalsMillis());
    }

    @Test
    public void testBuilderAddRemove() throws Exception {
        final VcnGatewayConnectionConfig gatewayConnConfig =
                buildVcnGatewayConnectionConfigBase()
                        .addExposedCapability(NET_CAPABILITY_DUN)
                        .removeExposedCapability(NET_CAPABILITY_DUN)
                        .build();

        assertArrayEquals(
                new int[] {NET_CAPABILITY_INTERNET}, gatewayConnConfig.getExposedCapabilities());
    }

    @Test
    public void testBuildWithoutMobikeEnabled() {
        final IkeSessionParams ikeParams =
                getIkeSessionParamsBase().removeIkeOption(IKE_OPTION_MOBIKE).build();
        final IkeTunnelConnectionParams tunnelParams = buildTunnelConnectionParams(ikeParams);

        try {
            new VcnGatewayConnectionConfig.Builder(VCN_GATEWAY_CONNECTION_NAME, tunnelParams);
            fail("Expected exception if MOBIKE not configured");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testBuilderAddGatewayOptions() throws Exception {
        final VcnGatewayConnectionConfig.Builder builder = buildVcnGatewayConnectionConfigBase();

        for (int option : GATEWAY_OPTIONS) {
            builder.addGatewayOption(option);
        }

        final VcnGatewayConnectionConfig gatewayConnConfig = builder.build();
        for (int option : GATEWAY_OPTIONS) {
            assertTrue(gatewayConnConfig.hasGatewayOption(option));
        }
    }

    @Test
    public void testBuilderAddRemoveGatewayOptions() throws Exception {
        final VcnGatewayConnectionConfig.Builder builder = buildVcnGatewayConnectionConfigBase();

        for (int option : GATEWAY_OPTIONS) {
            builder.addGatewayOption(option);
        }

        for (int option : GATEWAY_OPTIONS) {
            builder.removeGatewayOption(option);
        }

        final VcnGatewayConnectionConfig gatewayConnConfig = builder.build();
        for (int option : GATEWAY_OPTIONS) {
            assertFalse(gatewayConnConfig.hasGatewayOption(option));
        }
    }

    @Test
    public void testBuilderRequiresValidOption() {
        try {
            buildVcnGatewayConnectionConfigBase().addGatewayOption(-1);
            fail("Expected exception due to the invalid VCN gateway option");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testBuilderSetMinUdpPort4500NatTimeout() {
        final int natTimeoutSeconds = 600;
        final VcnGatewayConnectionConfig gatewayConnConfig = buildVcnGatewayConnectionConfigBase()
                .setMinUdpPort4500NatTimeoutSeconds(600).build();
        assertEquals(natTimeoutSeconds, gatewayConnConfig.getMinUdpPort4500NatTimeoutSeconds());
    }

    @Test
    public void testBuilderSetMinUdpPort4500NatTimeout_invalidValues() {
        try {
            buildVcnGatewayConnectionConfigBase().setMinUdpPort4500NatTimeoutSeconds(-1);
            fail("Expected exception due to invalid timeout range");
        } catch (IllegalArgumentException e) {
        }

        try {
            buildVcnGatewayConnectionConfigBase().setMinUdpPort4500NatTimeoutSeconds(119);
            fail("Expected exception due to invalid timeout range");
        } catch (IllegalArgumentException e) {
        }
    }
}
