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

import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.PROFILE_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.PROFILE_CONNECTOR_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.InterfaceGenerator.getCrossProfileTypeInterfaceClassName;
import static com.google.android.enterprise.connectedapps.processor.InterfaceGenerator.getMultipleSenderInterfaceClassName;
import static com.google.android.enterprise.connectedapps.processor.InterfaceGenerator.getSingleSenderCanThrowInterfaceClassName;
import static com.google.android.enterprise.connectedapps.processor.InterfaceGenerator.getSingleSenderInterfaceClassName;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.android.enterprise.connectedapps.annotations.CrossProfile;
import com.google.android.enterprise.connectedapps.annotations.CustomProfileConnector;
import com.google.android.enterprise.connectedapps.annotations.CustomProfileConnector.ProfileType;
import com.google.android.enterprise.connectedapps.processor.containers.ConnectorInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileTypeInfo;
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.google.android.enterprise.connectedapps.processor.containers.ProfileConnectorInfo;
import com.google.common.base.Ascii;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import java.util.Optional;
import javax.lang.model.element.Modifier;

/** Generator of cross-profile code for a single {@link CrossProfile} type. */
final class CrossProfileTypeInterfaceGenerator {

  private boolean generated = false;
  private final GeneratorContext generatorContext;
  private final GeneratorUtilities generatorUtilities;
  private final CrossProfileTypeInfo crossProfileType;
  private final Optional<ProfileConnectorInfo> profileConnector;

  CrossProfileTypeInterfaceGenerator(
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
          "CrossProfileTypeInterfaceGenerator#generate can only be called once");
    }
    generated = true;

    generateCrossProfileTypeInterface();
  }

  private void generateCrossProfileTypeInterface() {
    ClassName interfaceName =
        getCrossProfileTypeInterfaceClassName(generatorContext, crossProfileType);

    TypeSpec.Builder interfaceBuilder =
        TypeSpec.interfaceBuilder(interfaceName)
            .addJavadoc(
                "Entry point for cross-profile calls to {@link $T}.\n",
                crossProfileType.className())
            .addModifiers(Modifier.PUBLIC);

    ClassName connectorClassName =
        profileConnector.isPresent()
            ? profileConnector.get().connectorClassName()
            : PROFILE_CONNECTOR_CLASSNAME;

    interfaceBuilder.addMethod(
        MethodSpec.methodBuilder("create")
            .returns(interfaceName)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addParameter(connectorClassName, "connector")
            .addStatement(
                "return new $T(connector)",
                DefaultProfileClassGenerator.getDefaultProfileClassName(
                    generatorContext, crossProfileType))
            .build());

    interfaceBuilder.addMethod(
        MethodSpec.methodBuilder("current")
            .addJavadoc("Run a method on the current profile.\n")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .returns(getSingleSenderInterfaceClassName(generatorContext, crossProfileType))
            .build());

    interfaceBuilder.addMethod(
        MethodSpec.methodBuilder("other")
            .addJavadoc("Run a method on the other profile, if accessible.\n")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .returns(getSingleSenderCanThrowInterfaceClassName(generatorContext, crossProfileType))
            .build());

    interfaceBuilder.addMethod(
        MethodSpec.methodBuilder("personal")
            .addJavadoc("Run a method on the personal profile, if accessible.\n")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .returns(getSingleSenderCanThrowInterfaceClassName(generatorContext, crossProfileType))
            .build());

    interfaceBuilder.addMethod(
        MethodSpec.methodBuilder("work")
            .addJavadoc("Run a method on the work profile, if accessible.\n")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .returns(getSingleSenderCanThrowInterfaceClassName(generatorContext, crossProfileType))
            .build());

    interfaceBuilder.addMethod(
        MethodSpec.methodBuilder("profile")
            .addJavadoc("Run a method on the given profile, if accessible.\n")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addParameter(PROFILE_CLASSNAME, "profile")
            .returns(getSingleSenderCanThrowInterfaceClassName(generatorContext, crossProfileType))
            .build());

    interfaceBuilder.addMethod(
        MethodSpec.methodBuilder("profiles")
            .addJavadoc(
                CodeBlock.builder()
                    .add("Run a method on the given profiles, if accessible.\n\n")
                    .add(
                        "<p>This will deduplicate profiles to ensure that the method is only run"
                            + " at most once on each profile.\n")
                    .build())
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addParameter(ArrayTypeName.of(PROFILE_CLASSNAME), "profiles")
            .varargs(true)
            .returns(getMultipleSenderInterfaceClassName(generatorContext, crossProfileType))
            .build());

    interfaceBuilder.addMethod(
        MethodSpec.methodBuilder("both")
            .addJavadoc("Run a method on both the personal and work profile, if accessible.\n")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .returns(getMultipleSenderInterfaceClassName(generatorContext, crossProfileType))
            .build());

    if (!profileConnector.isPresent()
        || profileConnector.get().primaryProfile() != ProfileType.NONE) {
      generatePrimarySecondaryMethods(interfaceBuilder);
    }

    generatorUtilities.writeClassToFile(interfaceName.packageName(), interfaceBuilder);
  }

  private void generatePrimarySecondaryMethods(TypeSpec.Builder interfaceBuilder) {
    generatePrimaryMethod(interfaceBuilder);
    generateSecondaryMethod(interfaceBuilder);
    generateSuppliersMethod(interfaceBuilder);
  }

  private void generatePrimaryMethod(TypeSpec.Builder interfaceBuilder) {
    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder("primary")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .returns(getSingleSenderCanThrowInterfaceClassName(generatorContext, crossProfileType));

    if (profileConnector.isPresent()) {
      methodBuilder.addJavadoc(
          "Run a method on the primary ("
              + Ascii.toLowerCase(profileConnector.get().primaryProfile().name())
              + ") profile, if accessible.\n\n@see $T#primaryProfile()\n",
          CustomProfileConnector.class);
    } else {
      methodBuilder.addJavadoc(
          "Run a method on the primary profile, if accessible.\n\n"
              + "@throws $1T if the {@link $2T} does not have a primary profile set\n"
              + "@see $2T#primaryProfile()\n",
          IllegalStateException.class,
          CustomProfileConnector.class);
    }

    interfaceBuilder.addMethod(methodBuilder.build());
  }

  private void generateSecondaryMethod(TypeSpec.Builder interfaceBuilder) {
    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder("secondary")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .returns(getSingleSenderCanThrowInterfaceClassName(generatorContext, crossProfileType));

    if (profileConnector.isPresent()) {
      String secondaryProfileName =
          profileConnector.get().primaryProfile().equals(ProfileType.WORK)
              ? Ascii.toLowerCase(ProfileType.PERSONAL.name())
              : Ascii.toLowerCase(ProfileType.WORK.name());
      methodBuilder.addJavadoc(
          "Run a method on the secondary ("
              + secondaryProfileName
              + ") profile, if accessible.\n\n@see $T#primaryProfile()\n",
          CustomProfileConnector.class);
    } else {
      methodBuilder.addJavadoc(
          "Run a method on the secondary profile, if accessible.\n\n"
              + "@throws $1T if the {@link $2T} does not have a primary profile set\n"
              + "@see $2T#primaryProfile()\n",
          IllegalStateException.class,
          CustomProfileConnector.class);
    }

    interfaceBuilder.addMethod(methodBuilder.build());
  }

  private void generateSuppliersMethod(TypeSpec.Builder interfaceBuilder) {
    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder("suppliers")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .returns(getMultipleSenderInterfaceClassName(generatorContext, crossProfileType));

    if (profileConnector.isPresent()) {
      String primaryProfileName =
          profileConnector.get().primaryProfile().equals(ProfileType.WORK)
              ? Ascii.toLowerCase(ProfileType.WORK.name())
              : Ascii.toLowerCase(ProfileType.PERSONAL.name());
      String secondaryProfileName =
          profileConnector.get().primaryProfile().equals(ProfileType.WORK)
              ? Ascii.toLowerCase(ProfileType.PERSONAL.name())
              : Ascii.toLowerCase(ProfileType.WORK.name());
      methodBuilder
          .addJavadoc("Run a method on supplier profiles, if accessible.\n\n")
          .addJavadoc(
              "<p>When run from the primary ($1L) profile, supplier profiles are the primary ($1L)"
                  + " and secondary ($2L) profiles. When run from the secondary ($2L) profile,"
                  + " supplier profiles includes only the secondary ($2L) profile.\n\n",
              primaryProfileName,
              secondaryProfileName)
          .addJavadoc("@see $T#primaryProfile()\n", CustomProfileConnector.class);
    } else {
      methodBuilder
          .addJavadoc("Run a method on supplier profiles, if accessible.\n\n")
          .addJavadoc(
              "<p>When run from the primary profile, supplier profiles are the primary and"
                  + " secondary profiles. When run from the secondary profile, supplier profiles"
                  + " includes only the secondary profile.\n\n")
          .addJavadoc(
              "@throws $1T if the {@link $2T} does not have a primary profile set\n",
              IllegalStateException.class,
              CustomProfileConnector.class)
          .addJavadoc("@see $T#primaryProfile()\n", CustomProfileConnector.class);
    }

    interfaceBuilder.addMethod(methodBuilder.build());
  }
}
