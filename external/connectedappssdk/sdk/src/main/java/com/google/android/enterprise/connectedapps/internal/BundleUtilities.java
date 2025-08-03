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
package com.google.android.enterprise.connectedapps.internal;

import android.os.Bundle;

/** This class is only for internal use by the SDK. */
public final class BundleUtilities {
  private BundleUtilities() {}

  public static void writeThrowableToBundle(Bundle bundle, String key, Throwable throwable) {
    bundle.putSerializable(key, throwable);
  }

  public static Throwable readThrowableFromBundle(Bundle bundle, String key) {
    bundle.setClassLoader(Bundler.class.getClassLoader());
    return (Throwable) bundle.getSerializable(key);
  }
}
