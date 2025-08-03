/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.net.InetAddresses;
import android.net.LinkAddress;
import android.net.eap.EapSessionConfig;
import android.net.ipsec.ike.IkeFqdnIdentification;
import android.net.ipsec.ike.IkeSession;
import android.net.ipsec.ike.IkeSessionParams;
import android.net.ipsec.ike.IkeTrafficSelector;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.net.ipsec.test.ike.testutils.CertUtils;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetAddress;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Explicitly test setting up transport mode Child SA so that devices do not have
 * FEATURE_IPSEC_TUNNELS will be test covered. Tunnel mode Child SA setup has been tested in
 * IkeSessionPskTest and authentication method is orthogonal to Child mode.
 */
@RunWith(AndroidJUnit4.class)
public class IkeSessionMschapV2Test extends IkeSessionTestBase {
    private static final String IKE_INIT_RESP =
            "46B8ECA1E0D72A185B2A7EF460C5766F21202220000000000000015822000030"
                    + "0000002C010100040300000C0100000C800E0080030000080300000C03000008"
                    + "020000050000000804000002280000880002000048088C252A8562AF22E6969D"
                    + "7C85AFB7E264D9008D21FE8C39968E054DEFE101621294AFF31EE0F1AC118C26"
                    + "4992918E993C1FBB5150A018103C784494D74B7E39C391F52A618537301B3D5A"
                    + "B088DE468C3D1B71DDF61FB2780FC0B1A26D6FF5B839B3DD73C8B64B4612BED8"
                    + "95BAC4F772B250BA1554D7F881F9055667A3F8842900002416F078998EEAF6AC"
                    + "38FDCF45BDFA23FC70D69A4B07E24F8C7FF256C1033A787A2900001C00004004"
                    + "098C131C9CFC49BB25FAF538215BB118BCCB8DDA2900001C000040054934BCB5"
                    + "023633C9713789FAA9497B2A922429B9290000080000402E290000100000402F"
                    + "000200030004000529000008000040220000000800004014";
    private static final String IKE_AUTH_RESP_1_FRAG_1 =
            "46B8ECA1E0D72A185B2A7EF460C5766F3520232000000001000004D4240004B8"
                    + "00010002F66B1F0AFEA28BF8ABC02D15E2ED8A0B6D0F095E5D7DB7470FDB50D7"
                    + "29B79BB0C5291A76EE68F4B3421B7A2EC8642E73B2C171C17548FB8248EC1CBE"
                    + "471B5ED81F643ADEE3850ED5FF25A5CD453EB5B151C399AE0F9055C1A1862684"
                    + "5CCB3DBCC9D5ED984D64167E44E319BD8ABC2EA5FBD455958F77FCB75E8BE1C7"
                    + "02072E151FFF5F110AB1DF645F6188B26FB95AD4F51FC9CA869A5AE0BACB1943"
                    + "A03AF03F004D78404903A1C24D72782A63C524F94BE341D8984C69D1564FF54F"
                    + "66C428B802CC245439B223D391D48E31DB9BA990B8E195C9FE3B7B2D13D84E03"
                    + "66D7C4854A27D2795F0B27CDB30D34029895545B2694BD6383B6CFCE3B994A8D"
                    + "1817CE76DE226F143143280963E3041AF2E843C6016A779749A44C181455F48C"
                    + "00394747F587BF4A6CB2DDEE3BAC2C688DD9A57DCFDC40DC38BD592B3F3228C7"
                    + "1FF27E82879E9718270A01031A6D704DDF82019C4ACECF55989064D485864E66"
                    + "2AA86718DE8F4C1F907F6C6A8A77F81D45377C2BFE6951EA3C86436D36094DFB"
                    + "0094D65B25355475E76DFC995D4BBF789C8A293B3DFCAAD340E08A0DFA2DB09A"
                    + "7ACF950B807C75B9D9D21F100B555785BAB1AB7B834C375B354CC4C43BDE071C"
                    + "0EF1220745554A6E53BDA4BAA751CD1E0C8AAE7965ABD4D467319B1CB343AE54"
                    + "183F373A7EAAEB281B6F2BD0C865B6609D65E534CCB04616BBA4AA6714646C55"
                    + "5A4C9090956E3ABAE3DF0CE21828E6F0CBDE27FDFC8EF5A25CD0D9E5C50BBAE5"
                    + "A844336AFB7C607A7CB1F01E3F16E1082FC07B1C340CF8B9F162BBBBE47C5E72"
                    + "33A3F866C724CADDE909ABF27F50485A5E4D47C28F9286B8C5B4116A8B57139C"
                    + "E00D221773C554D119359824C8D6FA909199B88ACA8CFD7D933041CF3833B1CA"
                    + "1CA1CD6446A01998957CDEAC9E7A6803CE228CC558846AFC4AA9F59BFEA369AB"
                    + "10C3304F260D8125C32014E8F81F97B37844D12F44D4072F7DE6AC1268100490"
                    + "5006CA4A4BBE3EBF50AECF65C159AC0C744992A130195644C70237BEE2F7325D"
                    + "5C7C4DE95698A87424D433AC0CAD3CFD74D27CC890BC9E1982C430A0AED294AD"
                    + "EF2C807E81BC17DAFBDF8825920DE7DF483992DA08F9BEC86E52B8A586EFB8D2"
                    + "1869DA8EEFE5309F1553C984A999DAEAC89934C0A11E5BF1734B98D0B1B27B6A"
                    + "463A4A3B4E3D9A94BA1115C9A40AF13E755FA024C2855B9DB3BB6984780A8F4B"
                    + "D5585CB016661233A68B66076A602B715B5024E5FBDD5D85735F6B24FADFA485"
                    + "5475780CB7DE136BCF07D5BBB4B9EF932140D44223898D81175DE3051811B87F"
                    + "669C74535BEF22A6A0598B7EB808C766BF1A4113BE3261A474C2D806993475EE"
                    + "8C42749C306306D046699623E4B2DA8DDFE92CB546C70C33D6822513D5487F2C"
                    + "7AA79B5659ED9299BE5FC61DDA27821BAF8F8D2A09438DC50C60732BFF5171EA"
                    + "BD3C15463004DE73C7BE0E067E62B516387566D61390A9F7388FAF8B7C5FA153"
                    + "701668DAD18DB09B3560994128DD7337002B5323E77FB6A237D4FB6B9EC2581D"
                    + "AC2840C9B4D02AE113ABC2C370D84E4AB712B94CF3B55BDD6A903EF91CF5222E"
                    + "171419F2161FD048A8ACEDC39FCAAE0D961A0B183A329B3BC04D338B96D7D10A"
                    + "5664CFEB24ABA86283170164CD36688C8B95594A5D779037DB4B6164E0ECA6AC"
                    + "7B27FB75DF001E88389729BDEBAAB2E0DC49D785";
    private static final String IKE_AUTH_RESP_1_FRAG_2 =
            "46B8ECA1E0D72A185B2A7EF460C5766F35202320000000010000007400000058"
                    + "0002000212DE65524F39985A6EFB71433300BFDC9A0560A03085771F802DCC0B"
                    + "AA86D4A8AB25873D3F4109334ACF39888BB1A8C66B6B49D5F9255B200AFA550A"
                    + "8B1F618B8B764007A73B1552F5156F4F4276EC7D";
    private static final String IKE_AUTH_RESP_2 =
            "46B8ECA1E0D72A185B2A7EF460C5766F2E202320000000020000007030000054"
                    + "1944F5B731610D96FB313734A06EB98421135E69856347F2AA65F3070B7B74AF"
                    + "CC38DED38CDAA168CC67BB944B9A005D0F2A101414920D37EC1B57FABA6DA76D"
                    + "E1D32F8117C5445D5A052939C83DA0AE";
    private static final String IKE_AUTH_RESP_3 =
            "46B8ECA1E0D72A185B2A7EF460C5766F2E202320000000030000009030000074"
                    + "CDB1C2770F23649D406CF89C2219F4499D7EA44529C6AECE4590562414441AC2"
                    + "2867B7DF370410DFC482487752353A1941FF156E7E2AE057CB212A9310E26894"
                    + "8E1D9BBFC462DE76BD166CFB76D4C5F69EAC7269533180BEAB2D9B44C32FCE49"
                    + "657DAE0CBB69BFB5503D4A240595109A";
    private static final String IKE_AUTH_RESP_4 =
            "46B8ECA1E0D72A185B2A7EF460C5766F2E202320000000040000005030000034"
                    + "D5EA5282DB5FD3C3764B0CB7CB78082BDB1234F6C08B971D3ACF269F3D39605B"
                    + "BE4B69BBEF1B06417F5D96E772D59C10";
    private static final String IKE_AUTH_RESP_5 =
            "46B8ECA1E0D72A185B2A7EF460C5766F2E20232000000005000000E0270000C4"
                    + "2B3868394D364201CA2E1A7A2FC4ECF47BC9F99D489E3AE832AF4A1682BCC205"
                    + "AC3920524F16B3A8276066E3034310627203A795D8584F47C8348280C29CC226"
                    + "EADC0E5AF609948AB68F1F6F6807EF483D5785A74A385FC8005B606CCD460B24"
                    + "20F56EF09CB4A6BDDC0C5471CBC5C8D3853FE19AF514343380459D523EA25523"
                    + "C6C41FDACB2A9EEFFCC27D3C1AC6CE9A9B2BA9D4C322B1FC6C46BF29473FE68E"
                    + "53F3CA849FE775C59658D586890F38D67AC8DBDCFC4F65F2725F10B78474F572";
    private static final String DELETE_IKE_RESP =
            "46B8ECA1E0D72A185B2A7EF460C5766F2E202520000000060000005000000034"
                    + "43EA9ACAFBAC64D4CC838E30E6C059FDF25A8A71F6835A8C750D1CE6525666A0"
                    + "3EFA305DB36EC8BF37CEAA6AEDF15F81";

    // This value is align with the test vectors hex that are generated in an IPv4 environment
    private static final IkeTrafficSelector TRANSPORT_MODE_IN_TS =
            new IkeTrafficSelector(
                    MIN_PORT,
                    MAX_PORT,
                    InetAddresses.parseNumericAddress("192.168.0.212"),
                    InetAddresses.parseNumericAddress("192.168.0.212"));

    private static final IkeTrafficSelector TRANSPORT_MODE_OUT_TS =
            new IkeTrafficSelector(
                    MIN_PORT,
                    MAX_PORT,
                    InetAddresses.parseNumericAddress("192.168.0.223"),
                    InetAddresses.parseNumericAddress("192.168.0.223"));

    private static final EapSessionConfig EAP_CONFIG =
            new EapSessionConfig.Builder()
                    .setEapIdentity(EAP_IDENTITY)
                    .setEapMsChapV2Config(EAP_MSCHAPV2_USERNAME, EAP_MSCHAPV2_PASSWORD)
                    .build();

    private static X509Certificate sServerCaCert;

    @BeforeClass
    public static void setUpCertBeforeClass() throws Exception {
        sServerCaCert = CertUtils.createCertFromPemFile("server-a-self-signed-ca.pem");
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
                        .setAuthEap(sServerCaCert, EAP_CONFIG)
                        .build();
        return new IkeSession(
                sContext,
                ikeParams,
                buildTransportModeChildParamsWithTs(TRANSPORT_MODE_IN_TS, TRANSPORT_MODE_OUT_TS),
                mUserCbExecutor,
                mIkeSessionCallback,
                mFirstChildSessionCallback);
    }

    @Test
    public void testIkeSessionSetupAndChildSessionSetupWithTransportMode() throws Exception {
        // Open IKE Session
        IkeSession ikeSession = openIkeSessionWithRemoteAddress(mRemoteAddress);
        int expectedMsgId = 0;
        mTunNetworkContext.tunUtils.awaitReqAndInjectResp(
                IKE_DETERMINISTIC_INITIATOR_SPI,
                expectedMsgId++,
                false /* expectedUseEncap */,
                IKE_INIT_RESP);

        mTunNetworkContext.tunUtils.awaitReqAndInjectResp(
                IKE_DETERMINISTIC_INITIATOR_SPI,
                expectedMsgId++,
                true /* expectedUseEncap */,
                IKE_AUTH_RESP_1_FRAG_1,
                IKE_AUTH_RESP_1_FRAG_2);

        mTunNetworkContext.tunUtils.awaitReqAndInjectResp(
                IKE_DETERMINISTIC_INITIATOR_SPI,
                expectedMsgId++,
                true /* expectedUseEncap */,
                IKE_AUTH_RESP_2);
        mTunNetworkContext.tunUtils.awaitReqAndInjectResp(
                IKE_DETERMINISTIC_INITIATOR_SPI,
                expectedMsgId++,
                true /* expectedUseEncap */,
                IKE_AUTH_RESP_3);
        mTunNetworkContext.tunUtils.awaitReqAndInjectResp(
                IKE_DETERMINISTIC_INITIATOR_SPI,
                expectedMsgId++,
                true /* expectedUseEncap */,
                IKE_AUTH_RESP_4);
        mTunNetworkContext.tunUtils.awaitReqAndInjectResp(
                IKE_DETERMINISTIC_INITIATOR_SPI,
                expectedMsgId++,
                true /* expectedUseEncap */,
                IKE_AUTH_RESP_5);

        verifyIkeSessionSetupBlocking();
        verifyChildSessionSetupBlocking(
                mFirstChildSessionCallback,
                Arrays.asList(TRANSPORT_MODE_IN_TS),
                Arrays.asList(TRANSPORT_MODE_OUT_TS),
                new ArrayList<LinkAddress>());
        IpSecTransformCallRecord firstTransformRecordA =
                mFirstChildSessionCallback.awaitNextCreatedIpSecTransform();
        IpSecTransformCallRecord firstTransformRecordB =
                mFirstChildSessionCallback.awaitNextCreatedIpSecTransform();
        verifyCreateIpSecTransformPair(firstTransformRecordA, firstTransformRecordB);

        // Close IKE Session
        ikeSession.close();
        performCloseIkeBlocking(expectedMsgId++, DELETE_IKE_RESP);
        verifyCloseIkeAndChildBlocking(firstTransformRecordA, firstTransformRecordB);
    }
}
