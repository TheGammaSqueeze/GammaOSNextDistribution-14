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
import android.device.collectors.util.SendToInstrumentation
import android.os.Bundle
import android.tools.Cache
import android.tools.flicker.AssertionInvocationGroup
import android.tools.flicker.FlickerServiceResultsCollector.Companion.getKeyForAssertionResult
import android.tools.flicker.assertions.ScenarioAssertion
import java.lang.reflect.Method
import org.junit.Assume
import org.junit.runner.Description

class FlickerServiceCachedTestCase(
    private val assertion: ScenarioAssertion,
    method: Method,
    private val skipNonBlocking: Boolean,
    private val isLast: Boolean,
    injectedBy: IFlickerJUnitDecorator,
    private val instrumentation: Instrumentation,
    paramString: String = "",
) : InjectedTestCase(method, "FaaS_$assertion$paramString", injectedBy) {
    override fun execute(description: Description) {
        try {
            val result = assertion.execute()

            val metricBundle = Bundle()
            metricBundle.putString(
                getKeyForAssertionResult(result),
                if (result.passed) "0" else "1"
            )
            SendToInstrumentation.sendBundle(instrumentation, metricBundle)

            Assume.assumeTrue(
                "FaaS Test was non blocking - skipped",
                !skipNonBlocking || result.stabilityGroup == AssertionInvocationGroup.BLOCKING
            )
            result.assertionErrors.firstOrNull()?.let { throw it }
        } catch (e: Throwable) {
            throw e
        } finally {
            if (isLast) {
                Cache.clear()
            }
        }
    }
}
