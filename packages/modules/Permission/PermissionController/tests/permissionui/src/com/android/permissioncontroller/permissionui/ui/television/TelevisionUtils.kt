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

package com.android.permissioncontroller.permissionui.ui.television

import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

private val SELECTOR_FOCUSED = By.focused(true)
private val SELECTOR_RES_ID_PC_DECOR_TITLE =
    By.res("com.android.permissioncontroller", "decor_title")
private val SELECTOR_RES_ID_ANDROID_TITLE = By.res("android", "title")

private const val WAIT_DELAY = 3_000L
private const val RETRIES = 5

val UiDevice.fragmentDecorTitle: String?
    get() = wait(Until.findObject(SELECTOR_RES_ID_PC_DECOR_TITLE), WAIT_DELAY)?.text

val UiDevice.focusedElement: UiObject2
    get() =
        wait(Until.findObject(SELECTOR_FOCUSED), WAIT_DELAY) ?: error("Focused item is not found")

private val UiObject2.titleElement: UiObject2
    get() =
        wait(Until.findObject(SELECTOR_RES_ID_ANDROID_TITLE), WAIT_DELAY)
            ?: error("Could not retrieve title")

val UiDevice.focusedElementTitle: String?
    get() {
        repeat(RETRIES) {
            try {
                return focusedElement.titleElement.text
            } catch (e: StaleObjectException) {}
        }
        error("Could not get title text")
    }

fun UiDevice.navigateDown() = navigate { pressDPadDown() }

fun UiDevice.navigateUp() = navigate { pressDPadUp() }

@Suppress("ControlFlowWithEmptyBody")
fun UiDevice.navigateToTheBottom() {
    while (navigateDown()) {}
}

@Suppress("ControlFlowWithEmptyBody")
fun UiDevice.navigateToTheTop() {
    while (navigateUp()) {}
}

fun UiDevice.focusOnElementWithTitle(title: CharSequence): Boolean = checkAllItemsIfNeeded {
    focusedElementTitle == title
}

fun UiDevice.hasElementWithTitle(title: CharSequence): Boolean = checkAllItemsIfNeeded {
    hasObject(By.copy(SELECTOR_RES_ID_ANDROID_TITLE).text(title.toString()))
}

private fun UiDevice.checkAllItemsIfNeeded(predicate: () -> Boolean): Boolean {
    // Let's do one quick check first, right where we are. If it does not work - we'll do the walk.
    if (predicate()) return true

    // That didn't work, so we'll go over all the items in the list (if needed) top to bottom, but
    // let's make sure we start from the very top.
    navigateToTheTop()

    do {
        if (predicate()) return true
    } while (navigateDown())

    return false
}

private fun UiDevice.navigate(action: () -> Unit): Boolean {
    val prevFocusedTitle = focusedElementTitle

    action()
    waitForIdle()

    return prevFocusedTitle != focusedElementTitle
}
