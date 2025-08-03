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

package android.net.cts.util;

import static android.net.ipsec.ike.SaProposal.DH_GROUP_4096_BIT_MODP;
import static android.net.ipsec.ike.SaProposal.ENCRYPTION_ALGORITHM_AES_CBC;
import static android.net.ipsec.ike.SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_12;
import static android.net.ipsec.ike.SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA2_256_128;
import static android.net.ipsec.ike.SaProposal.KEY_LEN_AES_128;
import static android.net.ipsec.ike.SaProposal.KEY_LEN_AES_256;
import static android.net.ipsec.ike.SaProposal.PSEUDORANDOM_FUNCTION_AES128_XCBC;

import android.net.InetAddresses;
import android.net.ipsec.ike.ChildSaProposal;
import android.net.ipsec.ike.IkeFqdnIdentification;
import android.net.ipsec.ike.IkeIdentification;
import android.net.ipsec.ike.IkeIpv4AddrIdentification;
import android.net.ipsec.ike.IkeIpv6AddrIdentification;
import android.net.ipsec.ike.IkeSaProposal;
import android.net.ipsec.ike.IkeSessionParams;
import android.net.ipsec.ike.TunnelModeChildSessionParams;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/** Shared testing parameters and util methods for testing IKE */
public class IkeSessionTestUtils {
    private static final String TEST_SERVER_ADDR_V4 = "192.0.2.2";
    private static final String TEST_SERVER_ADDR_V6 = "2001:db8::2";
    public static final String TEST_IDENTITY = "client.cts.android.com";
    private static final byte[] TEST_PSK = "ikeAndroidPsk".getBytes();
    public static final int TEST_KEEPALIVE_TIMEOUT_UNSET = -1;
    public static final IkeSessionParams IKE_PARAMS_V4 = getTestIkeSessionParams(false);
    public static final IkeSessionParams IKE_PARAMS_V6 = getTestIkeSessionParams(true);

    public static final TunnelModeChildSessionParams CHILD_PARAMS = getChildSessionParams();

    private static TunnelModeChildSessionParams getChildSessionParams() {
        final TunnelModeChildSessionParams.Builder childOptionsBuilder =
                new TunnelModeChildSessionParams.Builder()
                        .addSaProposal(getChildSaProposals());

        return childOptionsBuilder.build();
    }

    private static IkeSessionParams getTestIkeSessionParams(boolean testIpv6) {
        return getTestIkeSessionParams(testIpv6, new IkeFqdnIdentification(TEST_IDENTITY));
    }

    public static IkeSessionParams getTestIkeSessionParams(boolean testIpv6,
            IkeIdentification identification) {
        return getTestIkeSessionParams(testIpv6, identification, TEST_KEEPALIVE_TIMEOUT_UNSET);
    }

    public static IkeSessionParams getTestIkeSessionParams(boolean testIpv6,
            IkeIdentification identification, int keepaliveTimer) {
        final String testServer = testIpv6 ? TEST_SERVER_ADDR_V6 : TEST_SERVER_ADDR_V4;
        final InetAddress addr = InetAddresses.parseNumericAddress(testServer);
        final IkeSessionParams.Builder ikeOptionsBuilder =
                new IkeSessionParams.Builder()
                        .setServerHostname(testServer)
                        .setLocalIdentification(identification)
                        .setRemoteIdentification(testIpv6
                                ? new IkeIpv6AddrIdentification((Inet6Address) addr)
                                : new IkeIpv4AddrIdentification((Inet4Address) addr))
                        .setAuthPsk(TEST_PSK)

                        .addSaProposal(getIkeSaProposals());
        if (keepaliveTimer != TEST_KEEPALIVE_TIMEOUT_UNSET) {
            ikeOptionsBuilder.setNattKeepAliveDelaySeconds(keepaliveTimer);
        }

        return ikeOptionsBuilder.build();
    }

    private static IkeSaProposal getIkeSaProposals() {
        return new IkeSaProposal.Builder()
                .addEncryptionAlgorithm(ENCRYPTION_ALGORITHM_AES_CBC, KEY_LEN_AES_256)
                .addIntegrityAlgorithm(INTEGRITY_ALGORITHM_HMAC_SHA2_256_128)
                .addDhGroup(DH_GROUP_4096_BIT_MODP)
                .addPseudorandomFunction(PSEUDORANDOM_FUNCTION_AES128_XCBC).build();
    }

    private static ChildSaProposal getChildSaProposals() {
        return new ChildSaProposal.Builder()
                .addEncryptionAlgorithm(ENCRYPTION_ALGORITHM_AES_GCM_12, KEY_LEN_AES_128)
                .build();
    }
}
