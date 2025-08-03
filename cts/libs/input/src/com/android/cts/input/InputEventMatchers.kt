/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.cts.input.inputeventmatchers

import android.graphics.Point
import android.graphics.PointF
import android.view.KeyEvent
import android.view.KeyEvent.keyCodeToString
import android.view.MotionEvent
import kotlin.math.abs
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher

private const val EPSILON = 0.001f

fun withCoords(pt: PointF): Matcher<MotionEvent> = object : TypeSafeMatcher<MotionEvent>() {
    override fun describeTo(description: Description) {
        description.appendText("With coords = $pt")
    }

    override fun matchesSafely(event: MotionEvent): Boolean {
        return (abs(event.x - pt.x) < EPSILON) &&
                (abs(event.y - pt.y) < EPSILON)
    }
}

fun withCoordsForPointerIndex(index: Int, pt: PointF): Matcher<MotionEvent> =
        object : TypeSafeMatcher<MotionEvent>() {
    override fun describeTo(description: Description) {
        description.appendText("With coords = $pt for pointer index = $index")
    }

    override fun matchesSafely(event: MotionEvent): Boolean {
        return (abs(event.getX(index)) - pt.x < EPSILON) &&
                (abs(event.getY(index)) - pt.y < EPSILON)
    }
}

fun withRawCoords(pt: PointF): Matcher<MotionEvent> = object : TypeSafeMatcher<MotionEvent>() {
    override fun describeTo(description: Description) {
        description.appendText("With coords = $pt")
    }

    override fun matchesSafely(event: MotionEvent): Boolean {
        return (abs(event.rawX) - pt.x < EPSILON) &&
                (abs(event.rawY) - pt.y < EPSILON)
    }

    override fun describeMismatchSafely(event: MotionEvent, mismatchDescription: Description) {
        mismatchDescription.appendText("Got raw coords = {${event.getRawX()}, ${event.getRawY()}}")
    }
}

fun withMotionAction(action: Int): Matcher<MotionEvent> = object : TypeSafeMatcher<MotionEvent>() {
    override fun describeTo(description: Description) {
        description.appendText("With action = ${MotionEvent.actionToString(action)}")
    }

    override fun matchesSafely(event: MotionEvent): Boolean {
        if (action == MotionEvent.ACTION_CANCEL) {
            if (event.flags and MotionEvent.FLAG_CANCELED != MotionEvent.FLAG_CANCELED) {
                return false
            }
        }
        return event.action == action
    }
}

fun withMotionAction(action: Int, index: Int): Matcher<MotionEvent> =
        object : TypeSafeMatcher<MotionEvent>() {
    override fun describeTo(description: Description) {
        description.appendText(
            "With action = ${MotionEvent.actionToString(action)}, index = $index"
        )
    }

    override fun matchesSafely(event: MotionEvent): Boolean {
        if (action != MotionEvent.ACTION_POINTER_DOWN && action != MotionEvent.ACTION_POINTER_UP) {
            throw Exception(
                "Matcher should only be used with ACTION_POINTER_DOWN or ACTION_POINTER_UP"
            )
        }
        return event.actionMasked == action && event.actionIndex == index
    }
}

fun withRawCoords(pt: Point): Matcher<MotionEvent> {
    return withRawCoords(PointF(pt))
}

fun withSource(source: Int): Matcher<MotionEvent> = object : TypeSafeMatcher<MotionEvent>() {
    override fun describeTo(description: Description) {
        description.appendText("With source = $source")
    }

    override fun matchesSafely(event: MotionEvent): Boolean {
        return event.getSource() == source
    }
}

fun withDeviceId(deviceId: Int): Matcher<MotionEvent> = object : TypeSafeMatcher<MotionEvent>() {
    override fun describeTo(description: Description) {
        description.appendText("With deviceId = $deviceId")
    }

    override fun matchesSafely(event: MotionEvent): Boolean {
        return event.getDeviceId() == deviceId
    }
}

fun withEventTime(eventTime: Long): Matcher<MotionEvent> = object : TypeSafeMatcher<MotionEvent>() {
    override fun describeTo(description: Description) {
        description.appendText("With eventTime = $eventTime")
    }

    override fun matchesSafely(event: MotionEvent): Boolean {
        return event.getEventTime() == eventTime
    }
}

fun withFlags(flags: Int): Matcher<MotionEvent> = object : TypeSafeMatcher<MotionEvent>() {
    override fun describeTo(description: Description) {
        description.appendText("With flags = $flags")
    }

    override fun matchesSafely(event: MotionEvent): Boolean {
        return event.flags and flags == flags
    }
}

fun withToolType(toolType: Int): Matcher<MotionEvent> = object : TypeSafeMatcher<MotionEvent>() {
    override fun describeTo(description: Description) {
        description.appendText("With tool type = $toolType")
    }

    override fun matchesSafely(event: MotionEvent): Boolean {
        for (p in 0..<event.getPointerCount()) {
            if (event.getToolType(p) != toolType) {
                return false
            }
        }
        return true
    }
}

fun withKeyCode(keyCode: Int): Matcher<KeyEvent> = object : TypeSafeMatcher<KeyEvent>() {
    override fun describeTo(description: Description) {
        description.appendText("With key code = " + keyCodeToString(keyCode))
    }

    override fun matchesSafely(event: KeyEvent): Boolean {
        return event.keyCode == keyCode
    }
}

fun withEdgeFlags(edgeFlags: Int): Matcher<MotionEvent> = object : TypeSafeMatcher<MotionEvent>() {
    override fun describeTo(description: Description) {
        description.appendText("With edge flags = 0x${edgeFlags.toString(16)}")
    }

    override fun matchesSafely(event: MotionEvent): Boolean {
        return (event.edgeFlags and edgeFlags) == edgeFlags
    }
}

fun withDownTime(downTime: Long): Matcher<MotionEvent> = object : TypeSafeMatcher<MotionEvent>() {
    override fun describeTo(description: Description) {
        description.appendText("With down time = $downTime")
    }

    override fun matchesSafely(event: MotionEvent): Boolean {
        return event.downTime == downTime
    }
}

fun withMetaState(metaState: Int): Matcher<MotionEvent> = object : TypeSafeMatcher<MotionEvent>() {
    override fun describeTo(description: Description) {
        description.appendText("With meta state = 0x${metaState.toString(16)}")
    }

    override fun matchesSafely(event: MotionEvent): Boolean {
        return event.metaState == metaState
    }
}

fun withPressure(pressure: Float): Matcher<MotionEvent> = object : TypeSafeMatcher<MotionEvent>() {
    override fun describeTo(description: Description) {
        description.appendText("With pressure = $pressure")
    }

    override fun matchesSafely(event: MotionEvent): Boolean {
        return abs(event.pressure - pressure) < EPSILON
    }
}

fun withSize(size: Float): Matcher<MotionEvent> = object : TypeSafeMatcher<MotionEvent>() {
    override fun describeTo(description: Description) {
        description.appendText("With size = $size")
    }

    override fun matchesSafely(event: MotionEvent): Boolean {
        return abs(event.size - size) < EPSILON
    }
}

fun withXPrecision(xPrecision: Float):
        Matcher<MotionEvent> = object : TypeSafeMatcher<MotionEvent>() {
    override fun describeTo(description: Description) {
        description.appendText("With xPrecision = $xPrecision")
    }

    override fun matchesSafely(event: MotionEvent): Boolean {
        return abs(event.xPrecision - xPrecision) < EPSILON
    }
}

fun withYPrecision(yPrecision: Float):
        Matcher<MotionEvent> = object : TypeSafeMatcher<MotionEvent>() {
    override fun describeTo(description: Description) {
        description.appendText("With yPrecision = $yPrecision")
    }

    override fun matchesSafely(event: MotionEvent): Boolean {
        return abs(event.yPrecision - yPrecision) < EPSILON
    }
}
