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

import android.automotive.powerpolicy.internal.PowerPolicyFailureReason;
import android.frameworks.automotive.powerpolicy.CarPowerPolicy;

/**
 * ICarPowerPolicyDelegateCallback is an interface implemented by CarService.
 *
 * <p>CarService registers {@code ICarPowerPolicyDelegateCallback} to the car power policy daemon to
 * receive the result of applying a power policy.
 *
 * System private API for CarService.
 *
 * @hide
 */
interface ICarPowerPolicyDelegateCallback {
  /**
   * The car power policy daemon calls this method to ask CarService to update power components'
   * state.
   *
   * <p>CarService decides which power components to update.
   *
   * @param powerPolicy The new power policy.
   */
  void updatePowerComponents(in CarPowerPolicy powerPolicy);

  /**
   * This is called when car power policy daemon completes the power policy application.
   *
   * @param requestId ID returned by {@code applyPowerPolicyAsync}.
   * @param accumulatedPolicy the current accumulated power policy after the request was applied.
   */
  oneway void onApplyPowerPolicySucceeded(int requestId, in CarPowerPolicy accumulatedPolicy);

  /**
   * This is called when car power policy daemon fails to apply the power policy.
   *
   * @param requestId ID returned by {@code applyPowerPolicyAsync}.
   * @param reason Code to tell why the power policy application failed.
   */
  oneway void onApplyPowerPolicyFailed(int requestId, in PowerPolicyFailureReason reason);

  /**
   * The car power policy daemon calls this when a power policy change has been applied by a client
   * other than CarService.
   *
   * <p>CarService can then notify its listeners of the power policy change.
   *
   * @param accumulatedPolicy the current accumulated power policy
   */
  oneway void onPowerPolicyChanged(in CarPowerPolicy accumulatedPolicy);
}
