/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.tests.utils

import android.health.connect.datatypes.BasalMetabolicRateRecord
import android.health.connect.datatypes.DataOrigin
import android.health.connect.datatypes.Device
import android.health.connect.datatypes.DistanceRecord
import android.health.connect.datatypes.HeartRateRecord
import android.health.connect.datatypes.Metadata
import android.health.connect.datatypes.Record
import android.health.connect.datatypes.SleepSessionRecord
import android.health.connect.datatypes.StepsRecord
import android.health.connect.datatypes.TotalCaloriesBurnedRecord
import android.health.connect.datatypes.units.Energy
import android.health.connect.datatypes.units.Length
import android.health.connect.datatypes.units.Power
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.healthconnect.controller.dataentries.units.PowerConverter
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.utils.randomInstant
import com.android.healthconnect.controller.utils.toInstant
import com.android.healthconnect.controller.utils.toLocalDateTime
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random

val NOW: Instant = Instant.parse("2022-10-20T07:06:05.432Z")
val MIDNIGHT: Instant = Instant.parse("2022-10-20T00:00:00.000Z")

fun getHeartRateRecord(heartRateValues: List<Long>, startTime: Instant = NOW): HeartRateRecord {
    return HeartRateRecord.Builder(
            getMetaData(),
            startTime,
            startTime.plusSeconds(2),
            heartRateValues.map { HeartRateRecord.HeartRateSample(it, NOW) })
        .build()
}

fun getStepsRecord(steps: Long, time: Instant = NOW): StepsRecord {
    return StepsRecord.Builder(getMetaData(), time, time.plusSeconds(2), steps).build()
}

fun getBasalMetabolicRateRecord(calories: Long): BasalMetabolicRateRecord {
    val watts = PowerConverter.convertWattsFromCalories(calories)
    return BasalMetabolicRateRecord.Builder(getMetaData(), NOW, Power.fromWatts(watts)).build()
}

fun getDistanceRecord(distance: Length, time: Instant = NOW): DistanceRecord {
    return DistanceRecord.Builder(getMetaData(), time, time.plusSeconds(2), distance).build()
}

fun getTotalCaloriesBurnedRecord(calories: Energy, time: Instant = NOW): TotalCaloriesBurnedRecord {
    return TotalCaloriesBurnedRecord.Builder(getMetaData(), time, time.plusSeconds(2), calories)
        .build()
}

fun getSleepSessionRecord(startTime: Instant = NOW): SleepSessionRecord {
    val endTime = startTime.toLocalDateTime().plusHours(8).toInstant()
    return SleepSessionRecord.Builder(getMetaData(), startTime, endTime).build()
}

fun getSleepSessionRecord(startTime: Instant, endTime: Instant): SleepSessionRecord {
    return SleepSessionRecord.Builder(getMetaData(), startTime, endTime).build()
}

fun getRandomRecord(healthPermissionType: HealthPermissionType, date: LocalDate): Record {
    return when (healthPermissionType) {
        HealthPermissionType.STEPS -> getStepsRecord(Random.nextLong(0, 5000), date.randomInstant())
        HealthPermissionType.DISTANCE ->
            getDistanceRecord(
                Length.fromMeters(Random.nextDouble(0.0, 5000.0)), date.randomInstant())
        HealthPermissionType.TOTAL_CALORIES_BURNED ->
            getTotalCaloriesBurnedRecord(
                Energy.fromCalories(Random.nextDouble(1500.0, 5000.0)), date.randomInstant())
        HealthPermissionType.SLEEP -> getSleepSessionRecord(date.randomInstant())
        else ->
            throw IllegalArgumentException(
                "HealthPermissionType $healthPermissionType not supported")
    }
}

fun getMetaData(): Metadata {
    return getMetaData(TEST_APP_PACKAGE_NAME)
}

fun getMetaData(packageName: String): Metadata {
    val device: Device =
        Device.Builder().setManufacturer("google").setModel("Pixel4a").setType(2).build()
    val dataOrigin = DataOrigin.Builder().setPackageName(packageName).build()
    return Metadata.Builder()
        .setId("test_id")
        .setDevice(device)
        .setDataOrigin(dataOrigin)
        .setClientRecordId("BMR" + Math.random().toString())
        .build()
}

fun getDataOrigin(packageName: String): DataOrigin =
    DataOrigin.Builder().setPackageName(packageName).build()

fun getSleepSessionRecords(inputDates: List<Pair<Instant, Instant>>): List<SleepSessionRecord> {
    val result = arrayListOf<SleepSessionRecord>()
    inputDates.forEach { (startTime, endTime) ->
        result.add(SleepSessionRecord.Builder(getMetaData(), startTime, endTime).build())
    }

    return result
}

fun verifySleepSessionListsEqual(actual: List<Record>, expected: List<SleepSessionRecord>) {
    assertThat(actual.size).isEqualTo(expected.size)
    for ((index, element) in actual.withIndex()) {
        assertThat(element is SleepSessionRecord).isTrue()
        val expectedElement = expected[index]
        val actualElement = element as SleepSessionRecord

        assertThat(actualElement.startTime).isEqualTo(expectedElement.startTime)
        assertThat(actualElement.endTime).isEqualTo(expectedElement.endTime)
        assertThat(actualElement.notes).isEqualTo(expectedElement.notes)
        assertThat(actualElement.title).isEqualTo(expectedElement.title)
        assertThat(actualElement.stages).isEqualTo(expectedElement.stages)
    }
}

// Enables or disables animations in a test
fun toggleAnimation(isEnabled: Boolean) {
    with(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())) {
        executeShellCommand(
            "settings put global transition_animation_scale ${if (isEnabled) 1 else 0}")
        executeShellCommand("settings put global window_animation_scale ${if (isEnabled) 1 else 0}")
        executeShellCommand(
            "settings put global animator_duration_scale ${if (isEnabled) 1 else 0}")
    }
}

// region apps

const val TEST_APP_PACKAGE_NAME = "android.healthconnect.controller.test.app"
const val TEST_APP_PACKAGE_NAME_2 = "android.healthconnect.controller.test.app2"
const val TEST_APP_PACKAGE_NAME_3 = "package.name.3"
const val UNSUPPORTED_TEST_APP_PACKAGE_NAME = "android.healthconnect.controller.test.app3"
const val OLD_PERMISSIONS_TEST_APP_PACKAGE_NAME = "android.healthconnect.controller.test.app4"
const val TEST_APP_NAME = "Health Connect test app"
const val TEST_APP_NAME_2 = "Health Connect test app 2"
const val TEST_APP_NAME_3 = "Health Connect test app 3"
const val OLD_APP_NAME = "Old permissions test app"

val TEST_APP =
    AppMetadata(packageName = TEST_APP_PACKAGE_NAME, appName = TEST_APP_NAME, icon = null)
val TEST_APP_2 =
    AppMetadata(packageName = TEST_APP_PACKAGE_NAME_2, appName = TEST_APP_NAME_2, icon = null)
val TEST_APP_3 =
    AppMetadata(packageName = TEST_APP_PACKAGE_NAME_3, appName = TEST_APP_NAME_3, icon = null)
val OLD_TEST_APP =
    AppMetadata(
        packageName = OLD_PERMISSIONS_TEST_APP_PACKAGE_NAME, appName = OLD_APP_NAME, icon = null)
// endregion
