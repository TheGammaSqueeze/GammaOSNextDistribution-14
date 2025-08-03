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

package com.android.adservices.data.topics;

import android.annotation.NonNull;

import com.android.internal.annotations.Immutable;

import com.google.auto.value.AutoValue;

import java.util.Arrays;
import java.util.Objects;

/**
 * POJO Represents an Encrypted Topic.
 *
 * @hide
 */
@Immutable
@AutoValue
public abstract class EncryptedTopic {

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[] {};
    private static final String EMPTY_STRING = "";
    /**
     * @return byte array containing encrypted Topic object.
     */
    @SuppressWarnings("mutable")
    public abstract byte[] getEncryptedTopic();

    /**
     * @return key used to identify the public encryption key used.
     */
    public abstract String getKeyIdentifier();

    /**
     * @return encapsulated key generated during HPKE setup.
     */
    @SuppressWarnings("mutable")
    public abstract byte[] getEncapsulatedKey();

    public static EncryptedTopic getDefaultInstance() {
        return create(EMPTY_BYTE_ARRAY, EMPTY_STRING, EMPTY_BYTE_ARRAY);
    }

    /**
     * @return generic builder
     */
    @NonNull
    public static EncryptedTopic.Builder builder() {
        return new AutoValue_EncryptedTopic.Builder();
    }

    /**
     * Creates an instance of {@link EncryptedTopic}.
     *
     * @param encryptedTopic byte array containing encrypted {@link Topic}.
     * @param keyIdentifier key used to identify the public encryption key used.
     * @param encapsulatedKey encapsulated key generated during HPKE setup.
     * @return Corresponding encrypted topic object.
     */
    @NonNull
    public static EncryptedTopic create(
            byte[] encryptedTopic, String keyIdentifier, byte[] encapsulatedKey) {
        Objects.requireNonNull(encryptedTopic);
        Objects.requireNonNull(keyIdentifier);
        Objects.requireNonNull(encapsulatedKey);

        return builder()
                .setEncryptedTopic(encryptedTopic)
                .setKeyIdentifier(keyIdentifier)
                .setEncapsulatedKey(encapsulatedKey)
                .build();
    }

    /** Builder for {@link EncryptedTopic}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Set Encrypted Topic */
        public abstract EncryptedTopic.Builder setEncryptedTopic(byte[] encryptedTopic);

        /** Set Key Identifier */
        public abstract EncryptedTopic.Builder setKeyIdentifier(String keyIdentifier);

        /** Set Encapsulated Key */
        public abstract EncryptedTopic.Builder setEncapsulatedKey(byte[] encapsulatedKey);

        /** Build a EncryptedTopic instance */
        @NonNull
        public abstract EncryptedTopic build();
    }

    @Override
    public final boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof EncryptedTopic)) return false;
        EncryptedTopic encryptedTopic = (EncryptedTopic) object;
        return Arrays.equals(getEncryptedTopic(), encryptedTopic.getEncryptedTopic())
                && getKeyIdentifier().equals(encryptedTopic.getKeyIdentifier())
                && Arrays.equals(getEncapsulatedKey(), encryptedTopic.getEncapsulatedKey());
    }

    @Override
    public final int hashCode() {
        return Objects.hash(
                Arrays.hashCode(getEncryptedTopic()),
                getKeyIdentifier(),
                Arrays.hashCode(getEncapsulatedKey()));
    }

    @Override
    public final String toString() {
        return "EncryptedTopic{"
                + "encryptedTopic="
                + Arrays.toString(getEncryptedTopic())
                + ", keyIdentifier="
                + getKeyIdentifier()
                + ", encapsulatedKey="
                + Arrays.toString(getEncapsulatedKey())
                + '}';
    }
}
