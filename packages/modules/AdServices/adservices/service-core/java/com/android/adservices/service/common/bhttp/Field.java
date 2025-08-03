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

package com.android.adservices.service.common.bhttp;

import static com.android.adservices.service.common.bhttp.Frc9000VariableLengthIntegerUtil.toFrc9000Int;

import android.annotation.NonNull;

import com.google.auto.value.AutoValue;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Represents a header or trailer field line. */
@AutoValue
public abstract class Field extends BinaryHttpSerializableComponent {
    static final int KNOWN_LENGTH_SERIALIZED_ARRAY_LENGTH = 4;

    /** Returns the name of the field. */
    @NonNull
    public abstract String getName();

    /** Returns the value of the field. */
    @NonNull
    public abstract String getValue();

    /** Creates a {@link Field} object. */
    @NonNull
    public static Field create(@NonNull final String name, @NonNull final String value) {
        return new AutoValue_Field(name.toLowerCase(Locale.ENGLISH), value);
    }

    /**
     * {@inheritDoc}
     *
     * @return [name length][name][value length][value]
     */
    @NonNull
    @Override
    byte[][] knownLengthSerialize() {
        byte[] name = getName().getBytes(StandardCharsets.UTF_8);
        byte[] value = getValue().getBytes(StandardCharsets.UTF_8);
        return new byte[][] {toFrc9000Int(name.length), name, toFrc9000Int(value.length), value};
    }

    @Override
    int getKnownLengthSerializedSectionsCount() {
        return KNOWN_LENGTH_SERIALIZED_ARRAY_LENGTH;
    }
}
