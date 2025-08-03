/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.enterprise.connectedapps.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotate an interface as being used for asynchronous cross-user callbacks.
 *
 * <p>Interfaces annotated with this must have a single method which returns void, and must only
 * take a single parameter of a type supported by the Connected Apps SDK.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface CrossUserCallback {

  /**
   * If the callback should be enforced to be "simple".
   *
   * <p>Simple callbacks have a single method which accepts 0 or 1 arguments.
   *
   * <p>Simple callbacks can be used with calls to multiple users and with
   * {@code .isAvailable()}, whereas non-simple callbacks can not.
   */
  boolean simple() default false;
}
