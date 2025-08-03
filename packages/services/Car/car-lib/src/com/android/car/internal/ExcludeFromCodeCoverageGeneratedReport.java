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

package com.android.car.internal;

import android.annotation.IntDef;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Annotation used to mark code to be excluded from coverage report.
 *
 * @hide
 */
@Target({ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD})
public @interface ExcludeFromCodeCoverageGeneratedReport {

    // Reason annotation and its associated constant values
    int DEPRECATED_CODE = 0;
    int BOILERPLATE_CODE = 1;
    int DUMP_INFO = 2;
    int DEBUGGING_CODE = 3;
    int PRIVATE_CONSTRUCTOR = 4;

    @IntDef(prefix = "REASON_", value = {
            DEPRECATED_CODE,
            BOILERPLATE_CODE,
            DUMP_INFO,
            DEBUGGING_CODE,
            PRIVATE_CONSTRUCTOR
    })
    @interface Reason { }

    /**
     * The reason explaining why the code is being excluded from the code coverage report.
     * <p>
     * Possible reasons to exclude code from coverage report are:
     * <p><ul>
     * <li>{@link ExcludeFromCodeCoverageGeneratedReport#DEPRECATED_CODE} to exclude deprecated
     * code from coverage report
     * <li>{@link ExcludeFromCodeCoverageGeneratedReport#BOILERPLATE_CODE} to exclude boilerplate
     * code like {@link java.lang.Object} methods, {@link android.os.Parcel} methods, etc
     * <li>{@link ExcludeFromCodeCoverageGeneratedReport#DUMP_INFO} to exclude dump info methods
     * <li>{@link ExcludeFromCodeCoverageGeneratedReport#DEBUGGING_CODE} to exclude debugging
     * <li>{@link ExcludeFromCodeCoverageGeneratedReport#PRIVATE_CONSTRUCTOR} to exclude private
     * constructors from classes that only provide static methods
     * purpose
     * code
     * </ul><p>
     */
    @Reason int reason();

    /**
     * Optional field used to provide extra details about the excluded code (e.g. it can be used to
     * tag a follow up bug).
     */
    String details() default "";
}
