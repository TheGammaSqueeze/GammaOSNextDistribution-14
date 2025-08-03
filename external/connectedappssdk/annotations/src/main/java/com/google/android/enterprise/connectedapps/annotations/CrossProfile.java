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
 * Indicate something is accessible from a different profile.
 *
 * <p>Annotated methods must only return, or take as parameters, types supported by the Profile
 * Aware SDK.
 *
 * <p>Annotated types must be provided by a {@link CrossProfileProvider} class.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface CrossProfile {

  /**
   * The {@link CustomProfileConnector} used by this type.
   *
   * <p>Setting this option for a cross-profile type ensures the generated code provides a better
   * API surface with more accurate Javadoc and stronger compile-time checking.
   *
   * <p>This argument can only be passed when annotating types, not methods.
   *
   * <p>Defaults to undefined, which allows any connector to be used.
   */
  Class<?> connector() default CrossProfile.class;

  /**
   * Custom parcelable wrappers to be accessible in this cross-profile type.
   *
   * <p>This argument can only be passed when annotating types, not methods.
   */
  Class<?>[] parcelableWrappers() default {};

  /**
   * Custom future wrappers to be accessible in this cross-profile type.
   *
   * <p>This argument can only be passed when annotating types, not methods.
   */
  Class<?>[] futureWrappers() default {};

  /**
   * Can this type contain only static {@link CrossProfile} annotated methods.
   *
   * <p>This argument can only be passed when annotating types, not methods.
   */
  boolean isStatic() default false;
}
