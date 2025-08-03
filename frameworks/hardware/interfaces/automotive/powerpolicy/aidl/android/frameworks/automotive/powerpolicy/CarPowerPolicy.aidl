/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.frameworks.automotive.powerpolicy;

import android.frameworks.automotive.powerpolicy.PowerComponent;

/**
 * Description of car power policy.
 * Components which are not specified in the CarPowerPolicy will remain unchanged.
 */
@VintfStability
parcelable CarPowerPolicy {
  /**
   * Unique ID representing power policy.
   */
  @utf8InCpp String policyId;

  /**
   * List of power components to be enabled.
   */
  PowerComponent[] enabledComponents;

  /**
   * List of power components to be disabled.
   */
  PowerComponent[] disabledComponents;

  /**
   * List of custom power components to be enabled.
   */
  int[] enabledCustomComponents = {};

  /**
   * List of custom power components to be disabled.
   */
  int[] disabledCustomComponents = {};
}
