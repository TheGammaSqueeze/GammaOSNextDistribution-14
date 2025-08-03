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

import static android.net.ipsec.ike.IkeSessionParams.IKE_OPTION_MOBIKE;
import static android.net.ipsec.ike.SaProposal.DH_GROUP_2048_BIT_MODP;
import static android.net.ipsec.ike.SaProposal.ENCRYPTION_ALGORITHM_AES_CBC;
import static android.net.ipsec.ike.SaProposal.INTEGRITY_ALGORITHM_AES_CMAC_96;
import static android.net.ipsec.ike.SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA2_256_128;
import static android.net.ipsec.ike.SaProposal.KEY_LEN_AES_128;
import static android.net.ipsec.ike.SaProposal.PSEUDORANDOM_FUNCTION_AES128_CMAC;

import android.net.InetAddresses;
import android.net.ipsec.ike.ChildSaProposal;
import android.net.ipsec.ike.IkeFqdnIdentification;
import android.net.ipsec.ike.IkeSaProposal;
import android.net.ipsec.ike.IkeSessionParams;
import android.net.ipsec.ike.IkeTunnelConnectionParams;
import android.net.ipsec.ike.TunnelModeChildSessionParams;

import java.net.InetAddress;

public class VcnTestBase {
    protected static final InetAddress REMOTE_ADDRESS =
            InetAddresses.parseNumericAddress("192.0.2.1");

    protected static IkeTunnelConnectionParams buildTunnelConnectionParams() {
        final IkeSessionParams ikeParams = getIkeSessionParamsBase().build();
        return buildTunnelConnectionParams(ikeParams);
    }

    protected static IkeTunnelConnectionParams buildTunnelConnectionParams(
            IkeSessionParams ikeParams) {
        final ChildSaProposal childProposal =
                new ChildSaProposal.Builder()
                        .addEncryptionAlgorithm(ENCRYPTION_ALGORITHM_AES_CBC, KEY_LEN_AES_128)
                        .addIntegrityAlgorithm(INTEGRITY_ALGORITHM_HMAC_SHA2_256_128)
                        .build();

        final TunnelModeChildSessionParams childParams =
                new TunnelModeChildSessionParams.Builder().addSaProposal(childProposal).build();

        return new IkeTunnelConnectionParams(ikeParams, childParams);
    }

    protected static IkeSessionParams.Builder getIkeSessionParamsBase() {
        final IkeSaProposal ikeProposal =
                new IkeSaProposal.Builder()
                        .addEncryptionAlgorithm(ENCRYPTION_ALGORITHM_AES_CBC, KEY_LEN_AES_128)
                        .addIntegrityAlgorithm(INTEGRITY_ALGORITHM_AES_CMAC_96)
                        .addPseudorandomFunction(PSEUDORANDOM_FUNCTION_AES128_CMAC)
                        .addDhGroup(DH_GROUP_2048_BIT_MODP)
                        .build();

        // TODO: b/192610392 Improve VcnManagerTest CTS by adding IPv6 test case.
        final String testLocalId = "client.test.ike.android.net";
        final String testRemoteId = "server.test.ike.android.net";
        final byte[] psk = "ikeAndroidPsk".getBytes();

        return new IkeSessionParams.Builder()
                .setServerHostname(REMOTE_ADDRESS.getHostAddress())
                .addSaProposal(ikeProposal)
                .setLocalIdentification(new IkeFqdnIdentification(testLocalId))
                .setRemoteIdentification(new IkeFqdnIdentification(testRemoteId))
                .setAuthPsk(psk)
                .addIkeOption(IKE_OPTION_MOBIKE);
    }
}
