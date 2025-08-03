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

package com.android.queryable.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to represent a TestApp query in bedstead annotations.
 *
 * </p> for e.g. @EnsureHasDeviceOwner(query = @Query(targetSdkVersion = @Int(equalTo = 29)))
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Query {

    // If adding fields to this annotation - update {@code TestAppProvider#query}.

    /** Require the packageName matches the {@link String} query. */
    StringQuery packageName() default @StringQuery();

    /** Require the minSdkVersion matches the {@link int} query */
    IntegerQuery minSdkVersion() default @IntegerQuery();

    /** Require the maxSdkVersion matches the {@link int} query */
    IntegerQuery maxSdkVersion() default @IntegerQuery();

    /** Require the targetSdkVersion matches the {@link int} query */
    IntegerQuery targetSdkVersion() default @IntegerQuery();

    /** Require the dpc to be or not be a device admin app. */
    BooleanQuery isDeviceAdmin() default @BooleanQuery;

    /** Require the dpc to be or not be a headless device owner single user app. */
    BooleanQuery isHeadlessDOSingleUser() default @BooleanQuery;
}
