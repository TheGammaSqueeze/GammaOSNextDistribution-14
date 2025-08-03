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
package com.google.android.enterprise.connectedapps.processor.containers;

import com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationFinder;
import com.google.android.enterprise.connectedapps.testing.annotations.CrossProfileTest;
import com.google.auto.value.AutoValue;
import javax.lang.model.element.TypeElement;

/** Wrapper of a {@link CrossProfileTest} annotated class. */
@AutoValue
public abstract class ValidatorCrossProfileTestInfo {

  public abstract TypeElement crossProfileTestElement();

  public abstract TypeElement configurationElement();

  public static ValidatorCrossProfileTestInfo create(
      Context context, TypeElement crossProfileTestElement) {
    CrossProfileTestAnnotationInfo annotationInfo =
        AnnotationFinder.extractCrossProfileTestAnnotationInfo(context, crossProfileTestElement);
    return new AutoValue_ValidatorCrossProfileTestInfo(
        crossProfileTestElement, annotationInfo.configuration());
  }
}
