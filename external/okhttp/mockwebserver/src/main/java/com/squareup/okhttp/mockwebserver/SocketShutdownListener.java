/*
 * Copyright (C) 2023 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.okhttp.mockwebserver;

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * Utility class for registering as a listener to {@link MockResponse#setSocketShutdownListener}.
 *
 * An instance will wait for a single shutdown callback. Once triggered, the listener will stay in a
 * "shutdown occurred" state.
 *
 * @see SocketPolicy
 */
public class SocketShutdownListener implements Consumer<SocketPolicy> {

  final CountDownLatch shutdownLatch = new CountDownLatch(1);

  @Override
  public void accept(SocketPolicy reason) {
    shutdownLatch.countDown();
  }

  public void waitForSocketShutdown() {
    try {
      shutdownLatch.await();
    } catch (InterruptedException e) {
    }
  }
}
