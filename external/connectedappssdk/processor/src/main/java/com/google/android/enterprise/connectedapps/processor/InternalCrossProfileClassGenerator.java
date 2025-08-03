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
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.BUNDLER_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.BUNDLE_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.BUNDLE_UTILITIES_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.CONTEXT_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.CROSS_PROFILE_CALLBACK_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.CROSS_PROFILE_FUTURE_RESULT_WRITER;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.METHOD_RUNNER_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.containers.CrossProfileMethodInfo.AutomaticallyResolvedParameterFilterBehaviour.REPLACE_AUTOMATICALLY_RESOLVED_PARAMETERS;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.joining;

import com.google.android.enterprise.connectedapps.annotations.CrossProfile;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileCallbackParameterInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileMethodInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileTypeInfo;
import com.google.android.enterprise.connectedapps.processor.containers.FutureWrapper;
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.google.android.enterprise.connectedapps.processor.containers.ProviderClassInfo;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.util.Optional;
import java.util.stream.IntStream;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * Generate the {@code Profile_*_Internal} class for a single {@link CrossProfile} type.
 *
 * <p>This must only be used once. It should be used after {@link EarlyValidator} has been used to
 * validate that the annotated code is correct.
 */
final class InternalCrossProfileClassGenerator {

  private boolean generated = false;
  private final GeneratorContext generatorContext;
  private final GeneratorUtilities generatorUtilities;
  private final ProviderClassInfo providerClass;
  private final CrossProfileTypeInfo crossProfileType;

  InternalCrossProfileClassGenerator(
      GeneratorContext generatorContext,
      ProviderClassInfo providerClass,
      CrossProfileTypeInfo crossProfileType) {
    this.generatorContext = checkNotNull(generatorContext);
    this.generatorUtilities = new GeneratorUtilities(generatorContext);
    this.providerClass = checkNotNull(providerClass);
    this.crossProfileType = checkNotNull(crossProfileType);
  }

  void generate() {
    if (generated) {
      throw new IllegalStateException(
          "InternalCrossProfileClassGenerator#generate can only be called once");
    }
    generated = true;

    generateInternalCrossProfileClass();
  }

  private void generateInternalCrossProfileClass() {
    ClassName className = getInternalCrossProfileClassName(generatorContext, crossProfileType);

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(className).addModifiers(Modifier.PUBLIC, Modifier.FINAL);

    classBuilder.addJavadoc(
        "Internal class for {@link $T}.\n\n"
            + "<p>This is used by the Connected Apps SDK to dispatch cross-profile calls.\n\n"
            + "<p>Cross-profile type identifier: $L.\n",
        crossProfileType.crossProfileTypeElement().asType(),
        crossProfileType.identifier());

    classBuilder.addField(
        FieldSpec.builder(className, "instance")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer("new $T()", className)
            .build());

    classBuilder.addField(
        FieldSpec.builder(BUNDLER_CLASSNAME, "bundler")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer(
                "new $T()",
                BundlerGenerator.getBundlerClassName(generatorContext, crossProfileType))
            .build());

    if (!crossProfileType.isStatic()) {
      ExecutableElement providerMethod =
          providerClass.findProviderMethodFor(generatorContext, crossProfileType);
      String paramsString = providerMethod.getParameters().isEmpty() ? "()" : "(context)";
      CodeBlock providerMethodCall =
          CodeBlock.of("$L$L", providerMethod.getSimpleName(), paramsString);

      classBuilder.addMethod(
          MethodSpec.methodBuilder("crossProfileType")
              .addParameter(CONTEXT_CLASSNAME, "context")
              .returns(crossProfileType.className())
              .addStatement(
                  "return $T.instance().providerClass(context).$L",
                  InternalProviderClassGenerator.getInternalProviderClassName(
                      generatorContext, providerClass),
                  providerMethodCall)
              .build());
    }

    classBuilder.addMethod(
        MethodSpec.methodBuilder("bundler")
            .returns(BUNDLER_CLASSNAME)
            .addStatement("return bundler")
            .build());

    classBuilder.addMethod(
        MethodSpec.methodBuilder("instance")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addJavadoc(
                "Connected-Apps SDK internal use only. This API may be removed at any time.")
            .returns(className)
            .addStatement("return instance")
            .build());

    classBuilder.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

    addMethodsField(classBuilder, crossProfileType);
    addCrossProfileTypeMethods(classBuilder, crossProfileType);
    addCallMethod(classBuilder);

    generatorUtilities.writeClassToFile(className.packageName(), classBuilder);
  }

  private void addMethodsField(
      TypeSpec.Builder classBuilder, CrossProfileTypeInfo crossProfileType) {
    int totalMethods = crossProfileType.crossProfileMethods().size();

    classBuilder.addField(
        FieldSpec.builder(ArrayTypeName.of(METHOD_RUNNER_CLASSNAME), "methods")
            .addModifiers(Modifier.PRIVATE)
            .initializer(
                "new $T[]{$L}",
                METHOD_RUNNER_CLASSNAME,
                IntStream.range(0, totalMethods)
                    .mapToObj(n -> "this::method" + n)
                    .collect(joining(",")))
            .build());
  }

  private void addCrossProfileTypeMethods(
      TypeSpec.Builder classBuilder, CrossProfileTypeInfo crossProfileType) {
    for (CrossProfileMethodInfo method : crossProfileType.crossProfileMethods()) {
      if (method.isBlocking(generatorContext, crossProfileType)) {
        addBlockingCrossProfileTypeMethod(classBuilder, method);
      } else if (method.isCrossProfileCallback(generatorContext)) {
        addCrossProfileCallbackCrossProfileTypeMethod(classBuilder, method);
      } else if (method.isFuture(crossProfileType)) {
        addFutureCrossProfileTypeMethod(classBuilder, method);
      } else {
        throw new IllegalStateException("Unknown method type: " + method);
      }
    }
  }

  private void addBlockingCrossProfileTypeMethod(
      TypeSpec.Builder classBuilder, CrossProfileMethodInfo method) {
    CodeBlock.Builder methodCode = CodeBlock.builder();

    methodCode.addStatement(
        "$1T returnBundle = new $1T($2T.class.getClassLoader())",
        BUNDLE_CLASSNAME,
        BUNDLER_CLASSNAME);

    addExtractParametersCode(methodCode, method);

    CodeBlock methodCall =
        CodeBlock.of(
            "$L.$L($L)",
            getCrossProfileTypeReference(method),
            method.simpleName(),
            method.commaSeparatedParameters(
                crossProfileType.supportedTypes(), REPLACE_AUTOMATICALLY_RESOLVED_PARAMETERS));

    if (!method.thrownExceptions().isEmpty()) {
      methodCode.beginControlFlow("try");
    }

    if (isPrimitiveOrObjectVoid(method.returnType())) {
      methodCode.addStatement(methodCall);
    } else {
      methodCall = CodeBlock.of("$T returnValue = $L", method.returnType(), methodCall);
      methodCode.addStatement(methodCall);
      methodCode.addStatement(
          "bundler.writeToBundle(returnBundle, $S, returnValue, $L)",
          "return",
          TypeUtils.generateBundlerType(method.returnType()));
    }

    if (!method.thrownExceptions().isEmpty()) {
      for (TypeName exceptionType : method.thrownExceptions()) {
        methodCode.nextControlFlow("catch ($L e)", exceptionType);
        methodCode.addStatement(
            "$T.writeThrowableToBundle(returnBundle, $S, e)",
            BUNDLE_UTILITIES_CLASSNAME,
            "throwable");
      }
      methodCode.endControlFlow();
    }

    methodCode.addStatement("return returnBundle");

    classBuilder.addMethod(
        MethodSpec.methodBuilder("method" + method.identifier())
            .addModifiers(Modifier.PRIVATE)
            .returns(BUNDLE_CLASSNAME)
            .addParameter(CONTEXT_CLASSNAME, "context")
            .addParameter(BUNDLE_CLASSNAME, "params")
            .addParameter(CROSS_PROFILE_CALLBACK_CLASSNAME, "callback")
            .addCode(methodCode.build())
            .addJavadoc(
                "Call $1L and return a {@link $2T} containing the return value under the \"return\""
                    + "key.\n",
                GeneratorUtilities.methodJavadocReference(method.methodElement()),
                BUNDLE_CLASSNAME)
            .build());
  }

  private void addCrossProfileCallbackCrossProfileTypeMethod(
      TypeSpec.Builder classBuilder, CrossProfileMethodInfo method) {
    CodeBlock.Builder methodCode = CodeBlock.builder();

    methodCode.addStatement(
        "$1T returnBundle = new $1T($2T.class.getClassLoader())",
        BUNDLE_CLASSNAME,
        BUNDLER_CLASSNAME);

    addExtractParametersCode(methodCode, method);

    createCrossProfileCallbackParameter(methodCode, method);

    CodeBlock methodCall =
        CodeBlock.of(
            "$L.$L($L)",
            getCrossProfileTypeReference(method),
            method.simpleName(),
            method.commaSeparatedParameters(
                crossProfileType.supportedTypes(), REPLACE_AUTOMATICALLY_RESOLVED_PARAMETERS));

    if (isPrimitiveOrObjectVoid(method.returnType())) {
      methodCode.addStatement(methodCall);
    } else {
      methodCall = CodeBlock.of("$T returnValue = $L", method.returnType(), methodCall);
      methodCode.addStatement(methodCall);
      methodCode.addStatement(
          "bundler.writeToBundle(returnBundle, $1S, returnValue, $2L)",
          "return",
          TypeUtils.generateBundlerType(method.returnType()));
    }

    methodCode.addStatement("return returnBundle");

    classBuilder.addMethod(
        MethodSpec.methodBuilder("method" + method.identifier())
            .addModifiers(Modifier.PRIVATE)
            .returns(BUNDLE_CLASSNAME)
            .addParameter(CONTEXT_CLASSNAME, "context")
            .addParameter(BUNDLE_CLASSNAME, "params")
            // TODO: This should be renamed to "callback" once we prefix unpacked parameter names
            //  (without doing this, a param named "callback" will cause a compile error)
            .addParameter(CROSS_PROFILE_CALLBACK_CLASSNAME, "crossProfileCallback")
            .addCode(methodCode.build())
            .addJavadoc(
                "Call $1L, and link the callback to {@code crossProfileCallback}.\n\n"
                    + "@return An empty bundle.\n",
                GeneratorUtilities.methodJavadocReference(method.methodElement()))
            .build());
  }

  private void addFutureCrossProfileTypeMethod(
      TypeSpec.Builder classBuilder, CrossProfileMethodInfo method) {
    CodeBlock.Builder methodCode = CodeBlock.builder();

    methodCode.addStatement(
        "$1T returnBundle = new $1T($2T.class.getClassLoader())",
        BUNDLE_CLASSNAME,
        BUNDLER_CLASSNAME);

    addExtractParametersCode(methodCode, method);

    CodeBlock methodCall =
        CodeBlock.of(
            "$L.$L($L)",
            getCrossProfileTypeReference(method),
            method.simpleName(),
            method.commaSeparatedParameters(
                crossProfileType.supportedTypes(), REPLACE_AUTOMATICALLY_RESOLVED_PARAMETERS));

    methodCode.addStatement("$T future = $L", method.returnType(), methodCall);

    TypeMirror rawFutureType = TypeUtils.removeTypeArguments(method.returnType());

    FutureWrapper futureWrapper =
        crossProfileType.supportedTypes().getType(rawFutureType).getFutureWrapper().get();
    // This assumes every Future is generic with one type argument
    TypeMirror wrappedReturnType =
        TypeUtils.extractTypeArguments(method.returnType()).iterator().next();
    methodCode.addStatement(
        "$T.writeFutureResult(future, new $T<>(callback, bundler, $L))",
        futureWrapper.wrapperClassName(),
        CROSS_PROFILE_FUTURE_RESULT_WRITER,
        TypeUtils.generateBundlerType(wrappedReturnType));

    methodCode.addStatement("return returnBundle");

    classBuilder.addMethod(
        MethodSpec.methodBuilder("method" + method.identifier())
            .addModifiers(Modifier.PRIVATE)
            .returns(BUNDLE_CLASSNAME)
            .addParameter(CONTEXT_CLASSNAME, "context")
            .addParameter(BUNDLE_CLASSNAME, "params")
            .addParameter(CROSS_PROFILE_CALLBACK_CLASSNAME, "callback")
            .addCode(methodCode.build())
            .addJavadoc(
                "Call $1L, and link the returned future to {@code crossProfileCallback}.\n\n"
                    + "@return An empty bundle.\n",
                GeneratorUtilities.methodJavadocReference(method.methodElement()))
            .build());
  }

  private void createCrossProfileCallbackParameter(
      CodeBlock.Builder methodCode, CrossProfileMethodInfo method) {
    CrossProfileCallbackParameterInfo callbackParameter =
        method.getCrossProfileCallbackParam(generatorContext).get();

    methodCode.addStatement(
        "$T $L = new $L(crossProfileCallback, bundler)",
        callbackParameter.variable().asType(),
        callbackParameter.getSimpleName(),
        CrossProfileCallbackCodeGenerator.getCrossProfileCallbackReceiverClassName(
            generatorContext, callbackParameter.crossProfileCallbackInterface()));
  }

  private static boolean isPrimitiveOrObjectVoid(TypeMirror typeMirror) {
    return typeMirror.getKind().equals(TypeKind.VOID)
        || typeMirror.toString().equals("java.lang.Void");
  }

  private void addExtractParametersCode(CodeBlock.Builder code, CrossProfileMethodInfo method) {
    Optional<CrossProfileCallbackParameterInfo> callbackParameter =
        method.getCrossProfileCallbackParam(generatorContext);
    for (VariableElement parameter : method.methodElement().getParameters()) {
      if (callbackParameter.isPresent()
          && callbackParameter.get().variable().getSimpleName().equals(parameter.getSimpleName())) {
        continue; // Don't extract a callback parameter
      }
      if (crossProfileType.supportedTypes().isAutomaticallyResolved(parameter.asType())) {
        continue;
      }
      code.addStatement(
          "@SuppressWarnings(\"unchecked\") $1T $2L = ($1T) bundler.readFromBundle(params, $2S,"
              + " $3L)",
          parameter.asType(),
          parameter.getSimpleName().toString(),
          TypeUtils.generateBundlerType(parameter.asType()));
    }
  }

  private static void addCallMethod(TypeSpec.Builder classBuilder) {
    classBuilder.addMethod(
        MethodSpec.methodBuilder("call")
            .addModifiers(Modifier.PUBLIC)
            .returns(BUNDLE_CLASSNAME)
            .addParameter(CONTEXT_CLASSNAME, "context")
            .addParameter(int.class, "methodIdentifier")
            .addParameter(BUNDLE_CLASSNAME, "params")
            .addParameter(CROSS_PROFILE_CALLBACK_CLASSNAME, "callback")
            .beginControlFlow("if (methodIdentifier >= methods.length)")
            .addStatement(
                "throw new $T(\"Invalid method identifier\" + methodIdentifier)",
                IllegalArgumentException.class)
            .endControlFlow()
            .addStatement("return methods[methodIdentifier].call(context, params, callback)")
            .addJavadoc(
                "Call the method referenced by {@code methodIdentifier}.\n\n"
                    + "<p>If the method is synchronous, this will return a {@link $1T} containing"
                    + " the return value under the key \"return\", otherwise it will return an"
                    + " empty {@link $1T}.\n",
                BUNDLE_CLASSNAME)
            .build());
  }

  private CodeBlock getCrossProfileTypeReference(CrossProfileMethodInfo method) {
    if (method.isStatic()) {
      return CodeBlock.of("$1T", crossProfileType.className());
    }
    return CodeBlock.of("crossProfileType(context)");
  }

  static ClassName getInternalCrossProfileClassName(
      GeneratorContext generatorContext, CrossProfileTypeInfo crossProfileType) {
    return transformClassName(crossProfileType.generatedClassName(), append("_Internal"));
  }
}

