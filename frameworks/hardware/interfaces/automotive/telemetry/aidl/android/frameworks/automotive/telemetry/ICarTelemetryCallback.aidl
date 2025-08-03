/*
 * Copyright (c) 2022, The Android Open Source Project
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

package android.frameworks.automotive.telemetry;

/**
 * Callback for cartelemetryd, used by vendor data-collecting components.
 *
 * <p>When this interface is extended in the future, the service must check the interface
 * version to ensure compatibility. See
 * https://source.android.com/docs/core/architecture/aidl/stable-aidl#querying-the-interface-version-of-the-remote-object
 */
@VintfStability
interface ICarTelemetryCallback {

  /**
   * Notifies client of currently active CarData IDs that the callback is associated with.
   *
   * This is the new set of IDs that we want them to publish.
   */
  void onChange(in int[] ids);
}
