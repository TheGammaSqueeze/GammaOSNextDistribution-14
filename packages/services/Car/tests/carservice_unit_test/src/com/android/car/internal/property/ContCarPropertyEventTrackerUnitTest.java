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

package com.android.car.internal.property;

import static com.google.common.truth.Truth.assertThat;

import android.car.hardware.CarPropertyValue;

import org.junit.Before;
import org.junit.Test;

import java.time.Duration;

public final class ContCarPropertyEventTrackerUnitTest {
    private static final int FIRST_PROPERTY_ID = 1234;
    private static final int AREA_ID_1 = 908;
    private static final float FIRST_UPDATE_RATE_HZ = 1F;
    private static final float RESOLUTION_INTEGER = 10.0F;
    private static final float RESOLUTION_FLOAT = 0.01F;
    private static final long TIMESTAMP_NANOS = Duration.ofSeconds(1).toNanos();
    private static final long FRESH_TIMESTAMP_NANOS = Duration.ofSeconds(2).toNanos();
    private static final long TOO_EARLY_TIMESTAMP_NANOS = Duration.ofMillis(1899).toNanos();
    private static final long ALMOST_FRESH_TIMESTAMP_NANOS = Duration.ofMillis(1999).toNanos();
    private static final long STALE_TIMESTAMP_NANOS = Duration.ofMillis(500).toNanos();
    private static final Integer INTEGER_VALUE_1 = 8438;
    private static final Integer INTEGER_VALUE_2 = 9134;
    private static final Integer SANITIZED_INTEGER_VALUE_1 = 8440;
    private static final Integer SIMILAR_TO_INTEGER_VALUE_1 = 8442;
    private static final Long LONG_VALUE_1 = 9274L;
    private static final Long SANITIZED_LONG_VALUE_1 = 9270L;
    private static final Float FLOAT_VALUE_1 = 89.3787F;
    private static final Float SANITIZED_FLOAT_VALUE_1 = 89.38F;
    private static final Integer[] INTEGER_ARRAY_VALUES_1 = {8438, 3864, 5093};
    private static final Integer[] SANITIZED_INTEGER_ARRAY_VALUES_1 = {8440, 3860, 5090};
    private static final Long[] LONG_ARRAY_VALUES_1 = {9274L, 4859L, 3958L};
    private static final Long[] SANITIZED_LONG_ARRAY_VALUES_1 = {9270L, 4860L, 3960L};
    private static final Float[] FLOAT_ARRAY_VALUES_1 = {89.3787F, 36.7253F, 34.2984F};
    private static final Float[] SANITIZED_FLOAT_ARRAY_VALUES_1 = {89.38F, 36.73F, 34.30F};
    private static final CarPropertyValue<Integer> GOOD_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, TIMESTAMP_NANOS, INTEGER_VALUE_1);
    private static final CarPropertyValue<Integer> SANITIZED_GOOD_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, TIMESTAMP_NANOS,
                    SANITIZED_INTEGER_VALUE_1);
    private static final CarPropertyValue<Long> GOOD_LONG_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, TIMESTAMP_NANOS, LONG_VALUE_1);
    private static final CarPropertyValue<Long> SANITIZED_GOOD_LONG_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, TIMESTAMP_NANOS,
                    SANITIZED_LONG_VALUE_1);
    private static final CarPropertyValue<Float> GOOD_FLOAT_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, TIMESTAMP_NANOS, FLOAT_VALUE_1);
    private static final CarPropertyValue<Float> SANITIZED_GOOD_FLOAT_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, TIMESTAMP_NANOS,
                    SANITIZED_FLOAT_VALUE_1);
    private static final CarPropertyValue<Integer[]> GOOD_INTEGER_ARRAY_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, TIMESTAMP_NANOS,
                    INTEGER_ARRAY_VALUES_1);
    private static final CarPropertyValue<Integer[]>
            SANITIZED_GOOD_INTEGER_ARRAY_CAR_PROPERTY_VALUE = new CarPropertyValue<>(
                    FIRST_PROPERTY_ID, AREA_ID_1, TIMESTAMP_NANOS,
                    SANITIZED_INTEGER_ARRAY_VALUES_1);
    private static final CarPropertyValue<Long[]> GOOD_LONG_ARRAY_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, TIMESTAMP_NANOS,
                    LONG_ARRAY_VALUES_1);
    private static final CarPropertyValue<Long[]> SANITIZED_GOOD_LONG_ARRAY_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, TIMESTAMP_NANOS,
                    SANITIZED_LONG_ARRAY_VALUES_1);
    private static final CarPropertyValue<Float[]> GOOD_FLOAT_ARRAY_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, TIMESTAMP_NANOS,
                    FLOAT_ARRAY_VALUES_1);
    private static final CarPropertyValue<Float[]> SANITIZED_GOOD_FLOAT_ARRAY_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, TIMESTAMP_NANOS,
                    SANITIZED_FLOAT_ARRAY_VALUES_1);
    private static final CarPropertyValue<Integer> SIMILAR_GOOD_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, TIMESTAMP_NANOS,
                    SIMILAR_TO_INTEGER_VALUE_1);
    private static final CarPropertyValue<Integer> FRESH_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, FRESH_TIMESTAMP_NANOS,
                    INTEGER_VALUE_1);
    private static final CarPropertyValue<Integer> FRESH_CAR_PROPERTY_VALUE_VALUE_2 =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, FRESH_TIMESTAMP_NANOS,
                    INTEGER_VALUE_2);
    private static final CarPropertyValue<Integer> TOO_EARLY_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, TOO_EARLY_TIMESTAMP_NANOS,
                    INTEGER_VALUE_1);
    private static final CarPropertyValue<Integer> ALMOST_FRESH_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, ALMOST_FRESH_TIMESTAMP_NANOS,
                    INTEGER_VALUE_1);
    private static final CarPropertyValue<Integer> STALE_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, STALE_TIMESTAMP_NANOS,
                    INTEGER_VALUE_1);
    private ContCarPropertyEventTracker mNoVurTracker;
    private ContCarPropertyEventTracker mVurTracker;
    private ContCarPropertyEventTracker mVurWithResolutionIntegerTracker;
    private ContCarPropertyEventTracker mVurWithResolutionFloatTracker;

    @Before
    public void setup() {
        mNoVurTracker = new ContCarPropertyEventTracker(
                /* useSystemLogger= */ false, FIRST_UPDATE_RATE_HZ, /* enableVur= */ false,
                /* resolution */ 0.0f);
        mVurTracker = new ContCarPropertyEventTracker(
                /* useSystemLogger= */ false, FIRST_UPDATE_RATE_HZ, /* enableVur= */ true,
                /* resolution */ 0.0f);
        mVurWithResolutionIntegerTracker = new ContCarPropertyEventTracker(
                /* useSystemLogger= */ false, FIRST_UPDATE_RATE_HZ, /* enableVur= */ true,
                RESOLUTION_INTEGER);
        mVurWithResolutionFloatTracker = new ContCarPropertyEventTracker(
                /* useSystemLogger= */ false, FIRST_UPDATE_RATE_HZ, /* enableVur= */ true,
                RESOLUTION_FLOAT);
    }

    @Test
    public void testHasUpdate_returnsTrueForFirstEvent() {
        assertThat(mNoVurTracker.hasUpdate(GOOD_CAR_PROPERTY_VALUE)).isTrue();
    }

    @Test
    public void testHasUpdate_returnsTrueForFirstEvent_vur() {
        assertThat(mVurTracker.hasUpdate(GOOD_CAR_PROPERTY_VALUE)).isTrue();
    }

    @Test
    public void testHasUpdate_returnsFalseForStaleEvent() {
        assertThat(mNoVurTracker.hasUpdate(GOOD_CAR_PROPERTY_VALUE)).isTrue();
        assertThat(mNoVurTracker.hasUpdate(STALE_CAR_PROPERTY_VALUE)).isFalse();
    }

    @Test
    public void testHasUpdate_returnsFalseForStaleEvent_vur() {
        assertThat(mVurTracker.hasUpdate(GOOD_CAR_PROPERTY_VALUE)).isTrue();
        assertThat(mVurTracker.hasUpdate(STALE_CAR_PROPERTY_VALUE)).isFalse();
    }

    @Test
    public void testHasUpdate_returnsFalseForEventArrivedTooEarly() {
        assertThat(mNoVurTracker.hasUpdate(GOOD_CAR_PROPERTY_VALUE))
                .isTrue();
        assertThat(mNoVurTracker.hasUpdate(TOO_EARLY_CAR_PROPERTY_VALUE)).isFalse();
    }

    @Test
    public void testHasUpdate_returnsFalseForEventArrivedTooEarly_vur() {
        assertThat(mVurTracker.hasUpdate(GOOD_CAR_PROPERTY_VALUE)).isTrue();
        assertThat(mVurTracker.hasUpdate(TOO_EARLY_CAR_PROPERTY_VALUE)).isFalse();
    }

    @Test
    public void testHasUpdate_returnsTrueForEventWithinOffset() {
        assertThat(mNoVurTracker.hasUpdate(GOOD_CAR_PROPERTY_VALUE)).isTrue();
        assertThat(mNoVurTracker.hasUpdate(ALMOST_FRESH_CAR_PROPERTY_VALUE)).isTrue();
    }

    @Test
    public void testHasUpdate_returnsTrueForFreshEvent() {
        assertThat(mNoVurTracker.hasUpdate(GOOD_CAR_PROPERTY_VALUE)).isTrue();
        assertThat(mNoVurTracker.hasUpdate(FRESH_CAR_PROPERTY_VALUE)).isTrue();
    }

    @Test
    public void testHasUpdate_returnsFalseIfValueIsTheSame_vur() {
        assertThat(mVurTracker.hasUpdate(GOOD_CAR_PROPERTY_VALUE)).isTrue();
        // FRESH_CAR_PROPERTY_VALUE has the same value as GOOD_CAR_PROPERTY_VALUE.
        assertThat(mVurTracker.hasUpdate(FRESH_CAR_PROPERTY_VALUE)).isFalse();
    }

    @Test
    public void testHasUpdate_returnsTrueIfValueIsDifferent_vur() {
        assertThat(mVurTracker.hasUpdate(GOOD_CAR_PROPERTY_VALUE)).isTrue();
        // FRESH_CAR_PROPERTY_VALUE has the same value as GOOD_CAR_PROPERTY_VALUE.
        assertThat(mVurTracker.hasUpdate(FRESH_CAR_PROPERTY_VALUE_VALUE_2)).isTrue();
    }

    @Test
    public void testHasUpdate_withResolution_returnsSanitizedIntegerCarPropertyValue() {
        assertThat(mVurWithResolutionIntegerTracker.hasUpdate(GOOD_CAR_PROPERTY_VALUE)).isTrue();
        // mVurWithResolutionTracker should store a sanitized CarPropertyValue.
        assertThat(mVurWithResolutionIntegerTracker.getCurrentCarPropertyValue()).isEqualTo(
                SANITIZED_GOOD_CAR_PROPERTY_VALUE);
    }

    @Test
    public void testHasUpdate_withResolution_returnsSanitizedLongCarPropertyValue() {
        assertThat(mVurWithResolutionIntegerTracker.hasUpdate(GOOD_LONG_CAR_PROPERTY_VALUE))
                .isTrue();
        assertThat(mVurWithResolutionIntegerTracker.getCurrentCarPropertyValue()).isEqualTo(
                SANITIZED_GOOD_LONG_CAR_PROPERTY_VALUE);
    }

    @Test
    public void testHasUpdate_withResolution_returnsSanitizedFloatCarPropertyValue() {
        assertThat(mVurWithResolutionFloatTracker.hasUpdate(GOOD_FLOAT_CAR_PROPERTY_VALUE))
                .isTrue();
        assertThat(mVurWithResolutionFloatTracker.getCurrentCarPropertyValue()).isEqualTo(
                SANITIZED_GOOD_FLOAT_CAR_PROPERTY_VALUE);
    }

    @Test
    public void testHasUpdate_withResolution_returnsSanitizedIntegerArrayCarPropertyValue() {
        assertThat(mVurWithResolutionIntegerTracker.hasUpdate(
                GOOD_INTEGER_ARRAY_CAR_PROPERTY_VALUE)).isTrue();
        assertThat(mVurWithResolutionIntegerTracker.getCurrentCarPropertyValue()).isEqualTo(
                SANITIZED_GOOD_INTEGER_ARRAY_CAR_PROPERTY_VALUE);
    }

    @Test
    public void testHasUpdate_withResolution_returnsSanitizedLongArrayCarPropertyValue() {
        assertThat(mVurWithResolutionIntegerTracker.hasUpdate(GOOD_LONG_ARRAY_CAR_PROPERTY_VALUE))
                .isTrue();
        assertThat(mVurWithResolutionIntegerTracker.getCurrentCarPropertyValue()).isEqualTo(
                SANITIZED_GOOD_LONG_ARRAY_CAR_PROPERTY_VALUE);
    }

    @Test
    public void testHasUpdate_withResolution_returnsSanitizedFloatArrayCarPropertyValue() {
        assertThat(mVurWithResolutionFloatTracker.hasUpdate(
                GOOD_FLOAT_ARRAY_CAR_PROPERTY_VALUE)).isTrue();
        assertThat(mVurWithResolutionFloatTracker.getCurrentCarPropertyValue().getValue())
                .isEqualTo(SANITIZED_GOOD_FLOAT_ARRAY_CAR_PROPERTY_VALUE.getValue());
    }

    @Test
    public void testHasUpdate_eithResolution_returnsFalseIfSimilarValueIsUnderResolution() {
        assertThat(mVurTracker.hasUpdate(GOOD_CAR_PROPERTY_VALUE)).isTrue();
        // SIMILAR_GOOD_CAR_PROPERTY_VALUE has a similar value to GOOD_CAR_PROPERTY_VALUE that
        // round to the same value when rounded by the given resolution. So the second value
        // should not show an update.
        assertThat(mVurTracker.hasUpdate(SIMILAR_GOOD_CAR_PROPERTY_VALUE)).isFalse();
    }
}
