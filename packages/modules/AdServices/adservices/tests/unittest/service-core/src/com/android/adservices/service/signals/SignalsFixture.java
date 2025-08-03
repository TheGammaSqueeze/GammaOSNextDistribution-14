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

package com.android.adservices.service.signals;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;

import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.service.devapi.DevContext;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SignalsFixture {

    public static final DevContext DEV_CONTEXT =
            DevContext.builder()
                    .setDevOptionsEnabled(false)
                    .setCallingAppPackageName(CommonFixture.TEST_PACKAGE_NAME)
                    .build();
    public static final byte[] KEY_1 = {(byte) 1, (byte) 2, (byte) 3, (byte) 4};
    public static final byte[] KEY_2 = {(byte) 5, (byte) 6, (byte) 7, (byte) 8};
    public static final byte[] VALUE_1 = {(byte) 42};
    public static final byte[] VALUE_2 = {(byte) 42, (byte) 5, (byte) 9};

    public static String BASE64_KEY_1 = toBase64(KEY_1);
    public static String BASE64_KEY_2 = toBase64(KEY_2);
    public static String BASE64_VALUE_1 = toBase64(VALUE_1);
    public static String BASE64_VALUE_2 = toBase64(VALUE_2);

    public static ByteBuffer BB_KEY_1 = ByteBuffer.wrap(KEY_1);
    public static ByteBuffer BB_KEY_2 = ByteBuffer.wrap(KEY_2);
    public static ByteBuffer BB_VALUE_1 = ByteBuffer.wrap(VALUE_1);
    public static ByteBuffer BB_VALUE_2 = ByteBuffer.wrap(VALUE_2);

    public static final AdTechIdentifier ADTECH = CommonFixture.VALID_BUYER_1;
    public static final String PACKAGE = CommonFixture.TEST_PACKAGE_NAME_1;
    public static final Instant NOW = CommonFixture.FIXED_NOW;

    public static final long ID_1 = 123L;
    public static final long ID_2 = 456L;
    public static final long ID_3 = 789L;

    public static byte[] intToBytes(int i) {
        return intToByteBuffer(i).array();
    }

    public static String intToBase64(int i) {
        return toBase64(intToBytes(i));
    }

    public static ByteBuffer intToByteBuffer(int i) {
        return ByteBuffer.allocate(4).putInt(i);
    }

    public static DBProtectedSignal createSignal(byte[] key, byte[] value) {
        return DBProtectedSignal.builder()
                .setBuyer(ADTECH)
                .setPackageName(PACKAGE)
                .setCreationTime(NOW)
                .setKey(key)
                .setValue(value)
                .build();
    }

    public static DBProtectedSignal createSignal(byte[] key, byte[] value, long id) {
        return DBProtectedSignal.builder()
                .setId(id)
                .setBuyer(ADTECH)
                .setPackageName(PACKAGE)
                .setCreationTime(NOW)
                .setKey(key)
                .setValue(value)
                .build();
    }

    public static DBProtectedSignal createSignal(
            byte[] key, byte[] value, long id, Instant creationTime) {
        return DBProtectedSignal.builder()
                .setId(id)
                .setBuyer(ADTECH)
                .setPackageName(PACKAGE)
                .setCreationTime(creationTime)
                .setKey(key)
                .setValue(value)
                .build();
    }

    public static void assertSignalsBuilderUnorderedListEquals(
            List<DBProtectedSignal.Builder> expected, List<DBProtectedSignal.Builder> actual) {
        List<DBProtectedSignal> expectedBuilt = new ArrayList<>();
        List<DBProtectedSignal> actualBuilt = new ArrayList<>();
        for (DBProtectedSignal.Builder builder : expected) {
            expectedBuilt.add(
                    builder.setBuyer(ADTECH).setPackageName(PACKAGE).setCreationTime(NOW).build());
        }
        for (DBProtectedSignal.Builder builder : actual) {
            actualBuilt.add(
                    builder.setBuyer(ADTECH).setPackageName(PACKAGE).setCreationTime(NOW).build());
        }
        assertThat(actualBuilt).containsExactlyElementsIn(expectedBuilt);
    }

    public static void assertSignalsUnorderedListEqualsExceptIdAndTime(
            List<DBProtectedSignal> expected, List<DBProtectedSignal> actual) {
        // Convert each signal into a string of with elements:
        // buyer:key (base64):value (base 64):package name

        Set<String> expectedSet =
                expected.stream()
                        .map(SignalsFixture::signalToStringNoTimeOrId)
                        .collect(Collectors.toSet());
        Set<String> actualSet =
                actual.stream()
                        .map(SignalsFixture::signalToStringNoTimeOrId)
                        .collect(Collectors.toSet());
        assertEquals(expectedSet, actualSet);
    }

    private static String signalToStringNoTimeOrId(DBProtectedSignal signal) {
        return String.join(
                ":",
                Arrays.asList(
                        signal.getBuyer().toString(),
                        toBase64(signal.getKey()),
                        toBase64(signal.getValue()),
                        signal.getPackageName()));
    }

    private static String toBase64(byte[] in) {
        return Base64.getEncoder().encodeToString(in);
    }
}
