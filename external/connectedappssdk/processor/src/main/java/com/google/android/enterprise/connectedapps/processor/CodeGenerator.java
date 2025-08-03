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

import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileCallbackInterfaceInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileConfigurationInfo;
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.google.android.enterprise.connectedapps.processor.containers.ProfileConnectorInfo;
import com.google.android.enterprise.connectedapps.processor.containers.ProviderClassInfo;
import com.google.android.enterprise.connectedapps.processor.containers.UserConnectorInfo;

/**
 * Generator of code for connected apps.
 *
 * <p>This is intended to be initialised and used once, which will generate all needed code.
 *
 * <p>This must only be used once. It should be used after {@link EarlyValidator} has been used to
 * validate that the annotated code is correct.
 */
final class CodeGenerator {
  private boolean generated = false;
  private final GeneratorContext generatorContext;
  private final ParcelableWrappersGenerator parcelableWrappersGenerator;
  private final FutureWrappersGenerator futureWrappersGenerator;
  private final TestCodeGenerator testCodeGenerator;

  CodeGenerator(GeneratorContext generatorContext) {
    this.generatorContext = checkNotNull(generatorContext);
    this.parcelableWrappersGenerator = new ParcelableWrappersGenerator(generatorContext);
    this.futureWrappersGenerator = new FutureWrappersGenerator(generatorContext);
    this.testCodeGenerator = new TestCodeGenerator(generatorContext);
  }

  void generate() {
    if (generated) {
      throw new IllegalStateException("CodeGenerator#generate can only be called once");
    }
    generated = true;

    parcelableWrappersGenerator.generate();
    futureWrappersGenerator.generate();
    testCodeGenerator.generate();

    for (ProfileConnectorInfo connector : generatorContext.generatedProfileConnectors()) {
      new ProfileConnectorCodeGenerator(generatorContext, connector).generate();
    }

    for (UserConnectorInfo connector : generatorContext.generatedUserConnectors()) {
      new UserConnectorCodeGenerator(generatorContext, connector).generate();
    }

    for (CrossProfileConfigurationInfo configuration : generatorContext.configurations()) {
      new ConfigurationCodeGenerator(generatorContext, configuration).generate();
    }

    for (ProviderClassInfo providerClass : generatorContext.providers()) {
      new ProviderClassCodeGenerator(generatorContext, providerClass).generate();
    }

    for (CrossProfileCallbackInterfaceInfo callbackInterface :
        generatorContext.crossProfileCallbackInterfaces()) {
      new CrossProfileCallbackCodeGenerator(generatorContext, callbackInterface).generate();
    }
  }
}
