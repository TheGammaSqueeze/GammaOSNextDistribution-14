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

import org.junit.Assert;
import org.junit.Test;

public class AdTechIdentifierValidatorTest {
    private static final String CLASS_NAME = "class";
    private static final String FIELD_NAME = "field";

    private final AdTechIdentifierValidator mAdTechIdentifierValidator =
            new AdTechIdentifierValidator(CLASS_NAME, FIELD_NAME);

    @Test
    public void testValidIdentifier() {
        Assert.assertTrue(
                mAdTechIdentifierValidator.getValidationViolations("domain.com").isEmpty());
    }

    @Test
    public void testNullIdentifier() {
        ValidatorTestUtil.assertViolationContainsOnly(
                mAdTechIdentifierValidator.getValidationViolations(null),
                String.format(
                        AdTechIdentifierValidator.IDENTIFIER_SHOULD_NOT_BE_NULL_OR_EMPTY,
                        CLASS_NAME,
                        FIELD_NAME));
    }

    @Test
    public void testEmptyIdentifier() {
        ValidatorTestUtil.assertViolationContainsOnly(
                mAdTechIdentifierValidator.getValidationViolations(""),
                String.format(
                        AdTechIdentifierValidator.IDENTIFIER_SHOULD_NOT_BE_NULL_OR_EMPTY,
                        CLASS_NAME,
                        FIELD_NAME));
    }

    @Test
    public void testMissingHost() {
        ValidatorTestUtil.assertViolationContainsOnly(
                mAdTechIdentifierValidator.getValidationViolations("test@"),
                String.format(
                        AdTechIdentifierValidator.IDENTIFIER_HAS_MISSING_DOMAIN_NAME,
                        CLASS_NAME,
                        FIELD_NAME));
    }

    @Test
    public void testMissingDomainIdentifier() {
        ValidatorTestUtil.assertViolationContainsOnly(
                mAdTechIdentifierValidator.getValidationViolations("a/b/c"),
                String.format(
                        AdTechIdentifierValidator.IDENTIFIER_IS_AN_INVALID_DOMAIN_NAME,
                        CLASS_NAME,
                        FIELD_NAME));
    }

    @Test
    public void testDomainHasPort() {
        ValidatorTestUtil.assertViolationContainsOnly(
                mAdTechIdentifierValidator.getValidationViolations("localhost:3000"),
                String.format(
                        AdTechIdentifierValidator.IDENTIFIER_IS_AN_INVALID_DOMAIN_NAME,
                        CLASS_NAME,
                        FIELD_NAME));
    }
}
