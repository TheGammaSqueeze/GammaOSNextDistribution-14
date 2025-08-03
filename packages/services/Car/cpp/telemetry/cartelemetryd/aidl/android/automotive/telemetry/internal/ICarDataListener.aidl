/*
 * Copyright (c) 2021, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.automotive.telemetry.internal;

import android.automotive.telemetry.internal.CarDataInternal;

/**
 * Listener for {@code ICarTelemetryInternal#registerListener}.
 */
interface ICarDataListener {
  /**
   * Called by native cartelemetryd when the data are available to be consumed. The service removes
   * the delivered data from its buffer when the callback succeeds. If the callback fails, it
   * immediately gives up and drops the data too and sends the next chunk of the available data.
   *
   * <p>If the collected data is too large, it will send only chunk of the data, and the callback
   * will be fired again.
   *
   * @param dataList the pushed data.
   * @throws IllegalStateException if data cannot be received.
   */
  void onCarDataReceived(in CarDataInternal[] dataList);
}
