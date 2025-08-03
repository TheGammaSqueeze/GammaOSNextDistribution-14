package com.android.healthconnect.controller.permissiontypes.api

import android.health.connect.HealthConnectManager
import android.health.connect.HealthDataCategory
import android.health.connect.RecordTypeInfoResponse
import android.health.connect.datatypes.Record
import androidx.core.os.asOutcomeReceiver
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.permissions.data.fromHealthPermissionCategory
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.shared.HealthDataCategoryInt
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class FilterPermissionTypesUseCase
@Inject
constructor(
    private val healthConnectManager: HealthConnectManager,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    /** Returns list of [HealthPermissionType] for selected app under [HealthDataCategory] */
    suspend operator fun invoke(
        category: @HealthDataCategoryInt Int,
        selectedAppPackageName: String
    ): List<HealthPermissionType> =
        withContext(dispatcher) {
            val permissionTypeList =
                getFilteredHealthPermissionTypes(category, selectedAppPackageName)
            alphabeticallySortedMetadataList(permissionTypeList)
        }

    /** Returns list of [HealthPermissionType] for selected app under [HealthDataCategory] */
    private suspend fun getFilteredHealthPermissionTypes(
        category: @HealthDataCategoryInt Int,
        selectedAppPackageName: String
    ): List<HealthPermissionType> =
        withContext(dispatcher) {
            try {
                val recordTypeInfoMap: Map<Class<out Record>, RecordTypeInfoResponse> =
                    suspendCancellableCoroutine { continuation ->
                        healthConnectManager.queryAllRecordTypesInfo(
                            Runnable::run, continuation.asOutcomeReceiver())
                    }
                filterHealthPermissionTypes(category, selectedAppPackageName, recordTypeInfoMap)
            } catch (e: Exception) {
                emptyList()
            }
        }

    /**
     * Filters list of [HealthPermissionType] for selected app under a [HealthDataCategory] from all
     * record type information
     */
    private fun filterHealthPermissionTypes(
        category: @HealthDataCategoryInt Int,
        selectedAppPackageName: String,
        recordTypeInfoMap: Map<Class<out Record>, RecordTypeInfoResponse>
    ): List<HealthPermissionType> {
        val filteredRecordTypeInfos =
            recordTypeInfoMap.values.filter {
                it.dataCategory == category &&
                    it.contributingPackages.isNotEmpty() &&
                    it.contributingPackages.any { contributingApp ->
                        contributingApp.packageName == selectedAppPackageName
                    }
            }
        return filteredRecordTypeInfos
            .map { recordTypeInfo ->
                fromHealthPermissionCategory(recordTypeInfo.permissionCategory)
            }
            .toSet()
            .toList()
    }

    /** Sorts list of App Metadata alphabetically */
    private fun alphabeticallySortedMetadataList(
        permissionTypes: List<HealthPermissionType>
    ): List<HealthPermissionType> {
        return permissionTypes
            .stream()
            .sorted(Comparator.comparing { permissionType -> permissionType.name })
            .toList()
    }
}
