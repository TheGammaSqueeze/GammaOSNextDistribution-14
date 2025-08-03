/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.federatedcompute.services.training.util;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import android.os.PowerManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.federatedcompute.services.common.BatteryInfo;
import com.android.federatedcompute.services.common.Clock;
import com.android.federatedcompute.services.common.Flags;
import com.android.federatedcompute.services.data.fbs.TrainingConstraints;
import com.android.federatedcompute.services.training.util.TrainingConditionsChecker.Condition;

import com.google.flatbuffers.FlatBufferBuilder;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.ByteBuffer;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public final class TrainingConditionsCheckerTest {
    private static final TrainingConstraints DEFAULT_TRAINING_CONSTRAINTS =
            createDefaultTrainingConstraints();

    private TrainingConditionsChecker mTrainingConditionsChecker;
    @Mock private PowerManager mPowerManager;
    @Mock private BatteryInfo mBatteryInfo;
    @Mock private Clock mClock;
    @Mock private Flags mFlags;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mFlags.getThermalStatusToThrottle()).thenReturn(PowerManager.THERMAL_STATUS_MODERATE);
        when(mFlags.getTrainingConditionCheckThrottlePeriodMillis()).thenReturn(1000L);
        mTrainingConditionsChecker =
                new TrainingConditionsChecker(mBatteryInfo, mPowerManager, mFlags, mClock);
    }

    @Test
    public void testCheckAllConditionsForFlTraining_allConditionsOk() {
        when(mClock.currentTimeMillis()).thenReturn(1000L);
        when(mBatteryInfo.batteryOkForTraining(anyBoolean())).thenReturn(true);
        when(mPowerManager.getCurrentThermalStatus()).thenReturn(PowerManager.THERMAL_STATUS_LIGHT);

        assertThat(
                        mTrainingConditionsChecker.checkAllConditionsForFlTraining(
                                DEFAULT_TRAINING_CONSTRAINTS))
                .isEmpty();
    }

    @Test
    public void testCheckAllConditionsForFlTraining_allTrainingConditionsNotOk() {
        when(mClock.currentTimeMillis()).thenReturn(1000L);
        when(mBatteryInfo.batteryOkForTraining(anyBoolean())).thenReturn(false);
        when(mPowerManager.getCurrentThermalStatus())
                .thenReturn(PowerManager.THERMAL_STATUS_SEVERE);

        Set<Condition> conditions =
                mTrainingConditionsChecker.checkAllConditionsForFlTraining(
                        DEFAULT_TRAINING_CONSTRAINTS);
        assertThat(conditions).containsAtLeast(Condition.BATTERY_NOT_OK, Condition.THERMALS_NOT_OK);
    }

    @Test
    public void testCheckAllConditionsForFlTraining_batteryNotOk() {
        when(mClock.currentTimeMillis()).thenReturn(1000L);
        when(mBatteryInfo.batteryOkForTraining(anyBoolean())).thenReturn(false);
        when(mPowerManager.getCurrentThermalStatus()).thenReturn(PowerManager.THERMAL_STATUS_LIGHT);

        assertThat(
                        mTrainingConditionsChecker.checkAllConditionsForFlTraining(
                                DEFAULT_TRAINING_CONSTRAINTS))
                .contains(Condition.BATTERY_NOT_OK);
    }

    @Test
    public void testCheckAllFlConditions_throttled() {
        when(mClock.currentTimeMillis()).thenReturn(1000L, 1100L, 2100L);
        when(mBatteryInfo.batteryOkForTraining(anyBoolean())).thenReturn(true);
        when(mPowerManager.getCurrentThermalStatus())
                .thenReturn(PowerManager.THERMAL_STATUS_CRITICAL);

        assertThat(
                        mTrainingConditionsChecker.checkAllConditionsForFlTraining(
                                DEFAULT_TRAINING_CONSTRAINTS))
                .containsExactly(Condition.THERMALS_NOT_OK);

        // Throttled, will not detect thermal state change.
        assertThat(
                        mTrainingConditionsChecker.checkAllConditionsForFlTraining(
                                DEFAULT_TRAINING_CONSTRAINTS))
                .isEmpty();

        // Past throttle duration, check again.
        assertThat(
                        mTrainingConditionsChecker.checkAllConditionsForFlTraining(
                                DEFAULT_TRAINING_CONSTRAINTS))
                .containsExactly(Condition.THERMALS_NOT_OK);
    }

    @Test
    public void testCheckAllConditionsForFlTraining_thermallyThrottled_conditionsReported() {
        when(mClock.currentTimeMillis()).thenReturn(1000L);
        when(mBatteryInfo.batteryOkForTraining(anyBoolean())).thenReturn(true);
        when(mPowerManager.getCurrentThermalStatus())
                .thenReturn(PowerManager.THERMAL_STATUS_CRITICAL);

        assertThat(
                        mTrainingConditionsChecker.checkAllConditionsForFlTraining(
                                DEFAULT_TRAINING_CONSTRAINTS))
                .containsExactly(Condition.THERMALS_NOT_OK);
    }

    @Test
    public void
            testCheckAllConditionsForFlTraining_thermallyThrottledAtThreshold_conditionsReported() {
        when(mClock.currentTimeMillis()).thenReturn(1000L);
        when(mPowerManager.getCurrentThermalStatus())
                .thenReturn(PowerManager.THERMAL_STATUS_MODERATE);
        when(mBatteryInfo.batteryOkForTraining(anyBoolean())).thenReturn(true);

        assertThat(
                        mTrainingConditionsChecker.checkAllConditionsForFlTraining(
                                DEFAULT_TRAINING_CONSTRAINTS))
                .containsExactly(Condition.THERMALS_NOT_OK);
    }

    @Test
    public void testCheckAllConditionsForFlTraining_notThermallyThrottled_conditionsNotReported() {
        when(mClock.currentTimeMillis()).thenReturn(1000L);
        when(mPowerManager.getCurrentThermalStatus()).thenReturn(PowerManager.THERMAL_STATUS_LIGHT);
        when(mBatteryInfo.batteryOkForTraining(anyBoolean())).thenReturn(true);

        assertThat(
                        mTrainingConditionsChecker.checkAllConditionsForFlTraining(
                                DEFAULT_TRAINING_CONSTRAINTS))
                .isEmpty();
    }

    private static TrainingConstraints createDefaultTrainingConstraints() {
        FlatBufferBuilder builder = new FlatBufferBuilder();
        builder.finish(TrainingConstraints.createTrainingConstraints(builder, true, true, true));
        byte[] trainingConstraintsBytes = builder.sizedByteArray();
        return TrainingConstraints.getRootAsTrainingConstraints(
                ByteBuffer.wrap(trainingConstraintsBytes));
    }
}
