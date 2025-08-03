/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.input.cts

import android.app.Instrumentation
import android.graphics.PointF
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent

object PointerConstants {
    const val ACTION_POINTER_1_DOWN = (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT) or
            MotionEvent.ACTION_POINTER_DOWN
    const val ACTION_POINTER_1_UP = (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT) or
            MotionEvent.ACTION_POINTER_UP
}

class TouchInjector(instrumentation: Instrumentation) {
    private val instrumentation = instrumentation
    private var downTime: Long = 0

    /**
     * Inject a single pointer motion event by indicating the action and pointer.
     */
   fun sendEvent(action: Int, pt: PointF) {
        val eventTime = when (action) {
            MotionEvent.ACTION_DOWN -> {
                downTime = SystemClock.uptimeMillis()
                downTime
            }
            else -> SystemClock.uptimeMillis()
        }
        val event = MotionEvent.obtain(downTime, eventTime, action, pt.x, pt.y, 0 /*metaState*/)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        instrumentation.sendPointerSync(event)
    }

    /**
     * Inject a couple of motion events which simulate a multitouch with 2 pointers.
     * The sequence will be DOWN -> MOVE -> SECOND POINTER DOWN -> SECOND POINTER UP -> UP.
     *
     * @param cancelPointer: true if the ACTION_POINTER_UP event is a pointer cancel.
     */
    fun sendMultiTouchEvent(pointers: Array<PointF>, cancelPointer: Boolean = false) {
        val eventTime = SystemClock.uptimeMillis()
        val pointerCount = pointers.size
        val properties = arrayOfNulls<MotionEvent.PointerProperties>(pointerCount)
        val coords = arrayOfNulls<MotionEvent.PointerCoords>(pointerCount)

        for (i in 0 until pointerCount) {
            properties[i] = MotionEvent.PointerProperties()
            properties[i]!!.id = i
            properties[i]!!.toolType = MotionEvent.TOOL_TYPE_FINGER
            coords[i] = MotionEvent.PointerCoords()
            coords[i]!!.x = pointers[i].x
            coords[i]!!.y = pointers[i].y
        }

        sendEvent(MotionEvent.ACTION_DOWN, pointers[0])
        sendEvent(MotionEvent.ACTION_MOVE, pointers[0])

        var event = MotionEvent.obtain(downTime, eventTime, PointerConstants.ACTION_POINTER_1_DOWN,
                pointerCount, properties, coords, 0 /*metaState*/, 0 /*buttonState*/,
                0f /*xPrecision*/, 0f /*yPrecision*/, 0 /*deviceId*/, 0 /*edgeFlags*/,
                InputDevice.SOURCE_TOUCHSCREEN, 0 /*flags */)
        instrumentation.sendPointerSync(event)

        val flags = when (cancelPointer) {
            true -> MotionEvent.FLAG_CANCELED
            false -> 0
        }
        event = MotionEvent.obtain(downTime, eventTime, PointerConstants.ACTION_POINTER_1_UP,
                pointerCount, properties, coords, 0 /*metaState*/, 0 /*buttonState*/,
                0f /*xPrecision*/, 0f /*yPrecision*/, 0 /*deviceId*/, 0 /*edgeFlags*/,
                InputDevice.SOURCE_TOUCHSCREEN, flags)
        instrumentation.sendPointerSync(event)

        sendEvent(MotionEvent.ACTION_UP, pointers[0])
    }
}
