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

package com.android.bedstead.harrier.annotations.meta;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark a Harrier annotation as being Parameterized.
 *
 * <p>There will be a separate run generated for the annotated method for each
 * {@link ParameterizedAnnotation} annotation. The test will be named methodName[paramName].
 *
 * <p>If any {@link ParameterizedAnnotation} annotations are applied to a test, then the basic
 * un-parameterized test will not be run.
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
@RequiresBedsteadJUnit4
public @interface ParameterizedAnnotation {

    /**
     * Other parameterized annotations which are less powerful versions of this one.
     *
     * <p>For example, if this annotation represents a permission, and there is another annotation
     * representing a permission which allows a subset of this one, then this annotation may shadow
     * that one.
     *
     * <p>This will mean that these annotations will never be used together - one will be removed
     * depending on whether the test requires the most powerful or least powerful state.
     *
     * <p>This should not be used if you want to explicitly test the state represented by each
     * annotation.
     */
    Class<? extends Annotation>[] shadows() default {};
}
