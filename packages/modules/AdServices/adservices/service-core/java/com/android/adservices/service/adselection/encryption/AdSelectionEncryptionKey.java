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

package com.android.adservices.service.adselection.encryption;

import android.annotation.IntDef;

import com.google.auto.value.AutoValue;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Represents the encryption key returned by the key fetch server and used during ad selection. */
@AutoValue
public abstract class AdSelectionEncryptionKey {
    @IntDef(
            value = {
                AdSelectionEncryptionKeyType.UNASSIGNED,
                AdSelectionEncryptionKeyType.AUCTION,
                AdSelectionEncryptionKeyType.JOIN,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AdSelectionEncryptionKeyType {
        int UNASSIGNED = 0;
        int AUCTION = 1;
        int JOIN = 2;
    }
    /** Encryption key type of this key. */
    @AdSelectionEncryptionKeyType
    public abstract int keyType();
    /** Key identifier used to uniquely identify key of this key type. */
    public abstract String keyIdentifier();

    /** The public key of the asymmetric key pair as bytes sent by the key server. */
    @SuppressWarnings("mutable")
    public abstract byte[] publicKey();

    /** Method to create a builder for this key. */
    public static Builder builder() {
        return new AutoValue_AdSelectionEncryptionKey.Builder();
    }

    /** Builder for AdSelectionEncryptionKey. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the key type for this encryption key. */
        public abstract Builder setKeyType(@AdSelectionEncryptionKeyType int keyType);

        /** Set the identifier for this encryption key. */
        public abstract Builder setKeyIdentifier(String keyIdentifier);

        /** Set the public key for this encryption key. */
        public abstract Builder setPublicKey(byte[] publicKey);

        /** Builds the key. */
        public abstract AdSelectionEncryptionKey build();
    }
}
