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

package com.android.adservices.service.measurement.util;

import static org.junit.Assert.assertThrows;

import android.net.Uri;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ValidationTest {
    private static final Uri NULL_ARGUMENT = null;
    private static final List<Integer> EMPTY_LIST = new ArrayList<>();

    @Test
    public void testValidateNonNull_throwsExceptionWhenNull_oneArgument() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Validation.validateNonNull(NULL_ARGUMENT)
        );
    }

    @Test
    public void testValidateNonNull_throwsExceptionWhenNull_twoArguments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Validation.validateNonNull(32, NULL_ARGUMENT)
        );
    }

    @Test
    public void testValidateNonNull_doesNotThrowExceptionWhenNotNull() {
        Validation.validateNonNull(333, "xyz");
    }

    @Test
    public void testValidateNotEmpty_throwsExceptionWhenEmpty_oneArgument() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Validation.validateNotEmpty(EMPTY_LIST)
        );
    }

    @Test
    public void testValidateNotEmpty_throwsExceptionWhenEmpty_twoArguments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Validation.validateNotEmpty(List.of(1), EMPTY_LIST)
        );
    }

    @Test
    public void testValidateNotEmpty_doesNotThrowExceptionWhenNotEmpty() {
        Validation.validateNotEmpty(List.of(1, 2, 3));
    }

    @Test
    public void testValidateUri_throwsExceptionWhenNull_oneArgument() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Validation.validateUri(NULL_ARGUMENT)
        );
    }

    @Test
    public void testValidateUri_throwsExceptionWhenNull_twoArguments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Validation.validateUri(Uri.parse("https://abc.test"), NULL_ARGUMENT)
        );
    }

    @Test
    public void testValidateUri_throwsExceptionWhenInvalid_oneArgument() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Validation.validateUri(Uri.parse("abc.test"))
        );
    }

    @Test
    public void testValidateUri_throwsExceptionWhenInvalid_twoArguments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Validation.validateUri(Uri.parse("https://abc.test"), Uri.parse("abc.test"))
        );
    }

    @Test
    public void testValidateUri_doesNotThrowExceptionWhenNotNullAndValid() {
        Validation.validateNonNull(Uri.parse("https://abc.test"), Uri.parse("https://xyz.test"));
    }
}
