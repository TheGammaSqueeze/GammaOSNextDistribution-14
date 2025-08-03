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

import com.google.android.enterprise.connectedapps.processor.SupportedTypes;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Context for connected apps code validators.
 *
 * <p>This is used to validate enough that a {@link GeneratorContext} can be created for further
 * validation and generation.
 */
@AutoValue
public abstract class ValidatorContext extends Context {

  public static Builder builderFromPreValidatorContext(PreValidatorContext preValidatorContext) {
    return new AutoValue_ValidatorContext.Builder()
        .setProcessingEnv(preValidatorContext.processingEnv())
        .setElements(preValidatorContext.elements())
        .setTypes(preValidatorContext.types());
  }

  public abstract SupportedTypes globalSupportedTypes();

  public abstract ImmutableSet<ProfileConnectorInfo> newProfileConnectorInterfaces();

  public abstract ImmutableSet<UserConnectorInfo> newUserConnectorInterfaces();

  public abstract ImmutableSet<TypeElement> newGeneratedProfileConnectors();

  public abstract ImmutableSet<TypeElement> newGeneratedUserConnectors();

  public abstract ImmutableSet<ValidatorCrossProfileConfigurationInfo> newConfigurations();

  public abstract ImmutableSet<ValidatorCrossProfileTypeInfo> newCrossProfileTypes();

  public abstract ImmutableSet<ExecutableElement> newCrossProfileMethods();

  public abstract ImmutableSet<ValidatorProviderClassInfo> newProviderClasses();

  public abstract ImmutableSet<ExecutableElement> newProviderMethods();

  public abstract ImmutableSet<TypeElement> newCrossProfileCallbackInterfaces();

  public abstract ImmutableSet<ValidatorCrossProfileTestInfo> newCrossProfileTests();

  public abstract ImmutableSet<TypeElement> newCustomParcelableWrappers();

  public abstract ImmutableSet<TypeElement> newCustomFutureWrappers();

  /** A builder for {@link ValidatorContext}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setProcessingEnv(ProcessingEnvironment processingEnv);

    public abstract Builder setElements(Elements elements);

    public abstract Builder setTypes(Types types);

    public abstract Builder setGlobalSupportedTypes(SupportedTypes globalSupportedTypes);

    public abstract Builder setNewProfileConnectorInterfaces(
        Collection<ProfileConnectorInfo> newProfileConnectorInterfaces);

    public abstract Builder setNewUserConnectorInterfaces(
        Collection<UserConnectorInfo> newUserConnectorInterfaces);

    public abstract Builder setNewGeneratedProfileConnectors(
        Collection<TypeElement> newGeneratedConnectors);

    public abstract Builder setNewGeneratedUserConnectors(
        Collection<TypeElement> newGeneratedUserConnectors);

    public abstract Builder setNewConfigurations(
        Collection<ValidatorCrossProfileConfigurationInfo> newConfigurations);

    public abstract Builder setNewCrossProfileTypes(
        Collection<ValidatorCrossProfileTypeInfo> newCrossProfileTypes);

    public abstract Builder setNewCrossProfileMethods(
        Collection<ExecutableElement> newCrossProfileMethods);

    public abstract Builder setNewProviderClasses(
        Collection<ValidatorProviderClassInfo> newProviderClasses);

    public abstract Builder setNewProviderMethods(Collection<ExecutableElement> newProviderMethods);

    public abstract Builder setNewCrossProfileCallbackInterfaces(
        Collection<TypeElement> newCrossProfileCallbackInterfaces);

    public abstract Builder setNewCrossProfileTests(
        Collection<ValidatorCrossProfileTestInfo> newCrossProfileTests);

    public abstract Builder setNewCustomParcelableWrappers(
        Collection<TypeElement> newCustomParcelableWrappers);

    public abstract Builder setNewCustomFutureWrappers(
        Collection<TypeElement> newCustomFutureWrappers);

    public abstract ValidatorContext build();
  }
}
