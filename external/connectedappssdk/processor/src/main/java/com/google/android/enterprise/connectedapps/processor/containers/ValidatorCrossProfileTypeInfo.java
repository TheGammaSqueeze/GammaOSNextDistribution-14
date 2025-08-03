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

import static com.google.android.enterprise.connectedapps.processor.GeneratorUtilities.findCrossProfileMethodsInClass;
import static java.util.stream.Collectors.toList;

import com.google.android.enterprise.connectedapps.annotations.CrossProfile;
import com.google.android.enterprise.connectedapps.processor.SupportedTypes;
import com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationFinder;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/** A wrapper around basic information from a {@link CrossProfile} type annotation. */
@AutoValue
public abstract class ValidatorCrossProfileTypeInfo {

  public abstract TypeElement crossProfileTypeElement();

  public abstract ImmutableList<ExecutableElement> crossProfileMethods();

  public abstract Optional<ConnectorInfo> connectorInfo();

  public abstract SupportedTypes supportedTypes();

  public abstract ImmutableCollection<TypeElement> parcelableWrapperClasses();

  public abstract ImmutableCollection<TypeElement> futureWrapperClasses();

  public abstract boolean isStatic();

  public static ValidatorCrossProfileTypeInfo create(
      Context context, TypeElement crossProfileTypeElement, SupportedTypes globalSupportedTypes) {
    CrossProfileAnnotationInfo annotationInfo =
        AnnotationFinder.extractCrossProfileAnnotationInfo(context, crossProfileTypeElement);

    Optional<ConnectorInfo> connectorInfo =
        createConnectorInfo(context, annotationInfo, globalSupportedTypes);

    List<ExecutableElement> crossProfileMethodElements =
        findCrossProfileMethodsInClass(crossProfileTypeElement).stream()
            .sorted(Comparator.comparing(i -> i.getSimpleName().toString()))
            .collect(toList());

    SupportedTypes incomingSupportedTypes =
        connectorInfo.isPresent() ? connectorInfo.get().supportedTypes() : globalSupportedTypes;

    SupportedTypes supportedTypes =
        incomingSupportedTypes
            .asBuilder()
            .addParcelableWrappers(
                ParcelableWrapper.createCustomParcelableWrappers(
                    context, annotationInfo.parcelableWrapperClasses()))
            .addFutureWrappers(
                FutureWrapper.createCustomFutureWrappers(
                    context, annotationInfo.futureWrapperClasses()))
            .build();

    return new AutoValue_ValidatorCrossProfileTypeInfo(
        crossProfileTypeElement,
        ImmutableList.copyOf(crossProfileMethodElements),
        connectorInfo,
        supportedTypes,
        annotationInfo.parcelableWrapperClasses(),
        annotationInfo.futureWrapperClasses(),
        annotationInfo.isStatic());
  }

  private static Optional<ConnectorInfo> createConnectorInfo(
      Context context,
      CrossProfileAnnotationInfo annotationInfo,
      SupportedTypes globalSupportedTypes) {
    if (annotationInfo.connectorIsDefault()) {
      return Optional.empty();
    } else if (ConnectorInfo.isProfileConnector(context, annotationInfo.connectorClass())) {
      return Optional.of(
          ConnectorInfo.forProfileConnector(
              context, annotationInfo.connectorClass(), globalSupportedTypes));
    } else if (ConnectorInfo.isUserConnector(context, annotationInfo.connectorClass())) {
      return Optional.of(
          ConnectorInfo.forUserConnector(
              context, annotationInfo.connectorClass(), globalSupportedTypes));
    }

    return Optional.of(
        ConnectorInfo.invalid(context, annotationInfo.connectorClass(), globalSupportedTypes));
  }
}
