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

package android.tools.traces.io

import android.tools.Logger
import android.tools.Scenario
import android.tools.ScenarioBuilder
import android.tools.Tag
import android.tools.Timestamp
import android.tools.Timestamps
import android.tools.io.FLICKER_IO_TAG
import android.tools.io.ResultArtifactDescriptor
import android.tools.io.RunStatus
import android.tools.io.TraceType
import android.tools.io.TransitionTimeRange
import java.io.File

/** Helper class to create run result artifact files */
open class ResultWriter {
    protected var scenario: Scenario = ScenarioBuilder().createEmptyScenario()
    private var runStatus: RunStatus = RunStatus.UNDEFINED
    private val files = mutableMapOf<ResultArtifactDescriptor, File>()
    private var transitionStartTime = Timestamps.min()
    private var transitionEndTime = Timestamps.max()
    private var executionError: Throwable? = null
    private var outputDir: File? = null

    /** Sets the artifact scenario to [_scenario] */
    fun forScenario(_scenario: Scenario) = apply { scenario = _scenario }

    /** Sets the artifact transition start time to [time] */
    fun setTransitionStartTime(time: Timestamp) = apply { transitionStartTime = time }

    /** Sets the artifact transition end time to [time] */
    fun setTransitionEndTime(time: Timestamp) = apply { transitionEndTime = time }

    /** Sets the artifact status as successfully executed transition ([RunStatus.RUN_EXECUTED]) */
    fun setRunComplete() = apply { runStatus = RunStatus.RUN_EXECUTED }

    /** Sets the dir where the artifact file will be stored to [dir] */
    fun withOutputDir(dir: File) = apply { outputDir = dir }

    /**
     * Sets the artifact status as failed executed transition ([RunStatus.RUN_FAILED])
     *
     * @param error that caused the transition to fail
     */
    fun setRunFailed(error: Throwable) = apply {
        runStatus = RunStatus.RUN_FAILED
        executionError = error
    }

    /**
     * Adds [artifact] to the result artifact
     *
     * @param traceType used when adding [artifact] to the result artifact
     * @param tag used when adding [artifact] to the result artifact
     */
    fun addTraceResult(traceType: TraceType, artifact: File, tag: String = Tag.ALL) = apply {
        Logger.d(
            FLICKER_IO_TAG,
            "Add trace result file=$artifact type=$traceType tag=$tag scenario=$scenario"
        )
        val fileDescriptor = ResultArtifactDescriptor(traceType, tag)
        files[fileDescriptor] = artifact
    }

    /** @return writes the result artifact to disk and returns it */
    open fun write(): IResultData {
        return Logger.withTracing("write") {
            val outputDir = outputDir
            requireNotNull(outputDir) { "Output dir not configured" }
            require(!scenario.isEmpty) { "Scenario shouldn't be empty" }

            if (runStatus == RunStatus.UNDEFINED) {
                Logger.w(FLICKER_IO_TAG, "Writing result with $runStatus run status")
            }

            val artifact =
                ArtifactBuilder()
                    .withScenario(scenario)
                    .withOutputDir(outputDir)
                    .withStatus(runStatus)
                    .withFiles(files)
                    .build()
            ResultData(
                artifact,
                TransitionTimeRange(transitionStartTime, transitionEndTime),
                executionError
            )
        }
    }
}
