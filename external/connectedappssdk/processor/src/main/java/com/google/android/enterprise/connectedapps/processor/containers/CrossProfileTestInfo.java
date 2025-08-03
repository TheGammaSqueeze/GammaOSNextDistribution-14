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

import static java.util.stream.Collectors.toSet;

import com.google.android.enterprise.connectedapps.testing.annotations.CrossProfileTest;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.lang.model.element.TypeElement;

/** Wrapper of a {@link CrossProfileTest} annotated class. */
@AutoValue
public abstract class CrossProfileTestInfo {

  public abstract TypeElement crossProfileTestElement();

  public abstract ImmutableSet<CrossProfileConfigurationInfo> configurations();

  public static CrossProfileTestInfo create(
      ValidatorContext context, ValidatorCrossProfileTestInfo validatorCrossProfileTest) {

    Set<CrossProfileConfigurationInfo> configurations =
        ValidatorCrossProfileConfigurationInfo.createMultipleFromElement(
                context, validatorCrossProfileTest.configurationElement())
            .stream()
            .map(b -> CrossProfileConfigurationInfo.create(context, b))
            .collect(toSet());

    return new AutoValue_CrossProfileTestInfo(
        validatorCrossProfileTest.crossProfileTestElement(), ImmutableSet.copyOf(configurations));
  }
}
