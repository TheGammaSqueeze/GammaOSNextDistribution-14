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

import static com.google.common.truth.Truth.assertWithMessage;

import android.adservices.common.CommonFixture;
import android.net.Uri;

import org.junit.Assert;
import org.junit.Test;

import java.util.Locale;

public class AdTechUriValidatorTest {
    private static final String CLASS_NAME = "class";
    private static final String URI_FIELD_NAME = "field";

    private final AdTechUriValidator mValidator =
            new AdTechUriValidator(
                    ValidatorUtil.AD_TECH_ROLE_BUYER,
                    CommonFixture.VALID_BUYER_1.toString(),
                    CLASS_NAME,
                    URI_FIELD_NAME);

    @Test
    public void testValidUri_hasNoViolation() {
        Assert.assertTrue(
                mValidator
                        .getValidationViolations(
                                Uri.parse("https://" + CommonFixture.VALID_BUYER_1 + "/valid/uri"))
                        .isEmpty());
    }

    @Test
    public void testNullUri_hasViolation() {
        ValidatorTestUtil.assertViolationContainsOnly(
                mValidator.getValidationViolations(null),
                String.format(
                        Locale.ENGLISH,
                        AdTechUriValidator.URI_SHOULD_BE_SPECIFIED,
                        CLASS_NAME,
                        URI_FIELD_NAME));
    }

    @Test
    public void testNoHostUri_hasViolation() {
        ValidatorTestUtil.assertViolationContainsOnly(
                mValidator.getValidationViolations(Uri.parse("/a/b/c")),
                String.format(
                        Locale.ENGLISH,
                        AdTechUriValidator.URI_SHOULD_HAVE_PRESENT_HOST,
                        CLASS_NAME,
                        URI_FIELD_NAME));
    }

    @Test
    public void testNotMatchHost_hasViolation() {
        String uriHost = "buy.com";
        ValidatorTestUtil.assertViolationContainsOnly(
                mValidator.getValidationViolations(Uri.parse("https://" + uriHost + "/not/match")),
                String.format(
                        Locale.ENGLISH,
                        AdTechUriValidator.IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                        ValidatorUtil.AD_TECH_ROLE_BUYER,
                        CommonFixture.VALID_BUYER_1,
                        ValidatorUtil.AD_TECH_ROLE_BUYER,
                        URI_FIELD_NAME,
                        uriHost));
    }

    @Test
    public void testNotHttpsHost_hasViolation() {
        ValidatorTestUtil.assertViolationContainsOnly(
                mValidator.getValidationViolations(
                        Uri.parse("http://" + CommonFixture.VALID_BUYER_1 + "/not/https/")),
                String.format(
                        Locale.ENGLISH,
                        AdTechUriValidator.URI_SHOULD_USE_HTTPS,
                        CLASS_NAME,
                        URI_FIELD_NAME));
    }

    @Test
    public void testDomainContainingNotMatchingHost_hasNoViolations() {
        // The domain given contains but does not match the expected URI
        Uri mismatchingUri =
                Uri.parse(
                        "https://subdomain."
                                + CommonFixture.VALID_BUYER_1
                                + ".fake.net/path/to/resource");

        assertWithMessage("List of validation errors")
                .that(mValidator.getValidationViolations(mismatchingUri))
                .containsExactly(
                        String.format(
                                Locale.ENGLISH,
                                AdTechUriValidator.IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                ValidatorUtil.AD_TECH_ROLE_BUYER,
                                CommonFixture.VALID_BUYER_1,
                                ValidatorUtil.AD_TECH_ROLE_BUYER,
                                URI_FIELD_NAME,
                                mismatchingUri.getHost()));
    }

    @Test
    public void testSubdomainMatchingHost_hasNoViolations() {
        Uri subdomainUri =
                Uri.parse("https://subdomain." + CommonFixture.VALID_BUYER_1 + "/path/to/resource");

        assertWithMessage("List of validation errors")
                .that(mValidator.getValidationViolations(subdomainUri))
                .isEmpty();
    }

    @Test
    public void testLongerSubdomainMatchingHost_hasNoViolations() {
        Uri subdomainUri =
                Uri.parse(
                        "https://s.u.b.d.o.m.a.i.n."
                                + CommonFixture.VALID_BUYER_1
                                + "/path/to/resource");

        assertWithMessage("List of validation errors")
                .that(mValidator.getValidationViolations(subdomainUri))
                .isEmpty();
    }

    @Test
    public void testNonSubdomainEndingWithSameSubstring_hasViolation() {
        // Note NO separating `.` so that the host is different
        Uri mismatchingUri =
                Uri.parse(
                        "https://notasubdomain"
                                + CommonFixture.VALID_BUYER_1
                                + "/path/to/resource");

        assertWithMessage("List of validation errors")
                .that(mValidator.getValidationViolations(mismatchingUri))
                .containsExactly(
                        String.format(
                                Locale.ENGLISH,
                                AdTechUriValidator.IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                ValidatorUtil.AD_TECH_ROLE_BUYER,
                                CommonFixture.VALID_BUYER_1,
                                ValidatorUtil.AD_TECH_ROLE_BUYER,
                                URI_FIELD_NAME,
                                mismatchingUri.getHost()));
    }

    @Test
    public void testSubdomainWithMixedCaseMatchingHost_hasNoViolations() {
        String mixedCaseHost = "tEst.COm";
        assertWithMessage("Mixed case host matches original host")
                .that(mixedCaseHost.equalsIgnoreCase(CommonFixture.VALID_BUYER_1.toString()))
                .isTrue();

        Uri subdomainUri = Uri.parse("https://suBdoMAiN." + mixedCaseHost + "/path/to/resource");

        assertWithMessage("List of validation errors")
                .that(mValidator.getValidationViolations(subdomainUri))
                .isEmpty();
    }
}
