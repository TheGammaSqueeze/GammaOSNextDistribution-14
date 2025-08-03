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
 * Specify configuration for cross-profile calls.
 *
 * <p>Typically you should have only one {@link CrossProfileConfiguration} annotated class in your
 * build.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface CrossProfileConfiguration {
  /** provider classes that should be accessible */
  Class<?>[] providers() default {};

  /**
   * A {@code android.app.Service} subclass which should be used as the superclass of the generated
   * service. By default this is {@code android.app.Service}.
   */
  Class<?> serviceSuperclass() default CrossProfileConfiguration.class;
  // android.app.Service isn't available to the annotations library, so we default to
  // CrossProfileConfiguration and swap it to android.app.Service when compiling

  /**
   * A {@code android.app.Service} subclass which should be used instead of the generated service.
   *
   * <p>If not specified, a service will be generated.
   *
   * <p>The class name must match the {@link CustomProfileConnector#serviceClassName()}.
   */
  Class<?> serviceClass() default CrossProfileConfiguration.class;

  /**
   * The {@link CustomProfileConnector} used by this configuration.
   *
   * <p>Defaults to {@code com.google.android.enterprise.connectedapps.CrossProfileConnector}.
   */
  Class<?> connector() default CrossProfileConfiguration.class;
  // com.google.android.enterprise.connectedapps.CrossProfileConnector isn't available to the
  // annotations library, so we default to CrossProfileConfiguration and swap it out when compiling.
}
