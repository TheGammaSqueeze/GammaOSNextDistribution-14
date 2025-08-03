/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car;

import static com.google.common.truth.Truth.assertThat;

import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.CarPropertyManager;
import android.os.Parcel;
import android.os.Parcelable;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/** Unit tests for {@link android.car.hardware.property.CarPropertyEvent} */
@RunWith(MockitoJUnitRunner.class)
public final class CarPropertyEventTest {
    private static final int PROPERTY_ID = 1234;
    private static final int AREA_ID = 5678;
    private static final long TIMESTAMP_NANOS = 9294;
    private static final Float VALUE = 12.0F;
    private static final CarPropertyValue<Float> CAR_PROPERTY_VALUE = new CarPropertyValue<>(
            PROPERTY_ID, AREA_ID, TIMESTAMP_NANOS, VALUE);
    private static final int EVENT_TYPE = CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE;
    private static final int ERROR_CODE =
            CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_INVALID_ARG;
    private static final CarPropertyEvent CAR_PROPERTY_EVENT = new CarPropertyEvent(EVENT_TYPE,
            CAR_PROPERTY_VALUE, ERROR_CODE);

    private static final int FAKE_PROPERTY_ID = 0x1101111;
    private static final int FAKE_AREA_ID = 0x1;
    private static final int FAKE_PROPERTY_VALUE = 5;
    private final Parcel mParcel = Parcel.obtain();

    @After
    public void tearDown() throws Exception {
        mParcel.recycle();
    }

    private <T extends Parcelable> T readFromParcel() {
        mParcel.setDataPosition(0);
        return mParcel.readParcelable(null);
    }

    private void writeToParcel(Parcelable value) {
        mParcel.writeParcelable(value, 0);
    }

    @Test
    public void testCreateErrorEvent() {
        CarPropertyEvent carPropertyEvent = CarPropertyEvent
                .createErrorEventWithErrorCode(FAKE_PROPERTY_ID, FAKE_AREA_ID,
                        CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_UNKNOWN);

        assertThat(carPropertyEvent.getErrorCode())
                .isEqualTo(CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_UNKNOWN);
        assertThat(carPropertyEvent.getEventType()).isEqualTo(
                CarPropertyEvent.PROPERTY_EVENT_ERROR);
        assertThat(carPropertyEvent.describeContents()).isEqualTo(0);
    }

    @Test
    public void testWriteAndReadEvent() {
        CarPropertyValue<Integer> value = new CarPropertyValue<Integer>(FAKE_PROPERTY_ID,
                FAKE_AREA_ID, FAKE_PROPERTY_VALUE);
        CarPropertyEvent carPropertyEvent = new CarPropertyEvent(
                CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE, value);

        writeToParcel(carPropertyEvent);
        CarPropertyEvent eventReadFromParcel = readFromParcel();

        assertThat(eventReadFromParcel.getCarPropertyValue().getAreaId())
                .isEqualTo(FAKE_AREA_ID);
        assertThat(eventReadFromParcel.getCarPropertyValue().getPropertyId())
                .isEqualTo(FAKE_PROPERTY_ID);
        assertThat(eventReadFromParcel.getCarPropertyValue().getValue())
                .isEqualTo(FAKE_PROPERTY_VALUE);
        assertThat(eventReadFromParcel.getEventType())
                .isEqualTo(CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE);
    }

    @Test
    public void equals_returnsTrueForSameObject() {
        assertThat(CAR_PROPERTY_EVENT.equals(CAR_PROPERTY_EVENT)).isTrue();
    }

    @Test
    public void equals_returnsFalseForNull() {
        assertThat(CAR_PROPERTY_EVENT.equals(null)).isFalse();
    }

    @Test
    public void equals_returnsFalseForNonCarPropertyEvent() {
        assertThat(CAR_PROPERTY_EVENT.equals(new Object())).isFalse();
    }

    @Test
    public void equals_returnsFalseForDifferentEventTypes() {
        int differentEventType = CarPropertyEvent.PROPERTY_EVENT_ERROR;
        assertThat(CAR_PROPERTY_EVENT.equals(
                new CarPropertyEvent(differentEventType, CAR_PROPERTY_VALUE,
                        ERROR_CODE))).isFalse();
    }

    @Test
    public void equals_returnsFalseForDifferentCarPropertyValues() {
        CarPropertyValue<Integer> differentCarPropertyValue = new CarPropertyValue<>(PROPERTY_ID,
                AREA_ID, 893);
        assertThat(CAR_PROPERTY_EVENT.equals(
                new CarPropertyEvent(EVENT_TYPE, differentCarPropertyValue, ERROR_CODE))).isFalse();
    }

    @Test
    public void equals_returnsFalseForDifferentErrorCodes() {
        int differentErrorCode = CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_UNKNOWN;
        assertThat(CAR_PROPERTY_EVENT.equals(new CarPropertyEvent(EVENT_TYPE, CAR_PROPERTY_VALUE,
                differentErrorCode))).isFalse();
    }

    @Test
    public void equals_returnsTrueWhenEqual() {
        assertThat(CAR_PROPERTY_EVENT.equals(
                new CarPropertyEvent(EVENT_TYPE, CAR_PROPERTY_VALUE, ERROR_CODE))).isTrue();
    }

    @Test
    public void hashCode_returnsSameValueForSameInstance() {
        assertThat(CAR_PROPERTY_EVENT.hashCode()).isEqualTo(CAR_PROPERTY_EVENT.hashCode());
    }

    @Test
    public void hashCode_returnsDifferentValueForDifferentCarPropertyEvent() {
        int differentErrorCode = CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_UNKNOWN;
        assertThat(CAR_PROPERTY_EVENT.hashCode()).isNotEqualTo(
                new CarPropertyEvent(EVENT_TYPE, CAR_PROPERTY_VALUE,
                        differentErrorCode).hashCode());
    }
}
