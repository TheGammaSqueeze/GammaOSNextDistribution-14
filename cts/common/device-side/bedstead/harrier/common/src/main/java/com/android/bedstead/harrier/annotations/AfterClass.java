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

package com.android.bedstead.harrier.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Replacement for {@link org.junit.AfterClass} for use by classes which use {@code Devicestate}.
 *
 * <p>Methods annotated {@link AfterClass} must be public, static, must return {@code void}, and
 * must take no arguments.
 *
 * <p>The annotated method will be called after all tests, once per class.
 *
 * <p>The state prior to calling this method is not guaranteed, as test methods may have changed the
 * state. If any class-level annotation assumptions are violated this method will not be run.
 *
 * <p>If there are multiple methods annotated {@code @AfterClass} there is no guarantee as to the
 * order they will be executed.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AfterClass {
}
