package com.google.android.enterprise.connectedapps.processor;

import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.squareup.javapoet.ClassName;
import java.util.function.Function;
import javax.lang.model.element.TypeElement;

final class ClassNameUtilities {

  /**
   * Given an {@code originalClassName}, creates a new {@link ClassName} which has an identical
   * package, but with the type name transformed by a string function.
   */
  static ClassName transformClassName(
      ClassName originalClassName, Function<String, String> transformation) {
    return ClassName.get(
        originalClassName.packageName(), transformation.apply(originalClassName.simpleName()));
  }

  /**
   * When used with {@link #transformClassName(ClassName, Function)}, inserts the {@code string} in
   * front of the type name of the type being transformed.
   */
  static Function<String, String> prepend(String string) {
    return name -> string + name;
  }

  /**
   * When used with {@link #transformClassName(ClassName, Function)}, inserts the {@code string}
   * after the type name of the type being transformed.
   */
  static Function<String, String> append(String string) {
    return name -> name + string;
  }

  static ClassName getBuilderClassName(ClassName originalClassName) {
    return ClassName.get(
        originalClassName.packageName() + "." + originalClassName.simpleName(), "Builder");
  }

  /**
   * Creates a new {@link ClassName} which has the package of the {@link TypeElement} provided, and
   * the specified {@code simpleName}.
   */
  static ClassName classNameInferringPackageFromElement(
      GeneratorContext generatorContext, TypeElement packageElement, String simpleName) {
    return ClassName.get(
        generatorContext.elements().getPackageOf(packageElement).getQualifiedName().toString(),
        simpleName);
  }

  private ClassNameUtilities() {}
}
