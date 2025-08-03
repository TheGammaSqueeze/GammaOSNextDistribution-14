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

import static com.google.android.enterprise.connectedapps.processor.ClassNameUtilities.append;
import static com.google.android.enterprise.connectedapps.processor.ClassNameUtilities.transformClassName;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.BUNDLER_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.BUNDLE_CALL_RECEIVER_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.BUNDLE_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.BUNDLE_UTILITIES_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.CONTEXT_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.CROSS_PROFILE_CALLBACK_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.CROSS_PROFILE_SENDER_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.EXCEPTION_THROWER_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.PARCEL_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.ServiceGenerator.getConnectedAppsServiceClassName;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.joining;

import com.google.android.enterprise.connectedapps.annotations.CrossProfileConfiguration;
import com.google.android.enterprise.connectedapps.annotations.UncaughtExceptionsPolicy;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileConfigurationInfo;
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.google.android.enterprise.connectedapps.processor.containers.ProviderClassInfo;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import java.util.List;
import javax.lang.model.element.Modifier;

/**
 * Generate the {@code *_Dispatcher} class for a single {@link CrossProfileConfiguration} annotated
 * class.
 *
 * <p>This class includes the dispatch of calls to providers.
 *
 * <p>This must only be used once. It should be used after {@link EarlyValidator} has been used to
 * validate that the annotated code is correct.
 */
final class DispatcherGenerator {

  private boolean generated = false;
  private final GeneratorContext generatorContext;
  private final GeneratorUtilities generatorUtilities;
  private final CrossProfileConfigurationInfo configuration;

  DispatcherGenerator(
      GeneratorContext generatorContext, CrossProfileConfigurationInfo configuration) {
    this.generatorContext = checkNotNull(generatorContext);
    this.generatorUtilities = new GeneratorUtilities(generatorContext);
    this.configuration = checkNotNull(configuration);
  }

  void generate() {
    if (generated) {
      throw new IllegalStateException("DispatcherGenerator#generate can only be called once");
    }
    generated = true;

    generateDispatcherClass();
  }

  private void generateDispatcherClass() {
    ClassName className = getDispatcherClassName(generatorContext, configuration);

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addJavadoc(
                "Class for dispatching calls to appropriate providers.\n\n"
                    + "<p>This uses a {@link $T} to construct calls before passing the completed"
                    + " call\n"
                    + "to a provider.\n",
                BUNDLE_CALL_RECEIVER_CLASSNAME);

    classBuilder.addField(
        FieldSpec.builder(BUNDLE_CALL_RECEIVER_CLASSNAME, "bundleCallReceiver")
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .initializer("new $T()", BUNDLE_CALL_RECEIVER_CLASSNAME)
            .build());

    addCallMethod(classBuilder);
    addPrepareCallMethod(classBuilder);
    addPrepareBundleMethod(classBuilder);
    addFetchResponseMethod(classBuilder);
    addFetchResponseBundleMethod(classBuilder);

    generatorUtilities.writeClassToFile(className.packageName(), classBuilder);
  }

  private static void addPrepareCallMethod(TypeSpec.Builder classBuilder) {
    MethodSpec prepareCallMethod =
        MethodSpec.methodBuilder("prepareCall")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(CONTEXT_CLASSNAME, "context")
            .addParameter(long.class, "callId")
            .addParameter(int.class, "blockId")
            .addParameter(int.class, "numBytes")
            .addParameter(ArrayTypeName.of(byte.class), "paramBytes")
            .addStatement("bundleCallReceiver.prepareCall(callId, blockId, numBytes, paramBytes)")
            .addJavadoc(
                "Store a block of bytes to be part of a future call to\n"
                    + "{@link #call(Context, long, int, long, int, byte[], ICrossProfileCallback)}."
                    + "\n\n"
                    + "@param callId Arbitrary identifier used to link together\n"
                    + "    {@link #prepareCall(Context, long, int, int, byte[])} and\n    "
                    + "{@link #call(Context, long, int, long, int, byte[], ICrossProfileCallback)}"
                    + " calls.\n"
                    + "@param blockId The (zero indexed) number of this block. Each block should"
                    + " be\n    {@link $1T#MAX_BYTES_PER_BLOCK} bytes so the total number of blocks"
                    + " is\n    {@code numBytes / $1T#MAX_BYTES_PER_BLOCK}.\n"
                    + "@param numBytes The total number of bytes being transferred (across all"
                    + " blocks for this call,\n    including the final {@link"
                    + " #call(Context, long, int, long, int, byte[], ICrossProfileCallback)}.\n"
                    + "@param paramBytes The bytes for this block. Should contain\n    {@link"
                    + " $1T#MAX_BYTES_PER_BLOCK} bytes.\n\n"
                    + "@see $2T#prepareCall(long, int, int, byte[])",
                CROSS_PROFILE_SENDER_CLASSNAME,
                BUNDLE_CALL_RECEIVER_CLASSNAME)
            .build();
    classBuilder.addMethod(prepareCallMethod);
  }

  private static void addPrepareBundleMethod(TypeSpec.Builder classBuilder) {
    MethodSpec prepareCallMethod =
        MethodSpec.methodBuilder("prepareBundle")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(CONTEXT_CLASSNAME, "context")
            .addParameter(long.class, "callId")
            .addParameter(int.class, "bundleId")
            .addParameter(BUNDLE_CLASSNAME, "bundle")
            .addStatement("bundleCallReceiver.prepareBundle(callId, bundleId, bundle)")
            .addJavadoc(
                "Store a bundle to be part of a future call to\n"
                    + "{@link #call(Context, long, int, long, int, byte[], ICrossProfileCallback)}."
                    + "\n\n"
                    + "@param callId Arbitrary identifier used to link together\n"
                    + "    {@link #prepareCall(Context, long, int, int, byte[])} and\n    "
                    + "{@link #call(Context, long, int, long, int, byte[], ICrossProfileCallback)}"
                    + " calls.\n"
                    + "@param bundleId The (zero indexed) number of this bundle.\n"
                    + "@see $1T#prepareBundle(long, int, bundle)",
                BUNDLE_CALL_RECEIVER_CLASSNAME)
            .build();
    classBuilder.addMethod(prepareCallMethod);
  }

  private static void addFetchResponseMethod(TypeSpec.Builder classBuilder) {
    MethodSpec prepareCallMethod =
        MethodSpec.methodBuilder("fetchResponse")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(CONTEXT_CLASSNAME, "context")
            .addParameter(long.class, "callId")
            .addParameter(int.class, "blockId")
            .returns(ArrayTypeName.of(byte.class))
            .addStatement("return bundleCallReceiver.getPreparedResponse(callId, blockId)")
            .addJavadoc(
                "Fetch a response block if a previous call to\n {@link #call(Context, long, int,"
                    + " long, int, byte[], ICrossProfileCallback)} returned a\n byte array with"
                    + " 1 as the first byte.\n\n"
                    + "@param callId should be the same callId used with\n    {@link #call(Context,"
                    + " long, int, long, int, byte[], ICrossProfileCallback)}\n"
                    + "@param blockId The (zero indexed) number of the block to fetch.\n\n"
                    + "@see $1T#getPreparedResponse(long, int)\n",
                BUNDLE_CALL_RECEIVER_CLASSNAME)
            .build();
    classBuilder.addMethod(prepareCallMethod);
  }

  private static void addFetchResponseBundleMethod(TypeSpec.Builder classBuilder) {
    MethodSpec prepareCallMethod =
        MethodSpec.methodBuilder("fetchResponseBundle")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(CONTEXT_CLASSNAME, "context")
            .addParameter(long.class, "callId")
            .addParameter(int.class, "bundleId")
            .returns(BUNDLE_CLASSNAME)
            .addStatement("return bundleCallReceiver.getPreparedResponseBundle(callId, bundleId)")
            .addJavadoc(
                "Fetch a response bundle if a previous call to\n {@link #call(Context, long, int,"
                    + " long, int, byte[], ICrossProfileCallback)} returned a\n byte array with"
                    + " 2 as the first byte.\n\n"
                    + "@param callId should be the same callId used with\n    {@link #call(Context,"
                    + " long, int, long, int, byte[], ICrossProfileCallback)}\n"
                    + "@param blockId The (zero indexed) number of the bundle to fetch.\n\n"
                    + "@see $1T#getPreparedResponseBundle(long, int)\n",
                BUNDLE_CALL_RECEIVER_CLASSNAME)
            .build();
    classBuilder.addMethod(prepareCallMethod);
  }

  private void addCallMethod(TypeSpec.Builder classBuilder) {
    CodeBlock.Builder methodCode = CodeBlock.builder();

    methodCode.beginControlFlow("try");

    methodCode.addStatement(
        "$1T bundle = bundleCallReceiver.getPreparedCall(callId, blockId, paramBytes)",
        BUNDLE_CLASSNAME);

    List<ProviderClassInfo> providers = configuration.providers().asList();

    if (!providers.isEmpty()) {
      addProviderDispatch(methodCode, providers);
    }

    methodCode.addStatement(
        "throw new $T(\"Unknown type identifier \" + crossProfileTypeIdentifier)",
        IllegalArgumentException.class);

    methodCode.nextControlFlow("catch ($T e)", RuntimeException.class);
    methodCode.addStatement(
        "$1T throwableBundle = new $1T($2T.class.getClassLoader())",
        BUNDLE_CLASSNAME,
        BUNDLER_CLASSNAME);
    methodCode.addStatement(
        "$T.writeThrowableToBundle(throwableBundle, $S, e)",
        BUNDLE_UTILITIES_CLASSNAME,
        "throwable");
    methodCode.addStatement(
        "$1T throwableBytes = bundleCallReceiver.prepareResponse(callId, throwableBundle)",
        ArrayTypeName.of(byte.class));

    final UncaughtExceptionsPolicy exceptionsPolicy =
        configuration.connectorInfo().uncaughtExceptionsPolicy();

    if (exceptionsPolicy.rethrowExceptions) {
      methodCode.addStatement("$T.delayThrow(e)", EXCEPTION_THROWER_CLASSNAME);
    }

    methodCode.addStatement("return throwableBytes");
    methodCode.nextControlFlow("catch ($T e)", Error.class);
    methodCode.addStatement(
        "$1T throwableBundle = new $1T($2T.class.getClassLoader())",
        BUNDLE_CLASSNAME,
        BUNDLER_CLASSNAME);
    methodCode.addStatement(
        "$T.writeThrowableToBundle(throwableBundle, $S, e)",
        BUNDLE_UTILITIES_CLASSNAME,
        "throwable");
    methodCode.addStatement(
        "$1T throwableBytes = bundleCallReceiver.prepareResponse(callId, throwableBundle)",
        ArrayTypeName.of(byte.class));

    if (exceptionsPolicy.rethrowExceptions) {
      methodCode.addStatement("$T.delayThrow(e)", EXCEPTION_THROWER_CLASSNAME);
    }

    methodCode.addStatement("return throwableBytes");
    methodCode.endControlFlow();

    MethodSpec callMethod =
        MethodSpec.methodBuilder("call")
            .addModifiers(Modifier.PUBLIC)
            .returns(ArrayTypeName.of(byte.class))
            .addAnnotation(
                AnnotationSpec.builder(SuppressWarnings.class)
                    // Allow catching of RuntimeException
                    .addMember("value", "\"CatchSpecificExceptionsChecker\"")
                    .build())
            .addParameter(CONTEXT_CLASSNAME, "context")
            .addParameter(long.class, "callId")
            .addParameter(int.class, "blockId")
            .addParameter(long.class, "crossProfileTypeIdentifier")
            .addParameter(int.class, "methodIdentifier")
            .addParameter(ArrayTypeName.of(byte.class), "paramBytes")
            .addParameter(CROSS_PROFILE_CALLBACK_CLASSNAME, "callback")
            .addCode(methodCode.build())
            .addJavadoc(
                "Make a call, which will execute some annotated method and return a response.\n\n"
                    + "<p>The parameters to the call should be contained in a {@link $4T} written"
                    + " to a {@link $1T} and marshalled into\n"
                    + "a byte array. If the byte array is larger than {@link"
                    + " $2T#MAX_BYTES_PER_BLOCK},\n"
                    + "then it should be separated into blocks of {@link"
                    + " $2T#MAX_BYTES_PER_BLOCK}\n"
                    + "bytes, and {@link #prepareCall(Context, long, int, int, byte[])} used to"
                    + " set all but the final\n"
                    + "block, before calling this method with the final block.\n\n"
                    + "<p>The response will be an array of bytes. If the response is complete (it"
                    + " fits into a single\n"
                    + "block), then the first byte will be 0, otherwise the first byte will be 1"
                    + " and the next 4 bytes\n"
                    + "will be an int representing the total size of the return value. The rest of"
                    + " the bytes are the\n"
                    + "first block of the return value. {@link #fetchResponse(Context, long, int)"
                    + " should be used to\n"
                    + "fetch further blocks.\n\n"
                    + "@param callId Arbitrary identifier used to link together\n"
                    + "    {@link #prepareCall(Context, long, int, int, byte[])} and\n"
                    + "    {@link #call(Context, long, int, long, int, byte[],"
                    + " ICrossProfileCallback)} calls.\n"
                    + "@param blockId The (zero indexed) number of this block. Each block should"
                    + " be\n"
                    + "    {@link CrossProfileSender#MAX_BYTES_PER_BLOCK} bytes so the total"
                    + " number of blocks is\n"
                    + "    {@code numBytes / CrossProfileSender#MAX_BYTES_PER_BLOCK}.\n"
                    + "@param crossProfileTypeIdentifier The generated identifier for the type"
                    + " which contains the\n"
                    + "    method being called.\n"
                    + "@param methodIdentifier The index of the method being called on the cross"
                    + " profile type.\n"
                    + "@param paramBytes The bytes for the final block, this will be merged with"
                    + " any blocks\n"
                    + "    previously set by a call to {@link #prepareCall(Context, long, int,"
                    + " int, byte[])}.\n"
                    + "@param callback A callback to be used if this is an asynchronous call."
                    + " Otherwise this should be\n"
                    + "    {@code null}.\n\n"
                    + "@see $3T#getPreparedCall(long, int, byte[])\n",
                PARCEL_CLASSNAME,
                CROSS_PROFILE_SENDER_CLASSNAME,
                BUNDLE_CALL_RECEIVER_CLASSNAME,
                BUNDLE_CLASSNAME)
            .build();

    classBuilder.addMethod(callMethod);
  }

  private void addProviderDispatch(
      CodeBlock.Builder methodCode, List<ProviderClassInfo> providers) {
    for (ProviderClassInfo provider : providers) {
      addProviderDispatchInner(methodCode, provider);
    }
  }

  private void addProviderDispatchInner(CodeBlock.Builder methodCode, ProviderClassInfo provider) {
    String condition =
        provider.allCrossProfileTypes().stream()
            .map(
                h ->
                    "crossProfileTypeIdentifier == "
                        + h.identifier()
                        + "L // "
                        + h.crossProfileTypeElement().getQualifiedName()
                        + "\n")
            .collect(joining(" || "));

    methodCode.beginControlFlow("if ($L)", condition);
    methodCode.addStatement(
        "$1T returnBundle = $2T.instance().call(context.getApplicationContext(),"
            + " crossProfileTypeIdentifier, methodIdentifier, bundle, callback)",
        BUNDLE_CLASSNAME,
        InternalProviderClassGenerator.getInternalProviderClassName(generatorContext, provider));
    methodCode.addStatement(
        "$1T returnBytes = bundleCallReceiver.prepareResponse(callId, returnBundle)",
        ArrayTypeName.of(byte.class));
    methodCode.addStatement("return returnBytes");
    methodCode.endControlFlow();
  }

  static ClassName getDispatcherClassName(
      GeneratorContext generatorContext, CrossProfileConfigurationInfo configuration) {
    return transformClassName(
        getConnectedAppsServiceClassName(generatorContext, configuration), append("_Dispatcher"));
  }
}
