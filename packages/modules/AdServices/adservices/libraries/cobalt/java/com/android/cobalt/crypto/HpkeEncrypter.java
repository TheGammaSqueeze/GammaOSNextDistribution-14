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

package com.android.cobalt.crypto;

import static com.android.cobalt.crypto.PublicKeys.ANALYZER_CONTEXT_INFO_BYTES;
import static com.android.cobalt.crypto.PublicKeys.ANALYZER_KEY_DEV;
import static com.android.cobalt.crypto.PublicKeys.ANALYZER_KEY_INDEX_DEV;
import static com.android.cobalt.crypto.PublicKeys.ANALYZER_KEY_INDEX_PROD;
import static com.android.cobalt.crypto.PublicKeys.ANALYZER_KEY_PROD;
import static com.android.cobalt.crypto.PublicKeys.SHUFFLER_CONTEXT_INFO_BYTES;
import static com.android.cobalt.crypto.PublicKeys.SHUFFLER_KEY_DEV;
import static com.android.cobalt.crypto.PublicKeys.SHUFFLER_KEY_INDEX_DEV;
import static com.android.cobalt.crypto.PublicKeys.SHUFFLER_KEY_INDEX_PROD;
import static com.android.cobalt.crypto.PublicKeys.SHUFFLER_KEY_PROD;
import static com.android.cobalt.crypto.PublicKeys.X25519_PUBLIC_VALUE_LEN;

import androidx.annotation.NonNull;

import com.android.cobalt.CobaltPipelineType;
import com.android.internal.annotations.VisibleForTesting;

import com.google.cobalt.EncryptedMessage;
import com.google.cobalt.Envelope;
import com.google.cobalt.Observation;
import com.google.cobalt.ObservationToEncrypt;
import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;

import java.util.Objects;
import java.util.Optional;

/** Handler for encryption of {@link Envelope} and {@link Observation} via {@link HpkeEncrypt}. */
public final class HpkeEncrypter implements Encrypter {
    private final HpkeEncrypt mEncrypter;

    @VisibleForTesting final int mShufflerKeyIndex;
    @VisibleForTesting final int mAnalyzerKeyIndex;

    private final byte[] mShufflerKey;
    private final byte[] mAnalyzerKey;

    /** Creates a HpkeEncrypter compatible with the specified Cobalt environment */
    public static HpkeEncrypter createForEnvironment(
            @NonNull HpkeEncrypt encrypter, @NonNull CobaltPipelineType type) {
        Objects.requireNonNull(type);

        switch (type) {
            case PROD:
                return new HpkeEncrypter(
                        encrypter,
                        SHUFFLER_KEY_PROD,
                        SHUFFLER_KEY_INDEX_PROD,
                        ANALYZER_KEY_PROD,
                        ANALYZER_KEY_INDEX_PROD);
            case DEV:
                return new HpkeEncrypter(
                        encrypter,
                        SHUFFLER_KEY_DEV,
                        SHUFFLER_KEY_INDEX_DEV,
                        ANALYZER_KEY_DEV,
                        ANALYZER_KEY_INDEX_DEV);
        }

        throw new IllegalArgumentException("Unknown Cobalt environment");
    }

    HpkeEncrypter(
            @NonNull HpkeEncrypt encrypter,
            @NonNull byte[] shufflerKey,
            int shufflerKeyIndex,
            @NonNull byte[] analyzerKey,
            int analyzerKeyIndex) {
        this.mEncrypter = Objects.requireNonNull(encrypter);
        this.mShufflerKey = Objects.requireNonNull(shufflerKey);
        this.mShufflerKeyIndex = shufflerKeyIndex;
        this.mAnalyzerKey = Objects.requireNonNull(analyzerKey);
        this.mAnalyzerKeyIndex = analyzerKeyIndex;
    }

    /**
     * Encrypts the provided {@link Envelope} with the key for the shuffler and wraps it into an
     * {@link EncryptedMessage}.
     *
     * @return {@link EncryptedMessage} wrapped in an Optional if the {@link Envelope} is
     *     successfully encrypted. Optional will be empty if the {@link Envelope} is empty
     * @throws EncryptionFailedException if encryption fails
     */
    @Override
    public Optional<EncryptedMessage> encryptEnvelope(@NonNull Envelope envelope)
            throws EncryptionFailedException {
        Objects.requireNonNull(envelope);

        return encrypt(
                envelope,
                mShufflerKey,
                mShufflerKeyIndex,
                SHUFFLER_CONTEXT_INFO_BYTES,
                ByteString.EMPTY);
    }

    /**
     * Extract and encrypts {@link Observation} from the provided {@link ObservationToEncrypt} with
     * the key for the analyzer and wraps it into an {@link EncryptedMessage}.
     *
     * @return {@link EncryptedMessage} wrapped in an Optional if the {@link Observation} is
     *     successfully encrypted. Optional will be empty if the {@link Observation} is empty
     * @throws EncryptionFailedException if encryption fails
     */
    @Override
    public Optional<EncryptedMessage> encryptObservation(
            @NonNull ObservationToEncrypt observationToEncrypt) throws EncryptionFailedException {
        Objects.requireNonNull(observationToEncrypt);

        return encrypt(
                observationToEncrypt.getObservation(),
                mAnalyzerKey,
                mAnalyzerKeyIndex,
                ANALYZER_CONTEXT_INFO_BYTES,
                observationToEncrypt.getContributionId());
    }

    /**
     * Encrypt the given message and wraps it into an {@link EncryptedMessage.Builder}
     *
     * @param publicKey used by the encryption algorithm, must satisfies the encryption scheme
     *     required key length
     * @param contextInfoBytes used by the encryption algorithm, intended to provide additional data
     *     keeping the message integrity. Cannot be empty
     * @param contributionId passed by the Message to encrypt, used to set contributionId in {@link
     *     EncryptedMessage}. This field should only be set when encrypting an {@link Observation}
     *     that should be counted towards the shuffler threshold. All other Messages should pass a
     *     ByteString.EMPTY
     * @return {@link EncryptedMessage} wrapped in an Optional if the {@link MessageLite} is
     *     successfully encrypted. Optional will be empty if the {@link MessageLite} is empty
     * @throws EncryptionFailedException if encryption fails
     */
    private Optional<EncryptedMessage> encrypt(
            MessageLite message,
            byte[] publicKey,
            int keyIndex,
            byte[] contextInfoBytes,
            ByteString contributionId)
            throws EncryptionFailedException {
        // Assert the public key length matches the X25519 public key requirement, and
        // contextInfoBytes.
        if (publicKey.length != X25519_PUBLIC_VALUE_LEN || contextInfoBytes.length == 0) {
            throw new AssertionError(
                    String.format(
                            "Invalid HPKE parameters. Expected public key length of %d, got %d. "
                                    + "Expected non-zero context info length, got %d",
                            X25519_PUBLIC_VALUE_LEN, publicKey.length, contextInfoBytes.length));
        }

        byte[] plainText = message.toByteArray();
        if (plainText.length == 0) {
            return Optional.empty();
        }

        byte[] encryptedMessageBytes =
                mEncrypter.encrypt(publicKey, message.toByteArray(), contextInfoBytes);
        if (encryptedMessageBytes.length == 0) {
            throw new EncryptionFailedException("Message couldn't be encrypted.");
        }

        return Optional.of(
                EncryptedMessage.newBuilder()
                        .setCiphertext(ByteString.copyFrom(encryptedMessageBytes))
                        .setKeyIndex(keyIndex)
                        .setContributionId(contributionId)
                        .build());
    }
}
