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

import com.android.adservices.ohttp.algorithms.KemAlgorithmSpec;
import com.android.adservices.ohttp.algorithms.UnsupportedHpkeAlgorithmException;
import com.android.internal.util.Preconditions;

import com.google.auto.value.AutoValue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

/** Contains the key configuration required by Oblivious Http Client */
@AutoValue
public abstract class ObliviousHttpKeyConfig {

    private static int sKeyIdSizeInBytes = 1;
    private static int sKemIdSizeInBytes = 2;
    private static int sKdfIdSizeInBytes = 2;
    private static int sAeadIdSizeInBytes = 2;
    private static int sSymmetricAlgorithmsLengthInBytes = 2;
    private static String sOhttpReqLabel = "message/bhttp request";

    /** Returns the Key Identifier that tells the server which public key we are using */
    public abstract int keyId();

    /**
     * Returns the Key Encapsulation Mechanism algorithm Identifier
     *
     * <p>https://www.rfc-editor.org/rfc/rfc9180#name-key-encapsulation-mechanism
     */
    public abstract int kemId();

    /**
     * Returns the KDF Id from the first Symmetric Algorithm specified in the keyConfig
     *
     * <p>https://www.rfc-editor.org/rfc/rfc9180#name-key-derivation-functions-kd
     */
    public abstract int kdfId();

    /**
     * Returns the AEAD ID from the first Symmetric Algorithm specified in the keyConfig
     *
     * <p>https://www.rfc-editor.org/rfc/rfc9180#name-authenticated-encryption-wi
     */
    public abstract int aeadId();

    /** Returns the public key in byte array format */
    @SuppressWarnings("mutable")
    abstract byte[] publicKey();

    /**
     * Parses the keyConfig into its respective components
     *
     * <p>If there are multiple symmetric key algorithms defined in the keyConfig string, we only
     * parse the first one.
     *
     * <p>https://www.ietf.org/archive/id/draft-ietf-ohai-ohttp-03.html#section-3.1-2
     *
     * <pre>
     * HPKE Symmetric Algorithms {
     *   HPKE KDF ID (16),
     *   HPKE AEAD ID (16),
     * }
     *
     * OHTTP Key Config {
     *   Key Identifier (8),
     *   HPKE KEM ID (16),
     *   HPKE Public Key (Npk * 8),
     *   HPKE Symmetric Algorithms Length (16),
     *   HPKE Symmetric Algorithms (32..262140),
     * }
     * </pre>
     *
     * @param keyConfig The keyconfig to be parsed
     * @throws InvalidKeySpecException if the key does not abide by the spec laid out in Oblivious
     *     HTTP Draft.
     */
    public static ObliviousHttpKeyConfig fromSerializedKeyConfig(byte[] keyConfig)
            throws InvalidKeySpecException {

        int currentIndex = 0;

        // read KeyId
        int keyId = writeUptoTwoBytesIntoInteger(keyConfig, currentIndex, sKeyIdSizeInBytes);
        currentIndex += sKeyIdSizeInBytes;

        int kemId = writeUptoTwoBytesIntoInteger(keyConfig, currentIndex, sKemIdSizeInBytes);
        currentIndex += sKemIdSizeInBytes;

        int publicKeyLength = getPublicKeyLengthInBytes(kemId);
        byte[] publicKey =
                Arrays.copyOfRange(keyConfig, currentIndex, currentIndex + publicKeyLength);
        currentIndex += publicKeyLength;

        int algorithmsLength =
                writeUptoTwoBytesIntoInteger(
                        keyConfig, currentIndex, sSymmetricAlgorithmsLengthInBytes);
        currentIndex += sSymmetricAlgorithmsLengthInBytes;

        if (currentIndex + algorithmsLength != keyConfig.length) {
            throw new InvalidKeySpecException("Invalid length of symmetric algorithms");
        }

        // We choose the first set of algorithms from the list

        int kdfId = writeUptoTwoBytesIntoInteger(keyConfig, currentIndex, sKdfIdSizeInBytes);
        currentIndex += sKdfIdSizeInBytes;

        int aeadId = writeUptoTwoBytesIntoInteger(keyConfig, currentIndex, sAeadIdSizeInBytes);

        return builder()
                .setKeyId(keyId)
                .setKemId(kemId)
                .setKdfId(kdfId)
                .setAeadId(aeadId)
                .setPublicKey(publicKey)
                .build();
    }

    /**
     * Concatenates keyId, kemId, kdfId and aeadId according to OHTTP Spec.
     *
     * <p>https://www.ietf.org/archive/id/draft-ietf-ohai-ohttp-03.html#name-hpke-encapsulation
     *
     * <pre>hdr = concat(encode(1, keyID),
     *            encode(2, kemID),
     *            encode(2, kdfID),
     *            encode(2, aeadID)) </pre>
     */
    public byte[] serializeOhttpPayloadHeader() {
        byte[] header = new byte[7];

        // read one byte from integer keyId
        header[0] = (byte) (keyId() & 0xFF);

        copyTwoBytesFromInteger(kemId(), header, 1);

        copyTwoBytesFromInteger(kdfId(), header, 3);

        copyTwoBytesFromInteger(aeadId(), header, 5);

        return header;
    }

    /**
     * Generates the 'info' field as required by HPKE setupBaseS operation according to OHTTP spec
     *
     * <p>https://www.ietf.org/archive/id/draft-ietf-ohai-ohttp-03.html#name-hpke-encapsulation
     *
     * <pre>info = concat(encode_str("message/bhttp request"),
     *             encode(1, 0),
     *             hdr)</pre>
     */
    public RecipientKeyInfo createRecipientKeyInfo() throws IOException {

        byte[] ohttpReqLabelBytes = sOhttpReqLabel.getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(ohttpReqLabelBytes);
        outputStream.write((byte) 0);
        outputStream.write(serializeOhttpPayloadHeader());

        return RecipientKeyInfo.create(outputStream.toByteArray());
    }

    /** Get a copy of the public key array */
    public byte[] getPublicKey() {
        return Arrays.copyOf(publicKey(), publicKey().length);
    }

    /**
     * Serialize the current keyConfig object into a byte array
     *
     * <p>This method is not a strict inverse of {@link #fromSerializedKeyConfig(byte[])}. While
     * {@link #fromSerializedKeyConfig(byte[])} might receive a byte array with multiple AEAD and
     * KDF algorithms, we eventually only store the first set of algorithms in
     * ObliviousHttpKeyConfig, and thus we can only serialize the first set.
     *
     * <p>https://www.ietf.org/archive/id/draft-ietf-ohai-ohttp-03.html#section-3.1-2
     *
     * <pre>
     * HPKE Symmetric Algorithms {
     *   HPKE KDF ID (16),
     *   HPKE AEAD ID (16),
     * }
     *
     * OHTTP Key Config {
     *   Key Identifier (8),
     *   HPKE KEM ID (16),
     *   HPKE Public Key (Npk * 8),
     *   HPKE Symmetric Algorithms Length (16),
     *   HPKE Symmetric Algorithms (32..262140),
     * }
     * </pre>
     */
    public byte[] serializeKeyConfigToBytes() {
        int byteArraySize = getSerializedByteArraySize();
        byte[] serializedArray = new byte[byteArraySize];
        int currentIndex = 0;

        // read one byte from integer keyId
        serializedArray[currentIndex++] = (byte) (keyId() & 0xFF);

        copyTwoBytesFromInteger(kemId(), serializedArray, currentIndex);
        currentIndex += 2;

        // Copy the public key bytes
        System.arraycopy(publicKey(), 0, serializedArray, currentIndex, publicKey().length);
        currentIndex += publicKey().length;

        // Since we only store one set of algorithms, the algorithm length is the sum of KDF ID
        // and AEAD id.
        int algorithmsLength = sKdfIdSizeInBytes + sAeadIdSizeInBytes;
        copyTwoBytesFromInteger(algorithmsLength, serializedArray, currentIndex);
        currentIndex += 2;

        copyTwoBytesFromInteger(kdfId(), serializedArray, currentIndex);
        currentIndex += 2;

        copyTwoBytesFromInteger(aeadId(), serializedArray, currentIndex);

        return serializedArray;
    }

    /** Builder for ObliviousHttpKeyConfig. */
    public static ObliviousHttpKeyConfig.Builder builder() {
        return new AutoValue_ObliviousHttpKeyConfig.Builder();
    }

    /** Builder for {@link com.android.adservices.ohttp.ObliviousHttpKeyConfig}. */
    @AutoValue.Builder
    public abstract static class Builder {

        /** Sets key id. */
        public abstract Builder setKeyId(int keyId);

        /** Sets KEM id. */
        public abstract Builder setKemId(int kemId);

        /** Sets KDF id. */
        public abstract Builder setKdfId(int kdfId);

        /** Sets AEAD id. */
        public abstract Builder setAeadId(int aeadId);

        /** Sets public key. */
        public abstract Builder setPublicKey(byte[] publicKey);

        /** Builds the ObliviousHttpKeyConfig object. */
        public abstract ObliviousHttpKeyConfig build();
    }

    private static int writeUptoTwoBytesIntoInteger(
            byte[] keyConfig, int startingIndex, int numberOfBytes) throws InvalidKeySpecException {
        Preconditions.checkArgument(
                numberOfBytes == 1 || numberOfBytes == 2, "tried to write more than two bytes");

        if (startingIndex + numberOfBytes > keyConfig.length) {
            throw new InvalidKeySpecException("Invalid length of the keyConfig");
        }

        if (numberOfBytes == 1) {
            return writeOneByteToInt(keyConfig, startingIndex);
        }

        return writeTwoBytesToInt(keyConfig, startingIndex);
    }

    private static int writeOneByteToInt(byte[] input, int startingIndex) {
        return input[startingIndex];
    }

    private static int writeTwoBytesToInt(byte[] input, int startingIndex) {
        return ((input[startingIndex] & 0xff) << 8) | (input[startingIndex + 1] & 0xff);
    }

    private static void copyTwoBytesFromInteger(
            int sourceInteger, byte[] targetArray, int targetArrayStartPosition) {
        targetArray[targetArrayStartPosition] = (byte) ((sourceInteger >> 8) & 0xFF);
        targetArray[targetArrayStartPosition + 1] = (byte) (sourceInteger & 0xFF);
    }

    private static int getPublicKeyLengthInBytes(int kemIdentifier) throws InvalidKeySpecException {
        try {
            int length = KemAlgorithmSpec.get(kemIdentifier).publicKeyLength();
            return length;
        } catch (UnsupportedHpkeAlgorithmException e) {
            throw new InvalidKeySpecException("Unsupported Kem identifier");
        }
    }

    private int getSerializedByteArraySize() {
        return sKeyIdSizeInBytes
                + sKemIdSizeInBytes
                + publicKey().length
                + sSymmetricAlgorithmsLengthInBytes
                + sKdfIdSizeInBytes
                + sAeadIdSizeInBytes;
    }
}
