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

package android.net;

import static com.android.testutils.DevSdkIgnoreRuleKt.SC_V2;
import static com.android.testutils.ParcelUtils.assertParcelingIsLossless;

import static org.junit.Assert.assertThrows;

import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DevSdkIgnoreRunner.class)
@DevSdkIgnoreRule.IgnoreUpTo(SC_V2)
public class EthernetNetworkUpdateRequestTest {
    private IpConfiguration buildIpConfiguration() {
        return new IpConfiguration.Builder().setHttpProxy(
                new ProxyInfo("test.example.com", 1234, "")).build();
    }

    private NetworkCapabilities buildNetworkCapabilities() {
        return new NetworkCapabilities.Builder().addTransportType(
                NetworkCapabilities.TRANSPORT_ETHERNET).build();
    }

    @Test
    public void testParcelUnparcel() {
        EthernetNetworkUpdateRequest reqWithNonNull =
                new EthernetNetworkUpdateRequest.Builder().setIpConfiguration(
                        buildIpConfiguration()).setNetworkCapabilities(
                        buildNetworkCapabilities()).build();
        EthernetNetworkUpdateRequest reqWithNullCaps =
                new EthernetNetworkUpdateRequest.Builder().setIpConfiguration(
                        buildIpConfiguration()).build();
        EthernetNetworkUpdateRequest reqWithNullConfig =
                new EthernetNetworkUpdateRequest.Builder().setNetworkCapabilities(
                        buildNetworkCapabilities()).build();

        assertParcelingIsLossless(reqWithNonNull);
        assertParcelingIsLossless(reqWithNullCaps);
        assertParcelingIsLossless(reqWithNullConfig);
    }

    @Test
    public void testEmptyUpdateRequestThrows() {
        EthernetNetworkUpdateRequest.Builder emptyBuilder =
                new EthernetNetworkUpdateRequest.Builder();
        assertThrows(IllegalStateException.class, () -> emptyBuilder.build());
    }
}
