package com.android.healthconnect.controller.permissiontypes.api

import android.health.connect.HealthConnectManager
import android.health.connect.HealthDataCategory
import android.health.connect.RecordTypeInfoResponse
import android.health.connect.datatypes.DataOrigin
import android.health.connect.datatypes.Record
import androidx.core.os.asOutcomeReceiver
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.shared.HealthDataCategoryInt
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.app.AppMetadata
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class LoadContributingAppsUseCase
@Inject
constructor(
    private val appInfoReader: AppInfoReader,
    private val healthConnectManager: HealthConnectManager,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    /** Returns list of contributing apps for selected [HealthDataCategory] */
    suspend operator fun invoke(category: @HealthDataCategoryInt Int): List<AppMetadata> =
        withContext(dispatcher) {
            val appsList: MutableSet<AppMetadata> = mutableSetOf()
            val contributingAppsList = getListOfContributingApps(category)
            contributingAppsList.forEach { contributingApp ->
                if (appsList.none { it.packageName == contributingApp }) {
                    appsList.add(appInfoReader.getAppMetadata(contributingApp))
                }
            }
            alphabeticallySortedMetadataList(appsList)
        }

    /** Returns list of contributing apps for selected [HealthDataCategory] */
    private suspend fun getListOfContributingApps(
        category: @HealthDataCategoryInt Int
    ): List<String> =
        withContext(dispatcher) {
            try {
                val recordTypeInfoMap: Map<Class<out Record>, RecordTypeInfoResponse> =
                    suspendCancellableCoroutine { continuation ->
                        healthConnectManager.queryAllRecordTypesInfo(
                            Runnable::run, continuation.asOutcomeReceiver())
                    }
                filterContributingApps(category, recordTypeInfoMap).map { it.packageName }
            } catch (e: Exception) {
                emptyList()
            }
        }

    /**
     * Filters list of contributing apps for selected [HealthDataCategory] from all record type
     * information
     */
    private fun filterContributingApps(
        category: @HealthDataCategoryInt Int,
        recordTypeInfoMap: Map<Class<out Record>, RecordTypeInfoResponse>
    ): List<DataOrigin> =
        recordTypeInfoMap.values
            .filter { it.dataCategory == category && it.contributingPackages.isNotEmpty() }
            .map { it.contributingPackages }
            .flatten()

    /** Sorts list of App Metadata alphabetically */
    private fun alphabeticallySortedMetadataList(
        packageNames: Set<AppMetadata>
    ): List<AppMetadata> {
        return packageNames
            .stream()
            .sorted(Comparator.comparing { appMetaData -> appMetaData.appName })
            .toList()
    }
}
