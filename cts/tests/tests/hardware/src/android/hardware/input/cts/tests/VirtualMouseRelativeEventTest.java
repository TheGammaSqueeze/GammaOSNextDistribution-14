/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.hardware.input.cts.tests;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.hardware.input.VirtualMouseRelativeEvent;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class VirtualMouseRelativeEventTest {

    @Test
    public void parcelAndUnparcel_matches() {
        final float x = 100f;
        final float y = 4f;
        final long eventTimeNanos = 5000L;
        final VirtualMouseRelativeEvent originalEvent = new VirtualMouseRelativeEvent.Builder()
                .setRelativeX(x)
                .setRelativeY(y)
                .setEventTimeNanos(eventTimeNanos)
                .build();
        final Parcel parcel = Parcel.obtain();
        originalEvent.writeToParcel(parcel, /* flags= */ 0);
        parcel.setDataPosition(0);
        final VirtualMouseRelativeEvent recreatedEvent =
                VirtualMouseRelativeEvent.CREATOR.createFromParcel(parcel);
        assertWithMessage("Recreated event has different x").that(originalEvent.getRelativeX())
                .isEqualTo(recreatedEvent.getRelativeX());
        assertWithMessage("Recreated event has different y")
                .that(originalEvent.getRelativeY()).isEqualTo(recreatedEvent.getRelativeY());
        assertWithMessage("Recreated event has different event time")
                .that(originalEvent.getEventTimeNanos())
                .isEqualTo(recreatedEvent.getEventTimeNanos());
    }

    @Test
    public void relativeEvent_invalidEventTime_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualMouseRelativeEvent.Builder()
                .setRelativeX(50f)
                .setRelativeY(10f)
                .setEventTimeNanos(-10L)
                .build());
    }

    @Test
    public void relativeEvent_valid_created() {
        final float x = -50f;
        final float y = 83f;
        final long eventTimeNanos = 5000L;
        final VirtualMouseRelativeEvent event = new VirtualMouseRelativeEvent.Builder()
                .setRelativeX(x)
                .setRelativeY(y)
                .setEventTimeNanos(eventTimeNanos)
                .build();
        assertWithMessage("Incorrect x value").that(event.getRelativeX()).isEqualTo(x);
        assertWithMessage("Incorrect y value").that(event.getRelativeY()).isEqualTo(y);
        assertWithMessage("Incorrect event time").that(event.getEventTimeNanos())
                .isEqualTo(eventTimeNanos);
    }
}
