/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.internal.util.HexDump.hexStringToByteArray;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.ipsec.ike.cts.IkeTunUtils.PortPair;
import android.net.InetAddresses;
import android.net.LinkAddress;
import android.net.ipsec.ike.IkeFqdnIdentification;
import android.net.ipsec.ike.IkeSession;
import android.net.ipsec.ike.IkeSessionCallback;
import android.net.ipsec.ike.IkeSessionParams;
import android.net.ipsec.ike.IkeTrafficSelector;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Explicitly test transport mode Child SA so that devices without FEATURE_IPSEC_TUNNELS can be test
 * covered. Tunnel mode Child SA setup has been tested in IkeSessionPskTest. Rekeying process is
 * independent from Child SA mode.
 */
@RunWith(AndroidJUnit4.class)
public class IkeSessionLivenessCheckTest extends IkeSessionTestBase {

    @Rule public final DevSdkIgnoreRule ignoreRule = new DevSdkIgnoreRule();

    // This value is align with the test vectors hex that are generated in an IPv4 environment
    private static final IkeTrafficSelector TRANSPORT_MODE_INBOUND_TS =
            new IkeTrafficSelector(
                    MIN_PORT,
                    MAX_PORT,
                    InetAddresses.parseNumericAddress("172.58.35.40"),
                    InetAddresses.parseNumericAddress("172.58.35.40"));

    private byte[] buildInboundPkt(PortPair outPktSrcDestPortPair, String inboundDataHex)
            throws Exception {
        // Build inbound packet by flipping the outbound packet addresses and ports
        return IkeTunUtils.buildIkePacket(
                mRemoteAddress,
                mLocalAddress,
                outPktSrcDestPortPair.dstPort,
                outPktSrcDestPortPair.srcPort,
                true /* useEncap */,
                hexStringToByteArray(inboundDataHex));
    }

    private IkeSession openIkeSessionWithRemoteAddress(InetAddress remoteAddress) {
        IkeSessionParams ikeParams =
                new IkeSessionParams.Builder(sContext)
                        .setNetwork(mTunNetworkContext.tunNetwork)
                        .setServerHostname(remoteAddress.getHostAddress())
                        .addSaProposal(SaProposalTest.buildIkeSaProposalWithNormalModeCipher())
                        .addSaProposal(SaProposalTest.buildIkeSaProposalWithCombinedModeCipher())
                        .setLocalIdentification(new IkeFqdnIdentification(LOCAL_HOSTNAME))
                        .setRemoteIdentification(new IkeFqdnIdentification(REMOTE_HOSTNAME))
                        .setAuthPsk(IKE_PSK)
                        .build();
        return new IkeSession(
                sContext,
                ikeParams,
                buildTransportModeChildParamsWithTs(
                        TRANSPORT_MODE_INBOUND_TS, TRANSPORT_MODE_OUTBOUND_TS),
                mUserCbExecutor,
                mIkeSessionCallback,
                mFirstChildSessionCallback);
    }

    /**
     * Test Liveness Checks. Test coverage up to Android U is ignored because the Liveness APIs are
     * not available in versions below, including SDK 34.
     *
     * @throws java.lang.NoSuchMethodError if sdk {@code VERSION_CODES.UPSIDE_DOWN_CAKE} or less
     */
    @Test
    @IgnoreUpTo(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testIkeLivenessCheck() throws Exception {

        final String ikeInitResp =
                "46B8ECA1E0D72A1866B5248CF6C7472D21202220000000000000015022000030"
                        + "0000002C010100040300000C0100000C800E0100030000080300000C03000008"
                        + "0200000500000008040000022800008800020000920D3E830E7276908209212D"
                        + "E5A7F2A48706CFEF1BE8CB6E3B173B8B4E0D8C2DC626271FF1B13A88619E569E"
                        + "7B03C3ED2C127390749CDC7CDC711D0A8611E4457FFCBC4F0981B3288FBF58EA"
                        + "3E8B70E27E76AE70117FBBCB753660ADDA37EB5EB3A81BED6A374CCB7E132C2A"
                        + "94BFCE402DC76B19C158B533F6B1F2ABF01ACCC329000024B302CA2FB85B6CF4"
                        + "02313381246E3C53828D787F6DFEA6BD62D6405254AEE6242900001C00004004"
                        + "7A1682B06B58596533D00324886EF1F20EF276032900001C00004005BF633E31"
                        + "F9984B29A62E370BB2770FC09BAEA665290000080000402E290000100000402F"
                        + "00020003000400050000000800004014";
        final String ikeAuthResp =
                "46B8ECA1E0D72A1866B5248CF6C7472D2E20232000000001000000F0240000D4"
                        + "10166CA8647F56123DE74C17FA5E256043ABF73216C812EE32EE1BB01EAF4A82"
                        + "DC107AB3ADBFEE0DEA5EEE10BDD5D43178F4C975C7C775D252273BB037283C7F"
                        + "236FE34A6BCE4833816897075DB2055B9FFD66DFA45A0A89A8F70AFB59431EED"
                        + "A20602FB614369D12906D3355CF7298A5D25364ABBCC75A9D88E0E6581449FCD"
                        + "4E361A39E00EFD1FD0A69651F63DB46C12470226AA21BA5EFF48FAF0B6DDF61C"
                        + "B0A69392CE559495EEDB4D1C1D80688434D225D57210A424C213F7C993D8A456"
                        + "38153FBD194C5E247B592D1D048DB4C8";
        String ikeDpdResp =
                "46B8ECA1E0D72A1866B5248CF6C7472D2E202520000000020000005000000034"
                        + "DDC1F4421B0377957A0A247B67C14C431567B24CA2849230BA55018717B339C6"
                        + "CB14C18C3B4BB7EA29EBC8556C7C9727";
        final String deleteIkeReq =
                "46B8ECA1E0D72A1866B5248CF6C7472D2E20250000000000000000502A000034"
                        + "C6DD62B54C07488D7BB16C5BEF0C0ADD93BCA2AE590CE5E0787B28E4D5DD1DBC"
                        + "F25CA207907B08A7BB5F104879938A01";

        // Open IKE Session
        IkeSession ikeSession = openIkeSessionWithRemoteAddress(mRemoteAddress);
        PortPair localRemotePorts = performSetupIkeAndFirstChildBlocking(ikeInitResp, ikeAuthResp);

        // Local request message ID starts from 2 because there is one IKE_INIT message and a single
        // IKE_AUTH message.
        int expectedReqMsgId = 2;
        int expectedRespMsgId = 0;

        verifyIkeSessionSetupBlocking();
        verifyChildSessionSetupBlocking(
                mFirstChildSessionCallback,
                Arrays.asList(TRANSPORT_MODE_INBOUND_TS),
                Arrays.asList(TRANSPORT_MODE_OUTBOUND_TS),
                new ArrayList<LinkAddress>());
        IpSecTransformCallRecord firstTransformRecordA =
                mFirstChildSessionCallback.awaitNextCreatedIpSecTransform();
        IpSecTransformCallRecord firstTransformRecordB =
                mFirstChildSessionCallback.awaitNextCreatedIpSecTransform();
        verifyCreateIpSecTransformPair(firstTransformRecordA, firstTransformRecordB);

        // request the Liveness check twice
        ikeSession.requestLivenessCheck();
        ikeSession.requestLivenessCheck();

        int livenessStatus = mIkeSessionCallback.awaitNextOnLivenessStatus();
        if (livenessStatus == IkeSessionCallback.LIVENESS_STATUS_ON_DEMAND_STARTED) {
            assertEquals(
                    IkeSessionCallback.LIVENESS_STATUS_ON_DEMAND_ONGOING,
                    mIkeSessionCallback.awaitNextOnLivenessStatus());
        } else if (livenessStatus == IkeSessionCallback.LIVENESS_STATUS_BACKGROUND_STARTED) {
            assertEquals(
                    IkeSessionCallback.LIVENESS_STATUS_BACKGROUND_ONGOING,
                    mIkeSessionCallback.awaitNextOnLivenessStatus());
        } else {
            fail("Unexpected status.");
        }

        mTunNetworkContext.tunUtils.awaitReqAndInjectResp(
                IKE_DETERMINISTIC_INITIATOR_SPI,
                expectedReqMsgId,
                true /* expectedUseEncap */,
                ikeDpdResp);

        livenessStatus = mIkeSessionCallback.awaitNextOnLivenessStatus();
        assertEquals(IkeSessionCallback.LIVENESS_STATUS_SUCCESS, livenessStatus);

        // Inject delete IKE request
        mTunNetworkContext.tunUtils.injectPacket(buildInboundPkt(localRemotePorts, deleteIkeReq));
        mTunNetworkContext.tunUtils.awaitResp(
                IKE_DETERMINISTIC_INITIATOR_SPI, expectedRespMsgId++, true /* expectedUseEncap */);

        verifyDeleteIpSecTransformPair(
                mFirstChildSessionCallback, firstTransformRecordA, firstTransformRecordB);
        mFirstChildSessionCallback.awaitOnClosed();
        mIkeSessionCallback.awaitOnClosed();
    }
}
