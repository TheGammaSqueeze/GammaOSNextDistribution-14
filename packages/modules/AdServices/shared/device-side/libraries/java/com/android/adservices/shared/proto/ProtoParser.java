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

import android.annotation.Nullable;
import android.text.TextUtils;
import android.util.Base64;

import com.android.adservices.shared.util.LogUtil;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;

import java.util.Objects;

/** Helper class that provides utility to parse Base64 encoded string to a proto. */
public final class ProtoParser {
    private ProtoParser() {
        throw new UnsupportedOperationException("static methods present");
    }

    /**
     * Parses Base64 encoded string to a proto object.
     *
     * @param parser A protobuf parser object. e.g. MyProto.parser()
     * @param property The property which needs to be decoded
     * @param value Base64 encoded String
     * @return parsed proto from the Base64 encoded string
     */
    @Nullable
    public static <T extends MessageLite> T parseBase64EncodedStringToProto(
            Parser<T> parser, String property, String value) {
        if (TextUtils.isEmpty(value)) {
            LogUtil.d("Property %s is empty.", property);
            return null;
        }

        byte[] decode = getDecodedPropertyValue(property, value);
        if (Objects.isNull(decode)) {
            return null;
        }

        T proto = null;
        try {
            proto = parser.parseFrom(decode);
        } catch (InvalidProtocolBufferException e) {
            // TODO(b/315382750): Add CEL for this
            LogUtil.e(e, "Error while parsing %s. Error: ", property);
        }

        return proto;
    }

    /*
     * Helper function to decode a proto property
     *
     * @param property The property which needs to be decoded
     * @param base64value The base64 value of the property
     * @return The decoded value of the property passed as the parameter
     */
    private static byte[] getDecodedPropertyValue(String property, String base64value) {
        try {
            return Base64.decode(base64value, Base64.NO_PADDING | Base64.NO_WRAP);
        } catch (IllegalArgumentException e) {
            // TODO(b/315382750): Add CEL for this
            LogUtil.e(e, "Error while decoding %s. Error: ", property);
        }
        return null;
    }
}
