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

import android.automotive.watchdog.internal.PackageInfo;
import android.automotive.watchdog.internal.PackageIoOveruseStats;
import android.automotive.watchdog.internal.TimeoutLength;
import android.automotive.watchdog.internal.UserPackageIoUsageStats;
import android.automotive.watchdog.internal.ResourceStats;

/**
 * ICarWatchdogServiceForSystem interface used by the watchdog server to communicate with the
 * watchdog service.
 *
 * @hide
 */
interface ICarWatchdogServiceForSystem {
  /**
   * Checks if the client is alive.
   *
   * Watchdog server calls this method, expecting the clients will respond within timeout.
   * The final timeout is decided by the server, considering the requested timeout on client
   * registration. If no response from the clients, watchdog server will dump process information
   * and kill them.
   *
   * @param sessionId                   Unique id to identify each health check session.
   * @param timeout                     Final timeout given by the server based on client request.
   */
  oneway void checkIfAlive(int sessionId, in TimeoutLength timeout);

  /**
   * Notifies the client that it will be forcedly terminated in 1 second.
   */
  oneway void prepareProcessTermination();

  /**
   * Returns the package information for the given UIDs. Only UIDs with package information will be
   * returned.
   *
   * @param uids                        List of UIDs to resolve the package infos.
   * @param vendorPackagePrefixes       List of vendor package prefixes.
   */
  List<PackageInfo> getPackageInfosForUids(
            in int[] uids, in @utf8InCpp List<String> vendorPackagePrefixes);

  // TODO(b/269191275): This method was replaced by onLatestResourceStats in Android U.
  //  Deprecate method in Android W (N+2 releases).
  /**
   * Pushes the latest I/O overuse stats to the watchdog server.
   *
   * @param packageIoOveruseStats       Latest package I/O overuse stats, for all packages, from the
   *                                    recent collection.
   */
  oneway void latestIoOveruseStats(in List<PackageIoOveruseStats> packageIoOveruseStats);

  /**
   * Resets resource overuse stats on the watchdog server side.
   *
   * @param packageNames       Package names for which to reset the stats.
   */
  oneway void resetResourceOveruseStats(in @utf8InCpp List<String> packageNames);

  // TODO(b/273354756): This method was replaced by an async request/response pattern Android U.
  // Requests for the I/O stats are made through the requestTodayIoUsageStats method. And responses
  // are received by the carwatchdog daemon via ICarWatchdog#onTodayIoUsageStats. Deprecate method
  // in Android W.
  /**
   * Fetches current UTC calendar day's I/O usage stats for all packages collected during the
   * previous boot.
   */
  List<UserPackageIoUsageStats> getTodayIoUsageStats();

  /**
   * Pushes the latest resource usage and I/O overuse stats to the carwatchdog service.
   *
   * @param resourceStats  Latest resource stats, for the overall system and all packages, from
   * the recent collection.
   */
  oneway void onLatestResourceStats(in List<ResourceStats> resourceStats);

  /**
   * Request watchdog service to fetch the AIDL VHAL pid.
   *
   * Watchdog service responds with the AIDL VHAL pid via the onAidlVhalPidFetched callback in
   * the ICarWatchdog.aidl. The response is provided via an asynchronous call because
   * the watchdog service (that resides inside the CarService process) should make a binder call
   * to the CarServiceHelperService (that resides inside the SystemServer process) to fetch the PID.
   * This is a time consuming operation and cannot be performed on the same binder call.
   */
  oneway void requestAidlVhalPid();

  /**
   * Requests current UTC calendar day's I/O usage stats for all packages collected during the
   * previous boot. Stats are sent by CarWatchdogService through the
   * ICarWatchdog#onTodayIoUsageStats method once it receives the request.
   */
  oneway void requestTodayIoUsageStats();
}
