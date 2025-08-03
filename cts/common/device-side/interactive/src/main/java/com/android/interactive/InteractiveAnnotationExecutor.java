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

package com.android.interactive;

import static com.android.bedstead.harrier.AnnotationExecutorUtil.checkFailOrSkip;

import com.android.bedstead.harrier.AnnotationExecutor;
import com.android.bedstead.harrier.annotations.FailureMode;

import java.lang.annotation.Annotation;

/**
 * Implementation of {@link AnnotationExecutor} for use with Interactive.
 */
public final class InteractiveAnnotationExecutor implements AnnotationExecutor {

    @Override
    public void applyAnnotation(Annotation annotation) {
        if (annotation instanceof RequireBooleanStepResult) {
            RequireBooleanStepResult requireBooleanStepResultAnnotation =
                    (RequireBooleanStepResult) annotation;
            requireBooleanStepResult(requireBooleanStepResultAnnotation.step(),
                    requireBooleanStepResultAnnotation.expectedResult(),
                    requireBooleanStepResultAnnotation.reason(),
                    requireBooleanStepResultAnnotation.failureMode());
        }
    }

    @Override
    public void teardownShareableState() {

    }

    @Override
    public void teardownNonShareableState() {

    }

    private void requireBooleanStepResult(
            Class<? extends Step<Boolean>> stepClass,
            boolean expectedResult, String reason, FailureMode failureMode) {
        boolean result = false;
        try {
            result = Step.execute(stepClass);
        } catch (Exception e) {
            throw new RuntimeException("Error executing step " + stepClass, e);
        }

        checkFailOrSkip(reason, result == expectedResult, failureMode);
    }
}
