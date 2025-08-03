/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.tools.flicker.junit

import android.app.Instrumentation
import android.os.Bundle
import android.tools.Logger
import android.tools.Scenario
import android.tools.flicker.legacy.runner.FLICKER_RUNNER_TAG
import android.tools.traces.ConditionList
import android.tools.traces.ConditionsFactory
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.Description

/** Helper class for flicker transition rules */
object Utils {
    /**
     * Conditions that determine when the UI is in a stable and no windows or layers are animating
     * or changing state.
     */
    private val UI_STABLE_CONDITIONS =
        ConditionList(
            listOf(
                ConditionsFactory.isWMStateComplete(),
                ConditionsFactory.hasLayersAnimating().negate()
            )
        )

    internal fun doWaitForUiStabilize(wmHelper: WindowManagerStateHelper) {
        Logger.withTracing("doWaitForUiStabilize") {
            wmHelper.StateSyncBuilder().add(UI_STABLE_CONDITIONS).waitFor()
        }
    }

    internal fun notifyRunnerProgress(
        scenario: Scenario,
        msg: String,
        instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    ) {
        notifyRunnerProgress(scenario.key, msg, instrumentation)
    }

    internal fun notifyRunnerProgress(
        scenarioName: String,
        msg: String,
        instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    ) {
        Logger.d(FLICKER_RUNNER_TAG, "$scenarioName - $msg")
        val results = Bundle()
        results.putString(Instrumentation.REPORT_KEY_STREAMRESULT, "$msg\n")
        instrumentation.sendStatus(1, results)
    }

    internal fun expandDescription(description: Description?, suffix: String): Description? =
        Description.createTestDescription(
            description?.className,
            "${description?.displayName}-$suffix",
            description?.annotations?.toTypedArray()
        )
}
