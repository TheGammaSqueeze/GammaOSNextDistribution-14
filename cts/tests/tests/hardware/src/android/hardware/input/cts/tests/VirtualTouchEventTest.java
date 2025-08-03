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

import android.hardware.input.VirtualTouchEvent;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class VirtualTouchEventTest {

    @Test
    public void parcelAndUnparcel_matches() {
        final float x = 50f;
        final float y = 800f;
        final int pointerId = 1;
        final float pressure = 0.5f;
        final float majorAxisSize = 10f;
        final long eventTimeNanos = 5000L;
        final VirtualTouchEvent originalEvent = new VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_DOWN)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .setX(x)
                .setY(y)
                .setPointerId(pointerId)
                .setPressure(pressure)
                .setMajorAxisSize(majorAxisSize)
                .setEventTimeNanos(eventTimeNanos)
                .build();
        final Parcel parcel = Parcel.obtain();
        originalEvent.writeToParcel(parcel, /* flags= */ 0);
        parcel.setDataPosition(0);
        final VirtualTouchEvent recreatedEvent = VirtualTouchEvent.CREATOR.createFromParcel(parcel);
        assertWithMessage("Recreated event has different action").that(originalEvent.getAction())
                .isEqualTo(recreatedEvent.getAction());
        assertWithMessage("Recreated event has different tool type")
                .that(originalEvent.getToolType()).isEqualTo(recreatedEvent.getToolType());
        assertWithMessage("Recreated event has different x").that(originalEvent.getX())
                .isEqualTo(recreatedEvent.getX());
        assertWithMessage("Recreated event has different y").that(originalEvent.getY())
                .isEqualTo(recreatedEvent.getY());
        assertWithMessage("Recreated event has different pointer id")
                .that(originalEvent.getPointerId()).isEqualTo(recreatedEvent.getPointerId());
        assertWithMessage("Recreated event has different pressure")
                .that(originalEvent.getPressure()).isEqualTo(recreatedEvent.getPressure());
        assertWithMessage("Recreated event has different major axis size")
                .that(originalEvent.getMajorAxisSize())
                .isEqualTo(recreatedEvent.getMajorAxisSize());
        assertWithMessage("Recreated event has different event time")
                .that(originalEvent.getEventTimeNanos())
                .isEqualTo(recreatedEvent.getEventTimeNanos());
    }

    @Test
    public void touchEvent_emptyBuilder_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualTouchEvent.Builder().build());
    }

    @Test
    public void touchEvent_noAction_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualTouchEvent.Builder()
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .setX(0f)
                .setY(1f)
                .setPointerId(1)
                .build());
    }

    @Test
    public void touchEvent_noPointerId_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_DOWN)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .setX(0f)
                .setY(1f)
                .build());
    }

    @Test
    public void touchEvent_noToolType_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_DOWN)
                .setX(0f)
                .setY(1f)
                .setPointerId(1)
                .build());
    }

    @Test
    public void touchEvent_noX_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_DOWN)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .setY(1f)
                .setPointerId(1)
                .build());
    }

    @Test
    public void touchEvent_noY_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_DOWN)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .setX(0f)
                .setPointerId(1)
                .build());
    }

    @Test
    public void touchEvent_invalidEventTime_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_DOWN)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .setX(0f)
                .setY(1f)
                .setPointerId(1)
                .setEventTimeNanos(-5L)
                .build());
    }

    @Test
    public void touchEvent_cancelUsedImproperly_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_CANCEL)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .setX(0f)
                .setY(1f)
                .setPointerId(1)
                .build());
    }

    @Test
    public void touchEvent_palmUsedImproperly_throwsIae() {
        assertThrows(IllegalArgumentException.class, () -> new VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_MOVE)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_PALM)
                .setX(0f)
                .setY(1f)
                .setPointerId(1)
                .build());
    }

    @Test
    public void touchEvent_palmAndCancelUsedProperly() {
        final float x = 0f;
        final float y = 1f;
        final int pointerId = 1;
        final float pressure = 0.5f;
        final float majorAxisSize = 10f;
        final VirtualTouchEvent event = new VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_CANCEL)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_PALM)
                .setX(x)
                .setY(y)
                .setPointerId(pointerId)
                .setPressure(pressure)
                .setMajorAxisSize(majorAxisSize)
                .build();
        assertWithMessage("Incorrect action").that(event.getAction()).isEqualTo(
                VirtualTouchEvent.ACTION_CANCEL);
        assertWithMessage("Incorrect tool type").that(event.getToolType()).isEqualTo(
                VirtualTouchEvent.TOOL_TYPE_PALM);
        assertWithMessage("Incorrect x").that(event.getX()).isEqualTo(x);
        assertWithMessage("Incorrect y").that(event.getY()).isEqualTo(y);
        assertWithMessage("Incorrect pointer id").that(event.getPointerId()).isEqualTo(pointerId);
        assertWithMessage("Incorrect pressure").that(event.getPressure()).isEqualTo(pressure);
        assertWithMessage("Incorrect major axis size").that(event.getMajorAxisSize()).isEqualTo(
                majorAxisSize);
    }

    @Test
    public void touchEvent_valid_created() {
        final float x = 0f;
        final float y = 1f;
        final int pointerId = 1;
        final long eventTimeNanos = 5000L;
        final VirtualTouchEvent event = new VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_DOWN)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .setX(x)
                .setY(y)
                .setPointerId(pointerId)
                .setEventTimeNanos(eventTimeNanos)
                .build();
        assertWithMessage("Incorrect action").that(event.getAction()).isEqualTo(
                VirtualTouchEvent.ACTION_DOWN);
        assertWithMessage("Incorrect tool type").that(event.getToolType()).isEqualTo(
                VirtualTouchEvent.TOOL_TYPE_FINGER);
        assertWithMessage("Incorrect x").that(event.getX()).isEqualTo(x);
        assertWithMessage("Incorrect y").that(event.getY()).isEqualTo(y);
        assertWithMessage("Incorrect pointer id").that(event.getPointerId()).isEqualTo(pointerId);
        assertWithMessage("Incorrect event time").that(event.getEventTimeNanos())
                .isEqualTo(eventTimeNanos);
    }

    @Test
    public void touchEvent_validWithPressureAndAxis_created() {
        final float x = 0f;
        final float y = 1f;
        final int pointerId = 1;
        final float pressure = 0.5f;
        final float majorAxisSize = 10f;
        final VirtualTouchEvent event = new VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_DOWN)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .setX(x)
                .setY(y)
                .setPointerId(pointerId)
                .setPressure(pressure)
                .setMajorAxisSize(majorAxisSize)
                .build();
        assertWithMessage("Incorrect action").that(event.getAction()).isEqualTo(
                VirtualTouchEvent.ACTION_DOWN);
        assertWithMessage("Incorrect tool type").that(event.getToolType()).isEqualTo(
                VirtualTouchEvent.TOOL_TYPE_FINGER);
        assertWithMessage("Incorrect x").that(event.getX()).isEqualTo(x);
        assertWithMessage("Incorrect y").that(event.getY()).isEqualTo(y);
        assertWithMessage("Incorrect pointer id").that(event.getPointerId()).isEqualTo(pointerId);
        assertWithMessage("Incorrect pressure").that(event.getPressure()).isEqualTo(pressure);
        assertWithMessage("Incorrect major axis size").that(event.getMajorAxisSize()).isEqualTo(
                majorAxisSize);
    }
}
