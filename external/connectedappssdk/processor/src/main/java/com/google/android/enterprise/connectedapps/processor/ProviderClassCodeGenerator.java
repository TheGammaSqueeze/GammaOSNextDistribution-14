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

import com.google.android.enterprise.connectedapps.processor.containers.ConnectorInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileTypeInfo;
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.google.android.enterprise.connectedapps.processor.containers.ProviderClassInfo;

/** Generator of code for a single provider class. */
class ProviderClassCodeGenerator {
  private boolean generated = false;

  private final GeneratorContext generatorContext;
  private final InternalProviderClassGenerator internalProviderClassGenerator;
  private final ProviderClassInfo providerClass;
  private final ConnectorInfo connectorInfo;

  ProviderClassCodeGenerator(GeneratorContext generatorContext, ProviderClassInfo providerClass) {
    this.generatorContext = checkNotNull(generatorContext);
    this.providerClass = checkNotNull(providerClass);
    this.internalProviderClassGenerator =
        new InternalProviderClassGenerator(generatorContext, providerClass);
    this.connectorInfo = providerClass.connectorInfo();
  }

  void generate() {
    if (generated) {
      throw new IllegalStateException(
          "ProviderClassCodeGenerator#generate can only be called once");
    }
    generated = true;

    internalProviderClassGenerator.generate();

    generatedSharedCrossConnectionApi();
    if (connectorInfo.hasCrossProfileConnector()) {
      generateCrossProfileApi();
    }
    if (connectorInfo.hasCrossUserConnector()) {
      generateCrossUserApi();
    }
  }

  private void generatedSharedCrossConnectionApi() {
    for (CrossProfileTypeInfo crossProfileType : providerClass.allCrossProfileTypes()) {
      new SharedTypeCodeGenerator(generatorContext, providerClass, crossProfileType).generate();
    }
  }

  private void generateCrossProfileApi() {
    for (CrossProfileTypeInfo crossProfileType : providerClass.allCrossProfileTypes()) {
      new CrossProfileTypeCodeGenerator(generatorContext, providerClass, crossProfileType)
          .generate();
    }
  }

  private void generateCrossUserApi() {
    for (CrossProfileTypeInfo crossProfileType : providerClass.allCrossProfileTypes()) {
      new CrossUserTypeCodeGenerator(generatorContext, providerClass, crossProfileType).generate();
    }
  }
}
