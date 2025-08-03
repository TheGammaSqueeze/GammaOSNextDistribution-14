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

package com.android.cts.input

import android.app.Instrumentation
import android.content.Context
import android.graphics.Point
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.server.wm.WindowManagerStateHelper
import android.util.Size
import android.view.Display
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.TestUtils.waitOn
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Helper class for configuring and interacting with a [UinputDevice] that uses the evdev
 * multitouch protocol.
 */
class UinputTouchDevice(
    instrumentation: Instrumentation,
    display: Display,
    private val rawResource: Int,
    private val source: Int,
    useDisplaySize: Boolean = false,
) :
    AutoCloseable {

    private val DISPLAY_ASSOCIATION_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(5)

    private val uinputDevice: UinputDevice
    private lateinit var port: String
    private val inputManager: InputManager

    init {
        val size = Size(display.getMode().getPhysicalWidth(), display.getMode().getPhysicalHeight())
        uinputDevice = createDevice(instrumentation, if (useDisplaySize) size else null)
        inputManager = instrumentation.targetContext.getSystemService(InputManager::class.java)!!
        associateWith(display)
        WindowManagerStateHelper().waitForAppTransitionIdleOnDisplay(display.displayId)
        instrumentation.uiAutomation.syncInputTransactions()
    }

    private fun injectEvent(events: IntArray) {
        uinputDevice.injectEvents(events.joinToString(
            prefix = "[",
            postfix = "]",
            separator = ",",
        ))
    }

    fun sendBtnTouch(isDown: Boolean) {
        injectEvent(intArrayOf(EV_KEY, BTN_TOUCH, if (isDown) 1 else 0))
    }

    fun sendBtn(btnCode: Int, isDown: Boolean) {
        injectEvent(intArrayOf(EV_KEY, btnCode, if (isDown) 1 else 0))
    }

    fun sendDown(id: Int, location: Point, toolType: Int? = null) {
        injectEvent(intArrayOf(EV_ABS, ABS_MT_SLOT, id))
        injectEvent(intArrayOf(EV_ABS, ABS_MT_TRACKING_ID, id))
        if (toolType != null) injectEvent(intArrayOf(EV_ABS, ABS_MT_TOOL_TYPE, toolType))
        injectEvent(intArrayOf(EV_ABS, ABS_MT_POSITION_X, location.x))
        injectEvent(intArrayOf(EV_ABS, ABS_MT_POSITION_Y, location.y))
    }

    fun sendMove(id: Int, location: Point) {
        // Use same events of down.
        sendDown(id, location)
    }

    fun sendUp(id: Int) {
        injectEvent(intArrayOf(EV_ABS, ABS_MT_SLOT, id))
        injectEvent(intArrayOf(EV_ABS, ABS_MT_TRACKING_ID, INVALID_TRACKING_ID))
    }

    fun sendToolType(id: Int, toolType: Int) {
        injectEvent(intArrayOf(EV_ABS, ABS_MT_SLOT, id))
        injectEvent(intArrayOf(EV_ABS, ABS_MT_TOOL_TYPE, toolType))
    }

    fun sendPressure(pressure: Int) {
        injectEvent(intArrayOf(EV_ABS, ABS_MT_PRESSURE, pressure))
    }

    fun sync() {
        injectEvent(intArrayOf(EV_SYN, SYN_REPORT, 0))
    }

    fun delay(delayMs: Int) {
        uinputDevice.injectDelay(delayMs)
    }

    fun getDeviceId(): Int {
        return uinputDevice.getDeviceId()
    }

    private fun readRawResource(context: Context): String {
        return context.resources
            .openRawResource(rawResource)
            .bufferedReader().use { it.readText() }
    }

    private fun createDevice(instrumentation: Instrumentation, sizeOverride: Size?): UinputDevice {
        val json = JSONObject(readRawResource(instrumentation.targetContext))
        val resourceDeviceId: Int = json.getInt("id")
        val vendorId = json.getInt("vid")
        val productId = json.getInt("pid")
        port = json.getString("port")

        if (sizeOverride != null) {
            // Use the given size to set the maximum values of relevant axes.
            val absInfo: JSONArray = json.getJSONArray("abs_info")
            for (i in 0 until absInfo.length()) {
                val item = absInfo.getJSONObject(i)
                val code: Any = item.get("code")
                if (code == ABS_MT_POSITION_X || code == "ABS_MT_POSITION_X") {
                    item.getJSONObject("info")
                        .put("maximum", sizeOverride.width - 1)
                }
                if (code == ABS_MT_POSITION_Y || code == "ABS_MT_POSITION_Y") {
                    item.getJSONObject("info")
                        .put("maximum", sizeOverride.height - 1)
                }
            }
        }

        // Create the uinput device.
        val registerCommand = json.toString()
        return UinputDevice(
            instrumentation,
            resourceDeviceId,
            vendorId,
            productId,
            source,
            registerCommand
        )
    }

    private fun associateWith(display: Display) {
        runWithShellPermissionIdentity(
                { inputManager.addUniqueIdAssociation(port, display.uniqueId!!) },
                "android.permission.ASSOCIATE_INPUT_DEVICE_TO_DISPLAY"
        )
        waitForDeviceUpdatesUntil {
            val inputDevice = inputManager.getInputDevice(uinputDevice.deviceId)
            display.displayId == inputDevice!!.associatedDisplayId
        }
    }

    private fun waitForDeviceUpdatesUntil(condition: () -> Boolean) {
        val lockForInputDeviceUpdates = Object()
        val inputDeviceListener =
            object : InputManager.InputDeviceListener {
                override fun onInputDeviceAdded(deviceId: Int) {
                    synchronized(lockForInputDeviceUpdates) {
                        lockForInputDeviceUpdates.notify()
                    }
                }

                override fun onInputDeviceRemoved(deviceId: Int) {
                    synchronized(lockForInputDeviceUpdates) {
                        lockForInputDeviceUpdates.notify()
                    }
                }

                override fun onInputDeviceChanged(deviceId: Int) {
                    synchronized(lockForInputDeviceUpdates) {
                        lockForInputDeviceUpdates.notify()
                    }
                }
            }

        inputManager.registerInputDeviceListener(
            inputDeviceListener,
            Handler(Looper.getMainLooper())
        )

        waitOn(
            lockForInputDeviceUpdates,
            condition,
            DISPLAY_ASSOCIATION_TIMEOUT_MILLIS,
            null
        )

        inputManager.unregisterInputDeviceListener(inputDeviceListener)
    }

    override fun close() {
        runWithShellPermissionIdentity(
                { inputManager.removeUniqueIdAssociation(port) },
                "android.permission.ASSOCIATE_INPUT_DEVICE_TO_DISPLAY"
        )
        uinputDevice.close()
    }

    companion object {
        const val EV_SYN = 0
        const val EV_KEY = 1
        const val EV_ABS = 3
        const val ABS_MT_SLOT = 0x2f
        const val ABS_MT_POSITION_X = 0x35
        const val ABS_MT_POSITION_Y = 0x36
        const val ABS_MT_TOOL_TYPE = 0x37
        const val ABS_MT_TRACKING_ID = 0x39
        const val ABS_MT_PRESSURE = 0x3a
        const val BTN_TOUCH = 0x14a
        const val BTN_TOOL_FINGER = 0x145
        const val BTN_TOOL_DOUBLETAP = 0x14d
        const val BTN_TOOL_TRIPLETAP = 0x14e
        const val BTN_TOOL_QUADTAP = 0x14f
        const val BTN_TOOL_QUINTTAP = 0x148
        const val SYN_REPORT = 0
        const val MT_TOOL_FINGER = 0
        const val MT_TOOL_PEN = 1
        const val MT_TOOL_PALM = 2
        const val INVALID_TRACKING_ID = -1

        fun toolBtnForFingerCount(numFingers: Int): Int {
            return when (numFingers) {
                1 -> BTN_TOOL_FINGER
                2 -> BTN_TOOL_DOUBLETAP
                3 -> BTN_TOOL_TRIPLETAP
                4 -> BTN_TOOL_QUADTAP
                5 -> BTN_TOOL_QUINTTAP
                else -> throw IllegalArgumentException("Number of fingers must be between 1 and 5")
            }
        }
    }
}
