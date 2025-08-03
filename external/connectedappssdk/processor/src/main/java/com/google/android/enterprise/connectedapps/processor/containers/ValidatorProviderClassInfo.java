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
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import javax.lang.model.element.TypeElement;

/** Wrapper of basic information for a cross-profile provider class. */
@AutoValue
public abstract class ValidatorProviderClassInfo {

  public abstract TypeElement providerClassElement();

  public abstract ImmutableCollection<TypeElement> staticTypes();

  public String simpleName() {
    return providerClassElement().getSimpleName().toString();
  }

  public ClassName className() {
    return ClassName.get(providerClassElement());
  }

  public static ValidatorProviderClassInfo create(
      Context context, TypeElement providerClassElement) {
    CrossProfileProviderAnnotationInfo annotationInfo =
        AnnotationFinder.extractCrossProfileProviderAnnotationInfo(context, providerClassElement);

    return new AutoValue_ValidatorProviderClassInfo(
        providerClassElement, ImmutableSet.copyOf(annotationInfo.staticTypes()));
  }
}
