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

import static com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationFinder.hasCrossProfileCallbackAnnotation;

import com.google.android.enterprise.connectedapps.processor.containers.Context;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileCallbackInterfaceInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileMethodInfo;
import com.google.android.enterprise.connectedapps.processor.containers.FutureWrapper;
import com.google.android.enterprise.connectedapps.processor.containers.ParcelableWrapper;
import com.google.android.enterprise.connectedapps.processor.containers.Type;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Utility methods for generating code related to valid types for use with the Connected Apps SDK.
 */
public final class SupportedTypes {

  @Override
  public String toString() {
    return "SupportedTypes{" +
        "usableTypes=" + usableTypes +
        '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SupportedTypes that = (SupportedTypes) o;
    return usableTypes.equals(that.usableTypes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(usableTypes);
  }

  /** Record of the current context for type checking. */
  @AutoValue
  public abstract static class TypeCheckContext {

    /** True if we are checking inside a generic type or an array. */
    public abstract boolean isWrapped();

    public abstract boolean isOnCrossProfileCallbackInterface();

    public abstract Builder toBuilder();

    public static TypeCheckContext create() {
      return new AutoValue_SupportedTypes_TypeCheckContext.Builder()
          .setWrapped(false)
          .setOnCrossProfileCallbackInterface(false)
          .build();
    }

    public static TypeCheckContext createForCrossProfileCallbackInterface() {
      return new AutoValue_SupportedTypes_TypeCheckContext.Builder()
          .setWrapped(false)
          .setOnCrossProfileCallbackInterface(true)
          .build();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setWrapped(boolean wrapped);

      abstract Builder setOnCrossProfileCallbackInterface(boolean onCrossProfileCallbackInterface);

      abstract TypeCheckContext build();
    }
  }

  private final ImmutableMap<String, Type> usableTypes;

  public boolean isFuture(TypeMirror type) {
    Type supportedType = get(type);
    return supportedType != null && supportedType.isFuture();
  }

  boolean isValidReturnType(TypeMirror type) {
    return isValidReturnType(type, TypeCheckContext.create());
  }

  private boolean isValidReturnType(TypeMirror type, TypeCheckContext context) {
    if (TypeUtils.isArray(type)) {
      TypeMirror wrappedType = TypeUtils.extractTypeFromArray(type);
      if (TypeUtils.isGeneric(wrappedType)) {
        return false; // We don't support generic arrays
      }
      if (TypeUtils.isArray(wrappedType)) {
        // We don't support non-primitive multidimensional arrays
        return TypeUtils.isPrimitiveArray(wrappedType);
      }
      return isValidReturnType(wrappedType, context);
    }

    return TypeUtils.isGeneric(type)
        ? isValidGenericReturnType(type, context)
        : isValidReturnType(get(type), context);
  }

  private static boolean isValidReturnType(@Nullable Type supportedType, TypeCheckContext context) {
    if (supportedType == null) {
      return false;
    }

    if (context.isWrapped() && !supportedType.isSupportedInsideWrapper()) {
      return false;
    }

    return supportedType.isAcceptableReturnType();
  }

  private boolean isValidGenericReturnType(TypeMirror type, TypeCheckContext context) {
    TypeMirror genericType = TypeUtils.removeTypeArguments(type);
    Type supportedType = get(genericType);

    if (supportedType == null) {
      return false;
    }

    if (!supportedType.isSupportedWithAnyGenericType()) {
      // We need to recursively check all type arguments
      for (TypeMirror typeArgument : TypeUtils.extractTypeArguments(type)) {
        if (!isValidReturnType(typeArgument, context.toBuilder().setWrapped(true).build())) {
          return false;
        }
      }
    }

    return isValidReturnType(supportedType, context);
  }

  /**
   * Returns true if this type is automatically resolved.
   *
   * <p>An automatically resolved type does not need to have a value provided by the developer at
   * runtime, and should instead use the value of
   * {@link #getAutomaticallyResolvedReplacement(TypeMirror)}.
   */
  public boolean isAutomaticallyResolved(TypeMirror type) {
    Type supportedType = get(type);
    return supportedType != null && supportedType.getAutomaticallyResolvedReplacement().isPresent();
  }

  public String getAutomaticallyResolvedReplacement(TypeMirror type) {
    Type supportedType = get(type);
    return supportedType.getAutomaticallyResolvedReplacement().get();
  }

  boolean isValidParameterType(TypeMirror type) {
    return isValidParameterType(type, TypeCheckContext.create());
  }

  boolean isValidParameterType(TypeMirror type, TypeCheckContext context) {
    if (TypeUtils.isArray(type)) {
      TypeMirror wrappedType = TypeUtils.extractTypeFromArray(type);
      if (TypeUtils.isGeneric(wrappedType)) {
        return false; // We don't support generic arrays
      }
      if (TypeUtils.isArray(wrappedType)) {
        // We don't support non-primitive multidimensional arrays
        return TypeUtils.isPrimitiveArray(wrappedType);
      }
      return isValidParameterType(wrappedType, context.toBuilder().setWrapped(true).build());
    }

    Type supportedType = get(TypeUtils.removeTypeArguments(type));
    if (context.isOnCrossProfileCallbackInterface()) {
      if (supportedType != null && !supportedType.isSupportedInsideCrossProfileCallback()) {
        return false;
      }
    }

    if (context.isWrapped()) {
      if (supportedType == null || !supportedType.isSupportedInsideWrapper()) {
        return false;
      }
    }

    return TypeUtils.isGeneric(type)
        ? isValidGenericParameterType(type, context)
        : isValidParameterType(get(type));
  }

  private static boolean isValidParameterType(Type supportedType) {
    return supportedType != null && supportedType.isAcceptableParameterType();
  }

  private boolean isValidGenericParameterType(TypeMirror type, TypeCheckContext context) {
    TypeMirror genericType = TypeUtils.removeTypeArguments(type);
    Type supportedType = get(genericType);

    if (supportedType == null) {
      return false;
    }

    if (!supportedType.isSupportedWithAnyGenericType()) {
      // We need to recursively check all type arguments
      for (TypeMirror typeArgument : TypeUtils.extractTypeArguments(type)) {
        if (!isValidParameterType(typeArgument, context.toBuilder().setWrapped(true).build())) {
          return false;
        }
      }
    }

    return isValidParameterType(supportedType);
  }

  ImmutableCollection<Type> usableTypes() {
    return usableTypes.values();
  }

  private Type get(TypeMirror type) {
    return usableTypes.getOrDefault(type.toString(), null);
  }

  CodeBlock generatePutIntoBundleCode(
      String bundleName, Type type, String keyCode, String valueCode) {

    if (type.getPutIntoBundleCode().isPresent()) {
      return CodeBlock.of(type.getPutIntoBundleCode().get(), bundleName, keyCode, valueCode);
    }

    throw new IllegalArgumentException(
        String.format("%s can not put into bundle", type.getQualifiedName()));
  }

  CodeBlock generateGetFromBundleCode(String bundleName, Type type, String keyCode) {
    if (type.getGetFromBundleCode().isPresent()) {
      return CodeBlock.of(type.getGetFromBundleCode().get(), bundleName, keyCode);
    }

    throw new IllegalArgumentException(
        String.format("%s can not get from bundle", type.getQualifiedName()));
  }

  CodeBlock generateWriteToParcelCode(String parcelName, Type type, String valueCode) {
    if (type.getWriteToParcelCode().isPresent()) {
      return CodeBlock.of(type.getWriteToParcelCode().get(), parcelName, valueCode);
    }

    throw new IllegalArgumentException(
        String.format("%s can not write to parcel", type.getQualifiedName()));
  }

  CodeBlock generateReadFromParcelCode(String parcelName, Type type) {
    if (type.getReadFromParcelCode().isPresent()) {
      return CodeBlock.of(type.getReadFromParcelCode().get(), parcelName);
    }

    throw new IllegalArgumentException(
        String.format("%s can not read from parcel", type.getQualifiedName()));
  }

  public Type getType(TypeMirror type) {
    String typeName = type.toString();
    if (!usableTypes.containsKey(typeName)) {
      throw new IllegalArgumentException(String.format("%s type not loaded", type));
    }

    return get(type);
  }

  private SupportedTypes(Map<String, Type> usableTypes) {
    this.usableTypes = ImmutableMap.copyOf(usableTypes);
  }

  public static SupportedTypes createFromMethods(
      Context context,
      Collection<ParcelableWrapper> parcelableWrappers,
      Collection<FutureWrapper> futureWrappers,
      Collection<ExecutableElement> methods) {
    Map<String, Type> usableTypes = new HashMap<>();

    addDefaultTypes(context, usableTypes);
    addParcelableWrapperTypes(usableTypes, parcelableWrappers);
    addFutureWrapperTypes(usableTypes, futureWrappers);
    addSupportForUsedTypes(context, usableTypes, methods);

    return new SupportedTypes(usableTypes);
  }

  private static void addSupportForUsedTypes(
      Context context, Map<String, Type> usableTypes, Collection<ExecutableElement> methods) {
    for (ExecutableElement method : methods) {
      addSupportForUsedType(context, usableTypes, method.getReturnType());

      for (VariableElement parameter : method.getParameters()) {
        addSupportForUsedType(context, usableTypes, parameter.asType());
      }
    }
  }

  private static void addSupportForUsedType(
      Context context, Map<String, Type> usableTypes, TypeMirror type) {
    if (TypeUtils.isArray(type)) {
      if (!TypeUtils.isPrimitiveArray(type)) {
        addSupportForUsedType(context, usableTypes, TypeUtils.extractTypeFromArray(type));
        type =
            context
                .types()
                .getArrayType(context.elements().getTypeElement("java.lang.Object").asType());
      }
    }

    if (TypeUtils.isGeneric(type)) {
      addSupportForGenericUsedType(context, usableTypes, type);
      return;
    }
    Optional<Type> optionalSupportedType = getSupportedType(context, usableTypes, type);
    if (!optionalSupportedType.isPresent()) {
      // The type isn't supported
      return;
    }

    Type supportedType = optionalSupportedType.get();

    // We don't support generic callbacks so any callback interfaces can be picked up here
    if (supportedType.isCrossProfileCallbackInterface()) {
      for (TypeMirror typeMirror :
          supportedType.getCrossProfileCallbackInterface().get().argumentTypes()) {
        addSupportForUsedType(context, usableTypes, typeMirror);
      }
    }

    addUsableType(usableTypes, supportedType);
  }

  private static void addSupportForGenericUsedType(
      Context context, Map<String, Type> usableTypes, TypeMirror type) {
    TypeMirror genericType = TypeUtils.removeTypeArguments(type);

    Optional<Type> optionalSupportedType = getSupportedType(context, usableTypes, genericType);
    if (!optionalSupportedType.isPresent()) {
      // The base type isn't supported
      return;
    }

    Type supportedType = optionalSupportedType.get();

    addUsableType(usableTypes, supportedType);

    if (!supportedType.isSupportedWithAnyGenericType()) {
      for (TypeMirror typeArgument : TypeUtils.extractTypeArguments(type)) {
        addSupportForUsedType(context, usableTypes, typeArgument);
      }
    }
  }

  private static Optional<Type> getSupportedType(
      Context context, Map<String, Type> usableTypes, TypeMirror type) {
    Elements elements = context.elements();
    Types types = context.types();
    if (usableTypes.containsKey(type.toString())) {
      return Optional.of(usableTypes.get(type.toString()));
    }

    TypeMirror parcelable = elements.getTypeElement("android.os.Parcelable").asType();
    if (types.isAssignable(type, parcelable)) {
      return Optional.of(createParcelableType(type));
    }

    TypeMirror serializable = elements.getTypeElement("java.io.Serializable").asType();
    if (types.isAssignable(type, serializable)) {
      return Optional.of(createSerializableType(type));
    }

    TypeElement element = elements.getTypeElement(type.toString());

    if (element != null && hasCrossProfileCallbackAnnotation(element)) {
      return Optional.of(createCrossProfileCallbackType(element));
    }

    // We don't support this type - it will error in a later stage
    return Optional.empty();
  }

  private static Type createCrossProfileCallbackType(TypeElement type) {
    return Type.builder()
        .setTypeMirror(type.asType())
        .setAcceptableReturnType(false)
        .setAcceptableParameterType(true)
        .setSupportedInsideWrapper(false)
        .setSupportedInsideCrossProfileCallback(false)
        .setCrossProfileCallbackInterface(CrossProfileCallbackInterfaceInfo.create(type))
        .build();
  }

  private static Type createParcelableType(TypeMirror typeMirror) {
    return Type.builder()
        .setTypeMirror(typeMirror)
        .setAcceptableReturnType(true)
        .setAcceptableParameterType(true)
        .setPutIntoBundleCode("$L.putParcelable($L, $L)")
        .setGetFromBundleCode("$L.getParcelable($L)")
        .setWriteToParcelCode("$L.writeParcelable($L, flags)")
        .setReadFromParcelCode("$L.readParcelable(Bundler.class.getClassLoader())")
        // Parcelables must take care of their own generic types
        .setSupportedWithAnyGenericType(true)
        .build();
  }

  private static Type createSerializableType(TypeMirror typeMirror) {
    return Type.builder()
        .setTypeMirror(typeMirror)
        .setAcceptableReturnType(true)
        .setAcceptableParameterType(true)
        .setPutIntoBundleCode("$L.putSerializable($L, $L)")
        .setGetFromBundleCode("$L.getSerializable($L)")
        .setWriteToParcelCode("$L.writeSerializable($L)")
        .setReadFromParcelCode("$L.readSerializable()")
        // Serializables must take care of their own generic types
        .setSupportedWithAnyGenericType(true)
        .build();
  }

  /** Create a {@link Builder} to create a new {@link SupportedTypes} with modified entries. */
  public Builder asBuilder() {
    return new Builder(usableTypes);
  }

  private static void addDefaultTypes(Context context, Map<String, Type> usableTypes) {
    Elements elements = context.elements();
    Types types = context.types();
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(types.getNoType(TypeKind.VOID))
            .setAcceptableReturnType(true)
            .setGetFromBundleCode("null")
            .setReadFromParcelCode("null")
            .build());
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(elements.getTypeElement("java.lang.Void").asType())
            .setAcceptableReturnType(true)
            .setGetFromBundleCode("null")
            .setReadFromParcelCode("null")
            .build());
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(elements.getTypeElement("java.lang.String").asType())
            .setAcceptableReturnType(true)
            .setAcceptableParameterType(true)
            .setPutIntoBundleCode("$L.putString($L, $L)")
            .setGetFromBundleCode("$L.getString($L)")
            .setWriteToParcelCode("$L.writeString($L)")
            .setReadFromParcelCode("$L.readString()")
            .build());
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(elements.getTypeElement("java.lang.CharSequence").asType())
            .setAcceptableReturnType(true)
            .setAcceptableParameterType(true)
            .setPutIntoBundleCode("$L.putString($L, String.valueOf($L))")
            .setGetFromBundleCode("$L.getString($L)")
            .setWriteToParcelCode("$L.writeString(String.valueOf($L))")
            .setReadFromParcelCode("$L.readString()")
            .build());
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(types.getPrimitiveType(TypeKind.BYTE))
            .setAcceptableReturnType(true)
            .setAcceptableParameterType(true)
            .setPutIntoBundleCode("$L.putByte($L, $L)")
            .setGetFromBundleCode("$L.getByte($L)")
            .setWriteToParcelCode("$L.writeByte($L)")
            .setReadFromParcelCode("$L.readByte()")
            .build());
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(types.boxedClass(types.getPrimitiveType(TypeKind.BYTE)).asType())
            .setAcceptableReturnType(true)
            .setAcceptableParameterType(true)
            .setPutIntoBundleCode("$L.putByte($L, $L)")
            .setGetFromBundleCode("$L.getByte($L)")
            .setWriteToParcelCode("$L.writeByte($L)")
            .setReadFromParcelCode("$L.readByte()")
            .build());
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(types.getPrimitiveType(TypeKind.SHORT))
            .setAcceptableReturnType(true)
            .setAcceptableParameterType(true)
            .setPutIntoBundleCode("$L.putShort($L, $L)")
            .setGetFromBundleCode("$L.getShort($L)")
            .setWriteToParcelCode("$L.writeInt($L)")
            .setReadFromParcelCode("(short)$L.readInt()")
            .build());
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(types.boxedClass(types.getPrimitiveType(TypeKind.SHORT)).asType())
            .setAcceptableReturnType(true)
            .setAcceptableParameterType(true)
            .setPutIntoBundleCode("$L.putShort($L, $L)")
            .setGetFromBundleCode("$L.getShort($L)")
            .setWriteToParcelCode("$L.writeInt($L)")
            .setReadFromParcelCode("(short)$L.readInt()")
            .build());
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(types.getPrimitiveType(TypeKind.INT))
            .setAcceptableReturnType(true)
            .setAcceptableParameterType(true)
            .setPutIntoBundleCode("$L.putInt($L, $L)")
            .setGetFromBundleCode("$L.getInt($L)")
            .setWriteToParcelCode("$L.writeInt($L)")
            .setReadFromParcelCode("$L.readInt()")
            .build());
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(types.boxedClass(types.getPrimitiveType(TypeKind.INT)).asType())
            .setAcceptableReturnType(true)
            .setAcceptableParameterType(true)
            .setPutIntoBundleCode("$L.putInt($L, $L)")
            .setGetFromBundleCode("$L.getInt($L)")
            .setWriteToParcelCode("$L.writeInt($L)")
            .setReadFromParcelCode("$L.readInt()")
            .build());
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(types.getPrimitiveType(TypeKind.LONG))
            .setAcceptableReturnType(true)
            .setAcceptableParameterType(true)
            .setPutIntoBundleCode("$L.putLong($L, $L)")
            .setGetFromBundleCode("$L.getLong($L)")
            .setWriteToParcelCode("$L.writeLong($L)")
            .setReadFromParcelCode("$L.readLong()")
            .build());
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(types.boxedClass(types.getPrimitiveType(TypeKind.LONG)).asType())
            .setAcceptableReturnType(true)
            .setAcceptableParameterType(true)
            .setPutIntoBundleCode("$L.putLong($L, $L)")
            .setGetFromBundleCode("$L.getLong($L)")
            .setWriteToParcelCode("$L.writeLong($L)")
            .setReadFromParcelCode("$L.readLong()")
            .build());
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(types.getPrimitiveType(TypeKind.FLOAT))
            .setAcceptableReturnType(true)
            .setAcceptableParameterType(true)
            .setPutIntoBundleCode("$L.putFloat($L, $L)")
            .setGetFromBundleCode("$L.getFloat($L)")
            .setWriteToParcelCode("$L.writeFloat($L)")
            .setReadFromParcelCode("$L.readFloat()")
            .build());
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(types.boxedClass(types.getPrimitiveType(TypeKind.FLOAT)).asType())
            .setAcceptableReturnType(true)
            .setAcceptableParameterType(true)
            .setPutIntoBundleCode("$L.putFloat($L, $L)")
            .setGetFromBundleCode("$L.getFloat($L)")
            .setWriteToParcelCode("$L.writeFloat($L)")
            .setReadFromParcelCode("$L.readFloat()")
            .build());
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(types.getPrimitiveType(TypeKind.DOUBLE))
            .setAcceptableReturnType(true)
            .setAcceptableParameterType(true)
            .setPutIntoBundleCode("$L.putDouble($L, $L)")
            .setGetFromBundleCode("$L.getDouble($L)")
            .setWriteToParcelCode("$L.writeDouble($L)")
            .setReadFromParcelCode("$L.readDouble()")
            .build());
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(types.boxedClass(types.getPrimitiveType(TypeKind.DOUBLE)).asType())
            .setAcceptableReturnType(true)
            .setAcceptableParameterType(true)
            .setPutIntoBundleCode("$L.putDouble($L, $L)")
            .setGetFromBundleCode("$L.getDouble($L)")
            .setWriteToParcelCode("$L.writeDouble($L)")
            .setReadFromParcelCode("$L.readDouble()")
            .build());
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(types.getPrimitiveType(TypeKind.CHAR))
            .setAcceptableReturnType(true)
            .setAcceptableParameterType(true)
            .setPutIntoBundleCode("$L.putChar($L, $L)")
            .setGetFromBundleCode("$L.getChar($L)")
            .setWriteToParcelCode("$L.writeInt($L)")
            .setReadFromParcelCode("(char)$L.readInt()")
            .build());
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(types.boxedClass(types.getPrimitiveType(TypeKind.CHAR)).asType())
            .setAcceptableReturnType(true)
            .setAcceptableParameterType(true)
            .setPutIntoBundleCode("$L.putChar($L, $L)")
            .setGetFromBundleCode("$L.getChar($L)")
            .setWriteToParcelCode("$L.writeInt($L)")
            .setReadFromParcelCode("(char)$L.readInt()")
            .build());
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(types.getPrimitiveType(TypeKind.BOOLEAN))
            .setAcceptableReturnType(true)
            .setAcceptableParameterType(true)
            .setPutIntoBundleCode("$L.putBoolean($L, $L)")
            .setGetFromBundleCode("$L.getBoolean($L)")
            .setWriteToParcelCode("$L.writeInt($L ? 1 : 0)")
            .setReadFromParcelCode("($L.readInt() == 1)")
            .build());
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(types.boxedClass(types.getPrimitiveType(TypeKind.BOOLEAN)).asType())
            .setAcceptableReturnType(true)
            .setAcceptableParameterType(true)
            .setPutIntoBundleCode("$L.putBoolean($L, $L)")
            .setGetFromBundleCode("$L.getBoolean($L)")
            .setWriteToParcelCode("$L.writeInt($L ? 1 : 0)")
            .setReadFromParcelCode("($L.readInt() == 1)")
            .build());
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(elements.getTypeElement("android.content.Context").asType())
            .setAcceptableParameterType(true)
            .setAutomaticallyResolvedReplacement("context")
            .setAcceptableReturnType(false)
            .setSupportedInsideWrapper(false)
            .setSupportedInsideCrossProfileCallback(false)
            .build());

    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(elements.getTypeElement("android.os.Parcelable").asType())
            .setAcceptableReturnType(true)
            .setAcceptableParameterType(true)
            .setPutIntoBundleCode("$L.putParcelable($L, $L)")
            .setGetFromBundleCode("$L.getParcelable($L)")
            .setWriteToParcelCode("$L.writeParcelable($L, flags)")
            .setReadFromParcelCode("$L.readParcelable(Bundler.class.getClassLoader())")
            .build());

    //region ****  Primitive Array Types  ****
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(types.getArrayType(types.getPrimitiveType(TypeKind.BOOLEAN)))
            .setAcceptableParameterType(true)
            .setAcceptableReturnType(true)
            .setPutIntoBundleCode("$L.putBooleanArray($L, $L)")
            .setGetFromBundleCode("$L.getBooleanArray($L)")
            .setWriteToParcelCode("$L.writeBooleanArray($L)")
            .setReadFromParcelCode("$L.createBooleanArray()")
            .build());
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(types.getArrayType(types.getPrimitiveType(TypeKind.BYTE)))
            .setAcceptableParameterType(true)
            .setAcceptableReturnType(true)
            .setPutIntoBundleCode("$L.putByteArray($L, $L)")
            .setGetFromBundleCode("$L.getByteArray($L)")
            .setWriteToParcelCode("$L.writeByteArray($L)")
            .setReadFromParcelCode("$L.createByteArray()")
            .build());
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(types.getArrayType(types.getPrimitiveType(TypeKind.CHAR)))
            .setAcceptableParameterType(true)
            .setAcceptableReturnType(true)
            .setPutIntoBundleCode("$L.putCharArray($L, $L)")
            .setGetFromBundleCode("$L.getCharArray($L)")
            .setWriteToParcelCode("$L.writeCharArray($L)")
            .setReadFromParcelCode("$L.createCharArray()")
            .build());
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(types.getArrayType(types.getPrimitiveType(TypeKind.DOUBLE)))
            .setAcceptableParameterType(true)
            .setAcceptableReturnType(true)
            .setPutIntoBundleCode("$L.putDoubleArray($L, $L)")
            .setGetFromBundleCode("$L.getDoubleArray($L)")
            .setWriteToParcelCode("$L.writeDoubleArray($L)")
            .setReadFromParcelCode("$L.createDoubleArray()")
            .build());
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(types.getArrayType(types.getPrimitiveType(TypeKind.FLOAT)))
            .setAcceptableParameterType(true)
            .setAcceptableReturnType(true)
            .setPutIntoBundleCode("$L.putFloatArray($L, $L)")
            .setGetFromBundleCode("$L.getFloatArray($L)")
            .setWriteToParcelCode("$L.writeFloatArray($L)")
            .setReadFromParcelCode("$L.createFloatArray()")
            .build());
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(types.getArrayType(types.getPrimitiveType(TypeKind.INT)))
            .setAcceptableParameterType(true)
            .setAcceptableReturnType(true)
            .setPutIntoBundleCode("$L.putIntArray($L, $L)")
            .setGetFromBundleCode("$L.getIntArray($L)")
            .setWriteToParcelCode("$L.writeIntArray($L)")
            .setReadFromParcelCode("$L.createIntArray()")
            .build());
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(types.getArrayType(types.getPrimitiveType(TypeKind.LONG)))
            .setAcceptableParameterType(true)
            .setAcceptableReturnType(true)
            .setPutIntoBundleCode("$L.putLongArray($L, $L)")
            .setGetFromBundleCode("$L.getLongArray($L)")
            .setWriteToParcelCode("$L.writeLongArray($L)")
            .setReadFromParcelCode("$L.createLongArray()")
            .build());
    //endregion ****  Primitive Array Types  ****

  }

  private static void addUsableType(Map<String, Type> usableTypes, Type type) {
    usableTypes.put(type.getQualifiedName(), type);
  }

  private static void addParcelableWrapperTypes(
      Map<String, Type> usableTypes, Collection<ParcelableWrapper> parcelableWrappers) {
    for (ParcelableWrapper parcelableWrapper : parcelableWrappers) {
      addParcelableWrapperType(usableTypes, parcelableWrapper);
    }
  }

  private static void addParcelableWrapperType(
      Map<String, Type> usableTypes, ParcelableWrapper parcelableWrapper) {
    String createParcelableCode = parcelableWrapper.wrapperClassName() + ".of(this, valueType, $L)";
    // "this" will be a Bundler as this code is only run within a Bundler

    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(parcelableWrapper.wrappedType())
            .setAcceptableReturnType(true)
            .setAcceptableParameterType(true)
            .setPutIntoBundleCode("$L.putParcelable($L, " + createParcelableCode + ")")
            .setGetFromBundleCode(
                "((" + parcelableWrapper.wrapperClassName() + ") $L.getParcelable($L)).get()")
            .setWriteToParcelCode("$L.writeParcelable(" + createParcelableCode + ", flags)")
            .setReadFromParcelCode(
                "(("
                    + parcelableWrapper.wrapperClassName()
                    + ") $L.readParcelable(Bundler.class.getClassLoader())).get()")
            .setParcelableWrapper(parcelableWrapper)
            .build());
  }

  private static void addFutureWrapperTypes(
      Map<String, Type> usableTypes, Collection<FutureWrapper> futureWrappers) {
    for (FutureWrapper futureWrapper : futureWrappers) {
      addFutureWrapperType(usableTypes, futureWrapper);
    }
  }

  private static void addFutureWrapperType(
      Map<String, Type> usableTypes, FutureWrapper futureWrapper) {
    addUsableType(
        usableTypes,
        Type.builder()
            .setTypeMirror(futureWrapper.wrappedType())
            .setAcceptableReturnType(true)
            .setSupportedInsideWrapper(false)
            .setFutureWrapper(futureWrapper)
            .build());
  }

  public static final class Builder {

    private Map<String, Type> usableTypes;

    private Builder(Map<String, Type> usableTypes) {
      this.usableTypes = usableTypes;
    }

    /** Filtering to only include used types. */
    public Builder filterUsed(Context context, Collection<CrossProfileMethodInfo> methods) {

      Map<String, Type> usedTypes = new HashMap<>();

      for (CrossProfileMethodInfo method : methods) {
        copySupportedTypesForMethod(context, usedTypes, method);
      }

      this.usableTypes = usedTypes;

      return this;
    }

    private void copySupportedTypesForMethod(
        Context context, Map<String, Type> usedTypes, CrossProfileMethodInfo method) {
      copySupportedType(context, usedTypes, method.returnType());
      for (TypeMirror argumentType : method.parameterTypes()) {
        copySupportedType(context, usedTypes, argumentType);
      }
    }

    private void copySupportedType(Context context, Map<String, Type> usedTypes, TypeMirror type) {
      if (TypeUtils.isGeneric(type)) {
        copySupportedGenericType(context, usedTypes, type);
        return;
      }

      if (TypeUtils.isArray(type)) {
        if (!TypeUtils.isPrimitiveArray(type)) {
          // Primitive arrays aren't resolved recursively
          copySupportedType(context, usedTypes, TypeUtils.extractTypeFromArray(type));
          type =
              context
                  .types()
                  .getArrayType(context.elements().getTypeElement("java.lang.Object").asType());
        }
      }

      // The type must have been seen in when constructing the original so this should not
      // be null
      Type supportedType = usableTypes.get(type.toString());

      // We don't support generic callbacks so any callback interfaces can be picked up here
      if (supportedType.isCrossProfileCallbackInterface()) {
        for (TypeMirror typeMirror :
            supportedType.getCrossProfileCallbackInterface().get().argumentTypes()) {
          copySupportedType(context, usedTypes, typeMirror);
        }
      }

      copySupportedType(usedTypes, supportedType);
    }

    private void copySupportedType(Map<String, Type> usedTypes, Type supportedType) {
      addUsableType(usedTypes, supportedType);
    }

    private void copySupportedGenericType(
        Context context, Map<String, Type> usedTypes, TypeMirror type) {
      TypeMirror genericType = TypeUtils.removeTypeArguments(type);

      // The type must have been seen in when constructing the oldSupportedTypes so this should not
      // be null
      Type supportedType = usableTypes.get(genericType.toString());

      if (!supportedType.isSupportedWithAnyGenericType()) {
        // We need to recursively copy all type arguments
        for (TypeMirror typeArgument : TypeUtils.extractTypeArguments(type)) {
          copySupportedType(context, usedTypes, typeArgument);
        }
      }

      copySupportedType(usedTypes, supportedType);
    }

    /** Add additianal parcelable wrappers. */
    public Builder addParcelableWrappers(Collection<ParcelableWrapper> parcelableWrappers) {
      Map<String, Type> newUsableTypes = new HashMap<>(usableTypes);

      addParcelableWrapperTypes(newUsableTypes, parcelableWrappers);

      usableTypes = newUsableTypes;

      return this;
    }

    /** Add additianal future wrappers. */
    public Builder addFutureWrappers(Collection<FutureWrapper> futureWrappers) {
      Map<String, Type> newUsableTypes = new HashMap<>(usableTypes);

      addFutureWrapperTypes(newUsableTypes, futureWrappers);

      usableTypes = newUsableTypes;

      return this;
    }

    public Builder replaceWrapperPrefix(ClassName prefix) {
      Map<String, Type> newUsableTypes = new HashMap<>();

      for (Type usableType : usableTypes.values()) {
        if (usableType.getParcelableWrapper().isPresent()) {
          replaceParcelableWrapperPrefix(newUsableTypes, prefix, usableType);
        } else if (usableType.getFutureWrapper().isPresent()) {
          replaceFutureWrapperPrefix(newUsableTypes, prefix, usableType);
        } else {
          addUsableType(newUsableTypes, usableType);
        }
      }

      usableTypes = newUsableTypes;

      return this;
    }

    private void replaceParcelableWrapperPrefix(
        Map<String, Type> newUsableTypes, ClassName prefix, Type usableType) {
      ParcelableWrapper parcelableWrapper = usableType.getParcelableWrapper().get();

      if (parcelableWrapper.wrapperType().equals(ParcelableWrapper.WrapperType.CUSTOM)) {
        // Custom types never get prefixed
        addUsableType(newUsableTypes, usableType);
        return;
      }

      addParcelableWrapperType(
          newUsableTypes,
          ParcelableWrapper.create(
              parcelableWrapper.wrappedType(),
              parcelableWrapper.defaultWrapperClassName(),
              prefix(prefix, parcelableWrapper.wrapperClassName()),
              parcelableWrapper.wrapperType()));
    }

    private void replaceFutureWrapperPrefix(
        Map<String, Type> newUsableTypes, ClassName prefix, Type usableType) {
      FutureWrapper futureWrapper = usableType.getFutureWrapper().get();

      if (futureWrapper.wrapperType().equals(FutureWrapper.WrapperType.CUSTOM)) {
        // Custom types never get prefixed
        addUsableType(newUsableTypes, usableType);
        return;
      }

      addFutureWrapperType(
          newUsableTypes,
          FutureWrapper.create(
              futureWrapper.wrappedType(),
              futureWrapper.defaultWrapperClassName(),
              prefix(prefix, futureWrapper.wrapperClassName()),
              futureWrapper.wrapperType()));
    }

    private ClassName prefix(ClassName prefix, ClassName finalName) {
      return ClassName.get(
          prefix.packageName(), prefix.simpleName() + "_" + finalName.simpleName());
    }

    /** Build a new {@link SupportedTypes}. */
    public SupportedTypes build() {
      return new SupportedTypes(usableTypes);
    }
  }
}
