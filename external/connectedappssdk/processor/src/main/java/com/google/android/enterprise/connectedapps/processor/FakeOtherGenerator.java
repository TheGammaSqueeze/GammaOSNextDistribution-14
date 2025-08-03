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
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.FAKE_PROFILE_CONNECTOR_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.PROFILE_RUNTIME_EXCEPTION_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.containers.CrossProfileMethodInfo.AutomaticallyResolvedParameterFilterBehaviour.REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS;
import static com.google.android.enterprise.connectedapps.processor.containers.CrossProfileMethodInfo.AutomaticallyResolvedParameterFilterBehaviour.REPLACE_AUTOMATICALLY_RESOLVED_PARAMETERS;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileMethodInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileTypeInfo;
import com.google.android.enterprise.connectedapps.processor.containers.FutureWrapper;
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * Generate the {@code Profile_*_FakeOther} class for a single cross-profile type.
 *
 * <p>This must only be used once. It should be used after {@link EarlyValidator} has been used to
 * validate that the annotated code is correct.
 */
final class FakeOtherGenerator {

  private boolean generated = false;
  private final GeneratorContext generatorContext;
  private final GeneratorUtilities generatorUtilities;
  private final CrossProfileTypeInfo crossProfileType;

  FakeOtherGenerator(GeneratorContext generatorContext, CrossProfileTypeInfo crossProfileType) {
    this.generatorContext = checkNotNull(generatorContext);
    this.generatorUtilities = new GeneratorUtilities(generatorContext);
    this.crossProfileType = checkNotNull(crossProfileType);
  }

  void generate() {
    if (generated) {
      throw new IllegalStateException("FakeSingleSenderGenerator#generate can only be called once");
    }
    generated = true;

    generateFakeOther();
  }

  private void generateFakeOther() {
    ClassName className = getFakeOtherClassName(generatorContext, crossProfileType);

    ClassName singleSenderCanThrowInterface =
        InterfaceGenerator.getSingleSenderCanThrowInterfaceClassName(
            generatorContext, crossProfileType);

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(className)
            .addJavadoc(
                "Fake implementation of {@link $T} for use during tests.\n\n"
                    + "<p>This acts based on the state of the passed in {@link $T} and acts as if"
                    + " making a call on the other profile.\n",
                singleSenderCanThrowInterface,
                FAKE_PROFILE_CONNECTOR_CLASSNAME)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(singleSenderCanThrowInterface);

    classBuilder.addField(
        FAKE_PROFILE_CONNECTOR_CLASSNAME, "connector", Modifier.PRIVATE, Modifier.FINAL);

    addConstructor(classBuilder);

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
        generateBlockingMethodOnFakeOther(classBuilder, method, crossProfileType);
      } else if (method.isCrossProfileCallback(generatorContext)) {
        generateCrossProfileCallbackMethodOnFakeOther(classBuilder, method, crossProfileType);
      } else if (method.isFuture(crossProfileType)) {
        generateFutureMethodOnFakeOther(classBuilder, method, crossProfileType);
      } else {
        throw new IllegalStateException("Unknown method type: " + method);
      }
    }

    generatorUtilities.writeClassToFile(className.packageName(), classBuilder);
  }

  private void addConstructor(TypeSpec.Builder classBuilder) {
    classBuilder.addField(CONTEXT_CLASSNAME, "context", Modifier.PRIVATE, Modifier.FINAL);

    if (crossProfileType.isStatic()) {
      classBuilder.addMethod(
          MethodSpec.constructorBuilder()
              .addModifiers(Modifier.PUBLIC)
              .addParameter(FAKE_PROFILE_CONNECTOR_CLASSNAME, "connector")
              .addStatement("this.context = connector.applicationContext()")
              .addStatement("this.connector = connector")
              .build());
    } else {
      classBuilder.addField(
          crossProfileType.className(), "crossProfileType", Modifier.PRIVATE, Modifier.FINAL);

      classBuilder.addMethod(
          MethodSpec.constructorBuilder()
              .addModifiers(Modifier.PUBLIC)
              .addParameter(FAKE_PROFILE_CONNECTOR_CLASSNAME, "connector")
              .addParameter(crossProfileType.className(), "crossProfileType")
              .addStatement("this.context = connector.applicationContext()")
              .addStatement("this.connector = connector")
              .addStatement("this.crossProfileType = crossProfileType")
              .build());
    }
  }

  private void generateBlockingMethodOnFakeOther(
      TypeSpec.Builder classBuilder,
      CrossProfileMethodInfo method,
      CrossProfileTypeInfo crossProfileType) {

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(method.simpleName())
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addExceptions(method.thrownExceptions())
            .addException(UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME)
            .returns(method.returnTypeTypeName())
            .addParameters(
                GeneratorUtilities.extractParametersFromMethod(
                    crossProfileType.supportedTypes(),
                    method.methodElement(),
                    REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS));

    CodeBlock methodCall =
        CodeBlock.of(
            "$L.$L($L)",
            getCrossProfileTypeReference(method, crossProfileType),
            method.simpleName(),
            method.commaSeparatedParameters(
                crossProfileType.supportedTypes(), REPLACE_AUTOMATICALLY_RESOLVED_PARAMETERS));

    if (method.returnType().getKind() != TypeKind.VOID) {
      methodCall = CodeBlock.of("return $L", methodCall);
    }

    methodBuilder.beginControlFlow("if (!connector.isConnected())");
    methodBuilder.addStatement(
        "throw new $T($S)",
        UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME,
        "Could not access other profile");
    methodBuilder.endControlFlow();

    methodBuilder.beginControlFlow("if (!connector.hasExplicitConnectionHolders())");
    methodBuilder.addStatement(
        "throw new $T($S)",
        UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME,
        "Synchronous calls can only be used when manually connected");
    methodBuilder.endControlFlow();

    methodBuilder.beginControlFlow("try");
    methodBuilder.addStatement(methodCall);
    methodBuilder.nextControlFlow("catch ($T e)", RuntimeException.class);
    methodBuilder.addStatement("throw new $T(e)", PROFILE_RUNTIME_EXCEPTION_CLASSNAME);
    methodBuilder.nextControlFlow("catch ($T e)", Error.class);
    methodBuilder.addStatement("throw new $T(e)", PROFILE_RUNTIME_EXCEPTION_CLASSNAME);
    methodBuilder.endControlFlow();
    classBuilder.addMethod(methodBuilder.build());
  }

  private static CodeBlock getCrossProfileTypeReference(
      CrossProfileMethodInfo method, CrossProfileTypeInfo crossProfileType) {
    return method.isStatic()
        ? CodeBlock.of("$1T", crossProfileType.className())
        : CodeBlock.of("crossProfileType");
  }

  private void generateCrossProfileCallbackMethodOnFakeOther(
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
            .addParameter(EXCEPTION_CALLBACK_CLASSNAME, "exceptionCallback");

    CodeBlock methodCall =
        CodeBlock.of(
            "$L.$L($L)",
            getCrossProfileTypeReference(method, crossProfileType),
            method.simpleName(),
            method.commaSeparatedParameters(
                crossProfileType.supportedTypes(), REPLACE_AUTOMATICALLY_RESOLVED_PARAMETERS));

    if (method.returnType().getKind() != TypeKind.VOID) {
      methodCall = CodeBlock.of("return $L", methodCall);
    }

    methodBuilder.beginControlFlow("if (!connector.isAvailable())");
    methodBuilder.addStatement(
        "exceptionCallback.onException(new $T($S))",
        UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME,
        "Could not access other profile");
    methodBuilder.addStatement("return");
    methodBuilder.endControlFlow();

    methodBuilder.addStatement("connector.automaticallyConnect()");
    methodBuilder.beginControlFlow("try");
    methodBuilder.addStatement(methodCall);
    methodBuilder.nextControlFlow("catch ($T e)", RuntimeException.class);
    methodBuilder.addStatement("throw new $T(e)", PROFILE_RUNTIME_EXCEPTION_CLASSNAME);
    methodBuilder.nextControlFlow("catch ($T e)", Error.class);
    methodBuilder.addStatement("throw new $T(e)", PROFILE_RUNTIME_EXCEPTION_CLASSNAME);
    methodBuilder.endControlFlow();

    classBuilder.addMethod(methodBuilder.build());
  }

  private void generateFutureMethodOnFakeOther(
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
                    REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS));

    CodeBlock methodCall =
        CodeBlock.of(
            "$L.$L($L)",
            getCrossProfileTypeReference(method, crossProfileType),
            method.simpleName(),
            method.commaSeparatedParameters(
                crossProfileType.supportedTypes(), REPLACE_AUTOMATICALLY_RESOLVED_PARAMETERS));

    if (method.returnType().getKind() != TypeKind.VOID) {
      methodCall = CodeBlock.of("$1T returnValue = $2L", method.returnType(), methodCall);
    }

    TypeMirror rawFutureType = TypeUtils.removeTypeArguments(method.returnType());

    FutureWrapper futureWrapper =
        crossProfileType.supportedTypes().getType(rawFutureType).getFutureWrapper().get();

    // This assumes futures are only generic on one argument, which is enforced
    TypeMirror wrappedType = TypeUtils.extractTypeArguments(method.returnType()).get(0);
    ParameterizedTypeName futureWrapperType =
        ParameterizedTypeName.get(futureWrapper.wrapperClassName(), ClassName.get(wrappedType));

    methodBuilder.beginControlFlow("if (!connector.isAvailable())");
    methodBuilder.addStatement(
        "$1T failedFuture = $2T.create(new $3T(), $4L)",
        futureWrapperType,
        futureWrapper.wrapperClassName(),
        BundlerGenerator.getBundlerClassName(generatorContext, crossProfileType),
        TypeUtils.generateBundlerType(wrappedType));
    methodBuilder.addStatement(
        "failedFuture.onException(new $1T($2S))",
        UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME,
        "Could not access other profile");
    methodBuilder.addStatement("return failedFuture.getFuture()");
    methodBuilder.endControlFlow();

    methodBuilder.beginControlFlow("try");
    methodBuilder.addStatement("connector.automaticallyConnect()");
    methodBuilder.addStatement(methodCall);
    if (method.returnType().getKind() != TypeKind.VOID) {
      methodBuilder.addStatement("return returnValue");
    }
    methodBuilder.nextControlFlow("catch ($T e)", RuntimeException.class);
    methodBuilder.addStatement("throw new $T(e)", PROFILE_RUNTIME_EXCEPTION_CLASSNAME);
    methodBuilder.nextControlFlow("catch ($T e)", Error.class);
    methodBuilder.addStatement("throw new $T(e)", PROFILE_RUNTIME_EXCEPTION_CLASSNAME);
    methodBuilder.endControlFlow();

    classBuilder.addMethod(methodBuilder.build());
  }

  static ClassName getFakeOtherClassName(
      GeneratorContext generatorContext, CrossProfileTypeInfo crossProfileType) {
    return transformClassName(crossProfileType.generatedClassName(), append("_FakeOther"));
  }
}
