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

import static android.net.ipsec.ike.IkeSessionConfiguration.EXTENSION_TYPE_FRAGMENTATION;
import static android.net.ipsec.ike.IkeSessionConfiguration.EXTENSION_TYPE_MOBIKE;
import static android.net.ipsec.ike.IkeSessionParams.ESP_ENCAP_TYPE_AUTO;
import static android.net.ipsec.ike.IkeSessionParams.ESP_IP_VERSION_AUTO;
import static android.net.ipsec.ike.IkeSessionParams.IKE_OPTION_MOBIKE;
import static android.net.ipsec.ike.IkeSessionParams.IKE_OPTION_REKEY_MOBILITY;
import static android.net.ipsec.ike.SaProposal.DH_GROUP_2048_BIT_MODP;
import static android.net.ipsec.ike.SaProposal.ENCRYPTION_ALGORITHM_AES_CBC;
import static android.net.ipsec.ike.SaProposal.INTEGRITY_ALGORITHM_AES_CMAC_96;
import static android.net.ipsec.ike.SaProposal.KEY_LEN_AES_128;
import static android.net.ipsec.ike.SaProposal.PSEUDORANDOM_FUNCTION_AES128_CMAC;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.net.Network;
import android.net.ipsec.ike.IkeSaProposal;
import android.net.ipsec.ike.IkeSession;
import android.net.ipsec.ike.IkeSessionConfiguration;
import android.net.ipsec.ike.IkeSessionConnectionInfo;
import android.net.ipsec.ike.IkeSessionParams;
import android.net.ipsec.ike.exceptions.IkeException;
import android.net.ipsec.ike.exceptions.IkeNetworkLostException;
import android.platform.test.annotations.AppModeFull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import com.android.modules.utils.build.SdkLevel;
import com.android.testutils.DevSdkIgnoreRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetAddress;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "MANAGE_IPSEC_TUNNELS permission can't be granted to instant apps")
@SdkSuppress(minSdkVersion = 31, codeName = "S")
public class IkeSessionMobikeTest extends IkeSessionPskTestBase {
    @Rule public final DevSdkIgnoreRule ignoreRule = new DevSdkIgnoreRule();

    private static final String IKE_INIT_RESP =
            "46b8eca1e0d72a189b9f8e0158e1c0a52120222000000000000001d022000030"
                    + "0000002c010100040300000c0100000c800e0080030000080300000803000008"
                    + "02000008000000080400000e28000108000e0000164d3413d855a1642d4d6355"
                    + "a8ef6666bfaa28a4b5264600c9ffbaef7930bd33af49022926013aae0a48d764"
                    + "750ccb3987605957e31a2ef0e6838cfa67af989933c2879434081c4e9787f0d4"
                    + "4da0d7dacca5589702a4537ee4fb18e8db21a948b245260f55212a1c619f61c6"
                    + "fa1caaff4474082f9714b14ef4bcc7b2b8f43fcb939931119e53b05274faec65"
                    + "2816c563529e60c1a88183eba9c456ecb644faf57b726b83e3242e08489d95e9"
                    + "81e59c7ad82cf3cdfb00fe0213c4e65d61e88bbefbd536261027da722a2bbf89"
                    + "c6378e63ce6fbcef282421e5576bba1b2faa3c4c2d41028f91df7ba165a24a18"
                    + "fcba4f96db3e5e0eed76dc7c3c432362dd4a82d32900002461cbd03c08819730"
                    + "f1060ed0c0446f784eb8dd884d3f73f54eb2b0c3071cc4f32900001c00004004"
                    + "07150f3fd9584dbebb7e88ad256c7bfb9b0bb55a2900001c00004005e3aa3788"
                    + "7040e38dbb4de8fd435161cce904ec59290000080000402e290000100000402f"
                    + "00020003000400050000000800004014";
    private static final String IKE_AUTH_RESP =
            "46b8eca1e0d72a189b9f8e0158e1c0a52e20232000000001000000fc240000e0"
                    + "1a666eb2a02b37682436a18fff5e9cef67b9096d6c7887ed235f8b5173c9469e"
                    + "361621b66849de2dbcabf956b3d055cafafd503530543540e81dac9bf8fb8826"
                    + "e08bc99e9ed2185d8f1322c8885abe4f98a9832c694da775eaa4ae69f17b8cbf"
                    + "b009bf82b4bf4012bca489595631c3168cd417f813e7d177d2ceb70766a0773c"
                    + "8819d8763627ddc9455ae3d5a5a03224020a66c8e58c8073c4a1fcf5d67cfa95"
                    + "15de86b392a63ff54ff5572302b9ce7725085b05839252794c3680f5d8f34019"
                    + "fa1930ea045d2a9987850e2049235c7328ef148370b6a3403408b987";
    private static final String IKE_UPDATE_SA_RESP =
            "46b8eca1e0d72a189b9f8e0158e1c0a52e202520000000020000007c29000060"
                    + "a1fd35f112d92d1df19ce734f6edf56ccda1bfd44ef6de428a097e04d5b40b28"
                    + "3897e42f23dd53e444dc6c676cf9a7d9d73bb3975d663ec351fb5ae4e56a55d8"
                    + "cbcf376a3b99cc6fd858621cc78b3017d895e4309f09a444028dba85";
    private static final String IKE_CREATE_CHILD_RESP =
            "46b8eca1e0d72a189b9f8e0158e1c0a52e20242000000003000000cc210000b0"
                    + "e6bb78203dbe2189806c5cecef5040b8c4c0253895c7c0acea6483a1f0f72425"
                    + "77ab46e18d553329d4ae1bd31cf57eec6ec31ceb1f2ed6b1195cac98b4b97a25"
                    + "115d14c414e44dba8ebbdaf502e43f98a09036bee0ea2a621176300874a3eae8"
                    + "c988357255b4e5923928d335b0ef62a565333fae6a64c85ac30e7da34ceeade4"
                    + "1a161bcad0b51f8209ee1fdaf53d50359ad6b986ecd4290c9f69a34c64ddc0eb"
                    + "73b8f3231f3f4e057404c18d";
    private static final String IKE_DELETE_CHILD_RESP =
            "46b8eca1e0d72a189b9f8e0158e1c0a52e202520000000040000004c2a000030"
                    + "53d97806d48ce44e0d4e1adf1de36778f77c3823bfaf8186cc71d4dc73497099"
                    + "a9049e7be8a2013affd56ab7";
    private static final String DELETE_IKE_RESP =
            "46b8eca1e0d72a189b9f8e0158e1c0a52e202520000000050000004c00000030"
                    + "818e6679fef4924a27452805c98125660d4396ab002f5ae45dcf75ef0d1e2190"
                    + "273b1c4527ba26ce37574852";

    private TunNetworkContext mSecondaryTunNetworkContext;

    private InetAddress mSecondaryLocalAddr;

    private IkeSession mIkeSession;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        mSecondaryLocalAddr = getNextAvailableIpv4AddressLocal();

        mSecondaryTunNetworkContext = new TunNetworkContext(mSecondaryLocalAddr);
    }

    @After
    public void tearDown() throws Exception {
        mSecondaryTunNetworkContext.close();

        if (mIkeSession != null) {
            mIkeSession.kill();
        }

        super.tearDown();
    }

    @Override
    protected IkeSessionParams getIkeSessionParams(InetAddress remoteAddress) {
        return createIkeParamsBuilderBase(remoteAddress)
                .addIkeOption(IkeSessionParams.IKE_OPTION_MOBIKE)
                .build();
    }

    @Test
    public void testMigrateNetworks() throws Exception {
        if (!hasTunnelsFeature()) return;

        final IkeSession ikeSession =
                setupAndVerifyIkeSessionWithMobility(
                        IKE_INIT_RESP,
                        IKE_AUTH_RESP,
                        mRemoteAddress,
                        true /* mobikeSupportedByServer */,
                        new int[] {IKE_OPTION_MOBIKE});

        final IpSecTransformCallRecord firstTransformRecordA =
                mFirstChildSessionCallback.awaitNextCreatedIpSecTransform();
        final IpSecTransformCallRecord firstTransformRecordB =
                mFirstChildSessionCallback.awaitNextCreatedIpSecTransform();
        verifyCreateIpSecTransformPair(firstTransformRecordA, firstTransformRecordB);

        // Local request message ID starts from 2 because there is one IKE_INIT message and a single
        // IKE_AUTH message.
        int expectedMsgId = 2;

        // TODO (b/277662795): Generate canned values and add testing for encap-type-change
        // migrations
        setNetworkAndVerifyConnectionInfoChange(
                ikeSession,
                mSecondaryTunNetworkContext,
                expectedMsgId++,
                IKE_UPDATE_SA_RESP,
                ESP_IP_VERSION_AUTO,
                mSecondaryLocalAddr,
                mRemoteAddress);

        if (hasTunnelMigrationFeature()) {
            verifyTransformsMigratedAndGetTransforms();

            // TODO (b/277662795): Verify closing IKE session. Will require regenerating
            // DELETE_IKE_RESP for the kernel-MOBIKE case, where the message ID is 3 instead of 5
        } else {
            final IpSecTransformCallRecord[] migrateRecords =
                    injectCreateChildRespAndVerifyTransformsMigrated(
                            mSecondaryTunNetworkContext, expectedMsgId++, IKE_CREATE_CHILD_RESP);
            injectDeleteChildRespAndVerifyTransformsDeleted(
                    mSecondaryTunNetworkContext,
                    expectedMsgId++,
                    IKE_DELETE_CHILD_RESP,
                    firstTransformRecordA,
                    firstTransformRecordB);

            // Close IKE Session
            ikeSession.close();
            mSecondaryTunNetworkContext.tunUtils.awaitReqAndInjectResp(
                    IKE_DETERMINISTIC_INITIATOR_SPI,
                    expectedMsgId++,
                    true /* expectedUseEncap */,
                    DELETE_IKE_RESP);
            verifyCloseIkeAndChildBlocking(migrateRecords[0], migrateRecords[1]);
        }
    }

    private IkeSession setupAndVerifyIkeSessionWithMobility(
            String ikeInitRespHex,
            String ikeAuthRespHex,
            InetAddress remoteAddress,
            boolean mobikeSupportedByServer,
            int[] ikeOptions)
            throws Exception {
        final IkeSaProposal saProposal =
                new IkeSaProposal.Builder()
                        .addEncryptionAlgorithm(ENCRYPTION_ALGORITHM_AES_CBC, KEY_LEN_AES_128)
                        .addIntegrityAlgorithm(INTEGRITY_ALGORITHM_AES_CMAC_96)
                        .addPseudorandomFunction(PSEUDORANDOM_FUNCTION_AES128_CMAC)
                        .addDhGroup(DH_GROUP_2048_BIT_MODP)
                        .build();

        final IkeSessionParams.Builder ikeParamsBuilder =
                createIkeParamsBuilderBase(remoteAddress, saProposal);
        for (int option : ikeOptions) {
            ikeParamsBuilder.addIkeOption(option);
        }

        final IkeSessionParams ikeParams = ikeParamsBuilder.build();

        final IkeSession ikeSession = openIkeSessionWithTunnelModeChild(remoteAddress, ikeParams);
        performSetupIkeAndFirstChildBlocking(
                ikeInitRespHex, true /* expectedAuthUseEncap */, ikeAuthRespHex);
        if (mobikeSupportedByServer) {
            verifyIkeSessionSetupBlocking(EXTENSION_TYPE_FRAGMENTATION, EXTENSION_TYPE_MOBIKE);
        } else {
            verifyIkeSessionSetupBlocking(EXTENSION_TYPE_FRAGMENTATION);
        }

        verifyChildSessionSetupBlocking(
                mFirstChildSessionCallback,
                Arrays.asList(TUNNEL_MODE_INBOUND_TS),
                Arrays.asList(TUNNEL_MODE_OUTBOUND_TS),
                Arrays.asList(EXPECTED_INTERNAL_LINK_ADDR));
        return ikeSession;
    }

    private void setNetworkAndVerifyConnectionInfoChange(
            IkeSession ikeSession,
            TunNetworkContext tunNetworkContext,
            int expectedMsgId,
            String ikeUpdateSaResp,
            int ipVersion,
            InetAddress expectedLocalAddress,
            InetAddress expectedRemoteAddress)
            throws Exception {
        ikeSession.setNetwork(tunNetworkContext.tunNetwork, ipVersion, ESP_ENCAP_TYPE_AUTO,
                IkeSessionParams.NATT_KEEPALIVE_INTERVAL_AUTO);

        tunNetworkContext.tunUtils.awaitReqAndInjectResp(
                IKE_DETERMINISTIC_INITIATOR_SPI,
                expectedMsgId,
                true /* expectedUseEncap */,
                ikeUpdateSaResp);

        verifyConnectionInfoChange(tunNetworkContext.tunNetwork, expectedLocalAddress,
                expectedRemoteAddress);
    }

    private void verifyConnectionInfoChange(
            Network expectedNetwork,
            InetAddress expectedLocalAddress,
            InetAddress expectedRemoteAddress) throws Exception {
        final IkeSessionConnectionInfo connectionInfo =
                mIkeSessionCallback.awaitOnIkeSessionConnectionInfoChanged();
        assertNotNull(connectionInfo);
        assertEquals(expectedNetwork, connectionInfo.getNetwork());
        assertEquals(expectedLocalAddress, connectionInfo.getLocalAddress());
        assertEquals(expectedRemoteAddress, connectionInfo.getRemoteAddress());
    }

    private IpSecTransformCallRecord[] injectCreateChildRespAndVerifyTransformsMigrated(
            TunNetworkContext tunNetworkContext, int expectedMsgId, String ikeCreateChildResp)
            throws Exception {
        tunNetworkContext.tunUtils.awaitReqAndInjectResp(
                IKE_DETERMINISTIC_INITIATOR_SPI,
                expectedMsgId,
                true /* expectedUseEncap */,
                ikeCreateChildResp);

        return verifyTransformsMigratedAndGetTransforms();
    }

    private IpSecTransformCallRecord[] verifyTransformsMigratedAndGetTransforms() throws Exception {
        final IpSecTransformCallRecord[] migrateRecords =
                mFirstChildSessionCallback.awaitNextMigratedIpSecTransform();
        assertNotNull(migrateRecords);
        verifyCreateIpSecTransformPair(migrateRecords[0], migrateRecords[1]);
        return migrateRecords;
    }

    private void injectDeleteChildRespAndVerifyTransformsDeleted(
            TunNetworkContext tunNetworkContext,
            int expectedMsgId,
            String ikeDeleteChildResp,
            IpSecTransformCallRecord transformRecordA,
            IpSecTransformCallRecord transformRecordB)
            throws Exception {
        tunNetworkContext.tunUtils.awaitReqAndInjectResp(
                IKE_DETERMINISTIC_INITIATOR_SPI,
                expectedMsgId,
                true /* expectedUseEncap */,
                ikeDeleteChildResp);

        verifyDeleteIpSecTransformPair(
                mFirstChildSessionCallback, transformRecordA, transformRecordB);
    }

    @Test
    public void testNetworkDied() throws Exception {
        if (!hasTunnelsFeature()) return;

        final IkeSession ikeSession =
                setupAndVerifyIkeSessionWithMobility(
                        IKE_INIT_RESP,
                        IKE_AUTH_RESP,
                        mRemoteAddress,
                        true /* mobikeSupportedByServer */,
                        new int[] {IKE_OPTION_MOBIKE});

        // Teardown test network to kill the IKE Session
        mTunNetworkContext.close();

        final IkeException exception = mIkeSessionCallback.awaitNextOnErrorException();
        assertTrue(exception instanceof IkeNetworkLostException);
        final IkeNetworkLostException networkLostException = (IkeNetworkLostException) exception;
        assertEquals(mTunNetworkContext.tunNetwork, networkLostException.getNetwork());

        ikeSession.kill();
    }

    @Test
    public void testSetNetworkWithoutMobikeEnabled() throws Exception {
        if (!hasTunnelsFeature()) return;

        final String ikeInitResp =
                "46B8ECA1E0D72A1821D31742E82FA9232120222000000000000001D022000030"
                        + "0000002C010100040300000C0100000C800E0080030000080300000803000008"
                        + "02000008000000080400000E28000108000E0000CE0DFFE121D30D2B5C4DBEC4"
                        + "AEBD2F8D83F0F8EC5E2998CE98BD90492D8AA6C9360F32AE98402F853DF12FA9"
                        + "A64ABFBB83D5FFAD1F18B6CB6FEBAB222AF5C98D4575BE2380B42F2A4E5B7B0B"
                        + "5528F372C4E70B5B7D01D706E3F1C2E4A9E8A687C427DDB1002B190A4D73BBBA"
                        + "E41801798408D73870657B846B84A5D9292A007A9EDA719CA3A820BB513EBE59"
                        + "C6BF5BEB7CC9A86F0722D98F6E73B5BBC2F5EEDB39992D036406B54BF0355534"
                        + "960D4771623ECFC561211F0580EEC051BD477076F4454E185DA7744E7B7D145B"
                        + "08C874529C2BFE387BB7C09FCD762CEBFF6C2DE0C4912DF5747B16F51D0A9570"
                        + "37EC652A1F025C4E80DEE9D91BF0DFEE17F3EF6F29000024196ADD342DBD954F"
                        + "A1160542E5F312A6A44A9D19AF6799698A781F4CF717CD722900001C00004004"
                        + "3EFFE36169090E6F6B6CB5B5BD321257E67C2B922900001C000040050AB409D2"
                        + "60D9EE157D15483E001603BB43D918C1290000080000402E290000100000402F"
                        + "00020003000400050000000800004014";
        final String IkeAuthRespWithoutMobikeSupport =
                "46B8ECA1E0D72A1821D31742E82FA9232E20232000000001000000EC240000D0"
                        + "493A4E97A90AE4F3CB4561D82F9123C22436EE0BAB686965D1EF7C724B2B3979"
                        + "594D3CBCF70C3C78F46B2D9F198DCB07CEE0F774A51CF4224B4A3223500214F2"
                        + "0AFBB7472156EF8FF03391D03A2D78001EE0B23AD5818BDAC15F348F3D97E54D"
                        + "0C6A3DBC7F89A764A883631CFCB6C8C5A4E939E7AF7AC744D6530A88CD8EDDAC"
                        + "F003BD73CE73A79D7ADDF53F9B3CCCBBF92F21FB29317F4151B17F0BC5F98CEE"
                        + "89B739E4A46BC80B10D34B159CCFA847F12F85DEE5B8AED854DC460EA92BE17A"
                        + "E2C1F56C7497001BF5B22E88";
        final String createChildResp =
                "46B8ECA1E0D72A1821D31742E82FA9232E20242000000002000000CC210000B0"
                        + "10869163B82783B650AD180040F191A516588586F051F77147F06FDDC70EA4A3"
                        + "C4FCCD61C1E3AF3672150207F0AAB3540D4E20AB4F89B70D5D8F57E6A6AD2A42"
                        + "F95516715BB3317B62878DA4D77170FD29994D8553300F05DC28973899F58FE2"
                        + "A60D0C1158B7A711F20FC2A2F95351A14650F63160746CCEF73F32033B766DD4"
                        + "730712D9EBB2D58CB1635CBF74559FA66CB56CFBE506CBC86C89F604D1A80E73"
                        + "9B269A1CE93F46451C3307E4";
        final String deleteChildResp =
                "46B8ECA1E0D72A1821D31742E82FA9232E202520000000030000004C2A000030"
                        + "E2D0B074AF644A5AA58F999AA376450780BB66BBCB64C84BD8E5CBC9549A2A1A"
                        + "524091EFE5D1ADE9694813B1";
        final String deleteIkeResp =
                "46B8ECA1E0D72A1821D31742E82FA9232E202520000000040000004C00000030"
                        + "59205A0B069A0D6C95B044B16DC655BA28A968463CCBCF60996EE56897C14F2C"
                        + "FF9F15D1120A78DD2DE2E1C9";

        final int[] ikeOptions =
                SdkLevel.isAtLeastT()
                        ? new int[] {IKE_OPTION_MOBIKE, IKE_OPTION_REKEY_MOBILITY}
                        : new int[] {IKE_OPTION_MOBIKE};
        final IkeSession ikeSession =
                setupAndVerifyIkeSessionWithMobility(
                        ikeInitResp,
                        IkeAuthRespWithoutMobikeSupport,
                        mRemoteAddress,
                        false /* mobikeSupportedByServer */,
                        ikeOptions);

        final IpSecTransformCallRecord firstTransformRecordA =
                mFirstChildSessionCallback.awaitNextCreatedIpSecTransform();
        final IpSecTransformCallRecord firstTransformRecordB =
                mFirstChildSessionCallback.awaitNextCreatedIpSecTransform();
        verifyCreateIpSecTransformPair(firstTransformRecordA, firstTransformRecordB);

        // Rekey-based mobility
        ikeSession.setNetwork(mSecondaryTunNetworkContext.tunNetwork);
        verifyConnectionInfoChange(mSecondaryTunNetworkContext.tunNetwork, mSecondaryLocalAddr,
                mRemoteAddress);

        // Local request message ID starts from 2 because there is one IKE_INIT message and a single
        // IKE_AUTH message.
        int expectedMsgId = 2;
        final IpSecTransformCallRecord[] migrateRecords =
                injectCreateChildRespAndVerifyTransformsMigrated(
                        mSecondaryTunNetworkContext, expectedMsgId++, createChildResp);
        injectDeleteChildRespAndVerifyTransformsDeleted(
                mSecondaryTunNetworkContext,
                expectedMsgId++,
                deleteChildResp,
                firstTransformRecordA,
                firstTransformRecordB);

        // Close IKE Session
        ikeSession.close();
        mSecondaryTunNetworkContext.tunUtils.awaitReqAndInjectResp(
                IKE_DETERMINISTIC_INITIATOR_SPI,
                expectedMsgId++,
                true /* expectedUseEncap */,
                deleteIkeResp);
        verifyCloseIkeAndChildBlocking(migrateRecords[0], migrateRecords[1]);
    }

    @Test
    public void testSetNetworkWithoutOptionMobike() throws Exception {
        if (!hasTunnelsFeature()) return;

        final String ikeInitResp =
                "46B8ECA1E0D72A18B45427679F9245D421202220000000000000015022000030"
                        + "0000002C010100040300000C0100000C800E0080030000080300000203000008"
                        + "0200000200000008040000022800008800020000A7AA3435D088EC1A2B7C2A47"
                        + "1FA1B85F1066C9B2006E7C353FB5B5FDBC2A88347ED2C6F5B7A265D03AE34039"
                        + "6AAC0145CFCC93F8BDB219DDFF22A603B8856A5DC59B6FAB7F17C5660CF38670"
                        + "8794FC72F273ADEB7A4F316519794AED6F8AB61F95DFB360FAF18C6C8CABE471"
                        + "6E18FE215348C2E582171A57FC41146B16C4AFE429000024A634B61C0E5C90C6"
                        + "8D8818B0955B125A9B1DF47BBD18775710792E651083105C2900001C00004004"
                        + "406FA3C5685A16B9B72C7F2EEE9993462C619ABE2900001C00004005AF905A87"
                        + "0A32222AA284A7070585601208A282F0290000080000402E290000100000402F"
                        + "00020003000400050000000800004014";
        final String IkeAuthRespWithoutMobikeSupport =
                "46B8ECA1E0D72A18B45427679F9245D42E20232000000001000000EC240000D0"
                        + "0D06D37198F3F0962DE8170D66F1A9008267F98CDD956D984BDCED2FC7FAF84A"
                        + "A6664EF25049B46B93C9ED420488E0C172AA6635BF4011C49792EF2B88FE7190"
                        + "E8859FEEF51724FD20C46E7B9A9C3DC4708EF7005707A18AB747C903ABCEAC5C"
                        + "6ECF5A5FC13633DCE3844A920ED10EF202F115DBFBB5D6D2D7AB1F34EB08DE7C"
                        + "A54DCE0A3A582753345CA2D05A0EFDB9DC61E81B2483B7D13EEE0A815D37252C"
                        + "23D2F29E9C30658227D2BB0C9E1A481EAA80BC6BE9006BEDC13E925A755A0290"
                        + "AEC4164D29997F52ED7DCC2E";

        // Open IKE Session without IKE_OPTION_MOBIKE
        mIkeSession =
                openIkeSessionWithTunnelModeChild(
                        mRemoteAddress, createIkeParamsBuilderBase(mRemoteAddress).build());
        performSetupIkeAndFirstChildBlocking(ikeInitResp, IkeAuthRespWithoutMobikeSupport);

        verifyIkeSessionSetupBlocking();

        final IkeSessionConfiguration ikeConfig = mIkeSessionCallback.awaitIkeConfig();
        assertFalse(ikeConfig.isIkeExtensionEnabled(IkeSessionConfiguration.EXTENSION_TYPE_MOBIKE));

        try {
            // manually change network when IKE_OPTION_MOBIKE is not set
            mIkeSession.setNetwork(mSecondaryTunNetworkContext.tunNetwork);

            fail("Expected error for setNetwork() when IKE_OPTION_MOBIKE is not set");
        } catch (IllegalStateException expected) {
        }
    }

    /** The MOBIKE spec explicitly disallows Transport mode. */
    @Test(expected = IllegalArgumentException.class)
    public void testStartSessionWithMobikeAndTransportMode() {
        mIkeSession = openIkeSessionWithTransportModeChild(mRemoteAddress);
    }
}
