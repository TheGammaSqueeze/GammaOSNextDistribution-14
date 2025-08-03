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
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.EXCEPTION_CALLBACK_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.containers.CrossProfileMethodInfo.AutomaticallyResolvedParameterFilterBehaviour.REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileMethodInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileTypeInfo;
import com.google.android.enterprise.connectedapps.processor.containers.FutureWrapper;
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;

/**
 * Generate the {@code Profile_*_AlwaysThrows} class for a single cross-profile type.
 *
 * <p>This class is used when running on Pre-O devices to shortcut any cross-profile code and just
 * throw an {@code UnavailableProfileException}.
 *
 * <p>This must only be used once. It should be used after {@link EarlyValidator} has been used to
 * validate that the annotated code is correct.
 */
final class AlwaysThrowsGenerator {

  private boolean generated = false;
  private final GeneratorContext generatorContext;
  private final GeneratorUtilities generatorUtilities;
  private final CrossProfileTypeInfo crossProfileType;

  AlwaysThrowsGenerator(GeneratorContext generatorContext, CrossProfileTypeInfo crossProfileType) {
    this.generatorContext = checkNotNull(generatorContext);
    this.generatorUtilities = new GeneratorUtilities(generatorContext);
    this.crossProfileType = checkNotNull(crossProfileType);
  }

  void generate() {
    if (generated) {
      throw new IllegalStateException("AlwaysThrowsGenerator#generate can only be called once");
    }
    generated = true;

    generateAlwaysThrowsClass();
  }

  private void generateAlwaysThrowsClass() {
    ClassName className = getAlwaysThrowsClassName(generatorContext, crossProfileType);

    ClassName singleSenderCanThrowInterface =
        InterfaceGenerator.getSingleSenderCanThrowInterfaceClassName(
            generatorContext, crossProfileType);

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(className)
            .addJavadoc(
                "Implementation of {@link $T} which throws an {@link $T} for every call.\n",
                singleSenderCanThrowInterface,
                UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(singleSenderCanThrowInterface);

    classBuilder.addField(String.class, "errorMessage", Modifier.PRIVATE, Modifier.FINAL);

    classBuilder.addMethod(
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(String.class, "errorMessage")
            .beginControlFlow("if (errorMessage == null)")
            .addStatement("throw new $T()", NullPointerException.class)
            .endControlFlow()
            .addStatement("this.errorMessage = errorMessage")
            .build());

    ClassName ifAvailableClass =
        IfAvailableGenerator.getIfAvailableClassName(generatorContext, crossProfileType);

    classBuilder.addMethod(
        MethodSpec.methodBuilder("ifAvailable")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ifAvailableClass)
            .addStatement("return new $T(this)", ifAvailableClass)
            .build());

    for (CrossProfileMethodInfo method : crossProfileType.crossProfileMethods()) {
      if (method.isBlocking(generatorContext, crossProfileType)) {
        generateBlockingMethodOnAlwaysThrowsClass(classBuilder, method, crossProfileType);
      } else if (method.isCrossProfileCallback(generatorContext)) {
        generateCrossProfileCallbackMethodOnAlwaysThrowsClass(
            classBuilder, method, crossProfileType);
      } else if (method.isFuture(crossProfileType)) {
        generateFutureMethodOnAlwaysThrowsClass(classBuilder, method, crossProfileType);
      } else {
        throw new IllegalStateException("Unknown method type: " + method);
      }
    }

    generatorUtilities.writeClassToFile(className.packageName(), classBuilder);
  }

  private void generateBlockingMethodOnAlwaysThrowsClass(
      TypeSpec.Builder classBuilder,
      CrossProfileMethodInfo method,
      CrossProfileTypeInfo crossProfileType) {

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(method.simpleName())
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addException(UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME)
            .returns(method.returnTypeTypeName())
            .addParameters(
                GeneratorUtilities.extractParametersFromMethod(
                    crossProfileType.supportedTypes(),
                    method.methodElement(),
                    REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS))
            .addStatement("throw new $T(errorMessage)", UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME);

    classBuilder.addMethod(methodBuilder.build());
  }

  private static void generateCrossProfileCallbackMethodOnAlwaysThrowsClass(
      TypeSpec.Builder classBuilder,
      CrossProfileMethodInfo method,
      CrossProfileTypeInfo crossProfileType) {
    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(method.simpleName())
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(method.returnTypeTypeName())
            .addParameters(
                GeneratorUtilities.extractParametersFromMethod(
                    crossProfileType.supportedTypes(),
                    method.methodElement(),
                    REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS))
            .addParameter(EXCEPTION_CALLBACK_CLASSNAME, "exceptionCallback")
            .addStatement(
                "exceptionCallback.onException(new $T(errorMessage))",
                UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME);

    classBuilder.addMethod(methodBuilder.build());
  }

  private void generateFutureMethodOnAlwaysThrowsClass(
      TypeSpec.Builder classBuilder,
      CrossProfileMethodInfo method,
      CrossProfileTypeInfo crossProfileType) {

    TypeMirror rawFutureType = TypeUtils.removeTypeArguments(method.returnType());

    FutureWrapper futureWrapper =
        crossProfileType.supportedTypes().getType(rawFutureType).getFutureWrapper().get();

    // This assumes futures are only generic on one argument, which is enforced
    TypeMirror wrappedType = TypeUtils.extractTypeArguments(method.returnType()).get(0);
    ParameterizedTypeName futureWrapperType =
        ParameterizedTypeName.get(futureWrapper.wrapperClassName(), ClassName.get(wrappedType));

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(method.simpleName())
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(method.returnTypeTypeName())
            .addParameters(
                GeneratorUtilities.extractParametersFromMethod(
                    crossProfileType.supportedTypes(),
                    method.methodElement(),
                    REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS))
            .addStatement(
                "$1T failedFuture = $2T.create(new $3T(), $4L)",
                futureWrapperType,
                futureWrapper.wrapperClassName(),
                BundlerGenerator.getBundlerClassName(generatorContext, crossProfileType),
                TypeUtils.generateBundlerType(wrappedType))
            .addStatement(
                "failedFuture.onException(new $T(errorMessage))",
                UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME)
            .addStatement("return failedFuture.getFuture()");

    classBuilder.addMethod(methodBuilder.build());
  }

  static ClassName getAlwaysThrowsClassName(
      GeneratorContext generatorContext, CrossProfileTypeInfo crossProfileType) {
    return transformClassName(crossProfileType.generatedClassName(), append("_AlwaysThrows"));
  }
}
