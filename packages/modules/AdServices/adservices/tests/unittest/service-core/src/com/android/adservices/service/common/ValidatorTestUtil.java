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

import static com.google.common.truth.Truth.assertThat;

import org.junit.Assert;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class ValidatorTestUtil {
    public static void assertViolationContainsOnly(
            Collection<String> errors, String... expectedErrors) {
        List<String> expectedErrorsList = Arrays.asList(expectedErrors);
        Assert.assertEquals(
                String.format("expected %s / actual %s", expectedErrorsList, errors),
                expectedErrors.length,
                errors.size());
        Assert.assertTrue(
                String.format("expected %s / actual %s", expectedErrorsList, errors),
                errors.containsAll(expectedErrorsList));
        Assert.assertTrue(
                String.format("expected %s / actual %s", expectedErrorsList, errors),
                expectedErrorsList.containsAll(errors));
    }

    public static void assertValidationFailuresMatch(
            final IllegalArgumentException actualException,
            final String expectedViolationsPrefix,
            final List<String> expectedViolations) {

        assertThat(actualException).hasMessageThat().startsWith(expectedViolationsPrefix);
        for (String violation : expectedViolations) {
            assertThat(actualException).hasMessageThat().contains(violation);
        }
    }
}
