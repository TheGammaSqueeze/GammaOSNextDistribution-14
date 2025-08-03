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

import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.BINDER_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.BUNDLE_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.CROSSPROFILESERVICE_STUB_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.CROSS_PROFILE_CALLBACK_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.INTENT_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.DispatcherGenerator.getDispatcherClassName;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.android.enterprise.connectedapps.annotations.CrossProfileConfiguration;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileConfigurationInfo;
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import javax.lang.model.element.Modifier;

/**
 * Generate the {@code *_Service} class for a single {@link CrossProfileConfiguration} annotated
 * class.
 *
 * <p>This class includes the dispatch of calls to providers.
 *
 * <p>This must only be used once. It should be used after {@link EarlyValidator} has been used to
 * validate that the annotated code is correct.
 */
final class ServiceGenerator {

  private boolean generated = false;
  private final GeneratorContext generatorContext;
  private final GeneratorUtilities generatorUtilities;
  private final CrossProfileConfigurationInfo configuration;

  ServiceGenerator(GeneratorContext generatorContext, CrossProfileConfigurationInfo configuration) {
    this.generatorContext = checkNotNull(generatorContext);
    this.generatorUtilities = new GeneratorUtilities(generatorContext);
    this.configuration = checkNotNull(configuration);
  }

  void generate() {
    if (generated) {
      throw new IllegalStateException("ServiceGenerator#generate can only be called once");
    }
    generated = true;

    if (configuration.serviceClass().isPresent()) {
      // Using a pre-existing service
      return;
    }

    generateServiceClass();
  }

  private void generateServiceClass() {
    ClassName className = getConnectedAppsServiceClassName(generatorContext, configuration);

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .superclass(configuration.serviceSuperclass())
            .addJavadoc(
                "Generated Service for {@link $T}\n\n"
                    + "<p>This is bound to by {@link $T} to make cross-profile calls.\n\n"
                    + "<p>This primarily forwards calls to {@link $T}\n\n"
                    + "<p>This service must be exposed in a <service> tag in your"
                    + " AndroidManifest.xml\n",
                configuration.configurationElement(),
                configuration.connectorInfo().connectorClassName(),
                getDispatcherClassName(generatorContext, configuration));

    addBinder(classBuilder);

    classBuilder.addMethod(
        MethodSpec.methodBuilder("onBind")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(BINDER_CLASSNAME)
            .addParameter(INTENT_CLASSNAME, "intent")
            .addStatement("return binder")
            .build());

    generatorUtilities.writeClassToFile(className.packageName(), classBuilder);
  }

  private void addBinder(TypeSpec.Builder classBuilder) {
    TypeSpec.Builder binderBuilder =
        TypeSpec.anonymousClassBuilder("")
            .addSuperinterface(CROSSPROFILESERVICE_STUB_CLASSNAME)
            .addField(
                FieldSpec.builder(
                        getDispatcherClassName(generatorContext, configuration), "dispatcher")
                    .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                    .initializer(
                        "new $T()", getDispatcherClassName(generatorContext, configuration))
                    .build());

    addPrepareCallMethod(binderBuilder);
    addPrepareBundleMethod(binderBuilder);
    addCallMethod(binderBuilder);
    addFetchResponseMethod(binderBuilder);
    addFetchResponseBundleMethod(binderBuilder);

    classBuilder.addField(
        FieldSpec.builder(CROSSPROFILESERVICE_STUB_CLASSNAME, "binder", Modifier.PRIVATE)
            .initializer("$L", binderBuilder.build())
            .build());
  }

  private static void addPrepareCallMethod(TypeSpec.Builder classBuilder) {
    MethodSpec prepareCallMethod =
        MethodSpec.methodBuilder("prepareCall")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .addParameter(long.class, "callId")
            .addParameter(int.class, "blockId")
            .addParameter(int.class, "numBytes")
            .addParameter(ArrayTypeName.of(byte.class), "paramBytes")
            .addStatement(
                "dispatcher.prepareCall(getApplicationContext(), callId, blockId, numBytes,"
                    + " paramBytes)")
            .build();
    classBuilder.addMethod(prepareCallMethod);
  }

  private static void addPrepareBundleMethod(TypeSpec.Builder classBuilder) {
    MethodSpec prepareCallMethod =
        MethodSpec.methodBuilder("prepareBundle")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .addParameter(long.class, "callId")
            .addParameter(int.class, "bundleId")
            .addParameter(BUNDLE_CLASSNAME, "bundle")
            .addStatement(
                "dispatcher.prepareBundle(getApplicationContext(), callId, bundleId, bundle)")
            .build();
    classBuilder.addMethod(prepareCallMethod);
  }

  private static void addCallMethod(TypeSpec.Builder classBuilder) {
    MethodSpec callMethod =
        MethodSpec.methodBuilder("call")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .returns(ArrayTypeName.of(byte.class))
            .addParameter(long.class, "callId")
            .addParameter(int.class, "blockId")
            .addParameter(long.class, "crossProfileTypeIdentifier")
            .addParameter(int.class, "methodIdentifier")
            .addParameter(ArrayTypeName.of(byte.class), "paramBytes")
            .addParameter(CROSS_PROFILE_CALLBACK_CLASSNAME, "callback")
            .addStatement(
                "return dispatcher.call(getApplicationContext(),"
                    + "callId, blockId, crossProfileTypeIdentifier, methodIdentifier, paramBytes,"
                    + " callback)")
            .build();
    classBuilder.addMethod(callMethod);
  }

  private static void addFetchResponseMethod(TypeSpec.Builder classBuilder) {
    MethodSpec prepareCallMethod =
        MethodSpec.methodBuilder("fetchResponse")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .addParameter(long.class, "callId")
            .addParameter(int.class, "blockId")
            .returns(ArrayTypeName.of(byte.class))
            .addStatement(
                "return dispatcher.fetchResponse(getApplicationContext(), callId, blockId)")
            .build();
    classBuilder.addMethod(prepareCallMethod);
  }

  private static void addFetchResponseBundleMethod(TypeSpec.Builder classBuilder) {
    MethodSpec prepareCallMethod =
        MethodSpec.methodBuilder("fetchResponseBundle")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .addParameter(long.class, "callId")
            .addParameter(int.class, "bundleId")
            .returns(BUNDLE_CLASSNAME)
            .addStatement(
                "return dispatcher.fetchResponseBundle(getApplicationContext(), callId, bundleId)")
            .build();
    classBuilder.addMethod(prepareCallMethod);
  }

  static ClassName getConnectedAppsServiceClassName(
      GeneratorContext generatorContext, CrossProfileConfigurationInfo configuration) {
    return configuration.connectorInfo().serviceName();
  }
}
