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
import android.hardware.input.VirtualStylusMotionEvent;
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
public class VirtualStylusMotionEventTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void parcelAndUnparcel_matches() {
        final int x = 50;
        final int y = 800;
        final int pressure = 10;
        final int tiltX = 10;
        final int tiltY = 20;
        final long eventTimeNanos = 5000L;
        final VirtualStylusMotionEvent originalEvent = new VirtualStylusMotionEvent.Builder()
                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
                .setX(x)
                .setY(y)
                .setPressure(pressure)
                .setTiltX(tiltX)
                .setTiltY(tiltY)
                .setEventTimeNanos(eventTimeNanos)
                .build();

        final Parcel parcel = Parcel.obtain();
        originalEvent.writeToParcel(parcel, /* flags= */ 0);
        parcel.setDataPosition(0);
        final VirtualStylusMotionEvent recreatedEvent =
                VirtualStylusMotionEvent.CREATOR.createFromParcel(parcel);

        assertWithMessage("Recreated event has different action").that(originalEvent.getAction())
                .isEqualTo(recreatedEvent.getAction());
        assertWithMessage("Recreated event has different tool type")
                .that(originalEvent.getToolType()).isEqualTo(recreatedEvent.getToolType());
        assertWithMessage("Recreated event has different x").that(originalEvent.getX())
                .isEqualTo(recreatedEvent.getX());
        assertWithMessage("Recreated event has different y").that(originalEvent.getY())
                .isEqualTo(recreatedEvent.getY());
        assertWithMessage("Recreated event has different x-axis tilt")
                .that(originalEvent.getTiltX()).isEqualTo(recreatedEvent.getTiltX());
        assertWithMessage("Recreated event has different y-axis tilt")
                .that(originalEvent.getTiltY()).isEqualTo(recreatedEvent.getTiltY());
        assertWithMessage("Recreated event has different pressure")
                .that(originalEvent.getPressure()).isEqualTo(recreatedEvent.getPressure());
        assertWithMessage("Recreated event has different event time")
                .that(originalEvent.getEventTimeNanos())
                .isEqualTo(recreatedEvent.getEventTimeNanos());
    }

    @Test
    public void stylusMotionEvent_emptyBuilder_throwsIae() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualStylusMotionEvent.Builder().build());
    }

    @Test
    public void stylusMotionEvent_noAction_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualStylusMotionEvent.Builder()
                .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
                .setX(0)
                .setY(1)
                .build());
    }

    @Test
    public void stylusMotionEvent_invalidAction_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualStylusMotionEvent.Builder()
                .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
                .setAction(100)
                .setX(0)
                .setY(1)
                .build());
    }

    @Test
    public void stylusMotionEvent_invalidToolType_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualStylusMotionEvent.Builder()
                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                .setToolType(100)
                .setX(0)
                .setY(1)
                .build());
    }

    @Test
    public void stylusMotionEvent_noX_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualStylusMotionEvent.Builder()
                .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                .setY(5)
                .build());
    }

    @Test
    public void stylusMotionEvent_noY_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualStylusMotionEvent.Builder()
                .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                .setX(5)
                .build());
    }

    @Test
    public void stylusMotionEvent_invalidEventTime_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualStylusMotionEvent.Builder()
                .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                .setX(0)
                .setY(1)
                .setEventTimeNanos(-10L)
                .build());
    }

    @Test
    public void stylusMotionEvent_invalidPressure_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualStylusMotionEvent.Builder()
                .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                .setX(0)
                .setY(1)
                .setPressure(-10)
                .build());
        assertThrows(IllegalArgumentException.class, () -> new VirtualStylusMotionEvent.Builder()
                .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                .setX(0)
                .setY(1)
                .setPressure(300)
                .build());
    }

    @Test
    public void stylusMotionEvent_invalidTiltX_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualStylusMotionEvent.Builder()
                .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                .setX(0)
                .setY(1)
                .setTiltX(-100)
                .build());
        assertThrows(IllegalArgumentException.class, () -> new VirtualStylusMotionEvent.Builder()
                .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                .setX(0)
                .setY(1)
                .setTiltX(100)
                .build());
    }

    @Test
    public void stylusMotionEvent_invalidTiltY_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualStylusMotionEvent.Builder()
                .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                .setX(0)
                .setY(1)
                .setTiltY(-100)
                .build());
        assertThrows(IllegalArgumentException.class, () -> new VirtualStylusMotionEvent.Builder()
                .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                .setX(0)
                .setY(1)
                .setTiltY(100)
                .build());
    }

    @Test
    public void stylusMotionEvent_validWithoutPressureAndTilt_created() {
        final int x = 50;
        final int y = 800;
        final long eventTimeNanos = 5000L;
        final VirtualStylusMotionEvent event = new VirtualStylusMotionEvent.Builder()
                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
                .setX(x)
                .setY(y)
                .setEventTimeNanos(eventTimeNanos)
                .build();
        assertWithMessage("Incorrect action").that(event.getAction()).isEqualTo(
                VirtualStylusMotionEvent.ACTION_DOWN);
        assertWithMessage("Incorrect tool type").that(event.getToolType()).isEqualTo(
                VirtualStylusMotionEvent.TOOL_TYPE_STYLUS);
        assertWithMessage("Incorrect x").that(event.getX()).isEqualTo(x);
        assertWithMessage("Incorrect y").that(event.getY()).isEqualTo(y);
        assertWithMessage("Incorrect event time").that(event.getEventTimeNanos())
                .isEqualTo(eventTimeNanos);
    }

    @Test
    public void stylusMotionEvent_validWithPressureAndTilt_created() {
        final int x = 50;
        final int y = 800;
        final int pressure = 10;
        final int tiltX = 10;
        final int tiltY = 20;
        final long eventTimeNanos = 5000L;
        final VirtualStylusMotionEvent event = new VirtualStylusMotionEvent.Builder()
                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
                .setX(x)
                .setY(y)
                .setPressure(pressure)
                .setTiltX(tiltX)
                .setTiltY(tiltY)
                .setEventTimeNanos(eventTimeNanos)
                .build();
        assertWithMessage("Incorrect action").that(event.getAction()).isEqualTo(
                VirtualStylusMotionEvent.ACTION_DOWN);
        assertWithMessage("Incorrect tool type").that(event.getToolType()).isEqualTo(
                VirtualStylusMotionEvent.TOOL_TYPE_STYLUS);
        assertWithMessage("Incorrect x").that(event.getX()).isEqualTo(x);
        assertWithMessage("Incorrect y").that(event.getY()).isEqualTo(y);
        assertWithMessage("Incorrect x-axis tilt").that(event.getTiltX()).isEqualTo(tiltX);
        assertWithMessage("Incorrect y-axis tilt").that(event.getTiltY()).isEqualTo(tiltY);
        assertWithMessage("Incorrect pressure").that(event.getPressure()).isEqualTo(pressure);
        assertWithMessage("Incorrect event time").that(event.getEventTimeNanos())
                .isEqualTo(eventTimeNanos);
    }
}
