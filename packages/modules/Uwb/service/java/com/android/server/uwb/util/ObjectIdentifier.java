/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.server.uwb.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;

// TODO: encode/decode the ObjectIdentifier per X.660.
/**
 * ObjectIdentifier for ADF OID.
 */
public class ObjectIdentifier {
    public static final ObjectIdentifier INVALID_OID =
            ObjectIdentifier.fromBytes(new byte[] {(byte) 0});
    public final byte[] value;

    private ObjectIdentifier(@NonNull byte[] value) {
        this.value = value;
    }

    /**
     * Convert the byte array to ObjectIdentifier.
     */
    @Nullable
    public static ObjectIdentifier fromBytes(@NonNull byte[] bytes) {
        if (bytes.length == 0) {
            return null;
        }
        return new ObjectIdentifier(bytes);
    }

    @Override
    public boolean equals(@Nullable Object that) {
        if (this == that) {
            return true;
        }
        if (that == null || !(that instanceof ObjectIdentifier)) {
            return false;
        }

        return Arrays.equals(value, ((ObjectIdentifier) that).value);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }
}
