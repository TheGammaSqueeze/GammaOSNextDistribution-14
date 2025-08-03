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
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.CONTEXT_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.EXCEPTION_CALLBACK_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.containers.CrossProfileMethodInfo.AutomaticallyResolvedParameterFilterBehaviour.REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS;
import static com.google.android.enterprise.connectedapps.processor.containers.CrossProfileMethodInfo.AutomaticallyResolvedParameterFilterBehaviour.REPLACE_AUTOMATICALLY_RESOLVED_PARAMETERS;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileMethodInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileTypeInfo;
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeKind;

/**
 * Generate the {@code Profile_*_CurrentProfile} class for a single crossProfileType class.
 *
 * <p>This must only be used once. It should be used after {@link EarlyValidator} has been used to
 * validate that the annotated code is correct.
 */
final class CurrentProfileGenerator {

  private boolean generated = false;
  private final GeneratorContext generatorContext;
  private final GeneratorUtilities generatorUtilities;
  private final CrossProfileTypeInfo crossProfileType;

  CurrentProfileGenerator(
      GeneratorContext generatorContext, CrossProfileTypeInfo crossProfileType) {
    this.generatorContext = checkNotNull(generatorContext);
    this.generatorUtilities = new GeneratorUtilities(generatorContext);
    this.crossProfileType = checkNotNull(crossProfileType);
  }

  void generate() {
    if (generated) {
      throw new IllegalStateException("CurrentProfileGenerator#generate can only be called once");
    }
    generated = true;

    generateCurrentProfileClass();
  }

  private void generateCurrentProfileClass() {
    ClassName className = getCurrentProfileClassName(generatorContext, crossProfileType);

    ClassName singleSenderInterface =
        InterfaceGenerator.getSingleSenderInterfaceClassName(generatorContext, crossProfileType);
    ClassName singleSenderCanThrowInterface =
        InterfaceGenerator.getSingleSenderCanThrowInterfaceClassName(
            generatorContext, crossProfileType);

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(className)
            .addJavadoc(
                "Implementation of {@link $T} and {@link $T} which makes calls to the current"
                    + " profile.\n\n"
                    + "<p>{@link $T} will not be thrown by calls to methods in this class.\n",
                singleSenderInterface,
                singleSenderCanThrowInterface,
                UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(singleSenderInterface)
            .addSuperinterface(singleSenderCanThrowInterface);

    addCrossProfileConstructor(classBuilder);

    for (CrossProfileMethodInfo method : crossProfileType.crossProfileMethods()) {
      generateMethodOnCurrentProfileClass(classBuilder, method, crossProfileType);

      if (method.isCrossProfileCallback(generatorContext)) {
        // To meet the interface for canThrow we need a version with exceptionCallback.
        // However we never use it.
        generateCrossProfileCallbackWithExceptionMethodOnCurrentProfileClass(
            classBuilder, method, crossProfileType);
      }
    }

    ClassName ifAvailableClass =
        IfAvailableGenerator.getIfAvailableClassName(generatorContext, crossProfileType);

    classBuilder.addMethod(
        MethodSpec.methodBuilder("ifAvailable")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ifAvailableClass)
            .addStatement("return new $T(this)", ifAvailableClass)
            .build());

    generatorUtilities.writeClassToFile(className.packageName(), classBuilder);
  }

  private void addCrossProfileConstructor(TypeSpec.Builder classBuilder) {
    classBuilder.addField(
        FieldSpec.builder(CONTEXT_CLASSNAME, "context")
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build());

    MethodSpec.Builder constructorBuilder =
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(CONTEXT_CLASSNAME, "context")
            .addStatement("this.context = context");

    if (!crossProfileType.isStatic()) {
      classBuilder.addField(
          FieldSpec.builder(crossProfileType.className(), "crossProfileType")
              .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
              .build());

      constructorBuilder
          .addParameter(crossProfileType.className(), "crossProfileType")
          .addStatement("this.crossProfileType = crossProfileType");
    }

    classBuilder.addMethod(constructorBuilder.build());
  }

  private void generateMethodOnCurrentProfileClass(
      TypeSpec.Builder classBuilder,
      CrossProfileMethodInfo method,
      CrossProfileTypeInfo crossProfileType) {

    CodeBlock crossProfileTypeReference =
        method.isStatic()
            ? CodeBlock.of("$1T", crossProfileType.className())
            : CodeBlock.of("crossProfileType");

    CodeBlock methodCall =
        CodeBlock.of(
            "$L.$L($L)",
            crossProfileTypeReference,
            method.simpleName(),
            method.commaSeparatedParameters(
                crossProfileType.supportedTypes(), REPLACE_AUTOMATICALLY_RESOLVED_PARAMETERS));

    if (method.returnType().getKind() != TypeKind.VOID) {
      methodCall = CodeBlock.of("return $L", methodCall);
    }

    classBuilder.addMethod(
        MethodSpec.methodBuilder(method.simpleName())
            .addAnnotation(Override.class)
            .addExceptions(method.thrownExceptions())
            .addModifiers(Modifier.PUBLIC)
            .returns(method.returnTypeTypeName())
            .addParameters(
                GeneratorUtilities.extractParametersFromMethod(
                    crossProfileType.supportedTypes(),
                    method.methodElement(),
                    REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS))
            .addStatement(methodCall)
            .build());
  }

  private static void generateCrossProfileCallbackWithExceptionMethodOnCurrentProfileClass(
      TypeSpec.Builder classBuilder,
      CrossProfileMethodInfo method,
      CrossProfileTypeInfo crossProfileType) {

    classBuilder.addMethod(
        MethodSpec.methodBuilder(method.simpleName())
            .addModifiers(Modifier.PUBLIC)
            .returns(method.returnTypeTypeName())
            .addParameters(
                GeneratorUtilities.extractParametersFromMethod(
                    crossProfileType.supportedTypes(),
                    method.methodElement(),
                    REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS))
            .addParameter(EXCEPTION_CALLBACK_CLASSNAME, "exceptionCallback")
            .addStatement(
                "$L($L)",
                method.simpleName(),
                method.commaSeparatedParameters(
                    crossProfileType.supportedTypes(), REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS))
            .build());
  }

  static ClassName getCurrentProfileClassName(
      GeneratorContext generatorContext, CrossProfileTypeInfo crossProfileType) {
    return transformClassName(crossProfileType.generatedClassName(), append("_CurrentProfile"));
  }
}
