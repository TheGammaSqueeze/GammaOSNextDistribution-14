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
package android.platform.test.rule

import android.hardware.input.InputManager
import android.os.ParcelFileDescriptor
import android.platform.helpers.uinput.UInputDevice
import android.platform.uiautomator_helpers.DeviceHelpers
import android.util.Log
import android.view.InputDevice
import androidx.core.content.getSystemService
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.MoreExecutors
import com.google.gson.stream.JsonReader
import java.io.InputStreamReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.runner.Description

/**
 * This rule allows end-to-end tests to add input devices through Uinput more easily. Additionally
 * it will wait for registration to complete and unregister devices after the test is complete.
 *
 * Sample usage:
 * ```
 * class InputDeviceTest {
 *     @get:Rule
 *     val inputDeviceRule = InputDeviceRule()
 *
 *     @Test
 *     fun testWithInputDevice() {
 *         inputDeviceRule.registerDevice(UinputKeyboard())
 *         // Continue test with input device added
 *     }
 * }
 * ```
 */
class InputDeviceRule : TestWatcher(), UInputDevice.EventInjector {

    private val inputManager = DeviceHelpers.context.getSystemService<InputManager>()!!
    private val deviceAddedMap = mutableMapOf<UInputDevice, CountDownLatch>()
    private val inputManagerDevices = mutableMapOf<DeviceId, UInputDevice>()

    private lateinit var inputStream: ParcelFileDescriptor.AutoCloseInputStream
    private lateinit var outputStream: ParcelFileDescriptor.AutoCloseOutputStream

    private val inputDeviceListenerDelegate =
        object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) = updateInputDevice(DeviceId(deviceId))

            override fun onInputDeviceChanged(deviceId: Int) = updateInputDevice(DeviceId(deviceId))

            override fun onInputDeviceRemoved(deviceId: Int) {
                val deviceIdWrapped = DeviceId(deviceId)
                inputManagerDevices[deviceIdWrapped]?.let {
                    deviceAddedMap.remove(it)
                    inputManagerDevices.remove(deviceIdWrapped)
                }
            }
        }

    override fun starting(description: Description?) {
        super.starting(description)

        val (stdOut, stdIn) =
            InstrumentationRegistry.getInstrumentation()
                .uiAutomation
                .executeShellCommandRw("uinput -")

        inputStream = ParcelFileDescriptor.AutoCloseInputStream(stdOut)
        outputStream = ParcelFileDescriptor.AutoCloseOutputStream(stdIn)

        inputManager.registerInputDeviceListener(
            inputDeviceListenerDelegate,
            DeviceHelpers.context.mainThreadHandler
        )
    }

    override fun finished(description: Description?) {
        inputStream.close()
        outputStream.close()

        inputManager.unregisterInputDeviceListener(inputDeviceListenerDelegate)
        deviceAddedMap.clear()
        inputManagerDevices.clear()
    }

    /**
     * Registers the provided device with Uinput. This call waits for
     * [InputManager.InputDeviceListener.onInputDeviceAdded] to be called before returning
     *
     * @throws RuntimeException if the device did not register successfully.
     */
    fun registerDevice(device: UInputDevice) {
        deviceAddedMap.putIfAbsent(device, CountDownLatch(1))

        writeCommand(device.getRegisterCommand())

        deviceAddedMap[device]!!.let { latch ->
            latch.await(20, TimeUnit.SECONDS)
            if (latch.count != 0L) {
                throw RuntimeException(
                    "Did not receive added notification for device ${device.name}"
                )
            }
        }
    }

    /** Send the [keycode] event, both key down and key up events, for the provided [deviceId]. */
    override fun sendKeyEvent(deviceId: Int, keycode: Int) {
        sendEventWithValues(deviceId, EV_KEY, keycode, KEY_DOWN)
        sendEventWithValues(deviceId, EV_KEY, keycode, KEY_UP)
    }

    /** Send the [eventType], [keycode], [value] for the provided [deviceId]. */
    override fun sendEventWithValues(deviceId: Int, eventType: Int, keycode: Int, value: Int) {
        injectEvdevEvents(deviceId, listOf(eventType, keycode, value, EV_SYN, SYN_REPORT, 0))
    }

    /**
     * The provided file should contain Uinput events in json format. The file path should be
     * relative to the assets root.
     */
    override fun sendEventsFromInputFile(deviceId: Int, inputFile: String) {
        InstrumentationRegistry.getInstrumentation().context.assets.open(inputFile).use {
            writeCommand(it.readBytes())
        }

        val executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool())
        val future = executor.submit { waitForEmptyUinputEventQueue() }
        try {
            future.get(waitForSyncTokenDurationSeconds, TimeUnit.SECONDS)
        } catch (e: Exception) {
            error("Did not receive '$EVENT_REPLAY_COMPLETE_SYNC_TOKEN' within timeout")
        }
    }

    /**
     * Inject array of uinput events for a device. The following is an example of events: [[EV_KEY],
     * [KEY_UP], [KEY_DOWN], [EV_SYN], [SYN_REPORT], 0]. The number of entries in the provided
     * [evdevEvents] has to be a multiple of 3.
     *
     * @param deviceId The id corresponding to [UInputDevice] to associate with the [evdevEvents]
     * @param evdevEvents The uinput events to be injected
     */
    private fun injectEvdevEvents(deviceId: Int, evdevEvents: List<Int>) {
        assert(evdevEvents.size % 3 == 0) { "Number of injected events should be a multiple of 3" }

        writeCommand("""{"command": "inject","id": $deviceId,"events": $evdevEvents}""")
    }

    private fun writeCommand(command: String) {
        writeCommand(command.toByteArray())
    }

    private fun writeCommand(command: ByteArray) {
        outputStream.write(command)
        outputStream.flush()
    }

    private fun updateInputDevice(deviceId: DeviceId) {
        val device: InputDevice = inputManager.getInputDevice(deviceId.deviceId) ?: return
        val uinputDevice = deviceAddedMap.keys.firstOrNull { it.isInputDevice(device) } ?: return

        inputManagerDevices[deviceId] = uinputDevice
        deviceAddedMap[uinputDevice]!!.countDown()
    }

    private fun waitForEmptyUinputEventQueue() {
        writeCommand(EVENT_REPLAY_COMPLETE_SYNC_TOKEN_EVENT)

        Log.d(
            "InputDeviceRule",
            "'$EVENT_REPLAY_COMPLETE_SYNC_TOKEN' sync token sent, waiting for it to be processed..."
        )
        var nextSyncToken: String? = null
        while (nextSyncToken != EVENT_REPLAY_COMPLETE_SYNC_TOKEN) {
            nextSyncToken = readSyncTokenFromInputStream()
        }
        Log.d("InputDeviceRule", "'$EVENT_REPLAY_COMPLETE_SYNC_TOKEN' sync token processed")
    }

    /**
     * Returns the syncToken from the inputStream or null if the inputStream contains content other
     * than a syncToken.
     */
    private fun readSyncTokenFromInputStream(): String? {
        val reader = JsonReader(InputStreamReader(inputStream, Charsets.UTF_8))
        var reason: String? = null
        var syncToken: String? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "reason" -> reason = reader.nextString()
                "syncToken" -> syncToken = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (reason != "sync" || syncToken == null) return null

        return syncToken
    }

    @JvmInline value class DeviceId(val deviceId: Int)

    private companion object {
        // See
        // https://cs.android.com/android/kernel/superproject/+/common-android-mainline:common/include/uapi/linux/input-event-codes.h
        // for these mappings.
        const val EV_KEY = 1
        const val EV_SYN = 0
        const val SYN_REPORT = 0
        const val KEY_UP = 0
        const val KEY_DOWN = 1

        const val EVENT_REPLAY_COMPLETE_SYNC_TOKEN = "event_replay_complete"
        val EVENT_REPLAY_COMPLETE_SYNC_TOKEN_EVENT =
            """
            {
                "id": 1,
                "command": "sync",
                "syncToken": "$EVENT_REPLAY_COMPLETE_SYNC_TOKEN"
            }

            {
              "id": 1,
              "command": "delay",
              "duration": 100
            }
            """
                .trimIndent()
        const val waitForSyncTokenDurationSeconds = 20L
    }
}
