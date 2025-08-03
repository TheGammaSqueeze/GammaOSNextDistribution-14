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

package android.platform.helpers.rules

import android.platform.helpers.ui.UiAutomatorUtils.getUiDevice
import android.provider.Settings.Global.ONE_HANDED_KEYGUARD_SIDE
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Resets the bouncer side to default after each test.
 *
 * This is needed to make the initial bouncer in a predictable location, not based on past
 * interactions.
 */
class SidedBouncerRule : TestWatcher() {
    override fun finished(description: Description?) {
        getUiDevice().executeShellCommand("settings delete global $ONE_HANDED_KEYGUARD_SIDE")
    }
}
