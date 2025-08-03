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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import android.os.UserHandle;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Test utilities shared between Robolectric and Instrumented tests. */
public final class SharedTestUtilities {

  public static final String INTERACT_ACROSS_USERS = "android.permission.INTERACT_ACROSS_USERS";
  public static final String INTERACT_ACROSS_USERS_FULL =
      "android.permission.INTERACT_ACROSS_USERS_FULL";

  private static final String OF_METHOD_NAME = "of";

  /** Get the {@link UserHandle} for the given user ID. */
  public static UserHandle getUserHandleForUserId(int userId) {
    try {
      return (UserHandle)
          UserHandle.class.getMethod(OF_METHOD_NAME, int.class).invoke(null, userId);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Error getting current user handle", e);
    }
  }

  public static @Nullable Throwable assertFutureHasException(
      ListenableFuture<?> future, Class<? extends Throwable> throwable) {
    AtomicReference<Throwable> thrown = new AtomicReference<>();
    try {
      FluentFuture.from(future)
          .catching(
              throwable,
              t -> {
                // Expected
                thrown.set(t);
                return null;
              },
              directExecutor())
          .get();
    } catch (InterruptedException | ExecutionException e) {
      throw new AssertionError("Unhandled exception", e);
    }

    assertThat(thrown.get()).isNotNull();
    return thrown.get();
  }

  public static void assertFutureDoesNotHaveException(
      ListenableFuture<?> future, Class<? extends Throwable> throwable) {
    AtomicBoolean didThrow = new AtomicBoolean(false);
    try {
      FluentFuture.from(future)
          .catching(
              throwable,
              expected -> {
                didThrow.set(true);
                return null;
              },
              directExecutor())
          .withTimeout(1, TimeUnit.SECONDS, Executors.newSingleThreadScheduledExecutor())
          .get();
    } catch (InterruptedException e) {
      throw new AssertionError("Unhandled exception", e);
    } catch (ExecutionException e) {
      // This is called when the 1 second times out - which means nothing was thrown
    }

    assertThat(didThrow.get()).isFalse();
  }

  /**
   * Repeatedly call {@code a} and {@code b} on different threads to attempt to force a race
   * condition.
   */
  public static void tryForceRaceCondition(int iterations, Runnable a, Runnable b)
      throws Exception {
    ExecutorService executorA = Executors.newSingleThreadExecutor();
    ExecutorService executorB = Executors.newSingleThreadExecutor();

    for (int i = 0; i < iterations; i++) {
      ListenableFuture<?> aFuture = Futures.submit(a, executorA);
      ListenableFuture<?> bFuture = Futures.submit(b, executorB);

      aFuture.get();
      bFuture.get();
    }
  }

  private SharedTestUtilities() {}
}
