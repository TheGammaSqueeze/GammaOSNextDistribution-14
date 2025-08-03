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
package com.google.android.enterprise.connectedapps;

import android.os.Bundle;
import com.google.android.enterprise.connectedapps.internal.BundleUtilities;
import com.google.android.enterprise.connectedapps.internal.Bundler;
import com.google.android.enterprise.connectedapps.internal.BundlerType;

/** Wrapper for adding support for a future type to the Connected Apps SDK. */
public abstract class FutureWrapper<E> implements LocalCallback {
  private final Bundler bundler;
  private final BundlerType bundlerType;

  protected FutureWrapper(Bundler bundler, BundlerType bundlerType) {
    if (bundler == null || bundlerType == null) {
      throw new NullPointerException();
    }
    this.bundler = bundler;
    this.bundlerType = bundlerType;
  }

  @Override
  public void onResult(int methodIdentifier, Bundle params) {
    @SuppressWarnings("unchecked")
    E result = (E) bundler.readFromBundle(params, "result", bundlerType);

    onResult(result);
  }

  public abstract void onResult(E result);

  @Override
  public void onException(Bundle exception) {
    Throwable throwable = BundleUtilities.readThrowableFromBundle(exception, "throwable");
    onException(throwable);
  }

  public abstract void onException(Throwable throwable);

}
