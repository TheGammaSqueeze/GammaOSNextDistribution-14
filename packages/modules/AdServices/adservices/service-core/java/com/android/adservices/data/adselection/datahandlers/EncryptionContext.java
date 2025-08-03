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

package com.android.adservices.data.adselection.datahandlers;

import android.annotation.NonNull;

import com.android.adservices.ohttp.ObliviousHttpRequestContext;
import com.android.adservices.service.adselection.encryption.AdSelectionEncryptionKey;

import com.google.auto.value.AutoValue;

/**
 * Data class representing the context used for encrypting the ad selection run payload generated
 * for server auction.
 */
@AutoValue
public abstract class EncryptionContext {

    /** Encryption key type used for encryption. */
    @AdSelectionEncryptionKey.AdSelectionEncryptionKeyType
    public abstract int getAdSelectionEncryptionKeyType();

    /** ObliviousHttpRequestContext used for encryption. */
    @NonNull
    public abstract ObliviousHttpRequestContext getOhttpRequestContext();

    /**
     * @return generic builder
     */
    @NonNull
    public static Builder builder() {
        return new AutoValue_EncryptionContext.Builder();
    }

    /** Builder for EncryptionContext. */
    @AutoValue.Builder
    public abstract static class Builder {

        /** Sets the encryption key type used for encryption. */
        public abstract Builder setAdSelectionEncryptionKeyType(
                @AdSelectionEncryptionKey.AdSelectionEncryptionKeyType int encryptionKeyType);

        /** Sets the obliviousHttpRequestContext used for encryption. */
        public abstract Builder setOhttpRequestContext(
                @NonNull ObliviousHttpRequestContext requestContext);

        /** Builds a {@link EncryptionContext} object. */
        @NonNull
        public abstract EncryptionContext build();
    }
}
