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
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.PROFILE_AWARE_UTILS_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.PROFILE_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.PROFILE_CONNECTOR_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.VERSION_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.VERSION_CODES_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CurrentProfileGenerator.getCurrentProfileClassName;
import static com.google.android.enterprise.connectedapps.processor.InterfaceGenerator.getMultipleSenderInterfaceClassName;
import static com.google.android.enterprise.connectedapps.processor.InterfaceGenerator.getSingleSenderCanThrowInterfaceClassName;
import static com.google.android.enterprise.connectedapps.processor.InterfaceGenerator.getSingleSenderInterfaceClassName;
import static com.google.android.enterprise.connectedapps.processor.MultipleProfilesGenerator.getMultipleProfilesClassName;
import static com.google.android.enterprise.connectedapps.processor.OtherProfileGenerator.getOtherProfileClassName;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.android.enterprise.connectedapps.annotations.CustomProfileConnector.ProfileType;
import com.google.android.enterprise.connectedapps.processor.containers.ConnectorInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileTypeInfo;
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.google.android.enterprise.connectedapps.processor.containers.ProfileConnectorInfo;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.Modifier;

/**
 * Generate the {@code DefaultProfile*} class for each cross-profile type.
 *
 * <p>This is intended to be initialised and used once, which will generate all needed code.
 *
 * <p>This must only be used once. It should be used after {@link EarlyValidator} has been used to
 * validate that the annotated code is correct.
 */
final class DefaultProfileClassGenerator {

  private boolean generated = false;
  private final GeneratorContext generatorContext;
  private final GeneratorUtilities generatorUtilities;
  private final CrossProfileTypeInfo crossProfileType;
  private final Optional<ProfileConnectorInfo> profileConnector;

  DefaultProfileClassGenerator(
      GeneratorContext generatorContext, CrossProfileTypeInfo crossProfileType) {
    this.generatorContext = checkNotNull(generatorContext);
    this.generatorUtilities = new GeneratorUtilities(generatorContext);
    this.crossProfileType = checkNotNull(crossProfileType);
    this.profileConnector =
        crossProfileType.connectorInfo().map(ConnectorInfo::profileConnector).map(Optional::get);
  }

  void generate() {
    if (generated) {
      throw new IllegalStateException(
          "DefaultProfileClassGenerator#generate can only be called once");
    }
    generated = true;

    generateDefaultProfileClass();
  }

  private void generateDefaultProfileClass() {
    ClassName className = getDefaultProfileClassName(generatorContext, crossProfileType);

    ClassName crossProfileTypeInterfaceClassName =
        InterfaceGenerator.getCrossProfileTypeInterfaceClassName(
            generatorContext, crossProfileType);

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(className)
            .addJavadoc(
                "Default implementation of {@link $T} to be used in production.\n",
                crossProfileTypeInterfaceClassName)
            .addModifiers(Modifier.FINAL);

    classBuilder.addSuperinterface(crossProfileTypeInterfaceClassName);

    classBuilder.addField(
        FieldSpec.builder(PROFILE_CONNECTOR_CLASSNAME, "connector")
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build());

    classBuilder.addMethod(
        MethodSpec.constructorBuilder()
            .addParameter(PROFILE_CONNECTOR_CLASSNAME, "connector")
            .addModifiers(Modifier.PUBLIC)
            .addStatement("this.connector = connector")
            .build());

    addCurrentMethod(classBuilder);

    classBuilder.addMethod(
        MethodSpec.methodBuilder("other")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(getSingleSenderCanThrowInterfaceClassName(generatorContext, crossProfileType))
            .beginControlFlow("if ($T.SDK_INT < $T.O)", VERSION_CLASSNAME, VERSION_CODES_CLASSNAME)
            .addStatement(
                "return new $T($S)",
                getAlwaysThrowsClassName(generatorContext, crossProfileType),
                "Cross-profile calls are not supported on this version of Android")
            .endControlFlow()
            .addStatement(
                "return new $T(connector)",
                getOtherProfileClassName(generatorContext, crossProfileType))
            .build());

    classBuilder.addMethod(
        MethodSpec.methodBuilder("personal")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(getSingleSenderCanThrowInterfaceClassName(generatorContext, crossProfileType))
            .beginControlFlow("if ($T.SDK_INT < $T.O)", VERSION_CLASSNAME, VERSION_CODES_CLASSNAME)
            .addStatement(
                "return new $T($S)",
                getAlwaysThrowsClassName(generatorContext, crossProfileType),
                "Cross-profile calls are not supported on this version of Android")
            .endControlFlow()
            .addStatement("return profile(connector.utils().getPersonalProfile())")
            .build());

    classBuilder.addMethod(
        MethodSpec.methodBuilder("work")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(getSingleSenderCanThrowInterfaceClassName(generatorContext, crossProfileType))
            .beginControlFlow("if ($T.SDK_INT < $T.O)", VERSION_CLASSNAME, VERSION_CODES_CLASSNAME)
            .addStatement(
                "return new $T($S)",
                getAlwaysThrowsClassName(generatorContext, crossProfileType),
                "Cross-profile calls are not supported on this version of Android")
            .endControlFlow()
            .addStatement("return profile(connector.utils().getWorkProfile())")
            .build());

    classBuilder.addMethod(
        MethodSpec.methodBuilder("profile")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(PROFILE_CLASSNAME, "profile")
            .returns(getSingleSenderCanThrowInterfaceClassName(generatorContext, crossProfileType))
            .beginControlFlow("if ($T.SDK_INT < $T.O)", VERSION_CLASSNAME, VERSION_CODES_CLASSNAME)
            .addStatement(
                "return new $T($S)",
                getAlwaysThrowsClassName(generatorContext, crossProfileType),
                "Cross-profile calls are not supported on this version of Android")
            .endControlFlow()
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
            .addModifiers(Modifier.PUBLIC)
            .returns(getMultipleSenderInterfaceClassName(generatorContext, crossProfileType))
            .addStatement("$1T utils = connector.utils()", PROFILE_AWARE_UTILS_CLASSNAME)
            .addStatement(
                "$1T currentProfileIdentifier = utils.getCurrentProfile()", PROFILE_CLASSNAME)
            .beginControlFlow("if ($T.SDK_INT < $T.O)", VERSION_CLASSNAME, VERSION_CODES_CLASSNAME)
            .addStatement("$T senders = new $T<>()", senderMapType, HashMap.class)
            .addStatement(
                "senders.put(currentProfileIdentifier, ($T) current())",
                InterfaceGenerator.getSingleSenderCanThrowInterfaceClassName(
                    generatorContext, crossProfileType))
            .addStatement(
                "return new $1T(senders)",
                getMultipleProfilesClassName(generatorContext, crossProfileType))
            .endControlFlow()
            .addStatement("$1T otherProfileIdentifier = utils.getOtherProfile()", PROFILE_CLASSNAME)
            .addStatement("return profiles(currentProfileIdentifier, otherProfileIdentifier)")
            .build());

    if (!profileConnector.isPresent()
        || profileConnector.get().primaryProfile() != ProfileType.NONE) {
      generatePrimarySecondaryMethods(classBuilder);
    }

    generatorUtilities.writeClassToFile(className.packageName(), classBuilder);
  }

  private void addCurrentMethod(TypeSpec.Builder classBuilder) {
    MethodSpec.Builder currentMethodBuilder =
        MethodSpec.methodBuilder("current")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(getSingleSenderInterfaceClassName(generatorContext, crossProfileType));

    if (crossProfileType.isStatic()) {
      currentMethodBuilder.addStatement(
          "return new $1T(connector.applicationContext())",
          getCurrentProfileClassName(generatorContext, crossProfileType));
    } else {
      currentMethodBuilder.addStatement(
          "return new $1T(connector.applicationContext(),"
              + " $2T.instance().crossProfileType(connector.applicationContext()))",
          getCurrentProfileClassName(generatorContext, crossProfileType),
          InternalCrossProfileClassGenerator.getInternalCrossProfileClassName(
              generatorContext, crossProfileType));
    }

    classBuilder.addMethod(currentMethodBuilder.build());
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
            .beginControlFlow("if ($T.SDK_INT < $T.O)", VERSION_CLASSNAME, VERSION_CODES_CLASSNAME)
            .addStatement(
                "return new $T($S)",
                getAlwaysThrowsClassName(generatorContext, crossProfileType),
                "Cross-profile calls are not supported on this version of Android")
            .endControlFlow()
            .addStatement(
                "$T primaryProfile = connector.utils().getPrimaryProfile()", PROFILE_CLASSNAME)
            .beginControlFlow("if (primaryProfile == null)")
            .addStatement("throw new $T(\"No primary profile set\")", IllegalStateException.class)
            .endControlFlow()
            .addStatement("return profile(primaryProfile)");

    classBuilder.addMethod(methodBuilder.build());
  }

  private void generateSecondaryMethod(TypeSpec.Builder classBuilder) {
    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder("secondary")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(getSingleSenderCanThrowInterfaceClassName(generatorContext, crossProfileType))
            .beginControlFlow("if ($T.SDK_INT < $T.O)", VERSION_CLASSNAME, VERSION_CODES_CLASSNAME)
            .addStatement(
                "return new $T($S)",
                getAlwaysThrowsClassName(generatorContext, crossProfileType),
                "Cross-profile calls are not supported on this version of Android")
            .endControlFlow()
            .addStatement(
                "$T secondaryProfile = connector.utils().getSecondaryProfile()", PROFILE_CLASSNAME)
            .beginControlFlow("if (secondaryProfile == null)")
            .addStatement("throw new $T(\"No primary profile set\")", IllegalStateException.class)
            .endControlFlow()
            .addStatement("return profile(secondaryProfile)");

    classBuilder.addMethod(methodBuilder.build());
  }

  private void generateSuppliersMethod(TypeSpec.Builder classBuilder) {
    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder("suppliers")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(getMultipleSenderInterfaceClassName(generatorContext, crossProfileType))
            .beginControlFlow("if ($T.SDK_INT < $T.O)", VERSION_CLASSNAME, VERSION_CODES_CLASSNAME)
            .addStatement("return both()")
            .endControlFlow()
            .addStatement("$1T utils = connector.utils()", PROFILE_AWARE_UTILS_CLASSNAME)
            .addStatement("$1T currentProfile = utils.getCurrentProfile()", PROFILE_CLASSNAME)
            .addStatement("$1T secondaryProfile = utils.getSecondaryProfile()", PROFILE_CLASSNAME)
            .beginControlFlow("if (secondaryProfile == null)")
            .addStatement("throw new $T(\"No primary profile set\")", IllegalStateException.class)
            .endControlFlow()
            .addStatement("return profiles(currentProfile, secondaryProfile)");

    classBuilder.addMethod(methodBuilder.build());
  }

  static ClassName getDefaultProfileClassName(
      GeneratorContext generatorContext, CrossProfileTypeInfo crossProfileType) {
    return transformClassName(crossProfileType.generatedClassName(), prepend("DefaultProfile"));
  }
}
