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
///////////////////////////////////////////////////////////////////////////////
// THIS FILE IS IMMUTABLE. DO NOT EDIT IN ANY CASE.                          //
///////////////////////////////////////////////////////////////////////////////

// This file is a snapshot of an AIDL file. Do not edit it manually. There are
// two cases:
// 1). this is a frozen version file - do not edit this in any case.
// 2). this is a 'current' file. If you make a backwards compatible change to
//     the interface (from the latest frozen version), the build system will
//     prompt you to update this file with `m <name>-update-api`.
//
// You must not make a backward incompatible change to any AIDL file built
// with the aidl_interface module type with versions property set. The module
// type is used to build AIDL files in a way that they can be used across
// independently updatable components of the system. If a device is shipped
// with such a backward incompatible change, it has a high risk of breaking
// later when a module using the interface is updated, e.g., Mainline modules.

package android.automotive.powerpolicy.internal;
/* @hide */
interface ICarPowerPolicyDelegate {
  android.automotive.powerpolicy.internal.PowerPolicyInitData notifyCarServiceReady(in android.automotive.powerpolicy.internal.ICarPowerPolicyDelegateCallback callback);
  void applyPowerPolicyAsync(int requestId, in @utf8InCpp String policyId, boolean force);
  void setPowerPolicyGroup(in @utf8InCpp String policyGroupId);
  void notifyPowerPolicyDefinition(in @utf8InCpp String policyId, in @utf8InCpp String[] enabledComponents, in @utf8InCpp String[] disabledComponents);
  void notifyPowerPolicyGroupDefinition(in @utf8InCpp String policyGroupId, in String[] powerPolicyPerState);
  void applyPowerPolicyPerPowerStateChangeAsync(int requestId, in android.automotive.powerpolicy.internal.ICarPowerPolicyDelegate.PowerState state);
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
}
