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
package com.android.adservices.common;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.android.adservices.common.AndroidSdk.Range;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Used by {@link AbstractSdkLevelSupportedRule SdkLevelSupportedRule} to only run a test if the
 * Android SDK level of the device is in the given {@link Range}.
 *
 * <p>The range can be open. For example, a test annotated with {@code RequiresSdkRange(atLeast=12)}
 * would be the equivalent at {@code RequiresSdkRangeAtLeastS}, while a test annotated with just
 * {@code RequiresSdkRange} would run on any SDK version.
 */
@Retention(RUNTIME)
@Target({METHOD, TYPE})
public @interface RequiresSdkRange {

    /**
     * Minimum device SDK to run the test (i.e., test will be skipped if device's SDK is LESS THAN
     * that).
     */
    int atLeast() default Range.NO_MIN;

    /**
     * Maximum device SDK to run the test (i.e., test will be skipped if device's SDK is MORE THAN
     * that).
     */
    int atMost() default Range.NO_MAX;

    /** Reason why the test should be skipped. */
    String reason() default "";
}
