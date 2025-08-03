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

import static com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationFinder.elementsAnnotatedWithCrossProfile;
import static com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationFinder.elementsAnnotatedWithCrossProfileCallback;
import static com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationFinder.elementsAnnotatedWithCrossProfileConfiguration;
import static com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationFinder.elementsAnnotatedWithCrossProfileConfigurations;
import static com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationFinder.elementsAnnotatedWithCrossProfileProvider;
import static com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationFinder.elementsAnnotatedWithCrossProfileTest;
import static com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationFinder.hasCrossProfileProviderAnnotation;
import static java.util.stream.Collectors.toSet;

import com.google.android.enterprise.connectedapps.annotations.CustomFutureWrapper;
import com.google.android.enterprise.connectedapps.annotations.CustomParcelableWrapper;
import com.google.android.enterprise.connectedapps.annotations.CustomProfileConnector;
import com.google.android.enterprise.connectedapps.annotations.CustomUserConnector;
import com.google.android.enterprise.connectedapps.annotations.GeneratedProfileConnector;
import com.google.android.enterprise.connectedapps.annotations.GeneratedUserConnector;
import com.google.android.enterprise.connectedapps.processor.containers.Context;
import com.google.android.enterprise.connectedapps.processor.containers.FutureWrapper;
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.google.android.enterprise.connectedapps.processor.containers.ParcelableWrapper;
import com.google.android.enterprise.connectedapps.processor.containers.PreValidatorContext;
import com.google.android.enterprise.connectedapps.processor.containers.ProfileConnectorInfo;
import com.google.android.enterprise.connectedapps.processor.containers.UserConnectorInfo;
import com.google.android.enterprise.connectedapps.processor.containers.ValidatorContext;
import com.google.android.enterprise.connectedapps.processor.containers.ValidatorCrossProfileConfigurationInfo;
import com.google.android.enterprise.connectedapps.processor.containers.ValidatorCrossProfileTestInfo;
import com.google.android.enterprise.connectedapps.processor.containers.ValidatorCrossProfileTypeInfo;
import com.google.android.enterprise.connectedapps.processor.containers.ValidatorProviderClassInfo;
import com.google.auto.service.AutoService;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/** Processor for generation of cross-profile code. */
@SupportedAnnotationTypes({
  "com.google.android.enterprise.connectedapps.annotations.Cacheable",
  "com.google.android.enterprise.connectedapps.annotations.CrossProfile",
  "com.google.android.enterprise.connectedapps.annotations.CrossProfileCallback",
  "com.google.android.enterprise.connectedapps.annotations.CrossProfileConfiguration",
  "com.google.android.enterprise.connectedapps.annotations.CrossProfileConfigurations",
  "com.google.android.enterprise.connectedapps.annotations.CrossProfileProvider",
  "com.google.android.enterprise.connectedapps.testing.annotations.CrossProfileTest",
  "com.google.android.enterprise.connectedapps.annotations.CustomProfileConnector",
  "com.google.android.enterprise.connectedapps.annotations.GeneratedProfileConnector",
  "com.google.android.enterprise.connectedapps.annotations.CrossUser",
  "com.google.android.enterprise.connectedapps.annotations.CrossUserCallback",
  "com.google.android.enterprise.connectedapps.annotations.CrossUserConfiguration",
  "com.google.android.enterprise.connectedapps.annotations.CrossUserConfigurations",
  "com.google.android.enterprise.connectedapps.annotations.CrossUserProvider",
  "com.google.android.enterprise.connectedapps.testing.annotations.CrossUserTest",
  "com.google.android.enterprise.connectedapps.annotations.CustomUserConnector",
  "com.google.android.enterprise.connectedapps.annotations.GeneratedUserConnector",
  "com.google.android.enterprise.connectedapps.annotations.CustomParcelableWrapper",
  "com.google.android.enterprise.connectedapps.annotations.CustomFutureWrapper"
})
@AutoService(javax.annotation.processing.Processor.class)
public final class Processor extends AbstractProcessor {

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latest();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    PreValidatorContext preValidatorContext = new PreValidatorContext(processingEnv);

    Collection<ValidatorCrossProfileTestInfo> newCrossProfileTests =
        findNewCrossProfileTests(preValidatorContext, roundEnv);
    // Only new configurations need code generating - but we need to support types used by methods
    // included in configurations under test
    Collection<ValidatorCrossProfileConfigurationInfo> newConfigurations =
        findNewConfigurations(preValidatorContext, roundEnv);
    Collection<ValidatorCrossProfileConfigurationInfo> allConfigurations =
        findAllConfigurations(preValidatorContext, newConfigurations, newCrossProfileTests);

    Collection<ValidatorProviderClassInfo> newProviderClasses =
        findNewProviderClasses(preValidatorContext, roundEnv);
    Collection<ExecutableElement> newProviderMethods = findNewProviderMethods(roundEnv);
    Collection<TypeElement> newGeneratedConnectors = findNewGeneratedConnectors(roundEnv);
    Collection<TypeElement> newGeneratedUserConnectors = findNewGeneratedUserConnectors(roundEnv);
    Collection<ExecutableElement> newCrossProfileMethods = findNewCrossProfileMethods(roundEnv);
    Collection<ExecutableElement> allCrossProfileMethods =
        findAllCrossProfileMethods(
            preValidatorContext,
            newCrossProfileMethods,
            allConfigurations,
            newProviderMethods,
            newProviderClasses);
    Collection<TypeElement> newCrossProfileCallbackInterfaces =
        findNewCrossProfileCallbackInterfaces(roundEnv);

    Collection<ExecutableElement> methods = new HashSet<>(allCrossProfileMethods);
    methods.addAll(
        newCrossProfileCallbackInterfaces.stream()
            .flatMap(i -> i.getEnclosedElements().stream())
            .filter(e -> e instanceof ExecutableElement)
            .map(e -> (ExecutableElement) e)
            .filter(e -> e.getKind() == ElementKind.METHOD)
            .collect(toSet()));

    Collection<TypeElement> newCustomParcelableWrappers = findNewParcelableWrappers(roundEnv);
    Collection<TypeElement> newCustomFutureWrappers = findNewFutureWrappers(roundEnv);

    Collection<FutureWrapper> globalFutureWrappers =
        FutureWrapper.createGlobalFutureWrappers(preValidatorContext);
    Collection<ParcelableWrapper> globalParcelableWrappers =
        ParcelableWrapper.createGlobalParcelableWrappers(preValidatorContext, methods);

    SupportedTypes globalSupportedTypes =
        SupportedTypes.createFromMethods(
            preValidatorContext, globalParcelableWrappers, globalFutureWrappers, methods);

    Collection<ValidatorCrossProfileTypeInfo> newCrossProfileTypes =
        findNewCrossProfileTypes(preValidatorContext, roundEnv, globalSupportedTypes);
    Collection<ProfileConnectorInfo> newProfileConnectorInterfaces =
        findNewProfileConnectorInterfaces(preValidatorContext, roundEnv, globalSupportedTypes);
    Collection<UserConnectorInfo> newUserConnectorInterfaces =
        findNewUserConnectorInterfaces(preValidatorContext, roundEnv, globalSupportedTypes);

    ValidatorContext validatorContext =
        ValidatorContext.builderFromPreValidatorContext(preValidatorContext)
            .setGlobalSupportedTypes(globalSupportedTypes)
            .setNewProfileConnectorInterfaces(newProfileConnectorInterfaces)
            .setNewUserConnectorInterfaces(newUserConnectorInterfaces)
            .setNewGeneratedProfileConnectors(newGeneratedConnectors)
            .setNewGeneratedUserConnectors(newGeneratedUserConnectors)
            .setNewConfigurations(newConfigurations)
            .setNewCrossProfileTypes(newCrossProfileTypes)
            .setNewCrossProfileMethods(newCrossProfileMethods)
            .setNewProviderClasses(newProviderClasses)
            .setNewProviderMethods(newProviderMethods)
            .setNewCrossProfileCallbackInterfaces(newCrossProfileCallbackInterfaces)
            .setNewCrossProfileTests(newCrossProfileTests)
            .setNewCustomParcelableWrappers(newCustomParcelableWrappers)
            .setNewCustomFutureWrappers(newCustomFutureWrappers)
            .build();

    boolean isValid = new EarlyValidator(validatorContext).validate();

    if (!isValid) {
      return false;
    }

    GeneratorContext generatorContext =
        GeneratorContext.createFromValidatorContext(validatorContext);

    isValid = new LateValidator(generatorContext).validate();

    if (!isValid) {
      return false;
    }

    new CodeGenerator(generatorContext).generate();

    return false;
  }

  private Collection<ValidatorCrossProfileConfigurationInfo> findNewConfigurations(
      Context context, RoundEnvironment roundEnv) {
    Set<ValidatorCrossProfileConfigurationInfo> annotations = new HashSet<>();

    elementsAnnotatedWithCrossProfileConfiguration(roundEnv)
        .map(
            element ->
                ValidatorCrossProfileConfigurationInfo.createFromElement(
                    context, (TypeElement) element))
        .forEach(annotations::add);

    elementsAnnotatedWithCrossProfileConfigurations(roundEnv)
        .map(
            element ->
                ValidatorCrossProfileConfigurationInfo.createMultipleFromElement(
                    context, (TypeElement) element))
        .forEach(annotations::addAll);

    return annotations;
  }

  private Collection<ValidatorCrossProfileConfigurationInfo> findAllConfigurations(
      Context context,
      Collection<ValidatorCrossProfileConfigurationInfo> newConfigurations,
      Collection<ValidatorCrossProfileTestInfo> crossProfileTests) {
    Set<ValidatorCrossProfileConfigurationInfo> allConfigurations = new HashSet<>();
    allConfigurations.addAll(newConfigurations);
    allConfigurations.addAll(
        crossProfileTests.stream()
            .flatMap(
                t ->
                    ValidatorCrossProfileConfigurationInfo.createMultipleFromElement(
                        context, t.configurationElement())
                        .stream())
            .collect(toSet()));
    return allConfigurations;
  }

  private Collection<ValidatorProviderClassInfo> findNewProviderClasses(
      Context context, RoundEnvironment roundEnv) {
    Set<ValidatorProviderClassInfo> annotatedClasses =
        elementsAnnotatedWithCrossProfileProvider(roundEnv)
            .filter(m -> m instanceof TypeElement)
            .map(m -> (TypeElement) m)
            .map(m -> ValidatorProviderClassInfo.create(context, m))
            .collect(toSet());

    Set<ValidatorProviderClassInfo> unannotatedClasses =
        elementsAnnotatedWithCrossProfileProvider(roundEnv)
            .filter(m -> m instanceof ExecutableElement)
            .map(m -> (ExecutableElement) m)
            .map(Element::getEnclosingElement)
            .map(m -> (TypeElement) m)
            .filter(m -> !hasCrossProfileProviderAnnotation(m))
            .map(m -> ValidatorProviderClassInfo.create(context, m))
            .collect(toSet());

    Collection<ValidatorProviderClassInfo> allProviders = new HashSet<>();
    allProviders.addAll(annotatedClasses);
    allProviders.addAll(unannotatedClasses);
    return allProviders;
  }

  private Collection<ExecutableElement> findNewProviderMethods(RoundEnvironment roundEnv) {
    return elementsAnnotatedWithCrossProfileProvider(roundEnv)
        .filter(m -> m instanceof ExecutableElement)
        .map(m -> (ExecutableElement) m)
        .collect(toSet());
  }

  private Collection<ValidatorCrossProfileTypeInfo> findNewCrossProfileTypes(
      Context context, RoundEnvironment roundEnv, SupportedTypes globalSupportedTypes) {
    Collection<ValidatorCrossProfileTypeInfo> annotatedTypes =
        elementsAnnotatedWithCrossProfile(roundEnv)
            .filter(m -> m instanceof TypeElement)
            .map(m -> (TypeElement) m)
            .map(m -> ValidatorCrossProfileTypeInfo.create(context, m, globalSupportedTypes))
            .collect(toSet());

    Collection<ValidatorCrossProfileTypeInfo> unannotatedTypes =
        elementsAnnotatedWithCrossProfile(roundEnv)
            .filter(m -> m instanceof ExecutableElement)
            .map(m -> (ExecutableElement) m)
            .map(ExecutableElement::getEnclosingElement)
            .filter(m -> m instanceof TypeElement)
            .map(m -> (TypeElement) m)
            .map(m -> ValidatorCrossProfileTypeInfo.create(context, m, globalSupportedTypes))
            .collect(toSet());

    Collection<ValidatorCrossProfileTypeInfo> allTypes = new HashSet<>();
    allTypes.addAll(annotatedTypes);
    allTypes.addAll(unannotatedTypes);
    return allTypes;
  }

  private Collection<ExecutableElement> findNewCrossProfileMethods(RoundEnvironment roundEnv) {
    return elementsAnnotatedWithCrossProfile(roundEnv)
        .filter(m -> m instanceof ExecutableElement)
        .map(m -> (ExecutableElement) m)
        .collect(toSet());
  }

  private Collection<ProfileConnectorInfo> findNewProfileConnectorInterfaces(
      Context context, RoundEnvironment roundEnv, SupportedTypes globalSupportedTypes) {
    Collection<TypeElement> connectorInterfaces =
        roundEnv.getElementsAnnotatedWith(CustomProfileConnector.class).stream()
            .map(m -> (TypeElement) m)
            .collect(toSet());

    // We manually add the SDK-provided CrossProfileConnector as it won't be detected by roundEnv
    connectorInterfaces.add(
        processingEnv
            .getElementUtils()
            .getTypeElement("com.google.android.enterprise.connectedapps.CrossProfileConnector"));

    return connectorInterfaces.stream()
        .map(t -> ProfileConnectorInfo.create(context, t, globalSupportedTypes))
        .collect(Collectors.toSet());
  }

  private Collection<UserConnectorInfo> findNewUserConnectorInterfaces(
      Context context, RoundEnvironment roundEnv, SupportedTypes globalSupportedTypes) {
    Collection<TypeElement> connectorInterfaces =
        roundEnv.getElementsAnnotatedWith(CustomUserConnector.class).stream()
            .map(m -> (TypeElement) m)
            .collect(toSet());

    // We manually add the SDK-provided CrossUserConnector as it won't be detected by roundEnv
    connectorInterfaces.add(
        processingEnv
            .getElementUtils()
            .getTypeElement("com.google.android.enterprise.connectedapps.CrossUserConnector"));

    return connectorInterfaces.stream()
        .map(t -> UserConnectorInfo.create(context, t, globalSupportedTypes))
        .collect(Collectors.toSet());
  }

  private Collection<TypeElement> findNewGeneratedConnectors(RoundEnvironment roundEnv) {
    Collection<TypeElement> connectorInterfaces =
        roundEnv.getElementsAnnotatedWith(GeneratedProfileConnector.class).stream()
            .map(m -> (TypeElement) m)
            .collect(toSet());

    return connectorInterfaces;
  }

  private Collection<TypeElement> findNewGeneratedUserConnectors(RoundEnvironment roundEnv) {
    Collection<TypeElement> connectorInterfaces =
        roundEnv.getElementsAnnotatedWith(GeneratedUserConnector.class).stream()
            .map(m -> (TypeElement) m)
            .collect(toSet());

    return connectorInterfaces;
  }

  private static Collection<ExecutableElement> findAllCrossProfileMethods(
      Context context,
      Collection<ExecutableElement> newCrossProfileMethods,
      Collection<ValidatorCrossProfileConfigurationInfo> configurations,
      Collection<ExecutableElement> newProviderMethods,
      Collection<ValidatorProviderClassInfo> newProviderClasses) {
    Collection<ExecutableElement> allCrossProfileMethods = new HashSet<>(newCrossProfileMethods);

    Collection<ValidatorProviderClassInfo> foundProviderClasses =
        configurations.stream()
            .flatMap(a -> a.providerClassElements().stream())
            .map(m -> ValidatorProviderClassInfo.create(context, m))
            .collect(toSet());

    Collection<ExecutableElement> providerMethods =
        foundProviderClasses.stream()
            .flatMap(
                m ->
                    GeneratorUtilities.findCrossProfileProviderMethodsInClass(
                        m.providerClassElement())
                        .stream())
            .collect(toSet());

    providerMethods.addAll(newProviderMethods);

    Collection<TypeElement> crossProfileTypes =
        providerMethods.stream()
            .map(e -> context.elements().getTypeElement(e.getReturnType().toString()))
            .filter(Objects::nonNull)
            .collect(toSet());
    crossProfileTypes.addAll(
        foundProviderClasses.stream().flatMap(m -> m.staticTypes().stream()).collect(toSet()));
    crossProfileTypes.addAll(
        newProviderClasses.stream().flatMap(m -> m.staticTypes().stream()).collect(toSet()));

    Collection<ExecutableElement> foundCrossProfileMethods =
        crossProfileTypes.stream()
            .flatMap(t -> GeneratorUtilities.findCrossProfileMethodsInClass(t).stream())
            .collect(toSet());

    allCrossProfileMethods.addAll(foundCrossProfileMethods);
    return allCrossProfileMethods;
  }

  private Collection<ValidatorCrossProfileTestInfo> findNewCrossProfileTests(
      Context context, RoundEnvironment roundEnv) {
    return elementsAnnotatedWithCrossProfileTest(roundEnv)
        .map(e -> (TypeElement) e)
        .map(e -> ValidatorCrossProfileTestInfo.create(context, e))
        .collect(toSet());
  }

  private Collection<TypeElement> findNewCrossProfileCallbackInterfaces(RoundEnvironment roundEnv) {
    return elementsAnnotatedWithCrossProfileCallback(roundEnv)
        .map(m -> (TypeElement) m)
        .collect(toSet());
  }

  private Collection<TypeElement> findNewParcelableWrappers(RoundEnvironment roundEnv) {
    return roundEnv.getElementsAnnotatedWith(CustomParcelableWrapper.class).stream()
        .map(m -> (TypeElement) m)
        .collect(toSet());
  }

  private Collection<TypeElement> findNewFutureWrappers(RoundEnvironment roundEnv) {
    return roundEnv.getElementsAnnotatedWith(CustomFutureWrapper.class).stream()
        .map(m -> (TypeElement) m)
        .collect(toSet());
  }
}
