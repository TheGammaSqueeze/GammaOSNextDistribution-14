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

import com.android.adservices.ohttp.algorithms.AeadAlgorithmSpec;
import com.android.adservices.ohttp.algorithms.HpkeAlgorithmSpec;
import com.android.adservices.ohttp.algorithms.UnsupportedHpkeAlgorithmException;
import com.android.internal.annotations.VisibleForTesting;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Provides methods for OHTTP client side encryption and decryption
 *
 * <ul>
 *   <li>Facilitates client side to initiate OHttp request flow by initializing the key config
 *       obtained from server, and subsequently uses it to encrypt the request payload.
 *   <li>After initializing this class with server's key config, users can call
 *       `CreateObliviousHttpRequest` which constructs OHTTP request of the input payload.
 *   <li>Handles decryption of response that will be sent back from Server in HTTP POST body.
 *   <li>Handles BoringSSL HPKE context setup and bookkeeping.
 * </ul>
 */
public class ObliviousHttpClient {

    // As defined in https://www.ietf.org/archive/id/draft-ietf-ohai-ohttp-03.html#section-4.2-3
    // required to export a secret for decryption
    private static String sResponseLabel = "message/bhttp response";

    // HPKE export methods require context strings
    // Context strings to export aead key and nonce as defined in
    // https://www.ietf.org/archive/id/draft-ietf-ohai-ohttp-03.html#section-4.2-3
    private static String sAeadKeyContext = "key";
    private static String sAeadNonceContext = "nonce";

    private ObliviousHttpKeyConfig mObliviousHttpKeyConfig;
    private HpkeAlgorithmSpec mHpkeAlgorithmSpec;

    private ObliviousHttpClient(ObliviousHttpKeyConfig keyConfig, HpkeAlgorithmSpec algorithmSpec) {
        mObliviousHttpKeyConfig = keyConfig;
        mHpkeAlgorithmSpec = algorithmSpec;
    }

    /**
     * Creates the ObliviousHttpClient and initializes it with the given obliviousHttpKeyConfig
     *
     * @throws UnsupportedHpkeAlgorithmException if the key config specifies unsupported OHTTP/HPKE
     *     algorithms
     */
    public static ObliviousHttpClient create(ObliviousHttpKeyConfig keyConfig)
            throws UnsupportedHpkeAlgorithmException {
        HpkeAlgorithmSpec hpkeAlgorithmSpec = HpkeAlgorithmSpec.fromKeyConfig(keyConfig);
        return new ObliviousHttpClient(keyConfig, hpkeAlgorithmSpec);
    }

    /**
     * Takes the plainText byte array and returns an ObliviousHttpRequest object which contains the
     * shared secret and the cipher text along with HPKE context.
     */
    public ObliviousHttpRequest createObliviousHttpRequest(byte[] plainText) throws IOException {
        byte[] seed = getSecureRandomBytes(mHpkeAlgorithmSpec.kem().seedLength());
        return createObliviousHttpRequest(plainText, seed);
    }

    /**
     * Creates an Oblivious Http Request with the given seed to produce deterministic shared secret
     *
     * <p>Should only be used for testing purposes. For production uses, seeds should be randomly
     * generated and cryptographically safe.
     */
    @VisibleForTesting
    public ObliviousHttpRequest createObliviousHttpRequest(byte[] plainText, byte[] seed)
            throws IOException {
        HpkeContextNativeRef hpkeContextNativeRef =
                HpkeContextNativeRef.createHpkeContextReference();
        KemNativeRef kemNativeRef = mHpkeAlgorithmSpec.kem().kemNativeRefSupplier().get();
        KdfNativeRef kdfAlgorithmSpec = mHpkeAlgorithmSpec.kdf().kdfNativeRefSupplier().get();
        AeadNativeRef aeadNativeRef = mHpkeAlgorithmSpec.aead().aeadNativeRefSupplier().get();

        RecipientKeyInfo recipientKeyInfo = mObliviousHttpKeyConfig.createRecipientKeyInfo();
        OhttpJniWrapper ohttpJniWrapper = OhttpJniWrapper.getInstance();
        HpkeEncryptResponse encryptResponse =
                ohttpJniWrapper.hpkeEncrypt(
                        hpkeContextNativeRef,
                        kemNativeRef,
                        kdfAlgorithmSpec,
                        aeadNativeRef,
                        mObliviousHttpKeyConfig.publicKey(),
                        recipientKeyInfo.getBytes(),
                        seed,
                        plainText,
                        /* aad= */ null);

        ObliviousHttpRequestContext requestContext =
                ObliviousHttpRequestContext.create(
                        mObliviousHttpKeyConfig, encryptResponse.encapsulatedSharedSecret(), seed);
        return ObliviousHttpRequest.create(plainText, encryptResponse.cipherText(), requestContext);
    }

    /**
     * Takes the ciphertext returned by the server and decrypts it
     *
     * <p>Decrypt as per https://www.ietf.org/archive/id/draft-ietf-ohai-ohttp-03.html#section-4.2-3
     *
     * @param encryptedResponse The encrypted response to be decrypted
     * @param requestContext The ObliviousHttpRequestContext generated during call to
     *     createObliviousHttpRequest
     * @return the decrypted response
     */
    public byte[] decryptObliviousHttpResponse(
            byte[] encryptedResponse, ObliviousHttpRequestContext requestContext)
            throws IOException {
        OhttpJniWrapper ohttpJniWrapper = OhttpJniWrapper.getInstance();

        // secret = context.Export("message/bhttp response", Nk)
        byte[] secret = export(ohttpJniWrapper, requestContext);

        byte[] responseNonce = extractResponseNonce(encryptedResponse);

        // salt = concat(enc, response_nonce)
        byte[] salt = concatSalt(requestContext, responseNonce);

        HkdfMessageDigestNativeRef messageDigest =
                mHpkeAlgorithmSpec.kdf().messageDigestSupplier().get();

        // prk = Extract(salt, secret)
        byte[] prk = extract(ohttpJniWrapper, messageDigest, secret, salt);

        //  aead_key = Expand(prk, "key", Nk)
        byte[] keyContext = sAeadKeyContext.getBytes(StandardCharsets.US_ASCII);
        AeadAlgorithmSpec aead = mHpkeAlgorithmSpec.aead();
        HkdfExpandResponse hkdfKeyResponse =
                ohttpJniWrapper.hkdfExpand(messageDigest, prk, keyContext, aead.keyLength());

        // aead_nonce = Expand(prk, "nonce", Nn)
        byte[] nonceContext = sAeadNonceContext.getBytes(StandardCharsets.US_ASCII);
        HkdfExpandResponse hkdfNonceResponse =
                ohttpJniWrapper.hkdfExpand(messageDigest, prk, nonceContext, aead.nonceLength());

        AeadNativeRef aeadNativeRef = aead.aeadNativeRefSupplier().get();
        byte[] cipherText = extractCipherText(encryptedResponse);
        byte[] decrypted =
                ohttpJniWrapper.aeadOpen(
                        aeadNativeRef,
                        hkdfKeyResponse.getBytes(),
                        hkdfNonceResponse.getBytes(),
                        cipherText);

        return decrypted;
    }

    @VisibleForTesting
    HpkeAlgorithmSpec getHpkeAlgorithmSpec() {
        return mHpkeAlgorithmSpec;
    }

    private byte[] extractResponseNonce(byte[] encryptedResponse) {
        // enc_response = concat(response_nonce, ct)
        // Length of response nonce => max(Nn, Nk)

        AeadAlgorithmSpec aead = mHpkeAlgorithmSpec.aead();
        int lengthNonce = Math.max(aead.nonceLength(), aead.keyLength());

        return Arrays.copyOfRange(encryptedResponse, 0, lengthNonce);
    }

    private byte[] extractCipherText(byte[] encryptedResponse) {
        // enc_response = concat(response_nonce, ct)
        // Length of response nonce => max(Nn, Nk)

        AeadAlgorithmSpec aead = mHpkeAlgorithmSpec.aead();
        int lengthNonce = Math.max(aead.nonceLength(), aead.keyLength());

        return Arrays.copyOfRange(encryptedResponse, lengthNonce, encryptedResponse.length);
    }

    private byte[] getSecureRandomBytes(int length) {
        SecureRandom random = new SecureRandom();
        byte[] token = new byte[length];
        random.nextBytes(token);

        return token;
    }

    private byte[] export(
            OhttpJniWrapper ohttpJniWrapper, ObliviousHttpRequestContext requestContext)
            throws IOException {
        byte[] labelBytes = sResponseLabel.getBytes(StandardCharsets.US_ASCII);

        // Regenerate HPKE context
        KemNativeRef kemNativeRef = mHpkeAlgorithmSpec.kem().kemNativeRefSupplier().get();
        KdfNativeRef kdfAlgorithmSpec = mHpkeAlgorithmSpec.kdf().kdfNativeRefSupplier().get();
        AeadNativeRef aeadNativeRef = mHpkeAlgorithmSpec.aead().aeadNativeRefSupplier().get();
        RecipientKeyInfo recipientKeyInfo = mObliviousHttpKeyConfig.createRecipientKeyInfo();
        HpkeContextNativeRef hpkectx = HpkeContextNativeRef.createHpkeContextReference();
        ohttpJniWrapper.hpkeCtxSetupSenderWithSeed(
                hpkectx,
                kemNativeRef,
                kdfAlgorithmSpec,
                aeadNativeRef,
                mObliviousHttpKeyConfig.publicKey(),
                recipientKeyInfo.getBytes(),
                requestContext.seed());

        HpkeExportResponse exportResponse =
                ohttpJniWrapper.hpkeExport(
                        hpkectx, labelBytes, mHpkeAlgorithmSpec.aead().keyLength());
        return exportResponse.getBytes();
    }

    private byte[] concatSalt(ObliviousHttpRequestContext requestContext, byte[] responseNonce)
            throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(requestContext.encapsulatedSharedSecret().getBytes());
        outputStream.write(responseNonce);
        return outputStream.toByteArray();
    }

    private byte[] extract(
            OhttpJniWrapper ohttpJniWrapper,
            HkdfMessageDigestNativeRef messageDigest,
            byte[] secret,
            byte[] salt) {
        HkdfExtractResponse extractResponse =
                ohttpJniWrapper.hkdfExtract(messageDigest, secret, salt);
        return extractResponse.getBytes();
    }
}
