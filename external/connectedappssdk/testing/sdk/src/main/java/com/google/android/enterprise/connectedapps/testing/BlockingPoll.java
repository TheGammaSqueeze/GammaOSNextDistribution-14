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
package com.google.android.enterprise.connectedapps.testing;

/** Utility for blocking a thread while polling for a state. */
public class BlockingPoll {

  /** Interface implemented when using {@link #poll(BooleanSupplier, long, long)}. */
  public interface BooleanSupplier {
    boolean getAsBoolean();
  }

  /**
   * Poll for a state at a given frequency while blocking the thread.
   *
   * @param func returns true when the polling condition is met. False otherwise
   * @param pollFrequency The number of milliseconds to wait between calling {@code func}
   * @param timeoutMillis The maximum number of milliseconds before an {@link
   *     IllegalStateException} is thrown.
   */
  public static void poll(BooleanSupplier func, long pollFrequency, long timeoutMillis) {
    long endTime = System.currentTimeMillis() + timeoutMillis;
    while (!func.getAsBoolean() && System.currentTimeMillis() < endTime) {
      try {
        Thread.sleep(pollFrequency);
      } catch (InterruptedException e) {
        throw new IllegalStateException("Sleep interrupted", e);
      }
    }

    if (!func.getAsBoolean()) {
      throw new IllegalStateException("Timeout after " + timeoutMillis);
    }
  }

  private BlockingPoll() {}
}
