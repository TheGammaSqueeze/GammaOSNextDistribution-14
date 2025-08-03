/*
 * Copyright (C) 2021 The Android Open Source Project
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
package android.platform.test.util;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TestFilterTest {
    private static final Description DESCRIPTION1 =
            Description.createTestDescription("testClassA", "method1");
    private static final Description DESCRIPTION2 =
            Description.createTestDescription("testClassB", "method2");

    @Test
    public void testFilters_singleTest() {
        assertThat(
                        TestFilter.isFilteredOrUnspecified(
                                buildArguments("testClassA#method1"), DESCRIPTION1))
                .isTrue();
    }

    @Test
    public void testFilters_multipleTests() {
        Bundle arguments = buildArguments("testClassA#method1,testClassB#method2");
        assertThat(TestFilter.isFilteredOrUnspecified(arguments, DESCRIPTION1)).isTrue();
        assertThat(TestFilter.isFilteredOrUnspecified(arguments, DESCRIPTION2)).isTrue();
    }

    @Test
    public void testFilters_allTestsWhenUnspecified() {
        assertThat(TestFilter.isFilteredOrUnspecified(new Bundle(), DESCRIPTION1)).isTrue();
        assertThat(TestFilter.isFilteredOrUnspecified(new Bundle(), DESCRIPTION2)).isTrue();
    }

    @Test
    public void testDoesNotFilter_otherTest() {
        assertThat(
                        TestFilter.isFilteredOrUnspecified(
                                buildArguments("testClassA#method1"), DESCRIPTION2))
                .isFalse();
    }

    @Test
    public void testDoesNotThrow_onBadArguments() {
        // If the argument is explicitly null, then it's treated as unspecified.
        assertThat(TestFilter.isFilteredOrUnspecified(buildArguments(null), DESCRIPTION1)).isTrue();
        // If the argument is any other invalid input, then it just won't match.
        assertThat(TestFilter.isFilteredOrUnspecified(buildArguments(",,,"), DESCRIPTION1))
                .isFalse();
        assertThat(TestFilter.isFilteredOrUnspecified(buildArguments("a#,#b"), DESCRIPTION1))
                .isFalse();
    }

    private Bundle buildArguments(String testFilterArg) {
        Bundle args = new Bundle();
        args.putString(TestFilter.OPTION_NAME, testFilterArg);
        return args;
    }
}
