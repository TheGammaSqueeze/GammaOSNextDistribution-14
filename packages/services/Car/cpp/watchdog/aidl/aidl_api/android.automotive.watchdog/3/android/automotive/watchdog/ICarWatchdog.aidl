/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.automotive.watchdog;
@VintfStability
interface ICarWatchdog {
  void registerClient(in android.automotive.watchdog.ICarWatchdogClient client, in android.automotive.watchdog.TimeoutLength timeout);
  void unregisterClient(in android.automotive.watchdog.ICarWatchdogClient client);
  /**
   * @deprecated Calling this API will result in unsupported operation binder error.
   */
  void registerMediator(in android.automotive.watchdog.ICarWatchdogClient mediator);
  /**
   * @deprecated Calling this API will result in unsupported operation binder error.
   */
  void unregisterMediator(in android.automotive.watchdog.ICarWatchdogClient mediator);
  /**
   * @deprecated Calling this API will result in unsupported operation binder error.
   */
  void registerMonitor(in android.automotive.watchdog.ICarWatchdogMonitor monitor);
  /**
   * @deprecated Calling this API will result in unsupported operation binder error.
   */
  void unregisterMonitor(in android.automotive.watchdog.ICarWatchdogMonitor monitor);
  void tellClientAlive(in android.automotive.watchdog.ICarWatchdogClient client, in int sessionId);
  /**
   * @deprecated Calling this API will result in unsupported operation binder error.
   */
  void tellMediatorAlive(in android.automotive.watchdog.ICarWatchdogClient mediator, in int[] clientsNotResponding, in int sessionId);
  /**
   * @deprecated Calling this API will result in unsupported operation binder error.
   */
  void tellDumpFinished(in android.automotive.watchdog.ICarWatchdogMonitor monitor, in int pid);
  /**
   * @deprecated Calling this API will result in unsupported operation binder error.
   */
  void notifySystemStateChange(in android.automotive.watchdog.StateType type, in int arg1, in int arg2);
  void addResourceOveruseListener(in android.automotive.watchdog.ResourceType[] resourceTypes, in android.automotive.watchdog.IResourceOveruseListener listener);
  void removeResourceOveruseListener(in android.automotive.watchdog.IResourceOveruseListener listener);
  List<android.automotive.watchdog.ResourceOveruseStats> getResourceOveruseStats(in android.automotive.watchdog.ResourceType[] resourceTypes);
}
