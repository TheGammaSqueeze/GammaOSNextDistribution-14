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

package android.frameworks.automotive.telemetry;

/**
 * Configurations associated with a ICarTelemetryCallback.
 *
 * <p>Contents of this object should not be parsed by Android framework.
 *
 * <p>Please see {@link ICarTelemetry#addCallback(CallbackConfig, ICarTelemetryCallback)}.
 */
@VintfStability
parcelable CallbackConfig {
  /**
   * A list of CarData IDs that the callback is interested in.
   *
   * <p>The callback will only be invoked for the IDs declared in the CallbackConfig.
   */
  int[] carDataIds;
}
