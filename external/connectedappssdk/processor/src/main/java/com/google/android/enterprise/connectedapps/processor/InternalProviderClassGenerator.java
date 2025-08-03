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
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.BUNDLE_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.CONTEXT_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.CROSS_PROFILE_CALLBACK_CLASSNAME;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileTypeInfo;
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.google.android.enterprise.connectedapps.processor.containers.ProviderClassInfo;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import javax.lang.model.element.Modifier;

/**
 * Generate the {@code Profile_*_Internal} class for a single provider class.
 *
 * <p>This must only be used once. It should be used after {@link EarlyValidator} has been used to
 * validate that the annotated code is correct.
 */
final class InternalProviderClassGenerator {

  private boolean generated = false;
  private final GeneratorContext generatorContext;
  private final GeneratorUtilities generatorUtilities;
  private final ProviderClassInfo providerClass;

  InternalProviderClassGenerator(
      GeneratorContext generatorContext, ProviderClassInfo providerClass) {
    this.generatorContext = checkNotNull(generatorContext);
    this.generatorUtilities = new GeneratorUtilities(generatorContext);
    this.providerClass = checkNotNull(providerClass);
  }

  void generate() {
    if (generated) {
      throw new IllegalStateException(
          "InternalProviderClassGenerator#generate can only be called once");
    }
    generated = true;

    generateInternalProviderClassClass();
  }

  private void generateInternalProviderClassClass() {
    ClassName className = getInternalProviderClassName(generatorContext, providerClass);

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(className).addModifiers(Modifier.PUBLIC, Modifier.FINAL);

    classBuilder.addJavadoc(
        "Internal provider class for $L\n",
        providerClass.providerClassElement().asType().toString());

    classBuilder.addField(
        FieldSpec.builder(className, "instance")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer("new $T()", className)
            .build());

    classBuilder.addField(
        FieldSpec.builder(providerClass.className(), "providerClass")
            .addModifiers(Modifier.PRIVATE)
            .build());

    classBuilder.addMethod(
        MethodSpec.methodBuilder("instance")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(className)
            .addStatement("return instance")
            .addJavadoc("Get the singleton instance of this provider.\n")
            .build());

    String providerClassConstructorParameters =
        providerClass.publicConstructorArgumentTypes().isEmpty()
            ? ""
            : "context"; // We only allow a context or no-args

    classBuilder.addMethod(
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)

            .build());

    classBuilder.addMethod(
        MethodSpec.methodBuilder("providerClass")
            .addJavadoc(
                "Get a singleton instance of the original class which caused generation of this"
                    + " class.\n")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(CONTEXT_CLASSNAME, "context")
            .returns(providerClass.className())
            .addComment(
                "This can't be constructed with the field declaration as sometimes these classes"
                    + " need arguments")
            .beginControlFlow("if (providerClass == null)")
            .beginControlFlow(
                "synchronized ($T.class)",
                getInternalProviderClassName(generatorContext, providerClass))
            .beginControlFlow("if (providerClass == null)")
            .addStatement(
                "this.providerClass = new $T($L)",
                providerClass.className(),
                providerClassConstructorParameters)
            .endControlFlow()
            .endControlFlow()
            .endControlFlow()
            .addStatement("return providerClass")
            .build());

    addCallMethod(classBuilder);

    generatorUtilities.writeClassToFile(className.packageName(), classBuilder);
  }

  private void addCallMethod(TypeSpec.Builder classBuilder) {
    CodeBlock.Builder methodCode = CodeBlock.builder();

    for (CrossProfileTypeInfo crossProfileType : providerClass.allCrossProfileTypes()) {
      addCrossProfileTypeDispatcher(methodCode, crossProfileType);
    }

    methodCode.addStatement(
        "throw new $T(\"Unknown type identifier \" + crossProfileTypeIdentifier)",
        IllegalArgumentException.class);

    classBuilder.addMethod(
        MethodSpec.methodBuilder("call")
            .addModifiers(Modifier.PUBLIC)
            .returns(BUNDLE_CLASSNAME)
            .addParameter(CONTEXT_CLASSNAME, "context")
            .addParameter(long.class, "crossProfileTypeIdentifier")
            .addParameter(int.class, "methodIdentifier")
            .addParameter(BUNDLE_CLASSNAME, "params")
            .addParameter(CROSS_PROFILE_CALLBACK_CLASSNAME, "callback")
            .addCode(methodCode.build())
            .addJavadoc(
                "Call the {@code call} method on the internal type referenced by the {@code"
                    + " crossProfileTypeIdentifier}.\n\n"
                    + "@return A {@link $1T} which contains the return value under the key "
                    + "\"return\" (if a synchronous call) or is empty\n (if asynchronous).\n",
                BUNDLE_CLASSNAME)
            .build());
  }

  private void addCrossProfileTypeDispatcher(
      CodeBlock.Builder methodCode, CrossProfileTypeInfo crossProfileType) {
    methodCode.beginControlFlow(
        "if (crossProfileTypeIdentifier == $LL)", crossProfileType.identifier());
    methodCode.addStatement(
        "return $T.instance().call(context, methodIdentifier, params, callback)",
        InternalCrossProfileClassGenerator.getInternalCrossProfileClassName(
            generatorContext, crossProfileType));
    methodCode.endControlFlow();
  }

  static ClassName getInternalProviderClassName(
      GeneratorContext generatorContext, ProviderClassInfo providerClass) {
    return classNameInferringPackageFromElement(
        generatorContext,
        providerClass.providerClassElement(),
        String.format("Profile_%s_Internal", providerClass.simpleName()));
  }
}
