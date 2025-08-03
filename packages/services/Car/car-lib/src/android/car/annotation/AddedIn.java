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

package android.car.annotation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Tells in which version of car API this method / type / field was added.
 * <p> For items marked with this, the client need to make sure to check car API version using
 * {@link android.car.Car#API_VERSION_MAJOR_INT} for major version and
 * {@link android.car.Car#API_VERSION_MINOR_INT} for minor version.
 *
 * @deprecated use {@link ApiRequirements} instead.
 *
 * @hide
 */
@Retention(RUNTIME)
@Target({ANNOTATION_TYPE, FIELD, TYPE, METHOD})
@Deprecated
public @interface AddedIn {
    int majorVersion();
    int minorVersion() default 0;
}
