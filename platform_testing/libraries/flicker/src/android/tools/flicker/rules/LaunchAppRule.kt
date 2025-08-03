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

package android.tools.flicker.rules

import android.app.Instrumentation
import android.tools.FLICKER_TAG
import android.tools.Logger
import android.tools.device.apphelpers.StandardAppHelper
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Launches an app before the test
 *
 * @param instrumentation Instrumentation mechanism to use
 * @param wmHelper WM/SF synchronization helper
 * @param appHelper App to launch
 * @param clearCacheAfterParsing If the caching used while parsing the proto should be
 *
 * ```
 *                               cleared or remain in memory
 * ```
 */
class LaunchAppRule
@JvmOverloads
constructor(
    private val appHelper: StandardAppHelper,
    private val instrumentation: Instrumentation = appHelper.instrumentation,
    private val clearCacheAfterParsing: Boolean = true,
    private val wmHelper: WindowManagerStateHelper =
        WindowManagerStateHelper(clearCacheAfterParsing = clearCacheAfterParsing)
) : TestWatcher() {
    @JvmOverloads
    constructor(
        componentMatcher: ComponentNameMatcher,
        appName: String = "",
        instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation(),
        clearCache: Boolean = true,
        wmHelper: WindowManagerStateHelper =
            WindowManagerStateHelper(clearCacheAfterParsing = clearCache)
    ) : this(
        StandardAppHelper(instrumentation, appName, componentMatcher),
        instrumentation,
        clearCache,
        wmHelper
    )

    override fun starting(description: Description?) {
        Logger.withTracing("LaunchAppRule:starting") {
            Logger.v(FLICKER_TAG, "Launching app $appHelper")
            appHelper.launchViaIntent()
            appHelper.exit(wmHelper)
        }
    }
}
