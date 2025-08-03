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

package android.automotive.watchdog.internal;

import android.automotive.watchdog.internal.ComponentType;
import android.automotive.watchdog.internal.ICarWatchdogMonitor;
import android.automotive.watchdog.internal.ICarWatchdogServiceForSystem;
import android.automotive.watchdog.internal.ProcessIdentifier;
import android.automotive.watchdog.internal.ResourceOveruseConfiguration;
import android.automotive.watchdog.internal.StateType;
import android.automotive.watchdog.internal.ThreadPolicyWithPriority;
import android.automotive.watchdog.internal.UserPackageIoUsageStats;

/**
 * ICarWatchdog is an interface implemented by the watchdog server. This interface is used only by
 * the internal services to communicate with the watchdog server.
 * Watchdog service is the counter part of the watchdog server to help communicate with
 * the car service and Java side services.
 * For health check, 3 components are involved: watchdog server, watchdog service, watchdog monitor.
 *   - watchdog server:   1. Checks clients' health status by pinging and waiting for the response.
 *                        2. Monitors disk I/O usage by system, OEM and third-party apps and
 *                        services.
 *   - watchdog service:  is a watchdog client by reporting its health status to the server, and
 *                        at the same time plays a role of watchdog server by checking its clients'
 *                        health status and performs resource overuse monitoring and notifying
 *                        the user and the apps.
 *   - watchdog monitor:  captures and reports the process state of watchdog clients.
 */
interface ICarWatchdog {
  /**
   * Register the CarWatchdogService to the watchdog server.
   * The caller should have system UID. Otherwise, returns security exception binder error.
   *
   * @param service             CarWatchdogService to register.
   */
  void registerCarWatchdogService(in ICarWatchdogServiceForSystem service);

  /**
   * Unregister the CarWatchdogService from the watchdog server.
   * The caller should have system UID. Otherwise, returns security exception binder error.
   *
   * @param service             CarWatchdogService to unregister.
   */
  void unregisterCarWatchdogService(in ICarWatchdogServiceForSystem service);

  /**
   * Register the monitor to the watchdog server.
   * The caller should have system UID. Otherwise, returns security exception binder error.
   *
   * @param monitor             Watchdog monitor to register.
   */
  void registerMonitor(in ICarWatchdogMonitor monitor);

  /**
   * Unregister the monitor from the watchdog server.
   * The caller should have system UID. Otherwise, returns security exception binder error.
   *
   * @param monitor             Watchdog monitor to unregister.
   */
  void unregisterMonitor(in ICarWatchdogMonitor monitor);

  /**
   * Tell watchdog server that the CarWatchdogService is alive together with the status of clients
   * under the CarWatchdogService.
   * The caller should have system UID. Otherwise, returns security exception binder error.
   *
   * @param service              Watchdog service that is responding.
   * @param clientsNotResponding List of process identifiers of clients which haven't responded to
   *                             the mediator.
   * @param sessionId            Session id given by watchdog server.
   */
  void tellCarWatchdogServiceAlive(in ICarWatchdogServiceForSystem service,
          in List<ProcessIdentifier> processIdentifiers, int sessionId);

  /**
   * Tell watchdog server that the monitor has finished dumping process information.
   * The caller should have system UID. Otherwise, returns security exception binder error.
   *
   * @param monitor              Watchdog monitor that is registered to watchdog server.
   * @param pid                  Process identifier of the process that has been dumped.
   */
  void tellDumpFinished(in ICarWatchdogMonitor monitor, in ProcessIdentifier processIdentifier);

  /**
   * Notify watchdog server about the system state change.
   * The caller should have system UID. Otherwise, returns security exception binder error.
   *
   * @param type                 One of the change types defined in the StateType enum.
   * @param arg1                 First state change information for the specified type.
   * @param arg2                 Second state change information for the specified type.
   *
   * When type is POWER_CYCLE, arg1 should contain the current power cycle of the device.
   * When type is USER_STATE, arg1 and arg2 should contain the user ID and the current user state.
   * When type is BOOT_PHASE, arg1 should contain the current boot phase.
   */
  void notifySystemStateChange(in StateType type, int arg1, int arg2);

  /**
   * Update the given resource overuse configurations.
   * The caller should have system UID. Otherwise, returns security exception binder error.
   *
   * @param configs              List of resource overuse configurations.
   */
  void updateResourceOveruseConfigurations(in List<ResourceOveruseConfiguration> configs);

  /**
   * Return the latest list of resource overuse configuration per component.
   * The caller should have system UID. Otherwise, returns security exception binder error.
   *
   * @return configs             List of resource overuse configurations.
   */
  List<ResourceOveruseConfiguration> getResourceOveruseConfigurations();

  /**
   * Enable/disable the client health checking.
   * Disabling the client health checking would stop killing clients on ANR.
   * The caller should have system UID. Otherwise, returns security exception binder error.
   *
   * @param enable            When set to true, client health checking is enabled.
   *                          Otherwise, it is disabled.
   */
  void controlProcessHealthCheck(boolean enable);

  /**
   * Set thread scheduling policy and priority.
   *
   * <p> This function would check whether the {@code tid} belongs to {@code pid} and {@code uid}.
   * If so, it sets the scheduling policy and priority. Otherwise, it returns errors.
   *
   * <p>This function may return one of the following error codes:
   * <ul>
   * <li> {@code EX_ILLEGAL_STATE} If the given {@code tid} does not belong to {@code pid} and
   * {@code uid}.
   * <li> {@code EX_SERVICE_SPECIFIC} if failed to set thread scheduling policy and priority.
   * <li> {@code EX_INVALID_ARGUMENT} If the provided policy or priority is not valid.
   *
   * @param pid The process id.
   * @param tid The thread id.
   * @param uid The package uid (aka linux real user ID).
   * @param policy The scheduling policy.
   * @param priority The scheduling priority.
   */
  void setThreadPriority(int pid, int tid, int uid, int policy, int priority);

  /**
   * Get thread scheduling policy and priority.
   *
   * <p> This function would check whether the {@code tid} belongs to {@code pid} and {@code uid}.
   * If so, it gets the scheduling policy and priority. Otherwise, it returns error.
   *
   * <p>This function may return one of the following error codes:
   * <ul>
   * <li> {@code EX_ILLEGAL_STATE} If the given {@code tid} does not belong to {@code pid} and
   * {@code uid}.
   * <li> {@code EX_SERVICE_SPECIFIC} if failed to get thread scheduling policy and priority.
   *
   * @param pid The process id.
   * @param tid The thread id.
   * @param uid The package uid (aka linux real user ID).
   * @return The policy with priority.
   */
   ThreadPolicyWithPriority getThreadPriority(int pid, int tid, int uid);

  /**
   * Updates the daemon with the AIDL VHAL {@code pid}.
   *
   * This call is a response to the {@link ICarWatchdogServiceForSystem.requestAidlVhalPid}
   * call.
   *
   * @param pid The AIDL VHAL process ID. On error, the pid will be {@code -1}.
   */
   void onAidlVhalPidFetched(int pid);

   /**
    * Handles the current UTC calendar day's I/O usage stats for all packages collected during
    * the previous boot. The I/O usage stats are provided by the CarWatchdogService.
    *
    * @param userPackageIoUsageStats I/O usage stats for all packages.
    */
   void onTodayIoUsageStatsFetched(in List<UserPackageIoUsageStats> userPackageIoUsageStats);
}
