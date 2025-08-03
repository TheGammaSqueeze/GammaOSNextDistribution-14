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

package platform.test.motion.golden

/**
 * A multivariate time series collected by a [MotionTestRule].
 *
 * A [TimeSeries] contains a number of distinct [Feature]s, each of which must contain exactly one
 * [DataPoint] per [frameIds], in the same order.
 */
data class TimeSeries(val frameIds: List<FrameId>, val features: List<Feature<*>>) {
    init {
        features.forEach {
            require(it.dataPoints.size == frameIds.size) {
                "Feature [${it.name}] includes ${it.dataPoints.size} data points, " +
                    "but ${frameIds.size} data points are expected"
            }
        }
    }
}

sealed interface FrameId {
    val label: String
}

/**
 * ID of recorded animation frame.
 *
 * [milliseconds] is relative to the recording start.
 */
data class TimestampFrameId(val milliseconds: Long) : FrameId {
    override val label: String
        get() = "${milliseconds}ms"
}

/** ID of UI frame before/after the animation. */
data class SupplementalFrameId(override val label: String) : FrameId

/**
 * Recorded [dataPoints], one per frame in the [TimeSeries].
 *
 * All [dataPoints] must be of the same [DataPointType].
 */
data class Feature<in T>(
    /** Test-specific, human readable name identifying the feature. */
    val name: String,
    /** Recorded data points of the feature. */
    val dataPoints: List<DataPoint<T>>
)
