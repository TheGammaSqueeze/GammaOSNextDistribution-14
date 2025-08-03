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
package com.google.android.enterprise.connectedapps.instrumented.utils;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Base class for callback listeners which can block until a result is received.
 *
 * <p>To use, extend this class passing {@code E} as the type of value being received, and call
 * {@link #receive(Object)} when the callback completes.
 */
public abstract class BlockingCallbackListener<E> {
  private BlockingQueue<E> results = new LinkedBlockingQueue<>();

  public E await(long timeout, TimeUnit unit) throws InterruptedException {
    return results.poll(timeout, unit);
  }

  public E await() throws InterruptedException {
    return await(10, TimeUnit.MINUTES);
  }

  protected void receive(E value) {
    results.offer(value);
  }
}
