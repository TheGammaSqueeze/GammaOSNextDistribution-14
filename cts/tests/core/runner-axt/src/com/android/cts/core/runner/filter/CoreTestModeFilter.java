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

package com.android.cts.core.runner.filter;

import android.os.Build;
import android.os.Bundle;
import android.platform.test.annotations.FlakyTest;
import android.platform.test.annotations.LargeTest;

import libcore.test.annotation.NonCts;
import libcore.test.annotation.NonMts;

import org.junit.runner.Description;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class CoreTestModeFilter implements Predicate<Description> {

    private static final String ARGUMENT_MODE = "core-test-mode";

    private final Set<Class> mTestSkippingAnnotations;

    /**
     * @param testSkippingAnnotations list of annotation classes.
     */
    private CoreTestModeFilter(Class<? extends Annotation>... testSkippingAnnotations) {
        mTestSkippingAnnotations = new HashSet<>(Arrays.asList(testSkippingAnnotations));
    }

    /**
     * Config this filter from the {@link #ARGUMENT_MODE} option.
     *
     * @see com.android.cts.core.runner.ExpectationBasedFilter#ExpectationBasedFilter(Bundle)
     */
    public static Predicate<Description> createInstance(Bundle args) {
        String mode = args.getString(ARGUMENT_MODE);
        if ("mts".equals(mode)) {
            // We have to hard-coded the annotation name of NonMts because the annotation definition
            // isn't built into the same apk file.
            return new CoreTestModeFilter(NonMts.class);
        } else if ("presubmit".equals(mode)) {
            return new CoreTestModeFilter(FlakyTest.class, LargeTest.class);
        } else {
            // The default mode is CTS, because most libcore test suites are prefixed with "Cts".
            // It's okay that ignoredTestsInCts.txt doesn't exist in the test .apk file, and
            // the created CoreExpectationFilter doesn't skip any test in this case.
            Set<String> expectationFile = Set.of("/skippedCtsTest.txt");
            return new CoreTestModeFilter(NonCts.class)
                    .and(CoreExpectationFilter.createInstance(expectationFile));
        }
    }

    @Override
    public boolean test(Description description) {
        if (isAnnotated(description.getAnnotations())) {
            return false;
        }

        // In addition to the method, check if the test class is annotated.
        Class<?> testClass = description.getTestClass();
        if (testClass != null && isAnnotated(Arrays.asList(testClass.getAnnotations()))) {
            return false;
        }
        return true;
    }

    private boolean isAnnotated(Collection<Annotation> annotations) {
        for (Annotation annotation : annotations) {
            Class<? extends Annotation> clazz = annotation.annotationType();
            if (mTestSkippingAnnotations.contains(clazz)) {
                if (annotation instanceof NonMts) {
                    if (isNonMtsTestDisabledOnThisSdk((NonMts) annotation)) {
                        return true;
                    } else {
                        // Process the next annotation.
                        continue;
                    }
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isNonMtsTestDisabledOnThisSdk(NonMts annotation) {
        int disabledUntilSdk = annotation.disabledUntilSdk();
        return disabledUntilSdk < 0 || Build.VERSION.SDK_INT < disabledUntilSdk;
    }

}
