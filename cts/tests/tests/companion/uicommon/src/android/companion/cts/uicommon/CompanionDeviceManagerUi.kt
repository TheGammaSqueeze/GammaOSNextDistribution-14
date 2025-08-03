/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.companion.cts.uicommon

import android.os.SystemClock
import android.os.SystemClock.sleep
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.SearchCondition
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

open class CompanionDeviceManagerUi(private val ui: UiDevice) {
    val isVisible: Boolean
        get() = ui.hasObject(CONFIRMATION_UI)

    fun dismiss() {
        if (!isVisible) return
        // Pressing back button should close (cancel) confirmation UI.
        ui.pressBack()
        waitUntilGone()
    }

    fun waitUntilVisible(timeout: Duration = 3.seconds) = ui.wait(
        Until.hasObject(CONFIRMATION_UI), "CDM UI has not appeared.", timeout)

    fun waitUntilNotificationVisible(isAuto: Boolean = false) = ui.wait(
        if (isAuto) Until.hasObject(NOTIFICATION_UI_AUTO) else Until.hasObject(NOTIFICATION_UI),
        "NOTIFICATION UI has not appeared.")

    fun waitUntilGone() = ui.waitShort(Until.gone(CONFIRMATION_UI), "CDM UI has not disappeared")

    fun waitAndClickOnFirstFoundDevice() {
        val firstDevice = ui.waitLongAndFind(
                Until.findObject(
                        DEVICE_LIST_WITH_ITEMS), "The item in the Device List not found or empty")
                .children[0]

        val startTime = SystemClock.uptimeMillis()
        var elapsedTime = 0L
        // Keep trying to click the first item in the list until the device_list is disappeared
        // or it times out after 5s.
        while (ui.hasObject(DEVICE_LIST) && elapsedTime < 5.seconds.inWholeMilliseconds) {
            firstDevice.click()
            sleep(0.2.seconds.inWholeMilliseconds)
            elapsedTime = SystemClock.uptimeMillis() - startTime
        }
    }

    fun waitUntilPositiveButtonIsEnabledAndClick() = ui.waitLongAndFind(
        Until.findObject(POSITIVE_BUTTON), "Positive button not found or not clickable")
            .click()

    fun waitUntilSystemDataTransferConfirmationVisible() = ui.wait(
            Until.hasObject(SYSTEM_DATA_TRANSFER_CONFIRMATION_UI),
            "System data transfer dialog has not appeared.")

    fun clickPositiveButton() = click(POSITIVE_BUTTON, "Positive button")

    fun clickNegativeButton() = click(NEGATIVE_BUTTON, "Negative button")

    fun clickNegativeButtonMultipleDevices() {
        ui.wait(Until.findObject(CONFIRMATION_UI), 2.seconds.inWholeMilliseconds)?.let {
            // swipe up (or scroll down) until cancel button is enabled
            val startTime = SystemClock.uptimeMillis()
            var elapsedTime = 0L
            // UiDevice.hasObject() takes a long time for some reason so wait at least 10 seconds
            while (!ui.hasObject(NEGATIVE_BUTTON_MULTIPLE_DEVICES)
                    && elapsedTime < 10.seconds.inWholeMilliseconds) {
                it.swipe(Direction.UP, 1.0F)
                elapsedTime = SystemClock.uptimeMillis() - startTime
            }
        }
        click(NEGATIVE_BUTTON_MULTIPLE_DEVICES, "Negative button for multiple devices")
    }

    fun waitUntilAppAppeared() = ui.wait(Until.hasObject(ASSOCIATION_REVOKE_APP_UI),
        "The test app has not appeared.")

    fun waitUntilPositiveButtonAppeared() = ui.waitLongAndFind(
        Until.findObject(POSITIVE_BUTTON), "Positive button")

    fun scrollToBottom() {
        ui.wait(Until.findObject(SCROLLABLE_PERMISSION_LIST), 2.seconds.inWholeMilliseconds)?.let {
            val positiveButton = waitUntilPositiveButtonAppeared()

            // swipe up (or scroll down) until "Allow" button is enabled
            val startTime = SystemClock.uptimeMillis()
            var elapsedTime = 0L
            while (!positiveButton.isEnabled && elapsedTime < 5.seconds.inWholeMilliseconds) {
                it.swipe(Direction.UP, 1.0F)

                // Wait before consecutive swipes
                if (!positiveButton.isEnabled) {
                    sleep(0.2.seconds.inWholeMilliseconds)
                }
                elapsedTime = SystemClock.uptimeMillis() - startTime
            }
        }
    }

    protected fun click(selector: BySelector, description: String) = ui.waitShortAndFind(
            Until.findObject(selector), "$description is not found")
            .click()

    companion object {
        private const val PACKAGE_NAME = "com.android.companiondevicemanager"
        private const val NOTIFICATION_PACKAGE_NAME = "com.android.settings"
        private const val NOTIFICATION_PACKAGE_NAME_AUTO = "com.android.car.settings"

        private val CONFIRMATION_UI = By.pkg(PACKAGE_NAME)
                .res(PACKAGE_NAME, "activity_confirmation")
        private val ASSOCIATION_REVOKE_APP_UI = By.pkg(ASSOCIATION_REVOKE_APP_NAME).depth(0)

        private val NOTIFICATION_UI = By.pkg(NOTIFICATION_PACKAGE_NAME).depth(0)

        private val NOTIFICATION_UI_AUTO = By.pkg(NOTIFICATION_PACKAGE_NAME_AUTO).depth(0)

        private val CLICKABLE_BUTTON =
                By.pkg(PACKAGE_NAME).clazz(".Button").clickable(true)
        private val POSITIVE_BUTTON = By.copy(CLICKABLE_BUTTON).res(PACKAGE_NAME, "btn_positive")
        private val NEGATIVE_BUTTON = By.copy(CLICKABLE_BUTTON).res(PACKAGE_NAME, "btn_negative")
        private val NEGATIVE_BUTTON_MULTIPLE_DEVICES = By.pkg(PACKAGE_NAME)
                .res(PACKAGE_NAME, "negative_multiple_devices_layout")

        private val DEVICE_LIST = By.res(PACKAGE_NAME, "device_list")
        private val DEVICE_LIST_ITEM = By.res(PACKAGE_NAME, "list_item_device")
        private val DEVICE_LIST_WITH_ITEMS = By.copy(DEVICE_LIST)
                .hasDescendant(DEVICE_LIST_ITEM)

        private val SCROLLABLE_PERMISSION_LIST = By.pkg(PACKAGE_NAME)
                .res(PACKAGE_NAME, "permission_list")

        private val SYSTEM_DATA_TRANSFER_CONFIRMATION_UI = By.pkg(PACKAGE_NAME)
                .res(PACKAGE_NAME, "data_transfer_confirmation")
    }

    protected fun UiDevice.wait(
        condition: SearchCondition<Boolean>,
        message: String,
        timeout: Duration = 3.seconds
    ) {
        if (!wait(condition, timeout.inWholeMilliseconds)) error(message)
    }

    protected fun UiDevice.waitShort(condition: SearchCondition<Boolean>, message: String) =
            wait(condition, message, 1.seconds)

    protected fun UiDevice.waitAndFind(
        condition: SearchCondition<UiObject2>,
        message: String,
        timeout: Duration = 3.seconds
    ): UiObject2 =
            wait(condition, timeout.inWholeMilliseconds) ?: error(message)

    protected fun UiDevice.waitShortAndFind(
        condition: SearchCondition<UiObject2>,
        message: String
    ): UiObject2 = waitAndFind(condition, message, 1.seconds)

    protected fun UiDevice.waitLongAndFind(
        condition: SearchCondition<UiObject2>,
        message: String
    ): UiObject2 = waitAndFind(condition, message, 10.seconds)
}
