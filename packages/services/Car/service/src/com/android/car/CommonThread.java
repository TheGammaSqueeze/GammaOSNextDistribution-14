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

package com.android.car;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Denotes that the annotated method or constructor should only be called on the common thread.
 *
 * <p>
 * Example:
 *
 * <pre>
 * <code>
 *  &#64;CommonThread
 *  public void doWork() { ... }
 * </code>
 * </pre>
 *
 * @memberDoc This method must be called on the common car thread. This is typically the thread
 * which the {@link CarServiceUtils#getCommonHandlerThread()} is bound to.
 */
@Retention(SOURCE)
@Target({METHOD, CONSTRUCTOR, FIELD})
public @interface CommonThread {
}
