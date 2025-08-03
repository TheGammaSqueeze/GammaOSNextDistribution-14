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
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.ABSTRACT_FAKE_PROFILE_CONNECTOR_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.PROFILE_AWARE_UTILS_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.PROFILE_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CurrentProfileGenerator.getCurrentProfileClassName;
import static com.google.android.enterprise.connectedapps.processor.InterfaceGenerator.getCrossProfileTypeInterfaceClassName;
import static com.google.android.enterprise.connectedapps.processor.InterfaceGenerator.getMultipleSenderInterfaceClassName;
import static com.google.android.enterprise.connectedapps.processor.InterfaceGenerator.getSingleSenderCanThrowInterfaceClassName;
import static com.google.android.enterprise.connectedapps.processor.InterfaceGenerator.getSingleSenderInterfaceClassName;
import static com.google.android.enterprise.connectedapps.processor.MultipleProfilesGenerator.getMultipleProfilesClassName;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.android.enterprise.connectedapps.annotations.CustomProfileConnector;
import com.google.android.enterprise.connectedapps.annotations.CustomProfileConnector.ProfileType;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileTypeInfo;
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import java.util.HashMap;
import java.util.Map;
import javax.lang.model.element.Modifier;

class FakeCrossProfileTypeGenerator {
  private boolean generated = false;
  private final CrossProfileTypeInfo crossProfileType;
  private final GeneratorContext generatorContext;
  private final GeneratorUtilities generatorUtilities;
  private final ClassName fakeProfileConnectorClassName;

  public FakeCrossProfileTypeGenerator(
      GeneratorContext generatorContext, CrossProfileTypeInfo crossProfileType) {
    this.generatorContext = checkNotNull(generatorContext);
    this.generatorUtilities = new GeneratorUtilities(generatorContext);
    this.crossProfileType = checkNotNull(crossProfileType);
    this.fakeProfileConnectorClassName =
        crossProfileType.connectorInfo().isPresent()
                && crossProfileType.connectorInfo().get().profileConnector().isPresent()
            ? FakeProfileConnectorGenerator.getFakeProfileConnectorClassName(
                crossProfileType.connectorInfo().get().profileConnector().get())
            : ABSTRACT_FAKE_PROFILE_CONNECTOR_CLASSNAME;
  }

  void generate() {
    if (generated) {
      throw new IllegalStateException(
          "FakeCrossProfileTypeGenerator#generate can only be called once");
    }
    generated = true;

    generateFakeCrossProfileType();
  }

  private void generateFakeCrossProfileType() {
    ClassName className = getFakeCrossProfileTypeClassName(generatorContext, crossProfileType);
    ClassName builderClassName =
        getFakeCrossProfileTypeBuilderClassName(generatorContext, crossProfileType);
    ClassName crossProfileTypeInterfaceClassName =
        getCrossProfileTypeInterfaceClassName(generatorContext, crossProfileType);

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(className)
            .addJavadoc(
                "Fake implementation of {@link $T} for use during tests.\n\n"
                    + "<p>This should be injected into your code under test and the {@link $T}\n"
                    + "used to control the fake state. Calls will be routed to the correct {@link"
                    + " $T}.\n",
                crossProfileTypeInterfaceClassName,
                fakeProfileConnectorClassName,
                crossProfileType.className())
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(crossProfileTypeInterfaceClassName);

    classBuilder.addField(
        FieldSpec.builder(fakeProfileConnectorClassName, "connector")
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build());

    addConstructor(classBuilder);

    classBuilder.addMethod(
        MethodSpec.methodBuilder("builder")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(builderClassName)
            .addStatement("return new $T()", builderClassName)
            .build());

    classBuilder.addMethod(
        MethodSpec.methodBuilder("current")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(getSingleSenderInterfaceClassName(generatorContext, crossProfileType))
            .beginControlFlow("if (connector.utils().runningOnPersonal())")
            .addStatement(
                "return ($T) personal()",
                InterfaceGenerator.getSingleSenderInterfaceClassName(
                    generatorContext, crossProfileType))
            .nextControlFlow("else")
            .addStatement(
                "return ($T) work()",
                InterfaceGenerator.getSingleSenderInterfaceClassName(
                    generatorContext, crossProfileType))
            .endControlFlow()
            .build());

    classBuilder.addMethod(
        MethodSpec.methodBuilder("other")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(getSingleSenderCanThrowInterfaceClassName(generatorContext, crossProfileType))
            .beginControlFlow("if (connector.utils().runningOnPersonal())")
            .addStatement("return work()")
            .nextControlFlow("else")
            .addStatement("return personal()")
            .endControlFlow()
            .build());

    addPersonalMethod(classBuilder);
    addWorkMethod(classBuilder);

    classBuilder.addMethod(
        MethodSpec.methodBuilder("profile")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(PROFILE_CLASSNAME, "profile")
            .returns(getSingleSenderCanThrowInterfaceClassName(generatorContext, crossProfileType))
            .beginControlFlow("if (profile.isCurrent())")
            .addStatement(
                "return ($T) current()",
                getSingleSenderCanThrowInterfaceClassName(generatorContext, crossProfileType))
            .nextControlFlow("else")
            .addComment("must be other profile")
            .addStatement("return other()")
            .endControlFlow()
            .build());

    ParameterizedTypeName senderMapType =
        ParameterizedTypeName.get(
            ClassName.get(Map.class),
            PROFILE_CLASSNAME,
            InterfaceGenerator.getSingleSenderCanThrowInterfaceClassName(
                generatorContext, crossProfileType));

    classBuilder.addMethod(
        MethodSpec.methodBuilder("profiles")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ArrayTypeName.of(PROFILE_CLASSNAME), "profiles")
            .varargs(true)
            .returns(getMultipleSenderInterfaceClassName(generatorContext, crossProfileType))
            .addStatement("$T senders = new $T<>()", senderMapType, HashMap.class)
            .beginControlFlow("for ($1T profileIdentifier : profiles)", PROFILE_CLASSNAME)
            .addStatement("senders.put(profileIdentifier, profile(profileIdentifier))")
            .endControlFlow()
            .addStatement(
                "return new $1T(senders)",
                getMultipleProfilesClassName(generatorContext, crossProfileType))
            .build());

    classBuilder.addMethod(
        MethodSpec.methodBuilder("both")
            .addAnnotation(Override.class)
            .addJavadoc("Run a method on both the personal and work profile, if accessible.")
            .addModifiers(Modifier.PUBLIC)
            .returns(getMultipleSenderInterfaceClassName(generatorContext, crossProfileType))
            .addStatement("$1T utils = connector.utils()", PROFILE_AWARE_UTILS_CLASSNAME)
            .addStatement(
                "$1T currentProfileIdentifier = utils.getCurrentProfile()", PROFILE_CLASSNAME)
            .addStatement("$1T otherProfileIdentifier = utils.getOtherProfile()", PROFILE_CLASSNAME)
            .addStatement("return profiles(currentProfileIdentifier, otherProfileIdentifier)")
            .build());

    if (!crossProfileType.connectorInfo().isPresent()
        || !crossProfileType.connectorInfo().get().profileConnector().isPresent()
        || crossProfileType.connectorInfo().get().profileConnector().get().primaryProfile()
            != ProfileType.NONE) {
      generatePrimarySecondaryMethods(classBuilder);
    }

    generateFakeCrossProfileTypeBuilder(classBuilder);

    generatorUtilities.writeClassToFile(className.packageName(), classBuilder);
  }

  private void addConstructor(TypeSpec.Builder classBuilder) {
    if (crossProfileType.isStatic()) {
      classBuilder.addMethod(
          MethodSpec.constructorBuilder()
              .addModifiers(Modifier.PRIVATE)
              .addParameter(fakeProfileConnectorClassName, "connector")
              .addStatement("this.connector = connector")
              .build());
    } else {
      classBuilder.addField(
          FieldSpec.builder(crossProfileType.className(), "personal")
              .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
              .build());

      classBuilder.addField(
          FieldSpec.builder(crossProfileType.className(), "work")
              .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
              .build());

      classBuilder.addMethod(
          MethodSpec.constructorBuilder()
              .addModifiers(Modifier.PRIVATE)
              .addParameter(crossProfileType.className(), "personal")
              .addParameter(crossProfileType.className(), "work")
              .addParameter(fakeProfileConnectorClassName, "connector")
              .addStatement("this.personal = personal")
              .addStatement("this.work = work")
              .addStatement("this.connector = connector")
              .build());
    }
  }

  private void addPersonalMethod(TypeSpec.Builder classBuilder) {
    ClassName currentProfileClassName =
        getCurrentProfileClassName(generatorContext, crossProfileType);
    CodeBlock currentPersonalFakeConstructor =
        crossProfileType.isStatic()
            ? CodeBlock.of("new $T(connector.applicationContext())", currentProfileClassName)
            : CodeBlock.of(
                "new $T(connector.applicationContext(), personal)", currentProfileClassName);
    ClassName fakeOtherClassName =
        FakeOtherGenerator.getFakeOtherClassName(generatorContext, crossProfileType);
    CodeBlock otherPersonalFakeConstructor =
        crossProfileType.isStatic()
            ? CodeBlock.of("new $T(connector)", fakeOtherClassName)
            : CodeBlock.of("new $T(connector, personal)", fakeOtherClassName);

    classBuilder.addMethod(
        MethodSpec.methodBuilder("personal")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(getSingleSenderCanThrowInterfaceClassName(generatorContext, crossProfileType))
            .beginControlFlow(
                "if (connector.runningOnProfile() == $T.ProfileType.PERSONAL)",
                CustomProfileConnector.class)
            .addStatement("return $L", currentPersonalFakeConstructor)
            .nextControlFlow("else")
            .addStatement("return $L", otherPersonalFakeConstructor)
            .endControlFlow()
            .build());
  }

  private void addWorkMethod(TypeSpec.Builder classBuilder) {
    ClassName currentProfileClassName =
        getCurrentProfileClassName(generatorContext, crossProfileType);
    CodeBlock currentWorkFakeConstructor =
        crossProfileType.isStatic()
            ? CodeBlock.of("new $T(connector.applicationContext())", currentProfileClassName)
            : CodeBlock.of("new $T(connector.applicationContext(), work)", currentProfileClassName);
    ClassName fakeOtherClassName =
        FakeOtherGenerator.getFakeOtherClassName(generatorContext, crossProfileType);
    CodeBlock otherWorkFakeConstructor =
        crossProfileType.isStatic()
            ? CodeBlock.of("new $T(connector)", fakeOtherClassName)
            : CodeBlock.of("new $T(connector, work)", fakeOtherClassName);

    classBuilder.addMethod(
        MethodSpec.methodBuilder("work")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(getSingleSenderCanThrowInterfaceClassName(generatorContext, crossProfileType))
            .beginControlFlow(
                "if (connector.runningOnProfile() == $T.ProfileType.WORK)",
                CustomProfileConnector.class)
            .addStatement("return $L", currentWorkFakeConstructor)
            .nextControlFlow("else")
            .addStatement("return $L", otherWorkFakeConstructor)
            .endControlFlow()
            .build());
  }

  private void generateFakeCrossProfileTypeBuilder(TypeSpec.Builder fakeCrossProfileType) {
    ClassName fakeCrossProfileTypeClassName =
        getFakeCrossProfileTypeClassName(generatorContext, crossProfileType);
    ClassName builderClassName =
        getFakeCrossProfileTypeBuilderClassName(generatorContext, crossProfileType);

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(builderClassName)
            .addJavadoc("Builder for {@link $T}.\n", fakeCrossProfileTypeClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC);

    if (crossProfileType.isStatic()) {
      setupStaticBuilder(fakeCrossProfileTypeClassName, classBuilder);
    } else {
      setupNonStaticBuilder(builderClassName, fakeCrossProfileTypeClassName, classBuilder);
    }

    classBuilder.addField(
        FieldSpec.builder(fakeProfileConnectorClassName, "connector")
            .addModifiers(Modifier.PRIVATE)
            .build());

    classBuilder.addMethod(
        MethodSpec.methodBuilder("connector")
            .addJavadoc(
                "Set the {@link $T} to be used to manage the state of this fake.\n",
                fakeProfileConnectorClassName)
            .addAnnotation(CanIgnoreReturnValue.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(builderClassName)
            .addParameter(fakeProfileConnectorClassName, "connector")
            .addStatement("this.connector = connector")
            .addStatement("return this")
            .build());

    fakeCrossProfileType.addType(classBuilder.build());
  }

  private static void setupStaticBuilder(
      ClassName fakeCrossProfileTypeClassName, TypeSpec.Builder classBuilder) {
    classBuilder.addMethod(
        MethodSpec.methodBuilder("build")
            .addJavadoc("Build the {@link $T}.\n", fakeCrossProfileTypeClassName)
            .addModifiers(Modifier.PUBLIC)
            .returns(fakeCrossProfileTypeClassName)
            .beginControlFlow("if (connector == null)")
            .addStatement(
                "throw new $T($S)",
                IllegalStateException.class,
                "All arguments must be set to build fake")
            .endControlFlow()
            .addStatement("return new $1T(connector)", fakeCrossProfileTypeClassName)
            .build());
  }

  private void setupNonStaticBuilder(
      ClassName builderClassName,
      ClassName fakeCrossProfileTypeClassName,
      TypeSpec.Builder classBuilder) {
    classBuilder.addField(
        FieldSpec.builder(crossProfileType.className(), "personal")
            .addModifiers(Modifier.PRIVATE)
            .build());

    classBuilder.addMethod(
        MethodSpec.methodBuilder("personal")
            .addJavadoc(
                "Set the {@link $T} to be used when a call needs to be made to the personal"
                    + " profile.\n",
                crossProfileType.className())
            .addAnnotation(CanIgnoreReturnValue.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(builderClassName)
            .addParameter(crossProfileType.className(), "personal")
            .addStatement("this.personal = personal")
            .addStatement("return this")
            .build());

    classBuilder.addField(
        FieldSpec.builder(crossProfileType.className(), "work")
            .addModifiers(Modifier.PRIVATE)
            .build());

    classBuilder.addMethod(
        MethodSpec.methodBuilder("work")
            .addJavadoc(
                "Set the {@link $T} to be used when a call needs to be made to the work profile.\n",
                crossProfileType.className())
            .addAnnotation(CanIgnoreReturnValue.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(builderClassName)
            .addParameter(crossProfileType.className(), "work")
            .addStatement("this.work = work")
            .addStatement("return this")
            .build());

    classBuilder.addMethod(
        MethodSpec.methodBuilder("build")
            .addJavadoc("Build the {@link $T}.\n", fakeCrossProfileTypeClassName)
            .addModifiers(Modifier.PUBLIC)
            .returns(fakeCrossProfileTypeClassName)
            .beginControlFlow("if (personal == null || work == null || connector == null)")
            .addStatement(
                "throw new $T($S)",
                IllegalStateException.class,
                "All arguments must be set to build fake")
            .endControlFlow()
            .addStatement(
                "return new $1T(personal, work, connector)", fakeCrossProfileTypeClassName)
            .build());
  }

  private void generatePrimarySecondaryMethods(TypeSpec.Builder classBuilder) {
    generatePrimaryMethod(classBuilder);
    generateSecondaryMethod(classBuilder);
    generateSuppliersMethod(classBuilder);
  }

  private void generatePrimaryMethod(TypeSpec.Builder classBuilder) {
    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder("primary")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(getSingleSenderCanThrowInterfaceClassName(generatorContext, crossProfileType))
            .addStatement("return profile(connector.utils().getPrimaryProfile())");

    classBuilder.addMethod(methodBuilder.build());
  }

  private void generateSecondaryMethod(TypeSpec.Builder classBuilder) {
    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder("secondary")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(getSingleSenderCanThrowInterfaceClassName(generatorContext, crossProfileType))
            .addStatement("return profile(connector.utils().getSecondaryProfile())");

    classBuilder.addMethod(methodBuilder.build());
  }

  private void generateSuppliersMethod(TypeSpec.Builder classBuilder) {
    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder("suppliers")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(getMultipleSenderInterfaceClassName(generatorContext, crossProfileType))
            .addStatement("$1T utils = connector.utils()", PROFILE_AWARE_UTILS_CLASSNAME)
            .addStatement(
                "$1T currentProfileIdentifier = utils.getCurrentProfile()", PROFILE_CLASSNAME)
            .addStatement(
                "$1T secondaryProfileIdentifier = utils.getSecondaryProfile()", PROFILE_CLASSNAME)
            .addStatement("return profiles(currentProfileIdentifier, secondaryProfileIdentifier)");

    classBuilder.addMethod(methodBuilder.build());
  }

  static ClassName getFakeCrossProfileTypeClassName(
      GeneratorContext generatorContext, CrossProfileTypeInfo crossProfileType) {
    return transformClassName(
        getCrossProfileTypeInterfaceClassName(generatorContext, crossProfileType), prepend("Fake"));
  }

  static ClassName getFakeCrossProfileTypeBuilderClassName(
      GeneratorContext generatorContext, CrossProfileTypeInfo crossProfileType) {
    return getBuilderClassName(
        getFakeCrossProfileTypeClassName(generatorContext, crossProfileType));
  }
}
