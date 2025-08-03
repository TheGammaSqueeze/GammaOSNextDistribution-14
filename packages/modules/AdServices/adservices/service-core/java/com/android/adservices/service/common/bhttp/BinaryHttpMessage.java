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

import com.android.internal.util.Preconditions;

import com.google.auto.value.AutoValue;

import java.util.Arrays;
import java.util.Objects;

/**
 * Binary Representation of HTTP Messages.
 *
 * @see <a href="https://www.ietf.org/archive/id/draft-ietf-httpbis-binary-message-06.html">Binary
 *     HTTP</a>
 */
@AutoValue
public abstract class BinaryHttpMessage extends BinaryHttpSerializableComponent {
    private static final int FRAMING_INDICATOR_LENGTH_IN_BYTES = 1;
    static final byte[] EMPTY_CONTENT = new byte[0];
    private static final int FRAMING_INDICATOR_SECTION_COUNT = 1;
    private static final int CONTENT_SECTION_COUNT = 2;

    /** Returns the framing indicator of the message. */
    @FramingIndicator
    public abstract byte getFramingIndicator();

    @NonNull
    abstract ControlData getControlData();

    /** Returns the header fields of the message. */
    @NonNull
    public abstract Fields getHeaderFields();

    /** Returns the content of the message. */
    @NonNull
    @SuppressWarnings("mutable")
    public abstract byte[] getContent();

    /**
     * By definition, content and trailer can be omitted without zero length indicator. The length
     * of padding does not have a strict definition, we define the length as following:
     *
     * <ol>
     *   <li>As we do not support trailer, we treat it as padding part for now.
     *   <li>If content has zero length, we calculate padding after the length field.
     *   <li>If length is omitted in the bytes, we return 0 for padding length.
     *       <ol/>
     *
     * @return the length of padding.
     */
    public abstract int getPaddingLength();

    /** Returns if the message represents a request. */
    public boolean isRequest() {
        return FramingIndicator.FRAMING_INDICATOR_REQUEST_OF_KNOWN_LENGTH == getFramingIndicator();
    }

    /** Returns if the message represents a response. */
    public boolean isResponse() {
        return FramingIndicator.FRAMING_INDICATOR_RESPONSE_OF_KNOWN_LENGTH == getFramingIndicator();
    }

    /** Returns request control data if the message is a request message. */
    @NonNull
    public RequestControlData getRequestControlData() {
        if (isRequest()) {
            return (RequestControlData) getControlData();
        }
        throw new IllegalArgumentException("Message does not represent a Request.");
    }

    /** Returns response control data if the message is a response message. */
    @NonNull
    public ResponseControlData getResponseControlData() {
        if (isResponse()) {
            return (ResponseControlData) getControlData();
        }
        throw new IllegalArgumentException("Message does not represent a Response.");
    }

    /** Serialize the message to a binary representation according to the framing indicator. */
    @NonNull
    public byte[] serialize() {
        switch (getFramingIndicator()) {
            case FramingIndicator.FRAMING_INDICATOR_REQUEST_OF_KNOWN_LENGTH:
            case FramingIndicator.FRAMING_INDICATOR_RESPONSE_OF_KNOWN_LENGTH:
                return combineWithPadding(knownLengthSerialize(), getPaddingLength());
            default:
                throw new IllegalArgumentException("Invalid framing indicator.");
        }
    }

    /** Get a builder for known length request. */
    @NonNull
    public static Builder knownLengthRequestBuilder(
            @NonNull final RequestControlData requestControlData) {
        return builder()
                .setFramingIndicator(FramingIndicator.FRAMING_INDICATOR_REQUEST_OF_KNOWN_LENGTH)
                .setControlData(requestControlData);
    }

    /** Get a builder for known length response. */
    @NonNull
    public static Builder knownLengthResponseBuilder(
            @NonNull final ResponseControlData responseControlData) {
        return builder()
                .setFramingIndicator(FramingIndicator.FRAMING_INDICATOR_RESPONSE_OF_KNOWN_LENGTH)
                .setControlData(responseControlData);
    }

    @NonNull
    static Builder builder() {
        return new AutoValue_BinaryHttpMessage.Builder()
                .setContent(EMPTY_CONTENT)
                .setPaddingLength(0);
    }

    @Override
    int getKnownLengthSerializedSectionsCount() {
        return FRAMING_INDICATOR_SECTION_COUNT
                + getControlData().getKnownLengthSerializedSectionsCount()
                + getHeaderFields().getKnownLengthSerializedSectionsCount()
                + CONTENT_SECTION_COUNT;
    }

    /**
     * {@inheritDoc}
     *
     * @return [framing indicator][control data sections]*n[header fields sections]*n[content
     *     length][content]
     * @see RequestControlData#knownLengthSerialize()
     * @see ResponseControlData#knownLengthSerialize()
     * @see Fields#knownLengthSerialize()
     */
    @Override
    byte[][] knownLengthSerialize() {
        byte[] framingIndicator = new byte[] {getFramingIndicator()};
        // For response, the informative responses are included in the control data to simplifying
        // the code.
        byte[][] controlData = getControlData().knownLengthSerialize();
        byte[][] headerFields = getHeaderFields().knownLengthSerialize();
        byte[][] content = new byte[][] {toFrc9000Int(getContent().length), getContent()};
        // We don't have trailer support for now.

        int totalSections =
                framingIndicator.length + controlData.length + headerFields.length + content.length;
        byte[][] result = new byte[totalSections][];
        result[0] = framingIndicator;
        int position = FRAMING_INDICATOR_LENGTH_IN_BYTES;
        System.arraycopy(controlData, 0, result, position, controlData.length);
        position += controlData.length;
        System.arraycopy(headerFields, 0, result, position, headerFields.length);
        position += headerFields.length;
        System.arraycopy(content, 0, result, position, content.length);
        return result;
    }

    @NonNull
    private byte[] combineWithPadding(@NonNull final byte[][] sections, final int paddingLength) {
        int pointer = 0;
        int totalSize = Arrays.stream(sections).mapToInt(s -> s.length).sum() + paddingLength;
        byte[] result = new byte[totalSize];
        for (byte[] section : sections) {
            for (byte b : section) {
                result[pointer++] = b;
            }
        }
        return result;
    }

    /** Padding length is ignored as it does not have any info. */
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BinaryHttpMessage)) return false;
        BinaryHttpMessage that = (BinaryHttpMessage) o;
        return getFramingIndicator() == that.getFramingIndicator()
                && getControlData().equals(that.getControlData())
                && getHeaderFields().equals(that.getHeaderFields())
                && Arrays.equals(getContent(), that.getContent());
    }

    @Override
    public final int hashCode() {
        return Objects.hash(
                getFramingIndicator(),
                getControlData(),
                getHeaderFields(),
                Arrays.hashCode(getContent()));
    }

    /** Builder for {@link BinaryHttpMessage}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Set framing indicator for the message. */
        @NonNull
        abstract Builder setFramingIndicator(byte framingIndicator);
        /** Set control data for the message. */
        @NonNull
        abstract Builder setControlData(@NonNull ControlData controlData);

        /** Set header fields for the message. */
        @NonNull
        public abstract Builder setHeaderFields(@NonNull Fields headerFields);

        /** Sets message content. */
        @NonNull
        public abstract Builder setContent(@NonNull byte[] content);

        /** Sets padding length of the message. */
        @NonNull
        public abstract Builder setPaddingLength(int paddingLength);

        abstract int getPaddingLength();

        @NonNull
        abstract BinaryHttpMessage autoBuild();

        /** Returns the message built. */
        @NonNull
        public BinaryHttpMessage build() {
            BinaryHttpMessage binaryHttpMessage = autoBuild();
            Preconditions.checkArgumentNonnegative(getPaddingLength());
            return binaryHttpMessage;
        }
    }
}
