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

import android.automotive.telemetry.internal.ICarDataListener;

/**
 * An internal API provided by cartelemetryd for receiving the collected data.
 */
interface ICarTelemetryInternal {
  /**
   * Sets a listener for CarData. If there are existing CarData in the buffer, the daemon will
   * start pushing them to the listener. There can be only a single registered listener at a time.
   *
   * @param listener the only listener.
   * @throws IllegalStateException if someone is already registered or the listener is dead.
   */
  void setListener(in ICarDataListener listener);

  /**
   * Clears the listener if exists. Silently ignores if there is no listener.
   */
  void clearListener();

  /**
   * Adds CarData IDs that a MetricsConfig subscribes to. Can be used for filtering purposes.
   */
  void addCarDataIds(in int[] ids);

  /**
   * Removes CarData IDs.
   */
  void removeCarDataIds(in int[] ids);
}
