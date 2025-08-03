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

import com.android.adservices.common.AndroidSdk.Level;
import com.android.adservices.common.AndroidSdk.Range;

import com.google.auto.value.AutoAnnotation;

import java.lang.annotation.Annotation;

/** Provides {@code auto-value-annotation}s for annotations used on test cases. */
final class TestAnnotations {

    private TestAnnotations() {
        throw new UnsupportedOperationException("provides only static methods");
    }

    public static Annotation newAnnotationForAtLeast(Level level, String reason) {
        switch (level) {
            case R:
                return sdkLevelAtLeastR(reason);
            case S:
                return sdkLevelAtLeastS(reason);
            case S2:
                return sdkLevelAtLeastS2(reason);
            case T:
                return sdkLevelAtLeastT(reason);
            case U:
                return sdkLevelAtLeastU(reason);
            default:
                throw new UnsupportedOperationException(level.toString());
        }
    }

    @AutoAnnotation
    public static RequiresSdkLevelAtLeastR sdkLevelAtLeastR(String reason) {
        return new AutoAnnotation_TestAnnotations_sdkLevelAtLeastR(reason);
    }

    @AutoAnnotation
    public static RequiresSdkLevelAtLeastS sdkLevelAtLeastS(String reason) {
        return new AutoAnnotation_TestAnnotations_sdkLevelAtLeastS(reason);
    }

    @AutoAnnotation
    public static RequiresSdkLevelAtLeastS2 sdkLevelAtLeastS2(String reason) {
        return new AutoAnnotation_TestAnnotations_sdkLevelAtLeastS2(reason);
    }

    @AutoAnnotation
    public static RequiresSdkLevelAtLeastT sdkLevelAtLeastT(String reason) {
        return new AutoAnnotation_TestAnnotations_sdkLevelAtLeastT(reason);
    }

    @AutoAnnotation
    public static RequiresSdkLevelAtLeastU sdkLevelAtLeastU(String reason) {
        return new AutoAnnotation_TestAnnotations_sdkLevelAtLeastU(reason);
    }

    @AutoAnnotation
    public static RequiresSdkRange newAnnotationForLessThanT(String reason) {
        return sdkRange(Range.NO_MIN, AndroidSdk.PRE_T, reason);
    }

    @AutoAnnotation
    public static RequiresSdkRange sdkRange(int atLeast, int atMost, String reason) {
        return new AutoAnnotation_TestAnnotations_sdkRange(atLeast, atMost, reason);
    }

    @AutoAnnotation
    public static RequiresLowRamDevice requiresLowRamDevice() {
        return new AutoAnnotation_TestAnnotations_requiresLowRamDevice();
    }

    @AutoAnnotation
    public static RequiresGoDevice requiresGoDevice() {
        return new AutoAnnotation_TestAnnotations_requiresGoDevice();
    }
}
