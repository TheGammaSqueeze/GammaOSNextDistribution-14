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

import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;

import java.util.Arrays;
import java.util.Objects;

/** Holds the private key of the Ohttp Gateway/Server */
@AutoValue
public abstract class OhttpGatewayPrivateKey {

    /** Get the bytes held by this object */
    @Nullable
    @SuppressWarnings("mutable")
    abstract byte[] getBytes();

    /** Create a {@link OhttpGatewayPrivateKey} object with the given bytes */
    public static OhttpGatewayPrivateKey create(byte[] bytes) {
        return Objects.isNull(bytes)
                ? new AutoValue_OhttpGatewayPrivateKey(null)
                : new AutoValue_OhttpGatewayPrivateKey(Arrays.copyOf(bytes, bytes.length));
    }
}
