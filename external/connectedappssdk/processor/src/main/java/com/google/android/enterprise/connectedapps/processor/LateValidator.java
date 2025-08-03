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

import static com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationFinder.validationMessageFormatterFor;
import static com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationFinder.validationMessageFormatterForClass;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.google.android.enterprise.connectedapps.processor.containers.ConnectorInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileConfigurationInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileTypeInfo;
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.google.android.enterprise.connectedapps.processor.containers.ProviderClassInfo;
import com.google.common.collect.Streams;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

/** Validator to check that annotations have been used correctly before generating code. */
final class LateValidator {

  private final GeneratorContext generatorContext;

  private static final String PROVIDER_CLASS_DIFFERENT_CONNECTOR_ERROR =
      "All @CROSS_PROFILE_ANNOTATION types provided by a provider class must use the same"
          + " ProfileConnector";
  private static final String CONFIGURATION_DIFFERENT_CONNECTOR_ERROR =
      "All @CROSS_PROFILE_ANNOTATION types specified in the same configuration must use the same"
          + " ProfileConnector";
  private static final String INCORRECT_SERVICE_CLASS =
      "The class specified by serviceClass must match the serviceClassName given by the"
          + " ProfileConnector";
  private static final String STATICTYPES_ERROR =
      "@CROSS_PROFILE_ANNOTATION classes referenced in @CROSS_PROFILE_PROVIDER_ANNOTATION"
          + " staticTypes annotations must not have non-static @CROSS_PROFILE_ANNOTATION annotated"
          + " methods";

  LateValidator(GeneratorContext generatorContext) {
    this.generatorContext = checkNotNull(generatorContext);
  }

  /**
   * Validate code.
   *
   * <p>This will show errors for all issues found. It will not terminate upon finding the first
   * error.
   *
   * @return True if the state is valid
   */
  boolean validate() {
    return Stream.of(
            validateProviderClasses(generatorContext.providers()),
            validateConfigurations(generatorContext.configurations()))
        .allMatch(b -> b);
  }

  private boolean validateProviderClasses(Collection<ProviderClassInfo> providerClasses) {
    boolean isValid = true;

    for (ProviderClassInfo providerClass : providerClasses) {
      isValid = validateProviderClass(providerClass) && isValid;
    }

    return isValid;
  }

  private boolean validateProviderClass(ProviderClassInfo providerClass) {
    boolean isValid = true;

    if (getConnectorQualifiedNamesUsedInProviderClass(providerClass).size() > 1) {
      showError(
          PROVIDER_CLASS_DIFFERENT_CONNECTOR_ERROR,
          providerClass.providerClassElement(),
          validationMessageFormatterForClass(providerClass.providerClassElement()));
      isValid = false;
    }

    for (CrossProfileTypeInfo crossProfileType : providerClass.staticTypes()) {
      if (!crossProfileType.isStatic()) {
        showError(STATICTYPES_ERROR, providerClass.providerClassElement());
        isValid = false;
      }
    }

    return isValid;
  }

  private boolean validateConfigurations(Collection<CrossProfileConfigurationInfo> configurations) {
    boolean isValid = true;

    for (CrossProfileConfigurationInfo configuration : configurations) {
      isValid = validateConfiguration(configuration) && isValid;
    }

    return isValid;
  }

  private boolean validateConfiguration(CrossProfileConfigurationInfo configuration) {
    boolean isValid = true;

    isValid =
        validateCrossProfileTypesHaveUniqueIdentifiers(
                configuration.providers().stream()
                    .flatMap(m -> m.allCrossProfileTypes().stream())
                    .collect(toList()))
            && isValid;

    if (getConnectorQualifiedNamesUsedInConfiguration(configuration).size() > 1) {
      showError(CONFIGURATION_DIFFERENT_CONNECTOR_ERROR, configuration.configurationElement());
      isValid = false;
    }

    if (configuration.serviceClass().isPresent()
        && !configuration
            .serviceClass()
            .get()
            .toString()
            .equals(configuration.connectorInfo().serviceName().toString())) {
      showError(INCORRECT_SERVICE_CLASS, configuration.configurationElement());
      isValid = false;
    }

    return isValid;
  }

  private static Collection<String> getConnectorQualifiedNamesUsedInConfiguration(
      CrossProfileConfigurationInfo configuration) {
    Set<String> connectorQualifiedNames =
        configuration.providers().stream()
            .flatMap(m -> getConnectorQualifiedNamesUsedInProviderClass(m).stream())
            .collect(toSet());
    connectorQualifiedNames.add(
        configuration.connectorInfo().connectorElement().asType().toString());
    return connectorQualifiedNames;
  }

  private boolean validateCrossProfileTypesHaveUniqueIdentifiers(
      Collection<CrossProfileTypeInfo> crossProfileTypes) {
    boolean isValid = true;
    Map<Long, List<CrossProfileTypeInfo>> crossProfileTypeByIdentifier =
        crossProfileTypes.stream().collect(groupingBy(CrossProfileTypeInfo::identifier));

    for (long identifier : crossProfileTypeByIdentifier.keySet()) {
      if (crossProfileTypeByIdentifier.get(identifier).size() > 1) {
        isValid = false;
        String crossProfileTypesString =
            crossProfileTypeByIdentifier.get(identifier).stream()
                .map(m -> m.className().toString())
                .collect(joining(","));
        showError(
            "The following cross-profile types all share an identifier("
                + identifier
                + "): "
                + crossProfileTypesString,
            crossProfileTypeByIdentifier.get(identifier).get(0).crossProfileTypeElement());
      }
    }

    return isValid;
  }

  private static Collection<String> getConnectorQualifiedNamesUsedInProviderClass(
      ProviderClassInfo providerClass) {
    return providerClass.allCrossProfileTypes().stream()
        .map(CrossProfileTypeInfo::connectorInfo)
        .flatMap(Streams::stream)
        .map(ConnectorInfo::connectorElement)
        .map(Element::asType)
        .map(TypeMirror::toString)
        .collect(toSet());
  }

  private void showError(
      String errorText,
      Element errorElement,
      ValidationMessageFormatter validationMessageFormatter) {
    showErrorPreformatted(validationMessageFormatter.format(errorText), errorElement);
  }

  private void showError(String errorText, Element errorElement) {
    showErrorPreformatted(
        validationMessageFormatterFor(errorElement).format(errorText), errorElement);
  }

  private void showErrorPreformatted(String errorText, Element errorElement) {
    generatorContext
        .processingEnv()
        .getMessager()
        .printMessage(Kind.ERROR, errorText, errorElement);
  }
}
