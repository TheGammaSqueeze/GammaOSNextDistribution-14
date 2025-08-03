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

import android.hardware.input.VirtualMouseScrollEvent;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class VirtualMouseScrollEventTest {

    @Test
    public void parcelAndUnparcel_matches() {
        final float x = 0.5f;
        final float y = -0.2f;
        final long eventTimeNanos = 5000L;
        final VirtualMouseScrollEvent originalEvent = new VirtualMouseScrollEvent.Builder()
                .setXAxisMovement(x)
                .setYAxisMovement(y)
                .setEventTimeNanos(eventTimeNanos)
                .build();
        final Parcel parcel = Parcel.obtain();
        originalEvent.writeToParcel(parcel, /* flags= */ 0);
        parcel.setDataPosition(0);
        final VirtualMouseScrollEvent recreatedEvent =
                VirtualMouseScrollEvent.CREATOR.createFromParcel(parcel);
        assertWithMessage("Recreated event has different x").that(originalEvent.getXAxisMovement())
                .isEqualTo(recreatedEvent.getXAxisMovement());
        assertWithMessage("Recreated event has different y")
                .that(originalEvent.getYAxisMovement())
                .isEqualTo(recreatedEvent.getYAxisMovement());
        assertWithMessage("Recreated event has different event time")
                .that(originalEvent.getEventTimeNanos())
                .isEqualTo(recreatedEvent.getEventTimeNanos());
    }

    @Test
    public void scrollEvent_xOutOfRange_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualMouseScrollEvent.Builder()
                .setXAxisMovement(1.5f)
                .setYAxisMovement(1.0f));
    }

    @Test
    public void scrollEvent_yOutOfRange_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualMouseScrollEvent.Builder()
                .setXAxisMovement(0.5f)
                .setYAxisMovement(1.1f));
    }

    @Test
    public void scrollEvent_invalidEventTime_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualMouseScrollEvent.Builder()
                .setXAxisMovement(-1f)
                .setYAxisMovement(1f)
                .setEventTimeNanos(-10L));
    }

    @Test
    public void scrollEvent_valid_created() {
        final float x = -1f;
        final float y = 1f;
        final long eventTimeNanos = 5000L;
        final VirtualMouseScrollEvent event = new VirtualMouseScrollEvent.Builder()
                .setXAxisMovement(x)
                .setYAxisMovement(y)
                .setEventTimeNanos(eventTimeNanos)
                .build();
        assertWithMessage("Incorrect x value").that(event.getXAxisMovement()).isEqualTo(x);
        assertWithMessage("Incorrect y value").that(event.getYAxisMovement()).isEqualTo(y);
        assertWithMessage("Incorrect event time").that(event.getEventTimeNanos())
                .isEqualTo(eventTimeNanos);
    }
}
