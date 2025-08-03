/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.automotive.watchdog;

import android.automotive.watchdog.PerStateBytes;

/**
 * Structure that describes the I/O usage stats for a package.
 */
@VintfStability
parcelable IoOveruseStats {
  /**
   * Package may be killed when the {@code remainingWriteBytes} are exhausted.
   */
  boolean killableOnOveruse;

  /**
   * Number of write bytes remaining before overusing disk I/O.
   */
  PerStateBytes remainingWriteBytes;

  /**
   * Start time, in epoch seconds, for the below stats.
   */
  long startTime;

  /**
   * Duration, in seconds, for the below stats.
   */
  long durationInSeconds;

  /**
   * Total number of bytes written to disk during the period.
   */
  PerStateBytes writtenBytes;

  /**
   * Total number of overuses during the period.
   */
  int totalOveruses;
}
