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

import com.google.android.enterprise.connectedapps.annotations.AvailabilityRestrictions;
import com.google.android.enterprise.connectedapps.annotations.CustomProfileConnector;
import com.google.android.enterprise.connectedapps.annotations.CustomProfileConnector.ProfileType;
import com.google.android.enterprise.connectedapps.annotations.UncaughtExceptionsPolicy;
import com.google.android.enterprise.connectedapps.processor.GeneratorUtilities;
import com.google.android.enterprise.connectedapps.processor.SupportedTypes;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/** Wrapper of an interface used as a profile connector. */
@AutoValue
public abstract class ProfileConnectorInfo {

  @AutoValue
  abstract static class CustomProfileConnectorAnnotationInfo {
    abstract ProfileType primaryProfile();

    abstract ClassName serviceName();

    abstract ImmutableCollection<TypeElement> parcelableWrapperClasses();

    abstract ImmutableCollection<TypeElement> futureWrapperClasses();

    abstract ImmutableCollection<TypeElement> importsClasses();

    abstract AvailabilityRestrictions availabilityRestrictions();

    abstract UncaughtExceptionsPolicy uncaughtExceptionsPolicy();
  }

  public abstract TypeElement connectorElement();

  public ClassName connectorClassName() {
    return ClassName.get(connectorElement());
  }

  public abstract ProfileType primaryProfile();

  public abstract ClassName serviceName();

  public abstract SupportedTypes supportedTypes();

  public abstract ImmutableCollection<TypeElement> parcelableWrapperClasses();

  public abstract ImmutableCollection<TypeElement> futureWrapperClasses();

  public abstract ImmutableCollection<TypeElement> importsClasses();

  public abstract AvailabilityRestrictions availabilityRestrictions();

  public abstract UncaughtExceptionsPolicy uncaughtExceptionsPolicy();

  public static ProfileConnectorInfo create(
      Context context, TypeElement connectorElement, SupportedTypes globalSupportedTypes) {
    Elements elements = context.elements();

    CustomProfileConnectorAnnotationInfo annotationInfo =
        extractFromCustomProfileConnectorAnnotation(context, elements, connectorElement);

    Set<TypeElement> parcelableWrappers = new HashSet<>(annotationInfo.parcelableWrapperClasses());
    Set<TypeElement> futureWrappers = new HashSet<>(annotationInfo.futureWrapperClasses());

    for (TypeElement importConnectorClass : annotationInfo.importsClasses()) {
      ProfileConnectorInfo importConnector =
          ProfileConnectorInfo.create(context, importConnectorClass, globalSupportedTypes);
      parcelableWrappers.addAll(importConnector.parcelableWrapperClasses());
      futureWrappers.addAll(importConnector.futureWrapperClasses());
    }

    return new AutoValue_ProfileConnectorInfo(
        connectorElement,
        annotationInfo.primaryProfile(),
        annotationInfo.serviceName(),
        globalSupportedTypes
            .asBuilder()
            .addParcelableWrappers(
                ParcelableWrapper.createCustomParcelableWrappers(context, parcelableWrappers))
            .addFutureWrappers(FutureWrapper.createCustomFutureWrappers(context, futureWrappers))
            .build(),
        ImmutableSet.copyOf(parcelableWrappers),
        ImmutableSet.copyOf(futureWrappers),
        annotationInfo.importsClasses(),
        annotationInfo.availabilityRestrictions(),
        annotationInfo.uncaughtExceptionsPolicy());
  }

  private static CustomProfileConnectorAnnotationInfo extractFromCustomProfileConnectorAnnotation(
      Context context, Elements elements, TypeElement connectorElement) {
    CustomProfileConnector customProfileConnector =
        connectorElement.getAnnotation(CustomProfileConnector.class);

    if (customProfileConnector == null) {
      return new AutoValue_ProfileConnectorInfo_CustomProfileConnectorAnnotationInfo(
          ProfileType.NONE,
          getDefaultServiceName(elements, connectorElement),
          ImmutableSet.of(),
          ImmutableSet.of(),
          ImmutableSet.of(),
          AvailabilityRestrictions.DEFAULT,
          UncaughtExceptionsPolicy.NOTIFY_RETHROW);
    }

    Collection<TypeElement> parcelableWrappers =
        GeneratorUtilities.extractClassesFromAnnotation(
            context.types(), customProfileConnector::parcelableWrappers);
    Collection<TypeElement> futureWrappers =
        GeneratorUtilities.extractClassesFromAnnotation(
            context.types(), customProfileConnector::futureWrappers);
    Collection<TypeElement> imports =
        GeneratorUtilities.extractClassesFromAnnotation(
            context.types(), customProfileConnector::imports);

    String serviceClassName = customProfileConnector.serviceClassName();

    return new AutoValue_ProfileConnectorInfo_CustomProfileConnectorAnnotationInfo(
        customProfileConnector.primaryProfile(),
        serviceClassName.isEmpty()
            ? getDefaultServiceName(elements, connectorElement)
            : ClassName.bestGuess(serviceClassName),
        ImmutableSet.copyOf(parcelableWrappers),
        ImmutableSet.copyOf(futureWrappers),
        ImmutableSet.copyOf(imports),
        customProfileConnector.availabilityRestrictions(),
        customProfileConnector.uncaughtExceptionsPolicy());
  }

  public static ClassName getDefaultServiceName(Elements elements, TypeElement connectorElement) {
    PackageElement originalPackage = elements.getPackageOf(connectorElement);

    return ClassName.get(
        originalPackage.getQualifiedName().toString(),
        String.format("%s_Service", connectorElement.getSimpleName().toString()));
  }
}
