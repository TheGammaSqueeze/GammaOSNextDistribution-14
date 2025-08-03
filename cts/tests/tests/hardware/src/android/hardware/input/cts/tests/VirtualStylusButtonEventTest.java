/*
 * Copyright 2023 The Android Open Source Project
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

import android.companion.virtual.flags.Flags;
import android.hardware.input.VirtualStylusButtonEvent;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_STYLUS)
@RunWith(AndroidJUnit4.class)
public class VirtualStylusButtonEventTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void parcelAndUnparcel_matches() {
        final VirtualStylusButtonEvent originalEvent = new VirtualStylusButtonEvent.Builder()
                .setAction(VirtualStylusButtonEvent.ACTION_BUTTON_PRESS)
                .setButtonCode(VirtualStylusButtonEvent.BUTTON_PRIMARY)
                .setEventTimeNanos(5000L)
                .build();

        final Parcel parcel = Parcel.obtain();
        originalEvent.writeToParcel(parcel, /* flags= */ 0);
        parcel.setDataPosition(0);
        final VirtualStylusButtonEvent recreatedEvent =
                VirtualStylusButtonEvent.CREATOR.createFromParcel(parcel);

        assertWithMessage("Recreated event has different action").that(originalEvent.getAction())
                .isEqualTo(recreatedEvent.getAction());
        assertWithMessage("Recreated event has different button code")
                .that(originalEvent.getButtonCode()).isEqualTo(recreatedEvent.getButtonCode());
        assertWithMessage("Recreated event has different event time")
                .that(originalEvent.getEventTimeNanos())
                .isEqualTo(recreatedEvent.getEventTimeNanos());
    }

    @Test
    public void stylusButtonEvent_emptyBuilder_throwsIae() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualStylusButtonEvent.Builder().build());
    }

    @Test
    public void stylusButtonEvent_noButtonCode_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualStylusButtonEvent.Builder()
                .setAction(VirtualStylusButtonEvent.ACTION_BUTTON_RELEASE).build());
    }

    @Test
    public void stylusButtonEvent_noAction_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualStylusButtonEvent.Builder()
                .setButtonCode(VirtualStylusButtonEvent.BUTTON_SECONDARY).build());
    }

    @Test
    public void stylusButtonEvent_invalidEventTime_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualStylusButtonEvent.Builder()
                .setAction(VirtualStylusButtonEvent.ACTION_BUTTON_RELEASE)
                .setButtonCode(VirtualStylusButtonEvent.BUTTON_PRIMARY)
                .setEventTimeNanos(-10L)
                .build());
    }

    @Test
    public void stylusButtonEvent_valid_created() {
        final long eventTimeNanos = 5000L;
        final VirtualStylusButtonEvent event = new VirtualStylusButtonEvent.Builder()
                .setAction(VirtualStylusButtonEvent.ACTION_BUTTON_PRESS)
                .setButtonCode(VirtualStylusButtonEvent.BUTTON_PRIMARY)
                .setEventTimeNanos(eventTimeNanos)
                .build();
        assertWithMessage("Incorrect button code").that(event.getButtonCode()).isEqualTo(
                VirtualStylusButtonEvent.BUTTON_PRIMARY);
        assertWithMessage("Incorrect action").that(event.getAction()).isEqualTo(
                VirtualStylusButtonEvent.ACTION_BUTTON_PRESS);
        assertWithMessage("Incorrect event time").that(event.getEventTimeNanos())
                .isEqualTo(eventTimeNanos);
    }
}
