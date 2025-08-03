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

import static com.google.android.enterprise.connectedapps.processor.ClassNameUtilities.getBuilderClassName;
import static com.google.android.enterprise.connectedapps.processor.ClassNameUtilities.prepend;
import static com.google.android.enterprise.connectedapps.processor.ClassNameUtilities.transformClassName;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.ABSTRACT_FAKE_USER_CONNECTOR_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.FAKE_USER_CONNECTOR_WRAPPER_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.USER_HANDLE_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CrossUserTypeCodeGenerator.getCrossUserTypeInterfaceClassName;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileTypeInfo;
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.util.HashMap;
import java.util.Map;
import javax.lang.model.element.Modifier;

final class FakeCrossUserTypeGenerator {

  private final GeneratorContext generatorContext;
  private final GeneratorUtilities generatorUtilities;
  private final CrossProfileTypeInfo crossUserType;

  private final ClassName fakeUserConnectorClassName;
  private final TypeName mapType;

  private boolean generated = false;

  public FakeCrossUserTypeGenerator(
      GeneratorContext generatorContext, CrossProfileTypeInfo crossUserType) {
    this.generatorContext = checkNotNull(generatorContext);
    this.generatorUtilities = new GeneratorUtilities(generatorContext);
    this.crossUserType = checkNotNull(crossUserType);
    this.fakeUserConnectorClassName =
        crossUserType.connectorInfo().isPresent()
                && crossUserType.connectorInfo().get().userConnector().isPresent()
            ? FakeUserConnectorGenerator.getFakeUserConnectorClassName(
                crossUserType.connectorInfo().get().userConnector().get())
            : ABSTRACT_FAKE_USER_CONNECTOR_CLASSNAME;
    this.mapType =
        ParameterizedTypeName.get(
            ClassName.get(Map.class), USER_HANDLE_CLASSNAME, crossUserType.className());
  }

  void generate() {
    if (generated) {
      throw new IllegalStateException(
          "FakeCrossUserTypeGenerator#generate can only be called once");
    }
    generated = true;

    generateFakeCrossUserType();
  }

  private void generateFakeCrossUserType() {
    ClassName className = getFakeCrossUserTypeClassName(generatorContext, crossUserType);
    ClassName builderClassName =
        getFakeCrossUserTypeBuilderClassName(generatorContext, crossUserType);
    ClassName crossUserTypeInterfaceClassName =
        getCrossUserTypeInterfaceClassName(generatorContext, crossUserType);

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(className)
            .addJavadoc(
                "Fake implementation of {@link $T} for use during tests.\n\n"
                    + "<p>This should be injected into your code under test and the {@link $T}\n"
                    + "used to control the fake state. Calls will be routed to the correct {@link"
                    + " $T}.\n",
                crossUserTypeInterfaceClassName,
                fakeUserConnectorClassName,
                crossUserType.className())
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(crossUserTypeInterfaceClassName);

    generateConstructor(classBuilder);
    generateInterfaceMethods(classBuilder);
    generateBuilder(classBuilder, className, builderClassName);

    generatorUtilities.writeClassToFile(className.packageName(), classBuilder);
  }

  private void generateConstructor(TypeSpec.Builder classBuilder) {
    classBuilder.addField(
        FieldSpec.builder(fakeUserConnectorClassName, "connector")
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build());
    classBuilder.addField(
        FieldSpec.builder(mapType, "targetTypeForUserHandle")
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build());
    classBuilder.addMethod(
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            .addParameter(fakeUserConnectorClassName, "connector")
            .addParameter(mapType, "targetTypeForUserHandle")
            .addStatement("this.connector = connector")
            .addStatement("this.targetTypeForUserHandle = targetTypeForUserHandle")
            .build());
  }

  private void generateInterfaceMethods(TypeSpec.Builder classBuilder) {
    generateCurrentMethod(classBuilder);
    generateSpecificUserMethod(classBuilder);
    generateTargetTypeGetter(classBuilder);
  }

  private void generateTargetTypeGetter(TypeSpec.Builder classBuilder) {
    classBuilder.addMethod(
        MethodSpec.methodBuilder("getTargetType")
            .addModifiers(Modifier.PRIVATE)
            .addParameter(USER_HANDLE_CLASSNAME, "userHandle")
            .returns(crossUserType.className())
            .beginControlFlow("if (userHandle == null)")
            .addStatement("throw new $T(\"Null user handle.\")", IllegalArgumentException.class)
            .nextControlFlow("else if (!targetTypeForUserHandle.containsKey(userHandle))")
            .addStatement(
                "throw new $T(\"No $L type specified for target user handle.\")",
                UnsupportedOperationException.class,
                crossUserType.generatedClassName().simpleName())
            .endControlFlow()
            .addStatement("return targetTypeForUserHandle.get(userHandle)")
            .build());
  }

  private void generateCurrentMethod(TypeSpec.Builder classBuilder) {
    classBuilder.addMethod(
        MethodSpec.methodBuilder("current")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(
                InterfaceGenerator.getSingleSenderInterfaceClassName(
                    generatorContext, crossUserType))
            .beginControlFlow("if (connector.runningOnUser() == null)")
            .addStatement(
                "throw new $T(\"Current user not specified - you must call setRunningOnUser on your"
                    + " connector.\")",
                UnsupportedOperationException.class)
            .endControlFlow()
            .addStatement("$T userHandle = connector.runningOnUser()", USER_HANDLE_CLASSNAME)
            .addStatement(
                "return new $T(connector.applicationContext(userHandle),"
                    + " getTargetType(userHandle))",
                CurrentProfileGenerator.getCurrentProfileClassName(generatorContext, crossUserType))
            .build());
  }

  private void generateSpecificUserMethod(TypeSpec.Builder classBuilder) {
    classBuilder.addMethod(
        MethodSpec.methodBuilder("user")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(USER_HANDLE_CLASSNAME, "userHandle")
            .returns(
                InterfaceGenerator.getSingleSenderCanThrowInterfaceClassName(
                    generatorContext, crossUserType))
            .beginControlFlow("if (connector.runningOnUser() == userHandle)")
            .addStatement(
                "return new $T(connector.applicationContext(userHandle),"
                    + " getTargetType(userHandle))",
                CurrentProfileGenerator.getCurrentProfileClassName(generatorContext, crossUserType))
            .endControlFlow()
            .addStatement(
                "return new $T(new $T(connector, userHandle), getTargetType(userHandle))",
                FakeOtherGenerator.getFakeOtherClassName(generatorContext, crossUserType),
                FAKE_USER_CONNECTOR_WRAPPER_CLASSNAME)
            .build());
  }

  private void generateBuilder(
      TypeSpec.Builder classBuilder, ClassName className, ClassName builderClassName) {
    classBuilder.addMethod(
        MethodSpec.methodBuilder("builder")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(builderClassName)
            .addStatement("return new $T()", builderClassName)
            .build());

    String targetTypeName = "target" + crossUserType.className().simpleName();
    classBuilder.addType(
        TypeSpec.classBuilder("Builder")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .addField(
                FieldSpec.builder(mapType, "targetTypeForUserHandle")
                    .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                    .initializer(
                        "new $T()",
                        ParameterizedTypeName.get(
                            ClassName.get(HashMap.class),
                            USER_HANDLE_CLASSNAME,
                            crossUserType.generatedClassName()))
                    .build())
            .addField(
                FieldSpec.builder(fakeUserConnectorClassName, "connector")
                    .addModifiers(Modifier.PRIVATE)
                    .build())
            .addMethod(
                MethodSpec.methodBuilder("user")
                    .addAnnotation(CanIgnoreReturnValue.class)
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(USER_HANDLE_CLASSNAME, "userHandle")
                    .addParameter(crossUserType.className(), targetTypeName)
                    .returns(builderClassName)
                    .addStatement("targetTypeForUserHandle.put(userHandle, $L)", targetTypeName)
                    .addStatement("return this")
                    .build())
            .addMethod(
                MethodSpec.methodBuilder("connector")
                    .addAnnotation(CanIgnoreReturnValue.class)
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(fakeUserConnectorClassName, "connector")
                    .returns(builderClassName)
                    .addStatement("this.connector = connector")
                    .addStatement("return this")
                    .build())
            .addMethod(
                MethodSpec.methodBuilder("build")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(className)
                    .beginControlFlow("if (connector == null)")
                    .addStatement(
                        "throw new $T(\"Cannot build $L with no connector specified.\")",
                        IllegalStateException.class,
                        className.simpleName())
                    .endControlFlow()
                    .addStatement("return new $T(connector, targetTypeForUserHandle)", className)
                    .build())
            .build());
  }

  static ClassName getFakeCrossUserTypeClassName(
      GeneratorContext generatorContext, CrossProfileTypeInfo crossUserType) {
    return transformClassName(
        getCrossUserTypeInterfaceClassName(generatorContext, crossUserType), prepend("Fake"));
  }

  static ClassName getFakeCrossUserTypeBuilderClassName(
      GeneratorContext generatorContext, CrossProfileTypeInfo crossUserType) {
    return getBuilderClassName(getFakeCrossUserTypeClassName(generatorContext, crossUserType));
  }
}
