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
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileConfigurationInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileTestInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileTypeInfo;
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.google.android.enterprise.connectedapps.processor.containers.ProviderClassInfo;
import java.util.HashSet;
import java.util.Set;

/**
 * Generator of cross-profile test code.
 *
 * <p>This is intended to be initialised and used once, which will generate all needed code.
 *
 * <p>This must only be used once. It should be used after {@link EarlyValidator} has been used to
 * validate that the annotated code is correct.
 */
final class TestCodeGenerator {
  private boolean generated = false;
  private final GeneratorContext generatorContext;
  private final Set<ConnectorInfo> fakedConnectors = new HashSet<>();
  private final Set<CrossProfileTypeInfo> allFakedTypes = new HashSet<>();
  private final Set<CrossProfileTypeInfo> crossProfileFakedTypes = new HashSet<>();
  private final Set<CrossProfileTypeInfo> crossUserFakedTypes = new HashSet<>();

  TestCodeGenerator(GeneratorContext generatorContext) {
    this.generatorContext = checkNotNull(generatorContext);
  }

  void generate() {
    if (generated) {
      throw new IllegalStateException("TestCodeGenerator#generate can only be called once");
    }
    generated = true;

    collectTestTypes();
    generateFakes();
  }

  private void generateFakes() {
    for (ConnectorInfo connectorInfo : fakedConnectors) {
      if (connectorInfo.hasCrossProfileConnector()) {
        new FakeProfileConnectorGenerator(generatorContext, connectorInfo.profileConnector().get())
            .generate();
      }

      if (connectorInfo.hasCrossUserConnector()) {
        new FakeUserConnectorGenerator(generatorContext, connectorInfo.userConnector().get())
            .generate();
      }
    }

    for (CrossProfileTypeInfo type : allFakedTypes) {
      new FakeOtherGenerator(generatorContext, type).generate();
    }

    for (CrossProfileTypeInfo type : crossProfileFakedTypes) {
      new FakeCrossProfileTypeGenerator(generatorContext, type).generate();
    }

    for (CrossProfileTypeInfo type : crossUserFakedTypes) {
      new FakeCrossUserTypeGenerator(generatorContext, type).generate();
    }
  }

  private void collectTestTypes() {
    for (CrossProfileTestInfo crossProfileTest : generatorContext.crossProfileTests()) {
      collectTestTypes(crossProfileTest);
    }
  }

  private void collectTestTypes(CrossProfileTestInfo crossProfileTest) {
    for (CrossProfileConfigurationInfo configuration : crossProfileTest.configurations()) {
      collectTestTypes(configuration);
    }
  }

  private void collectTestTypes(CrossProfileConfigurationInfo configuration) {
    if (configuration.connectorInfo().hasCrossProfileConnector()) {
      for (ProviderClassInfo provider : configuration.providers()) {
        collectTestTypes(crossProfileFakedTypes, provider);
      }
    }
    if (configuration.connectorInfo().hasCrossUserConnector()) {
      for (ProviderClassInfo provider : configuration.providers()) {
        collectTestTypes(crossUserFakedTypes, provider);
      }
    }

    fakedConnectors.add(configuration.connectorInfo());
  }

  private void collectTestTypes(Set<CrossProfileTypeInfo> targetSet, ProviderClassInfo provider) {
    for (CrossProfileTypeInfo type : provider.allCrossProfileTypes()) {
      collectTestTypes(targetSet, type);
    }
  }

  private void collectTestTypes(Set<CrossProfileTypeInfo> targetSet, CrossProfileTypeInfo type) {
    allFakedTypes.add(type);
    targetSet.add(type);
  }
}
