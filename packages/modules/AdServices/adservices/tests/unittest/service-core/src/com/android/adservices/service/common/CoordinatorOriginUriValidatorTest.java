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

package com.android.adservices.service.common;

import android.net.Uri;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class CoordinatorOriginUriValidatorTest {
    private CoordinatorOriginUriValidator mValidator;
    private static final String ALLOWLIST = "https://example-2.com,https://example.com";

    private static final String HOSTNAME = "example.com";
    private static final String SCHEME = "https://";
    private static final String PATH = "/should/not/exist";

    private static final String INVALID_URL = "/a/b/c";

    @Before
    public void setup() {
        mValidator = CoordinatorOriginUriValidator.createEnabledInstance(ALLOWLIST);
    }

    @Test
    public void test_multiCloudFlagOff_invalidUri_hasNoViolation() {
        mValidator = CoordinatorOriginUriValidator.createDisabledInstance();

        Assert.assertTrue(mValidator.getValidationViolations(Uri.parse(INVALID_URL)).isEmpty());
    }

    @Test
    public void test_validUri_hasNoViolation() {
        Assert.assertTrue(
                mValidator.getValidationViolations(Uri.parse(SCHEME + HOSTNAME)).isEmpty());
    }

    @Test
    public void test_nullUri_hasNoViolation() {
        Assert.assertTrue(mValidator.getValidationViolations(null).isEmpty());
    }

    @Test
    public void test_noHostUri_hasViolation() {
        ValidatorTestUtil.assertViolationContainsOnly(
                mValidator.getValidationViolations(Uri.parse(INVALID_URL)),
                String.format(CoordinatorOriginUriValidator.URI_SHOULD_HAVE_PRESENT_HOST));
    }

    @Test
    public void test_notHttpsScheme_hasViolation() {
        ValidatorTestUtil.assertViolationContainsOnly(
                mValidator.getValidationViolations(Uri.parse("http://" + HOSTNAME)),
                String.format(CoordinatorOriginUriValidator.URI_SHOULD_USE_HTTPS));
    }

    @Test
    public void test_hasPath_hasViolation() {
        ValidatorTestUtil.assertViolationContainsOnly(
                mValidator.getValidationViolations(Uri.parse(SCHEME + HOSTNAME + PATH)),
                String.format(CoordinatorOriginUriValidator.URI_SHOULD_NOT_HAVE_PATH));
    }

    @Test
    public void test_urlNotAllowlisted_hasViolation() {
        ValidatorTestUtil.assertViolationContainsOnly(
                mValidator.getValidationViolations(Uri.parse(SCHEME + "google.com")),
                String.format(CoordinatorOriginUriValidator.URI_SHOULD_BELONG_TO_ALLOWLIST));
    }
}
