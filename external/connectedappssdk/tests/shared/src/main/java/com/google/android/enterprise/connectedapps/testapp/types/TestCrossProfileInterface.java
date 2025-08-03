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
package com.google.android.enterprise.connectedapps.testapp.types;

import com.google.android.enterprise.connectedapps.annotations.CrossProfile;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import java.util.List;

@CrossProfile(connector = TestProfileConnector.class)
public interface TestCrossProfileInterface {

  // This needs to be a type which has a parcelable wrapper to ensure that we generate duplicate
  //  parcelable wrappers in multiple targets and they are resolved correctly
  @CrossProfile
  List<String> identityListOfStringMethod(List<String> s);
}
