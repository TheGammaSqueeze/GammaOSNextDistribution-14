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

import java.util.Objects;

/**
 * Represents a list of optional {@link InformativeResponse}s and a final response control data.
 *
 * <p>If the response control data includes an informational status code (that is, a value between
 * 100 and 199 inclusive), the control data is followed by a header section (encoded with known- or
 * indeterminate- length according to the framing indicator) and another block of control data .
 * This pattern repeats until the control data contains a final status code (200 to 599 inclusive).
 *
 * <p>The control data for a response message consists of the status code. The status code is
 * encoded as a variable length integer, not a length-prefixed decimal string.
 *
 * @see <a
 *     href="https://www.ietf.org/archive/id/draft-ietf-httpbis-binary-message-06.html#name-response-control-data">Binary
 *     HTTP Response Control Data</a>
 */
@AutoValue
public abstract class ResponseControlData extends ControlData {
    private static final int FINAL_STATUS_CODE_SECTION_COUNT = 1;

    /** Returns the final status code of the response. */
    public abstract int getFinalStatusCode();

    /** Returns the informative responses of the response. */
    @NonNull
    public abstract ImmutableList<InformativeResponse> getInformativeResponses();

    /**
     * {@inheritDoc}
     *
     * @return [[informative status code][header fields sections]*n]*n[final status code]
     * @see Fields#knownLengthSerialize()
     */
    @Override
    @NonNull
    byte[][] knownLengthSerialize() {
        int totalLength =
                FINAL_STATUS_CODE_SECTION_COUNT
                        + getInformativeResponses().stream()
                                .mapToInt(
                                        InformativeResponse::getKnownLengthSerializedSectionsCount)
                                .sum();
        byte[][] result = new byte[totalLength][];
        int pos = 0;

        for (InformativeResponse informativeResponse : getInformativeResponses()) {
            byte[][] subSections = informativeResponse.knownLengthSerialize();
            System.arraycopy(subSections, 0, result, pos, subSections.length);
            pos += subSections.length;
        }

        result[pos] = toFrc9000Int(getFinalStatusCode());

        return result;
    }

    @Override
    int getKnownLengthSerializedSectionsCount() {
        return getInformativeResponses().stream()
                        .mapToInt(InformativeResponse::getKnownLengthSerializedSectionsCount)
                        .sum()
                + FINAL_STATUS_CODE_SECTION_COUNT;
    }

    /** Get a builder for response control data. */
    @NonNull
    public static Builder builder() {
        return new AutoValue_ResponseControlData.Builder();
    }

    /** Builder for {@link ResponseControlData}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the final status code of the response. */
        public abstract Builder setFinalStatusCode(int value);

        @NonNull
        abstract ImmutableList.Builder<InformativeResponse> informativeResponsesBuilder();

        /** Append an informative response to the response. */
        public Builder addInformativeResponse(
                @NonNull final InformativeResponse informativeResponse) {
            informativeResponsesBuilder().add(informativeResponse);
            return this;
        }

        abstract ResponseControlData autoBuild();
        /** Returns the response control data built. */
        public ResponseControlData build() {
            ResponseControlData responseControlData = autoBuild();
            Objects.requireNonNull(responseControlData.getInformativeResponses());
            HttpStatusCodeUtil.checkIsFinalStatusCode(responseControlData.getFinalStatusCode());
            return responseControlData;
        }
    }
}
