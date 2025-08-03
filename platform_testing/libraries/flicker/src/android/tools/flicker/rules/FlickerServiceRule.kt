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

package android.tools.flicker.rules

import android.platform.test.rule.TestWatcher
import android.tools.AndroidLogger
import android.tools.CrossPlatform
import android.tools.FLICKER_TAG
import android.tools.Logger
import android.tools.TimestampFactory
import android.tools.flicker.FlickerConfig
import android.tools.flicker.FlickerService
import android.tools.flicker.FlickerServiceResultsCollector
import android.tools.flicker.FlickerServiceTracesCollector
import android.tools.flicker.IFlickerServiceResultsCollector
import android.tools.flicker.annotation.FlickerTest
import android.tools.flicker.config.FlickerConfig
import android.tools.flicker.config.FlickerServiceConfig
import android.tools.flicker.config.ScenarioId
import android.tools.traces.formatRealTimestamp
import android.tools.traces.getDefaultFlickerOutputDir
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth
import org.junit.AssumptionViolatedException
import org.junit.runner.Description
import org.junit.runner.notification.Failure

/**
 * A test rule that runs Flicker as a Service on the tests this rule is applied to.
 *
 * Note there are performance implications to using this test rule in tests. Tracing will be enabled
 * during the test which will slow down everything. So if the test is performance critical then an
 * alternative should be used.
 *
 * @see TODO for examples on how to use this test rule in your own tests
 */
open class FlickerServiceRule
@JvmOverloads
constructor(
    enabled: Boolean = true,
    failTestOnFlicker: Boolean = enabled,
    config: FlickerConfig = FlickerConfig().use(FlickerServiceConfig.DEFAULT),
    private val metricsCollector: IFlickerServiceResultsCollector =
        FlickerServiceResultsCollector(
            flickerService = FlickerService(config),
            tracesCollector = FlickerServiceTracesCollector(getDefaultFlickerOutputDir()),
            instrumentation = InstrumentationRegistry.getInstrumentation()
        ),
) : TestWatcher() {
    private val enabled: Boolean =
        InstrumentationRegistry.getArguments().getString("faas:enabled")?.let { it.toBoolean() }
            ?: enabled

    private val failTestOnFlicker: Boolean =
        InstrumentationRegistry.getArguments().getString("faas:failTestOnFlicker")?.let {
            it.toBoolean()
        }
            ?: failTestOnFlicker

    private var testFailed = false
    private var testSkipped = false

    init {
        CrossPlatform.setLogger(AndroidLogger())
            .setTimestampFactory(TimestampFactory { formatRealTimestamp(it) })
    }

    /** Invoked when a test is about to start */
    public override fun starting(description: Description) {
        if (shouldRun(description)) {
            handleStarting(description)
        }
    }

    /** Invoked when a test succeeds */
    public override fun succeeded(description: Description) {
        if (shouldRun(description)) {
            handleSucceeded(description)
        }
    }

    /** Invoked when a test fails */
    public override fun failed(e: Throwable?, description: Description) {
        if (shouldRun(description)) {
            handleFailed(e, description)
        }
    }

    /** Invoked when a test is skipped due to a failed assumption. */
    public override fun skipped(e: AssumptionViolatedException, description: Description) {
        if (shouldRun(description)) {
            handleSkipped(e, description)
        }
    }

    /** Invoked when a test method finishes (whether passing or failing) */
    public override fun finished(description: Description) {
        if (shouldRun(description)) {
            handleFinished(description)
        }
    }

    private fun handleStarting(description: Description) {
        Logger.i(LOG_TAG, "Test starting $description")
        metricsCollector.testStarted(description)
        testFailed = false
        testSkipped = false
    }

    private fun handleSucceeded(description: Description) {
        Logger.i(LOG_TAG, "Test succeeded $description")
    }

    private fun handleFailed(e: Throwable?, description: Description) {
        Logger.e(LOG_TAG, "$description test failed with", e)
        metricsCollector.testFailure(Failure(description, e))
        testFailed = true
    }

    private fun handleSkipped(e: AssumptionViolatedException, description: Description) {
        Logger.i(LOG_TAG, "Test skipped $description with", e)
        metricsCollector.testSkipped(description)
        testSkipped = true
    }

    private fun shouldRun(description: Description?): Boolean {
        // Only run FaaS if test rule is enabled and on tests with FlickerTest annotation if it's
        // used within the class, otherwise run on all tests
        return when {
            !enabled -> false
            // Nullable description case is only handled because of b/302018924.
            description != null ->
                (testClassHasFlickerTestAnnotations(description.testClass) ||
                    description.annotations.none { it is FlickerTest })
            else -> true
        }
    }

    private fun testClassHasFlickerTestAnnotations(testClass: Class<*>): Boolean {
        return testClass.methods.flatMap { it.annotations.asList() }.any { it is FlickerTest }
    }

    private fun handleFinished(description: Description) {
        Logger.i(LOG_TAG, "Test finished $description")
        metricsCollector.testFinished(description)
        for (executionError in metricsCollector.executionErrors) {
            Logger.e(LOG_TAG, "FaaS reported execution errors", executionError)
        }
        if (testSkipped || testFailed || metricsCollector.executionErrors.isNotEmpty()) {
            // If we had an execution error or the underlying test failed or was skipped, then we
            // have no guarantees about the correctness of the flicker assertions and detect
            // scenarios, so we should not check those and instead return immediately.
            return
        }
        val failedMetrics = metricsCollector.resultsForTest(description).filter { it.failed }
        val assertionErrors = failedMetrics.flatMap { it.assertionErrors }
        assertionErrors.forEach {
            Logger.e(LOG_TAG, "FaaS reported an assertion failure:")
            Logger.e(LOG_TAG, it.message)
            Logger.e(LOG_TAG, it.stackTraceToString())
        }

        if (failTestOnFlicker && testContainsFlicker(description)) {
            throw assertionErrors.firstOrNull() ?: error("Unexpectedly missing assertion error")
        }
        val flickerTestAnnotation: FlickerTest? =
            description.annotations.filterIsInstance<FlickerTest>().firstOrNull()
        if (failTestOnFlicker && flickerTestAnnotation != null) {
            val detectedScenarios = metricsCollector.detectedScenariosForTest(description)
            Truth.assertThat(detectedScenarios)
                .containsAtLeastElementsIn(flickerTestAnnotation.expected.map { ScenarioId(it) })
        }
    }

    private fun testContainsFlicker(description: Description): Boolean {
        val resultsForTest = metricsCollector.resultsForTest(description)
        return resultsForTest.any { it.failed }
    }

    companion object {
        const val LOG_TAG = "$FLICKER_TAG-ServiceRule"
    }
}
