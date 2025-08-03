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

import com.google.android.enterprise.connectedapps.annotations.CrossProfile;
import com.google.android.enterprise.connectedapps.annotations.CrossProfileConfiguration;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileConfigurationInfo;
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;

/**
 * Generator of code for a single {@link CrossProfileConfiguration}.
 *
 * <p>The {@code Service} will only be generated if the configuration contains at least one provider
 * class which has at least one {@link CrossProfile} type.
 */
class ConfigurationCodeGenerator {
  private boolean generated = false;
  private final ServiceGenerator serviceGenerator;
  private final DispatcherGenerator dispatcherGenerator;

  ConfigurationCodeGenerator(
      GeneratorContext generatorContext, CrossProfileConfigurationInfo configuration) {
    checkNotNull(configuration);
    this.serviceGenerator = new ServiceGenerator(checkNotNull(generatorContext), configuration);
    this.dispatcherGenerator = new DispatcherGenerator(generatorContext, configuration);
  }

  void generate() {
    if (generated) {
      throw new IllegalStateException(
          "ConfigurationCodeGenerator#generate can only be called once");
    }
    generated = true;

    serviceGenerator.generate();
    dispatcherGenerator.generate();
  }
}
