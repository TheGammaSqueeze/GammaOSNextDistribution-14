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

import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.PARCELABLE_CREATOR_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.GeneratorUtilities.findCrossProfileMethodsInClass;
import static com.google.android.enterprise.connectedapps.processor.TypeUtils.extractTypeArguments;
import static com.google.android.enterprise.connectedapps.processor.TypeUtils.getRawTypeClassName;
import static com.google.android.enterprise.connectedapps.processor.TypeUtils.getRawTypeQualifiedName;
import static com.google.android.enterprise.connectedapps.processor.TypeUtils.removeTypeArguments;
import static com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationFinder.hasCrossProfileAnnotation;
import static com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationFinder.hasCrossProfileConfigurationAnnotation;
import static com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationFinder.hasCrossProfileConfigurationsAnnotation;
import static com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationFinder.hasCrossProfileProviderAnnotation;
import static com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationFinder.validationMessageFormatterFor;
import static com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationFinder.validationMessageFormatterForClass;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import com.google.android.enterprise.connectedapps.annotations.Cacheable;
import com.google.android.enterprise.connectedapps.annotations.CustomFutureWrapper;
import com.google.android.enterprise.connectedapps.annotations.CustomParcelableWrapper;
import com.google.android.enterprise.connectedapps.annotations.CustomProfileConnector;
import com.google.android.enterprise.connectedapps.annotations.CustomUserConnector;
import com.google.android.enterprise.connectedapps.processor.SupportedTypes.TypeCheckContext;
import com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationFinder;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileAnnotationInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileCallbackAnnotationInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileMethodInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileProviderAnnotationInfo;
import com.google.android.enterprise.connectedapps.processor.containers.FutureWrapperAnnotationInfo;
import com.google.android.enterprise.connectedapps.processor.containers.ParcelableWrapperAnnotationInfo;
import com.google.android.enterprise.connectedapps.processor.containers.ProfileConnectorInfo;
import com.google.android.enterprise.connectedapps.processor.containers.UserConnectorInfo;
import com.google.android.enterprise.connectedapps.processor.containers.ValidatorContext;
import com.google.android.enterprise.connectedapps.processor.containers.ValidatorCrossProfileConfigurationInfo;
import com.google.android.enterprise.connectedapps.processor.containers.ValidatorCrossProfileTestInfo;
import com.google.android.enterprise.connectedapps.processor.containers.ValidatorCrossProfileTypeInfo;
import com.google.android.enterprise.connectedapps.processor.containers.ValidatorProviderClassInfo;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic.Kind;

/** Validator to check that annotations have been used correctly before generating code. */
public final class EarlyValidator {

  private static final String MULTIPLE_PROVIDERS_ERROR =
      "The @CROSS_PROFILE_ANNOTATION annotated type %s has been provided more than once";
  private static final String PROVIDING_NON_CROSS_PROFILE_TYPE_ERROR =
      "Methods annotated @CROSS_PROFILE_PROVIDER_ANNOTATION must only return"
          + " @CROSS_PROFILE_ANNOTATION annotated types";
  private static final String INVALID_CONSTRUCTORS_ERROR =
      "Provider classes must have a single public constructor which takes either a single Context"
          + " argument or no arguments";
  private static final String PROVIDER_INCORRECT_ARGS_ERROR =
      "Methods annotated @CROSS_PROFILE_PROVIDER_ANNOTATION can only take a single Context"
          + " argument, or no-args";
  private static final String STATIC_PROVIDER_ERROR =
      "Methods annotated @CROSS_PROFILE_PROVIDER_ANNOTATION can not be static";
  private static final String UNSUPPORTED_RETURN_TYPE_ERROR =
      "The type %s cannot be returned by methods annotated @CROSS_PROFILE_ANNOTATION";
  private static final String UNSUPPORTED_PARAMETER_TYPE_CROSS_PROFILE_METHOD =
      "The type %s cannot be used by parameters of methods annotated @CROSS_PROFILE_ANNOTATION";
  private static final String UNSUPPORTED_PARAMETER_TYPE_CROSS_ASYNC_CALLBACK =
      "The type %s cannot be used by parameters of methods on interfaces annotated"
          + " @CROSS_PROFILE_CALLBACK_ANNOTATION";
  private static final String CROSS_PROFILE_TYPE_DEFAULT_PACKAGE_ERROR =
      "@CROSS_PROFILE_ANNOTATION types must not be in the default package";
  private static final String NON_PUBLIC_CROSS_PROFILE_TYPE_ERROR =
      "@CROSS_PROFILE_ANNOTATION types must be public";
  private static final String NOT_A_PROVIDER_CLASS_ERROR =
      "All classes specified in 'providers' must be provider classes";
  private static final String CONNECTOR_MUST_BE_INTERFACE = "Connectors must be interfaces";
  private static final String CONNECTOR_MUST_EXTEND_CONNECTOR =
      "Interfaces specified as a connector must extend ProfileConnector or UserConnector";
  private static final String CUSTOM_PROFILE_CONNECTOR_MUST_BE_INTERFACE =
      "@CustomProfileConnector must only be applied to interfaces";
  private static final String CUSTOM_USER_CONNECTOR_MUST_BE_INTERFACE =
      "@CustomUserConnector must only be applied to interfaces";
  private static final String GENERATED_PROFILE_CONNECTOR_MUST_BE_INTERFACE =
      "@GeneratedProfileConnector must only be applied to interfaces";
  private static final String GENERATED_USER_CONNECTOR_MUST_BE_INTERFACE =
      "@GeneratedUserConnector must only be applied to interfaces";
  private static final String CUSTOM_PROFILE_CONNECTOR_MUST_EXTEND_CONNECTOR =
      "Interfaces annotated with @CustomProfileConnector must extend ProfileConnector";
  private static final String CUSTOM_USER_CONNECTOR_MUST_EXTEND_CONNECTOR =
      "Interfaces annotated with @CustomUserConnector must extend UserConnector";
  private static final String GENERATED_PROFILE_CONNECTOR_MUST_EXTEND_PROFILE_CONNECTOR =
      "Interfaces annotated with @GeneratedProfileConnector must extend ProfileConnector";
  private static final String GENERATED_USER_CONNECTOR_MUST_EXTEND_USER_CONNECTOR =
      "Interfaces annotated with @GeneratedUserConnector must extend UserConnector";
  private static final String CALLBACK_INTERFACE_DEFAULT_PACKAGE_ERROR =
      "Interfaces annotated @CROSS_PROFILE_CALLBACK_ANNOTATION must not be in the default package";
  private static final String NOT_INTERFACE_ERROR =
      "Only interfaces may be annotated @CROSS_PROFILE_CALLBACK_ANNOTATION";
  private static final String NOT_ONE_METHOD_ERROR =
      "Interfaces annotated @CROSS_PROFILE_CALLBACK_ANNOTATION(simple=true) must have exactly one"
          + " method";
  private static final String NO_METHODS_ERROR =
      "Interfaces annotated @CROSS_PROFILE_CALLBACK_ANNOTATION must have at least one method";
  private static final String DEFAULT_METHOD_ERROR =
      "Interfaces annotated @CROSS_PROFILE_CALLBACK_ANNOTATION must have no default methods";
  private static final String STATIC_METHOD_ERROR =
      "Interfaces annotated @CROSS_PROFILE_CALLBACK_ANNOTATION must have no static methods";
  private static final String NOT_VOID_ERROR =
      "Methods on interfaces annotated @CROSS_PROFILE_CALLBACK_ANNOTATION must return void";
  private static final String GENERIC_CALLBACK_INTERFACE_ERROR =
      "Interfaces annotated @CROSS_PROFILE_CALLBACK_ANNOTATION can not be generic";
  private static final String MORE_THAN_ONE_PARAMETER_ERROR =
      "Methods on interfaces annotated @CROSS_PROFILE_CALLBACK_ANNOTATION(simple=true) can only"
          + " take a single parameter";
  private static final String MULTIPLE_ASYNC_CALLBACK_PARAMETERS_ERROR =
      "Methods annotated @CROSS_PROFILE_ANNOTATION can have a maximum of one parameter of a type"
          + " annotated @CROSS_PROFILE_CALLBACK_ANNOTATION";
  private static final String NON_VOID_CALLBACK_ERROR =
      "Methods annotated @CROSS_PROFILE_ANNOTATION which take a parameter type annotated"
          + " @CROSS_PROFILE_CALLBACK_ANNOTATION must return void";
  private static final String METHOD_ISSTATIC_ERROR =
      "@CROSS_PROFILE_ANNOTATION annotations on methods can not specify isStatic";
  private static final String METHOD_CONNECTOR_ERROR =
      "@CROSS_PROFILE_ANNOTATION annotations on methods can not specify a connector";
  private static final String METHOD_PARCELABLE_WRAPPERS_ERROR =
      "@CROSS_PROFILE_ANNOTATION annotations on methods can not specify parcelable wrappers";
  private static final String ADDITIONAL_PROFILE_CONNECTOR_METHODS_ERROR =
      "Interfaces annotated with @GeneratedProfileConnector can not declare non-static methods";
  private static final String ADDITIONAL_USER_CONNECTOR_METHODS_ERROR =
      "Interfaces annotated with @GeneratedUserConnector can not declare non-static methods";
  private static final String NOT_A_CONFIGURATION_ERROR =
      "Configurations referenced in a @CROSS_PROFILE_TEST_ANNOTATION annotation must be annotated"
          + " @CROSS_PROFILE_CONFIGURATION_ANNOTATION or @CROSS_PROFILE_CONFIGURATIONS_ANNOTATION";
  private static final String ASYNC_DECLARED_EXCEPTION_ERROR =
      "Asynchronous methods annotated @CROSS_PROFILE_ANNOTATION cannot declare exceptions";
  private static final String NOT_PARCELABLE_ERROR =
      "Classes annotated @CustomParcelableWrapper must implement Parcelable";
  private static final String INCORRECT_OF_METHOD =
      "Classes annotated @CustomParcelableWrapper must have a static 'of' method which takes a"
          + " Bundler, a BundlerType, and an instance of the wrapped type as arguments and returns"
          + " an instance of the parcelable wrapper";
  private static final String INCORRECT_GET_METHOD =
      "Classes annotated @CustomParcelableWrapper must have a static 'get' method which takes no"
          + " arguments and returns an instance of the wrapped class";
  private static final String INCORRECT_PARCELABLE_IMPLEMENTATION =
      "Classes annotated @CustomParcelableWrapper must correctly implement Parcelable";
  private static final String PARCELABLE_WRAPPER_ANNOTATION_ERROR =
      "Parcelable Wrappers must be annotated @CustomParcelableWrapper";
  private static final String DOES_NOT_EXTEND_FUTURE_WRAPPER_ERROR =
      "Classes annotated @CustomFutureWrapper must extend FutureWrapper";
  private static final String INCORRECT_CREATE_METHOD_ERROR =
      "Classes annotated @CustomFutureWrapper must have a create method which returns an instance"
          + " of the class and takes a Bundler and BundlerType argument";
  private static final String INCORRECT_GET_FUTURE_METHOD_ERROR =
      "Classes annotated @CustomFutureWrapper must have a getFuture method which returns an"
          + " instance of the wrapped future and takes no arguments";
  private static final String INCORRECT_RESOLVE_CALLBACK_WHEN_FUTURE_IS_SET_METHOD_ERROR =
      "Classes annotated @CustomFutureWrapper must have a writeFutureResult method"
          + " which returns void and takes as arguments an instance of the wrapped future and a"
          + " FutureResultWriter";
  private static final String INCORRECT_GROUP_RESULTS_METHOD_ERROR =
      "Classes annotated @CustomFutureWrapper must have a groupResults method which returns an"
          + " instance of the wrapped future containing a map from Profile to the wrapped future"
          + " type, and takes as an argument a map from Profile to an instance of the wrapped"
          + " future";
  private static final String FUTURE_WRAPPER_ANNOTATION_ERROR =
      "Future Wrappers must be annotated @CustomFutureWrapper";
  private static final String IMPORTS_NOT_PROFILE_CONNECTOR_ERROR =
      "Classes included in includes= must be annotated @CustomProfileConnector";
  private static final String IMPORTS_NOT_USER_CONNECTOR_ERROR =
      "Classes included in includes= must be annotated @CustomUserConnector";
  private static final String MUST_HAVE_ONE_TYPE_PARAMETER_ERROR =
      "Classes annotated @CustomFutureWrapper must have a single type parameter";
  private static final String NOT_STATIC_ERROR =
      "Types annotated @CROSS_PROFILE_ANNOTATION(isStatic=true) must not contain any non-static"
          + " methods annotated @CROSS_PROFILE_ANNOTATION";
  private static final String METHOD_STATICTYPES_ERROR =
      "@CROSS_PROFILE_PROVIDER_ANNOTATION annotations on methods can not specify staticTypes";
  private static final String CACHEABLE_METHOD_RETURNS_VOID_ERROR =
      "Methods annotated with @Cacheable must return a non-void type";
  private static final String CACHEABLE_METHOD_RETURNS_NON_SERIALIZABLE_ERROR =
      "Methods annotated with @Cacheable must return a type which implements Serializable, return"
          + " a future with a Serializable result or return void with a simple callback parameter.";
  private static final String CACHEABLE_METHOD_NON_SIMPLE_CALLBACK_ERROR =
      "Methods annotated with @Cacheable may only have a callback parameter which is simple.";
  private static final String CACHEABLE_METHOD_USES_INVALID_PARAMETERS_ERROR =
      "Methods annotated with @Cacheable may only use callbacks that take a single Serializable"
          + " parameter.";

  private final ValidatorContext validatorContext;
  private final TypeMirror contextType;
  private final TypeMirror profileConnectorType;
  private final TypeMirror userConnectorType;
  private final TypeMirror parcelableType;
  private final TypeMirror bundlerType;
  private final TypeMirror bundlerTypeType;
  private final TypeMirror futureResultWriterType;
  private final TypeMirror profileType;

  EarlyValidator(ValidatorContext validatorContext) {
    this.validatorContext = validatorContext;
    contextType = validatorContext.elements().getTypeElement("android.content.Context").asType();

    parcelableType = validatorContext.elements().getTypeElement("android.os.Parcelable").asType();

    profileConnectorType =
        validatorContext
            .elements()
            .getTypeElement("com.google.android.enterprise.connectedapps.ProfileConnector")
            .asType();

    userConnectorType =
        validatorContext
            .elements()
            .getTypeElement("com.google.android.enterprise.connectedapps.UserConnector")
            .asType();

    bundlerType =
        validatorContext
            .elements()
            .getTypeElement("com.google.android.enterprise.connectedapps.internal.Bundler")
            .asType();

    bundlerTypeType =
        validatorContext
            .elements()
            .getTypeElement("com.google.android.enterprise.connectedapps.internal.BundlerType")
            .asType();

    futureResultWriterType =
        validatorContext
            .elements()
            .getTypeElement(
                "com.google.android.enterprise.connectedapps.internal.FutureResultWriter")
            .asType();

    profileType =
        validatorContext
            .elements()
            .getTypeElement("com.google.android.enterprise.connectedapps.Profile")
            .asType();
  }

  /**
   * Validate code.
   *
   * <p>This will show errors for all issues found. It will not terminate upon finding the first
   * error.
   *
   * @return True if the code is valid
   */
  boolean validate() {

    return Stream.of(
            validateProfileConnectorInterfaces(validatorContext.newProfileConnectorInterfaces()),
            validateUserConnectorInterfaces(validatorContext.newUserConnectorInterfaces()),
            validateGeneratedProfileConnectors(validatorContext.newGeneratedProfileConnectors()),
            validateGeneratedUserConnectors(validatorContext.newGeneratedUserConnectors()),
            validateConfigurations(validatorContext.newConfigurations()),
            validateCrossProfileTypes(validatorContext.newCrossProfileTypes()),
            validateProviderMethods(validatorContext.newProviderMethods()),
            validateProviderClasses(validatorContext.newProviderClasses()),
            validateCrossProfileCallbackInterfaces(
                validatorContext.newCrossProfileCallbackInterfaces()),
            validateCrossProfileTests(validatorContext.newCrossProfileTests()),
            validateCustomParcelableWrappers(validatorContext.newCustomParcelableWrappers()),
            validateCustomFutureWrappers(validatorContext.newCustomFutureWrappers()))
        .allMatch(b -> b);
  }

  private boolean validateProfileConnectorInterfaces(
      Collection<ProfileConnectorInfo> connectorInterfaces) {
    boolean isValid = true;

    for (ProfileConnectorInfo connectorInterface : connectorInterfaces) {
      isValid = validateProfileConnectorInterface(connectorInterface) && isValid;
    }

    return isValid;
  }

  private boolean validateProfileConnectorInterface(ProfileConnectorInfo connectorInterface) {
    boolean isValid = true;

    if (!connectorInterface.connectorElement().getKind().equals(ElementKind.INTERFACE)) {
      showError(CUSTOM_PROFILE_CONNECTOR_MUST_BE_INTERFACE, connectorInterface.connectorElement());
      isValid = false;
    }

    if (!implementsInterface(connectorInterface.connectorElement(), profileConnectorType)) {
      showError(
          CUSTOM_PROFILE_CONNECTOR_MUST_EXTEND_CONNECTOR, connectorInterface.connectorElement());
      isValid = false;
    }

    for (TypeElement parcelableWrapper : connectorInterface.parcelableWrapperClasses()) {
      if (parcelableWrapper.getAnnotation(CustomParcelableWrapper.class) == null) {
        showError(PARCELABLE_WRAPPER_ANNOTATION_ERROR, connectorInterface.connectorElement());
      }
    }

    for (TypeElement futureWrapper : connectorInterface.futureWrapperClasses()) {
      if (futureWrapper.getAnnotation(CustomFutureWrapper.class) == null) {
        isValid = false;
        showError(FUTURE_WRAPPER_ANNOTATION_ERROR, connectorInterface.connectorElement());
      }
    }

    for (TypeElement importer : connectorInterface.importsClasses()) {
      if (importer.getAnnotation(CustomProfileConnector.class) == null) {
        isValid = false;
        showError(IMPORTS_NOT_PROFILE_CONNECTOR_ERROR, connectorInterface.connectorElement());
        showError(IMPORTS_NOT_PROFILE_CONNECTOR_ERROR, connectorInterface.connectorElement());
      }
    }

    return isValid;
  }

  private boolean validateUserConnectorInterfaces(
      Collection<UserConnectorInfo> connectorInterfaces) {
    boolean isValid = true;

    for (UserConnectorInfo connectorInterface : connectorInterfaces) {
      isValid = validateUserConnectorInterface(connectorInterface) && isValid;
    }

    return isValid;
  }

  private boolean validateUserConnectorInterface(UserConnectorInfo connectorInterface) {
    boolean isValid = true;

    if (!connectorInterface.connectorElement().getKind().equals(ElementKind.INTERFACE)) {
      showError(CUSTOM_USER_CONNECTOR_MUST_BE_INTERFACE, connectorInterface.connectorElement());
      isValid = false;
    }

    if (!implementsInterface(connectorInterface.connectorElement(), userConnectorType)) {
      showError(CUSTOM_USER_CONNECTOR_MUST_EXTEND_CONNECTOR, connectorInterface.connectorElement());
      isValid = false;
    }

    for (TypeElement parcelableWrapper : connectorInterface.parcelableWrapperClasses()) {
      if (parcelableWrapper.getAnnotation(CustomParcelableWrapper.class) == null) {
        showError(PARCELABLE_WRAPPER_ANNOTATION_ERROR, connectorInterface.connectorElement());
      }
    }

    for (TypeElement futureWrapper : connectorInterface.futureWrapperClasses()) {
      if (futureWrapper.getAnnotation(CustomFutureWrapper.class) == null) {
        isValid = false;
        showError(FUTURE_WRAPPER_ANNOTATION_ERROR, connectorInterface.connectorElement());
      }
    }

    for (TypeElement importer : connectorInterface.importsClasses()) {
      if (importer.getAnnotation(CustomUserConnector.class) == null) {
        isValid = false;
        showError(IMPORTS_NOT_USER_CONNECTOR_ERROR, connectorInterface.connectorElement());
      }
    }

    return isValid;
  }

  private boolean validateGeneratedProfileConnectors(Collection<TypeElement> generatedConnectors) {
    boolean isValid = true;

    for (TypeElement generatedConnector : generatedConnectors) {
      isValid = validateGeneratedProfileConnector(generatedConnector) && isValid;
    }

    return isValid;
  }

  private boolean validateGeneratedProfileConnector(TypeElement generatedConnector) {
    boolean isValid = true;

    if (!generatedConnector.getKind().equals(ElementKind.INTERFACE)) {
      showError(GENERATED_PROFILE_CONNECTOR_MUST_BE_INTERFACE, generatedConnector);
      isValid = false;
    }

    if (!implementsInterface(generatedConnector, profileConnectorType)) {
      showError(GENERATED_PROFILE_CONNECTOR_MUST_EXTEND_PROFILE_CONNECTOR, generatedConnector);
      isValid = false;
    }

    if (generatedConnector.getEnclosedElements().stream()
        .anyMatch(i -> !i.getModifiers().contains(Modifier.STATIC))) {
      showError(ADDITIONAL_PROFILE_CONNECTOR_METHODS_ERROR, generatedConnector);
      isValid = false;
    }

    return isValid;
  }

  private boolean validateGeneratedUserConnectors(Collection<TypeElement> generatedConnectors) {
    boolean isValid = true;

    for (TypeElement generatedConnector : generatedConnectors) {
      isValid = validateGeneratedUserConnector(generatedConnector) && isValid;
    }

    return isValid;
  }

  private boolean validateGeneratedUserConnector(TypeElement generatedConnector) {
    boolean isValid = true;

    if (!generatedConnector.getKind().equals(ElementKind.INTERFACE)) {
      showError(GENERATED_USER_CONNECTOR_MUST_BE_INTERFACE, generatedConnector);
      isValid = false;
    }

    if (!implementsInterface(generatedConnector, userConnectorType)) {
      showError(GENERATED_USER_CONNECTOR_MUST_EXTEND_USER_CONNECTOR, generatedConnector);
      isValid = false;
    }

    if (generatedConnector.getEnclosedElements().stream()
        .anyMatch(i -> !i.getModifiers().contains(Modifier.STATIC))) {
      showError(ADDITIONAL_USER_CONNECTOR_METHODS_ERROR, generatedConnector);
      isValid = false;
    }

    return isValid;
  }

  private boolean implementsInterface(TypeElement type, TypeMirror interfaceType) {
    for (TypeMirror t : type.getInterfaces()) {
      if (validatorContext.types().isSameType(t, interfaceType)) {
        return true;
      }
    }
    return false;
  }

  private boolean validateConfigurations(
      Collection<ValidatorCrossProfileConfigurationInfo> configurations) {
    boolean isValid = true;

    for (ValidatorCrossProfileConfigurationInfo configuration : configurations) {
      isValid = validateConfiguration(configuration) && isValid;
    }

    return isValid;
  }

  private boolean validateConfiguration(ValidatorCrossProfileConfigurationInfo configuration) {
    boolean isValid = true;

    for (TypeElement providerClass : configuration.providerClassElements()) {
      if (!hasCrossProfileProviderAnnotation(providerClass)
          && GeneratorUtilities.findCrossProfileProviderMethodsInClass(providerClass).isEmpty()) {
        showError(NOT_A_PROVIDER_CLASS_ERROR, configuration.configurationElement());
        isValid = false;
      }
    }

    if (configuration.connector().isPresent()
        && !configuration.connector().get().getKind().equals(ElementKind.INTERFACE)) {
      showError(CONNECTOR_MUST_BE_INTERFACE, configuration.configurationElement());
      isValid = false;
    }

    if (configuration.connector().isPresent()
        && !implementsInterface(configuration.connector().get(), profileConnectorType)
        && !implementsInterface(configuration.connector().get(), userConnectorType)) {
      showError(CONNECTOR_MUST_EXTEND_CONNECTOR, configuration.configurationElement());
      isValid = false;
    }

    return isValid;
  }

  private boolean validateCrossProfileTypes(
      Collection<ValidatorCrossProfileTypeInfo> crossProfileTypes) {
    boolean isValid =
        validateCrossProfileTypesAreProvided(
            crossProfileTypes.stream()
                .map(ValidatorCrossProfileTypeInfo::crossProfileTypeElement)
                .collect(toSet()),
            validatorContext.newProviderMethods(),
            validatorContext.newProviderClasses());

    for (ValidatorCrossProfileTypeInfo crossProfileType : crossProfileTypes) {
      isValid = validateCrossProfileType(crossProfileType) && isValid;
    }

    return isValid;
  }

  private boolean validateCrossProfileType(ValidatorCrossProfileTypeInfo crossProfileType) {
    boolean isValid = true;

    PackageElement packageElement =
        (PackageElement) crossProfileType.crossProfileTypeElement().getEnclosingElement();
    if (packageElement.getQualifiedName().toString().isEmpty()) {
      showError(
          CROSS_PROFILE_TYPE_DEFAULT_PACKAGE_ERROR,
          crossProfileType.crossProfileTypeElement(),
          validationMessageFormatterForClass(crossProfileType.crossProfileTypeElement()));
      isValid = false;
    }

    if (!crossProfileType.crossProfileTypeElement().getModifiers().contains(Modifier.PUBLIC)) {
      showError(
          NON_PUBLIC_CROSS_PROFILE_TYPE_ERROR,
          crossProfileType.crossProfileTypeElement(),
          validationMessageFormatterForClass(crossProfileType.crossProfileTypeElement()));
      isValid = false;
    }

    if (crossProfileType.isStatic()) {
      for (ExecutableElement crossProfileMethod : crossProfileType.crossProfileMethods()) {
        if (!crossProfileMethod.getModifiers().contains(Modifier.STATIC)) {
          showError(NOT_STATIC_ERROR, crossProfileMethod);
          isValid = false;
        }
      }
    }

    if (crossProfileType.connectorInfo().isPresent()
        && !crossProfileType
            .connectorInfo()
            .get()
            .connectorElement()
            .getKind()
            .equals(ElementKind.INTERFACE)) {
      showError(CONNECTOR_MUST_BE_INTERFACE, crossProfileType.crossProfileTypeElement());
      isValid = false;
    }

    if (crossProfileType.connectorInfo().isPresent()
        && !implementsInterface(
            crossProfileType.connectorInfo().get().connectorElement(), profileConnectorType)
        && !implementsInterface(
            crossProfileType.connectorInfo().get().connectorElement(), userConnectorType)) {
      showError(CONNECTOR_MUST_EXTEND_CONNECTOR, crossProfileType.crossProfileTypeElement());
      isValid = false;
    }

    for (TypeElement parcelableWrapper : crossProfileType.parcelableWrapperClasses()) {
      if (parcelableWrapper.getAnnotation(CustomParcelableWrapper.class) == null) {
        showError(PARCELABLE_WRAPPER_ANNOTATION_ERROR, crossProfileType.crossProfileTypeElement());
      }
    }

    for (TypeElement futureWrapper : crossProfileType.futureWrapperClasses()) {
      if (futureWrapper.getAnnotation(CustomFutureWrapper.class) == null) {
        isValid = false;
        showError(FUTURE_WRAPPER_ANNOTATION_ERROR, crossProfileType.crossProfileTypeElement());
      }
    }

    isValid =
        crossProfileType.crossProfileMethods().stream()
                .map(m -> validateCrossProfileMethod(crossProfileType, m))
                .allMatch(b -> b)
            && isValid;

    return isValid;
  }

  private boolean validateCrossProfileTypesAreProvided(
      Collection<TypeElement> crossProfileTypeElements,
      Collection<ExecutableElement> providerMethods,
      Collection<ValidatorProviderClassInfo> providerClasses) {
    Map<String, Collection<Element>> crossProfileTypeProviders =
        crossProfileTypeElements.stream()
            .collect(toMap(element -> element.asType().toString(), element -> new HashSet<>()));

    for (ExecutableElement provider : providerMethods) {
      String providedTypeName = provider.getReturnType().toString();

      if (crossProfileTypeProviders.containsKey(providedTypeName)) {
        crossProfileTypeProviders.get(providedTypeName).add(provider);
      }
    }

    for (ValidatorProviderClassInfo provider : providerClasses) {
      for (TypeElement staticType : provider.staticTypes()) {
        String providedTypeName = staticType.getQualifiedName().toString();

        if (crossProfileTypeProviders.containsKey(providedTypeName)) {
          crossProfileTypeProviders.get(providedTypeName).add(provider.providerClassElement());
        }
      }
    }

    boolean isValid = true;

    for (String crossProfileType : crossProfileTypeProviders.keySet()) {
      Collection<Element> providers = crossProfileTypeProviders.get(crossProfileType);

      if (providers.size() > 1) {
        isValid = false;
        for (Element providerElement : providers) {
          showError(String.format(MULTIPLE_PROVIDERS_ERROR, crossProfileType), providerElement);
        }
      }
    }

    return isValid;
  }

  private boolean validateProviderMethods(Collection<ExecutableElement> providerMethods) {
    boolean isValid = true;

    for (ExecutableElement providerMethod : providerMethods) {
      TypeElement crossProfileType =
          validatorContext.elements().getTypeElement(providerMethod.getReturnType().toString());
      if (!hasCrossProfileAnnotation(crossProfileType)
          && findCrossProfileMethodsInClass(crossProfileType).isEmpty()) {
        showError(PROVIDING_NON_CROSS_PROFILE_TYPE_ERROR, providerMethod);
        isValid = false;
      }

      if (providerMethod.getParameters().stream()
              .anyMatch(v -> !validatorContext.types().isSameType(v.asType(), contextType))
          || providerMethod.getParameters().size() > 1) {
        showError(PROVIDER_INCORRECT_ARGS_ERROR, providerMethod);
        isValid = false;
      }

      if (providerMethod.getModifiers().contains(Modifier.STATIC)) {
        showError(STATIC_PROVIDER_ERROR, providerMethod);
        isValid = false;
      }

      CrossProfileProviderAnnotationInfo annotationInfo =
          AnnotationFinder.extractCrossProfileProviderAnnotationInfo(
              validatorContext, providerMethod);

      if (!annotationInfo.staticTypes().isEmpty()) {
        showError(METHOD_STATICTYPES_ERROR, providerMethod);
        isValid = false;
      }
    }

    return isValid;
  }

  private boolean validateProviderClasses(Collection<ValidatorProviderClassInfo> providerClasses) {
    boolean isValid = true;

    for (ValidatorProviderClassInfo provider : providerClasses) {
      if (!hasValidProviderClassConstructor(provider.providerClassElement())) {
        showError(INVALID_CONSTRUCTORS_ERROR, provider.providerClassElement());
        isValid = false;
      }

      if (provider.providerClassElement().getEnclosedElements().stream()
              .filter(e -> e instanceof ExecutableElement)
              .map(e -> (ExecutableElement) e)
              .filter(e -> e.getKind().equals(ElementKind.CONSTRUCTOR))
              .filter(e -> e.getModifiers().contains(Modifier.PUBLIC))
              .count()
          > 1) {
        showError(INVALID_CONSTRUCTORS_ERROR, provider.providerClassElement());
        isValid = false;
      }
    }

    return isValid;
  }

  private boolean hasValidProviderClassConstructor(TypeElement clazz) {
    for (ExecutableElement constructor :
        ElementFilter.constructorsIn(clazz.getEnclosedElements())) {
      if (constructor.getModifiers().contains(Modifier.PUBLIC)) {
        if (isValidProviderClassConstructor(constructor)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isValidProviderClassConstructor(ExecutableElement constructor) {
    if (constructor.getParameters().size() == 0) {
      return true;
    }

    if (constructor.getParameters().size() > 1) {
      return false;
    }

    return validatorContext
        .types()
        .isSameType(constructor.getParameters().iterator().next().asType(), contextType);
  }

  private boolean validateCrossProfileMethod(
      ValidatorCrossProfileTypeInfo crossProfileType, ExecutableElement crossProfileMethod) {
    boolean isValid = true;

    CrossProfileAnnotationInfo crossProfileAnnotation =
        AnnotationFinder.extractCrossProfileAnnotationInfo(validatorContext, crossProfileMethod);

    if (!crossProfileAnnotation.connectorIsDefault()) {
      showError(METHOD_CONNECTOR_ERROR, crossProfileMethod);
      isValid = false;
    }

    if (!crossProfileAnnotation.parcelableWrapperClasses().isEmpty()) {
      showError(METHOD_PARCELABLE_WRAPPERS_ERROR, crossProfileMethod);
      isValid = false;
    }

    if (!crossProfileMethod.getThrownTypes().isEmpty()) {
      if (CrossProfileMethodInfo.isFuture(crossProfileType.supportedTypes(), crossProfileMethod)
          || CrossProfileMethodInfo.getCrossProfileCallbackParam(
                  validatorContext, crossProfileMethod)
              .isPresent()) {
        showError(ASYNC_DECLARED_EXCEPTION_ERROR, crossProfileMethod);
        isValid = false;
      }
    }

    if (crossProfileAnnotation.isStatic()) {
      showError(METHOD_ISSTATIC_ERROR, crossProfileMethod);
      isValid = false;
    }

    isValid =
        isValid
            && validateReturnType(crossProfileType, crossProfileMethod)
            && validateParameterTypesForCrossProfileMethod(crossProfileType, crossProfileMethod);

    if (crossProfileMethod.getAnnotation(Cacheable.class) != null) {
      isValid = isValid && validateCacheableMethod(crossProfileType, crossProfileMethod);
    }

    return isValid;
  }

  private boolean validateReturnType(
      ValidatorCrossProfileTypeInfo crossProfileType, ExecutableElement crossProfileMethod) {
    TypeMirror returnType = crossProfileMethod.getReturnType();

    if (crossProfileType.supportedTypes().isValidReturnType(returnType)) {
      return true;
    }

    showError(String.format(UNSUPPORTED_RETURN_TYPE_ERROR, returnType), crossProfileMethod);
    return false;
  }

  private boolean validateParameterTypesForCrossProfileMethod(
      ValidatorCrossProfileTypeInfo crossProfileType, ExecutableElement crossProfileMethod) {
    boolean isValid =
        crossProfileMethod.getParameters().stream()
            .allMatch(p -> validateParameterTypeForCrossProfileMethod(crossProfileType, p));

    List<TypeElement> crossProfileCallbackParameters =
        crossProfileMethod.getParameters().stream()
            .map(v -> validatorContext.elements().getTypeElement(v.asType().toString()))
            .filter(Objects::nonNull)
            .filter(AnnotationFinder::hasCrossProfileCallbackAnnotation)
            .collect(Collectors.toList());

    if (crossProfileCallbackParameters.size() > 1) {
      isValid = false;
      showError(MULTIPLE_ASYNC_CALLBACK_PARAMETERS_ERROR, crossProfileMethod);
    }

    if (crossProfileCallbackParameters.size() == 1) {
      if (!crossProfileMethod.getReturnType().getKind().equals(TypeKind.VOID)) {
        isValid = false;
        showError(NON_VOID_CALLBACK_ERROR, crossProfileMethod);
      }

      isValid =
          validateParameterTypesForCrossProfileCallbackInterface(
                  crossProfileType, crossProfileCallbackParameters.get(0))
              && isValid;
    }

    if (!crossProfileCallbackParameters.isEmpty()
        && !crossProfileMethod.getReturnType().getKind().equals(TypeKind.VOID)) {
      isValid = false;
      showError(NON_VOID_CALLBACK_ERROR, crossProfileMethod);
    }

    return isValid;
  }

  private boolean validateParameterTypeForCrossProfileCallbackInterface(
      ValidatorCrossProfileTypeInfo crossProfileType, VariableElement parameter) {
    TypeMirror parameterType = parameter.asType();

    if (crossProfileType
        .supportedTypes()
        .isValidParameterType(
            parameterType, TypeCheckContext.createForCrossProfileCallbackInterface())) {
      return true;
    }

    showError(
        String.format(UNSUPPORTED_PARAMETER_TYPE_CROSS_ASYNC_CALLBACK, parameterType),
        parameter,
        validationMessageFormatterFor(crossProfileType.crossProfileMethods().get(0)));
    return false;
  }

  private boolean validateParameterTypesForCrossProfileCallbackInterface(
      ValidatorCrossProfileTypeInfo crossProfileType, TypeElement crossProfileCallbackInterface) {
    return crossProfileCallbackInterface.getEnclosedElements().stream()
        .filter(m -> m instanceof ExecutableElement)
        .map(m -> (ExecutableElement) m)
        .map(m -> validateParameterTypesForCrossProfileCallbackInterface(crossProfileType, m))
        .allMatch(b -> b);
  }

  private boolean validateParameterTypesForCrossProfileCallbackInterface(
      ValidatorCrossProfileTypeInfo crossProfileType, ExecutableElement method) {
    return method.getParameters().stream()
        .allMatch(m -> validateParameterTypeForCrossProfileCallbackInterface(crossProfileType, m));
  }

  private boolean validateParameterTypeForCrossProfileMethod(
      ValidatorCrossProfileTypeInfo crossProfileType, VariableElement parameter) {
    TypeMirror parameterType = parameter.asType();

    if (crossProfileType.supportedTypes().isValidParameterType(parameterType)) {
      return true;
    }

    showError(
        String.format(UNSUPPORTED_PARAMETER_TYPE_CROSS_PROFILE_METHOD, parameterType),
        parameter,
        validationMessageFormatterFor(crossProfileType.crossProfileMethods().get(0)));
    return false;
  }

  private boolean validateCrossProfileCallbackInterfaces(
      Collection<TypeElement> crossProfileCallbackInterfaces) {
    return crossProfileCallbackInterfaces.stream()
        .allMatch(this::validateCrossProfileCallbackInterface);
  }

  private boolean validateCrossProfileCallbackInterface(TypeElement crossProfileCallbackInterface) {
    boolean isValid = true;

    CrossProfileCallbackAnnotationInfo annotationInfo =
        AnnotationFinder.extractCrossProfileCallbackAnnotationInfo(
            validatorContext, crossProfileCallbackInterface);

    PackageElement packageElement =
        (PackageElement) crossProfileCallbackInterface.getEnclosingElement();
    if (packageElement.getQualifiedName().toString().isEmpty()) {
      showError(CALLBACK_INTERFACE_DEFAULT_PACKAGE_ERROR, crossProfileCallbackInterface);
      isValid = false;
    }

    if (crossProfileCallbackInterface.getKind() != ElementKind.INTERFACE) {
      showError(NOT_INTERFACE_ERROR, crossProfileCallbackInterface);
      isValid = false;
    }

    if (!crossProfileCallbackInterface.getTypeParameters().isEmpty()) {
      showError(GENERIC_CALLBACK_INTERFACE_ERROR, crossProfileCallbackInterface);
      isValid = false;
    }

    Collection<ExecutableElement> methods = getMethods(crossProfileCallbackInterface);

    if (methods.isEmpty()) {
      showError(NO_METHODS_ERROR, crossProfileCallbackInterface);
      isValid = false;
    }

    if (annotationInfo.simple() && methods.size() > 1) {
      showError(NOT_ONE_METHOD_ERROR, crossProfileCallbackInterface);
      isValid = false;
    }

    isValid =
        methods.stream()
                .allMatch(
                    (method) ->
                        validateMethodOnCrossProfileCallbackInterface(
                            annotationInfo, method, crossProfileCallbackInterface))
            && isValid;

    return isValid;
  }

  private boolean validateMethodOnCrossProfileCallbackInterface(
      CrossProfileCallbackAnnotationInfo annotationInfo,
      ExecutableElement method,
      TypeElement crossProfileCallbackInterface) {
    boolean isValid = true;

    if (method.isDefault()) {
      showError(
          DEFAULT_METHOD_ERROR,
          method,
          validationMessageFormatterFor(crossProfileCallbackInterface));
      isValid = false;
    }

    if (method.getModifiers().contains(Modifier.STATIC)) {
      showError(
          STATIC_METHOD_ERROR,
          method,
          validationMessageFormatterFor(crossProfileCallbackInterface));
      isValid = false;
    }

    if (!method.getReturnType().getKind().equals(TypeKind.VOID)) {
      showError(
          NOT_VOID_ERROR, method, validationMessageFormatterFor(crossProfileCallbackInterface));
      isValid = false;
    }

    if (annotationInfo.simple() && method.getParameters().size() > 1) {
      showError(
          MORE_THAN_ONE_PARAMETER_ERROR,
          method,
          validationMessageFormatterFor(crossProfileCallbackInterface));
      isValid = false;
    }

    return isValid;
  }

  private boolean validateCacheableMethod(
      ValidatorCrossProfileTypeInfo crossProfileTypeInfo, ExecutableElement cacheableMethod) {
    boolean isValid = true;

    TypeMirror returnType = cacheableMethod.getReturnType();

    if (returnType.getKind().equals(TypeKind.VOID)) {
      isValid = isValid && validateCallbackOnCacheableMethod(cacheableMethod);
    } else if (!isSerializable(crossProfileTypeInfo, returnType)) {
      showError(CACHEABLE_METHOD_RETURNS_NON_SERIALIZABLE_ERROR, cacheableMethod);
      isValid = false;
    }

    return isValid;
  }

  private boolean isSerializable(
      ValidatorCrossProfileTypeInfo crossProfileTypeInfo, TypeMirror type) {
    return isSerializable(type) || isFutureWithSerializableResult(crossProfileTypeInfo, type);
  }

  private boolean isSerializable(TypeMirror type) {
    TypeMirror serializable =
        validatorContext.elements().getTypeElement(Serializable.class.getCanonicalName()).asType();

    return validatorContext.types().isAssignable(type, serializable);
  }

  private boolean isFutureWithSerializableResult(
      ValidatorCrossProfileTypeInfo crossProfileType, TypeMirror type) {

    if (!crossProfileType.supportedTypes().isFuture(removeTypeArguments(type))) {
      return false;
    }

    TypeMirror futureResult = extractTypeArguments(type).get(0);

    return isSerializable(futureResult);
  }

  private boolean validateCallbackOnCacheableMethod(ExecutableElement cacheableMethod) {
    boolean isValid = true;

    if (!hasCallback(cacheableMethod)) {
      showError(CACHEABLE_METHOD_RETURNS_VOID_ERROR, cacheableMethod);
      return false;
    }

    TypeElement callback =
        cacheableMethod.getParameters().stream()
            .map(v -> validatorContext.elements().getTypeElement(v.asType().toString()))
            .filter(Objects::nonNull)
            .filter(AnnotationFinder::hasCrossProfileCallbackAnnotation)
            .findFirst()
            .get();

    CrossProfileCallbackAnnotationInfo annotationInfo =
        AnnotationFinder.extractCrossProfileCallbackAnnotationInfo(validatorContext, callback);
    if (!annotationInfo.simple()) {
      showError(CACHEABLE_METHOD_NON_SIMPLE_CALLBACK_ERROR, cacheableMethod);
      isValid = false;
    }

    ExecutableElement method = getMethods(callback).stream().findFirst().get();
    if (!hasSingleSerializableParameterOnly(method)) {
      showError(CACHEABLE_METHOD_USES_INVALID_PARAMETERS_ERROR, cacheableMethod);
      isValid = false;
    }

    return isValid;
  }

  private boolean hasCallback(ExecutableElement method) {
    return method.getParameters().stream()
        .map(v -> validatorContext.elements().getTypeElement(v.asType().toString()))
        .filter(Objects::nonNull)
        .anyMatch(AnnotationFinder::hasCrossProfileCallbackAnnotation);
  }

  private boolean hasSingleSerializableParameterOnly(ExecutableElement method) {
    return method.getParameters().stream().filter(p -> isSerializable(p.asType())).count() == 1;
  }

  private boolean validateCrossProfileTests(
      Collection<ValidatorCrossProfileTestInfo> crossProfileTests) {
    return crossProfileTests.stream().allMatch(this::validateCrossProfileTest);
  }

  private boolean validateCrossProfileTest(ValidatorCrossProfileTestInfo crossProfileTest) {
    boolean isValid = true;

    if (!hasCrossProfileConfigurationAnnotation(crossProfileTest.configurationElement())
        && !hasCrossProfileConfigurationsAnnotation(crossProfileTest.configurationElement())) {
      showError(NOT_A_CONFIGURATION_ERROR, crossProfileTest.crossProfileTestElement());
      isValid = false;
    }
    return isValid;
  }

  private boolean validateCustomParcelableWrappers(
      Collection<TypeElement> customParcelableWrappers) {
    return customParcelableWrappers.stream().allMatch(this::validateCustomParcelableWrapper);
  }

  private boolean validateCustomParcelableWrapper(TypeElement customParcelableWrapper) {
    boolean isValid = true;
    if (!validatorContext.types().isAssignable(customParcelableWrapper.asType(), parcelableType)) {
      showError(NOT_PARCELABLE_ERROR, customParcelableWrapper);
      isValid = false;
    }

    ClassName parcelableWrapperRawType = getRawTypeClassName(customParcelableWrapper.asType());
    ClassName wrappedParamRawType =
        getRawTypeClassName(
            ParcelableWrapperAnnotationInfo.extractFromParcelableWrapperAnnotation(
                    validatorContext,
                    customParcelableWrapper.getAnnotation(CustomParcelableWrapper.class))
                .originalType()
                .asType());

    Optional<ExecutableElement> ofMethod =
        customParcelableWrapper.getEnclosedElements().stream()
            .filter(p -> p.getKind().equals(ElementKind.METHOD))
            .map(p -> (ExecutableElement) p)
            .filter(p -> p.getSimpleName().contentEquals("of"))
            // We drop generics as without being overly prescriptive it's impossible to know that
            // the method is returning the correct generic type
            .filter(
                p ->
                    getRawTypeClassName(p.getReturnType())
                        .equals(getRawTypeClassName(customParcelableWrapper.asType())))
            .filter(p -> ofMethodHasExpectedArguments(wrappedParamRawType, p))
            .findFirst();

    if (!ofMethod.isPresent()) {
      showError(INCORRECT_OF_METHOD, customParcelableWrapper);
      isValid = false;
    }

    Optional<ExecutableElement> getMethod =
        customParcelableWrapper.getEnclosedElements().stream()
            .filter(p -> p.getKind().equals(ElementKind.METHOD))
            .map(p -> (ExecutableElement) p)
            .filter(p -> p.getSimpleName().contentEquals("get"))
            // We drop generics as without being overly prescriptive it's impossible to know that
            // the method is returning the correct generic type
            .filter(p -> getRawTypeClassName(p.getReturnType()).equals(wrappedParamRawType))
            .findFirst();

    if (!getMethod.isPresent()) {
      showError(INCORRECT_GET_METHOD, customParcelableWrapper);
      isValid = false;
    }

    TypeName creatorType =
        ParameterizedTypeName.get(PARCELABLE_CREATOR_CLASSNAME, parcelableWrapperRawType);

    Optional<VariableElement> creator =
        customParcelableWrapper.getEnclosedElements().stream()
            .filter(p -> p.getKind().equals(ElementKind.FIELD))
            .map(p -> (VariableElement) p)
            .filter(p -> p.getSimpleName().contentEquals("CREATOR"))
            .filter(
                p ->
                    p.getModifiers()
                        .containsAll(
                            Arrays.asList(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC)))
            .filter(p -> ClassName.get(p.asType()).equals(creatorType))
            .findFirst();

    if (!creator.isPresent()) {
      showError(INCORRECT_PARCELABLE_IMPLEMENTATION, customParcelableWrapper);
      isValid = false;
    }

    return isValid;
  }

  private boolean ofMethodHasExpectedArguments(
      ClassName wrappedParamRawType, ExecutableElement ofMethod) {
    List<? extends VariableElement> parameters = ofMethod.getParameters();
    if (parameters.size() != 3) {
      return false;
    }

    if (!validatorContext.types().isSameType(parameters.get(0).asType(), bundlerType)) {
      return false;
    }

    if (!validatorContext.types().isSameType(parameters.get(1).asType(), bundlerTypeType)) {
      return false;
    }

    if (!getRawTypeClassName(parameters.get(2).asType()).equals(wrappedParamRawType)) {
      return false;
    }

    return true;
  }

  private boolean validateCustomFutureWrappers(Collection<TypeElement> futureWrappers) {
    return futureWrappers.stream().map(this::validateCustomFutureWrapper).allMatch(b -> b);
  }

  private boolean validateCustomFutureWrapper(TypeElement futureWrapper) {
    boolean isValid = true;

    ClassName wrappedFutureRawType =
        getRawTypeClassName(
            FutureWrapperAnnotationInfo.extractFromFutureWrapperAnnotation(
                    validatorContext, futureWrapper.getAnnotation(CustomFutureWrapper.class))
                .originalType()
                .asType());

    if (!getRawTypeQualifiedName(futureWrapper.getSuperclass())
        .equals("com.google.android.enterprise.connectedapps.FutureWrapper")) {
      showError(DOES_NOT_EXTEND_FUTURE_WRAPPER_ERROR, futureWrapper);
      isValid = false;
    }

    if (futureWrapper.getTypeParameters().size() != 1) {
      showError(MUST_HAVE_ONE_TYPE_PARAMETER_ERROR, futureWrapper);
      isValid = false;
    }

    Optional<ExecutableElement> createMethod =
        futureWrapper.getEnclosedElements().stream()
            .filter(e -> e instanceof ExecutableElement)
            .map(e -> (ExecutableElement) e)
            .filter(e -> e.getSimpleName().contentEquals("create"))
            .filter(
                e -> e.getModifiers().containsAll(Arrays.asList(Modifier.PUBLIC, Modifier.STATIC)))
            // We drop generics as without being overly prescriptive it's impossible to know that
            // the method is returning the correct generic type
            .filter(
                e ->
                    getRawTypeClassName(e.getReturnType())
                        .equals(getRawTypeClassName(futureWrapper.asType())))
            .filter(this::createMethodHasExpectedArguments)
            .findFirst();

    if (!createMethod.isPresent()) {
      showError(INCORRECT_CREATE_METHOD_ERROR, futureWrapper);
      isValid = false;
    }

    Optional<ExecutableElement> getFutureMethod =
        futureWrapper.getEnclosedElements().stream()
            .filter(e -> e instanceof ExecutableElement)
            .map(e -> (ExecutableElement) e)
            .filter(e -> e.getSimpleName().contentEquals("getFuture"))
            .filter(e -> e.getModifiers().contains(Modifier.PUBLIC))
            .filter(e -> !e.getModifiers().contains(Modifier.STATIC))
            // We drop generics as without being overly prescriptive it's impossible to know that
            // the method is returning the correct generic type
            .filter(e -> getRawTypeClassName(e.getReturnType()).equals(wrappedFutureRawType))
            .filter(e -> e.getParameters().isEmpty())
            .findFirst();

    if (!getFutureMethod.isPresent()) {
      showError(INCORRECT_GET_FUTURE_METHOD_ERROR, futureWrapper);
      isValid = false;
    }

    Optional<ExecutableElement> writeFutureResultMethod =
        futureWrapper.getEnclosedElements().stream()
            .filter(e -> e instanceof ExecutableElement)
            .map(e -> (ExecutableElement) e)
            .filter(e -> e.getSimpleName().contentEquals("writeFutureResult"))
            .filter(
                e -> e.getModifiers().containsAll(Arrays.asList(Modifier.PUBLIC, Modifier.STATIC)))
            .filter(e -> e.getReturnType().toString().equals("void"))
            .filter(e -> writeFutureResultMethodHasExpectedArguments(e, wrappedFutureRawType))
            .findFirst();

    if (!writeFutureResultMethod.isPresent()) {
      showError(INCORRECT_RESOLVE_CALLBACK_WHEN_FUTURE_IS_SET_METHOD_ERROR, futureWrapper);
      isValid = false;
    }

    Optional<ExecutableElement> groupResultsMethod =
        futureWrapper.getEnclosedElements().stream()
            .filter(e -> e instanceof ExecutableElement)
            .map(e -> (ExecutableElement) e)
            .filter(e -> e.getSimpleName().contentEquals("groupResults"))
            .filter(
                e -> e.getModifiers().containsAll(Arrays.asList(Modifier.PUBLIC, Modifier.STATIC)))
            .filter(e -> groupResultsMethodHasExpectedReturnType(e, wrappedFutureRawType))
            .filter(e -> groupResultsMethodHasExpectedArguments(e, wrappedFutureRawType))
            .findFirst();

    if (!groupResultsMethod.isPresent()) {
      showError(INCORRECT_GROUP_RESULTS_METHOD_ERROR, futureWrapper);
      isValid = false;
    }

    return isValid;
  }

  private boolean groupResultsMethodHasExpectedReturnType(
      ExecutableElement groupResultsMethod, ClassName wrappedFutureRawType) {

    if (!getRawTypeClassName(groupResultsMethod.getReturnType()).equals(wrappedFutureRawType)) {
      return false;
    }

    TypeMirror wrappedReturnType = extractTypeArguments(groupResultsMethod.getReturnType()).get(0);

    if (!getRawTypeClassName(wrappedReturnType).equals(ClassName.get(Map.class))) {
      return false;
    }

    TypeMirror wrappedReturnTypeKey = extractTypeArguments(wrappedReturnType).get(0);

    if (!validatorContext.types().isSameType(wrappedReturnTypeKey, profileType)) {
      return false;
    }

    return true;
  }

  private boolean groupResultsMethodHasExpectedArguments(
      ExecutableElement groupResultsMethod, ClassName wrappedFutureRawType) {
    if (groupResultsMethod.getParameters().size() != 1) {
      return false;
    }

    TypeMirror param = groupResultsMethod.getParameters().get(0).asType();

    if (!getRawTypeClassName(param).equals(ClassName.get(Map.class))) {
      return false;
    }

    List<TypeMirror> params = extractTypeArguments(param);

    TypeMirror keyParam = params.get(0);
    TypeMirror valueParam = params.get(1);

    if (!validatorContext.types().isSameType(keyParam, profileType)) {
      return false;
    }

    if (!getRawTypeClassName(valueParam).equals(wrappedFutureRawType)) {
      return false;
    }

    return true;
  }

  private boolean createMethodHasExpectedArguments(ExecutableElement createMethod) {
    if (createMethod.getParameters().size() != 2) {
      return false;
    }

    if (!validatorContext
        .types()
        .isSameType(createMethod.getParameters().get(0).asType(), bundlerType)) {
      return false;
    }

    if (!validatorContext
        .types()
        .isSameType(createMethod.getParameters().get(1).asType(), bundlerTypeType)) {
      return false;
    }

    return true;
  }

  private boolean writeFutureResultMethodHasExpectedArguments(
      ExecutableElement method, ClassName wrappedFutureRawType) {
    if (method.getParameters().size() != 2) {
      return false;
    }

    if (!getRawTypeClassName(method.getParameters().get(0).asType()).equals(wrappedFutureRawType)) {
      return false;
    }

    if (!validatorContext
        .types()
        .isAssignable(
            removeTypeArguments(method.getParameters().get(1).asType()), futureResultWriterType)) {
      return false;
    }

    return true;
  }

  private Collection<ExecutableElement> getMethods(TypeElement typeElement) {
    return typeElement.getEnclosedElements().stream()
        .filter(e -> e instanceof ExecutableElement)
        .map(e -> (ExecutableElement) e)
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
    validatorContext
        .processingEnv()
        .getMessager()
        .printMessage(Kind.ERROR, errorText, errorElement);
  }
}
