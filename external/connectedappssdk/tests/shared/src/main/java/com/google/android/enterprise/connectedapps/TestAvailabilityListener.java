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

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TestAvailabilityListener implements AvailabilityListener {

  private static final long DEFAULT_TIMEOUT = 30;
  private static final TimeUnit DEFAULT_UNIT = SECONDS;

  private int availabilityChangedCount = 0;
  private CountDownLatch latch = new CountDownLatch(1);

  public int availabilityChangedCount() {
    return availabilityChangedCount;
  }

  public void reset() {
    availabilityChangedCount = 0;
    latch.countDown();
    latch = new CountDownLatch(1);
  }

  public int awaitAvailabilityChange() throws InterruptedException {
    return awaitAvailabilityChange(DEFAULT_TIMEOUT, DEFAULT_UNIT);
  }

  public int awaitAvailabilityChange(long timeout, TimeUnit unit) throws InterruptedException {
    latch.await(timeout, unit);
    return availabilityChangedCount();
  }

  @Override
  public void availabilityChanged() {
    availabilityChangedCount++;
    latch.countDown();
  }
}
