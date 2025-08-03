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
package com.android.adservices.cobalt;

import static com.android.adservices.cobalt.CobaltConstants.DEFAULT_API_KEY;

import androidx.annotation.NonNull;

import com.google.common.io.BaseEncoding;
import com.google.protobuf.ByteString;

import java.util.Objects;

/** Static data and functions related to Cobalt API keys. */
public final class CobaltApiKeys {
    /** Copy an API key from a base-16 encoded hex string. */
    static ByteString copyFromHexApiKey(@NonNull String apiKey) {
        Objects.requireNonNull(apiKey);
        return apiKey.equals(DEFAULT_API_KEY)
                ? ByteString.copyFromUtf8(apiKey)
                : ByteString.copyFrom(BaseEncoding.base16().lowerCase().decode(apiKey));
    }

    private CobaltApiKeys() {
        throw new UnsupportedOperationException("Contains only static members");
    }
}
