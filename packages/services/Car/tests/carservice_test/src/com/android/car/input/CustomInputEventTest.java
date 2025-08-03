/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.input;

import static com.google.common.truth.Truth.assertThat;

import android.car.CarOccupantZoneManager;
import android.car.input.CustomInputEvent;
import android.os.Parcel;

import org.junit.Test;

public final class CustomInputEventTest {

    @Test
    public void testCustomInputEventWriteAndReadParcel() {
        CustomInputEvent original = new CustomInputEvent(
                /* inputCode= */ CustomInputEvent.INPUT_CODE_F1,
                /* targetDisplayType= */ CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER,
                /* repeatCounter= */ 3);

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        CustomInputEvent actual = CustomInputEvent.CREATOR.createFromParcel(parcel);
        assertThat(actual).isEqualTo(original);

        // Check generated getters
        assertThat(actual.getInputCode()).isEqualTo(CustomInputEvent.INPUT_CODE_F1);
        assertThat(actual.getRepeatCounter()).isEqualTo(3);
        assertThat(actual.getTargetDisplayType()).isEqualTo(
                CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER);
    }

    @Test
    public void testInputCodeToString() {
        assertThat(CustomInputEvent.inputCodeToString(CustomInputEvent.INPUT_CODE_F1)).isEqualTo(
                Integer.toString(CustomInputEvent.INPUT_CODE_F1));
    }

    @Test
    public void testCustomInputEventParcelNewArray() {
        CustomInputEvent[] eventArray = CustomInputEvent.CREATOR.newArray(10);
        assertThat(eventArray).hasLength(10);
    }
}
