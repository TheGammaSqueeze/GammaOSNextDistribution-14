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

package android.platform.test.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that {@code SetFlagsRule} should disable all the given feature flags before running the
 * annotated test or class.
 *
 * <p>This annotation works together with {@link EnableFlags} to define the value of the flag that
 * needs to be set for the test to run. It is an error for either a method or class to declare that
 * a flag is set to be both enabled and disabled.
 *
 * <p>If the value for a particular flag is defined (by either {@code EnableFlags} or {@code
 * DisableFlags}) by both the class and test method, then the values must be consistent.
 *
 * <p>If the value of a one flag is required by an annotation on the class, and the value of a
 * different flag is required by an annotation of the method, then both requirements apply.
 *
 * <p>With {@code SetFlagsRule}, the flag will be disabled within the test process for the duration
 * of the test(s). When being run with {@code FlagsParameterization} that enables the flag, then the
 * test will be skipped with 'assumption failed'.
 *
 * <p>Both {@code SetFlagsRule} and {@code CheckFlagsRule} will fail the test if a particular flag
 * is both set (with {@code EnableFlags} or {@code DisableFlags}) and required (with {@code
 * RequiresFlagsEnabled} or {@code RequiresFlagsDisabled}).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface DisableFlags {
    /**
     * The list of the feature flags to be disabled. Each item is the full flag name with the format
     * {package_name}.{flag_name}.
     */
    String[] value();
}
