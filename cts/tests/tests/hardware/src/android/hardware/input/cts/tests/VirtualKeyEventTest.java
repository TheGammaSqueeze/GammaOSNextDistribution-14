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

import android.hardware.input.VirtualKeyEvent;
import android.os.Parcel;
import android.view.KeyEvent;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class VirtualKeyEventTest {

    @Test
    public void parcelAndUnparcel_matches() {
        final VirtualKeyEvent originalEvent = new VirtualKeyEvent.Builder()
                .setAction(VirtualKeyEvent.ACTION_DOWN)
                .setKeyCode(KeyEvent.KEYCODE_ENTER)
                .setEventTimeNanos(5000L)
                .build();
        final Parcel parcel = Parcel.obtain();
        originalEvent.writeToParcel(parcel, /* flags= */ 0);
        parcel.setDataPosition(0);
        final VirtualKeyEvent recreatedEvent =
                VirtualKeyEvent.CREATOR.createFromParcel(parcel);
        assertWithMessage("Recreated event has different action").that(originalEvent.getAction())
                .isEqualTo(recreatedEvent.getAction());
        assertWithMessage("Recreated event has different key code").that(originalEvent.getKeyCode())
                .isEqualTo(recreatedEvent.getKeyCode());
        assertWithMessage("Recreated event has different event time")
                .that(originalEvent.getEventTimeNanos())
                .isEqualTo(recreatedEvent.getEventTimeNanos());
    }

    @Test
    public void keyEvent_emptyBuilder_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualKeyEvent.Builder().build());
    }

    @Test
    public void keyEvent_noKeyCode_throwsIae() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualKeyEvent.Builder().setAction(VirtualKeyEvent.ACTION_DOWN).build());
    }

    @Test
    public void keyEvent_noAction_throwsIae() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualKeyEvent.Builder().setKeyCode(KeyEvent.KEYCODE_A).build());
    }

    @Test
    public void keyEvent_invalidEventTime_throwsIae() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualKeyEvent.Builder()
                        .setAction(VirtualKeyEvent.ACTION_DOWN)
                        .setKeyCode(KeyEvent.KEYCODE_A)
                        .setEventTimeNanos(-3L)
                        .build());
    }

    @Test
    public void keyEvent_valid_created() {
        final long eventTimeNanos = 5000L;
        final VirtualKeyEvent event = new VirtualKeyEvent.Builder()
                .setAction(VirtualKeyEvent.ACTION_DOWN)
                .setKeyCode(KeyEvent.KEYCODE_A)
                .setEventTimeNanos(eventTimeNanos)
                .build();
        assertWithMessage("Incorrect key code").that(event.getKeyCode()).isEqualTo(
                KeyEvent.KEYCODE_A);
        assertWithMessage("Incorrect action").that(event.getAction()).isEqualTo(
                VirtualKeyEvent.ACTION_DOWN);
        assertWithMessage("Incorrect event time").that(event.getEventTimeNanos())
                .isEqualTo(eventTimeNanos);
    }
}
