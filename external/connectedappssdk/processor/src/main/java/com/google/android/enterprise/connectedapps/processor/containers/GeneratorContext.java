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

import static java.util.stream.Collectors.toSet;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/** Context for connected apps code generators. */
@AutoValue
public abstract class GeneratorContext extends Context {

  public static GeneratorContext createFromValidatorContext(ValidatorContext validatorContext) {
    Collection<CrossProfileConfigurationInfo> configurations =
        validatorContext.newConfigurations().stream()
            .map(a -> CrossProfileConfigurationInfo.create(validatorContext, a))
            .collect(toSet());

    Collection<ProviderClassInfo> providers =
        validatorContext.newProviderClasses().stream()
            .map(m -> ProviderClassInfo.create(validatorContext, m))
            .collect(toSet());

    Collection<CrossProfileTypeInfo> crossProfileTypes =
        validatorContext.newCrossProfileTypes().stream()
            .map(m -> CrossProfileTypeInfo.create(validatorContext, m))
            .collect(toSet());

    Collection<CrossProfileCallbackInterfaceInfo> crossProfileCallbackInterfaces =
        validatorContext.newCrossProfileCallbackInterfaces().stream()
            .map(CrossProfileCallbackInterfaceInfo::create)
            .collect(toSet());

    Collection<ProfileConnectorInfo> generatedProfileConnectors =
        validatorContext.newGeneratedProfileConnectors().stream()
            .map(
                t ->
                    ProfileConnectorInfo.create(
                        validatorContext, t, validatorContext.globalSupportedTypes()))
            .collect(toSet());

    Collection<UserConnectorInfo> generatedUserConnectors =
        validatorContext.newGeneratedUserConnectors().stream()
            .map(
                t ->
                    UserConnectorInfo.create(
                        validatorContext, t, validatorContext.globalSupportedTypes()))
            .collect(toSet());

    Collection<CrossProfileTestInfo> crossProfileTests =
        validatorContext.newCrossProfileTests().stream()
            .map(t -> CrossProfileTestInfo.create(validatorContext, t))
            .collect(toSet());

    return GeneratorContext.builder()
        .setProcessingEnv(validatorContext.processingEnv())
        .setElements(validatorContext.elements())
        .setTypes(validatorContext.types())
        .setConfigurations(configurations)
        .setGeneratedProfileConnectors(generatedProfileConnectors)
        .setGeneratedUserConnectors(generatedUserConnectors)
        .setProviders(providers)
        .setCrossProfileTypes(crossProfileTypes)
        .setCrossProfileMethods(validatorContext.newCrossProfileMethods())
        .setCrossProfileCallbackInterfaces(crossProfileCallbackInterfaces)
        .setCrossProfileTests(crossProfileTests)
        .build();
  }

  static Builder builder() {
    return new AutoValue_GeneratorContext.Builder();
  }

  public abstract ImmutableSet<CrossProfileConfigurationInfo> configurations();

  public abstract ImmutableSet<ProfileConnectorInfo> generatedProfileConnectors();

  public abstract ImmutableSet<UserConnectorInfo> generatedUserConnectors();

  public abstract ImmutableSet<ProviderClassInfo> providers();

  public abstract ImmutableSet<CrossProfileTypeInfo> crossProfileTypes();

  public abstract ImmutableSet<ExecutableElement> crossProfileMethods();

  public abstract ImmutableSet<CrossProfileCallbackInterfaceInfo> crossProfileCallbackInterfaces();

  public abstract ImmutableSet<CrossProfileTestInfo> crossProfileTests();

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setProcessingEnv(ProcessingEnvironment processingEnv);

    abstract Builder setElements(Elements elements);

    abstract Builder setTypes(Types types);

    abstract Builder setConfigurations(Collection<CrossProfileConfigurationInfo> configurations);

    abstract Builder setGeneratedProfileConnectors(
        Collection<ProfileConnectorInfo> generatedProfileConnectors);

    abstract Builder setGeneratedUserConnectors(
        Collection<UserConnectorInfo> generatedUserConnectors);

    abstract Builder setProviders(Collection<ProviderClassInfo> providers);

    abstract Builder setCrossProfileTypes(Collection<CrossProfileTypeInfo> crossProfileTypes);

    abstract Builder setCrossProfileMethods(Collection<ExecutableElement> crossProfileMethods);

    abstract Builder setCrossProfileCallbackInterfaces(
        Collection<CrossProfileCallbackInterfaceInfo> crossProfileCallbackInterfaces);

    abstract Builder setCrossProfileTests(Collection<CrossProfileTestInfo> crossProfileTests);

    abstract GeneratorContext build();
  }
}
