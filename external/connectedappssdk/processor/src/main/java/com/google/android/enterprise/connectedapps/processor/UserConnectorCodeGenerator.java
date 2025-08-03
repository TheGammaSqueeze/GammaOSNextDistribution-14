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
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.ABSTRACT_USER_CONNECTOR_BUILDER_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.ABSTRACT_USER_CONNECTOR_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.AVAILABILITY_RESTRICTIONS_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.CONTEXT_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.SCHEDULED_EXECUTOR_SERVICE_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.USER_BINDER_FACTORY_CLASSNAME;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.android.enterprise.connectedapps.annotations.GeneratedUserConnector;
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.google.android.enterprise.connectedapps.processor.containers.UserConnectorInfo;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import javax.lang.model.element.Modifier;

/**
 * Generate the {@code Generated*} class for a single {@link GeneratedUserConnector} annotated
 * class.
 *
 * <p>This must only be used once. It should be used after {@link EarlyValidator} has been used to
 * validate that the annotated code is correct.
 */
class UserConnectorCodeGenerator {
  private boolean generated = false;

  private final GeneratorContext generatorContext;
  private final GeneratorUtilities generatorUtilities;
  private final UserConnectorInfo connector;

  UserConnectorCodeGenerator(GeneratorContext generatorContext, UserConnectorInfo connector) {
    this.generatorContext = checkNotNull(generatorContext);
    this.generatorUtilities = new GeneratorUtilities(generatorContext);
    this.connector = checkNotNull(connector);
  }

  void generate() {
    if (generated) {
      throw new IllegalStateException(
          "UserConnectorCodeGenerator#generate can only be called once");
    }
    generated = true;

    generateUserConnector();
  }

  private void generateUserConnector() {
    ClassName className = getGeneratedUserConnectorClassName(generatorContext, connector);
    ClassName builderClassName =
        getGeneratedUserConnectorBuilderClassName(generatorContext, connector);

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(className)
            .addJavadoc(
                "Generated implementation of {@link $T}.\n\n"
                    + "<p>All logic is implemented by {@link $T}.\n",
                connector.connectorClassName(),
                ABSTRACT_USER_CONNECTOR_CLASSNAME)
            .addModifiers(Modifier.FINAL)
            .addSuperinterface(connector.connectorClassName())
            .superclass(ABSTRACT_USER_CONNECTOR_CLASSNAME);

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
                "super($1T.class, builder.userConnectorBuilder)", connector.connectorClassName())
            .build());

    generateUserConnectorBuilder(classBuilder);

    generatorUtilities.writeClassToFile(className.packageName(), classBuilder);
  }

  private void generateUserConnectorBuilder(TypeSpec.Builder userConnector) {
    ClassName connectorClassName = getGeneratedUserConnectorClassName(generatorContext, connector);
    ClassName builderClassName =
        getGeneratedUserConnectorBuilderClassName(generatorContext, connector);

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(builderClassName)
            .addJavadoc("Builder for {@link $T}.\n", connectorClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC);

    CodeBlock initialiser =
        CodeBlock.of(
            "new $T().setServiceClassName($S).setAvailabilityRestrictions($T.$L)",
            ABSTRACT_USER_CONNECTOR_BUILDER_CLASSNAME,
            connector.serviceName().toString(),
            AVAILABILITY_RESTRICTIONS_CLASSNAME,
            connector.availabilityRestrictions().name());

    classBuilder.addMethod(
        MethodSpec.constructorBuilder()
            .addParameter(CONTEXT_CLASSNAME, "context")
            .addStatement("userConnectorBuilder.setContext(context)")
            .build());

    classBuilder.addField(
        FieldSpec.builder(ABSTRACT_USER_CONNECTOR_BUILDER_CLASSNAME, "userConnectorBuilder")
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .initializer(initialiser)
            .build());

    classBuilder.addMethod(
        MethodSpec.methodBuilder("setBinderFactory")
            .addAnnotation(CanIgnoreReturnValue.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(USER_BINDER_FACTORY_CLASSNAME, "binderFactory")
            .returns(builderClassName)
            .addStatement("userConnectorBuilder.setBinderFactory(binderFactory)")
            .addStatement("return this")
            .build());

    classBuilder.addMethod(
        MethodSpec.methodBuilder("setScheduledExecutorService")
            .addAnnotation(CanIgnoreReturnValue.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(SCHEDULED_EXECUTOR_SERVICE_CLASSNAME, "scheduledExecutorService")
            .returns(builderClassName)
            .addStatement(
                "userConnectorBuilder.setScheduledExecutorService(scheduledExecutorService)")
            .addStatement("return this")
            .build());

    classBuilder.addMethod(
        MethodSpec.methodBuilder("build")
            .addModifiers(Modifier.PUBLIC)
            .returns(connectorClassName)
            .addStatement("return new $1T(this)", connectorClassName)
            .build());

    userConnector.addType(classBuilder.build());
  }

  static ClassName getGeneratedUserConnectorClassName(
      GeneratorContext generatorContext, UserConnectorInfo connector) {
    return transformClassName(connector.connectorClassName(), prepend("Generated"));
  }

  static ClassName getGeneratedUserConnectorBuilderClassName(
      GeneratorContext generatorContext, UserConnectorInfo connector) {
    return getBuilderClassName(getGeneratedUserConnectorClassName(generatorContext, connector));
  }
}
