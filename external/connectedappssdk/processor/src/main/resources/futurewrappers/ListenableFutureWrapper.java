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
package com.google.android.enterprise.connectedapps.futurewrappers;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.android.enterprise.connectedapps.FutureWrapper;
import com.google.android.enterprise.connectedapps.Profile;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import com.google.android.enterprise.connectedapps.internal.Bundler;
import com.google.android.enterprise.connectedapps.internal.BundlerType;
import com.google.android.enterprise.connectedapps.internal.CrossProfileCallbackMultiMerger;
import com.google.android.enterprise.connectedapps.internal.FutureResultWriter;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.Map;

/** Wrapper for adding support for {@link ListenableFuture} to the Connected Apps SDK. */
public final class ListenableFutureWrapper<E> extends FutureWrapper<E> {

  private final SettableFuture<E> future = SettableFuture.create();

  public static <E> ListenableFutureWrapper<E> create(Bundler bundler, BundlerType bundlerType) {
    return new ListenableFutureWrapper<>(bundler, bundlerType);
  }

  private ListenableFutureWrapper(Bundler bundler, BundlerType bundlerType) {
    super(bundler, bundlerType);
  }

  public ListenableFuture<E> getFuture() {
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
      ListenableFuture<E> future, FutureResultWriter<E> resultWriter) {
    FluentFuture.from(future)
        .addCallback(
            new FutureCallback<E>() {
              @Override
              public void onSuccess(E result) {
                resultWriter.onSuccess(result);
              }

              @Override
              public void onFailure(Throwable t) {
                resultWriter.onFailure(t);
              }
            },
            directExecutor());
  }

  private static final class MergerFutureCallback<E> implements FutureCallback<E> {

    private final Profile profileId;
    private final CrossProfileCallbackMultiMerger<E> merger;

    MergerFutureCallback(Profile profileId, CrossProfileCallbackMultiMerger<E> merger) {
      if (profileId == null || merger == null) {
        throw new NullPointerException();
      }
      this.profileId = profileId;
      this.merger = merger;
    }

    @Override
    public void onSuccess(E result) {
      merger.onResult(profileId, result);
    }

    @Override
    public void onFailure(Throwable t) {
      // TODO: What should we do with the Throwable?
      merger.missingResult(profileId);
    }
  }

  public static <E> ListenableFuture<Map<Profile, E>> groupResults(
      Map<Profile, ListenableFuture<E>> results) {
    SettableFuture<Map<Profile, E>> m = SettableFuture.create();
    CrossProfileCallbackMultiMerger<E> merger =
        new CrossProfileCallbackMultiMerger<>(results.size(), m::set);
    for (Map.Entry<Profile, ListenableFuture<E>> result : results.entrySet()) {
      FluentFuture.from(result.getValue())
          .catching(
              UnavailableProfileException.class,
              (throwable) -> {
                merger.missingResult(result.getKey());
                return null; // This will be passed into the callback but will be rejected by merger
                // as duplicate
              },
              directExecutor())
          .addCallback(new MergerFutureCallback<>(result.getKey(), merger), directExecutor());
    }
    return m;
  }
}
