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

import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.BUNDLER_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.BUNDLER_TYPE_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.INVALID_PROTOCOL_BUFFER_EXCEPTION_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.PARCELABLE_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.PARCEL_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.containers.ParcelableWrapper.PARCELABLE_WRAPPER_PACKAGE;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.google.android.enterprise.connectedapps.processor.containers.ParcelableWrapper;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;

/**
 * Generate the Parcelable Wrapper for a single Proto.
 *
 * <p>This is intended to be initialised and used once, which will generate all needed code.
 *
 * <p>This must only be used once. It should be used after {@link EarlyValidator} has been used to
 * validate that the annotated code is correct.
 */
public final class ProtoParcelableWrapperGenerator {

  private static final String GENERATED_PARCELABLE_WRAPPER_PACKAGE =
      PARCELABLE_WRAPPER_PACKAGE + ".generated";

  private boolean generated = false;
  private final GeneratorContext generatorContext;
  private final GeneratorUtilities generatorUtilities;
  private final ParcelableWrapper parcelableWrapper;

  ProtoParcelableWrapperGenerator(
      GeneratorContext generatorContext, ParcelableWrapper parcelableWrapper) {
    this.generatorContext = checkNotNull(generatorContext);
    this.generatorUtilities = new GeneratorUtilities(generatorContext);
    this.parcelableWrapper = checkNotNull(parcelableWrapper);
  }

  void generate() {
    if (generated) {
      throw new IllegalStateException(
          "ProtoParcelableWrapperGenerator#generate can only be called once");
    }
    generated = true;

    generateProtoParcelableWrapper();
  }

  private void generateProtoParcelableWrapper() {
    ClassName wrapperClassName = parcelableWrapper.wrapperClassName();

    if (generatorContext.elements().getTypeElement(wrapperClassName.toString()) != null) {
      // We don't generate things which already exist
      return;
    }

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(wrapperClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(PARCELABLE_CLASSNAME)
            .addJavadoc(
                "Wrapper for reading & writing {@link $T} instances to and from {@link $T}"
                    + " instances.",
                parcelableWrapper.wrappedType(),
                PARCEL_CLASSNAME);

    classBuilder.addField(
        FieldSpec.builder(ClassName.get(parcelableWrapper.wrappedType()), "proto", Modifier.PRIVATE)
            .build());

    classBuilder.addField(
        FieldSpec.builder(int.class, "NULL_SIZE")
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
            .initializer("-1")
            .build());

    classBuilder.addMethod(
        MethodSpec.methodBuilder("of")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addJavadoc(
                "Create a wrapper for the given {@link $T}.\n", parcelableWrapper.wrappedType())
            .returns(parcelableWrapper.wrapperClassName())
            .addParameter(BUNDLER_CLASSNAME, "bundler")
            .addParameter(BUNDLER_TYPE_CLASSNAME, "type")
            .addParameter(ClassName.get(parcelableWrapper.wrappedType()), "proto")
            .addStatement(
                "return new $T(bundler, type, proto)", parcelableWrapper.wrapperClassName())
            .build());

    classBuilder.addMethod(
        MethodSpec.methodBuilder("get")
            .addModifiers(Modifier.PUBLIC)
            .returns(ClassName.get(parcelableWrapper.wrappedType()))
            .addStatement("return proto")
            .build());

    classBuilder.addMethod(
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            .addParameter(BUNDLER_CLASSNAME, "bundler")
            .addParameter(BUNDLER_TYPE_CLASSNAME, "type")
            .addParameter(ClassName.get(parcelableWrapper.wrappedType()), "proto")
            .addStatement("this.proto = proto")
            .build());

    classBuilder.addMethod(
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            .addParameter(PARCEL_CLASSNAME, "in")
            .addStatement("int size = in.readInt()")
            .beginControlFlow("if (size == NULL_SIZE)")
            .addStatement("proto = null")
            .addStatement("return")
            .endControlFlow()
            .addStatement("byte[] protoBytes = new byte[size]")
            .addStatement("in.readByteArray(protoBytes)")
            .beginControlFlow("try")
            .addStatement("proto = $T.parseFrom(protoBytes)", parcelableWrapper.wrappedType())
            .nextControlFlow("catch ($T e)", INVALID_PROTOCOL_BUFFER_EXCEPTION_CLASSNAME)
            .addComment("TODO: Deal with exception")
            .endControlFlow()
            .build());

    classBuilder.addMethod(
        MethodSpec.methodBuilder("writeToParcel")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(PARCEL_CLASSNAME, "dest")
            .addParameter(int.class, "flags")
            .beginControlFlow("if (proto == null)")
            .addStatement("dest.writeInt(NULL_SIZE)")
            .addStatement("return")
            .endControlFlow()
            .addStatement("byte[] protoBytes = proto.toByteArray()")
            .addStatement("dest.writeInt(protoBytes.length)")
            .addStatement("dest.writeByteArray(protoBytes)")
            .build());

    generatorUtilities.addDefaultParcelableMethods(
        classBuilder, parcelableWrapper.wrapperClassName());

    generatorUtilities.writeClassToFile(
        parcelableWrapper.wrapperClassName().packageName(), classBuilder);
  }

  public static ClassName getGeneratedProtoWrapperClassName(TypeMirror type) {
    String simpleName = type.toString().substring(type.toString().lastIndexOf(".") + 1);
    return ClassName.get(GENERATED_PARCELABLE_WRAPPER_PACKAGE, simpleName + "Wrapper");
  }
}
