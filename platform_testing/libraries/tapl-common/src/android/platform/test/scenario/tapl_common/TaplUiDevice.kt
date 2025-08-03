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
package android.platform.test.scenario.tapl_common

import android.platform.uiautomator_helpers.DeviceHelpers.waitForObj
import androidx.test.uiautomator.BySelector

/** Wrapper of UiDevice for finding TAPL UI objects and performing flake-free gestures. */
object TaplUiDevice {
    /**
     * Waits for a UI object with a given selector. Fails if the object is not visible.
     *
     * @param [selector] Selector for the ui object.
     * @param [objectName] Name of the object for diags
     * @return The found UI object.
     */
    @JvmStatic
    fun waitForObject(selector: BySelector, objectName: String): TaplUiObject {
        val uiObject = waitForObj(selector)
        return TaplUiObject(uiObject, objectName)
    }
}
