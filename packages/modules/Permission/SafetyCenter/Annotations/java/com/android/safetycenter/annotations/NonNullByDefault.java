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
package com.android.safetycenter.annotations;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.annotation.Nonnull;
import javax.annotation.meta.TypeQualifierDefault;

/**
 * Specifies that all type uses are {@link Nonnull} within the annotated package, unless tagged with
 * {@code @Nullable}. This helps IDEs flag all potential nullability issues without having to use
 * {@code @NonNull} annotations.
 *
 * <p>This is similar to {@code @ParametersAreNonnullByDefault}, but is also applied more widely
 * (e.g. to methods return types and fields).
 */
@Retention(CLASS)
@Target(PACKAGE)
@TypeQualifierDefault({PARAMETER, FIELD, METHOD})
@Nonnull // Android variant cannot be applied as a type qualifier.
public @interface NonNullByDefault {}
