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

package android.tools.flicker.assertors.assertions

import android.tools.PlatformConsts
import android.tools.datatypes.Region
import android.tools.flicker.ScenarioInstance
import android.tools.flicker.assertions.FlickerTest
import android.tools.flicker.assertors.AssertionTemplate
import android.tools.traces.component.ComponentNameMatcher

/**
 * Checks if the [ComponentNameMatcher.STATUS_BAR] layer is placed at the correct position at the
 * start of the transition
 */
class StatusBarLayerPositionAtStart : AssertionTemplate() {
    /** {@inheritDoc} */
    override fun doEvaluate(scenarioInstance: ScenarioInstance, flicker: FlickerTest) {
        flicker.assertWmStart {
            visibleRegion(ComponentNameMatcher.STATUS_BAR)
                .coversExactly(getExpectedStatusBarPosition(scenarioInstance))
        }
    }

    // TODO: Maybe find another way to get the expected position that doesn't rely on use the data
    // from the WM trace
    // can we maybe dump another trace that just has system info for this purpose?
    // TODO: Also this is duplicated code we can probably extract this out
    private fun getExpectedStatusBarPosition(scenarioInstance: ScenarioInstance): Region {
        val wmState =
            scenarioInstance.reader.readWmTrace()?.entries?.last()
                ?: error("Missing wm trace entries")
        val display =
            wmState.getDisplay(PlatformConsts.DEFAULT_DISPLAY) ?: error("Display not found")
        TODO("return WindowUtils.getExpectedStatusBarPosition(display)")
    }
}
