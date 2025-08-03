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

import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.android.enterprise.connectedapps.annotations.CrossProfile;
import com.google.android.enterprise.connectedapps.testapp.TestStringCallbackListener;
import com.google.common.util.concurrent.ListenableFuture;

public final class NonInstantiableTestCrossProfileType {
  private NonInstantiableTestCrossProfileType() {}

  @CrossProfile
  public static String staticIdentityStringMethod(String s) {
    return s;
  }

  @CrossProfile
  public static void staticAsyncIdentityStringMethod(
      String s, TestStringCallbackListener callback) {
    callback.stringCallback(s);
  }

  @CrossProfile
  public static ListenableFuture<String> staticFutureIdentityStringMethod(String s) {
    return immediateFuture(s);
  }
}
