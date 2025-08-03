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

package android.automotive.powerpolicy.internal;

import android.frameworks.automotive.powerpolicy.CarPowerPolicy;

/**
 * Structure to store the initial data returned from the car power policy daemon to CarService.
 *
 * @hide
 */
parcelable PowerPolicyInitData {
  /**
   * The IDs of custom components registered by OEMs.
   */
  int[] registeredCustomComponents;

  /**
   * The current power policy.
   */
  CarPowerPolicy currentPowerPolicy;

  /**
  * The power policies that have been registered by the power policy daemon.
  */
  CarPowerPolicy[] registeredPolicies;
}
