package com.android.healthconnect.controller.tests.deletion

import android.health.connect.HealthDataCategory
import android.os.Bundle
import com.android.healthconnect.controller.deletion.ChosenRange
import com.android.healthconnect.controller.deletion.DeletionParameters
import com.android.healthconnect.controller.deletion.DeletionState
import com.android.healthconnect.controller.deletion.DeletionType
import com.android.healthconnect.controller.permissions.data.HealthPermissionStrings
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import java.lang.IllegalStateException
import java.time.Duration
import java.time.Instant
import junit.framework.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class DeletionParametersTest {

    @Test
    fun deletionToParcel() {

        val deletionParameters =
            DeletionParameters(
                chosenRange = ChosenRange.DELETE_RANGE_LAST_24_HOURS,
                deletionType = DeletionType.DeletionTypeAllData(),
            )

        val bundle = Bundle()
        bundle.putParcelable("DELETION_TEST", deletionParameters)

        val outValue = bundle.getParcelable("DELETION_TEST") as DeletionParameters?

        assertTrue(outValue != null)
        assertTrue(deletionParameters.chosenRange == outValue?.chosenRange)
        assertTrue(deletionParameters.deletionType == outValue?.deletionType)
    }

    @Test
    fun deletionTypeHealthPermissionTypeData_toParcel() {
        val startTime = Instant.parse("2022-11-11T20:00:00.000Z")
        val endTime = Instant.parse("2022-11-14T20:00:00.000Z")
        val deletionType =
            DeletionType.DeletionTypeHealthPermissionTypeData(
                HealthPermissionType.ACTIVE_CALORIES_BURNED)
        val deletionParameters =
            DeletionParameters(
                chosenRange = ChosenRange.DELETE_RANGE_LAST_30_DAYS,
                startTimeMs = startTime.toEpochMilli(),
                endTimeMs = endTime.toEpochMilli(),
                deletionType = deletionType,
                deletionState = DeletionState.STATE_DELETION_STARTED,
                showTimeRangePickerDialog = false)

        val bundle = Bundle()
        bundle.putParcelable("DELETION_TEST", deletionParameters)

        val outValue = bundle.getParcelable("DELETION_TEST") as DeletionParameters?

        assertTrue(outValue != null)
        assertTrue(deletionParameters.chosenRange == outValue?.chosenRange)
        assertTrue(deletionParameters.startTimeMs == outValue?.startTimeMs)
        assertTrue(deletionParameters.endTimeMs == outValue?.endTimeMs)
        assertTrue(deletionParameters.deletionType == outValue?.deletionType)
        assertTrue(
            deletionType.healthPermissionType ==
                (outValue?.deletionType as DeletionType.DeletionTypeHealthPermissionTypeData)
                    .healthPermissionType)
        assertTrue(deletionParameters.deletionState == outValue.deletionState)
        assertTrue(
            deletionParameters.showTimeRangePickerDialog == outValue.showTimeRangePickerDialog)
    }

    @Test
    fun deletionTypeCategoryData_toParcel() {
        val startTime = Instant.parse("2022-11-11T20:00:00.000Z")
        val endTime = Instant.parse("2022-11-14T20:00:00.000Z")
        val deletionType = DeletionType.DeletionTypeCategoryData(HealthDataCategory.ACTIVITY)
        val deletionParameters =
            DeletionParameters(
                chosenRange = ChosenRange.DELETE_RANGE_LAST_30_DAYS,
                startTimeMs = startTime.toEpochMilli(),
                endTimeMs = endTime.toEpochMilli(),
                deletionType = deletionType,
                deletionState = DeletionState.STATE_DELETION_STARTED,
                showTimeRangePickerDialog = false)

        val bundle = Bundle()
        bundle.putParcelable("DELETION_TEST", deletionParameters)

        val outValue = bundle.getParcelable("DELETION_TEST") as DeletionParameters?

        assertTrue(outValue != null)
        assertTrue(deletionParameters.chosenRange == outValue?.chosenRange)
        assertTrue(deletionParameters.startTimeMs == outValue?.startTimeMs)
        assertTrue(deletionParameters.endTimeMs == outValue?.endTimeMs)
        assertTrue(deletionParameters.deletionType == outValue?.deletionType)
        assertTrue(
            deletionType.category ==
                (outValue?.deletionType as DeletionType.DeletionTypeCategoryData).category)
        assertTrue(deletionParameters.deletionState == outValue.deletionState)
        assertTrue(
            deletionParameters.showTimeRangePickerDialog == outValue.showTimeRangePickerDialog)
    }

    @Test
    fun getPermissionTypeLabel_permissionTypeFromApp_correctPermissionLabelReturned() {
        val deletionParameters =
            DeletionParameters(
                chosenRange = ChosenRange.DELETE_RANGE_LAST_24_HOURS,
                deletionType =
                    DeletionType.DeletionTypeHealthPermissionTypeFromApp(
                        HealthPermissionType.ACTIVE_CALORIES_BURNED,
                        packageName = TEST_APP_PACKAGE_NAME,
                        appName = TEST_APP_NAME),
            )

        assertTrue(
            deletionParameters.getPermissionTypeLabel() ==
                HealthPermissionStrings.fromPermissionType(
                        HealthPermissionType.ACTIVE_CALORIES_BURNED)
                    .lowercaseLabel)
    }

    @Test
    fun getPermissionTypeLabel_permissionTypeDoesNotExist_errorThrown() {
        val deletionParameters = DeletionParameters()

        assertThrows(IllegalStateException::class.java) {
            deletionParameters.getPermissionTypeLabel()
        }
    }

    @Test
    fun getCategoryLabel_categoryDataDoesNotExist_errorThrown() {
        val deletionParameters = DeletionParameters()

        assertThrows(IllegalStateException::class.java) { deletionParameters.getCategoryLabel() }
    }

    @Test
    fun getStartTimeInstant_24HoursRangeSelected_correctStartTimeReturned() {
        val deletionParameters =
            DeletionParameters(
                chosenRange = ChosenRange.DELETE_RANGE_LAST_24_HOURS,
                deletionType = DeletionType.DeletionTypeAllData())

        assertTrue(
            deletionParameters.getStartTimeInstant() ==
                deletionParameters.getEndTimeInstant().minus(Duration.ofDays(1)))
    }

    @Test
    fun getStartTimeInstant_7DaysRangeSelected_correctStartTimeReturned() {
        val deletionParameters =
            DeletionParameters(
                chosenRange = ChosenRange.DELETE_RANGE_LAST_7_DAYS,
                deletionType = DeletionType.DeletionTypeAllData())

        assertTrue(
            deletionParameters.getStartTimeInstant() ==
                deletionParameters.getEndTimeInstant().minus(Duration.ofDays(7)))
    }

    @Test
    fun getStartTimeInstant_30DaysRangeSelected_correctStartTimeReturned() {
        val deletionParameters =
            DeletionParameters(
                chosenRange = ChosenRange.DELETE_RANGE_LAST_30_DAYS,
                deletionType = DeletionType.DeletionTypeAllData())

        assertTrue(
            deletionParameters.getStartTimeInstant() ==
                deletionParameters.getEndTimeInstant().minus(Duration.ofDays(30)))
    }

    @Test
    fun getStartTimeInstant_allTimeRangeSelected_correctStartTimeReturned() {
        val deletionParameters =
            DeletionParameters(
                chosenRange = ChosenRange.DELETE_RANGE_ALL_DATA,
                deletionType = DeletionType.DeletionTypeAllData())

        assertTrue(deletionParameters.getStartTimeInstant() == Instant.EPOCH)
    }

    @Test
    fun getEndTimeInstant_24HoursRangeSelected_correctEndTimeReturned() {
        val startTime = Instant.parse("2022-11-11T20:00:00.000Z")
        val endTime = Instant.parse("2022-11-14T20:00:00.000Z")
        val deletionParameters =
            DeletionParameters(
                chosenRange = ChosenRange.DELETE_RANGE_LAST_7_DAYS,
                startTimeMs = startTime.toEpochMilli(),
                endTimeMs = endTime.toEpochMilli(),
                deletionType = DeletionType.DeletionTypeAllData())

        assertTrue(
            deletionParameters.getEndTimeInstant() == Instant.ofEpochMilli(endTime.toEpochMilli()))
    }

    @Test
    fun getEndTimeInstant_allTimeRangeSelected_correctEndTimeReturned() {
        val startTime = Instant.parse("2022-11-11T20:00:00.000Z")
        val endTime = Instant.parse("2022-11-14T20:00:00.000Z")
        val deletionParameters =
            DeletionParameters(
                chosenRange = ChosenRange.DELETE_RANGE_ALL_DATA,
                startTimeMs = startTime.toEpochMilli(),
                endTimeMs = endTime.toEpochMilli(),
                deletionType = DeletionType.DeletionTypeAllData())

        assertTrue(deletionParameters.getEndTimeInstant() == Instant.ofEpochMilli(Long.MAX_VALUE))
    }
}
