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
package com.google.android.enterprise.connectedapps.testapp.wrappers;

import com.google.android.enterprise.connectedapps.FutureWrapper;
import com.google.android.enterprise.connectedapps.Profile;
import com.google.android.enterprise.connectedapps.internal.Bundler;
import com.google.android.enterprise.connectedapps.internal.BundlerType;
import com.google.android.enterprise.connectedapps.internal.CrossProfileCallbackMultiMerger;
import com.google.android.enterprise.connectedapps.internal.FutureResultWriter;
import com.google.android.enterprise.connectedapps.testapp.SimpleFuture;
import java.util.Map;

/** Wrapper for adding support for {@link SimpleFuture} to the Connected Apps SDK. */
@com.google.android.enterprise.connectedapps.annotations.CustomFutureWrapper(
    originalType = SimpleFuture.class)
public final class SimpleFutureWrapper<E> extends FutureWrapper<E> {

  private final SimpleFuture<E> future = new SimpleFuture<>();

  public static <E> SimpleFutureWrapper<E> create(Bundler bundler, BundlerType bundlerType) {
    return new SimpleFutureWrapper<>(bundler, bundlerType);
  }

  public static <E> SimpleFuture<E> immediateFailedFuture(Throwable t) {
    SimpleFuture<E> future = new SimpleFuture<>();
    future.setException(t);
    return future;
  }

  private SimpleFutureWrapper(Bundler bundler, BundlerType bundlerType) {
    super(bundler, bundlerType);
  }

  public SimpleFuture<E> getFuture() {
    return future;
  }

  @Override
  public void onResult(E result) {
    future.set(result);
  }

  @Override
  public void onException(Throwable throwable) {
    future.setException(throwable);
  }

  public static <E> void writeFutureResult(
      SimpleFuture<E> future, FutureResultWriter<E> resultWriter) {

    future.setCallback(resultWriter::onSuccess, resultWriter::onFailure);
  }

  public static <E> SimpleFuture<Map<Profile, E>> groupResults(
      Map<Profile, SimpleFuture<E>> results) {
    SimpleFuture<Map<Profile, E>> m = new SimpleFuture<>();

    CrossProfileCallbackMultiMerger<E> merger =
        new CrossProfileCallbackMultiMerger<>(results.size(), m::set);
    for (Map.Entry<Profile, SimpleFuture<E>> result : results.entrySet()) {
      result
          .getValue()
          .setCallback(
              (value) -> merger.onResult(result.getKey(), value),
              (throwable) -> merger.missingResult(result.getKey()));
    }
    return m;
  }
}
