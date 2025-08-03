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

package com.android.server.art.testing;

import static org.mockito.Mockito.argThat;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.Log;

import com.android.server.art.CopyAndRewriteProfileResult;

import com.google.common.truth.Correspondence;
import com.google.common.truth.Truth;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

public final class TestingUtils {
    private static final String TAG = "ArtServiceTesting";

    private TestingUtils() {}

    /**
     * Recursively compares two objects using reflection. Returns true if the two objects are equal.
     * For simplicity, this method only supports types that every field is a primitive type, a
     * string, a {@link List}, or a supported type.
     */
    public static boolean deepEquals(
            @Nullable Object a, @Nullable Object b, @NonNull StringBuilder errorMsg) {
        try {
            if (a == null && b == null) {
                return true;
            }
            if (a == null || b == null) {
                errorMsg.append(String.format("Nullability mismatch: %s != %s",
                        a == null ? "null" : "nonnull", b == null ? "null" : "nonnull"));
                return false;
            }
            if (a instanceof List && b instanceof List) {
                return listDeepEquals((List<?>) a, (List<?>) b, errorMsg);
            }
            if (a.getClass() != b.getClass()) {
                errorMsg.append(
                        String.format("Type mismatch: %s != %s", a.getClass(), b.getClass()));
                return false;
            }
            if (a.getClass() == String.class) {
                if (!a.equals(b)) {
                    errorMsg.append(String.format("%s != %s", a, b));
                }
                return a.equals(b);
            }
            if (a.getClass().isArray()) {
                throw new UnsupportedOperationException("Array type is not supported");
            }
            for (Field field : a.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                if (field.getType().isPrimitive()) {
                    if (!field.get(a).equals(field.get(b))) {
                        errorMsg.append(String.format("Field %s mismatch: %s != %s",
                                field.getName(), field.get(a), field.get(b)));
                        return false;
                    }
                } else if (!deepEquals(field.get(a), field.get(b), errorMsg)) {
                    errorMsg.insert(0, String.format("Field %s mismatch: ", field.getName()));
                    return false;
                }
            }
            return true;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /** Same as above, but ignores any error message. */
    public static boolean deepEquals(@Nullable Object a, @Nullable Object b) {
        var errorMsgIgnored = new StringBuilder();
        return deepEquals(a, b, errorMsgIgnored);
    }

    /**
     * A Mockito argument matcher that uses {@link #deepEquals} to compare objects and logs any
     * mismatch.
     */
    public static <T> T deepEq(@Nullable T expected) {
        return argThat(arg -> {
            var errorMsg = new StringBuilder();
            boolean result = deepEquals(arg, expected, errorMsg);
            if (!result) {
                Log.e(TAG, errorMsg.toString());
            }
            return result;
        });
    }

    /**
     * A Mockito argument matcher that matches a list containing expected in any order.
     */
    @SafeVarargs
    public static <ListType extends List<ItemType>, ItemType> ListType inAnyOrder(
            @Nullable ItemType... expected) {
        return argThat(argument -> {
            try {
                Truth.assertThat(argument).containsExactlyElementsIn(expected);
                return true;
            } catch (AssertionError error) {
                return false;
            }
        });
    }

    /**
     * {@link #inAnyOrder(Object[])} but using {@link #deepEquals(Object, Object)}} for comparisons.
     *
     * @see #inAnyOrder(Object[])
     */
    @SafeVarargs
    public static <ListType extends List<ItemType>, ItemType> ListType inAnyOrderDeepEquals(
            @Nullable ItemType... expected) {
        return argThat(argument -> {
            try {
                Truth.assertThat(argument)
                        .comparingElementsUsing(deepEquality())
                        .containsExactlyElementsIn(expected);
                return true;
            } catch (AssertionError error) {
                return false;
            }
        });
    }

    /**
     * A Truth correspondence that uses {@link #deepEquals} to compare objects and reports any
     * mismatch.
     */
    public static <T> Correspondence<T, T> deepEquality() {
        return Correspondence.<T, T>from(TestingUtils::deepEquals, "deeply equals")
                .formattingDiffsUsing((actual, expected) -> {
                    var errorMsg = new StringBuilder();
                    deepEquals(actual, expected, errorMsg);
                    return errorMsg.toString();
                });
    }

    public static CopyAndRewriteProfileResult createCopyAndRewriteProfileSuccess() {
        var result = new CopyAndRewriteProfileResult();
        result.status = CopyAndRewriteProfileResult.Status.SUCCESS;
        return result;
    }

    public static CopyAndRewriteProfileResult createCopyAndRewriteProfileNoProfile() {
        var result = new CopyAndRewriteProfileResult();
        result.status = CopyAndRewriteProfileResult.Status.NO_PROFILE;
        return result;
    }

    public static CopyAndRewriteProfileResult createCopyAndRewriteProfileBadProfile(
            String errorMsg) {
        var result = new CopyAndRewriteProfileResult();
        result.status = CopyAndRewriteProfileResult.Status.BAD_PROFILE;
        result.errorMsg = errorMsg;
        return result;
    }

    private static boolean listDeepEquals(
            @NonNull List<?> a, @NonNull List<?> b, @NonNull StringBuilder errorMsg) {
        if (a.size() != b.size()) {
            errorMsg.append(String.format("List length mismatch: %d != %d", a.size(), b.size()));
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!deepEquals(a.get(i), b.get(i), errorMsg)) {
                errorMsg.insert(0, String.format("Element %d mismatch: ", i));
                return false;
            }
        }
        return true;
    };
}
