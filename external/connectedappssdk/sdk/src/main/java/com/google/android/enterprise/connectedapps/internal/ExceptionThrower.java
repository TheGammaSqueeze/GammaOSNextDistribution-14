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

import android.os.Handler;
import android.os.Looper;

/** Utility class for throwing an exception on the main thread after a delay. */
public final class ExceptionThrower {

  private ExceptionThrower() {}

  private static class ThrowingRunnable implements Runnable {
    RuntimeException runtimeException;
    Error error;

    ThrowingRunnable(RuntimeException runtimeException) {
      this.runtimeException = runtimeException;
    }

    ThrowingRunnable(Error error) {
      this.error = error;
    }


    @Override
    public void run() {
      throw runtimeException;
    }
  }

  /** Throw the given {@link RuntimeException} after a delay on the main looper. */
  public static void delayThrow(RuntimeException runtimeException) {
    // We add a small delay to ensure that the return can be completed before crashing
    new Handler(Looper.getMainLooper()).postDelayed(new ThrowingRunnable(runtimeException), 1000);
  }

  /** Throw the given {@link Error} after a delay on the main looper. */
  public static void delayThrow(Error error) {
    // We add a small delay to ensure that the return can be completed before crashing
    new Handler(Looper.getMainLooper()).postDelayed(new ThrowingRunnable(error), 1000);
  }
}
