package com.android.healthconnect.controller.tests.data.access

import android.content.Context
import android.health.connect.HealthConnectManager
import android.health.connect.HealthDataCategory
import android.health.connect.HealthPermissionCategory
import android.health.connect.RecordTypeInfoResponse
import android.health.connect.datatypes.HeartRateRecord
import android.health.connect.datatypes.Record
import android.health.connect.datatypes.StepsRecord
import android.health.connect.datatypes.WeightRecord
import android.os.OutcomeReceiver
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.data.access.LoadPermissionTypeContributorAppsUseCase
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME_2
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME_3
import com.android.healthconnect.controller.tests.utils.getDataOrigin
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.invocation.InvocationOnMock

@HiltAndroidTest
class LoadPermissionTypeContributorAppsUseCaseTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    private lateinit var context: Context
    private val healthConnectManager: HealthConnectManager =
        Mockito.mock(HealthConnectManager::class.java)
    private lateinit var loadPermissionTypeContributorAppsUseCase:
        LoadPermissionTypeContributorAppsUseCase

    @Inject lateinit var appInfoReader: AppInfoReader

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        context = InstrumentationRegistry.getInstrumentation().context
        hiltRule.inject()
        loadPermissionTypeContributorAppsUseCase =
            LoadPermissionTypeContributorAppsUseCase(
                appInfoReader, healthConnectManager, Dispatchers.Main)
    }

    @Test
    fun loadPermissionTypeContributorAppsUseCase_noRecordsStored_returnsEmptyMap() = runTest {
        Mockito.doAnswer(prepareAnswer(mapOf()))
            .`when`(healthConnectManager)
            .queryAllRecordTypesInfo(ArgumentMatchers.any(), ArgumentMatchers.any())

        val result = loadPermissionTypeContributorAppsUseCase.invoke(HealthPermissionType.STEPS)
        val expected = listOf<AppMetadata>()
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun loadPermissionTypeContributorAppsUseCase_returnsCorrectApps() = runTest {
        val recordTypeInfoMap: Map<Class<out Record>, RecordTypeInfoResponse> =
            mapOf(
                StepsRecord::class.java to
                    RecordTypeInfoResponse(
                        HealthPermissionCategory.STEPS,
                        HealthDataCategory.ACTIVITY,
                        listOf(
                            getDataOrigin(TEST_APP_PACKAGE_NAME),
                            getDataOrigin(TEST_APP_PACKAGE_NAME_2))),
                WeightRecord::class.java to
                    RecordTypeInfoResponse(
                        HealthPermissionCategory.WEIGHT,
                        HealthDataCategory.BODY_MEASUREMENTS,
                        listOf((getDataOrigin(TEST_APP_PACKAGE_NAME_2)))),
                HeartRateRecord::class.java to
                    RecordTypeInfoResponse(
                        HealthPermissionCategory.HEART_RATE,
                        HealthDataCategory.VITALS,
                        listOf((getDataOrigin(TEST_APP_PACKAGE_NAME_3)))))
        Mockito.doAnswer(prepareAnswer(recordTypeInfoMap))
            .`when`(healthConnectManager)
            .queryAllRecordTypesInfo(ArgumentMatchers.any(), ArgumentMatchers.any())

        val result = loadPermissionTypeContributorAppsUseCase.invoke(HealthPermissionType.STEPS)
        assertThat(result.size).isEqualTo(2)
        assertThat(result[0].packageName).isEqualTo(TEST_APP_PACKAGE_NAME)
        assertThat(result[1].packageName).isEqualTo(TEST_APP_PACKAGE_NAME_2)
    }

    private fun prepareAnswer(
        map: Map<Class<out Record>, RecordTypeInfoResponse>
    ): (InvocationOnMock) -> Map<Class<out Record>, RecordTypeInfoResponse> {
        val answer = { args: InvocationOnMock ->
            val receiver =
                args.arguments[1]
                    as OutcomeReceiver<Map<Class<out Record>, RecordTypeInfoResponse>, *>
            receiver.onResult(map)
            map
        }
        return answer
    }
}
