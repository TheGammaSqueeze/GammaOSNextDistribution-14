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

import static com.google.android.enterprise.connectedapps.processor.ClassNameUtilities.append;
import static com.google.android.enterprise.connectedapps.processor.ClassNameUtilities.prepend;
import static com.google.android.enterprise.connectedapps.processor.ClassNameUtilities.transformClassName;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.EXCEPTION_CALLBACK_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.PROFILE_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.PROFILE_RUNTIME_EXCEPTION_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.GeneratorUtilities.generateMethodReference;
import static com.google.android.enterprise.connectedapps.processor.containers.CrossProfileMethodInfo.AutomaticallyResolvedParameterFilterBehaviour.REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toList;

import com.google.android.enterprise.connectedapps.annotations.CrossProfile;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileCallbackInterfaceInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileCallbackParameterInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileMethodInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileTypeInfo;
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/** Generator of cross-profile code for a single {@link CrossProfile} type. */
final class InterfaceGenerator {

  private boolean generated = false;
  private final GeneratorContext generatorContext;
  private final GeneratorUtilities generatorUtilities;
  private final CrossProfileTypeInfo crossProfileType;

  InterfaceGenerator(GeneratorContext generatorContext, CrossProfileTypeInfo crossProfileType) {
    this.generatorContext = checkNotNull(generatorContext);
    this.generatorUtilities = new GeneratorUtilities(generatorContext);
    this.crossProfileType = checkNotNull(crossProfileType);
  }

  void generate() {
    if (generated) {
      throw new IllegalStateException("InterfaceGenerator#generate can only be called once");
    }
    generated = true;

    generateSingleSenderInterface();
    generateSingleSenderCanThrowInterface();
    generateMultipleSenderInterface();
  }

  private void generateSingleSenderInterface() {
    ClassName interfaceName = getSingleSenderInterfaceClassName(generatorContext, crossProfileType);

    TypeSpec.Builder interfaceBuilder =
        TypeSpec.interfaceBuilder(interfaceName)
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc(
                "Interface used for interacting with an instance of {@link $T} on a given"
                    + " profile.\n\n",
                crossProfileType.className())
            .addJavadoc(
                "<p>The profile is guaranteed to be available, so no {@link $T} will be thrown for"
                    + " any call.\n",
                UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME);

    for (CrossProfileMethodInfo method : crossProfileType.crossProfileMethods()) {
      generateMethodOnSingleSenderInterface(interfaceBuilder, method, crossProfileType);
    }

    generatorUtilities.writeClassToFile(interfaceName.packageName(), interfaceBuilder);
  }

  private void generateMethodOnSingleSenderInterface(
      TypeSpec.Builder interfaceBuilder,
      CrossProfileMethodInfo method,
      CrossProfileTypeInfo crossProfileType) {

    CodeBlock methodReference = generateMethodReference(crossProfileType, method);

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(method.simpleName())
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .returns(method.returnTypeTypeName())
            .addJavadoc("Make a call to {@link $L} on the given profile.\n\n", methodReference);

    for (TypeMirror automaticallyResolvedType :
        method.automaticallyResolvedParameterTypes(crossProfileType.supportedTypes())) {
      methodBuilder.addJavadoc(
          "<p>A {@link $T} will automatically be passed to {@link $L}.\n\n",
          automaticallyResolvedType,
          methodReference);
    }

    methodBuilder
        .addJavadoc("@see $L\n", methodReference)
        .addExceptions(method.thrownExceptions())
        .addParameters(
            GeneratorUtilities.extractParametersFromMethod(
                crossProfileType.supportedTypes(),
                method.methodElement(),
                REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS));

    interfaceBuilder.addMethod(methodBuilder.build());
  }

  private void generateSingleSenderCanThrowInterface() {
    ClassName interfaceName =
        getSingleSenderCanThrowInterfaceClassName(generatorContext, crossProfileType);

    TypeSpec.Builder interfaceBuilder =
        TypeSpec.interfaceBuilder(interfaceName)
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc(
                "Interface used for interacting with a {@link $T} on a given profile.\n",
                crossProfileType.className());

    for (CrossProfileMethodInfo method : crossProfileType.crossProfileMethods()) {
      generateMethodOnSingleSenderCanThrowInterface(interfaceBuilder, method, crossProfileType);
    }

    interfaceBuilder.addMethod(
        MethodSpec.methodBuilder("ifAvailable")
            .addJavadoc(
                "Make a call, returning a default value in case of error rather than throwing"
                    + " {@link $T}.\n",
                UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME)
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .returns(
                IfAvailableGenerator.getIfAvailableClassName(generatorContext, crossProfileType))
            .build());

    generatorUtilities.writeClassToFile(interfaceName.packageName(), interfaceBuilder);
  }

  private void generateMethodOnSingleSenderCanThrowInterface(
      TypeSpec.Builder interfaceBuilder,
      CrossProfileMethodInfo method,
      CrossProfileTypeInfo crossProfileType) {

    CodeBlock methodReference = generateMethodReference(crossProfileType, method);

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(method.simpleName())
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addExceptions(method.thrownExceptions())
            .returns(method.returnTypeTypeName())
            .addJavadoc("Make a call to {@link $L} on the given profile.\n\n", methodReference)
            .addParameters(
                GeneratorUtilities.extractParametersFromMethod(
                    crossProfileType.supportedTypes(),
                    method.methodElement(),
                    REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS));

    for (TypeMirror automaticallyResolvedType :
        method.automaticallyResolvedParameterTypes(crossProfileType.supportedTypes())) {
      methodBuilder.addJavadoc(
          "<p>A {@link $T} will automatically be passed to {@link $L}.\n\n",
          automaticallyResolvedType,
          methodReference);
    }

    if (method.isBlocking(generatorContext, crossProfileType)) {
      methodBuilder.addException(UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME);
      methodBuilder.addJavadoc(
          "<p>If an unchecked exception is thrown and this call is made to a profile other than"
              + " the current one, a {@link $T} will be thrown with the original exception as the"
              + " cause.\n\n",
          PROFILE_RUNTIME_EXCEPTION_CLASSNAME);
      methodBuilder.addJavadoc(
          "@throws $T if the profile is not connected.\n", UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME);
    } else if (method.isCrossProfileCallback(generatorContext)) {
      methodBuilder.addJavadoc(
          "<p>If an unchecked exception is thrown and this call is made to a profile other than"
              + " the current one, a {@link $T} will be thrown on another thread with the original"
              + " exception as the cause.\n\n",
          PROFILE_RUNTIME_EXCEPTION_CLASSNAME);
      methodBuilder.addJavadoc(
          "<p>If the profile does not exist or is not available, an {@link $T} will be passed into"
              + " the {@code exceptionCallback}.\n\n",
          UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME);
      methodBuilder.addParameter(EXCEPTION_CALLBACK_CLASSNAME, "exceptionCallback");
    } else {
      // Future
      methodBuilder.addJavadoc(
          "<p>If an unchecked exception is thrown and this call is made to a profile other than"
              + " the current one, a {@link $T} will be thrown on another thread with the original"
              + " exception as the cause.\n\n",
          PROFILE_RUNTIME_EXCEPTION_CLASSNAME);
      methodBuilder.addJavadoc(
          "<p>If the profile does not exist or is not available, the future will be set with an"
              + " {@link $T}.\n\n",
          UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME);
    }

    methodBuilder.addJavadoc("@see $L\n", methodReference);

    interfaceBuilder.addMethod(methodBuilder.build());
  }

  private void generateMultipleSenderInterface() {
    ClassName interfaceName =
        getMultipleSenderInterfaceClassName(generatorContext, crossProfileType);

    TypeSpec.Builder interfaceBuilder =
        TypeSpec.interfaceBuilder(interfaceName)
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc(
                "Interface used for interacting with a {@link $T} on multiple profiles.\n\n",
                crossProfileType.className())
            .addJavadoc(
                "<p>If any profiles are unavailable, the profile will not be included in the"
                    + " results. No {@link $T} will be thrown for any call.\n",
                UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME);

    for (CrossProfileMethodInfo method : crossProfileType.crossProfileMethods()) {
      if (method.isBlocking(generatorContext, crossProfileType)) {
        generateBlockingMethodOnMultipleSenderInterface(interfaceBuilder, method, crossProfileType);
      } else if (method.isCrossProfileCallback(generatorContext)) {
        generateCrossProfileCallbackMethodOnMultipleSenderInterface(
            interfaceBuilder, method, crossProfileType);
      } else if (method.isFuture(crossProfileType)) {
        generateFutureMethodOnMultipleSenderInterface(interfaceBuilder, method, crossProfileType);
      } else {
        throw new IllegalStateException("Unknown method type: " + method);
      }
    }

    generatorUtilities.writeClassToFile(interfaceName.packageName(), interfaceBuilder);
  }

  private void generateBlockingMethodOnMultipleSenderInterface(
      TypeSpec.Builder interfaceBuilder,
      CrossProfileMethodInfo method,
      CrossProfileTypeInfo crossProfileType) {

    TypeName returnType;

    if (!method.thrownExceptions().isEmpty()) {
      // We don't add methods with exceptions to the multiplesender interface
      return;
    }

    if (method.returnType().getKind().equals(TypeKind.VOID)) {
      // void is a special case so we don't return a map
      returnType = TypeName.VOID;
    } else {
      TypeName boxedMethodReturnType =
          TypeName.get(generatorUtilities.boxIfNecessary(method.returnType()));
      returnType =
          ParameterizedTypeName.get(
              ClassName.get(Map.class), PROFILE_CLASSNAME, boxedMethodReturnType);
    }

    CodeBlock methodReference = generateMethodReference(crossProfileType, method);

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(method.simpleName())
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .returns(returnType)
            .addParameters(
                GeneratorUtilities.extractParametersFromMethod(
                    crossProfileType.supportedTypes(),
                    method.methodElement(),
                    REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS))
            .addJavadoc("Make a call to {@link $L} on the given profiles.\n\n", methodReference);

    for (TypeMirror automaticallyResolvedType :
        method.automaticallyResolvedParameterTypes(crossProfileType.supportedTypes())) {
      methodBuilder.addJavadoc(
          "<p>A {@link $T} will automatically be passed to {@link $L}.\n\n",
          automaticallyResolvedType,
          methodReference);
    }

    methodBuilder
        .addJavadoc(
            "<p>If any profiles are not connected, they will not be included in the"
                + " results.\n\n")
        .addJavadoc(
            "<p>If an unchecked exception is thrown on any profile other than the current one,"
                + " a {@link $T} will be thrown with the original exception as the cause.\n\n",
            PROFILE_RUNTIME_EXCEPTION_CLASSNAME)
        .addJavadoc("@see $L\n", methodReference);

    interfaceBuilder.addMethod(methodBuilder.build());
  }

  private void generateCrossProfileCallbackMethodOnMultipleSenderInterface(
      TypeSpec.Builder interfaceBuilder,
      CrossProfileMethodInfo method,
      CrossProfileTypeInfo crossProfileType) {

    if (!method.isSimpleCrossProfileCallback(generatorContext)) {
      // Non-simple callbacks can't be used with multiple profiles
      return;
    }

    List<ParameterSpec> parameters =
        convertCallbackParametersIntoMulti(
            GeneratorUtilities.extractParametersFromMethod(
                crossProfileType.supportedTypes(),
                method.methodElement(),
                REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS),
            method.getCrossProfileCallbackParam(generatorContext).get());

    CodeBlock methodReference = generateMethodReference(crossProfileType, method);

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(method.simpleName())
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addParameters(parameters)
            .addJavadoc("Make a call to {@link $L} on the given profiles.\n\n", methodReference);

    for (TypeMirror automaticallyResolvedType :
        method.automaticallyResolvedParameterTypes(crossProfileType.supportedTypes())) {
      methodBuilder.addJavadoc(
          "<p>A {@link $T} will automatically be passed to {@link $L}.\n\n",
          automaticallyResolvedType,
          methodReference);
    }

    methodBuilder
        .addJavadoc(
            "<p>If any profiles are not available, they will not be included in the"
                + " results.\n\n")
        .addJavadoc(
            "<p>If an unchecked exception is thrown on any profile other than the current one,"
                + " a {@link $T} will be thrown on another thread with the original exception"
                + " as the cause.\n\n",
            PROFILE_RUNTIME_EXCEPTION_CLASSNAME)
        .addJavadoc("@see $L\n", methodReference);

    interfaceBuilder.addMethod(methodBuilder.build());
  }

  private void generateFutureMethodOnMultipleSenderInterface(
      TypeSpec.Builder interfaceBuilder,
      CrossProfileMethodInfo method,
      CrossProfileTypeInfo crossProfileType) {

    ClassName rawFutureType = TypeUtils.getRawTypeClassName(method.returnType());
    // We assume all Futures are generic with a single generic type
    TypeMirror wrappedType = TypeUtils.extractTypeArguments(method.returnType()).iterator().next();

    TypeMirror boxedWrappedType = generatorUtilities.boxIfNecessary(wrappedType);

    TypeName mapType =
        ParameterizedTypeName.get(
            ClassName.get(Map.class), PROFILE_CLASSNAME, ClassName.get(boxedWrappedType));

    ParameterizedTypeName returnType = ParameterizedTypeName.get(rawFutureType, mapType);

    CodeBlock methodReference = generateMethodReference(crossProfileType, method);

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(method.simpleName())
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .returns(returnType)
            .addParameters(
                GeneratorUtilities.extractParametersFromMethod(
                    crossProfileType.supportedTypes(),
                    method.methodElement(),
                    REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS))
            .addJavadoc("Make a call to {@link $L} on the given profiles.\n\n", methodReference);

    for (TypeMirror automaticallyResolvedType :
        method.automaticallyResolvedParameterTypes(crossProfileType.supportedTypes())) {
      methodBuilder.addJavadoc(
          "<p>A {@link $T} will automatically be passed to {@link $L}.\n\n",
          automaticallyResolvedType,
          methodReference);
    }

    methodBuilder
        .addJavadoc(
            "<p>If any profiles are not available, or if any profiles set the future with an"
                + " {@link $T}, they will not be included in the results.\n\n",
            Exception.class)
        .addJavadoc(
            "<p>If an unchecked exception is thrown on any profile other than the current one,"
                + " a {@link $T} will be thrown on another thread with the original exception"
                + " as the cause.\n\n",
            PROFILE_RUNTIME_EXCEPTION_CLASSNAME)
        .addJavadoc(
            "<p>Only the first result passed in for each profile will be passed into the "
                + "callback.\n\n"
        )
        .addJavadoc("@see $L\n", methodReference);

    interfaceBuilder.addMethod(methodBuilder.build());
  }

  private List<ParameterSpec> convertCallbackParametersIntoMulti(
      List<ParameterSpec> parameters, CrossProfileCallbackParameterInfo callbackParameter) {
    return parameters.stream()
        .map(
            e ->
                e.name.equals(callbackParameter.getSimpleName().toString())
                    ? convertCallbackToMulti(e, callbackParameter.crossProfileCallbackInterface())
                    : e)
        .collect(toList());
  }

  private ParameterSpec convertCallbackToMulti(
      ParameterSpec parameter, CrossProfileCallbackInterfaceInfo callbackInterface) {
    return ParameterSpec.builder(
            CrossProfileCallbackCodeGenerator.getCrossProfileCallbackMultiInterfaceClassName(
                generatorContext, callbackInterface),
            parameter.name)
        .addModifiers(parameter.modifiers)
        .addAnnotations(parameter.annotations)
        .build();
  }

  static ClassName getCrossProfileTypeInterfaceClassName(
      GeneratorContext generatorContext, CrossProfileTypeInfo crossProfileType) {
    return transformClassName(crossProfileType.generatedClassName(), prepend("Profile"));
  }

  static ClassName getSingleSenderInterfaceClassName(
      GeneratorContext generatorContext, CrossProfileTypeInfo crossProfileType) {
    return transformClassName(crossProfileType.generatedClassName(), append("_SingleSender"));
  }

  static ClassName getSingleSenderCanThrowInterfaceClassName(
      GeneratorContext generatorContext, CrossProfileTypeInfo crossProfileType) {
    return transformClassName(
        crossProfileType.generatedClassName(), append("_SingleSenderCanThrow"));
  }

  static ClassName getMultipleSenderInterfaceClassName(
      GeneratorContext generatorContext, CrossProfileTypeInfo crossProfileType) {
    return transformClassName(crossProfileType.generatedClassName(), append("_MultipleSender"));
  }
}
