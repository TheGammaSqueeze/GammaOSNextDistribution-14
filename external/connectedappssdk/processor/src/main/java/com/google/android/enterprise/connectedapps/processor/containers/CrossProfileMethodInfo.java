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
package com.google.android.enterprise.connectedapps.processor.containers;

import static com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationFinder.hasCrossProfileCallbackAnnotation;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

import com.google.android.enterprise.connectedapps.annotations.Cacheable;
import com.google.android.enterprise.connectedapps.annotations.CrossProfile;
import com.google.android.enterprise.connectedapps.annotations.CrossProfileCallback;
import com.google.android.enterprise.connectedapps.processor.SupportedTypes;
import com.google.android.enterprise.connectedapps.processor.TypeUtils;
import com.google.auto.value.AutoValue;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

/** Wrapper of a {@link CrossProfile} annotated method. */
@AutoValue
public abstract class CrossProfileMethodInfo {

  public abstract ExecutableElement methodElement();

  public abstract int identifier();

  public abstract boolean isStatic();

  public abstract boolean isCacheable();

  public String simpleName() {
    return methodElement().getSimpleName().toString();
  }

  public TypeMirror returnType() {
    return methodElement().getReturnType();
  }

  public TypeName returnTypeTypeName() {
    return ClassName.get(returnType());
  }

  public Collection<TypeName> thrownExceptions() {
    return methodElement().getThrownTypes().stream()
        .map(ClassName::get)
        .collect(toSet());
  }

  public Collection<TypeMirror> automaticallyResolvedParameterTypes(SupportedTypes supportedTypes) {
    return parameterTypes().stream()
        .filter(supportedTypes::isAutomaticallyResolved)
        .collect(toSet());
  }

  /**
   * Specify behaviour when encountering parameters of a type which is automatically resolved by the
   * SDK.
   */
  public enum AutomaticallyResolvedParameterFilterBehaviour {
    /** Do not change the parameters. */
    LEAVE_AUTOMATICALLY_RESOLVED_PARAMETERS,
    /** Remove the parameters and act as if they are not present. */
    REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS,
    /** Replace the parameter with the variable specified in the type configuration. */
    REPLACE_AUTOMATICALLY_RESOLVED_PARAMETERS
  }

  /**
   * A string of parameter names separated by commas.
   *
   * <p>This is useful when generating a call for this method, using the same parameter names.
   *
   * <p>Parameters which are automatically resolved will be removed.
   */
  public String commaSeparatedParameters(
      SupportedTypes supportedTypes,
      AutomaticallyResolvedParameterFilterBehaviour filterBehaviour) {
    return commaSeparatedParameters(supportedTypes, filterBehaviour, Function.identity());
  }

  /**
   * A string of parameter names separated by commas.
   *
   * <p>This is useful when generating a call for this method, using the same parameter names.
   *
   * <p>Parameters which are automatically resolved will be removed.
   */
  public String commaSeparatedParameters(
      SupportedTypes supportedTypes,
      AutomaticallyResolvedParameterFilterBehaviour filterBehaviour,
      Function<String, String> map) {
    if (filterBehaviour
        == AutomaticallyResolvedParameterFilterBehaviour.REMOVE_AUTOMATICALLY_RESOLVED_PARAMETERS) {
      return methodElement().getParameters().stream()
          .filter(p -> !supportedTypes.isAutomaticallyResolved(p.asType()))
          .map(p -> p.getSimpleName().toString())
          .map(map)
          .collect(joining(", "));
    } else if (filterBehaviour
        == AutomaticallyResolvedParameterFilterBehaviour
            .REPLACE_AUTOMATICALLY_RESOLVED_PARAMETERS) {
      return methodElement().getParameters().stream()
          .map(
              p ->
                  supportedTypes.isAutomaticallyResolved(p.asType())
                      ? supportedTypes.getAutomaticallyResolvedReplacement(p.asType())
                      : p.getSimpleName().toString())
          .map(map)
          .collect(joining(", "));
    } else if (filterBehaviour
        == AutomaticallyResolvedParameterFilterBehaviour.LEAVE_AUTOMATICALLY_RESOLVED_PARAMETERS) {
      return methodElement().getParameters().stream()
          .map(p -> p.getSimpleName().toString())
          .map(map)
          .collect(joining(", "));
    }
    throw new IllegalArgumentException("Invalid filter behaviour: " + filterBehaviour);
  }

  /** An unordered collection of the types used in the parameters of this method. */
  public Collection<TypeMirror> parameterTypes() {
    return methodElement().getParameters().stream().map(Element::asType).collect(toSet());
  }

  /**
   * True if both {@link #isCrossProfileCallback(GeneratorContext)} and {@link
   * #isFuture(CrossProfileTypeInfo)} are {@code False}.
   */
  public boolean isBlocking(GeneratorContext context, CrossProfileTypeInfo type) {
    return !isCrossProfileCallback(context) && !isFuture(type);
  }

  /** True if any argument is annotated with {@link CrossProfileCallback}. */
  public boolean isCrossProfileCallback(GeneratorContext generatorContext) {
    return getCrossProfileCallbackParam(generatorContext).isPresent();
  }

  /** True if there is only a single {@link CrossProfileCallback} argument and it is simple. */
  public boolean isSimpleCrossProfileCallback(GeneratorContext generatorContext) {
    Optional<CrossProfileCallbackParameterInfo> param =
        getCrossProfileCallbackParam(generatorContext);

    if (param.isPresent()) {
      return param.get().crossProfileCallbackInterface().isSimple();
    }

    return false;
  }

  /** True if the return type is a supported {@code Future} type. */
  public boolean isFuture(CrossProfileTypeInfo type) {
    return isFuture(type.supportedTypes(), methodElement());
  }

  public static boolean isFuture(SupportedTypes supportedTypes, ExecutableElement method) {
    return supportedTypes.isFuture(TypeUtils.removeTypeArguments(method.getReturnType()));
  }

  /** Return the {@link CrossProfileCallback} annotated parameter, if any. */
  public Optional<CrossProfileCallbackParameterInfo> getCrossProfileCallbackParam(
      GeneratorContext generatorContext) {
    return getCrossProfileCallbackParam(generatorContext, methodElement());
  }

  public static Optional<CrossProfileCallbackParameterInfo> getCrossProfileCallbackParam(
      Context context, ExecutableElement method) {
    return method.getParameters().stream()
        .filter(v -> isCrossProfileCallbackInterface(context.elements(), v.asType()))
        .findFirst()
        .map(e -> (VariableElement) e)
        .map(e -> CrossProfileCallbackParameterInfo.create(context, e));
  }

  private static boolean isCrossProfileCallbackInterface(Elements elements, TypeMirror type) {
    TypeElement typeElement = elements.getTypeElement(type.toString());
    return typeElement != null && hasCrossProfileCallbackAnnotation(typeElement);
  }

  public static CrossProfileMethodInfo create(
      int identifier,
      ValidatorCrossProfileTypeInfo type,
      ExecutableElement methodElement,
      Context context) {
    return new AutoValue_CrossProfileMethodInfo(
        methodElement,
        identifier,
        methodElement.getModifiers().contains(Modifier.STATIC),
        methodElement.getAnnotation(Cacheable.class) != null);
  }
}
