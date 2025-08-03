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

import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.content.Context;
import android.net.vcn.VcnConfig;
import android.net.vcn.VcnGatewayConnectionConfig;
import android.util.ArraySet;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class VcnConfigTest extends VcnTestBase {
    private static final VcnGatewayConnectionConfig GATEWAY_CONNECTION_CONFIG =
            VcnGatewayConnectionConfigTest.buildVcnGatewayConnectionConfig();

    private static final Set<Integer> RESTRICTED_TRANSPORTS_DEFAULT =
            Collections.singleton(TRANSPORT_WIFI);

    private static final Set<Integer> RESTRICTED_TRANSPORTS = new ArraySet<>();

    static {
        RESTRICTED_TRANSPORTS.add(TRANSPORT_WIFI);
        RESTRICTED_TRANSPORTS.add(TRANSPORT_CELLULAR);
    }

    private final Context mContext;

    public VcnConfigTest() {
        mContext = InstrumentationRegistry.getContext();
    }

    private VcnConfig.Builder makeMinimalTestConfigBuilder() {
        return new VcnConfig.Builder(mContext)
                .addGatewayConnectionConfig(GATEWAY_CONNECTION_CONFIG);
    }

    @Test
    public void testBuilderAndGettersWithMinimumSet() {
        final VcnConfig config = makeMinimalTestConfigBuilder().build();

        assertEquals(
                Collections.singleton(GATEWAY_CONNECTION_CONFIG),
                config.getGatewayConnectionConfigs());
        assertEquals(
                RESTRICTED_TRANSPORTS_DEFAULT, config.getRestrictedUnderlyingNetworkTransports());
    }

    @Test
    public void testBuilderAndGettersWithRestrictedTransports() {
        final VcnConfig config =
                makeMinimalTestConfigBuilder()
                        .setRestrictedUnderlyingNetworkTransports(RESTRICTED_TRANSPORTS)
                        .build();

        assertEquals(
                Collections.singleton(GATEWAY_CONNECTION_CONFIG),
                config.getGatewayConnectionConfigs());
        assertEquals(RESTRICTED_TRANSPORTS, config.getRestrictedUnderlyingNetworkTransports());
    }

    private static VcnGatewayConnectionConfig buildGatewayConfig(String gatewayName) {
        return new VcnGatewayConnectionConfig.Builder(gatewayName, buildTunnelConnectionParams())
                .addExposedCapability(NET_CAPABILITY_INTERNET)
                .build();
    }

    @Test
    public void testBuilderAndGettersWithMultipleGatewayConfigs() {
        final VcnGatewayConnectionConfig gatewayConfigOne = buildGatewayConfig("gatewayConfigOne");
        final VcnGatewayConnectionConfig gatewayConfigTwo = buildGatewayConfig("gatewayConfigTwo");
        final VcnConfig config =
                new VcnConfig.Builder(mContext)
                        .addGatewayConnectionConfig(gatewayConfigOne)
                        .addGatewayConnectionConfig(gatewayConfigTwo)
                        .build();

        final Set<VcnGatewayConnectionConfig> expectedGatewayConfigs = new ArraySet<>();
        expectedGatewayConfigs.add(gatewayConfigOne);
        expectedGatewayConfigs.add(gatewayConfigTwo);
        assertEquals(expectedGatewayConfigs, config.getGatewayConnectionConfigs());
    }

    @Test
    public void testBuilderRequiresGatewayConnectionConfig() {
        try {
            new VcnConfig.Builder(mContext).build();
            fail("Expected exception due to no VcnGatewayConnectionConfigs provided");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testBuilderRequiresUniqueGatewayConnectionNames() {
        try {
            new VcnConfig.Builder(mContext)
                    .addGatewayConnectionConfig(GATEWAY_CONNECTION_CONFIG)
                    .addGatewayConnectionConfig(GATEWAY_CONNECTION_CONFIG);
            fail("Expected exception due to duplicate gateway connection name");
        } catch (IllegalArgumentException e) {
        }
    }
}
