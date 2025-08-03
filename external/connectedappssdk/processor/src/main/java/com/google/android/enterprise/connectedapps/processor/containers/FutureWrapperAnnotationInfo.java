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

import com.google.android.enterprise.connectedapps.annotations.CustomFutureWrapper;
import com.google.android.enterprise.connectedapps.processor.GeneratorUtilities;
import com.google.auto.value.AutoValue;
import javax.lang.model.element.TypeElement;

/**
 * Wrapper around information contained in a {@link
 * com.google.android.enterprise.connectedapps.annotations.CustomFutureWrapper} annotation.
 */
@AutoValue
public abstract class FutureWrapperAnnotationInfo {

  public abstract TypeElement originalType();

  public static FutureWrapperAnnotationInfo extractFromFutureWrapperAnnotation(
      Context context, CustomFutureWrapper customFutureWrapperAnnotation) {
    if (customFutureWrapperAnnotation == null) {
      throw new NullPointerException("customFutureWrapperAnnotation must not be null");
    }

    TypeElement originalType =
        GeneratorUtilities.extractClassFromAnnotation(
            context.types(), customFutureWrapperAnnotation::originalType);

    return new AutoValue_FutureWrapperAnnotationInfo(originalType);
  }
}
