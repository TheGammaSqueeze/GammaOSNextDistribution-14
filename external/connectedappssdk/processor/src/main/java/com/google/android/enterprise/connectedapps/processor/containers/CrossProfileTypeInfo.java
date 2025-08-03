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

import static java.util.stream.Collectors.toCollection;

import com.google.android.enterprise.connectedapps.annotations.CrossProfile;
import com.google.android.enterprise.connectedapps.processor.ProcessorConfiguration;
import com.google.android.enterprise.connectedapps.processor.SupportedTypes;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Hashing;
import com.squareup.javapoet.ClassName;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/** Wrapper of a {@link CrossProfile} type. */
@AutoValue
public abstract class CrossProfileTypeInfo {

  public abstract TypeElement crossProfileTypeElement();

  public abstract ImmutableCollection<CrossProfileMethodInfo> crossProfileMethods();

  public abstract SupportedTypes supportedTypes();

  public abstract Optional<ConnectorInfo> connectorInfo();

  /**
   * The verbatim (not prefixed) name of the interface class used to make cross-profile or
   * cross-user calls.
   */
  public abstract ClassName generatedClassName();

  public String simpleName() {
    return crossProfileTypeElement().getSimpleName().toString();
  }

  public ClassName className() {
    return ClassName.get(crossProfileTypeElement());
  }

  public boolean isStatic() {
    return crossProfileMethods().stream().allMatch(CrossProfileMethodInfo::isStatic);
  }

  /**
   * Get a numeric identifier for the cross-profile type.
   *
   * <p>This identifier is based on the type's qualified name, and will not change between runs.
   */
  public long identifier() {
    // Stored in a 64 bit long, with ~200 cross-profile types, chance of collision is 1 in 10^15
    return Hashing.murmur3_128()
        .hashString(crossProfileTypeElement().getQualifiedName().toString(), StandardCharsets.UTF_8)
        .asLong();
  }

  public static CrossProfileTypeInfo create(
      ValidatorContext context, ValidatorCrossProfileTypeInfo crossProfileType) {
    TypeElement crossProfileTypeElement = crossProfileType.crossProfileTypeElement();

    List<ExecutableElement> crossProfileMethodElements = crossProfileType.crossProfileMethods();

    Collection<CrossProfileMethodInfo> crossProfileMethods =
        IntStream.range(0, crossProfileMethodElements.size())
            .mapToObj(
                t ->
                    CrossProfileMethodInfo.create(
                        t, crossProfileType, crossProfileMethodElements.get(t), context))
            .collect(toCollection(LinkedHashSet::new));

    SupportedTypes.Builder supportedTypesBuilder = crossProfileType.supportedTypes().asBuilder();

    supportedTypesBuilder.filterUsed(context, crossProfileMethods);

    if (ProcessorConfiguration.GENERATE_TYPE_SPECIFIC_WRAPPERS) {
      supportedTypesBuilder.replaceWrapperPrefix(
          ClassName.bestGuess(
              crossProfileType.crossProfileTypeElement().getQualifiedName().toString()));
    }

    return new AutoValue_CrossProfileTypeInfo(
        crossProfileTypeElement,
        ImmutableSet.copyOf(crossProfileMethods),
        supportedTypesBuilder.build(),
        crossProfileType.connectorInfo(),
        findGeneratedClassName(context, crossProfileTypeElement));
  }

  private static ClassName findGeneratedClassName(
      ValidatorContext context, TypeElement typeElement) {
    return ClassName.get(
        context.elements().getPackageOf(typeElement).getQualifiedName().toString(),
        typeElement.getSimpleName().toString());
  }
}
