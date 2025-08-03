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

import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.PARCELABLE_CREATOR_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.PARCEL_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.containers.CrossProfileMethodInfo.AutomaticallyResolvedParameterFilterBehaviour.LEAVE_AUTOMATICALLY_RESOLVED_PARAMETERS;
import static com.google.android.enterprise.connectedapps.processor.containers.CrossProfileMethodInfo.AutomaticallyResolvedParameterFilterBehaviour.REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS;
import static com.google.android.enterprise.connectedapps.processor.containers.CrossProfileMethodInfo.AutomaticallyResolvedParameterFilterBehaviour.REPLACE_AUTOMATICALLY_RESOLVED_PARAMETERS;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toList;

import com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationFinder;
import com.google.android.enterprise.connectedapps.processor.containers.Context;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileMethodInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileMethodInfo.AutomaticallyResolvedParameterFilterBehaviour;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileTypeInfo;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;

/** Utility methods used for code generation. */
public final class GeneratorUtilities {

  private final Context context;

  public GeneratorUtilities(Context context) {
    this.context = checkNotNull(context);
  }

  /**
   * Extract a class provided in an annotation.
   *
   * <p>The {@code runnable} should call the annotation method that the class is being extracted
   * for.
   */
  public static TypeElement extractClassFromAnnotation(Types types, Runnable runnable) {
    // From https://docs.oracle.com/javase/8/docs/api/javax/lang/model/AnnotatedConstruct.html
    // "The annotation returned by this method could contain an element whose value is of type
    // Class. This value cannot be returned directly: information necessary to locate and load a
    // class (such as the class loader to use) is not available, and the class might not be loadable
    // at all. Attempting to read a Class object by invoking the relevant method on the returned
    // annotation will result in a MirroredTypeException, from which the corresponding TypeMirror
    // may be extracted."
    try {
      runnable.run();
    } catch (MirroredTypeException e) {
      return e.getTypeMirrors().stream()
          .map(t -> (TypeElement) types.asElement(t))
          .findFirst()
          .get();
    }
    throw new AssertionError("Could not extract class from annotation");
  }

  /**
   * Extract classes provided in an annotation.
   *
   * <p>The {@code runnable} should call the annotation method that the classes are being extracted
   * for.
   */
  public static List<TypeElement> extractClassesFromAnnotation(Types types, Runnable runnable) {
    // From https://docs.oracle.com/javase/8/docs/api/javax/lang/model/AnnotatedConstruct.html
    // "The annotation returned by this method could contain an element whose value is of type
    // Class. This value cannot be returned directly: information necessary to locate and load a
    // class (such as the class loader to use) is not available, and the class might not be loadable
    // at all. Attempting to read a Class object by invoking the relevant method on the returned
    // annotation will result in a MirroredTypeException, from which the corresponding TypeMirror
    // may be extracted."
    try {
      runnable.run();
    } catch (MirroredTypesException e) {
      return e.getTypeMirrors().stream()
          .map(t -> (TypeElement) types.asElement(t))
          .collect(toList());
    }
    throw new AssertionError("Could not extract classes from annotation");
  }

  public static ImmutableSet<ExecutableElement> findCrossProfileMethodsInClass(TypeElement clazz) {
    ImmutableSet.Builder<ExecutableElement> result = ImmutableSet.builder();
    clazz.getEnclosedElements().stream()
        .filter(e -> e instanceof ExecutableElement)
        .map(e -> (ExecutableElement) e)
        .filter(e -> e.getKind() == ElementKind.METHOD)
        .filter(AnnotationFinder::hasCrossProfileAnnotation)
        .forEach(result::add);
    return result.build();
  }

  public static ImmutableSet<ExecutableElement> findCrossProfileProviderMethodsInClass(
      TypeElement clazz) {
    ImmutableSet.Builder<ExecutableElement> result = ImmutableSet.builder();
    clazz.getEnclosedElements().stream()
        .filter(e -> e instanceof ExecutableElement)
        .map(e -> (ExecutableElement) e)
        .filter(e -> e.getKind() == ElementKind.METHOD)
        .filter(AnnotationFinder::hasCrossProfileProviderAnnotation)
        .forEach(result::add);
    return result.build();
  }

  /** Generate a {@code @link} reference to a given method. */
  public static CodeBlock methodJavadocReference(ExecutableElement method) {
    CodeBlock.Builder methodCall = CodeBlock.builder();
    methodCall.add("{@link $T#", method.getEnclosingElement());
    methodCall.add("$L(", method.getSimpleName());

    if (!method.getParameters().isEmpty()) {
      methodCall.add("$T", method.getParameters().iterator().next().asType());

      for (VariableElement param :
          method.getParameters().subList(1, method.getParameters().size())) {
        methodCall.add(",$T", param.asType());
      }
    }

    methodCall.add(")}");
    return methodCall.build();
  }

  public void writeClassToFile(String packageName, TypeSpec.Builder clazzBuilder) {
    writeClassToFile(packageName, clazzBuilder.build());
  }

  void writeClassToFile(String packageName, TypeSpec clazz) {
    final String qualifiedClassName =
        packageName.isEmpty() ? clazz.name : packageName + "." + clazz.name;

    JavaFile javaFile = JavaFile.builder(packageName, clazz).build();
    try {
      JavaFileObject builderFile =
          context.processingEnv().getFiler().createSourceFile(qualifiedClassName);
      try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
        javaFile.writeTo(out);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Error writing " + qualifiedClassName + " to file", e);
    }
  }

  /**
   * Take the parameters of an {@link ExecutableElement} and return {@link ParameterSpec} instances
   * ready to be used with a generated method.
   */
  static List<ParameterSpec> extractParametersFromMethod(
      SupportedTypes supportedTypes,
      ExecutableElement method,
      AutomaticallyResolvedParameterFilterBehaviour filterBehaviour) {
    if (filterBehaviour == LEAVE_AUTOMATICALLY_RESOLVED_PARAMETERS) {
      return extractParametersFromMethod(method);
    } else if (filterBehaviour == REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS) {
      return method.getParameters().stream()
          .filter(param -> !supportedTypes.isAutomaticallyResolved(param.asType()))
          .map(GeneratorUtilities::convertVariableToParameterSpec)
          .collect(toList());
    } else if (filterBehaviour == REPLACE_AUTOMATICALLY_RESOLVED_PARAMETERS) {
      throw new IllegalArgumentException("Can not replace parameters when extracting");
    }
    throw new IllegalArgumentException("Unknown filterBehaviour " + filterBehaviour);
  }

  /**
   * Take the parameters of an {@link ExecutableElement} and return {@link ParameterSpec} instances
   * ready to be used with a generated method.
   *
   * <p>This will not filter automatically resolved parameters. For that functionality use {@link
   * #extractParametersFromMethod(SupportedTypes, ExecutableElement,
   * AutomaticallyResolvedParameterFilterBehaviour)}.
   */
  static List<ParameterSpec> extractParametersFromMethod(ExecutableElement method) {
    return method.getParameters().stream()
        .map(GeneratorUtilities::convertVariableToParameterSpec)
        .collect(toList());
  }

  private static ParameterSpec convertVariableToParameterSpec(VariableElement variable) {
    ParameterSpec.Builder builder =
        ParameterSpec.builder(
            ClassName.get(variable.asType()), variable.getSimpleName().toString());
    builder.addModifiers(variable.getModifiers());
    return builder.build();
  }

  /** If type is primitive, return the boxed version of that type, otherwise return the type. */
  TypeMirror boxIfNecessary(TypeMirror type) {
    if (!type.getKind().isPrimitive()) {
      return type;
    }

    PrimitiveType primitiveType = (PrimitiveType) type;
    return context.types().boxedClass(primitiveType).asType();
  }

  void addDefaultParcelableMethods(TypeSpec.Builder classBuilder, ClassName className) {
    classBuilder.addMethod(
        MethodSpec.methodBuilder("describeContents")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .returns(int.class)
            .addStatement("return 0")
            .build());

    TypeName creatorType = ParameterizedTypeName.get(PARCELABLE_CREATOR_CLASSNAME, className);

    TypeSpec creator =
        TypeSpec.anonymousClassBuilder("")
            .addSuperinterface(creatorType)
            .addMethod(
                MethodSpec.methodBuilder("createFromParcel")
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .returns(className)
                    .addParameter(PARCEL_CLASSNAME, "in")
                    .addStatement("return new $T(in)", className)
                    .build())
            .addMethod(
                MethodSpec.methodBuilder("newArray")
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .returns(ArrayTypeName.of(className))
                    .addParameter(int.class, "size")
                    .addStatement("return new $T[size]", className)
                    .build())
            .build();

    classBuilder.addField(
        FieldSpec.builder(creatorType, "CREATOR")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .addAnnotation(
                AnnotationSpec.builder(SuppressWarnings.class)
                    .addMember("value", "$S", "rawtypes")
                    .build())
            .initializer("$L", creator)
            .build());
  }

  /** Generate a reference to a cross-profile method which can be used in javadoc. */
  public static CodeBlock generateMethodReference(
      CrossProfileTypeInfo crossProfileType, CrossProfileMethodInfo method) {
    CodeBlock.Builder reference = CodeBlock.builder();

    reference.add("$T#$L(", crossProfileType.className(), method.simpleName());

    List<TypeMirror> parameterTypes = convertParametersToTypes(method);

    if (!parameterTypes.isEmpty()) {
      for (int i = 0; i < parameterTypes.size() - 1; i++) {
        reference.add("$T, ", TypeUtils.getRawTypeClassName(parameterTypes.get(i)));
      }
      reference.add("$T", TypeUtils.getRawTypeClassName(Iterables.getLast(parameterTypes)));
    }

    reference.add(")");
    return reference.build();
  }

  private static List<TypeMirror> convertParametersToTypes(CrossProfileMethodInfo method) {
    return method.methodElement().getParameters().stream().map(Element::asType).collect(toList());
  }
}
