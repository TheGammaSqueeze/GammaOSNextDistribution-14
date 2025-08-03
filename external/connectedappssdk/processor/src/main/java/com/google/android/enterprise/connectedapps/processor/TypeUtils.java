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

import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.BUNDLER_TYPE_CLASSNAME;
import static java.util.stream.Collectors.toList;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/** Utilities for manipulating {@link TypeMirror} instances. */
public class TypeUtils {

  public static boolean isArray(TypeMirror type) {
    return type instanceof ArrayType;
  }

  /**
   * Extract a type from an array.
   *
   * <p>Assumes that {@code type} represents an array.
   */
  public static TypeMirror extractTypeFromArray(TypeMirror type) {
    return ((ArrayType) type).getComponentType();
  }

  public static boolean isGeneric(TypeMirror type) {
    if (type instanceof DeclaredType) {
      return !((DeclaredType) type).getTypeArguments().isEmpty();
    }
    return false;
  }

  public static TypeMirror removeTypeArguments(TypeMirror type) {
    if (type instanceof DeclaredType) {
      return ((DeclaredType) type).asElement().asType();
    }
    return type;
  }

  public static List<TypeMirror> extractTypeArguments(TypeMirror type) {
    if (!(type instanceof DeclaredType)) {
      return null;
    }

    return new ArrayList<>(((DeclaredType) type).getTypeArguments());
  }

  static ClassName getRawTypeClassName(TypeMirror type) {
    String rawTypeQualifiedName = getRawTypeQualifiedName(type);

    if (!rawTypeQualifiedName.contains(".")) {
      return ClassName.get("", rawTypeQualifiedName);
    }

    String packageName = rawTypeQualifiedName.substring(0, rawTypeQualifiedName.lastIndexOf("."));
    String simpleName = rawTypeQualifiedName.substring(rawTypeQualifiedName.lastIndexOf(".") + 1);

    return ClassName.get(packageName, simpleName);
  }

  static String getRawTypeQualifiedName(TypeMirror type) {
    // This converts e.g. java.util.List<String> into java.util.List
    return type.toString().split("<", 2)[0];
  }

  static CodeBlock generateBundlerType(TypeMirror type) {
    if (isArray(type)) {
      return generateArrayBundlerType(type);
    }
    if (isGeneric(type)) {
      return generateGenericBundlerType(type);
    }
    return CodeBlock.of("$T.of($S)", BUNDLER_TYPE_CLASSNAME, getRawTypeQualifiedName(type));
  }

  static TypeMirror getArrayRootType(TypeMirror type) {
    while (TypeUtils.isArray(type)) {
      type = TypeUtils.extractTypeFromArray(type);
    }

    return type;
  }

  static boolean isPrimitiveArray(TypeMirror type) {
    return getArrayRootType(type).getKind().isPrimitive();
  }

  private static CodeBlock generateArrayBundlerType(TypeMirror type) {
    TypeMirror arrayType = extractTypeFromArray(type);

    if (isPrimitiveArray(arrayType)) {
      return CodeBlock.of("$T.of($S)", BUNDLER_TYPE_CLASSNAME, type);
    }

    return CodeBlock.of(
        "$T.of($S, $L)",
        BUNDLER_TYPE_CLASSNAME,
        "java.lang.Object[]",
        generateBundlerType(arrayType));
  }

  private static CodeBlock generateGenericBundlerType(TypeMirror type) {
    CodeBlock.Builder typeArgs = CodeBlock.builder();

    List<CodeBlock> typeArgBlocks =
        extractTypeArguments(type).stream().map(TypeUtils::generateBundlerType).collect(toList());

    typeArgs.add(typeArgBlocks.get(0));
    for (CodeBlock typeArgBlock : typeArgBlocks.subList(1, typeArgBlocks.size())) {
      typeArgs.add(", $L", typeArgBlock);
    }

    return CodeBlock.of(
        "$T.of($S, $L)", BUNDLER_TYPE_CLASSNAME, getRawTypeQualifiedName(type), typeArgs.build());
  }

  private TypeUtils() {}
}
