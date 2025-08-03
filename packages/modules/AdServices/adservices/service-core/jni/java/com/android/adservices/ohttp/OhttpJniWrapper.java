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

import android.annotation.Nullable;

import java.util.Objects;

/**
 * Contains JNI wrappers over BoringSSL methods required to implement Oblivious HTTP.
 *
 * <p>The wrappers follow the following design philosophies:
 *
 * <ul>
 *   <li>None of the JNI wrappers marshal or unmarshal complex Java objects across the JNI boundary.
 *   <li>If the class provides a method to allocate memory on the heap (usually with _new suffix),
 *       it will also provide a method to free that memory (usually with _free suffix)
 * </ul>
 *
 * Mockito can not mock native methods. Hence, this class implements an interface {@link
 * IOhttpJniWrapper} that can be mocked.
 */
// TODO(b/309107764) : Refactor this class to have a consistent style
class OhttpJniWrapper implements IOhttpJniWrapper {
    private static OhttpJniWrapper sWrapper;

    private OhttpJniWrapper() {
        // We included ohttp_jni as part of hpki_jni shared lib to save on space.
        // Creating a new shared library was around 50 KB more expensive.
        System.loadLibrary("hpke_jni");
    }

    public static OhttpJniWrapper getInstance() {
        if (sWrapper == null) {
            sWrapper = new OhttpJniWrapper();
        }

        return sWrapper;
    }

    /**
     * Releases memory associated with the HPKE context, which must have been created with {@link
     * #hpkeCtxNew()}
     */
    public native void hpkeCtxFree(long ctx);

    /** Returns the reference to the KEM algorithm DHKEM(X25519, HKDF-SHA256) */
    public native long hpkeKemDhkemX25519HkdfSha256();

    /** Returns a reference to the KDF algorithm HKDF-SHA256 */
    public native long hpkeKdfHkdfSha256();

    /** Returns a reference to the AEAD algorithm AES-256-GCM */
    public native long hpkeAeadAes256Gcm();

    /** Returns a reference to the HKDF_SHA256 message digest (i.e., SHA256) */
    public native long hkdfSha256MessageDigest();

    /**
     * Returns reference to a newly-allocated EVP_HPKE_CTX BoringSSL object. Object thus created
     * must be freed by calling {@link #hpkeCtxFree(long)}
     */
    public native long hpkeCtxNew();

    /**
     * Calls the boringSSL {@code EVP_HPKE_CTX_setup_sender_with_seed} method which implements the
     * SetupBaseS HPKE operation.
     *
     * <p>It encapsulates and returns the sharedSecret for publicKey and sets up ctx as sender
     * context.
     *
     * @param hpkeContextNativeRef The EVP_HPKE_CTX context containing reference of the heap
     *     allocated HPKE context created using hpkeCtxNew()
     * @param kemNativeRef The reference to the KEM algorithm
     * @param kdfNativeRef The reference to the KDF algorithm
     * @param aeadNativeRef The reference to the AEAD algorithm
     * @param publicKey The server's public key
     * @param info Optional info parameter used by the KDF algorithm. See
     *     https://www.rfc-editor.org/rfc/rfc9180#name-encryption-to-a-public-key
     * @param seed Seed to deterministically generate the KEM ephemeral key. See
     *     https://www.rfc-editor.org/rfc/rfc9180#name-derivekeypair
     * @return The encapsulated shared secret that can be decrypted by the server using their
     *     private key and will also be required on the client to decrypt server's response
     */
    public EncapsulatedSharedSecret hpkeCtxSetupSenderWithSeed(
            HpkeContextNativeRef hpkeContextNativeRef,
            KemNativeRef kemNativeRef,
            KdfNativeRef kdfNativeRef,
            AeadNativeRef aeadNativeRef,
            byte[] publicKey,
            @Nullable byte[] info,
            byte[] seed) {
        Objects.requireNonNull(hpkeContextNativeRef);
        Objects.requireNonNull(kemNativeRef);
        Objects.requireNonNull(kdfNativeRef);
        Objects.requireNonNull(aeadNativeRef);
        Objects.requireNonNull(publicKey);
        Objects.requireNonNull(seed);
        byte[] encapsulatedSharedSecret =
                hpkeCtxSetupSenderWithSeed(
                        hpkeContextNativeRef.getAddress(),
                        kemNativeRef.getAddress(),
                        kdfNativeRef.getAddress(),
                        aeadNativeRef.getAddress(),
                        publicKey,
                        info,
                        seed);
        return EncapsulatedSharedSecret.create(encapsulatedSharedSecret);
    }

    /**
     * Calls HPKE setupSender and Seal operations. These two operations combined make up HPKE
     * encryption.
     *
     * <p>{@link #hpkeCtxSeal(HpkeContextNativeRef, byte[], byte[])} should never be called without
     * calling {@link #hpkeCtxSetupSenderWithSeed(HpkeContextNativeRef, KemNativeRef, KdfNativeRef,
     * AeadNativeRef, byte[], byte[], byte[])} first. Hence, we provide a public method that calls
     * both.
     *
     * @param hpkeContextNativeRef Reference to the HPKE context object
     * @param kemNativeRef Reference to the KEM object
     * @param kdfNativeRef Reference to the KDF object
     * @param aeadNativeRef Reference to the AEAD object
     * @param publicKey Server's public key used to encapsulate shared secret
     * @param info Optional info parameter used by the KDF algorithm during setupSender operation.
     *     See https://www.rfc-editor.org/rfc/rfc9180#name-encryption-to-a-public-key
     * @param seed Seed to deterministically generate the KEM ephemeral key. See
     *     https://www.rfc-editor.org/rfc/rfc9180#name-derivekeypair
     * @param plainText The plain text to be encrypted
     * @param aad An optional additional info parameter that provides additional authenticated data
     *     to the AEAD algorithm in use. See
     *     https://www.rfc-editor.org/rfc/rfc9180#name-derivekeypair
     * @return {@link HpkeEncryptResponse} containing the encrypted cipher text and the encapsulated
     *     shared secret that would later be useful for decryption.
     */
    public HpkeEncryptResponse hpkeEncrypt(
            HpkeContextNativeRef hpkeContextNativeRef,
            KemNativeRef kemNativeRef,
            KdfNativeRef kdfNativeRef,
            AeadNativeRef aeadNativeRef,
            byte[] publicKey,
            @Nullable byte[] info,
            byte[] seed,
            @Nullable byte[] plainText,
            @Nullable byte[] aad) {
        EncapsulatedSharedSecret encapsulatedSharedSecret =
                hpkeCtxSetupSenderWithSeed(
                        hpkeContextNativeRef,
                        kemNativeRef,
                        kdfNativeRef,
                        aeadNativeRef,
                        publicKey,
                        info,
                        seed);

        byte[] cipherText = hpkeCtxSeal(hpkeContextNativeRef, plainText, aad);

        return HpkeEncryptResponse.create(encapsulatedSharedSecret, cipherText);
    }

    /**
     * Provides the Ohttp gateway side decryption method
     *
     * <p>https://www.ietf.org/archive/id/draft-ietf-ohai-ohttp-03.html#section-4.1-10
     */
    public GatewayDecryptResponse gatewayDecrypt(
            HpkeContextNativeRef hpkeContextNativeRef,
            KemNativeRef kemNativeRef,
            KdfNativeRef kdfNativeRef,
            AeadNativeRef aeadNativeRef,
            byte[] cipherText) {

        byte[] decryptedBytes =
                gatewayDecrypt(
                        hpkeContextNativeRef.getAddress(),
                        kemNativeRef.getAddress(),
                        kdfNativeRef.getAddress(),
                        aeadNativeRef.getAddress(),
                        cipherText);

        return GatewayDecryptResponse.create(decryptedBytes);
    }

    /**
     * Sets up the {@code hpkeContextNativeRef} as the recipient/gateway/server context and returns
     * whether the setup was successful.
     *
     * <p>https://www.ietf.org/archive/id/draft-ietf-ohai-ohttp-03.html#section-4.1-10
     */
    public boolean hpkeSetupRecipient(
            HpkeContextNativeRef hpkeContextNativeRef,
            KemNativeRef kemNativeRef,
            KdfNativeRef kdfNativeRef,
            AeadNativeRef aeadNativeRef,
            OhttpGatewayPrivateKey privateKey,
            EncapsulatedSharedSecret encapsulatedSharedSecret,
            RecipientKeyInfo recipientKeyInfo) {

        return hpkeSetupRecipient(
                hpkeContextNativeRef.getAddress(),
                kemNativeRef.getAddress(),
                kdfNativeRef.getAddress(),
                aeadNativeRef.getAddress(),
                privateKey.getBytes(),
                encapsulatedSharedSecret.getBytes(),
                recipientKeyInfo.getBytes());
    }

    /**
     * Computes and returns a HKDF PseudoRandomKey (as specified by RFC 5869) from initial keying
     * material {@code secret} and {@code salt} using a digest method referenced by {@code
     * hkdfMessageDigestNativeRef}
     */
    public HkdfExtractResponse hkdfExtract(
            HkdfMessageDigestNativeRef hkdfMessageDigestNativeRef, byte[] secret, byte[] salt) {
        Objects.requireNonNull(hkdfMessageDigestNativeRef);
        byte[] extractResponse = hkdfExtract(hkdfMessageDigestNativeRef.getAddress(), secret, salt);
        return HkdfExtractResponse.create(extractResponse);
    }

    /**
     * Uses the HPKE context to export a secret of length {@code secretLength}. This function uses
     * contextual information in the form of a {@code contextString} for the secret. This is
     * necessary to separate different uses of exported secrets and bind relevant caller-specific
     * context into the output.
     */
    public HpkeExportResponse hpkeExport(
            HpkeContextNativeRef hpkeContext, byte[] contextString, int secretLength) {
        Objects.requireNonNull(hpkeContext);
        byte[] exportResponse = hpkeExport(hpkeContext.getAddress(), contextString, secretLength);
        return HpkeExportResponse.create(exportResponse);
    }

    /**
     * Computes and returns a HKDF Output Keying Material (as specified by RFC 5869) of length
     * {@code keyLength} from the PseudoRandomKey ({@code prkArray}) and {@code info} using a digest
     * referenced by {@code hkdfMessageDigestNativeRef}
     */
    public HkdfExpandResponse hkdfExpand(
            HkdfMessageDigestNativeRef hkdfMessageDigestNativeRef,
            byte[] prkArray,
            byte[] infoArray,
            int keyLength) {
        Objects.requireNonNull(hkdfMessageDigestNativeRef);
        byte[] expandResponse =
                hkdfExpand(hkdfMessageDigestNativeRef.getAddress(), prkArray, infoArray, keyLength);
        return HkdfExpandResponse.create(expandResponse);
    }

    /**
     * Decrypts the {@code cipherTextArray} using the symmetric key {@code keyArray} and the
     * single-use {@code nonceArray} that were used at the time of encryption.
     */
    public byte[] aeadOpen(
            AeadNativeRef aeadNativeRef,
            byte[] keyArray,
            byte[] nonceArray,
            byte[] cipherTextArray) {
        Objects.requireNonNull(aeadNativeRef);
        return aeadOpen(aeadNativeRef.getAddress(), keyArray, nonceArray, cipherTextArray);
    }

    /**
     * Encrypts the {@code cipherTextArray} using the symmetric key {@code keyArray} and the
     * single-use {@code nonceArray}
     */
    public byte[] aeadSeal(
            AeadNativeRef aeadNativeRef,
            byte[] keyArray,
            byte[] nonceArray,
            byte[] plainTextArray) {
        Objects.requireNonNull(aeadNativeRef);
        return aeadSeal(aeadNativeRef.getAddress(), keyArray, nonceArray, plainTextArray);
    }

    /**
     * Calls the boringSSL method {@code EVP_HPKE_CTX_seal} to encrypt the {@code plainText} using
     * optional {@code aad}
     *
     * <p>This method must be preceded by {@link #hpkeCtxSetupSenderWithSeed(HpkeContextNativeRef,
     * KemNativeRef, KdfNativeRef, AeadNativeRef, byte[], byte[], byte[])} or it will lead to a
     * SIGSEGV fault. Hence, we do not provide a public interface for this method.
     *
     * @return encrypted ciphertext
     */
    @Nullable
    private byte[] hpkeCtxSeal(
            HpkeContextNativeRef hpkeContextNativeRef,
            @Nullable byte[] plaintText,
            @Nullable byte[] aad) {
        Objects.requireNonNull(hpkeContextNativeRef);
        return hpkeCtxSeal(hpkeContextNativeRef.getAddress(), plaintText, aad);
    }

    @Nullable
    private native byte[] hpkeCtxSetupSenderWithSeed(
            long ctx,
            long kemNativeRef,
            long kdfNativeRef,
            long aeadNativeRef,
            byte[] publicKey,
            @Nullable byte[] info,
            byte[] seed);

    @Nullable
    private native byte[] gatewayDecrypt(
            long hpkeCtxRef,
            long kemNativeRef,
            long kdfNativeRef,
            long aeadNativeRef,
            byte[] encryptedData);

    private native boolean hpkeSetupRecipient(
            long hpkeCtxRef,
            long kemNativeRef,
            long kdfNativeRef,
            long aeadNativeRef,
            byte[] privateKey,
            byte[] enc,
            byte[] info);

    private native byte[] hpkeCtxSeal(long ctx, @Nullable byte[] plaintText, @Nullable byte[] aad);

    private native byte[] aeadOpen(
            long aeadNativeRef, byte[] keyArray, byte[] nonceArray, byte[] cipherTextArray);

    private native byte[] aeadSeal(
            long aeadNativeRef, byte[] keyArray, byte[] nonceArray, byte[] plainTextArray);

    private native byte[] hkdfExpand(
            long hkdfMessageDigestNativeRef, byte[] prkArray, byte[] infoArray, int keyLength);

    private native byte[] hkdfExtract(long hkdfMessageDigestNativeRef, byte[] secret, byte[] salt);

    private native byte[] hpkeExport(long hpkeContext, byte[] contextString, int secretLength);
}
