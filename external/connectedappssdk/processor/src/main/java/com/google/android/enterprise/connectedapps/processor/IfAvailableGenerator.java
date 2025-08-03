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
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.IF_AVAILABLE_FUTURE_RESULT_WRITER;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.GeneratorUtilities.generateMethodReference;
import static com.google.android.enterprise.connectedapps.processor.containers.CrossProfileMethodInfo.AutomaticallyResolvedParameterFilterBehaviour.REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileCallbackInterfaceInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileCallbackParameterInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileMethodInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileTypeInfo;
import com.google.android.enterprise.connectedapps.processor.containers.FutureWrapper;
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * Generate the {@code Profile_*_IfAvailable} class for a single cross-profile type.
 *
 * <p>This must only be used once. It should be used after {@link EarlyValidator} has been used to
 * validate that the annotated code is correct.
 */
final class IfAvailableGenerator {

  private boolean generated = false;
  private final GeneratorContext generatorContext;
  private final GeneratorUtilities generatorUtilities;
  private final CrossProfileTypeInfo crossProfileType;

  IfAvailableGenerator(GeneratorContext generatorContext, CrossProfileTypeInfo crossProfileType) {
    this.generatorContext = checkNotNull(generatorContext);
    this.generatorUtilities = new GeneratorUtilities(generatorContext);
    this.crossProfileType = checkNotNull(crossProfileType);
  }

  void generate() {
    if (generated) {
      throw new IllegalStateException("IfAvailableGenerator#generate can only be called once");
    }
    generated = true;

    generateIfAvailableClass();
  }

  private void generateIfAvailableClass() {
    ClassName className = getIfAvailableClassName(generatorContext, crossProfileType);

    ClassName singleSenderCanThrowInterface =
        InterfaceGenerator.getSingleSenderCanThrowInterfaceClassName(
            generatorContext, crossProfileType);
    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(className)
            .addJavadoc(
                "Wrapper of {@link $T} which will replace\n{@link $T} with default values.\n",
                singleSenderCanThrowInterface,
                UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

    classBuilder.addField(
        FieldSpec.builder(singleSenderCanThrowInterface, "singleSenderCanThrow")
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build());

    classBuilder.addMethod(
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(singleSenderCanThrowInterface, "singleSenderCanThrow")
            .addStatement("this.singleSenderCanThrow = singleSenderCanThrow")
            .build());

    for (CrossProfileMethodInfo method : crossProfileType.crossProfileMethods()) {
      generateMethodOnIfAvailableClass(classBuilder, method, crossProfileType);
    }

    generatorUtilities.writeClassToFile(className.packageName(), classBuilder);
  }

  private void generateMethodOnIfAvailableClass(
      TypeSpec.Builder classBuilder,
      CrossProfileMethodInfo method,
      CrossProfileTypeInfo crossProfileType) {

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(method.simpleName())
            .addModifiers(Modifier.PUBLIC)
            .addExceptions(method.thrownExceptions())
            .addParameters(
                GeneratorUtilities.extractParametersFromMethod(
                    crossProfileType.supportedTypes(),
                    method.methodElement(),
                    REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS))
            .addJavadoc("Call {@link $L}.", generateMethodReference(crossProfileType, method));

    if (method.isBlocking(generatorContext, crossProfileType)) {
      if (method.returnType().getKind().equals(TypeKind.VOID)) {
        methodBuilder
            .addJavadoc(
                "\n\n<p>{@link $T} will be ignored.\n", UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME)
            .beginControlFlow("try")
            .addStatement(
                "singleSenderCanThrow.$L($L)",
                method.simpleName(),
                method.commaSeparatedParameters(
                    crossProfileType.supportedTypes(), REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS))
            .nextControlFlow("catch ($T e)", UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME)
            .addComment("Ignore exception")
            .endControlFlow();
      } else {
        methodBuilder.addParameter(method.returnTypeTypeName(), "defaultValue");
        methodBuilder
            .addJavadoc(
                "\n\n<p>In case of {@link $T}, {@code defaultValue} will be returned.\n",
                UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME)
            .returns(method.returnTypeTypeName())
            .beginControlFlow("try")
            .addStatement(
                "return singleSenderCanThrow.$L($L)",
                method.simpleName(),
                method.commaSeparatedParameters(
                    crossProfileType.supportedTypes(), REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS))
            .nextControlFlow("catch ($T e)", UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME)
            .addStatement("return defaultValue")
            .endControlFlow();
      }
    } else if (method.isCrossProfileCallback(generatorContext)) {
      if (!method.isSimpleCrossProfileCallback(generatorContext)) {
        // Non-simple callbacks can't be used with multiple profiles
        return;
      }

      CrossProfileCallbackParameterInfo callbackParam =
          method.getCrossProfileCallbackParam(generatorContext).get();
      CrossProfileCallbackInterfaceInfo callbackInterface =
          callbackParam.crossProfileCallbackInterface();
      if (callbackInterface.argumentTypes().isEmpty()) {
        // Void
        // This assumes a single callback method
        methodBuilder
            .addJavadoc(
                "\n\n<p>If the profile is not available, the callback will be called anyway.\n")
            .addStatement(
                "singleSenderCanThrow.$1L(\n    $2L,\n    (e) -> {$3L.$4L();})",
                method.simpleName(),
                method.commaSeparatedParameters(
                    crossProfileType.supportedTypes(), REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS),
                callbackParam.getSimpleName(),
                callbackInterface.methods().get(0).getSimpleName());
      } else {
        // This assumes a single callback method
        methodBuilder.addParameter(
            ClassName.get(callbackInterface.argumentTypes().iterator().next()), "defaultValue");
        methodBuilder
            .addJavadoc(
                "\n\n"
                    + "<p>If the profile is not available, the callback will be called with the"
                    + " {@code defaultValue}.\n")
            .addStatement(
                "singleSenderCanThrow.$1L(\n    $2L,\n    (e) -> {$3L.$4L(defaultValue);})",
                method.simpleName(),
                method.commaSeparatedParameters(
                    crossProfileType.supportedTypes(), REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS),
                method.getCrossProfileCallbackParam(generatorContext).get().getSimpleName(),
                callbackInterface.methods().get(0).getSimpleName());
      }
    } else if (method.isFuture(crossProfileType)) {
      // This assumes a Future is generic on a single type
      TypeMirror wrappedReturnType = TypeUtils.extractTypeArguments(method.returnType()).get(0);
      TypeMirror rawFutureType = TypeUtils.removeTypeArguments(method.returnType());
      FutureWrapper futureWrapper =
          crossProfileType.supportedTypes().getType(rawFutureType).getFutureWrapper().get();

      methodBuilder
          .addParameter(ClassName.get(wrappedReturnType), "defaultValue")
          .returns(ClassName.get(method.returnType()));
      methodBuilder
          .addJavadoc(
              "\n\n"
                  + "<p>If the profile is not available, the future will be resolved with the"
                  + " {@code defaultValue}.\n")
          .addStatement(
              "$1T internalCrossProfileClass = $1T.instance()",
              InternalCrossProfileClassGenerator.getInternalCrossProfileClassName(
                  generatorContext, crossProfileType))
          .addStatement(
              "$1T<$2T> futureWrapper = $1T.create(internalCrossProfileClass.bundler(), $3L)",
              futureWrapper.wrapperClassName(),
              wrappedReturnType,
              TypeUtils.generateBundlerType(wrappedReturnType))
          .addStatement(
              "$T.writeFutureResult(singleSenderCanThrow.$L($L), new"
                  + " $T<$T>(futureWrapper, defaultValue))",
              futureWrapper.wrapperClassName(),
              method.simpleName(),
              method.commaSeparatedParameters(
                  crossProfileType.supportedTypes(), REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS),
              IF_AVAILABLE_FUTURE_RESULT_WRITER,
              wrappedReturnType)
          .addStatement("return futureWrapper.getFuture()");
    }
    classBuilder.addMethod(methodBuilder.build());
  }

  static ClassName getIfAvailableClassName(
      GeneratorContext generatorContext, CrossProfileTypeInfo crossProfileType) {
    return transformClassName(crossProfileType.generatedClassName(), append("_IfAvailable"));
  }
}
