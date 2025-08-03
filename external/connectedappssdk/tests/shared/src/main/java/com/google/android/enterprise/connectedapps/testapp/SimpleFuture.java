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
package com.google.android.enterprise.connectedapps.testapp;

import java.util.concurrent.CountDownLatch;

/** A very simple implementation of the future pattern used to test custom future wrappers. */
public class SimpleFuture<E> {

  public static interface Consumer<E> {
    void accept(E value);
  }

  private E value;
  private Throwable thrown;
  private final CountDownLatch countDownLatch = new CountDownLatch(1);
  private Consumer<E> callback;
  private Consumer<Throwable> exceptionCallback;

  public void set(E value) {
    this.value = value;
    countDownLatch.countDown();
    if (callback != null) {
      callback.accept(value);
    }
  }

  public void setException(Throwable t) {
    this.thrown = t;
    countDownLatch.countDown();
    if (exceptionCallback != null) {
      exceptionCallback.accept(thrown);
    }
  }

  public E get() {
    try {
      countDownLatch.await();
    } catch (InterruptedException e) {
      return null;
    }
    if (thrown != null) {
      throw new RuntimeException(thrown);
    }
    return value;
  }

  public void setCallback(Consumer<E> callback, Consumer<Throwable> exceptionCallback) {
    if (value != null) {
      callback.accept(value);
    } else if (thrown != null) {
      exceptionCallback.accept(thrown);
    } else {
      this.callback = callback;
      this.exceptionCallback = exceptionCallback;
    }
  }
}
