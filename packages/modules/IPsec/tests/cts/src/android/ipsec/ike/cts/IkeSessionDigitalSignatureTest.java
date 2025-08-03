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
import android.net.ipsec.ike.IkeDerAsn1DnIdentification;
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
import java.security.interfaces.RSAPrivateKey;
import java.util.ArrayList;
import java.util.Arrays;

import javax.security.auth.x500.X500Principal;

/**
 * Explicitly test setting up transport mode Child SA so that devices do not have
 * FEATURE_IPSEC_TUNNELS will be test covered. Tunnel mode Child SA setup has been tested in
 * IkeSessionPskTest and authentication method is orthogonal to Child mode.
 */
@RunWith(AndroidJUnit4.class)
public class IkeSessionDigitalSignatureTest extends IkeSessionTestBase {
    private static final int EXPECTED_AUTH_REQ_FRAG_COUNT = 3;

    private static final String IKE_INIT_RESP =
            "46B8ECA1E0D72A18438F7F8BF397F8E021202220000000000000015822000030"
                    + "0000002C010100040300000C0100000C800E0080030000080300000C03000008"
                    + "0200000500000008040000022800008800020000C412B02C9501CC99130FCBE8"
                    + "68D2D3733765C6F402B2EBBD0DDC29E6A19DB806EF4BF8AE3698AAD8538D6AAF"
                    + "3FA3EA53F76220CF8CCB9A9E9053D45ADC484642A21DC3424552CCE932480DC1"
                    + "1FD7F054EE94A0ABEDA1F4B4755CFAFD50294C47CBCA3D91CF1E691ED563ACFE"
                    + "285964A4EAFDCB61505F54A4F5D8E9A16500835B29000024E6F04A3D98883AF1"
                    + "801A50D5B3A798D853D1A601DD867BE4F68B9E3CA13F72622900001C00004004"
                    + "32DCE1372267ACD0D61CFDB67B3C1124673070DE2900001C000040057647E2B6"
                    + "749AC02EE68FA97494583D4EA57D9171290000080000402E290000100000402F"
                    + "000200030004000529000008000040220000000800004014";
    private static final String IKE_AUTH_RESP_FRAG_1 =
            "46B8ECA1E0D72A18438F7F8BF397F8E03520232000000001000004D4240004B8"
                    + "000100026D4BBDD8B228AC06C6DE106F77E3B85C0DC8FB23DA72614C09559381"
                    + "48009184A49802AB4AF82A8C0D21C90038F8C0611BC9D22B5B13821522E60019"
                    + "28DA5CCF1D8A586D23DBB138D8F42EDC6D051EB35EF3CCEFA69AFA6DCB428DC6"
                    + "A5EC6305B54DFD8D4830CCE90AE78492DF5F5C4BB03DB7EEA47F2D4D0A4D5001"
                    + "449D6324CDE47CD363667D2B921E71C5382184F81AD5D541F9CD31674D8B3114"
                    + "04A1226134A385585D727EBBFA93A84C452F85967BCA6ED5D9E1E00B0E333C87"
                    + "0B308724ACA8562C5D27B670B7AA5A8A6359836D33A484B26E91FD419CBBC8D1"
                    + "E912F60CE4DF71BBDF2A49DF4F6AC0EF833A9B324DCF8A3A65E871536F3315EE"
                    + "D344F847FFA08D1972F8CEEAFCDD127F36C72A24E4E50C02807D980B0FAAA4AA"
                    + "028BCE4EB52700B4346AC4A9FE2A0F34A49D479DE8C42E1DEDF3EDB77B59F14F"
                    + "7B87DC15CCE7C66A2F2889AA030002BE06C179BC00997D60B1BD08EFC9D7A1B4"
                    + "84F1189CADCF3D2283AB185F434F1A539B9C505EE0E35F929288CF6C8A24C37A"
                    + "77896C724226BE4E8DAD681E4256C4D704F5790CF897C09F6120D510A6E88D3A"
                    + "758CCE6CF7970E7A5CEE36386096A90BB4F374ACAF6FEA91B4492E76A1F967B4"
                    + "E26D9A1F1B18DE3C9D04BD87F75705D9FDA804893DDFC590A1CDF5F5AE3A8F8C"
                    + "0BA16B0CFC371489C33EAE511AC4337269CB64581219988183FD78B883476300"
                    + "A1C55B7BE126DF31FE8FF5764BDCD5088D77161E8AAB322A4FD5D70EA301B626"
                    + "9CDD860C5C692DB8F5F3524B6A9B240186676F300AD3907E40565315FFE4CB0F"
                    + "782A174866C5970DBAB5B1E327ACC50EEB6F70495E515AB63C925D5AF23665FC"
                    + "D2F21CC1E6AA799C63F54DE9676D40AD8A9BC2903D7B321A09C7A2FEE19F91C6"
                    + "8DAA0413AE5EDBC1646D1CFFFB670DBEB10894785697F990D6255D9418732DA0"
                    + "9434579A774A82AC679B5F933BE4CE0D127590DCE2D1B6786CC08DE729C46C7B"
                    + "9F500322D51DE54DC2E65B856C8680AD5D4A74B2ADFCC071ADF66C588DBA39A8"
                    + "ADC0BE0465230F8994B190EB6B0FC2F9E2BC47A1F228CBE078F633BB2901A120"
                    + "4E3C97F9152F60742E067F36312E3549CF1B17FE1E4BA41BD4CFCB3E7949D1C7"
                    + "9BD8AF08EE83E307EEFAD624DB888E13F2CC29EE6B8C71BC85CD5A8514ACA24E"
                    + "3AFD482B34F97A2463DCACA91FF3EE3FA86A32C2484640443825319338EF6339"
                    + "AD5F17DBC0065C48076B3FBBDA820CAC82F0EF3171FC309589DDB05A3C15A2B7"
                    + "2E7DCD7B95CEE6B75FA3E05B1D90F9316CDC974876BCB2981ED4EE6B1300054E"
                    + "355F8A1A14A22134391FC78F7F0F0037988C6A54E62284206307F8F2691804F0"
                    + "6109E72B66496805CC91ABE10B4B29DD8AD5633F64D9CA28FA76AB09DF7EBA35"
                    + "176BF7B6365C074ED915E3ACDEE4B63F47B96B733A5B536B1BB1000EA19F711C"
                    + "B91E17025F391EEFB2F418B7AAF511D3B53C55129906A121C0958D133832A494"
                    + "701072FBCB2E75F1CFA50808A4F58856C15D0247790D0CE64685F84945B39C9B"
                    + "6A4B706B2D8287D900042F8FAD3FCBD2BB6993A1B552FD941B8B5AD48908C6CF"
                    + "ECCDB6CC3B08C26CE41CE4FE9C785085AF60EA220085AFFC284FC978175F85C6"
                    + "F131D18F1FB98043DD236951AADCDF64D78A804C67F4D830B92B1FC4C7AB7349"
                    + "C91417FE1F4BC25B0581FDB25015516295538391";
    private static final String IKE_AUTH_RESP_FRAG_2 =
            "46B8ECA1E0D72A18438F7F8BF397F8E0352023200000000100000104000000E8"
                    + "00020002F53A92CE389C3E84C0E012104FBDA702075E6C797F189464565E058B"
                    + "B3FB262BAA995D93482C9C70BE6BA82EB78D5ED3B1182D48FD937861AAFBD392"
                    + "5BC26C47B9E0F1623CC1FB6869238BEF6DDAD04143D87A4F48387089A1EC200E"
                    + "7E164E6264A6663AF5DBCB9C3C1B1C99C36FF4837E628A00BA98956B04601173"
                    + "8F454420A83B0B53BF2007971FC56FA8F16F21B3C0CD133545CA57CCE99D1535"
                    + "7E2213EA87741D8D4552FC45151AD7558B6DEA2846696F1B484066BD27D98202"
                    + "8DD9CFDA8EA25577B5BB18FEAC9ACD1E306EDD63EF43030F2D32F5246992D415"
                    + "CA8A64FC";
    private static final String DELETE_IKE_RESP =
            "46B8ECA1E0D72A18438F7F8BF397F8E02E202520000000020000005000000034"
                    + "FA002B5B3BE405C09631759A8FC61EF97375025EEB1E976029932584D0D11AC9"
                    + "AB0B2C5258BD4EEEE57C2068DD77494A";

    // Using IPv4 for transport mode Child SA. IPv6 is currently infeasible because the IKE server
    // that generates the test vectors is running in an IPv4 only network.
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

    // TODO(b/157510502): Add test for IKE Session setup with transport mode Child in IPv6 network

    private static final String LOCAL_ID_ASN1_DN =
            "CN=client.test.ike.android.net, O=Android, C=US";
    private static final String REMOTE_ID_ASN1_DN =
            "CN=server.test.ike.android.net, O=Android, C=US";

    private static X509Certificate sServerCaCert;
    private static X509Certificate sClientEndCert;
    private static X509Certificate sClientIntermediateCaCertOne;
    private static X509Certificate sClientIntermediateCaCertTwo;
    private static RSAPrivateKey sClientPrivateKey;

    @BeforeClass
    public static void setUpCertsBeforeClass() throws Exception {
        sServerCaCert = CertUtils.createCertFromPemFile("server-a-self-signed-ca.pem");
        sClientEndCert = CertUtils.createCertFromPemFile("client-a-end-cert.pem");
        sClientIntermediateCaCertOne =
                CertUtils.createCertFromPemFile("client-a-intermediate-ca-one.pem");
        sClientIntermediateCaCertTwo =
                CertUtils.createCertFromPemFile("client-a-intermediate-ca-two.pem");
        sClientPrivateKey = CertUtils.createRsaPrivateKeyFromKeyFile("client-a-private-key.key");
    }

    private IkeSession openIkeSessionWithRemoteAddress(InetAddress remoteAddress) {
        IkeSessionParams ikeParams =
                new IkeSessionParams.Builder(sContext)
                        .setNetwork(mTunNetworkContext.tunNetwork)
                        .setServerHostname(remoteAddress.getHostAddress())
                        .addSaProposal(SaProposalTest.buildIkeSaProposalWithNormalModeCipher())
                        .addSaProposal(SaProposalTest.buildIkeSaProposalWithCombinedModeCipher())
                        .setLocalIdentification(
                                new IkeDerAsn1DnIdentification(new X500Principal(LOCAL_ID_ASN1_DN)))
                        .setRemoteIdentification(
                                new IkeDerAsn1DnIdentification(
                                        new X500Principal(REMOTE_ID_ASN1_DN)))
                        .setAuthDigitalSignature(
                                sServerCaCert,
                                sClientEndCert,
                                Arrays.asList(
                                        sClientIntermediateCaCertOne, sClientIntermediateCaCertTwo),
                                sClientPrivateKey)
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
        performSetupIkeAndFirstChildBlocking(
                IKE_INIT_RESP,
                EXPECTED_AUTH_REQ_FRAG_COUNT /* expectedReqPktCnt */,
                true /* expectedAuthUseEncap */,
                IKE_AUTH_RESP_FRAG_1,
                IKE_AUTH_RESP_FRAG_2);

        // IKE INIT and IKE AUTH takes two exchanges. Message ID starts from 2
        int expectedMsgId = 2;

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
