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
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.ABSTRACT_PROFILE_CONNECTOR_BUILDER_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.ABSTRACT_PROFILE_CONNECTOR_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.AVAILABILITY_RESTRICTIONS_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.CONNECTION_BINDER_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.CONTEXT_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.SCHEDULED_EXECUTOR_SERVICE_CLASSNAME;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.android.enterprise.connectedapps.annotations.CustomProfileConnector.ProfileType;
import com.google.android.enterprise.connectedapps.annotations.GeneratedProfileConnector;
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.google.android.enterprise.connectedapps.processor.containers.ProfileConnectorInfo;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import javax.lang.model.element.Modifier;

/**
 * Generate the {@code Generated*} class for a single {@link GeneratedProfileConnector} annotated
 * class.
 *
 * <p>This must only be used once. It should be used after {@link EarlyValidator} has been used to
 * validate that the annotated code is correct.
 */
class ProfileConnectorCodeGenerator {
  private boolean generated = false;

  private final GeneratorContext generatorContext;
  private final GeneratorUtilities generatorUtilities;
  private final ProfileConnectorInfo connector;

  ProfileConnectorCodeGenerator(GeneratorContext generatorContext, ProfileConnectorInfo connector) {
    this.generatorContext = checkNotNull(generatorContext);
    this.generatorUtilities = new GeneratorUtilities(generatorContext);
    this.connector = checkNotNull(connector);
  }

  void generate() {
    if (generated) {
      throw new IllegalStateException(
          "ProfileConnectorCodeGenerator#generate can only be called once");
    }
    generated = true;

    generateProfileConnector();
  }

  private void generateProfileConnector() {
    ClassName className = getGeneratedProfileConnectorClassName(generatorContext, connector);
    ClassName builderClassName =
        getGeneratedProfileConnectorBuilderClassName(generatorContext, connector);

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(className)
            .addJavadoc(
                "Generated implementation of {@link $T}.\n\n"
                    + "<p>All logic is implemented by {@link $T}.\n",
                connector.connectorClassName(),
                ABSTRACT_PROFILE_CONNECTOR_CLASSNAME)
            .addModifiers(Modifier.FINAL)
            .addSuperinterface(connector.connectorClassName())
            .superclass(ABSTRACT_PROFILE_CONNECTOR_CLASSNAME);

    classBuilder.addMethod(
        MethodSpec.methodBuilder("builder")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(builderClassName)
            .addParameter(CONTEXT_CLASSNAME, "context")
            .addStatement("return new $T(context)", builderClassName)
            .build());

    classBuilder.addMethod(
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            .addParameter(builderClassName, "builder")
            .addStatement(
                "super($1T.class, builder.profileConnectorBuilder)", connector.connectorClassName())
            .build());

    generateProfileConnectorBuilder(classBuilder);

    generatorUtilities.writeClassToFile(className.packageName(), classBuilder);
  }

  private void generateProfileConnectorBuilder(TypeSpec.Builder profileConnector) {
    ClassName profileConnectorClassName =
        getGeneratedProfileConnectorClassName(generatorContext, connector);
    ClassName builderClassName =
        getGeneratedProfileConnectorBuilderClassName(generatorContext, connector);

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(builderClassName)
            .addJavadoc("Builder for {@link $T}.\n", profileConnectorClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC);

    CodeBlock initialiser =
        CodeBlock.of(
            "new $T().setServiceClassName($S).setAvailabilityRestrictions($T.$L)",
            ABSTRACT_PROFILE_CONNECTOR_BUILDER_CLASSNAME,
            connector.serviceName().toString(),
            AVAILABILITY_RESTRICTIONS_CLASSNAME,
            connector.availabilityRestrictions().name());

    if (connector.primaryProfile() != ProfileType.NONE) {
      initialiser =
          CodeBlock.of(
              "$L.setPrimaryProfileType($T.$L)",
              initialiser,
              ProfileType.class,
              connector.primaryProfile().name());
    }

    classBuilder.addMethod(
        MethodSpec.constructorBuilder()
            .addParameter(CONTEXT_CLASSNAME, "context")
            .addStatement("profileConnectorBuilder.setContext(context)")
            .build());

    classBuilder.addField(
        FieldSpec.builder(ABSTRACT_PROFILE_CONNECTOR_BUILDER_CLASSNAME, "profileConnectorBuilder")
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .initializer(initialiser)
            .build());

    classBuilder.addMethod(
        MethodSpec.methodBuilder("setScheduledExecutorService")
            .addAnnotation(CanIgnoreReturnValue.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(SCHEDULED_EXECUTOR_SERVICE_CLASSNAME, "scheduledExecutorService")
            .returns(builderClassName)
            .addStatement(
                "profileConnectorBuilder.setScheduledExecutorService(scheduledExecutorService)")
            .addStatement("return this")
            .build());

    classBuilder.addMethod(
        MethodSpec.methodBuilder("setBinder")
            .addAnnotation(CanIgnoreReturnValue.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(CONNECTION_BINDER_CLASSNAME, "binder")
            .returns(builderClassName)
            .addStatement("profileConnectorBuilder.setBinder(binder)")
            .addStatement("return this")
            .build());

    classBuilder.addMethod(
        MethodSpec.methodBuilder("build")
            .addModifiers(Modifier.PUBLIC)
            .returns(profileConnectorClassName)
            .addStatement("return new $1T(this)", profileConnectorClassName)
            .build());

    profileConnector.addType(classBuilder.build());
  }

  static ClassName getGeneratedProfileConnectorClassName(
      GeneratorContext generatorContext, ProfileConnectorInfo connector) {
    return transformClassName(connector.connectorClassName(), prepend("Generated"));
  }

  static ClassName getGeneratedProfileConnectorBuilderClassName(
      GeneratorContext generatorContext, ProfileConnectorInfo connector) {
    return getBuilderClassName(getGeneratedProfileConnectorClassName(generatorContext, connector));
  }
}
