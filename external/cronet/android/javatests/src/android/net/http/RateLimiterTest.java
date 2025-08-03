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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class RateLimiterTest {

  @Test
  public void testImmediateRateLimit() {
    RateLimiter rateLimiter = new RateLimiter(1);
    assertTrue("First request was rate limited", rateLimiter.tryAcquire());
    assertFalse("Second request was not rate limited", rateLimiter.tryAcquire());
  }

  @Test
  public void testInvalidSamplePerSecond() {
    int samplesPerSecond = -1;
    assertThrows(
        "samples per second was negative",
        IllegalArgumentException.class,
        () -> new RateLimiter(samplesPerSecond));
  }
}

