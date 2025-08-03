package com.android.healthconnect.controller.tests.permissiontypes.api

import android.content.Context
import android.health.connect.HealthConnectManager
import android.health.connect.HealthDataCategory
import android.health.connect.HealthPermissionCategory
import android.health.connect.RecordTypeInfoResponse
import android.health.connect.datatypes.ExerciseLap
import android.health.connect.datatypes.ExerciseSegment
import android.health.connect.datatypes.ExerciseSessionRecord
import android.health.connect.datatypes.ExerciseSessionType
import android.health.connect.datatypes.Record
import android.health.connect.datatypes.StepsCadenceRecord
import android.os.OutcomeReceiver
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.permissiontypes.api.FilterPermissionTypesUseCase
import com.android.healthconnect.controller.tests.utils.CoroutineTestRule
import com.android.healthconnect.controller.tests.utils.NOW
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME_2
import com.android.healthconnect.controller.tests.utils.getDataOrigin
import com.android.healthconnect.controller.tests.utils.getMetaData
import com.google.common.truth.Truth
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Matchers
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock

@ExperimentalCoroutinesApi
@HiltAndroidTest
class FilterPermissionTypesUseCaseTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)
    @get:Rule val coroutineTestRule = CoroutineTestRule()

    private var manager: HealthConnectManager = Mockito.mock(HealthConnectManager::class.java)
    private lateinit var usecase: FilterPermissionTypesUseCase
    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        usecase = FilterPermissionTypesUseCase(manager, Dispatchers.Main)
    }

    @Test
    fun appFilters_permissionCategoriesAreFilteredCorrectly() = runTest {
        val recordTypeInfo =
            mapOf<Record, RecordTypeInfoResponse>(
                getStepsCadence(listOf(10.3, 20.1)) to
                    RecordTypeInfoResponse(
                        HealthPermissionCategory.STEPS,
                        HealthDataCategory.ACTIVITY,
                        listOf(getDataOrigin(TEST_APP_PACKAGE_NAME))),
                getRecord(type = ExerciseSessionType.EXERCISE_SESSION_TYPE_BIKING) to
                    RecordTypeInfoResponse(
                        HealthPermissionCategory.EXERCISE,
                        HealthDataCategory.ACTIVITY,
                        listOf(getDataOrigin(TEST_APP_PACKAGE_NAME_2))))

        Mockito.doAnswer(prepareAnswer(recordTypeInfo))
            .`when`(manager)
            .queryAllRecordTypesInfo(Matchers.any(), Matchers.any())

        val testApp1FilteredPermissionCategories =
            usecase.invoke(HealthDataCategory.ACTIVITY, TEST_APP_PACKAGE_NAME)

        Truth.assertThat(testApp1FilteredPermissionCategories.size).isEqualTo(1)
        Truth.assertThat(testApp1FilteredPermissionCategories).contains(HealthPermissionType.STEPS)

        val testApp2FilteredPermissionCategories =
            usecase.invoke(HealthDataCategory.ACTIVITY, TEST_APP_PACKAGE_NAME_2)

        Truth.assertThat(testApp2FilteredPermissionCategories.size).isEqualTo(1)
        Truth.assertThat(testApp2FilteredPermissionCategories)
            .contains(HealthPermissionType.EXERCISE)
    }

    @Test
    fun appFilters_duplicateDataPresentInApiResponse_duplicateDataTypesNotShownOnFilters() =
        runTest {
            val recordTypeInfo =
                mapOf<Record, RecordTypeInfoResponse>(
                    getStepsCadence(listOf(10.3, 20.1)) to
                        RecordTypeInfoResponse(
                            HealthPermissionCategory.STEPS,
                            HealthDataCategory.ACTIVITY,
                            listOf(getDataOrigin(TEST_APP_PACKAGE_NAME))),
                    getStepsCadence(listOf(10.3, 20.1)) to
                        RecordTypeInfoResponse(
                            HealthPermissionCategory.STEPS,
                            HealthDataCategory.ACTIVITY,
                            listOf(getDataOrigin(TEST_APP_PACKAGE_NAME))),
                    getRecord(type = ExerciseSessionType.EXERCISE_SESSION_TYPE_BIKING) to
                        RecordTypeInfoResponse(
                            HealthPermissionCategory.EXERCISE,
                            HealthDataCategory.ACTIVITY,
                            listOf(getDataOrigin(TEST_APP_PACKAGE_NAME))),
                    getRecord(type = ExerciseSessionType.EXERCISE_SESSION_TYPE_BIKING) to
                        RecordTypeInfoResponse(
                            HealthPermissionCategory.EXERCISE,
                            HealthDataCategory.ACTIVITY,
                            listOf(getDataOrigin(TEST_APP_PACKAGE_NAME))))

            Mockito.doAnswer(prepareAnswer(recordTypeInfo))
                .`when`(manager)
                .queryAllRecordTypesInfo(Matchers.any(), Matchers.any())

            val result = usecase.invoke(HealthDataCategory.ACTIVITY, TEST_APP_PACKAGE_NAME)

            Truth.assertThat(result.size).isEqualTo(2)
            Truth.assertThat(result).contains(HealthPermissionType.EXERCISE)
            Truth.assertThat(result).contains(HealthPermissionType.STEPS)
        }

    private fun prepareAnswer(
        recordTypeInfo: Map<Record, RecordTypeInfoResponse>
    ): (InvocationOnMock) -> Nothing? {
        val answer = { args: InvocationOnMock ->
            val receiver =
                args.arguments[1] as OutcomeReceiver<Map<Record, RecordTypeInfoResponse>, *>
            receiver.onResult(recordTypeInfo)
            null
        }
        return answer
    }

    private fun getStepsCadence(samples: List<Double>): StepsCadenceRecord {
        return StepsCadenceRecord.Builder(
                getMetaData(),
                NOW,
                NOW.plusSeconds(samples.size.toLong() + 1),
                samples.map { rate ->
                    StepsCadenceRecord.StepsCadenceRecordSample(rate, NOW.plusSeconds(1))
                })
            .build()
    }

    private fun getRecord(
        type: Int = ExerciseSessionType.EXERCISE_SESSION_TYPE_BIKING,
        title: String? = null,
        note: String? = null,
        laps: List<ExerciseLap> = emptyList(),
        segments: List<ExerciseSegment> = emptyList()
    ): ExerciseSessionRecord {
        return ExerciseSessionRecord.Builder(getMetaData(), NOW, NOW.plusSeconds(1000), type)
            .setNotes(note)
            .setLaps(laps)
            .setTitle(title)
            .setSegments(segments)
            .build()
    }
}
