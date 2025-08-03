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

package android.ipsec.ike.cts;

import static android.app.AppOpsManager.OP_MANAGE_IPSEC_TUNNELS;

import android.net.ipsec.ike.ChildSessionParams;
import android.net.ipsec.ike.IkeFqdnIdentification;
import android.net.ipsec.ike.IkeSaProposal;
import android.net.ipsec.ike.IkeSession;
import android.net.ipsec.ike.IkeSessionParams;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.net.InetAddress;

abstract class IkeSessionPskTestBase extends IkeSessionTestBase {
    @BeforeClass
    public static void setUpTunnelPermissionBeforeClass() throws Exception {
        // Under normal circumstances, the MANAGE_IPSEC_TUNNELS appop would be auto-granted, and
        // a standard permission is insufficient. So we shell out the appop, to give us the
        // right appop permissions.
        setAppOp(OP_MANAGE_IPSEC_TUNNELS, true);
    }

    // This method is guaranteed to run in subclasses and will run after subclasses' @AfterClass
    // methods.
    @AfterClass
    public static void tearDownTunnelPermissionAfterClass() throws Exception {
        setAppOp(OP_MANAGE_IPSEC_TUNNELS, false);
    }

    protected IkeSession openIkeSessionWithTunnelModeChild(InetAddress remoteAddress) {
        return openIkeSession(remoteAddress, buildTunnelModeChildSessionParams());
    }

    protected IkeSession openIkeSessionWithTunnelModeChild(
            InetAddress remoteAddress, IkeSessionParams ikeParams) {
        return openIkeSession(remoteAddress, ikeParams, buildTunnelModeChildSessionParams());
    }

    protected IkeSession openIkeSessionWithTransportModeChild(InetAddress remoteAddress) {
        return openIkeSession(remoteAddress, buildTransportModeChildParamsWithDefaultTs());
    }

    protected IkeSessionParams.Builder createIkeParamsBuilderBase(InetAddress remoteAddress) {
        return createIkeParamsBuilderBase(
                remoteAddress,
                SaProposalTest.buildIkeSaProposalWithNormalModeCipher(),
                SaProposalTest.buildIkeSaProposalWithCombinedModeCipher());
    }

    protected IkeSessionParams.Builder createIkeParamsBuilderBase(
            InetAddress remoteAddress, IkeSaProposal... saProposals) {
        final IkeSessionParams.Builder builder =
                new IkeSessionParams.Builder(sContext)
                        .setNetwork(mTunNetworkContext.tunNetwork)
                        .setServerHostname(remoteAddress.getHostAddress())
                        .setLocalIdentification(new IkeFqdnIdentification(LOCAL_HOSTNAME))
                        .setRemoteIdentification(new IkeFqdnIdentification(REMOTE_HOSTNAME))
                        .setAuthPsk(IKE_PSK);

        for (IkeSaProposal saProposal : saProposals) {
            builder.addSaProposal(saProposal);
        }

        return builder;
    }

    protected abstract IkeSessionParams getIkeSessionParams(InetAddress remoteAddress);

    private IkeSession openIkeSession(InetAddress remoteAddress, ChildSessionParams childParams) {
        return openIkeSession(remoteAddress, getIkeSessionParams(remoteAddress), childParams);
    }

    private IkeSession openIkeSession(
            InetAddress remoteAddress, IkeSessionParams ikeParams, ChildSessionParams childParams) {
        return new IkeSession(
                sContext,
                ikeParams,
                childParams,
                mUserCbExecutor,
                mIkeSessionCallback,
                mFirstChildSessionCallback);
    }
}
