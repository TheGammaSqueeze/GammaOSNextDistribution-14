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

package android.car.watchdog;

import android.car.watchdog.ICarWatchdogServiceCallback;
import android.car.watchdog.IResourceOveruseListener;
import android.car.watchdog.PackageKillableState;
import android.car.watchdog.ResourceOveruseConfiguration;
import android.car.watchdog.ResourceOveruseStats;
import android.os.UserHandle;

/** @hide */
interface ICarWatchdogService {
    // registerClient needs to get callingPid, so cannot be oneway.
    void registerClient(in ICarWatchdogServiceCallback client, in int timeout);
    void unregisterClient(in ICarWatchdogServiceCallback client);
    void tellClientAlive(in ICarWatchdogServiceCallback client, in int sessionId);

    ResourceOveruseStats getResourceOveruseStats(
        in int resourceOveruseFlag, in int maxStatsPeriod);
    List<ResourceOveruseStats> getAllResourceOveruseStats(
        in int resourceOveruseFlag, in int minimumStatsFlag, in int maxStatsPeriod);
    ResourceOveruseStats getResourceOveruseStatsForUserPackage(
        in String packageName, in UserHandle userHandle, in int resourceOveruseFlag,
            in int maxStatsPeriod);

    // addResourceOveruseListener needs to get callingUid, so cannot be oneway.
    void addResourceOveruseListener(
        in int resourceOveruseFlag, in IResourceOveruseListener listener);
    oneway void removeResourceOveruseListener(in IResourceOveruseListener listener);

    // Following APIs need to get calling pid/uid for permission checking, so cannot be oneway.
    void addResourceOveruseListenerForSystem(
        in int resourceOveruseFlag, in IResourceOveruseListener listener);
    void removeResourceOveruseListenerForSystem(in IResourceOveruseListener listener);

    void setKillablePackageAsUser(in String packageName, in UserHandle userHandle,
        in boolean isKillable);
    List<PackageKillableState> getPackageKillableStatesAsUser(in UserHandle user);

    int setResourceOveruseConfigurations(
        in List<ResourceOveruseConfiguration> configurations, in int resourceOveruseFlag);
    List<ResourceOveruseConfiguration> getResourceOveruseConfigurations(
        in int resourceOveruseFlag);
}
