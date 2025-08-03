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

/** Interface for {@link OhttpJniWrapper} to allow mocking of native methods. */
interface IOhttpJniWrapper {

    void hpkeCtxFree(long ctx);

    /** Returns the reference to the KEM algorithm DHKEM(X25519, HKDF-SHA256) */
    long hpkeKemDhkemX25519HkdfSha256();

    /** Returns a reference to the KDF algorithm HKDF-SHA256 */
    long hpkeKdfHkdfSha256();

    /** Returns a reference to the AEAD algorithm AES-256-GCM */
    long hpkeAeadAes256Gcm();

    /** Returns a reference to the HKDF_SHA256 message digest (i.e., SHA256) */
    long hkdfSha256MessageDigest();

    /**
     * Calls the boringSSL EVP_HPKE_CTX_setup_sender_with_seed method which implements the
     * SetupBaseS HPKE operation.
     *
     * <p>It encapsulates and returns the sharedSecret for publicKey and sets up ctx as sender
     * context.
     *
     * @param ctx The EVP_HPKE_CTX context containing reference of the heap allocated HPKE context
     *     created using hpkeCtxNew()
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
    EncapsulatedSharedSecret hpkeCtxSetupSenderWithSeed(
            HpkeContextNativeRef ctx,
            KemNativeRef kemNativeRef,
            KdfNativeRef kdfNativeRef,
            AeadNativeRef aeadNativeRef,
            byte[] publicKey,
            @Nullable byte[] info,
            byte[] seed);

    /**
     * Calls HPKE setupSender and Seal operations. These two operations combined make up HPKE
     * encryption.
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
     *     shared secret that would later be useful for decrypting the response from the server.
     */
    HpkeEncryptResponse hpkeEncrypt(
            HpkeContextNativeRef hpkeContextNativeRef,
            KemNativeRef kemNativeRef,
            KdfNativeRef kdfNativeRef,
            AeadNativeRef aeadNativeRef,
            byte[] publicKey,
            @Nullable byte[] info,
            byte[] seed,
            @Nullable byte[] plainText,
            @Nullable byte[] aad);

    /**
     * Returns reference to a newly-allocated EVP_HPKE_CTX BoringSSL object. Object thus created
     * must be freed by calling {@link #hpkeCtxFree(long)}
     */
    long hpkeCtxNew();

    /**
     * Uses the HPKE context to export a secret of length {@code secretLength}. This function uses
     * contextual information in the form of a {@code contextString} for the secret. This is
     * necessary to separate different uses of exported secrets and bind relevant caller-specific
     * context into the output.
     */
    HpkeExportResponse hpkeExport(
            HpkeContextNativeRef hpkeContext, byte[] contextString, int secretLength);

    /**
     * Computes and returns a HKDF PseudoRandomKey (as specified by RFC 5869) from initial keying
     * material {@code secret} and {@code salt} using a digest method referenced by {@code
     * hkdfMessageDigestNativeRef}
     */
    HkdfExtractResponse hkdfExtract(
            HkdfMessageDigestNativeRef hkdfMessageDigestNativeRef, byte[] secret, byte[] salt);

    /**
     * Computes and returns a HKDF Output Keying Material (as specified by RFC 5869) of length
     * {@code keyLength} from the PseudoRandomKey ({@code prkArray}) and {@code info} using a digest
     * referenced by {@code hkdfMessageDigestNativeRef}
     */
    HkdfExpandResponse hkdfExpand(
            HkdfMessageDigestNativeRef hkdfMessageDigestNativeRef,
            byte[] prkArray,
            byte[] infoArray,
            int keyLength);

    /**
     * Decrypts the {@code cipherTextArray} using the symmetric key {@code keyArray} and the
     * single-use {@code nonceArray} that were used at the time of encryption.
     */
    byte[] aeadOpen(
            AeadNativeRef aeadNativeRef,
            byte[] keyArray,
            byte[] nonceArray,
            byte[] cipherTextArray);
}
