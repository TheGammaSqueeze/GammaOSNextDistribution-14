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

import android.frameworks.automotive.powerpolicy.CarPowerPolicy;
import android.frameworks.automotive.powerpolicy.CarPowerPolicyFilter;
import android.frameworks.automotive.powerpolicy.ICarPowerPolicyChangeCallback;
import android.frameworks.automotive.powerpolicy.PowerComponent;

/**
 * ICarPowerPolicyServer is an interface implemented by the power policy daemon.
 * VHAL changes the power policy and the power policy daemon notifies the change to registered
 * subscribers. When subscribing to policy changes, a filter can be specified so that the registered
 * callbacks can listen only to a specific power component's change.
 */

@VintfStability
interface ICarPowerPolicyServer {
  /**
   * Gets the current power policy.
   * @throws IllegalStateException if the current policy is not set.
   */
  CarPowerPolicy getCurrentPowerPolicy();

  /**
   * Gets whether the power component is turned on or off.
   *
   * @param componentId Power component ID defined in PowerComponent.aidl to check power state.
   * @return True if the component's power state is on.
   * @throws IllegalArgumentException if the componentId is invalid.
   */
  boolean getPowerComponentState(in PowerComponent componentId);

  /**
   * Subscribes to power policy change.
   * Notification is sent to the registered callback when the power policy changes and the power
   * state of the components which the callback is interested in changes.
   *
   * @param callback Callback that is invoked when the power policy changes.
   * @param filter The list of components which the callback is interested in.
   * @throws IllegalArgumentException if the callback is already registered.
   * @throws IllegalStateException if the callback is dead.
   */
  void registerPowerPolicyChangeCallback(in ICarPowerPolicyChangeCallback callback,
      in CarPowerPolicyFilter filter);

  /**
   * Unsubscribes from power policy change.
   *
   * @param callback Callback that doesn't want to receive power policy change.
   * @throws IllegalArgumentException if the callback is not registered.
   */
  void unregisterPowerPolicyChangeCallback(in ICarPowerPolicyChangeCallback callback);

  /**
   * Applies the power policy.
   *
   * <p>{@code policyId} should be one of power policy IDs defined in
   * {@code /vendor/etc/automotive/power_policy.xml} or predefined system power policies.
   *
   * @param policyId ID of power policy.
   * @throws IllegalArgumentException if {@code policyId} is invalid.
   */
  void applyPowerPolicy(in @utf8InCpp String policyId);

  /**
   * Sets the current power policy group.
   *
   * <p>{@code policyGroupId} should be one of power policy group IDs defined in
   * {@code /vendor/etc/automotive/power_policy.xml}.
   *
   * @param policyGroupId ID of power policy group.
   * @throws IllegalArgumentException if {@code policyGroupId} is invalid.
   */
  void setPowerPolicyGroup(in @utf8InCpp String policyGroupId);
}
