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

import static com.google.android.enterprise.connectedapps.processor.AlwaysThrowsGenerator.getAlwaysThrowsClassName;
import static com.google.android.enterprise.connectedapps.processor.ClassNameUtilities.prepend;
import static com.google.android.enterprise.connectedapps.processor.ClassNameUtilities.transformClassName;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.CONTEXT_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.PROCESS_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.USER_CONNECTOR_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.USER_CONNECTOR_WRAPPER_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.USER_HANDLE_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.VERSION_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.VERSION_CODES_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CurrentProfileGenerator.getCurrentProfileClassName;
import static com.google.android.enterprise.connectedapps.processor.InterfaceGenerator.getSingleSenderCanThrowInterfaceClassName;
import static com.google.android.enterprise.connectedapps.processor.InterfaceGenerator.getSingleSenderInterfaceClassName;
import static com.google.android.enterprise.connectedapps.processor.OtherProfileGenerator.getOtherProfileClassName;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileTypeInfo;
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.google.android.enterprise.connectedapps.processor.containers.ProviderClassInfo;
import com.google.android.enterprise.connectedapps.processor.containers.UserConnectorInfo;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import java.util.Optional;
import javax.lang.model.element.Modifier;

class CrossUserTypeCodeGenerator {

  private final GeneratorContext generatorContext;
  private final GeneratorUtilities generatorUtilities;
  private final CrossProfileTypeInfo crossUserType;
  private final Optional<UserConnectorInfo> userConnector;

  private boolean generated = false;

  public CrossUserTypeCodeGenerator(
      GeneratorContext generatorContext,
      ProviderClassInfo providerClass,
      CrossProfileTypeInfo crossUserType) {
    checkNotNull(generatorContext);
    checkNotNull(crossUserType);
    this.generatorContext = generatorContext;
    generatorUtilities = new GeneratorUtilities(generatorContext);
    this.crossUserType = crossUserType;
    this.userConnector =
        crossUserType.connectorInfo().map(connectorInfo -> connectorInfo.userConnector().get());
  }

  void generate() {
    if (generated) {
      throw new IllegalStateException(
          "CrossProfileTypeCodeGenerator#generate can only be called once");
    }
    generated = true;

    generateCrossUserInterface();
    generateCrossUserDefaultImplementation();
  }

  private void generateCrossUserInterface() {
    ClassName interfaceName = getCrossUserTypeInterfaceClassName(generatorContext, crossUserType);
    ClassName connectorClassName =
        userConnector.map(UserConnectorInfo::connectorClassName).orElse(USER_CONNECTOR_CLASSNAME);

    TypeSpec.Builder interfaceBuilder =
        TypeSpec.interfaceBuilder(interfaceName)
            .addJavadoc(
                "Entry point for cross-user calls to {@link $T}.\n", crossUserType.className())
            .addModifiers(Modifier.PUBLIC);

    interfaceBuilder.addMethod(
        MethodSpec.methodBuilder("create")
            .returns(interfaceName)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addParameter(connectorClassName, "connector")
            .addStatement(
                "return new $T(connector)",
                getDefaultUserClassName(generatorContext, crossUserType))
            .build());

    interfaceBuilder.addMethod(
        MethodSpec.methodBuilder("current")
            .addJavadoc("Run a method on the current user.\n")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .returns(getSingleSenderInterfaceClassName(generatorContext, crossUserType))
            .build());

    interfaceBuilder.addMethod(
        MethodSpec.methodBuilder("user")
            .addJavadoc("Run a method on a specific user.\n")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addParameter(USER_HANDLE_CLASSNAME, "userHandle")
            .returns(getSingleSenderCanThrowInterfaceClassName(generatorContext, crossUserType))
            .build());

    generatorUtilities.writeClassToFile(interfaceName.packageName(), interfaceBuilder);
  }

  private void generateCrossUserDefaultImplementation() {
    ClassName userClassName = getDefaultUserClassName(generatorContext, crossUserType);
    ClassName crossUserTypeInterfaceClassName =
        getCrossUserTypeInterfaceClassName(generatorContext, crossUserType);

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(userClassName)
            .addJavadoc(
                "Default implementation of {@link $T} to be used in production.\n",
                crossUserTypeInterfaceClassName)
            .addModifiers(Modifier.FINAL);

    classBuilder.addSuperinterface(crossUserTypeInterfaceClassName);

    classBuilder.addField(
        FieldSpec.builder(USER_CONNECTOR_CLASSNAME, "connector")
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build());

    classBuilder.addMethod(
        MethodSpec.constructorBuilder()
            .addParameter(USER_CONNECTOR_CLASSNAME, "connector")
            .addModifiers(Modifier.PUBLIC)
            .addStatement("this.connector = connector")
            .build());

    addCurrentMethod(classBuilder);
    addUserMethod(classBuilder);
    addCurrentProfileHelper(classBuilder);

    generatorUtilities.writeClassToFile(userClassName.packageName(), classBuilder);
  }

  private void addCurrentProfileHelper(TypeSpec.Builder classBuilder) {
    ClassName currentProfileConcreteType =
        getCurrentProfileClassName(generatorContext, crossUserType);
    MethodSpec.Builder currentProfileHelperMethodBuilder =
        MethodSpec.methodBuilder("instanceOfCurrentProfile")
            .addModifiers(Modifier.PRIVATE)
            .returns(currentProfileConcreteType)
            .addStatement(
                "$T context = connector.applicationContext($T.myUserHandle())",
                CONTEXT_CLASSNAME,
                PROCESS_CLASSNAME);

    if (crossUserType.isStatic()) {
      currentProfileHelperMethodBuilder.addStatement(
          "return new $1T(context)", currentProfileConcreteType);
    } else {
      currentProfileHelperMethodBuilder.addStatement(
          "return new $1T(context, $2T.instance().crossProfileType(context))",
          currentProfileConcreteType,
          InternalCrossProfileClassGenerator.getInternalCrossProfileClassName(
              generatorContext, crossUserType));
    }

    classBuilder.addMethod(currentProfileHelperMethodBuilder.build());
  }

  private void addCurrentMethod(TypeSpec.Builder classBuilder) {
    classBuilder.addMethod(
        MethodSpec.methodBuilder("current")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(getSingleSenderInterfaceClassName(generatorContext, crossUserType))
            .addStatement("return instanceOfCurrentProfile()")
            .build());
  }

  private void addUserMethod(TypeSpec.Builder classBuilder) {
    classBuilder.addMethod(
        MethodSpec.methodBuilder("user")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(USER_HANDLE_CLASSNAME, "userHandle")
            .returns(getSingleSenderCanThrowInterfaceClassName(generatorContext, crossUserType))
            .beginControlFlow("if ($T.SDK_INT < $T.O)", VERSION_CLASSNAME, VERSION_CODES_CLASSNAME)
            .addStatement(
                "return new $T($S)",
                getAlwaysThrowsClassName(generatorContext, crossUserType),
                "Cross-user calls are not supported on this version of Android")
            .nextControlFlow("else if (userHandle == $T.myUserHandle())", PROCESS_CLASSNAME)
            .addStatement("return instanceOfCurrentProfile()")
            .nextControlFlow("else")
            .addStatement(
                "return new $T(new $T(connector, userHandle))",
                getOtherProfileClassName(generatorContext, crossUserType),
                USER_CONNECTOR_WRAPPER_CLASSNAME)
            .endControlFlow()
            .build());
  }

  static ClassName getCrossUserTypeInterfaceClassName(
      GeneratorContext generatorContext, CrossProfileTypeInfo crossProfileType) {
    return transformClassName(crossProfileType.generatedClassName(), prepend("User"));
  }

  static ClassName getDefaultUserClassName(
      GeneratorContext generatorContext, CrossProfileTypeInfo crossProfileType) {
    return transformClassName(
        getCrossUserTypeInterfaceClassName(generatorContext, crossProfileType), prepend("Default"));
  }
}
