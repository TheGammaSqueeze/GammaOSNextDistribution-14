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

import static com.google.android.enterprise.connectedapps.processor.GeneratorUtilities.findCrossProfileProviderMethodsInClass;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import java.util.stream.Stream;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;

/** Wrapper of a cross-profile provider class. */
@AutoValue
public abstract class ProviderClassInfo {

  public abstract TypeElement providerClassElement();

  public ImmutableCollection<CrossProfileTypeInfo> allCrossProfileTypes() {
    ImmutableSet.Builder<CrossProfileTypeInfo> types = ImmutableSet.builder();
    types.addAll(nonStaticTypes());
    types.addAll(staticTypes());
    return types.build();
  }

  public abstract ImmutableCollection<CrossProfileTypeInfo> nonStaticTypes();

  public abstract ImmutableCollection<CrossProfileTypeInfo> staticTypes();

  public String simpleName() {
    return providerClassElement().getSimpleName().toString();
  }

  public ClassName className() {
    return ClassName.get(providerClassElement());
  }

  public abstract ConnectorInfo connectorInfo();

  public ImmutableCollection<VariableElement> publicConstructorArgumentTypes() {
    return ImmutableList.copyOf(
        providerClassElement().getEnclosedElements().stream()
            .filter(e -> e instanceof ExecutableElement)
            .map(e -> (ExecutableElement) e)
            .filter(e -> e.getKind().equals(ElementKind.CONSTRUCTOR))
            .filter(e -> e.getModifiers().contains(Modifier.PUBLIC))
            .findFirst()
            .get()
            .getParameters());
  }

  public ExecutableElement findProviderMethodFor(
      GeneratorContext generatorContext, CrossProfileTypeInfo crossProfileType) {
    if (!nonStaticTypes().contains(crossProfileType)) {
      throw new IllegalArgumentException("This provider class does not provide this type");
    }

    return providerClassElement().getEnclosedElements().stream()
        .filter(e -> e instanceof ExecutableElement)
        .map(e -> (ExecutableElement) e)
        .filter(
            e ->
                generatorContext
                    .types()
                    .isSameType(
                        e.getReturnType(), crossProfileType.crossProfileTypeElement().asType()))
        .findFirst()
        .get();
  }

  public static ProviderClassInfo create(
      ValidatorContext context, ValidatorProviderClassInfo provider) {
    ImmutableSet.Builder<CrossProfileTypeInfo> nonStaticTypesBuilder = ImmutableSet.builder();
    extractCrossProfileTypeElementsFromReturnValues(
            context.elements(), provider.providerClassElement())
        .stream()
        .map(
            crossProfileTypeElement ->
                ValidatorCrossProfileTypeInfo.create(
                    context, crossProfileTypeElement, context.globalSupportedTypes()))
        .map(crossProfileType -> CrossProfileTypeInfo.create(context, crossProfileType))
        .forEach(nonStaticTypesBuilder::add);
    ImmutableSet<CrossProfileTypeInfo> nonStaticTypes = nonStaticTypesBuilder.build();

    ImmutableSet.Builder<CrossProfileTypeInfo> staticTypesBuilder = ImmutableSet.builder();
    provider.staticTypes().stream()
        .map(
            crossProfileTypeElement ->
                ValidatorCrossProfileTypeInfo.create(
                    context, crossProfileTypeElement, context.globalSupportedTypes()))
        .map(crossProfileType -> CrossProfileTypeInfo.create(context, crossProfileType))
        .forEach(staticTypesBuilder::add);
    ImmutableSet<CrossProfileTypeInfo> staticTypes = staticTypesBuilder.build();

    return new AutoValue_ProviderClassInfo(
        provider.providerClassElement(),
        nonStaticTypes,
        staticTypes,
        findConnector(context, staticTypes, nonStaticTypes));
  }

  public static ImmutableSet<TypeElement> extractCrossProfileTypeElementsFromReturnValues(
      Elements elements, TypeElement providerClassElement) {
    ImmutableSet.Builder<TypeElement> result = ImmutableSet.builder();
    findCrossProfileProviderMethodsInClass(providerClassElement).stream()
        .map(e -> elements.getTypeElement(e.getReturnType().toString()))
        .forEach(result::add);
    return result.build();
  }

  private static ConnectorInfo findConnector(
      ValidatorContext context,
      ImmutableSet<CrossProfileTypeInfo> staticTypes,
      ImmutableSet<CrossProfileTypeInfo> nonStaticTypes) {
    return Stream.concat(staticTypes.stream(), nonStaticTypes.stream())
        .filter(typeInfo -> typeInfo.connectorInfo().isPresent())
        .map(typeInfo -> typeInfo.connectorInfo().get())
        .findFirst()
        .orElse(ConnectorInfo.unspecified(context, context.globalSupportedTypes()));
  }
}
