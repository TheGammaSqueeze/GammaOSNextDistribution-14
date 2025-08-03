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
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.BUNDLER_TYPE_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.BUNDLE_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.PARCEL_CLASSNAME;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toList;

import com.google.android.enterprise.connectedapps.annotations.CrossProfileConfiguration;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileTypeInfo;
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.google.android.enterprise.connectedapps.processor.containers.Type;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import java.util.List;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;

/**
 * Generate the {@code *_Bundler} class for a single {@link CrossProfileConfiguration} annotated
 * method.
 *
 * <p>This class is responsible for reading and writing {@code Bundle} and {@code Parcel} instances.
 *
 * <p>This must only be used once. It should be used after {@link EarlyValidator} has been used to
 * validate that the annotated code is correct.
 */
final class BundlerGenerator {

  private boolean generated = false;
  private final GeneratorContext generatorContext;
  private final GeneratorUtilities generatorUtilities;
  private final CrossProfileTypeInfo crossProfileType;

  BundlerGenerator(GeneratorContext generatorContext, CrossProfileTypeInfo crossProfileType) {
    this.generatorContext = checkNotNull(generatorContext);
    this.generatorUtilities = new GeneratorUtilities(generatorContext);
    this.crossProfileType = checkNotNull(crossProfileType);
  }

  void generate() {
    if (generated) {
      throw new IllegalStateException("BundlerGenerator#generate can only be called once");
    }
    generated = true;

    generateBundlerClass();
  }

  private void generateBundlerClass() {
    ClassName className = getBundlerClassName(generatorContext, crossProfileType);

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(className)
            .addJavadoc(
                "Implementation of {@link $T} for use with {@link $T}.\n",
                BUNDLER_CLASSNAME,
                crossProfileType.className())
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(BUNDLER_CLASSNAME);

    classBuilder.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC).build());
    classBuilder.addMethod(
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            .addParameter(PARCEL_CLASSNAME, "in")
            .build());

    makeParcelable(classBuilder, className);
    addWriteToBundleMethod(classBuilder);
    addReadFromBundleMethod(classBuilder);
    addWriteToParcelMethod(classBuilder);
    addReadFromParcelMethod(classBuilder);
    addCreateArrayMethod(classBuilder);

    generatorUtilities.writeClassToFile(className.packageName(), classBuilder);
  }

  private void makeParcelable(TypeSpec.Builder classBuilder, ClassName bundlerClassName) {
    classBuilder.addMethod(
        MethodSpec.methodBuilder("writeToParcel")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .addParameter(PARCEL_CLASSNAME, "dest")
            .addParameter(int.class, "flags")
            .build());

    generatorUtilities.addDefaultParcelableMethods(classBuilder, bundlerClassName);
  }

  private void addWriteToParcelMethod(TypeSpec.Builder classBuilder) {
    CodeBlock.Builder methodCode = CodeBlock.builder();

    List<Type> types =
        crossProfileType.supportedTypes().usableTypes().stream()
            .filter(Type::canBeBundled)
            .filter(t -> !t.isPrimitive())
            .collect(toList());
    addWriteToParcelTypes(methodCode, types);

    classBuilder.addMethod(
        MethodSpec.methodBuilder("writeToParcel")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            // This is for passing rawtypes into the Parcelable*.of() methods
            // ReflectedParcelable isn't a problem because it's the same APK on both sides
            .addAnnotation(
                AnnotationSpec.builder(SuppressWarnings.class)
                    .addMember("value", "{\"unchecked\", \"ReflectedParcelable\"}")
                    .build())
            .addParameter(PARCEL_CLASSNAME, "parcel")
            .addParameter(Object.class, "value")
            .addParameter(BUNDLER_TYPE_CLASSNAME, "valueType")
            .addParameter(int.class, "flags")
            .addCode(methodCode.build())
            .build());
  }

  private void addWriteToParcelTypes(CodeBlock.Builder codeBuilder, List<Type> types) {
    codeBuilder.beginControlFlow(
        "if ($S.equals(valueType.rawTypeQualifiedName()))", "java.lang.Void");
    codeBuilder.addStatement("return");
    for (Type type : types) {
      codeBuilder.nextControlFlow(
          "else if ($S.equals(valueType.rawTypeQualifiedName()))",
          TypeUtils.getRawTypeQualifiedName(type.getTypeMirror()));
      addWriteToParcelType(codeBuilder, type);
    }
    codeBuilder.endControlFlow();

    codeBuilder.addStatement(
        "throw new $T(\"Type \" + valueType.rawTypeQualifiedName() + \" cannot be written to"
            + " Parcel\")",
        IllegalArgumentException.class);
  }

  private void addWriteToParcelType(CodeBlock.Builder codeBuilder, Type type) {
    CodeBlock convertedValue =
        CodeBlock.of("($L) value", TypeUtils.getRawTypeQualifiedName(type.getTypeMirror()));
    codeBuilder.addStatement(
        crossProfileType
            .supportedTypes()
            .generateWriteToParcelCode("parcel", type, convertedValue.toString()));
    codeBuilder.addStatement("return");
  }

  private void addReadFromParcelMethod(TypeSpec.Builder classBuilder) {
    CodeBlock.Builder methodCode = CodeBlock.builder();

    List<Type> types =
        crossProfileType.supportedTypes().usableTypes().stream()
            .filter(Type::canBeBundled)
            .collect(toList());
    addReadFromParcelTypes(methodCode, types);

    methodCode.addStatement(
        "throw new $T(\"Type \" + valueType.rawTypeQualifiedName() + \" cannot be read from"
            + " Parcel\")",
        IllegalArgumentException.class);

    classBuilder.addMethod(
        MethodSpec.methodBuilder("readFromParcel")
            .addAnnotation(
                AnnotationSpec.builder(SuppressWarnings.class)
                    .addMember("value", "\"unchecked\"")
                    .build())
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(Object.class)
            .addParameter(PARCEL_CLASSNAME, "parcel")
            .addParameter(BUNDLER_TYPE_CLASSNAME, "valueType")
            .addCode(methodCode.build())
            .build());
  }

  private void addReadFromParcelTypes(CodeBlock.Builder codeBuilder, List<Type> types) {
    codeBuilder.beginControlFlow(
        "if ($S.equals(valueType.rawTypeQualifiedName()))", "java.lang.Void");
    codeBuilder.addStatement("return null");
    for (Type type : types) {
      codeBuilder.nextControlFlow(
          "else if ($S.equals(valueType.rawTypeQualifiedName()))",
          TypeUtils.getRawTypeQualifiedName(type.getTypeMirror()));
      addReadFromParcelType(codeBuilder, type);
    }
    codeBuilder.endControlFlow();
  }

  private void addReadFromParcelType(CodeBlock.Builder codeBuilder, Type type) {
    TypeMirror objectType = type.getTypeMirror();
    if (objectType.getKind().isPrimitive()) {
      PrimitiveType primitiveType = (PrimitiveType) objectType;
      objectType = generatorContext.types().boxedClass(primitiveType).asType();
    }

    codeBuilder.addStatement(
        "return ($L) $L",
        TypeUtils.getRawTypeQualifiedName(objectType),
        crossProfileType.supportedTypes().generateReadFromParcelCode("parcel", type));
  }

  private void addCreateArrayMethod(TypeSpec.Builder classBuilder) {
    CodeBlock.Builder methodCode = CodeBlock.builder();

    List<Type> types =
        crossProfileType.supportedTypes().usableTypes().stream()
            .filter(Type::canBeBundled)
            .filter(t -> !t.isGeneric())
            .filter(
                t ->
                    !t.isPrimitive()) // We can't return a primitive array with return type Object[]
            .filter(t -> !t.isArray()) // We don't support multidimensional arrays
            .collect(toList());
    addCreateArrayTypes(methodCode, types);

    methodCode.addStatement(
        "throw new $T(\"Cannot create array of type \" + valueType.rawTypeQualifiedName())",
        IllegalArgumentException.class);

    classBuilder.addMethod(
        MethodSpec.methodBuilder("createArray")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ArrayTypeName.of(Object.class))
            .addParameter(BUNDLER_TYPE_CLASSNAME, "valueType")
            .addParameter(int.class, "size")
            .addCode(methodCode.build())
            .build());
  }

  private void addCreateArrayTypes(CodeBlock.Builder codeBuilder, List<Type> types) {
    codeBuilder.beginControlFlow(
        "if ($S.equals(valueType.rawTypeQualifiedName()))", "java.lang.Void");
    codeBuilder.addStatement("return new Void[size]");
    for (Type type : types) {
      codeBuilder.nextControlFlow(
          "else if ($S.equals(valueType.rawTypeQualifiedName()))",
          TypeUtils.getRawTypeQualifiedName(type.getTypeMirror()));
      addCreateArrayType(codeBuilder, type);
    }
    codeBuilder.endControlFlow();
  }

  private void addCreateArrayType(CodeBlock.Builder codeBuilder, Type type) {
    codeBuilder.addStatement("return new $T[size]", type.getTypeMirror());
  }

  private void addWriteToBundleMethod(TypeSpec.Builder classBuilder) {
    CodeBlock.Builder methodCode = CodeBlock.builder();

    List<Type> types =
        crossProfileType.supportedTypes().usableTypes().stream()
            .filter(Type::canBeBundled)
            .filter(t -> !t.isPrimitive())
            .collect(toList());

    addWriteToBundleTypes(methodCode, types);

    classBuilder.addMethod(
        MethodSpec.methodBuilder("writeToBundle")
            // This is for passing rawtypes into the Parcelable*.of() methods
            .addAnnotation(
                AnnotationSpec.builder(SuppressWarnings.class)
                    .addMember("value", "{\"unchecked\", \"ReflectedParcelable\"}")
                    .build())
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(BUNDLE_CLASSNAME, "bundle")
            .addParameter(String.class, "key")
            .addParameter(Object.class, "value")
            .addParameter(BUNDLER_TYPE_CLASSNAME, "valueType")
            .addCode(methodCode.build())
            .build());
  }

  private void addWriteToBundleTypes(CodeBlock.Builder codeBuilder, List<Type> types) {
    codeBuilder.beginControlFlow(
        "if ($S.equals(valueType.rawTypeQualifiedName()))", "java.lang.Void");
    codeBuilder.addStatement("return");
    for (Type type : types) {
      codeBuilder.nextControlFlow(
          "else if ($S.equals(valueType.rawTypeQualifiedName()))",
          TypeUtils.getRawTypeQualifiedName(type.getTypeMirror()));
      addWriteToBundleType(codeBuilder, type);
    }
    codeBuilder.endControlFlow();

    codeBuilder.addStatement(
        "throw new $T(\"Type \" + valueType.rawTypeQualifiedName() + \" cannot be written to"
            + " Bundle\")",
        IllegalArgumentException.class);
  }

  private void addWriteToBundleType(CodeBlock.Builder codeBuilder, Type type) {
    CodeBlock convertedValue =
        CodeBlock.of("($L) value", TypeUtils.getRawTypeQualifiedName(type.getTypeMirror()));
    codeBuilder.addStatement(
        crossProfileType
            .supportedTypes()
            .generatePutIntoBundleCode("bundle", type, "key", convertedValue.toString()));
    codeBuilder.addStatement("return");
  }

  private void addReadFromBundleMethod(TypeSpec.Builder classBuilder) {
    CodeBlock.Builder methodCode = CodeBlock.builder();

    List<Type> types =
        crossProfileType.supportedTypes().usableTypes().stream()
            .filter(Type::canBeBundled)
            .collect(toList());

    methodCode.addStatement("bundle.setClassLoader($T.class.getClassLoader())", BUNDLER_CLASSNAME);

    addReadFromBundleTypes(methodCode, types);

    methodCode.addStatement(
        "throw new $T(\"Type \" + valueType.rawTypeQualifiedName() + \" cannot be read from"
            + " Bundle\")",
        IllegalArgumentException.class);

    classBuilder.addMethod(
        MethodSpec.methodBuilder("readFromBundle")
            .addAnnotation(
                AnnotationSpec.builder(SuppressWarnings.class)
                    .addMember("value", "\"unchecked\"")
                    .build())
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(Object.class)
            .addParameter(BUNDLE_CLASSNAME, "bundle")
            .addParameter(String.class, "key")
            .addParameter(BUNDLER_TYPE_CLASSNAME, "valueType")
            .addCode(methodCode.build())
            .build());
  }

  private void addReadFromBundleTypes(CodeBlock.Builder codeBuilder, List<Type> types) {

    codeBuilder.beginControlFlow(
        "if ($S.equals(valueType.rawTypeQualifiedName()))", "java.lang.Void");
    codeBuilder.addStatement("return null");
    for (Type type : types) {
      codeBuilder.nextControlFlow(
          "else if ($S.equals(valueType.rawTypeQualifiedName()))",
          TypeUtils.getRawTypeQualifiedName(type.getTypeMirror()));
      addReadFromBundleType(codeBuilder, type);
    }
    codeBuilder.endControlFlow();
  }

  private void addReadFromBundleType(CodeBlock.Builder codeBuilder, Type type) {
    TypeMirror objectType = type.getTypeMirror();
    if (objectType.getKind().isPrimitive()) {
      PrimitiveType primitiveType = (PrimitiveType) objectType;
      objectType = generatorContext.types().boxedClass(primitiveType).asType();
    }

    codeBuilder.addStatement(
        "return ($L) $L",
        TypeUtils.getRawTypeQualifiedName(objectType),
        crossProfileType.supportedTypes().generateGetFromBundleCode("bundle", type, "key"));
  }

  static ClassName getBundlerClassName(
      GeneratorContext generatorContext, CrossProfileTypeInfo crossProfileType) {
    return transformClassName(crossProfileType.generatedClassName(), append("_Bundler"));
  }
}
