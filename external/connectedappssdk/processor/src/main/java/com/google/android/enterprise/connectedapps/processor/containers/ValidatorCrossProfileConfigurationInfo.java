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

import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.SERVICE_CLASSNAME;

import com.google.android.enterprise.connectedapps.annotations.CrossProfileConfiguration;
import com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationFinder;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/** A wrapper around basic information from a {@link CrossProfileConfiguration} annotation. */
@AutoValue
public abstract class ValidatorCrossProfileConfigurationInfo {

  public abstract TypeElement configurationElement();

  public abstract ImmutableCollection<TypeElement> providerClassElements();

  public abstract ClassName serviceSuperclass();

  public abstract Optional<TypeElement> serviceClass();

  public abstract Optional<TypeElement> connector();

  public static ImmutableSet<ValidatorCrossProfileConfigurationInfo> createMultipleFromElement(
      Context context, TypeElement annotatedElement) {
    ImmutableSet<CrossProfileConfigurationAnnotationInfo> infos =
        AnnotationFinder.extractCrossProfileConfigurationsAnnotationInfo(context, annotatedElement)
            .configurations();
    ImmutableSet.Builder<ValidatorCrossProfileConfigurationInfo> configurations =
        ImmutableSet.builder();

    if (infos.isEmpty()) {
      configurations.add(createFromElement(context, annotatedElement));
    } else {
      for (CrossProfileConfigurationAnnotationInfo info : infos) {
        configurations.add(createFromAnnotationInfo(info, annotatedElement));
      }
    }

    return configurations.build();
  }

  public static ValidatorCrossProfileConfigurationInfo createFromElement(
      Context context, TypeElement annotatedElement) {
    CrossProfileConfigurationAnnotationInfo annotationInfo =
        extractFromCrossProfileConfigurationAnnotation(context, annotatedElement);

    return createFromAnnotationInfo(annotationInfo, annotatedElement);
  }

  private static ValidatorCrossProfileConfigurationInfo createFromAnnotationInfo(
      CrossProfileConfigurationAnnotationInfo annotationInfo, TypeElement annotatedElement) {
    ClassName serviceSuperclass =
        serviceSuperclassIsDefault(annotationInfo.serviceSuperclass())
            ? SERVICE_CLASSNAME
            : ClassName.get(annotationInfo.serviceSuperclass());

    TypeElement serviceClass =
        serviceClassIsDefault(annotationInfo.serviceClass()) ? null : annotationInfo.serviceClass();

    Optional<TypeElement> connector =
        connectorIsDefault(annotationInfo.connector())
            ? Optional.empty()
            : Optional.of(annotationInfo.connector());

    return new AutoValue_ValidatorCrossProfileConfigurationInfo(
        annotatedElement,
        ImmutableSet.copyOf(annotationInfo.providerClasses()),
        serviceSuperclass,
        Optional.ofNullable(serviceClass),
        connector);
  }

  private static boolean serviceSuperclassIsDefault(TypeElement serviceSuperclass) {
    // CrossProfileConfiguration.class is the default specified serviceSuperclass
    return serviceSuperclass
        .asType()
        .toString()
        .equals(CrossProfileConfiguration.class.getCanonicalName());
  }

  private static boolean serviceClassIsDefault(TypeElement serviceClass) {
    // CrossProfileConfiguration.class is the default specified serviceClass
    return serviceClass
        .asType()
        .toString()
        .equals(CrossProfileConfiguration.class.getCanonicalName());
  }

  private static boolean connectorIsDefault(TypeElement connector) {
    // CrossProfileConfiguration.class is the default specified connector
    return connector.asType().toString().equals(CrossProfileConfiguration.class.getCanonicalName());
  }

  private static CrossProfileConfigurationAnnotationInfo
      extractFromCrossProfileConfigurationAnnotation(Context context, Element annotatedElement) {
    return AnnotationFinder.extractCrossProfileConfigurationAnnotationInfo(
        context, annotatedElement);
  }
}
