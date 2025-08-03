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

package android.net.ipsec.test.ike;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.fail;

import org.junit.Test;

public class IkeTunnelConnectionParamsTest {
    private static final IkeSessionParams IKE_PARAMS =
            new IkeSessionParams.Builder()
                    .setServerHostname("192.0.2.100")
                    .addSaProposal(SaProposalTest.buildTestIkeProposal())
                    .setLocalIdentification(new IkeFqdnIdentification("test.client.com"))
                    .setRemoteIdentification(new IkeFqdnIdentification("test.server.com"))
                    .setAuthPsk("psk".getBytes())
                    .build();
    private static final TunnelModeChildSessionParams CHILD_PARAMS =
            new TunnelModeChildSessionParams.Builder()
                    .addSaProposal(SaProposalTest.buildTestChildProposal())
                    .build();

    @Test
    public void testBuildAndGetters() {
        final IkeTunnelConnectionParams tunnelParams =
                new IkeTunnelConnectionParams(IKE_PARAMS, CHILD_PARAMS);

        assertEquals(IKE_PARAMS, tunnelParams.getIkeSessionParams());
        assertEquals(CHILD_PARAMS, tunnelParams.getTunnelModeChildSessionParams());
    }

    @Test
    public void testEquals() {
        final IkeTunnelConnectionParams tunnelParams =
                new IkeTunnelConnectionParams(IKE_PARAMS, CHILD_PARAMS);
        final IkeTunnelConnectionParams otherTunnelParams =
                new IkeTunnelConnectionParams(IKE_PARAMS, CHILD_PARAMS);

        assertEquals(tunnelParams, otherTunnelParams);
        assertNotSame(tunnelParams, otherTunnelParams);
    }

    @Test
    public void testConstructConfigWithoutIkeParams() {
        try {
            new IkeTunnelConnectionParams(null, CHILD_PARAMS);
            fail("Expect to fail because ikeParams was null");
        } catch (NullPointerException expected) {
        }
    }

    @Test
    public void testBuilderConfigWithoutChildParams() {
        try {
            new IkeTunnelConnectionParams(IKE_PARAMS, null);
            fail("Expect to fail because childParams was null");
        } catch (NullPointerException expected) {
        }
    }
}
