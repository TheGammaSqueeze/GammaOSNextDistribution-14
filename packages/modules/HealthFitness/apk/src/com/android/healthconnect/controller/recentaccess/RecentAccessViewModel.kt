/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.healthconnect.controller.recentaccess

import android.health.connect.accesslog.AccessLog
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.controller.permissions.connectedapps.ILoadHealthPermissionApps
import com.android.healthconnect.controller.recentaccess.RecentAccessViewModel.RecentAccessState.Loading
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.uppercaseTitle
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.app.ConnectedAppStatus
import com.android.healthconnect.controller.shared.dataTypeToCategory
import com.android.healthconnect.controller.utils.TimeSource
import com.android.healthconnect.controller.utils.postValueIfUpdated
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class RecentAccessViewModel
@Inject
constructor(
    private val appInfoReader: AppInfoReader,
    private val loadHealthPermissionApps: ILoadHealthPermissionApps,
    private val loadRecentAccessUseCase: ILoadRecentAccessUseCase,
    private val timeSource: TimeSource
) : ViewModel() {

    companion object {
        private val MAX_CLUSTER_DURATION = Duration.ofMinutes(10)
        private val MAX_GAP_BETWEEN_LOGS_IN_CLUSTER_DURATION = Duration.ofMinutes(1)
    }

    private val _recentAccessApps = MutableLiveData<RecentAccessState>()
    val recentAccessApps: LiveData<RecentAccessState>
        get() = _recentAccessApps

    fun loadRecentAccessApps(maxNumEntries: Int = -1) {
        // Don't show loading if data was loaded before just refresh.
        if (_recentAccessApps.value !is RecentAccessState.WithData) {
            _recentAccessApps.postValue(Loading)
        }
        viewModelScope.launch {
            try {
                val clusters = getRecentAccessAppsClusters(maxNumEntries)
                _recentAccessApps.postValueIfUpdated(RecentAccessState.WithData(clusters))
            } catch (ex: Exception) {
                _recentAccessApps.postValueIfUpdated(RecentAccessState.Error)
            }
        }
    }

    private suspend fun getRecentAccessAppsClusters(
        maxNumEntries: Int
    ): List<RecentAccessEntry> {
        val accessLogs = loadRecentAccessUseCase.invoke()
        val connectedApps = loadHealthPermissionApps.invoke()
        val inactiveApps =
            connectedApps
                .groupBy { it.status }[ConnectedAppStatus.INACTIVE]
                .orEmpty()
                .map { connectedAppMetadata -> connectedAppMetadata.appMetadata.packageName }

        val clusters = clusterEntries(accessLogs, maxNumEntries)
        val filteredClusters = mutableListOf<RecentAccessEntry>()
        clusters.forEach {
            if (inactiveApps.contains(it.metadata.packageName)) {
                it.isInactive = true
            }
            if (inactiveApps.contains(it.metadata.packageName) ||
                appInfoReader.isAppInstalled(it.metadata.packageName)) {
                filteredClusters.add(it)
            }
        }
        return filteredClusters
    }

    private data class DataAccessEntryCluster(
        val latestTime: Instant,
        var earliestTime: Instant,
        val recentDataAccessEntry: RecentAccessEntry
    )

    private suspend fun clusterEntries(
        accessLogs: List<AccessLog>,
        maxNumEntries: Int
    ): List<RecentAccessEntry> {
        if (accessLogs.isEmpty()) {
            return listOf()
        }

        val dataAccessEntries = mutableListOf<RecentAccessEntry>()
        val currentDataAccessEntryClusters = hashMapOf<String, DataAccessEntryCluster>()

        // Logs are sorted by time, descending
        for (currentLog in accessLogs) {
            val currentPackageName = currentLog.packageName
            val currentCluster = currentDataAccessEntryClusters.get(currentPackageName)

            if (currentCluster == null) {
                // If no cluster started for this app yet, init one with the current log
                currentDataAccessEntryClusters.put(
                    currentPackageName, initDataAccessEntryCluster(currentLog))
            } else if (logBelongsToCluster(currentLog, currentCluster)) {
                updateDataAccessEntryCluster(currentCluster, currentLog)
            } else {
                // Log doesn't belong to current cluster. Convert current cluster to UI entry and
                // remove
                // it from currently accumulating clusters, start a new cluster with currentLog

                dataAccessEntries.add(currentCluster.recentDataAccessEntry)

                currentDataAccessEntryClusters.remove(currentPackageName)

                currentDataAccessEntryClusters.put(
                    currentPackageName, initDataAccessEntryCluster(currentLog))

                // If we have enough entries already and all clusters that are still being
                // accumulated are
                // already earlier than the ones we completed, we can finish and return what we have
                if (maxNumEntries != -1 && dataAccessEntries.size >= maxNumEntries) {
                    val earliestDataAccessEntryTime = dataAccessEntries.minOf { it.instantTime }
                    if (currentDataAccessEntryClusters.values.none {
                        it.earliestTime.isAfter(earliestDataAccessEntryTime)
                    }) {
                        break
                    }
                }
            }
        }

        // complete all remaining clusters and add them to the list of entries. If we already had
        // enough
        // entries and we don't need these remaining clusters (we broke the loop above early), they
        // will be
        // filtered out anyway by final sorting and limiting.
        currentDataAccessEntryClusters.values.map { cluster ->
            dataAccessEntries.add(cluster.recentDataAccessEntry)
        }

        return dataAccessEntries
            .sortedByDescending { it.instantTime }
            .take(if (maxNumEntries != -1) maxNumEntries else dataAccessEntries.size)
    }

    private suspend fun initDataAccessEntryCluster(
        accessLog: AccessLog
    ): DataAccessEntryCluster {
        val newCluster =
            DataAccessEntryCluster(
                latestTime = accessLog.accessTime,
                earliestTime = Instant.MIN,
                recentDataAccessEntry =
                    RecentAccessEntry(
                        metadata =
                            appInfoReader.getAppMetadata(packageName = accessLog.packageName)))

        updateDataAccessEntryCluster(newCluster, accessLog)
        return newCluster
    }

    private fun logBelongsToCluster(
        accessLog: AccessLog,
        cluster: DataAccessEntryCluster
    ): Boolean =
        Duration.between(accessLog.accessTime, cluster.latestTime)
            .compareTo(MAX_CLUSTER_DURATION) <= 0 &&
            Duration.between(accessLog.accessTime, cluster.earliestTime)
                .compareTo(MAX_GAP_BETWEEN_LOGS_IN_CLUSTER_DURATION) <= 0

    private fun updateDataAccessEntryCluster(
        cluster: DataAccessEntryCluster,
        accessLog: AccessLog
    ) {
        val midnight =
            timeSource
                .currentLocalDateTime()
                .toLocalDate()
                .atStartOfDay(timeSource.deviceZoneOffset())
                .toInstant()

        cluster.earliestTime = accessLog.accessTime
        cluster.recentDataAccessEntry.instantTime = accessLog.accessTime
        cluster.recentDataAccessEntry.isToday = (!accessLog.accessTime.isBefore(midnight))

        if (accessLog.operationType == AccessLog.OperationType.OPERATION_TYPE_READ) {
            cluster.recentDataAccessEntry.dataTypesRead.addAll(
                accessLog.recordTypes.map { dataTypeToCategory(it).uppercaseTitle() })
        } else {
            cluster.recentDataAccessEntry.dataTypesWritten.addAll(
                accessLog.recordTypes.map { dataTypeToCategory(it).uppercaseTitle() })
        }
    }

    sealed class RecentAccessState {
        object Loading : RecentAccessState()
        object Error : RecentAccessState()
        data class WithData(val recentAccessEntries: List<RecentAccessEntry>) : RecentAccessState()
    }
}
