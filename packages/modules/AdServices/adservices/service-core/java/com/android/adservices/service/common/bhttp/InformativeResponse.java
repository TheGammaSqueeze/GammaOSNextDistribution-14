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

/**
 * Responses that include informational status codes (see Section 15.2 of [HTTP]) are encoded by
 * repeating the response control data and associated header section until a final response control
 * data is encoded. The status code distinguishes between informational and final responses.
 *
 * <p>If the response control data includes an informational status code (that is, a value between
 * 100 and 199 inclusive), the control data is followed by a header section (encoded with known- or
 * indeterminate- length according to the framing indicator) and another block of control data. This
 * pattern repeats until the control data contains a final status code (200 to 599 inclusive).
 *
 * @see <a
 *     href="https://www.ietf.org/archive/id/draft-ietf-httpbis-binary-message-06.html#name-informational-status-codes">Informational
 *     Status Codes</a>}
 */
@AutoValue
public abstract class InformativeResponse extends BinaryHttpSerializableComponent {
    private static final int STATUS_CODE_SECTION_COUNT = 1;

    /** Returns the informative status code. */
    public abstract int getInformativeStatusCode();

    /** Returns the header fields of the informative response. */
    @NonNull
    public abstract Fields getHeaderFields();

    /**
     * {@inheritDoc}
     *
     * @return [informative status code][header fields sections]*n
     */
    @NonNull
    @Override
    byte[][] knownLengthSerialize() {
        byte[][] result = new byte[getKnownLengthSerializedSectionsCount()][];
        result[0] = toFrc9000Int(getInformativeStatusCode());
        byte[][] header = getHeaderFields().knownLengthSerialize();
        System.arraycopy(header, 0, result, STATUS_CODE_SECTION_COUNT, header.length);
        return result;
    }

    @Override
    int getKnownLengthSerializedSectionsCount() {
        return STATUS_CODE_SECTION_COUNT
                + getHeaderFields().getKnownLengthSerializedSectionsCount();
    }

    /** Get a builder for informative response. */
    @NonNull
    public static Builder builder() {
        return new AutoValue_InformativeResponse.Builder();
    }

    /** Builder for {@link InformativeResponse}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the informative status code. */
        @NonNull
        public abstract Builder setInformativeStatusCode(int value);

        @NonNull
        abstract Fields.Builder headerFieldsBuilder();

        /** Append the header fields of the informative response. */
        @NonNull
        public Builder appendHeaderField(@NonNull final String name, @NonNull final String value) {
            headerFieldsBuilder().appendField(name, value);
            return this;
        }

        /**
         * This should only be used in {@link
         * BinaryHttpMessageDeserializer#deserializeKnownLengthResponseControlData
         * (BinaryHttpMessageDeserializer.BinaryHttpByteArrayReader)}.
         *
         * <p>Calling this method after {@link #appendHeaderField(String, String)} will result in
         * unchecked exception.
         */
        @NonNull
        Builder setHeaderFields(@NonNull final Fields fields) {
            headerFieldsBuilder().setFields(fields.getFields());
            return this;
        }

        @NonNull
        abstract InformativeResponse autoBuild();

        /** Returns the informative response built. */
        @NonNull
        public InformativeResponse build() {
            InformativeResponse informativeResponse = autoBuild();
            HttpStatusCodeUtil.checkIsInformativeStatusCode(
                    informativeResponse.getInformativeStatusCode());
            return informativeResponse;
        }
    }
}
