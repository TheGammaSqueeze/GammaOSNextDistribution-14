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

import android.car.input.CarInputManager;
import android.car.input.RotaryEvent;
import android.os.Parcel;

import org.junit.Test;

public final class RotaryEventTest {

    @Test
    public void testRotaryEventWriteAndReadParcel() {
        long[] uptimeMillisForClicks = new long[]{12345L};
        RotaryEvent original = new RotaryEvent(
                /* inputType= */ CarInputManager.INPUT_TYPE_ROTARY_VOLUME,
                /* clockwise= */ true,
                uptimeMillisForClicks);

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        RotaryEvent actual = RotaryEvent.CREATOR.createFromParcel(parcel);
        assertThat(actual).isEqualTo(original);

        // Check generated getters
        assertThat(actual.getUptimeMillisForClick(0)).isEqualTo(12345L);
        assertThat(actual.getUptimeMillisForClicks()).isEqualTo(uptimeMillisForClicks);
        assertThat(actual.getNumberOfClicks()).isEqualTo(1);
        assertThat(actual.getInputType()).isEqualTo(CarInputManager.INPUT_TYPE_ROTARY_VOLUME);
        assertThat(actual.isClockwise()).isEqualTo(true);
    }

    @Test
    public void testRotaryEventParcelNewArray() {
        RotaryEvent[] eventArray = RotaryEvent.CREATOR.newArray(10);
        assertThat(eventArray).hasLength(10);
    }
}
