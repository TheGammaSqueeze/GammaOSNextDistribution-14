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
package com.android.adservices.ohttp;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.common.io.BaseEncoding;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class OhttpJniWrapperTest {

    private static final String SERVER_PUBLIC_KEY =
            "6d21cfe09fbea5122f9ebc2eb2a69fcc4f06408cd54aac934f012e76fcdcef62";

    private static final String SERVER_PRIVATE_KEY =
            "b77431ecfa8f4cfc30d6e467aafa06944dffe28cb9dd1409e33a3045f5adc8a1";

    private static final String KEM_SEED = "wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww";

    private final OhttpJniWrapper mOhttpJniWrapper = OhttpJniWrapper.getInstance();

    @Test
    public void hpkeKemDhkemX25519HkdfSha256_success_nonZeroReference() {
        Assert.assertNotEquals(mOhttpJniWrapper.hpkeKemDhkemX25519HkdfSha256(), 0);
    }

    @Test
    public void hpkeKdfHkdfSha256_success_nonZeroReference() {
        Assert.assertNotEquals(mOhttpJniWrapper.hpkeKdfHkdfSha256(), 0);
    }

    @Test
    public void hpkeAeadAes256Gcm_success_nonZeroReference() {
        Assert.assertNotEquals(mOhttpJniWrapper.hpkeAeadAes256Gcm(), 0);
    }

    @Test
    public void hpkeCtxNew_success_nonZeroReference() {
        Assert.assertNotEquals(mOhttpJniWrapper.hpkeCtxNew(), 0);
    }

    @Test
    public void freeHpkeContext_success_noError() {
        mOhttpJniWrapper.hpkeCtxFree(mOhttpJniWrapper.hpkeCtxNew());
    }

    @Test
    public void hpkeCtxSetupSenderWithSeed_returnsCorrectSharedSecret() throws Exception {
        KemNativeRef kem = KemNativeRef.getHpkeKemDhkemX25519HkdfSha256Reference();
        KdfNativeRef kdf = KdfNativeRef.getHpkeKdfHkdfSha256Reference();
        AeadNativeRef aead = AeadNativeRef.getHpkeAeadAes256GcmReference();
        HpkeContextNativeRef evpCtxSender = HpkeContextNativeRef.createHpkeContextReference();
        byte[] seedBytes = KEM_SEED.getBytes(StandardCharsets.US_ASCII);
        byte[] recipientKeyInfoBytes = createRecipientKeyInfoBytes();
        byte[] keyBytes = BaseEncoding.base16().lowerCase().decode(SERVER_PUBLIC_KEY);

        EncapsulatedSharedSecret enc =
                mOhttpJniWrapper.hpkeCtxSetupSenderWithSeed(
                        evpCtxSender, kem, kdf, aead, keyBytes, recipientKeyInfoBytes, seedBytes);

        Assert.assertEquals(
                BaseEncoding.base16().lowerCase().encode(enc.getBytes()),
                "1cf579aba45a10ba1d1ef06d91fca2aa9ed0a1150515653155405d0b18cb9a67");
    }

    @Test
    public void hpkeEncrypt_returnsCorrectResponse() throws Exception {
        KemNativeRef kem = KemNativeRef.getHpkeKemDhkemX25519HkdfSha256Reference();
        KdfNativeRef kdf = KdfNativeRef.getHpkeKdfHkdfSha256Reference();
        AeadNativeRef aead = AeadNativeRef.getHpkeAeadAes256GcmReference();
        HpkeContextNativeRef evpCtxSender = HpkeContextNativeRef.createHpkeContextReference();
        byte[] seedBytes = KEM_SEED.getBytes(StandardCharsets.US_ASCII);
        byte[] recipientKeyInfoBytes = createRecipientKeyInfoBytes();
        byte[] keyBytes = BaseEncoding.base16().lowerCase().decode(SERVER_PUBLIC_KEY);
        String plainText = "test request 1";
        byte[] plainTextBytes = plainText.getBytes(StandardCharsets.US_ASCII);

        mOhttpJniWrapper.hpkeCtxSetupSenderWithSeed(
                evpCtxSender, kem, kdf, aead, keyBytes, recipientKeyInfoBytes, seedBytes);
        HpkeEncryptResponse response =
                mOhttpJniWrapper.hpkeEncrypt(
                        evpCtxSender,
                        kem,
                        kdf,
                        aead,
                        keyBytes,
                        recipientKeyInfoBytes,
                        seedBytes,
                        plainTextBytes,
                        null);

        Assert.assertEquals(
                BaseEncoding.base16()
                        .lowerCase()
                        .encode(response.encapsulatedSharedSecret().getBytes()),
                "1cf579aba45a10ba1d1ef06d91fca2aa9ed0a1150515653155405d0b18cb9a67");
        Assert.assertEquals(
                BaseEncoding.base16().lowerCase().encode(response.cipherText()),
                "2ef2da3b97acee493624b9959f0fc6df008a6f0701c923c5a60ed0ed2c34");
    }

    @Test
    public void hpkeEncrypt_emptyPlainText_returnsNullCipherText() throws Exception {
        KemNativeRef kem = KemNativeRef.getHpkeKemDhkemX25519HkdfSha256Reference();
        KdfNativeRef kdf = KdfNativeRef.getHpkeKdfHkdfSha256Reference();
        AeadNativeRef aead = AeadNativeRef.getHpkeAeadAes256GcmReference();
        HpkeContextNativeRef evpCtxSender = HpkeContextNativeRef.createHpkeContextReference();
        byte[] seedBytes = KEM_SEED.getBytes(StandardCharsets.US_ASCII);
        byte[] recipientKeyInfoBytes = createRecipientKeyInfoBytes();
        byte[] keyBytes = BaseEncoding.base16().lowerCase().decode(SERVER_PUBLIC_KEY);

        mOhttpJniWrapper.hpkeCtxSetupSenderWithSeed(
                evpCtxSender, kem, kdf, aead, keyBytes, recipientKeyInfoBytes, seedBytes);
        HpkeEncryptResponse response =
                mOhttpJniWrapper.hpkeEncrypt(
                        evpCtxSender,
                        kem,
                        kdf,
                        aead,
                        keyBytes,
                        recipientKeyInfoBytes,
                        seedBytes,
                        null,
                        null);

        Assert.assertEquals(
                BaseEncoding.base16()
                        .lowerCase()
                        .encode(response.encapsulatedSharedSecret().getBytes()),
                "1cf579aba45a10ba1d1ef06d91fca2aa9ed0a1150515653155405d0b18cb9a67");
        Assert.assertNull(response.cipherText());
    }

    @Test
    public void hpkeCtxSetupSenderWithSeed_nullContext_throwsException() throws Exception {
        KemNativeRef kem = KemNativeRef.getHpkeKemDhkemX25519HkdfSha256Reference();
        KdfNativeRef kdf = KdfNativeRef.getHpkeKdfHkdfSha256Reference();
        AeadNativeRef aead = AeadNativeRef.getHpkeAeadAes256GcmReference();
        HpkeContextNativeRef evpCtxSender = null;
        byte[] seedBytes = KEM_SEED.getBytes(StandardCharsets.US_ASCII);
        byte[] recipientKeyInfoBytes = createRecipientKeyInfoBytes();
        byte[] keyBytes = BaseEncoding.base16().lowerCase().decode(SERVER_PUBLIC_KEY);

        assertThrows(
                NullPointerException.class,
                () ->
                        mOhttpJniWrapper.hpkeCtxSetupSenderWithSeed(
                                evpCtxSender,
                                kem,
                                kdf,
                                aead,
                                keyBytes,
                                recipientKeyInfoBytes,
                                seedBytes));
    }

    @Test
    public void hpkeCtxSetupSenderWithSeed_emptyPublicKey_throwsException() throws Exception {
        KemNativeRef kem = KemNativeRef.getHpkeKemDhkemX25519HkdfSha256Reference();
        KdfNativeRef kdf = KdfNativeRef.getHpkeKdfHkdfSha256Reference();
        AeadNativeRef aead = AeadNativeRef.getHpkeAeadAes256GcmReference();
        HpkeContextNativeRef evpCtxSender = HpkeContextNativeRef.createHpkeContextReference();
        byte[] seedBytes = KEM_SEED.getBytes(StandardCharsets.US_ASCII);
        byte[] recipientKeyInfoBytes = createRecipientKeyInfoBytes();
        byte[] keyBytes = null;

        assertThrows(
                NullPointerException.class,
                () ->
                        mOhttpJniWrapper.hpkeCtxSetupSenderWithSeed(
                                evpCtxSender,
                                kem,
                                kdf,
                                aead,
                                keyBytes,
                                recipientKeyInfoBytes,
                                seedBytes));
    }

    @Test
    public void hpkeCtxSetupSenderWithSeed_invalidPublicKey_returnsNullArray() throws Exception {
        KemNativeRef kem = KemNativeRef.getHpkeKemDhkemX25519HkdfSha256Reference();
        KdfNativeRef kdf = KdfNativeRef.getHpkeKdfHkdfSha256Reference();
        AeadNativeRef aead = AeadNativeRef.getHpkeAeadAes256GcmReference();
        HpkeContextNativeRef evpCtxSender = HpkeContextNativeRef.createHpkeContextReference();
        String serverPublicKey = "abcd";
        byte[] seedBytes = KEM_SEED.getBytes(StandardCharsets.US_ASCII);
        byte[] recipientKeyInfoBytes = createRecipientKeyInfoBytes();
        byte[] keyBytes = BaseEncoding.base16().lowerCase().decode(serverPublicKey);

        EncapsulatedSharedSecret enc =
                mOhttpJniWrapper.hpkeCtxSetupSenderWithSeed(
                        evpCtxSender, kem, kdf, aead, keyBytes, recipientKeyInfoBytes, seedBytes);

        Assert.assertNull(enc.getBytes());
    }

    @Test
    public void hpkeExport_returnsCorrectSecret() throws Exception {
        HpkeContextNativeRef evpCtxSender = setupAndGetHpkeContext();
        String label = "message/bhttp response";
        byte[] labelBytes = label.getBytes(StandardCharsets.US_ASCII);

        HpkeExportResponse hpkeExportedSecret =
                mOhttpJniWrapper.hpkeExport(evpCtxSender, labelBytes, 32);

        Assert.assertEquals(
                BaseEncoding.base16().lowerCase().encode(hpkeExportedSecret.getBytes()),
                "3b8795c1fdb196febef3c9ab31bd9d7d55c63c828feeb686c2233e90c1d440d4");
    }

    @Test
    public void hkdfExtract_returnsCorrectResponse() throws Exception {
        byte[] hpkeExportedSecret =
                BaseEncoding.base16()
                        .lowerCase()
                        .decode("3b8795c1fdb196febef3c9ab31bd9d7d55c63c828feeb686c2233e90c1d440d4");
        String responseNonceString = "llllllllllllllllllllllllllllllll";
        byte[] responseNonce = responseNonceString.getBytes(StandardCharsets.US_ASCII);
        String responseNonceHex = BaseEncoding.base16().lowerCase().encode(responseNonce);
        String enc = "1cf579aba45a10ba1d1ef06d91fca2aa9ed0a1150515653155405d0b18cb9a67";
        byte[] salt = BaseEncoding.base16().lowerCase().decode(enc + responseNonceHex);

        HkdfExtractResponse extract =
                mOhttpJniWrapper.hkdfExtract(
                        HkdfMessageDigestNativeRef.getHkdfSha256MessageDigestReference(),
                        hpkeExportedSecret,
                        salt);

        Assert.assertEquals(
                BaseEncoding.base16().lowerCase().encode(extract.getBytes()),
                "095b6f1ef722296230a76445694d7d1f7017f541732915bc1034af039e18abf0");
    }

    @Test
    public void hkdfExpand_returnsCorrectResponse() throws Exception {
        byte[] keyInfo = "key".getBytes(StandardCharsets.US_ASCII);
        byte[] prk =
                BaseEncoding.base16()
                        .lowerCase()
                        .decode("095b6f1ef722296230a76445694d7d1f7017f541732915bc1034af039e18abf0");

        HkdfExpandResponse expandedKey =
                mOhttpJniWrapper.hkdfExpand(
                        HkdfMessageDigestNativeRef.getHkdfSha256MessageDigestReference(),
                        prk,
                        keyInfo,
                        32);

        Assert.assertEquals(
                BaseEncoding.base16().lowerCase().encode(expandedKey.getBytes()),
                "373975f067d2ba1afb5ef6183715ab36f9a61e6d9a405a1b080a3afdc127258a");
    }

    @Test
    public void aeadSeal_returnsCorrectResponse() throws Exception {
        byte[] aeadKey =
                BaseEncoding.base16()
                        .lowerCase()
                        .decode("373975f067d2ba1afb5ef6183715ab36f9a61e6d9a405a1b080a3afdc127258a");
        byte[] aeadNonce = BaseEncoding.base16().lowerCase().decode("c529e635076b377e4f78752a");
        byte[] plainText =
                BaseEncoding.base16().lowerCase().decode("7465737420726573706f6e73652031");

        byte[] cipherText =
                mOhttpJniWrapper.aeadSeal(
                        AeadNativeRef.getHpkeAeadAes256GcmReference(),
                        aeadKey,
                        aeadNonce,
                        plainText);

        Assert.assertEquals(
                "f623a32dba30cdf1a011543bdd7e95ace60be30b029574dc3be9abee478df9",
                BaseEncoding.base16().lowerCase().encode(cipherText));
    }

    @Test
    public void aeadOpen_returnsCorrectResponse() throws Exception {
        byte[] aeadKey =
                BaseEncoding.base16()
                        .lowerCase()
                        .decode("373975f067d2ba1afb5ef6183715ab36f9a61e6d9a405a1b080a3afdc127258a");
        byte[] aeadNonce = BaseEncoding.base16().lowerCase().decode("c529e635076b377e4f78752a");
        byte[] cipherText =
                BaseEncoding.base16()
                        .lowerCase()
                        .decode("f623a32dba30cdf1a011543bdd7e95ace60be30b029574dc3be9abee478df9");

        byte[] plainText =
                mOhttpJniWrapper.aeadOpen(
                        AeadNativeRef.getHpkeAeadAes256GcmReference(),
                        aeadKey,
                        aeadNonce,
                        cipherText);

        Assert.assertEquals(
                BaseEncoding.base16().lowerCase().encode(plainText),
                "7465737420726573706f6e73652031");
    }

    @Test
    public void hpkeSetupRecipient_missingkey_throwsError() throws Exception {

        KemNativeRef kem = KemNativeRef.getHpkeKemDhkemX25519HkdfSha256Reference();
        KdfNativeRef kdf = KdfNativeRef.getHpkeKdfHkdfSha256Reference();
        AeadNativeRef aead = AeadNativeRef.getHpkeAeadAes256GcmReference();
        HpkeContextNativeRef evpCtxSender = HpkeContextNativeRef.createHpkeContextReference();

        byte[] seedBytes = KEM_SEED.getBytes(StandardCharsets.US_ASCII);
        byte[] recipientKeyInfoBytes = createRecipientKeyInfoBytes();
        byte[] keyBytes = BaseEncoding.base16().lowerCase().decode(SERVER_PUBLIC_KEY);
        String plainText = "test request 1";
        byte[] plainTextBytes = plainText.getBytes(StandardCharsets.US_ASCII);

        mOhttpJniWrapper.hpkeCtxSetupSenderWithSeed(
                evpCtxSender, kem, kdf, aead, keyBytes, recipientKeyInfoBytes, seedBytes);
        HpkeEncryptResponse response =
                mOhttpJniWrapper.hpkeEncrypt(
                        evpCtxSender,
                        kem,
                        kdf,
                        aead,
                        keyBytes,
                        recipientKeyInfoBytes,
                        seedBytes,
                        plainTextBytes,
                        null);

        byte[] privateKeyBytes = BaseEncoding.base16().lowerCase().decode("3b");

        HpkeContextNativeRef evpCtxRecipient = HpkeContextNativeRef.createHpkeContextReference();
        assertThrows(
                IllegalStateException.class,
                () ->
                        mOhttpJniWrapper.hpkeSetupRecipient(
                                evpCtxRecipient,
                                kem,
                                kdf,
                                aead,
                                OhttpGatewayPrivateKey.create(privateKeyBytes),
                                response.encapsulatedSharedSecret(),
                                RecipientKeyInfo.create(recipientKeyInfoBytes)));
    }

    @Test
    public void hpkeSetupRecipient_successful() throws Exception {
        KemNativeRef kem = KemNativeRef.getHpkeKemDhkemX25519HkdfSha256Reference();
        KdfNativeRef kdf = KdfNativeRef.getHpkeKdfHkdfSha256Reference();
        AeadNativeRef aead = AeadNativeRef.getHpkeAeadAes256GcmReference();
        HpkeContextNativeRef evpCtxSender = HpkeContextNativeRef.createHpkeContextReference();

        byte[] seedBytes = KEM_SEED.getBytes(StandardCharsets.US_ASCII);
        byte[] recipientKeyInfoBytes = createRecipientKeyInfoBytes();
        byte[] keyBytes = BaseEncoding.base16().lowerCase().decode(SERVER_PUBLIC_KEY);
        String plainText = "test request 1";
        byte[] plainTextBytes = plainText.getBytes(StandardCharsets.US_ASCII);

        mOhttpJniWrapper.hpkeCtxSetupSenderWithSeed(
                evpCtxSender, kem, kdf, aead, keyBytes, recipientKeyInfoBytes, seedBytes);
        HpkeEncryptResponse response =
                mOhttpJniWrapper.hpkeEncrypt(
                        evpCtxSender,
                        kem,
                        kdf,
                        aead,
                        keyBytes,
                        recipientKeyInfoBytes,
                        seedBytes,
                        plainTextBytes,
                        null);

        byte[] privateKeyBytes = BaseEncoding.base16().lowerCase().decode(SERVER_PRIVATE_KEY);
        HpkeContextNativeRef evpCtxRecipient = HpkeContextNativeRef.createHpkeContextReference();
        assertTrue(
                mOhttpJniWrapper.hpkeSetupRecipient(
                        evpCtxRecipient,
                        kem,
                        kdf,
                        aead,
                        OhttpGatewayPrivateKey.create(privateKeyBytes),
                        response.encapsulatedSharedSecret(),
                        RecipientKeyInfo.create(recipientKeyInfoBytes)));
    }

    @Test
    public void gatewayDecrypt_returnsCorrectResponse() throws Exception {
        KemNativeRef kem = KemNativeRef.getHpkeKemDhkemX25519HkdfSha256Reference();
        KdfNativeRef kdf = KdfNativeRef.getHpkeKdfHkdfSha256Reference();
        AeadNativeRef aead = AeadNativeRef.getHpkeAeadAes256GcmReference();
        HpkeContextNativeRef evpCtxSender = HpkeContextNativeRef.createHpkeContextReference();

        byte[] seedBytes = KEM_SEED.getBytes(StandardCharsets.US_ASCII);
        byte[] recipientKeyInfoBytes = createRecipientKeyInfoBytes();
        byte[] keyBytes = BaseEncoding.base16().lowerCase().decode(SERVER_PUBLIC_KEY);
        String plainText = "test request 1";
        byte[] plainTextBytes = plainText.getBytes(StandardCharsets.US_ASCII);

        mOhttpJniWrapper.hpkeCtxSetupSenderWithSeed(
                evpCtxSender, kem, kdf, aead, keyBytes, recipientKeyInfoBytes, seedBytes);
        HpkeEncryptResponse response =
                mOhttpJniWrapper.hpkeEncrypt(
                        evpCtxSender,
                        kem,
                        kdf,
                        aead,
                        keyBytes,
                        recipientKeyInfoBytes,
                        seedBytes,
                        plainTextBytes,
                        null);

        byte[] privateKeyBytes = BaseEncoding.base16().lowerCase().decode(SERVER_PRIVATE_KEY);
        HpkeContextNativeRef evpCtxRecipient = HpkeContextNativeRef.createHpkeContextReference();
        mOhttpJniWrapper.hpkeSetupRecipient(
                evpCtxRecipient,
                kem,
                kdf,
                aead,
                OhttpGatewayPrivateKey.create(privateKeyBytes),
                response.encapsulatedSharedSecret(),
                RecipientKeyInfo.create(recipientKeyInfoBytes));
        GatewayDecryptResponse decryptResponse =
                mOhttpJniWrapper.gatewayDecrypt(
                        evpCtxRecipient, kem, kdf, aead, response.cipherText());

        String unencryptedText = new String(decryptResponse.getBytes(), StandardCharsets.US_ASCII);

        Assert.assertEquals(unencryptedText, plainText);
    }

    private HpkeContextNativeRef setupAndGetHpkeContext() throws Exception {
        KemNativeRef kem = KemNativeRef.getHpkeKemDhkemX25519HkdfSha256Reference();
        KdfNativeRef kdf = KdfNativeRef.getHpkeKdfHkdfSha256Reference();
        AeadNativeRef aead = AeadNativeRef.getHpkeAeadAes256GcmReference();
        HpkeContextNativeRef evpCtxSender = HpkeContextNativeRef.createHpkeContextReference();
        byte[] seedBytes = KEM_SEED.getBytes(StandardCharsets.US_ASCII);
        byte[] recipientKeyInfoBytes = createRecipientKeyInfoBytes();
        byte[] keyBytes = BaseEncoding.base16().lowerCase().decode(SERVER_PUBLIC_KEY);

        mOhttpJniWrapper.hpkeCtxSetupSenderWithSeed(
                evpCtxSender, kem, kdf, aead, keyBytes, recipientKeyInfoBytes, seedBytes);

        return evpCtxSender;
    }

    // This method will eventually be part of ObliviousHttpKeyConfig
    private static byte[] createRecipientKeyInfoBytes() throws Exception {
        /*
          hdr = concat(encode(1, keyID),
               encode(2, kemID),
               encode(2, kdfID),
               encode(2, aeadID))
          info = concat(encode_str("message/bhttp request"),
                encode(1, 0),
                hdr)
        */
        int keyId = 4;
        int kemId = 0x0020;
        int kdfId = 0x0001;
        int aeadId = 0x0002;
        String httpLabel = "message/bhttp request";
        byte[] b = httpLabel.getBytes(StandardCharsets.US_ASCII);
        byte[] header = new byte[7];
        header[0] = (byte) keyId;
        header[1] = header[3] = header[5] = (byte) 0;
        header[2] = (byte) kemId;
        header[4] = (byte) kdfId;
        header[6] = (byte) aeadId;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(b);
        outputStream.write((byte) 0);
        outputStream.write(header);

        return outputStream.toByteArray();
    }
}
