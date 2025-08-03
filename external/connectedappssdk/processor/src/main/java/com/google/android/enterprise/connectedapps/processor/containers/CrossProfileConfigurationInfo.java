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

import com.google.android.enterprise.connectedapps.annotations.CrossProfileConfiguration;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.squareup.javapoet.ClassName;
import java.util.Optional;
import javax.lang.model.element.TypeElement;

/** Wrapper of a {@link CrossProfileConfiguration} annotated class. */
@AutoValue
public abstract class CrossProfileConfigurationInfo {

  public abstract TypeElement configurationElement();

  public abstract ImmutableCollection<ProviderClassInfo> providers();

  public abstract ClassName serviceSuperclass();

  public abstract Optional<TypeElement> serviceClass();

  public String simpleName() {
    return configurationElement().getSimpleName().toString();
  }

  public ClassName className() {
    return ClassName.get(configurationElement());
  }

  public abstract ConnectorInfo connectorInfo();

  public static CrossProfileConfigurationInfo create(
      ValidatorContext context, ValidatorCrossProfileConfigurationInfo configuration) {
    ImmutableSet.Builder<ProviderClassInfo> providerClassesBuilder = ImmutableSet.builder();
    configuration.providerClassElements().stream()
        .map(m -> ProviderClassInfo.create(context, ValidatorProviderClassInfo.create(context, m)))
        .forEach(providerClassesBuilder::add);
    ImmutableSet<ProviderClassInfo> providerClasses = providerClassesBuilder.build();

    ConnectorInfo connectorInfo =
        providerClasses.stream()
            .flatMap(m -> m.allCrossProfileTypes().stream())
            .map(CrossProfileTypeInfo::connectorInfo)
            .flatMap(Streams::stream)
            .findFirst()
            .orElseGet(() -> defaultConnector(context, configuration));

    return new AutoValue_CrossProfileConfigurationInfo(
        configuration.configurationElement(),
        providerClasses,
        configuration.serviceSuperclass(),
        configuration.serviceClass(),
        connectorInfo);
  }

  private static ConnectorInfo defaultConnector(
      ValidatorContext context, ValidatorCrossProfileConfigurationInfo configuration) {
    if (configuration.connector().isPresent()) {
      if (ConnectorInfo.isProfileConnector(context, configuration.connector().get())) {
        return ConnectorInfo.forProfileConnector(
            context, configuration.connector().get(), context.globalSupportedTypes());
      } else if (ConnectorInfo.isUserConnector(context, configuration.connector().get())) {
        return ConnectorInfo.forUserConnector(
            context, configuration.connector().get(), context.globalSupportedTypes());
      }
    }

    return ConnectorInfo.unspecified(context, context.globalSupportedTypes());
  }
}
