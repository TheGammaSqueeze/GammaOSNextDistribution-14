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

import android.hardware.input.VirtualMouseButtonEvent;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class VirtualMouseButtonEventTest {

    @Test
    public void parcelAndUnparcel_matches() {
        final VirtualMouseButtonEvent originalEvent = new VirtualMouseButtonEvent.Builder()
                .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_PRESS)
                .setButtonCode(VirtualMouseButtonEvent.BUTTON_PRIMARY)
                .setEventTimeNanos(5000L)
                .build();
        final Parcel parcel = Parcel.obtain();
        originalEvent.writeToParcel(parcel, /* flags= */ 0);
        parcel.setDataPosition(0);
        final VirtualMouseButtonEvent recreatedEvent =
                VirtualMouseButtonEvent.CREATOR.createFromParcel(parcel);
        assertWithMessage("Recreated event has different action").that(originalEvent.getAction())
                .isEqualTo(recreatedEvent.getAction());
        assertWithMessage("Recreated event has different button code")
                .that(originalEvent.getButtonCode()).isEqualTo(recreatedEvent.getButtonCode());
        assertWithMessage("Recreated event has different event time")
                .that(originalEvent.getEventTimeNanos())
                .isEqualTo(recreatedEvent.getEventTimeNanos());
    }

    @Test
    public void buttonEvent_emptyBuilder_throwsIae() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualMouseButtonEvent.Builder().build());
    }

    @Test
    public void buttonEvent_noButtonCode_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualMouseButtonEvent.Builder()
                .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_RELEASE).build());
    }

    @Test
    public void buttonEvent_noAction_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualMouseButtonEvent.Builder()
                .setButtonCode(VirtualMouseButtonEvent.BUTTON_BACK).build());
    }

    @Test
    public void buttonEvent_invalidEventTime_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualMouseButtonEvent.Builder()
                .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_RELEASE)
                .setButtonCode(VirtualMouseButtonEvent.BUTTON_BACK)
                .setEventTimeNanos(-10L)
                .build());
    }

    @Test
    public void buttonEvent_valid_created() {
        final long eventTimeNanos = 5000L;
        final VirtualMouseButtonEvent event = new VirtualMouseButtonEvent.Builder()
                .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_PRESS)
                .setButtonCode(VirtualMouseButtonEvent.BUTTON_BACK)
                .setEventTimeNanos(eventTimeNanos)
                .build();
        assertWithMessage("Incorrect button code").that(event.getButtonCode()).isEqualTo(
                VirtualMouseButtonEvent.BUTTON_BACK);
        assertWithMessage("Incorrect action").that(event.getAction()).isEqualTo(
                VirtualMouseButtonEvent.ACTION_BUTTON_PRESS);
        assertWithMessage("Incorrect event time").that(event.getEventTimeNanos())
                .isEqualTo(eventTimeNanos);
    }
}
