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

package com.android.adservices.data.adselection;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.CopyAnnotations;

import java.time.Instant;

/** Table representing EncryptionKeys. */
@AutoValue
@CopyAnnotations
@Entity(
        tableName = "encryption_key",
        indices = {@Index(value = {"encryption_key_type", "expiry_instant"})},
        primaryKeys = {"encryption_key_type", "key_identifier"})
public abstract class DBEncryptionKey {
    /** Type of Key. */
    @NonNull
    @CopyAnnotations
    @EncryptionKeyConstants.EncryptionKeyType
    @ColumnInfo(name = "encryption_key_type")
    public abstract int getEncryptionKeyType();

    /** KeyIdentifier used for versioning the keys. */
    @NonNull
    @CopyAnnotations
    @ColumnInfo(name = "key_identifier")
    public abstract String getKeyIdentifier();

    /**
     * The actual public key. Encoding and parsing of this key is dependent on the keyType and will
     * be managed by the Key Client.
     */
    @NonNull
    @CopyAnnotations
    @ColumnInfo(name = "public_key")
    public abstract String getPublicKey();

    /** Instant this EncryptionKey entry was created. */
    @CopyAnnotations
    @ColumnInfo(name = "creation_instant")
    public abstract Instant getCreationInstant();

    /**
     * Expiry TTL for this encryption key in seconds. This is sent by the server and stored on
     * device for computing expiry Instant. Clients should directly read the expiryInstant unless
     * they specifically need to know the expiry ttl seconds reported by the server.
     */
    @NonNull
    @CopyAnnotations
    @ColumnInfo(name = "expiry_ttl_seconds")
    public abstract Long getExpiryTtlSeconds();

    /**
     * Expiry Instant for this encryption key computed as
     * creationInstant.plusSeconds(expiryTtlSeconds). Clients should use this field to read the key
     * expiry value instead of computing it from creation instant and expiry ttl seconds.
     */
    @NonNull
    @CopyAnnotations
    @ColumnInfo(name = "expiry_instant")
    public abstract Instant getExpiryInstant();

    /** Returns an AutoValue builder for a {@link DBEncryptionKey} entity. */
    @NonNull
    public static DBEncryptionKey.Builder builder() {
        return new AutoValue_DBEncryptionKey.Builder();
    }

    /**
     * Creates a {@link DBEncryptionKey} object using the builder.
     *
     * <p>Required for Room SQLite integration.
     */
    @NonNull
    public static DBEncryptionKey create(
            @EncryptionKeyConstants.EncryptionKeyType int encryptionKeyType,
            String keyIdentifier,
            String publicKey,
            Instant creationInstant,
            Long expiryTtlSeconds,
            Instant expiryInstant) {

        return builder()
                .setEncryptionKeyType(encryptionKeyType)
                .setKeyIdentifier(keyIdentifier)
                .setPublicKey(publicKey)
                .setCreationInstant(creationInstant)
                .setExpiryInstant(expiryInstant)
                .setExpiryTtlSeconds(expiryTtlSeconds)
                .build();
    }

    /** Builder class for a {@link DBEncryptionKey}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets encryption key tupe. */
        public abstract Builder setEncryptionKeyType(
                @EncryptionKeyConstants.EncryptionKeyType int encryptionKeyType);

        /** Identifier used to identify the encryptionKey. */
        public abstract Builder setKeyIdentifier(String keyIdentifier);

        /** Public key of an asymmetric key pair represented by this encryptionKey. */
        public abstract Builder setPublicKey(String publicKey);

        /** Ttl in seconds for the EncryptionKey. */
        public abstract Builder setExpiryTtlSeconds(Long expiryTtlSeconds);

        /** Creation instant for the key. */
        abstract Builder setCreationInstant(Instant creationInstant);

        /** Expiry instant for the key. */
        abstract Builder setExpiryInstant(Instant expiryInstant);

        abstract Instant getCreationInstant();

        abstract Instant getExpiryInstant();

        abstract Long getExpiryTtlSeconds();

        abstract DBEncryptionKey autoBuild();

        /** Builds the key based on the set values after validating the input. */
        public final DBEncryptionKey build() {
            // TODO(b/284445328): Set creation Instant as the instant key was fetched.
            // This would allow accurate computation of expiry instant as fetchInstant + maxage.
            Instant creationInstant = Instant.now();
            setCreationInstant(creationInstant);
            setExpiryInstant(creationInstant.plusSeconds(getExpiryTtlSeconds()));
            return autoBuild();
        }
    }
}
