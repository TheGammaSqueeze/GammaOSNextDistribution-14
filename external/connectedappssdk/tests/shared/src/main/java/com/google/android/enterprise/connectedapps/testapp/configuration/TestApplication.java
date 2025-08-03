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
package com.google.android.enterprise.connectedapps.testapp.configuration;

import android.app.Service;
import com.google.android.enterprise.connectedapps.annotations.CrossProfileConfiguration;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector_Service;
import com.google.android.enterprise.connectedapps.testapp.types.SeparateBuildTargetProvider;
import com.google.android.enterprise.connectedapps.testapp.TestInterfaceProvider;
import com.google.android.enterprise.connectedapps.testapp.types.TestProvider;

@CrossProfileConfiguration(providers = {
    TestProvider.class, SeparateBuildTargetProvider.class, TestInterfaceProvider.class})
public abstract class TestApplication {

  // This is available so the test targets can access the generated Service class.
  public static Class<? extends Service> getService() {
    return TestProfileConnector_Service.class;
  }

  private TestApplication() {}
}
