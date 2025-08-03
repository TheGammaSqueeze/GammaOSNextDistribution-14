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

class SharedTypeCodeGenerator {
  private boolean generated = false;
  private final InterfaceGenerator interfaceGenerator;
  private final CurrentProfileGenerator currentProfileGenerator;
  private final OtherProfileGenerator otherProfileGenerator;
  private final IfAvailableGenerator ifAvailableGenerator;
  private final AlwaysThrowsGenerator alwaysThrowsGenerator;
  private final InternalCrossProfileClassGenerator internalCrossProfileClassGenerator;
  private final BundlerGenerator bundlerGenerator;

  public SharedTypeCodeGenerator(
      GeneratorContext generatorContext,
      ProviderClassInfo providerClass,
      CrossProfileTypeInfo crossProfileType) {
    checkNotNull(generatorContext);
    checkNotNull(crossProfileType);
    this.interfaceGenerator = new InterfaceGenerator(generatorContext, crossProfileType);
    this.currentProfileGenerator = new CurrentProfileGenerator(generatorContext, crossProfileType);
    this.otherProfileGenerator = new OtherProfileGenerator(generatorContext, crossProfileType);
    this.ifAvailableGenerator = new IfAvailableGenerator(generatorContext, crossProfileType);
    this.alwaysThrowsGenerator = new AlwaysThrowsGenerator(generatorContext, crossProfileType);
    this.internalCrossProfileClassGenerator =
        new InternalCrossProfileClassGenerator(generatorContext, providerClass, crossProfileType);
    this.bundlerGenerator = new BundlerGenerator(generatorContext, crossProfileType);
  }

  void generate() {
    if (generated) {
      throw new IllegalStateException("SharedTypeCodeGenerator#generate can only be called once");
    }
    generated = true;

    interfaceGenerator.generate();
    currentProfileGenerator.generate();
    otherProfileGenerator.generate();
    ifAvailableGenerator.generate();
    alwaysThrowsGenerator.generate();
    internalCrossProfileClassGenerator.generate();
    bundlerGenerator.generate();
  }
}
