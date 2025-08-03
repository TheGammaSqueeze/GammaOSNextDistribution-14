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
import com.google.common.collect.ImmutableList;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a header or trailer section of a binary http message.
 *
 * @see <a
 *     href="https://www.ietf.org/archive/id/draft-ietf-httpbis-binary-message-06.html#name-header-and-trailer-field-li">Binary
 *     HTTP Header And Trailer Field</a>
 */
@AutoValue
public abstract class Fields extends BinaryHttpSerializableComponent {
    private static final int TOTAL_LENGTH_SECTION_COUNT = 1;
    public static final Fields EMPTY_FIELDS = builder().build();

    /** Constructs a Fields object with provided map. DO NOT USE if you care about field order. */
    @NonNull
    public static Fields copyFromMap(@NonNull final Map<String, String> fields) {
        Objects.requireNonNull(fields);
        Builder builder = builder();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            builder.appendField(field.getKey(), field.getValue());
        }
        return builder.build();
    }

    /** Returns the Fields. */
    @NonNull
    public abstract ImmutableList<Field> getFields();

    /**
     * {@inheritDoc}
     *
     * @return [total field length][[name length][name][value length][value]]*n
     */
    @Override
    @NonNull
    byte[][] knownLengthSerialize() {
        byte[][] sections = new byte[getKnownLengthSerializedSectionsCount()][];
        int i = TOTAL_LENGTH_SECTION_COUNT;
        for (Field field : getFields()) {
            System.arraycopy(
                    field.knownLengthSerialize(),
                    0,
                    sections,
                    i,
                    Field.KNOWN_LENGTH_SERIALIZED_ARRAY_LENGTH);
            i += Field.KNOWN_LENGTH_SERIALIZED_ARRAY_LENGTH;
        }
        int totalLength =
                Arrays.stream(sections)
                        .filter(Objects::nonNull)
                        .mapToInt(section -> section.length)
                        .sum();
        byte[] length = toFrc9000Int(totalLength);
        sections[0] = length;
        return sections;
    }

    @Override
    int getKnownLengthSerializedSectionsCount() {
        return TOTAL_LENGTH_SECTION_COUNT
                + getFields().size() * Field.KNOWN_LENGTH_SERIALIZED_ARRAY_LENGTH;
    }

    /** Get a builder for fields. */
    @NonNull
    public static Builder builder() {
        return new AutoValue_Fields.Builder();
    }

    /** Builder for {@link Fields}. */
    @AutoValue.Builder
    public abstract static class Builder {
        abstract ImmutableList.Builder<Field> fieldsBuilder();

        /** Append a field to the header or trailer. */
        @NonNull
        public Builder appendField(@NonNull final String name, @NonNull final String value) {
            fieldsBuilder().add(Field.create(name, value));
            return this;
        }

        /**
         * This should only be used in {@link InformativeResponse.Builder#setHeaderFields(Fields)}.
         *
         * <p>Calling this method after {@link #appendField(String, String)} will result in
         * unchecked exception.
         */
        @NonNull
        abstract Builder setFields(@NonNull ImmutableList<Field> fields);

        /** Returns the fields built. */
        @NonNull
        public abstract Fields build();
    }
}
