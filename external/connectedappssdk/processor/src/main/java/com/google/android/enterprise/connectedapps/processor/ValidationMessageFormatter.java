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
package com.google.android.enterprise.connectedapps.processor;

import com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationNames;

/** Formats annotation validation messages with the provided names of the annotation set. */
public final class ValidationMessageFormatter {

  private final AnnotationNames annotationNames;

  public static ValidationMessageFormatter forAnnotations(AnnotationNames annotationNames) {
    return new ValidationMessageFormatter(annotationNames);
  }

  private ValidationMessageFormatter(AnnotationNames annotationNames) {
    this.annotationNames = annotationNames;
  }

  /**
   * Supports the replacement strings CROSS_PROFILE_ANNOTATION, CROSS_PROFILE_CALLBACK_ANNOTATION,
   * CROSS_PROFILE_CONFIGURATION_ANNOTATION, CROSS_PROFILE_CONFIGURATIONS_ANNOTATION,
   * CROSS_PROFILE_PROVIDER_ANNOTATION, and CROSS_PROFILE_TEST_ANNOTATION.
   */
  String format(String message) {
    return message
        .replace("CROSS_PROFILE_ANNOTATION", annotationNames.crossProfile())
        .replace("CROSS_PROFILE_CALLBACK_ANNOTATION", annotationNames.crossProfileCallback())
        .replace(
            "CROSS_PROFILE_CONFIGURATION_ANNOTATION", annotationNames.crossProfileConfiguration())
        .replace(
            "CROSS_PROFILE_CONFIGURATIONS_ANNOTATION", annotationNames.crossProfileConfigurations())
        .replace("CROSS_PROFILE_PROVIDER_ANNOTATION", annotationNames.crossProfileProvider())
        .replace("CROSS_PROFILE_TEST_ANNOTATION", annotationNames.crossProfileTest());
  }
}
