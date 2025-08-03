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

import android.os.SystemProperties
import org.junit.runner.Description

/**
 * Test rule that enables the bubble bar flag.
 *
 * Note that for the bubble bar to be enabled, taskbar must be restarted in transient mode and
 * navigation mode must be set to NO_BUTTON.
 *
 * @see TaskbarModeSwitchRule
 * @see NavigationModeRule
 */
class BubbleBarRule : TestWatcher() {

    private var wasBubbleBarEnabled = false

    override fun starting(description: Description?) {
        wasBubbleBarEnabled = SystemProperties.get(BUBBLE_BAR_SYS_PROP) == "1"
        if (!wasBubbleBarEnabled) {
            SystemProperties.set(BUBBLE_BAR_SYS_PROP, "1")
        }
    }

    override fun finished(description: Description?) {
        if (!wasBubbleBarEnabled) {
            SystemProperties.set(BUBBLE_BAR_SYS_PROP, "0")
        }
    }
}

private const val BUBBLE_BAR_SYS_PROP = "persist.wm.debug.bubble_bar"
