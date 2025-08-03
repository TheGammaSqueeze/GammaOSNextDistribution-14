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

package android.net.cts;

import static android.net.IpSecAlgorithm.AUTH_AES_CMAC;
import static android.net.IpSecAlgorithm.AUTH_AES_XCBC;
import static android.net.IpSecAlgorithm.AUTH_CRYPT_CHACHA20_POLY1305;
import static android.net.IpSecAlgorithm.CRYPT_AES_CTR;
import static android.net.cts.PacketUtils.AES_CMAC;
import static android.net.cts.PacketUtils.AES_CMAC_ICV_LEN;
import static android.net.cts.PacketUtils.AES_CMAC_KEY_LEN;
import static android.net.cts.PacketUtils.AES_CTR;
import static android.net.cts.PacketUtils.AES_CTR_BLK_SIZE;
import static android.net.cts.PacketUtils.AES_CTR_IV_LEN;
import static android.net.cts.PacketUtils.AES_CTR_KEY_LEN_20;
import static android.net.cts.PacketUtils.AES_CTR_KEY_LEN_28;
import static android.net.cts.PacketUtils.AES_CTR_KEY_LEN_36;
import static android.net.cts.PacketUtils.AES_CTR_SALT_LEN;
import static android.net.cts.PacketUtils.AES_XCBC;
import static android.net.cts.PacketUtils.AES_XCBC_ICV_LEN;
import static android.net.cts.PacketUtils.AES_XCBC_KEY_LEN;
import static android.net.cts.PacketUtils.CHACHA20_POLY1305;
import static android.net.cts.PacketUtils.CHACHA20_POLY1305_BLK_SIZE;
import static android.net.cts.PacketUtils.CHACHA20_POLY1305_ICV_LEN;
import static android.net.cts.PacketUtils.CHACHA20_POLY1305_IV_LEN;
import static android.net.cts.PacketUtils.CHACHA20_POLY1305_KEY_LEN;
import static android.net.cts.PacketUtils.CHACHA20_POLY1305_SALT_LEN;
import static android.net.cts.PacketUtils.ESP_HDRLEN;
import static android.net.cts.PacketUtils.IP6_HDRLEN;
import static android.net.cts.PacketUtils.getIpHeader;
import static android.net.cts.util.CtsNetUtils.TestNetworkCallback;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assume.assumeTrue;

import android.net.IpSecAlgorithm;
import android.net.IpSecManager;
import android.net.IpSecTransform;
import android.net.Network;
import android.net.TestNetworkInterface;
import android.net.cts.PacketUtils.BytePayload;
import android.net.cts.PacketUtils.EspAeadCipher;
import android.net.cts.PacketUtils.EspAuth;
import android.net.cts.PacketUtils.EspAuthNull;
import android.net.cts.PacketUtils.EspCipher;
import android.net.cts.PacketUtils.EspCipherNull;
import android.net.cts.PacketUtils.EspCryptCipher;
import android.net.cts.PacketUtils.EspHeader;
import android.net.cts.PacketUtils.IpHeader;
import android.net.cts.PacketUtils.UdpHeader;
import android.platform.test.annotations.AppModeFull;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Socket cannot bind in instant app mode")
public class IpSecAlgorithmImplTest extends IpSecBaseTest {
    private static final InetAddress LOCAL_ADDRESS =
            InetAddress.parseNumericAddress("2001:db8:1::1");
    private static final InetAddress REMOTE_ADDRESS =
            InetAddress.parseNumericAddress("2001:db8:1::2");

    private static final int REMOTE_PORT = 12345;
    private static final IpSecManager sIpSecManager =
            InstrumentationRegistry.getContext().getSystemService(IpSecManager.class);

    private static class CheckCryptoImplTest implements TestNetworkRunnable.Test {
        private final IpSecAlgorithm mIpsecEncryptAlgo;
        private final IpSecAlgorithm mIpsecAuthAlgo;
        private final IpSecAlgorithm mIpsecAeadAlgo;
        private final EspCipher mEspCipher;
        private final EspAuth mEspAuth;

        CheckCryptoImplTest(
                IpSecAlgorithm ipsecEncryptAlgo,
                IpSecAlgorithm ipsecAuthAlgo,
                IpSecAlgorithm ipsecAeadAlgo,
                EspCipher espCipher,
                EspAuth espAuth) {
            mIpsecEncryptAlgo = ipsecEncryptAlgo;
            mIpsecAuthAlgo = ipsecAuthAlgo;
            mIpsecAeadAlgo = ipsecAeadAlgo;
            mEspCipher = espCipher;
            mEspAuth = espAuth;
        }

        private static byte[] buildTransportModeEspPayload(
                int srcPort, int dstPort, int spi, EspCipher espCipher, EspAuth espAuth)
                throws Exception {
            final UdpHeader udpPayload =
                    new UdpHeader(srcPort, dstPort, new BytePayload(TEST_DATA));
            final IpHeader preEspIpHeader =
                    getIpHeader(
                            udpPayload.getProtocolId(), LOCAL_ADDRESS, REMOTE_ADDRESS, udpPayload);

            final PacketUtils.EspHeader espPayload =
                    new EspHeader(
                            udpPayload.getProtocolId(),
                            spi,
                            1 /* sequence number */,
                            udpPayload.getPacketBytes(preEspIpHeader),
                            espCipher,
                            espAuth);
            return espPayload.getPacketBytes(preEspIpHeader);
        }

        @Override
        public void runTest(TestNetworkInterface testIface, TestNetworkCallback tunNetworkCallback)
                throws Exception {
            final TunUtils tunUtils = new TunUtils(testIface.getFileDescriptor());
            tunNetworkCallback.waitForAvailable();
            final Network testNetwork = tunNetworkCallback.currentNetwork;

            final IpSecTransform.Builder transformBuilder =
                    new IpSecTransform.Builder(InstrumentationRegistry.getContext());
            if (mIpsecAeadAlgo != null) {
                transformBuilder.setAuthenticatedEncryption(mIpsecAeadAlgo);
            } else {
                if (mIpsecEncryptAlgo != null) {
                    transformBuilder.setEncryption(mIpsecEncryptAlgo);
                }
                if (mIpsecAuthAlgo != null) {
                    transformBuilder.setAuthentication(mIpsecAuthAlgo);
                }
            }

            try (IpSecManager.SecurityParameterIndex outSpi =
                            sIpSecManager.allocateSecurityParameterIndex(REMOTE_ADDRESS);
                    IpSecManager.SecurityParameterIndex inSpi =
                            sIpSecManager.allocateSecurityParameterIndex(LOCAL_ADDRESS);
                    IpSecTransform outTransform =
                            transformBuilder.buildTransportModeTransform(LOCAL_ADDRESS, outSpi);
                    IpSecTransform inTransform =
                            transformBuilder.buildTransportModeTransform(REMOTE_ADDRESS, inSpi);
                    // Bind localSocket to a random available port.
                    DatagramSocket localSocket = new DatagramSocket(0)) {
                sIpSecManager.applyTransportModeTransform(
                        localSocket, IpSecManager.DIRECTION_IN, inTransform);
                sIpSecManager.applyTransportModeTransform(
                        localSocket, IpSecManager.DIRECTION_OUT, outTransform);

                // Send ESP packet
                final DatagramPacket outPacket =
                        new DatagramPacket(
                                TEST_DATA, 0, TEST_DATA.length, REMOTE_ADDRESS, REMOTE_PORT);
                testNetwork.bindSocket(localSocket);
                localSocket.send(outPacket);
                final byte[] outEspPacket =
                        tunUtils.awaitEspPacket(outSpi.getSpi(), false /* useEncap */);

                // Remove transform for good hygiene
                sIpSecManager.removeTransportModeTransforms(localSocket);

                // Get the kernel-generated ESP payload
                final byte[] outEspPayload = new byte[outEspPacket.length - IP6_HDRLEN];
                System.arraycopy(outEspPacket, IP6_HDRLEN, outEspPayload, 0, outEspPayload.length);

                // Get the IV of the kernel-generated ESP payload
                final byte[] iv =
                        Arrays.copyOfRange(
                                outEspPayload, ESP_HDRLEN, ESP_HDRLEN + mEspCipher.ivLen);

                // Build ESP payload using the kernel-generated IV and the user space crypto
                // implementations
                mEspCipher.updateIv(iv);
                final byte[] expectedEspPayload =
                        buildTransportModeEspPayload(
                                localSocket.getLocalPort(),
                                REMOTE_PORT,
                                outSpi.getSpi(),
                                mEspCipher,
                                mEspAuth);

                // Compare user-space-generated and kernel-generated ESP payload
                assertArrayEquals(expectedEspPayload, outEspPayload);
            }
        }

        @Override
        public void cleanupTest() {
            // Do nothing
        }

        @Override
        public InetAddress[] getTestNetworkAddresses() {
            return new InetAddress[] {LOCAL_ADDRESS};
        }
    }

    private void checkAesCtr(int keyLen) throws Exception {
        final byte[] cryptKey = getKeyBytes(keyLen);

        final IpSecAlgorithm ipsecEncryptAlgo =
                new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CTR, cryptKey);
        final EspCipher espCipher =
                new EspCryptCipher(
                        AES_CTR, AES_CTR_BLK_SIZE, cryptKey, AES_CTR_IV_LEN, AES_CTR_SALT_LEN);

        runWithShellPermissionIdentity(new TestNetworkRunnable(new CheckCryptoImplTest(
                ipsecEncryptAlgo, null /* ipsecAuthAlgo */, null /* ipsecAeadAlgo */,
                espCipher, EspAuthNull.getInstance())));
    }

    @Test
    public void testAesCtr160() throws Exception {
        assumeTrue(hasIpSecAlgorithm(CRYPT_AES_CTR));

        checkAesCtr(AES_CTR_KEY_LEN_20);
    }

    @Test
    public void testAesCtr224() throws Exception {
        assumeTrue(hasIpSecAlgorithm(CRYPT_AES_CTR));

        checkAesCtr(AES_CTR_KEY_LEN_28);
    }

    @Test
    public void testAesCtr288() throws Exception {
        assumeTrue(hasIpSecAlgorithm(CRYPT_AES_CTR));

        checkAesCtr(AES_CTR_KEY_LEN_36);
    }

    @Test
    public void testAesXcbc() throws Exception {
        assumeTrue(hasIpSecAlgorithm(AUTH_AES_XCBC));

        final byte[] authKey = getKeyBytes(AES_XCBC_KEY_LEN);
        final IpSecAlgorithm ipsecAuthAlgo =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_AES_XCBC, authKey, AES_XCBC_ICV_LEN * 8);
        final EspAuth espAuth = new EspAuth(AES_XCBC, authKey, AES_XCBC_ICV_LEN);

        runWithShellPermissionIdentity(new TestNetworkRunnable(new CheckCryptoImplTest(
                null /* ipsecEncryptAlgo */, ipsecAuthAlgo, null /* ipsecAeadAlgo */,
                EspCipherNull.getInstance(), espAuth)));
    }

    @Test
    public void testAesCmac() throws Exception {
        assumeTrue(hasIpSecAlgorithm(AUTH_AES_CMAC));

        final byte[] authKey = getKeyBytes(AES_CMAC_KEY_LEN);
        final IpSecAlgorithm ipsecAuthAlgo =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_AES_CMAC, authKey, AES_CMAC_ICV_LEN * 8);
        final EspAuth espAuth = new EspAuth(AES_CMAC, authKey, AES_CMAC_ICV_LEN);

        runWithShellPermissionIdentity(new TestNetworkRunnable(new CheckCryptoImplTest(
                null /* ipsecEncryptAlgo */, ipsecAuthAlgo, null /* ipsecAeadAlgo */,
                EspCipherNull.getInstance(), espAuth)));
    }

    @Test
    public void testChaCha20Poly1305() throws Exception {
        assumeTrue(hasIpSecAlgorithm(AUTH_CRYPT_CHACHA20_POLY1305));

        final byte[] cryptKey = getKeyBytes(CHACHA20_POLY1305_KEY_LEN);
        final IpSecAlgorithm ipsecAeadAlgo =
                new IpSecAlgorithm(
                        IpSecAlgorithm.AUTH_CRYPT_CHACHA20_POLY1305,
                        cryptKey,
                        CHACHA20_POLY1305_ICV_LEN * 8);
        final EspAeadCipher espAead =
                new EspAeadCipher(
                        CHACHA20_POLY1305,
                        CHACHA20_POLY1305_BLK_SIZE,
                        cryptKey,
                        CHACHA20_POLY1305_IV_LEN,
                        CHACHA20_POLY1305_ICV_LEN,
                        CHACHA20_POLY1305_SALT_LEN);

        runWithShellPermissionIdentity(
                new TestNetworkRunnable(
                        new CheckCryptoImplTest(
                                null /* ipsecEncryptAlgo */,
                                null /* ipsecAuthAlgo */,
                                ipsecAeadAlgo,
                                espAead,
                                EspAuthNull.getInstance())));
    }
}
