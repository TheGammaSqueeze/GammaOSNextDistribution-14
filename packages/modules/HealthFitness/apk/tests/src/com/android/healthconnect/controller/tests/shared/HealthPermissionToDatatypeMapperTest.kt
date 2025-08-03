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
package com.android.healthconnect.controller.tests.shared

import android.health.connect.datatypes.ActiveCaloriesBurnedRecord
import android.health.connect.datatypes.BasalMetabolicRateRecord
import android.health.connect.datatypes.HeartRateRecord
import android.health.connect.datatypes.SpeedRecord
import android.health.connect.datatypes.StepsCadenceRecord
import android.health.connect.datatypes.StepsRecord
import com.android.healthconnect.controller.permissions.data.HealthPermissionType.ACTIVE_CALORIES_BURNED
import com.android.healthconnect.controller.permissions.data.HealthPermissionType.BASAL_METABOLIC_RATE
import com.android.healthconnect.controller.permissions.data.HealthPermissionType.HEART_RATE
import com.android.healthconnect.controller.permissions.data.HealthPermissionType.SPEED
import com.android.healthconnect.controller.permissions.data.HealthPermissionType.STEPS
import com.android.healthconnect.controller.shared.HealthPermissionToDatatypeMapper.getDataTypes
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HealthPermissionToDatatypeMapperTest {

    @Test
    fun getDataTypes_steps_returnsCorrectRecords() {
        assertThat(getDataTypes(STEPS))
            .containsExactly(StepsRecord::class.java, StepsCadenceRecord::class.java)
    }

    @Test
    fun getDataTypes_heartRate_returnsCorrectRecords() {
        assertThat(getDataTypes(HEART_RATE)).containsExactly(HeartRateRecord::class.java)
    }

    @Test
    fun getDataTypes_basalMetabolicRate_returnsCorrectRecords() {
        assertThat(getDataTypes(BASAL_METABOLIC_RATE))
            .containsExactly(BasalMetabolicRateRecord::class.java)
    }

    @Test
    fun getDataTypes_speed_returnsCorrectRecords() {
        assertThat(getDataTypes(SPEED)).containsExactly(SpeedRecord::class.java)
    }

    @Test
    fun getDataTypes_activeCaloriesBurned_returnsCorrectRecords() {
        assertThat(getDataTypes(ACTIVE_CALORIES_BURNED))
            .containsExactly(ActiveCaloriesBurnedRecord::class.java)
    }
}
