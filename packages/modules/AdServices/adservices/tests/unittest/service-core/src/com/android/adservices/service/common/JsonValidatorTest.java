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

package com.android.adservices.service.common;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class JsonValidatorTest {
    private static final String CLASS_NAME = "class";
    private static final String FIELD_NAME = "field";

    private final JsonValidator mJsonValidator = new JsonValidator(CLASS_NAME, FIELD_NAME);

    @Test
    public void testNullJson() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    mJsonValidator.getValidationViolations(null);
                });
    }

    @Test
    public void testValidJsonObject() {
        assertTrue(mJsonValidator.getValidationViolations("{\"a\":5}").isEmpty());
    }

    @Test
    public void testValidJsonArray() {
        ValidatorTestUtil.assertViolationContainsOnly(
                mJsonValidator.getValidationViolations("[\"a\", \"b\"]"),
                String.format(JsonValidator.SHOULD_BE_A_VALID_JSON, CLASS_NAME, FIELD_NAME));
    }

    @Test
    public void testInvalidJson() {
        ValidatorTestUtil.assertViolationContainsOnly(
                mJsonValidator.getValidationViolations("invalid[json]field"),
                String.format(JsonValidator.SHOULD_BE_A_VALID_JSON, CLASS_NAME, FIELD_NAME));
    }
}
