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

import static com.google.android.enterprise.connectedapps.processor.ClassNameUtilities.classNameInferringPackageFromElement;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.ASYNC_CALLBACK_PARAM_MULTIMERGER_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.ASYNC_CALLBACK_PARAM_MULTIMERGER_COMPLETE_LISTENER_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.BUNDLER_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.BUNDLE_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.BUNDLE_UTILITIES_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.CROSS_PROFILE_CALLBACK_BUNDLE_CALL_SENDER_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.CROSS_PROFILE_CALLBACK_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.CROSS_PROFILE_CALLBACK_EXCEPTION_BUNDLE_CALL_SENDER_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.EXCEPTION_CALLBACK_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.LOCAL_CALLBACK_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.PROFILE_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.joining;

import com.google.android.enterprise.connectedapps.annotations.CrossProfileCallback;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileCallbackInterfaceInfo;
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.util.Map;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/** Generator of code for a single {@link CrossProfileCallback}. */
public class CrossProfileCallbackCodeGenerator {
  private boolean generated = false;
  private final GeneratorContext generatorContext;
  private final GeneratorUtilities generatorUtilities;
  private final CrossProfileCallbackInterfaceInfo callbackInterface;

  private final TypeMirror voidTypeMirror;

  CrossProfileCallbackCodeGenerator(
      GeneratorContext generatorContext, CrossProfileCallbackInterfaceInfo callbackInterface) {
    this.generatorContext = checkNotNull(generatorContext);
    this.callbackInterface = checkNotNull(callbackInterface);
    this.generatorUtilities = new GeneratorUtilities(generatorContext);

    voidTypeMirror = generatorContext.elements().getTypeElement("java.lang.Void").asType();
  }

  void generate() {
    if (generated) {
      throw new IllegalStateException(
          "CrossProfileCallbackCodeGenerator#generate can only be called once");
    }
    generated = true;

    generateReceiverClass();
    generateSenderClass();

    if (callbackInterface.isSimple()) {
      // There can be only one method
      ExecutableElement method = callbackInterface.methods().get(0);
      generateMultiInterface(method);
      generateMultiMergerResultClass(method);
      generateMultiMergerInputClass(method);
    }
  }

  private void generateMultiInterface(ExecutableElement method) {
    ClassName interfaceName =
        getCrossProfileCallbackMultiInterfaceClassName(generatorContext, callbackInterface);

    TypeSpec.Builder interfaceBuilder =
        TypeSpec.interfaceBuilder(interfaceName)
            .addJavadoc(
                "Callback interface used when using a {@link $T} with multiple profiles.\n",
                callbackInterface.interfaceElement())
            .addModifiers(Modifier.PUBLIC);

    addMultiMethod(interfaceBuilder, method);

    generatorUtilities.writeClassToFile(interfaceName.packageName(), interfaceBuilder);
  }

  private void generateMultiMergerResultClass(ExecutableElement method) {
    ClassName className =
        getCrossProfileCallbackMultiMergerResultClassName(generatorContext, callbackInterface);

    TypeMirror paramType =
        method.getParameters().isEmpty() ? voidTypeMirror : method.getParameters().get(0).asType();

    TypeName mergerInterface =
        ParameterizedTypeName.get(
            ASYNC_CALLBACK_PARAM_MULTIMERGER_COMPLETE_LISTENER_CLASSNAME,
            ClassName.get(generatorUtilities.boxIfNecessary(paramType)));

    ParameterizedTypeName multiParameterType = getMultiParameterType(paramType);

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(className)
            .addJavadoc(
                "Implementation of {@link $T} which forwards completed results to an instance of"
                    + " {@link $T}.\n",
                ASYNC_CALLBACK_PARAM_MULTIMERGER_COMPLETE_LISTENER_CLASSNAME,
                callbackInterface.interfaceElement())
            .addSuperinterface(mergerInterface)
            .addModifiers(Modifier.PUBLIC);

    classBuilder.addField(
        FieldSpec.builder(
                getCrossProfileCallbackMultiInterfaceClassName(generatorContext, callbackInterface),
                "callback")
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build());

    classBuilder.addMethod(
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(
                getCrossProfileCallbackMultiInterfaceClassName(generatorContext, callbackInterface),
                "callback")
            .addStatement("this.callback = callback")
            .build());

    String resultToPass = method.getParameters().isEmpty() ? "" : "results";

    classBuilder.addMethod(
        MethodSpec.methodBuilder("onResult")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(multiParameterType, "results")
            .addStatement("callback.$L($L)", method.getSimpleName(), resultToPass)
            .build());

    generatorUtilities.writeClassToFile(className.packageName(), classBuilder);
  }

  private void generateMultiMergerInputClass(ExecutableElement method) {
    ClassName className =
        getCrossProfileCallbackMultiMergerInputClassName(generatorContext, callbackInterface);

    TypeMirror paramType =
        method.getParameters().isEmpty() ? voidTypeMirror : method.getParameters().get(0).asType();

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(className)
            .addJavadoc(
                "Implementation of {@link $T} which passes results into an instance of {@link"
                    + " $T}.\n",
                callbackInterface.interfaceElement(),
                ASYNC_CALLBACK_PARAM_MULTIMERGER_CLASSNAME)
            .addSuperinterface(ClassName.get(callbackInterface.interfaceElement()))
            .addModifiers(Modifier.PUBLIC);

    classBuilder.addField(
        FieldSpec.builder(PROFILE_CLASSNAME, "profileId")
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build());

    classBuilder.addField(
        FieldSpec.builder(
                ParameterizedTypeName.get(
                    ASYNC_CALLBACK_PARAM_MULTIMERGER_CLASSNAME,
                    ClassName.get(generatorUtilities.boxIfNecessary(paramType))),
                "callback")
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build());

    classBuilder.addMethod(
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(PROFILE_CLASSNAME, "profileId")
            .addParameter(
                ParameterizedTypeName.get(
                    ASYNC_CALLBACK_PARAM_MULTIMERGER_CLASSNAME,
                    ClassName.get(generatorUtilities.boxIfNecessary(paramType))),
                "callback")
            .addStatement("this.profileId = profileId")
            .addStatement("this.callback = callback")
            .build());

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(method.getSimpleName().toString())
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC);

    if (!method.getParameters().isEmpty()) {
      String paramName = method.getParameters().get(0).getSimpleName().toString();
      methodBuilder.addParameter(ClassName.get(paramType), paramName);
      methodBuilder.addStatement("callback.onResult(profileId, $L)", paramName);
    } else {
      methodBuilder.addStatement("callback.onResult(profileId, null)");
    }

    classBuilder.addMethod(methodBuilder.build());

    generatorUtilities.writeClassToFile(className.packageName(), classBuilder);
  }

  private void addMultiMethod(TypeSpec.Builder interfaceBuilder, ExecutableElement method) {
    if (method.getParameters().isEmpty()) {
      interfaceBuilder.addMethod(
          MethodSpec.methodBuilder(method.getSimpleName().toString())
              .addModifiers(method.getModifiers())
              .build());

      return;
    }

    // There can be only one parameter
    VariableElement param = method.getParameters().get(0);
    ParameterizedTypeName paramType = getMultiParameterType(param.asType());

    interfaceBuilder.addMethod(
        MethodSpec.methodBuilder(method.getSimpleName().toString())
            .addModifiers(method.getModifiers())
            .addParameter(paramType, param.getSimpleName().toString())
            .build());
  }

  private ParameterizedTypeName getMultiParameterType(TypeMirror paramType) {
    TypeName boxedParamType = TypeName.get(generatorUtilities.boxIfNecessary(paramType));
    return ParameterizedTypeName.get(ClassName.get(Map.class), PROFILE_CLASSNAME, boxedParamType);
  }

  private void generateReceiverClass() {
    ClassName className =
        getCrossProfileCallbackReceiverClassName(generatorContext, callbackInterface);

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(ClassName.get(callbackInterface.interfaceElement()))
            .addJavadoc(
                "Implementation of {@link $1T} which wraps an {@link $2T},\n"
                    + "writing the callback value to a {@link $3T} and passing it to the {@link"
                    + " $2T}.\n",
                callbackInterface.interfaceElement(),
                CROSS_PROFILE_CALLBACK_CLASSNAME,
                BUNDLE_CLASSNAME);

    classBuilder.addField(
        FieldSpec.builder(CROSS_PROFILE_CALLBACK_CLASSNAME, "callback")
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build());

    classBuilder.addField(
        FieldSpec.builder(BUNDLER_CLASSNAME, "bundler")
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build());

    classBuilder.addMethod(
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(CROSS_PROFILE_CALLBACK_CLASSNAME, "callback")
            .addParameter(BUNDLER_CLASSNAME, "bundler")
            .beginControlFlow("if (callback == null || bundler == null)")
            .addStatement("throw new $T()", NullPointerException.class)
            .endControlFlow()
            .addStatement("this.callback = callback")
            .addStatement("this.bundler = bundler")
            .build());

    for (ExecutableElement method : callbackInterface.methods()) {
      addReceiverMethod(classBuilder, callbackInterface, method);
    }

    generatorUtilities.writeClassToFile(className.packageName(), classBuilder);
  }

  private static void addReceiverMethod(
      TypeSpec.Builder classBuilder,
      CrossProfileCallbackInterfaceInfo callbackInterface,
      ExecutableElement method) {
    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(method.getSimpleName().toString())
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .addAnnotation(
                AnnotationSpec.builder(SuppressWarnings.class)
                    // Allow catching of Exception
                    .addMember("value", "\"CatchSpecificExceptionsChecker\"")
                    .build())
            .addParameters(GeneratorUtilities.extractParametersFromMethod(method));

    methodBuilder.beginControlFlow("try");

    methodBuilder.addStatement(
        "$1T callSender = new $1T(callback, /* methodIdentifier= */ $2L)",
        CROSS_PROFILE_CALLBACK_BUNDLE_CALL_SENDER_CLASSNAME,
        callbackInterface.getIdentifier(method));

    methodBuilder.addStatement(
        "$1T bundle = new $1T($2T.class.getClassLoader())", BUNDLE_CLASSNAME, BUNDLER_CLASSNAME);

    for (VariableElement param : method.getParameters()) {
      methodBuilder.addStatement(
          "bundler.writeToBundle(bundle, $1S, $1L, $2L)",
          param.getSimpleName(),
          TypeUtils.generateBundlerType(param.asType()));
    }

    methodBuilder.addStatement("callSender.makeBundleCall(bundle)");

    methodBuilder
        .nextControlFlow("catch ($T e)", Exception.class)
        .beginControlFlow("try")
        .addStatement(
            "$1T unavailableProfileException = new $1T(\"Error when writing callback result\", e)",
            UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME)
        .addStatement(
            "$1T bundle = new $1T($2T.class.getClassLoader())", BUNDLE_CLASSNAME, BUNDLER_CLASSNAME)
        .addStatement(
            "$T.writeThrowableToBundle(bundle, $S, unavailableProfileException)",
            BUNDLE_UTILITIES_CLASSNAME,
            "throwable")
        .addStatement(
            "$1T callSender = new $1T(callback)",
            CROSS_PROFILE_CALLBACK_EXCEPTION_BUNDLE_CALL_SENDER_CLASSNAME)
        .addStatement("callSender.makeBundleCall(bundle)")
        .nextControlFlow("catch ($T r)", UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME)
        .addComment(
            "TODO: Decide what should happen if the connection is dropped between the call and"
                + " response")
        .endControlFlow()
        .endControlFlow();

    classBuilder.addMethod(methodBuilder.build());
  }

  private void generateSenderClass() {
    ClassName className =
        getCrossProfileCallbackSenderClassName(generatorContext, callbackInterface);

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(className)
            .addJavadoc(
                "Implementation of {@link $1T} which wraps an instance of {@link $2T},\n"
                    + "extracting results and exceptions in callbacks and passing them on to the"
                    + " {@link $2T}.\n",
                LOCAL_CALLBACK_CLASSNAME,
                callbackInterface.interfaceElement())
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(LOCAL_CALLBACK_CLASSNAME);

    classBuilder.addField(
        FieldSpec.builder(ClassName.get(callbackInterface.interfaceElement()), "callback")
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build());

    classBuilder.addField(
        FieldSpec.builder(EXCEPTION_CALLBACK_CLASSNAME, "exceptionCallback")
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build());

    classBuilder.addField(
        FieldSpec.builder(BUNDLER_CLASSNAME, "bundler")
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build());

    classBuilder.addMethod(
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ClassName.get(callbackInterface.interfaceElement()), "callback")
            .addParameter(EXCEPTION_CALLBACK_CLASSNAME, "exceptionCallback")
            .addParameter(BUNDLER_CLASSNAME, "bundler")
            .beginControlFlow("if (callback == null || bundler == null)")
            .addStatement("throw new $T()", NullPointerException.class)
            .endControlFlow()
            .addStatement("this.callback = callback")
            .addStatement("this.exceptionCallback = exceptionCallback")
            .addStatement("this.bundler = bundler")
            .build());

    addSenderCallbackMethod(classBuilder);
    addSenderExceptionMethod(classBuilder);

    generatorUtilities.writeClassToFile(className.packageName(), classBuilder);
  }

  private void addSenderCallbackMethod(TypeSpec.Builder classBuilder) {
    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder("onResult")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(int.class, "methodIdentifier")
            .addParameter(BUNDLE_CLASSNAME, "params");
    methodBuilder.beginControlFlow("switch (methodIdentifier)$>");

    for (ExecutableElement method : callbackInterface.methods()) {
      // $> means increase indentation, $< means decrease
      methodBuilder.addCode("$<case $L:\n$>", callbackInterface.getIdentifier(method));
      addDispatchCode(methodBuilder, method);
      methodBuilder.addStatement("return");
    }

    methodBuilder.endControlFlow();

    classBuilder.addMethod(methodBuilder.build());
  }

  private void addSenderExceptionMethod(TypeSpec.Builder classBuilder) {
    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder("onException")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(BUNDLE_CLASSNAME, "exception");
    methodBuilder.addStatement(
        "$1T throwable = $2T.readThrowableFromBundle(exception, $3S)",
        Throwable.class,
        BUNDLE_UTILITIES_CLASSNAME,
        "throwable");

    methodBuilder.addStatement("exceptionCallback.onException(throwable)");

    classBuilder.addMethod(methodBuilder.build());
  }

  private void addDispatchCode(MethodSpec.Builder methodBuilder, ExecutableElement method) {
    for (VariableElement parameter : method.getParameters()) {
      methodBuilder.addStatement(
          "@SuppressWarnings(\"unchecked\") $1T $2L = ($1T) bundler.readFromBundle(params, $2S,"
              + " $3L)",
          parameter.asType(),
          parameter.getSimpleName().toString(),
          TypeUtils.generateBundlerType(parameter.asType()));
    }

    String commaSeparatedParams =
        method.getParameters().stream()
            .map(p -> p.getSimpleName().toString())
            .collect(joining(","));

    methodBuilder.addStatement("callback.$L($L)", method.getSimpleName(), commaSeparatedParams);
  }

  static ClassName getCrossProfileCallbackMultiInterfaceClassName(
      GeneratorContext generatorContext, CrossProfileCallbackInterfaceInfo callbackInterface) {
    return classNameInferringPackageFromElement(
        generatorContext,
        callbackInterface.interfaceElement(),
        String.format("%s_Multi", callbackInterface.simpleName()));
  }

  static ClassName getCrossProfileCallbackMultiMergerResultClassName(
      GeneratorContext generatorContext, CrossProfileCallbackInterfaceInfo callbackInterface) {
    return classNameInferringPackageFromElement(
        generatorContext,
        callbackInterface.interfaceElement(),
        String.format("Profile_%s_MultiMergerResult", callbackInterface.simpleName()));
  }

  static ClassName getCrossProfileCallbackMultiMergerInputClassName(
      GeneratorContext generatorContext, CrossProfileCallbackInterfaceInfo callbackInterface) {
    return classNameInferringPackageFromElement(
        generatorContext,
        callbackInterface.interfaceElement(),
        String.format("Profile_%s_MultiMergerInput", callbackInterface.simpleName()));
  }

  static ClassName getCrossProfileCallbackReceiverClassName(
      GeneratorContext generatorContext, CrossProfileCallbackInterfaceInfo callbackInterface) {
    return classNameInferringPackageFromElement(
        generatorContext,
        callbackInterface.interfaceElement(),
        String.format("Profile_%s_Receiver", callbackInterface.simpleName()));
  }

  static ClassName getCrossProfileCallbackSenderClassName(
      GeneratorContext generatorContext, CrossProfileCallbackInterfaceInfo callbackInterface) {
    return classNameInferringPackageFromElement(
        generatorContext,
        callbackInterface.interfaceElement(),
        String.format("Profile_%s_Sender", callbackInterface.simpleName()));
  }
}

