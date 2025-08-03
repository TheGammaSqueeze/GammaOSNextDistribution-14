/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.shared.proto;

import android.util.Base64;

import com.android.adservices.common.AdServicesMockitoTestCase;

import com.google.protobuf.MessageLite;

import org.junit.Test;

public final class ProtoParserTest extends AdServicesMockitoTestCase {
    private static final ErrorCodeSampleInterval SAMPLE_RATE =
            ErrorCodeSampleInterval.newBuilder()
                    .putSampleIntervalToErrorCodes(
                            10, ErrorCodeList.newBuilder().addErrorCode(1).addErrorCode(2).build())
                    .build();

    private static final String PROPERTY_NAME = "Property";

    @Test
    public void parseBase64EncodedStringToProto_success() {
        String encodedStr = getBase64EncodedString(SAMPLE_RATE, Base64.DEFAULT);

        ErrorCodeSampleInterval actual =
                ProtoParser.parseBase64EncodedStringToProto(
                        ErrorCodeSampleInterval.parser(), PROPERTY_NAME, encodedStr);

        expect.that(actual).isNotNull();
        expect.that(actual).isEqualTo(SAMPLE_RATE);
    }

    @Test
    public void parseBase64EncodedStringToProto_noWrapNoPadding_success() {
        String encodedStr = getBase64EncodedString(SAMPLE_RATE, Base64.NO_WRAP | Base64.NO_PADDING);

        ErrorCodeSampleInterval actual =
                ProtoParser.parseBase64EncodedStringToProto(
                        ErrorCodeSampleInterval.parser(), PROPERTY_NAME, encodedStr);

        expect.that(actual).isNotNull();
        expect.that(actual).isEqualTo(SAMPLE_RATE);
    }

    @Test
    public void parseBase64EncodedStringToProto_incorrectEncodedString() {
        ErrorCodeSampleInterval actual =
                ProtoParser.parseBase64EncodedStringToProto(
                        ErrorCodeSampleInterval.parser(), PROPERTY_NAME, /*value= */ "xyz");

        expect.that(actual).isNull();
    }

    @Test
    public void parseBase64EncodedStringToProto_emptyEncodedString() {
        ErrorCodeSampleInterval actual =
                ProtoParser.parseBase64EncodedStringToProto(
                        ErrorCodeSampleInterval.parser(), PROPERTY_NAME, /*value= */ "");

        expect.that(actual).isNull();
    }

    @Test
    public void parseBase64EncodedStringToProto_nullEncodedString() {
        ErrorCodeSampleInterval actual =
                ProtoParser.parseBase64EncodedStringToProto(
                        ErrorCodeSampleInterval.parser(), PROPERTY_NAME, /*value= */ null);

        expect.that(actual).isNull();
    }

    // Converts proto to a Base64 encoded string.
    private static <T extends MessageLite> String getBase64EncodedString(T value, int flag) {
        return Base64.encodeToString(value.toByteArray(), flag);
    }
}
