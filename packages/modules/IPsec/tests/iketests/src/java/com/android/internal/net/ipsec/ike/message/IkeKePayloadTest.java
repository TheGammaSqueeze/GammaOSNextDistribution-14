/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.internal.net.ipsec.test.ike.message;

import static com.android.internal.net.TestUtils.createMockRandomFactory;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.net.ipsec.test.ike.SaProposal;
import android.net.ipsec.test.ike.exceptions.InvalidSyntaxException;

import androidx.test.filters.SdkSuppress;

import com.android.internal.net.TestUtils;
import com.android.internal.net.ipsec.test.ike.IkeDhParams;
import com.android.internal.net.utils.test.BigIntegerUtils;

import org.junit.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.interfaces.DHPrivateKey;
import javax.crypto.spec.DHPrivateKeySpec;

public final class IkeKePayloadTest {
    private static final String KE_PAYLOAD_GENERIC_HEADER = "28000088";
    private static final String KE_PAYLOAD_RAW_PACKET =
            "00020000b4a2faf4bb54878ae21d638512ece55d9236fc50"
                    + "46ab6cef82220f421f3ce6361faf36564ecb6d28798a94aa"
                    + "d7b2b4b603ddeaaa5630adb9ece8ac37534036040610ebdd"
                    + "92f46bef84f0be7db860351843858f8acf87056e272377f7"
                    + "0c9f2d81e29c7b0ce4f291a3a72476bb0b278fd4b7b0a4c2"
                    + "6bbeb08214c7071376079587";

    private static final boolean CRITICAL_BIT = false;

    @IkePayload.PayloadType
    private static final int NEXT_PAYLOAD_TYPE = IkePayload.PAYLOAD_TYPE_NONCE;

    private static final int EXPECTED_DH_GROUP = SaProposal.DH_GROUP_1024_BIT_MODP;

    private static final int EXPECTED_KE_DATA_LEN = 128;

    private static final String KEY_EXCHANGE_DATA_RAW_PACKET =
            "b4a2faf4bb54878ae21d638512ece55d9236fc5046ab6cef"
                    + "82220f421f3ce6361faf36564ecb6d28798a94aad7b2b4b6"
                    + "03ddeaaa5630adb9ece8ac37534036040610ebdd92f46bef"
                    + "84f0be7db860351843858f8acf87056e272377f70c9f2d81"
                    + "e29c7b0ce4f291a3a72476bb0b278fd4b7b0a4c26bbeb082"
                    + "14c7071376079587";

    private static final String REMOTE_PUBLIC_KEY_1024 =
            "24FC7B6557350D9AC7135A548DE46C3338787D72FE14493C"
                    + "A55CEB4D5AD25E780AD09927B7555AD2BF2582ED8BCE59A2"
                    + "522643E3C57FCF68D16CB9B44DC76C4086B0161B42A71333"
                    + "365AB167096DABA3C059F06D39CF508C6549672D07547295"
                    + "BABE3241AE6CA26B2FE07745EB9D27EBA83E0890192C230F"
                    + "4896FEC1B5BA6EA4";
    private static final String LOCAL_PRIVATE_KEY_1024_X_VALUE =
            "B714330D5817B14B349F44DC5F228F555E02B31A9BF69106"
                    + "08EEB96100FF469164A29E14BE5DE529B3EB86218AE4DF8C"
                    + "546D699872C955C56A8FC9F7DA59B24D84087D8A70ACE380"
                    + "8D90FE1301788B008624541453264A5DBBDF4F5DB517AADD"
                    + "D69319BF607C85A69481FD0EA8AE0BB3DA03D4C125AF3A25"
                    + "62636B5C2F5A647B";
    private static final String EXPECTED_SHARED_KEY_1024 =
            "F663BA76BCB9B12D41504D8E5C8A70289162883B900EF76F"
                    + "D2D478EB841C6407A6D6216D506EDF3D89873A66C69DAD37"
                    + "339C3DFBC1D3427E874EC133E5EE0375AA2E72FA301D4DE5"
                    + "35CABB05869755747EDE21615D7BD1F720943A0D689E83ED"
                    + "2D2BFB286D1D6D5D11F7D24250EEB26B38435C25EA81FC2C"
                    + "9C17B94F389B94B9";

    private static final String KEY_EXCHANGE_ALGORITHM_MODP = "DH";
    private static final String KEY_EXCHANGE_ALGORITHM_CURVE = "XDH";
    private static final String KEY_EXCHANGE_CURVE_PROVIDER = "AndroidOpenSSL";

    @Test
    public void testDecodeIkeKePayload() throws Exception {
        byte[] inputPacket = TestUtils.hexStringToByteArray(KE_PAYLOAD_RAW_PACKET);

        IkeKePayload payload = new IkeKePayload(CRITICAL_BIT, inputPacket);

        assertFalse(payload.isOutbound);
        assertEquals(EXPECTED_DH_GROUP, payload.dhGroup);

        byte[] keyExchangeData = TestUtils.hexStringToByteArray(KEY_EXCHANGE_DATA_RAW_PACKET);
        assertEquals(keyExchangeData.length, payload.keyExchangeData.length);
        for (int i = 0; i < keyExchangeData.length; i++) {
            assertEquals(keyExchangeData[i], payload.keyExchangeData[i]);
        }
    }

    @Test
    public void testDecodeIkeKePayloadWithUnrecognizedDh() throws Exception {
        int expectedDhGroup = 0x0fff;
        String unrecognizedKePayload =
                "0fff0000b4a2faf4bb54878ae21d638512ece55d9236fc50"
                        + "46ab6cef82220f421f3ce6361faf36564ecb6d28798a94aa"
                        + "d7b2b4b603ddeaaa5630adb9ece8ac37534036040610ebdd"
                        + "92f46bef84f0be7db860351843858f8acf87056e272377f7"
                        + "0c9f2d81e29c7b0ce4f291a3a72476bb0b278fd4b7b0a4c2"
                        + "6bbeb08214c7071376079587";
        byte[] inputPacket = TestUtils.hexStringToByteArray(unrecognizedKePayload);

        IkeKePayload payload = new IkeKePayload(CRITICAL_BIT, inputPacket);

        assertFalse(payload.isOutbound);
        assertEquals(expectedDhGroup, payload.dhGroup);
    }

    @Test
    public void testDecodeIkeKePayloadWithInvalidKeData() throws Exception {
        // Cut bytes of KE data from original KE payload
        String badKeyPayloadPacket =
                KE_PAYLOAD_RAW_PACKET.substring(0, KE_PAYLOAD_RAW_PACKET.length() - 2);
        byte[] inputPacket = TestUtils.hexStringToByteArray(badKeyPayloadPacket);

        try {
            IkeKePayload payload = new IkeKePayload(CRITICAL_BIT, inputPacket);
            fail("Expected InvalidSyntaxException: KE data length doesn't match its DH group type");
        } catch (InvalidSyntaxException expected) {
        }
    }

    @Test
    public void testEncodeIkeKePayload() throws Exception {
        byte[] inputPacket = TestUtils.hexStringToByteArray(KE_PAYLOAD_RAW_PACKET);
        IkeKePayload payload = new IkeKePayload(CRITICAL_BIT, inputPacket);

        ByteBuffer byteBuffer = ByteBuffer.allocate(payload.getPayloadLength());
        payload.encodeToByteBuffer(NEXT_PAYLOAD_TYPE, byteBuffer);

        byte[] expectedKePayload =
                TestUtils.hexStringToByteArray(KE_PAYLOAD_GENERIC_HEADER + KE_PAYLOAD_RAW_PACKET);
        assertArrayEquals(expectedKePayload, byteBuffer.array());
    }

    @Test
    public void testGetIkeKePayload() throws Exception {
        IkeKePayload payload =
                IkeKePayload.createOutboundKePayload(
                        SaProposal.DH_GROUP_1024_BIT_MODP, createMockRandomFactory());

        assertEquals(EXPECTED_DH_GROUP, payload.dhGroup);
        assertEquals(EXPECTED_KE_DATA_LEN, payload.keyExchangeData.length);
        assertTrue(payload.localPrivateKey instanceof DHPrivateKey);
    }

    private PrivateKey getModpPrivateKey(String primeHex, String privateKeyXValueHex)
            throws Exception {
        BigInteger primeValue = BigIntegerUtils.unsignedHexStringToBigInteger(primeHex);
        BigInteger baseGenValue = BigInteger.valueOf(IkeDhParams.BASE_GENERATOR_MODP);
        BigInteger privateKeyValue =
                BigIntegerUtils.unsignedHexStringToBigInteger(privateKeyXValueHex);
        DHPrivateKeySpec privateKeySpec =
                new DHPrivateKeySpec(privateKeyValue, primeValue, baseGenValue);
        KeyFactory dhKeyFactory = KeyFactory.getInstance(KEY_EXCHANGE_ALGORITHM_MODP);
        return dhKeyFactory.generatePrivate(privateKeySpec);
    }

    @Test
    public void testGetSharedKey1024Modp() throws Exception {
        PrivateKey privateKey =
                getModpPrivateKey(IkeDhParams.PRIME_1024_BIT_MODP, LOCAL_PRIVATE_KEY_1024_X_VALUE);

        byte[] remotePublicKey = TestUtils.hexStringToByteArray(REMOTE_PUBLIC_KEY_1024);
        byte[] sharedKeyBytes =
                IkeKePayload.getSharedKey(
                        privateKey, remotePublicKey, SaProposal.DH_GROUP_1024_BIT_MODP);

        byte[] expectedSharedKeyBytes = TestUtils.hexStringToByteArray(EXPECTED_SHARED_KEY_1024);
        assertTrue(Arrays.equals(expectedSharedKeyBytes, sharedKeyBytes));
    }

    @Test
    public void testGetSharedKey1536Modp() throws Exception {
        final String publicKeyRemoteHex =
                "1907B9796CD091E3FCBEDDFE8113E1D9463F65DCFD5371FB"
                        + "A4E50DF78B059E3C84C8F6D53E597DF7190016B0D44A8F78"
                        + "52DAE8602B74423848012FF254F552D521579088D8AB8BAF"
                        + "2523CDC55BCC6BD2C59153A678498FB1F2EC2953F49C2109"
                        + "1F38B729ADF50BB02B0FE82908FFC867004B63A5CC6AD183"
                        + "49F57B346F870B928D018BDC97CE44DA187CE975919E97DA"
                        + "3A9D1827D5C9C8881A6355478F7602099AF56C802DE9AD54"
                        + "1295B3B238BAF28628ADDFAB9C9A6886EE24F6671B9151D5";
        final String privateKeyXValueHex =
                "a62c4aca7ec6787c075fe1690edbd768b406fe5b6bbd6d77"
                        + "1913143c75289f0e10eded5428615fa0b61271bd739e15b8"
                        + "99243de341511fbf0ab87d309746d690f1b98ada5d76b6fc"
                        + "33e6b21f59d7d5f4d2cd4e7cb1b8da2cdbc3914ff6e1a1e2"
                        + "65512ef09455dd271419fc7b7584ca5e61212c68b128307c"
                        + "bf31a4d8ce51bd20112dbb846edb5fc8d7d4c2b94ac84ba3"
                        + "e4c7805f3565ba9e1bb2724f8f5aebf9c14fca8ed6638566"
                        + "03b02bc569b868100a5e4c3906f3a9344eb57d2daeba0562";
        final String expectedSharedKeyHex =
                "0BDBC66787BCBE1F899C070CCC05E9C728ED764E93DFE454"
                        + "FD6B1AE691382FCB5B36656F5D10B85E914230071F098004"
                        + "67B594A2C9902790EDAA792CE389376BB491283482AEBC75"
                        + "225474DCD4269F92246467164A9CEFFA14371AC3477F17DB"
                        + "60EE21D85B76BF6B46B851C685E385CDFD213B5450043F56"
                        + "CE0C98CFF91D63C00FED85FE9DD318673E4E7F16BC052016"
                        + "A5D347E045EFABD7ABA6D35E53E64972779E11AFD3561076"
                        + "65F0AC3A5E64C6C065786F5B63A9B7BC993C85234D457ABB";

        PrivateKey privateKey =
                getModpPrivateKey(IkeDhParams.PRIME_1536_BIT_MODP, privateKeyXValueHex);

        byte[] remotePublicKey = TestUtils.hexStringToByteArray(publicKeyRemoteHex);
        byte[] sharedKeyBytes =
                IkeKePayload.getSharedKey(
                        privateKey, remotePublicKey, SaProposal.DH_GROUP_1536_BIT_MODP);

        byte[] expectedSharedKeyBytes = TestUtils.hexStringToByteArray(expectedSharedKeyHex);
        assertTrue(Arrays.equals(expectedSharedKeyBytes, sharedKeyBytes));
    }

    @Test
    public void testGetSharedkeyWithInvalidRemoteKey() throws Exception {
        byte[] remotePublicKey = TestUtils.hexStringToByteArray(REMOTE_PUBLIC_KEY_1024);
        PrivateKey privateKey =
                getModpPrivateKey(IkeDhParams.PRIME_1024_BIT_MODP, LOCAL_PRIVATE_KEY_1024_X_VALUE);

        try {
            byte[] sharedKeyBytes =
                    IkeKePayload.getSharedKey(
                            privateKey, remotePublicKey, SaProposal.DH_GROUP_1536_BIT_MODP);
            fail("Expected to fail because of invalid remote public key.");
        } catch (GeneralSecurityException expected) {
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testGetIkeCurveKePayload() throws Exception {
        IkeKePayload payload =
                IkeKePayload.createOutboundKePayload(
                        SaProposal.DH_GROUP_CURVE_25519, createMockRandomFactory());

        final int expectedKeDataLen = 32;

        assertEquals(SaProposal.DH_GROUP_CURVE_25519, payload.dhGroup);
        assertEquals(expectedKeDataLen, payload.keyExchangeData.length);
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testGetSharedKeyWithCurve25519() throws Exception {
        final String privateKeyHex =
                "302e020100300506032b656e0422042077076d0a7318a57d3c16c17251b26645"
                        + "df4c2f87ebc0992ab177fba51db92c2a";
        final String publicKeyHex =
                "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f";
        final String sharedKeyHex =
                "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742";

        KeyFactory kf =
                KeyFactory.getInstance(KEY_EXCHANGE_ALGORITHM_CURVE, KEY_EXCHANGE_CURVE_PROVIDER);
        PrivateKey privateKey =
                kf.generatePrivate(
                        new PKCS8EncodedKeySpec(TestUtils.hexStringToByteArray(privateKeyHex)));

        final byte[] sharedSecret =
                IkeKePayload.getSharedKey(
                        privateKey,
                        TestUtils.hexStringToByteArray(publicKeyHex),
                        SaProposal.DH_GROUP_CURVE_25519);

        assertArrayEquals(TestUtils.hexStringToByteArray(sharedKeyHex), sharedSecret);
    }
}
