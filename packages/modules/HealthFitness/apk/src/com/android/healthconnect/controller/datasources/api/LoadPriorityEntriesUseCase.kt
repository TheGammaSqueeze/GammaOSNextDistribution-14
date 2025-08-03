package com.android.healthconnect.controller.datasources.api

import android.health.connect.datatypes.Record
import com.android.healthconnect.controller.data.entries.api.LoadDataEntriesInput
import com.android.healthconnect.controller.data.entries.api.LoadEntriesHelper
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationPeriod
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.permissiontypes.api.ILoadPriorityListUseCase
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import com.android.healthconnect.controller.utils.toInstantAtStartOfDay
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class LoadPriorityEntriesUseCase
@Inject
constructor(
    private val loadEntriesHelper: LoadEntriesHelper,
    private val loadPriorityListUseCase: ILoadPriorityListUseCase,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) : ILoadPriorityEntriesUseCase {

    /**
     * Returns a list of records from the specified date originating from any of the apps on the
     * priority list for this healthPermissionType.
     */
    override suspend fun invoke(
        healthPermissionType: HealthPermissionType,
        localDate: LocalDate
    ): UseCaseResults<List<Record>> =
        withContext(dispatcher) {
            try {
                val localDateInstant = localDate.toInstantAtStartOfDay()
                val records = mutableListOf<Record>()

                when (val priorityAppsResult =
                    loadPriorityListUseCase.invoke(
                        HealthDataCategoryExtensions.fromHealthPermissionType(
                            healthPermissionType))) {
                    is UseCaseResults.Success -> {
                        val priorityApps = priorityAppsResult.data

                        priorityApps.forEach { priorityApp ->
                            val input =
                                LoadDataEntriesInput(
                                    HealthPermissionType.SLEEP,
                                    packageName = priorityApp.packageName,
                                    displayedStartTime = localDateInstant,
                                    period = DateNavigationPeriod.PERIOD_DAY,
                                    showDataOrigin = false)
                            val entryRecords = loadEntriesHelper.readRecords(input)

                            records.addAll(entryRecords)
                        }
                    }
                    is UseCaseResults.Failed -> {
                        throw priorityAppsResult.exception
                    }
                }

                // Sorted for testing
                UseCaseResults.Success(
                    records.sortedByDescending { loadEntriesHelper.getStartTime(it) })
            } catch (e: Exception) {
                UseCaseResults.Failed(e)
            }
        }
}

interface ILoadPriorityEntriesUseCase {
    suspend fun invoke(
        healthPermissionType: HealthPermissionType,
        localDate: LocalDate
    ): UseCaseResults<List<Record>>
}
