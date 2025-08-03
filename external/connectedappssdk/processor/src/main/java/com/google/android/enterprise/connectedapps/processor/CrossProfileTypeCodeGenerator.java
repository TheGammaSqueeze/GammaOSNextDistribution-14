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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileTypeInfo;
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.google.android.enterprise.connectedapps.processor.containers.ProviderClassInfo;

class CrossProfileTypeCodeGenerator {
  private boolean generated = false;
  private final CrossProfileTypeInterfaceGenerator crossProfileTypeInterfaceGenerator;
  private final MultipleProfilesGenerator multipleProfilesGenerator;
  private final DefaultProfileClassGenerator defaultProfileClassGenerator;

  public CrossProfileTypeCodeGenerator(
      GeneratorContext generatorContext,
      ProviderClassInfo providerClass,
      CrossProfileTypeInfo crossProfileType) {
    checkNotNull(generatorContext);
    checkNotNull(crossProfileType);
    this.crossProfileTypeInterfaceGenerator =
        new CrossProfileTypeInterfaceGenerator(generatorContext, crossProfileType);
    this.multipleProfilesGenerator =
        new MultipleProfilesGenerator(generatorContext, crossProfileType);
    this.defaultProfileClassGenerator =
        new DefaultProfileClassGenerator(generatorContext, crossProfileType);
  }

  void generate() {
    if (generated) {
      throw new IllegalStateException(
          "CrossProfileTypeCodeGenerator#generate can only be called once");
    }
    generated = true;

    crossProfileTypeInterfaceGenerator.generate();
    multipleProfilesGenerator.generate();
    defaultProfileClassGenerator.generate();
  }
}
