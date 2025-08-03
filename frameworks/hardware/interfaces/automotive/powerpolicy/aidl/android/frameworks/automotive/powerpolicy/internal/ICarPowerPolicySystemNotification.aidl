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

package android.frameworks.automotive.powerpolicy.internal;

import android.frameworks.automotive.powerpolicy.internal.PolicyState;

/**
 * ICarPowerPolicyServer is an interface implemented by the power policy daemon.
 * VHAL changes the power policy and the power policy daemon notifies the change to registered
 * subscribers. When subscribing to policy changes, a filter can be specified so that the registered
 * callbacks can listen only to a specific power component's change.
 *
 * @deprecated {@link android.automotive.powerpolicy.internal.ICarPowerPolicyDelegate} replaces
 * this interface.
 *
 * @hide
 */
@VintfStability
interface ICarPowerPolicySystemNotification {
  /**
   * CarService uses this method to tell that CarService is ready for taking over power policy
   * management.
   * Once this method is called, car power policy daemon stops serving for the requests from vehicle
   * HAL. CarService starts serving power policy management based on the current power policy ID and
   * power policy group ID returned from the call.
   *
   * System private API for CarService.
   *
   * @return The current power policy ID and power policy group ID.
   * @throws SecurityException if the caller doesn't have sufficient permissions.
   */
  PolicyState notifyCarServiceReady();

  /**
   * CarService uses this method to tell that the current power policy is changed.
   * Then, car power policy daemon propagates the change to registered callbacks.
   *
   * System private API for CarService.
   *
   * @param policyId The current policy ID.
   * @param force If {@code true}, the given policy is applied even when the current policy
   *        is a system power policy.
   * @throws IllegalStateException if it fails to notify power policy change.
   * @throws SecurityException if the caller doesn't have sufficient permissions.
   */
  void notifyPowerPolicyChange(in @utf8InCpp String policyId, boolean force);

  /**
   * CarService uses this method to tell that there is a newly defined power policy.
   * When a new power policy is defined on the fly through "define-power-policy" in
   * {@code CarShellCommand}, CarService makes sure that car power policy daemon maintains the same
   * power policies.
   *
   * System private API for CarService.
   *
   * @param policyId The new policy ID.
   * @param enabledComponents List of components to be enabled.
   * @param disabledComponents List of components to be disabled.
   * @throws IllegalStateException if it fails to notify power policy definition.
   * @throws SecurityException if the caller doesn't have sufficient permissions.
   */
  void notifyPowerPolicyDefinition(in @utf8InCpp String policyId,
      in @utf8InCpp String[] enabledComponents, in @utf8InCpp String[] disabledComponents);
}
