/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.car.storagemonitoring;

import android.car.storagemonitoring.IIoStatsListener;
import android.car.storagemonitoring.IoStatsEntry;
import android.car.storagemonitoring.IoStats;
import android.car.storagemonitoring.WearEstimate;
import android.car.storagemonitoring.WearEstimateChange;

/** @hide */
interface ICarStorageMonitoring {
  /**
   * Returns the value of the PRE_EOL register.
   */
  int getPreEolIndicatorStatus() = 1;

  /**
   * Returns the current wear estimate indicators.
   *
   * @deprecated wear estimate data is unreliable
   */
  WearEstimate getWearEstimate() = 2;

  /**
   * Returns the list of all observed wear estimate changes.
   *
   * @deprecated wear estimate data is unreliable
   */
  List<WearEstimateChange> getWearEstimateHistory() = 3;

  /**
   * Returns I/O stats as collected at service boot time.
   *
   * @deprecated use CarWatchdogManager#getResourceOveruseStats(int, int) instead.
   */
  List<IoStatsEntry> getBootIoStats() = 4;

  /**
   * Returns total I/O stats as collected from kernel start until the last snapshot.
   *
   * @deprecated use CarWatchdogManager#getResourceOveruseStats(int, int) instead.
   */
  List<IoStatsEntry> getAggregateIoStats() = 5;

  /**
   * Return the I/O stats deltas currently known to the service.
   *
   * @deprecated use CarWatchdogManager#getResourceOveruseStats(int, int) instead.
   */
  List<IoStats> getIoStatsDeltas() = 6;

  /**
   * Register a new listener to receive new I/O activity deltas as they are generated.
   *
   * @deprecated IIoStatsListener is deprecated
   */
  void registerListener(IIoStatsListener listener) = 7;

  /**
   * Remove a listener registration, terminating delivery of I/O activity deltas to it.
   *
   * @deprecated IIoStatsListener is deprecated
   */
  void unregisterListener(IIoStatsListener listener) = 8;

  /**
   * Returns the approximate amount of bytes written to disk during the previous shutdown.
   *
   * @deprecated use CarWatchdogManager#getResourceOveruseStats(int, int) instead.
   */
  long getShutdownDiskWriteAmount() = 9;
}
