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

import android.automotive.powerpolicy.internal.PowerPolicyInitData;
import android.automotive.powerpolicy.internal.ICarPowerPolicyDelegateCallback;

/**
 * ICarPowerPolicyDelegate is an interface implemented by the power policy daemon.
 *
 * <p>CarService delegates the request of applying power policy to the power policy daemon using
 * {@code ICarPowerPolicyDelegate}.
 *
 * System private API for CarService.
 *
 * @hide
 */
interface ICarPowerPolicyDelegate {
  /**
   * CarService uses this method to tell power policy daemon that CarService is ready.
   *
   * <p>Once this method is called, the car power policy daemon asks CarService to update power
   * components state.
   *
   * <p>If this method is called multiple times, the second or later calls are ignored.
   *
   * @param callback The callback that car power policy daemon use to communicate with CarService.
   * @return The available custom power components, current power policy, and registered power
   * policies in {@PowerPolicyInitData}.
   * @throws SecurityException if the caller doesn't have sufficient permissions.
   */
  PowerPolicyInitData notifyCarServiceReady(in ICarPowerPolicyDelegateCallback callback);

  /**
   * CarService uses this method to request power policy application.
   *
   * <p>This method should return immediately after queueing the request. When the car power policy
   * daemon finishes applying the power policy, it invokes
   * {@code ICarPowerPolicyDelegateCallback.onApplyPowerPolicySucceeded}.
   *
   * @param The request ID for power policy application. Must be unique.
   * @param policyId The policy ID to apply.
   * @param force If {@code true}, the given policy is applied even when the current policy is a
   *        system power policy.
   * @throws IllegalArgumentException if {@code policyId} is invalid.
   * @throws IllegalStateException if it fails to apply the power policy.
   * @throws SecurityException if the caller doesn't have sufficient permissions.
   */
  void applyPowerPolicyAsync(int requestId, in @utf8InCpp String policyId, boolean force);

  /**
   * CarService uses this method to set a power policy group.
   *
   * @param policyGroupId The new policy group ID.
   * @throws IllegalArgumentException if {@code policyGroupId} is invalid.
   * @throws IllegalStateException if it fails to notify power policy definition.
   * @throws SecurityException if the caller doesn't have sufficient permissions.
   */
  void setPowerPolicyGroup(in @utf8InCpp String policyGroupId);

  /**
   * CarService uses this method to tell that there is a newly defined power policy.
   *
   * <p>When a new power policy is defined on the fly through "define-power-policy" in
   * {@code CarShellCommand}, CarService makes sure that the car power policy daemon maintains the
   * same power policies.
   *
   * @param policyId The new policy ID.
   * @param enabledComponents List of components to be enabled.
   * @param disabledComponents List of components to be disabled.
   * @throws IllegalArgumentException if the given policy ID or components are invalid.
   * @throws IllegalStateException if it fails to notify power policy definition.
   * @throws SecurityException if the caller doesn't have sufficient permissions.
   */
  void notifyPowerPolicyDefinition(in @utf8InCpp String policyId,
    in @utf8InCpp String[] enabledComponents, in @utf8InCpp String[] disabledComponents);

  /**
   * CarService uses this method to tell that there is a newly defined power policy group.
   *
   * <p>When a new power policy group is defined on the fly through "define-power-policy-group" in
   * {@code CarShellCommand}, CarService makes sure that the car power policy daemon maintains the
   * same power policy groups.
   *
   * @param policyGroupId The new policy group ID.
   * @param policyPerState String Array of size 2. Index 0 for WaitForVHAL and index 1 for On. Empty
   *        string means no power policy for the power state.
   * @throws IllegalArgumentException if the given policy group ID or mapping is invalid.
   * @throws SecurityException if the caller doesn't have sufficient permissions.
   */
  void notifyPowerPolicyGroupDefinition(in @utf8InCpp String policyGroupId,
    in String[] powerPolicyPerState);

  /**
   * Enumeration of power states, matching those defined in CarPowerManager.
   */
  @Backing(type="int")
  enum PowerState {
    INVALID = 0,
    WAIT_FOR_VHAL = 1,
    SUSPEND_ENTER = 2,
    SUSPEND_EXIT = 3,
    SHUTDOWN_ENTER = 5,
    ON = 6,
    SHUTDOWN_PREPARE = 7,
    SHUTDOWN_CANCELLED = 8,
    HIBERNATION_ENTER = 9,
    HIBERNATION_EXIT = 10,
    PRE_SHUTDOWN_PREPARE = 11,
    POST_SUSPEND_ENTER = 12,
    POST_SHUTDOWN_ENTER = 13,
    POST_HIBERNATION_ENTER = 14,
  }

  /**
   * CarService uses this method to request power policy application according to the power state
   * change.
   *
   * <p>This method should return immediately after queueing the request. When the car power policy
   * daemon finishes applying the power policy for the new power state, it invokes
   * {@code ICarPowerPolicyDelegateCallback.onApplyPowerPolicySucceeded}.
   *
   * @param requestId The request ID for power policy application. Must be unique.
   * @param state The power state.
   * @throws IllegalArgumentException if {@code state} is not supported.
   * @throws SecurityException if the caller doesn't have sufficient permissions.
   */
  void applyPowerPolicyPerPowerStateChangeAsync(int requestId, in PowerState state);
}
