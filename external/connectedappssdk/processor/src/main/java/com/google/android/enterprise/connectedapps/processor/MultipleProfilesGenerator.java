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
import static com.google.android.enterprise.connectedapps.processor.ClassNameUtilities.transformClassName;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.ASYNC_CALLBACK_PARAM_MULTIMERGER_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.CALLBACK_MERGER_EXCEPTION_CALLBACK_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.PROFILE_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.containers.CrossProfileMethodInfo.AutomaticallyResolvedParameterFilterBehaviour.REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toList;

import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileCallbackInterfaceInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileCallbackParameterInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileMethodInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileTypeInfo;
import com.google.android.enterprise.connectedapps.processor.containers.FutureWrapper;
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * Generate the {@code Profile_*_MultipleProfiles} class for a single cross-profile type.
 *
 * <p>This must only be used once. It should be used after {@link EarlyValidator} has been used to
 * validate that the annotated code is correct.
 */
final class MultipleProfilesGenerator {

  private boolean generated = false;
  private final GeneratorContext generatorContext;
  private final GeneratorUtilities generatorUtilities;
  private final CrossProfileTypeInfo crossProfileType;

  MultipleProfilesGenerator(
      GeneratorContext generatorContext, CrossProfileTypeInfo crossProfileType) {
    this.generatorContext = checkNotNull(generatorContext);
    this.generatorUtilities = new GeneratorUtilities(generatorContext);
    this.crossProfileType = checkNotNull(crossProfileType);
  }

  void generate() {
    if (generated) {
      throw new IllegalStateException("MultipleProfilesGenerator#generate can only be called once");
    }
    generated = true;

    generateMultipleProfilesClass();
  }

  private void generateMultipleProfilesClass() {
    ClassName className = getMultipleProfilesClassName(generatorContext, crossProfileType);

    ClassName multipleSenderInterface =
        InterfaceGenerator.getMultipleSenderInterfaceClassName(generatorContext, crossProfileType);
    ClassName singleSenderCanThrowInterfaceName =
        InterfaceGenerator.getSingleSenderCanThrowInterfaceClassName(
            generatorContext, crossProfileType);

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(className)
            .addJavadoc(
                "Default implementation of {@link $T}.\n\n"
                    + "<p>Wraps a number of {@link $T} instances and merges their return"
                    + " values.\n",
                multipleSenderInterface,
                singleSenderCanThrowInterfaceName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(multipleSenderInterface);

    ParameterizedTypeName senderMapType =
        ParameterizedTypeName.get(
            ClassName.get(Map.class), PROFILE_CLASSNAME, singleSenderCanThrowInterfaceName);

    classBuilder.addField(senderMapType, "senders", Modifier.PRIVATE, Modifier.FINAL);

    classBuilder.addMethod(
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(senderMapType, "senders")
            .addStatement("this.senders = senders")
            .build());

    for (CrossProfileMethodInfo method : crossProfileType.crossProfileMethods()) {
      if (method.isBlocking(generatorContext, crossProfileType)) {
        generateBlockingMethodOnMultipleProfilesClass(classBuilder, method, crossProfileType);
      } else if (method.isCrossProfileCallback(generatorContext)) {
        generateCrossProfileCallbackMethodOnMultipleProfilesClass(
            classBuilder, method, crossProfileType);
      } else if (method.isFuture(crossProfileType)) {
        generateFutureMethodOnMultipleProfilesClass(classBuilder, method, crossProfileType);
      } else {
        throw new IllegalStateException("Unknown method type: " + method);
      }
    }

    generatorUtilities.writeClassToFile(className.packageName(), classBuilder);
  }

  private void generateBlockingMethodOnMultipleProfilesClass(
      TypeSpec.Builder classBuilder,
      CrossProfileMethodInfo method,
      CrossProfileTypeInfo crossProfileType) {

    if (!method.thrownExceptions().isEmpty()) {
      // We don't add methods with exceptions to multiple profiles
      return;
    }

    TypeName returnType;
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

    String methodName = method.simpleName();
    String params =
        method.commaSeparatedParameters(
            crossProfileType.supportedTypes(), REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS);

    CodeBlock methodCall = CodeBlock.of("$L($L)", methodName, params);

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(methodName)
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(returnType)
            .addParameters(
                GeneratorUtilities.extractParametersFromMethod(
                    crossProfileType.supportedTypes(),
                    method.methodElement(),
                    REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS));

    if (method.returnType().getKind().equals(TypeKind.VOID)) {
      methodBuilder.beginControlFlow(
          "for ($T sender : senders.values())",
          InterfaceGenerator.getSingleSenderCanThrowInterfaceClassName(
              generatorContext, crossProfileType));
      methodBuilder.beginControlFlow("try");
      methodBuilder.addStatement("sender.$L", methodCall);
      methodBuilder.nextControlFlow("catch ($T e)", UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME);
      methodBuilder.addComment(
          "If the profile is not available we just don't include it in results");
      methodBuilder.endControlFlow();
      methodBuilder.endControlFlow();
    } else {
      methodBuilder.addStatement("$T returnValue = new $T<>()", returnType, HashMap.class);
      methodBuilder.beginControlFlow(
          "for ($T senderIdentifier : senders.keySet())", PROFILE_CLASSNAME);
      methodBuilder.addStatement(
          "$T sender = senders.get(senderIdentifier)",
          InterfaceGenerator.getSingleSenderCanThrowInterfaceClassName(
              generatorContext, crossProfileType));
      methodBuilder.beginControlFlow("try");
      methodBuilder.addStatement("returnValue.put(senderIdentifier, sender.$L)", methodCall);
      methodBuilder.nextControlFlow("catch ($T e)", UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME);
      methodBuilder.addComment(
          "If the profile is not available we just don't include it in results");
      methodBuilder.endControlFlow();
      methodBuilder.endControlFlow();
      methodBuilder.addStatement("return returnValue");
    }

    classBuilder.addMethod(methodBuilder.build());
  }

  private void generateCrossProfileCallbackMethodOnMultipleProfilesClass(
      TypeSpec.Builder classBuilder,
      CrossProfileMethodInfo method,
      CrossProfileTypeInfo crossProfileType) {

    if (!method.isSimpleCrossProfileCallback(generatorContext)) {
      // Non-simple callbacks can't be used with multiple profiles
      return;
    }

    String methodName = method.simpleName();

    CrossProfileCallbackParameterInfo callbackParameter =
        method.getCrossProfileCallbackParam(generatorContext).get();
    CrossProfileCallbackInterfaceInfo callbackInterface =
        callbackParameter.crossProfileCallbackInterface();

    List<ParameterSpec> parameters =
        convertCallbackParametersIntoMulti(
            GeneratorUtilities.extractParametersFromMethod(
                crossProfileType.supportedTypes(),
                method.methodElement(),
                REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS),
            callbackParameter);

    TypeMirror paramType =
        callbackInterface.methods().get(0).getParameters().isEmpty()
            ? generatorContext.elements().getTypeElement("java.lang.Void").asType()
            : callbackInterface.methods().get(0).getParameters().get(0).asType();

    if (paramType.getKind().isPrimitive()) {
      PrimitiveType primitiveType = (PrimitiveType) paramType;
      paramType = generatorContext.types().boxedClass(primitiveType).asType();
    }

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(methodName)
            .addModifiers(Modifier.PUBLIC)
            .addParameters(parameters)
            .addStatement(
                "$1T mergedResultListener = new $1T($2L)",
                CrossProfileCallbackCodeGenerator.getCrossProfileCallbackMultiMergerResultClassName(
                    generatorContext, callbackInterface),
                callbackParameter.getSimpleName())
            .addStatement(
                "$1T<$2T> merger = new $1T<>(senders.size(), mergedResultListener)",
                ASYNC_CALLBACK_PARAM_MULTIMERGER_CLASSNAME,
                paramType);

    String params =
        method.commaSeparatedParameters(
            crossProfileType.supportedTypes(),
            REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS,
            (p) ->
                callbackParameter.getSimpleName().contentEquals(p)
                    ? generateMergerInputConstructor(callbackInterface)
                    : p);

    methodBuilder.beginControlFlow(
        "for ($T senderIdentifier : senders.keySet())", PROFILE_CLASSNAME);
    methodBuilder.addStatement(
        "$T sender = senders.get(senderIdentifier)",
        InterfaceGenerator.getSingleSenderCanThrowInterfaceClassName(
            generatorContext, crossProfileType));

    methodBuilder.addStatement(
        "sender.$L($L, new $T<$T>(senderIdentifier, merger))",
        methodName,
        params,
        CALLBACK_MERGER_EXCEPTION_CALLBACK_CLASSNAME,
        paramType);
    methodBuilder.endControlFlow();

    classBuilder.addMethod(methodBuilder.build());
  }

  private void generateFutureMethodOnMultipleProfilesClass(
      TypeSpec.Builder classBuilder,
      CrossProfileMethodInfo method,
      CrossProfileTypeInfo crossProfileType) {

    ClassName rawFutureType = TypeUtils.getRawTypeClassName(method.returnType());
    FutureWrapper futureWrapper =
        crossProfileType
            .supportedTypes()
            .getType(TypeUtils.removeTypeArguments(method.returnType()))
            .getFutureWrapper()
            .get();
    // We assume all Futures are generic with a single generic type
    TypeMirror wrappedType = TypeUtils.extractTypeArguments(method.returnType()).iterator().next();

    TypeMirror boxedWrappedType = generatorUtilities.boxIfNecessary(wrappedType);

    TypeName mapType =
        ParameterizedTypeName.get(
            ClassName.get(Map.class), PROFILE_CLASSNAME, ClassName.get(boxedWrappedType));

    ParameterizedTypeName returnType = ParameterizedTypeName.get(rawFutureType, mapType);

    String methodName = method.simpleName();
    String params =
        method.commaSeparatedParameters(
            crossProfileType.supportedTypes(), REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS);

    CodeBlock methodCall = CodeBlock.of("$L($L)", methodName, params);

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(methodName)
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(returnType)
            .addParameters(
                GeneratorUtilities.extractParametersFromMethod(
                    crossProfileType.supportedTypes(),
                    method.methodElement(),
                    REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS));

    methodBuilder.addStatement(
        "$T<$T, $T> results = new $T<>()",
        Map.class,
        PROFILE_CLASSNAME,
        method.returnType(),
        HashMap.class);
    methodBuilder.beginControlFlow(
        "for ($T senderIdentifier : senders.keySet())", PROFILE_CLASSNAME);
    methodBuilder.addStatement(
        "$T sender = senders.get(senderIdentifier)",
        InterfaceGenerator.getSingleSenderCanThrowInterfaceClassName(
            generatorContext, crossProfileType));
    methodBuilder.addStatement("results.put(senderIdentifier, sender.$L)", methodCall);
    methodBuilder.endControlFlow();
    methodBuilder.addStatement("return $T.groupResults(results)", futureWrapper.wrapperClassName());

    classBuilder.addMethod(methodBuilder.build());
  }

  private List<ParameterSpec> convertCallbackParametersIntoMulti(
      List<ParameterSpec> parameters, CrossProfileCallbackParameterInfo callbackParam) {
    return parameters.stream()
        .map(
            e ->
                e.name.equals(callbackParam.getSimpleName().toString())
                    ? convertCallbackToMulti(e, callbackParam.crossProfileCallbackInterface())
                    : e)
        .collect(toList());
  }

  private String generateMergerInputConstructor(
      CrossProfileCallbackInterfaceInfo callbackInterface) {
    return CodeBlock.of(
            "new $T(senderIdentifier, merger)",
            CrossProfileCallbackCodeGenerator.getCrossProfileCallbackMultiMergerInputClassName(
                generatorContext, callbackInterface))
        .toString();
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

  static ClassName getMultipleProfilesClassName(
      GeneratorContext generatorContext, CrossProfileTypeInfo crossProfileType) {
    return transformClassName(crossProfileType.generatedClassName(), append("_MultipleProfiles"));
  }
}
