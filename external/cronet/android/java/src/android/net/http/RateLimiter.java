/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.net.http;

import android.os.SystemClock;

/**
 * This allows us to do ratelimiting based on the time difference between the last log action and
 * the current log action. This class allows us to specify the number of samples/second we want for
 * each request.
 */
public final class RateLimiter {

  private static final long ONE_SECOND_MILLIS = 1000L;

  // The last tracked time
  private final int samplesPerSeconds;
  private int samplesLoggedDuringSecond = 0;
  private long lastPermitMillis = Long.MIN_VALUE;

  public RateLimiter(int samplesPerSeconds) {
    if (samplesPerSeconds <= 0) {
      throw new IllegalArgumentException("Expect sample rate to be > 0 sample(s) per second");
    }

    this.samplesPerSeconds = samplesPerSeconds;
  }

  // Check if rate limiting should happen based on a time passed or sample rate.
  public synchronized boolean tryAcquire() {
    long currentMillis = SystemClock.elapsedRealtime();

    if (lastPermitMillis + ONE_SECOND_MILLIS <= currentMillis) {
      // reset samplesLoggedDuringSecond and stopwatch once a second has passed
      samplesLoggedDuringSecond = 1;
      lastPermitMillis = currentMillis;
      return true;
    } else if (samplesLoggedDuringSecond < samplesPerSeconds) {
      samplesLoggedDuringSecond++;
      return true;
    }
    return false;
  }
}
