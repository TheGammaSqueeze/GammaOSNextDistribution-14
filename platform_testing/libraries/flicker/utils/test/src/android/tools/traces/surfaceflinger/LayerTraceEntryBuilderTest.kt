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

package android.tools.traces.surfaceflinger

import android.tools.Timestamps
import android.tools.datatypes.ActiveBuffer
import android.tools.datatypes.Color
import android.tools.datatypes.Rect
import android.tools.datatypes.RectF
import android.tools.datatypes.Region
import android.tools.datatypes.Size
import android.tools.traces.surfaceflinger.Display.Companion.BLANK_LAYER_STACK
import android.tools.utils.CleanFlickerEnvironmentRule
import com.google.common.truth.Truth
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains [LayerTraceEntryBuilder] tests. To run this test: `atest
 * FlickerLibTest:LayerTraceEntryBuilderTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LayerTraceEntryBuilderTest {

    @Test
    fun createsEntryWithCorrectClockTime() {
        val builder =
            LayerTraceEntryBuilder()
                .setElapsedTimestamp(100)
                .setLayers(emptyList())
                .setDisplays(emptyList())
                .setVSyncId(123)
                .setRealToElapsedTimeOffsetNs(500)
        val entry = builder.build()
        Truth.assertThat(entry.elapsedTimestamp).isEqualTo(100)
        Truth.assertThat(entry.clockTimestamp).isEqualTo(600)

        Truth.assertThat(entry.timestamp.elapsedNanos).isEqualTo(Timestamps.empty().elapsedNanos)
        Truth.assertThat(entry.timestamp.systemUptimeNanos).isEqualTo(100)
        Truth.assertThat(entry.timestamp.unixNanos).isEqualTo(600)
    }

    @Test
    fun supportsMissingRealToElapsedTimeOffsetNs() {
        val builder =
            LayerTraceEntryBuilder()
                .setElapsedTimestamp(100)
                .setLayers(emptyList())
                .setDisplays(emptyList())
                .setVSyncId(123)
        val entry = builder.build()
        Truth.assertThat(entry.elapsedTimestamp).isEqualTo(100)
        Truth.assertThat(entry.clockTimestamp).isEqualTo(null)

        Truth.assertThat(entry.timestamp.elapsedNanos).isEqualTo(Timestamps.empty().elapsedNanos)
        Truth.assertThat(entry.timestamp.systemUptimeNanos).isEqualTo(100)
        Truth.assertThat(entry.timestamp.unixNanos).isEqualTo(Timestamps.empty().unixNanos)
    }

    @Test
    fun removesLayersFromOffDisplays() {
        val offDisplayStackId = BLANK_LAYER_STACK

        val layers =
            listOf(
                Layer.from(
                    name = "layer",
                    id = 1,
                    parentId = -1,
                    z = 1,
                    visibleRegion = Region.EMPTY,
                    activeBuffer = ActiveBuffer.EMPTY,
                    flags = 0,
                    bounds = RectF.EMPTY,
                    color = Color.EMPTY,
                    isOpaque = true,
                    shadowRadius = 0f,
                    cornerRadius = 0f,
                    screenBounds = RectF.EMPTY,
                    transform = Transform.EMPTY,
                    currFrame = 0,
                    effectiveScalingMode = 0,
                    bufferTransform = Transform.EMPTY,
                    hwcCompositionType = HwcCompositionType.HWC_TYPE_UNSPECIFIED,
                    backgroundBlurRadius = 0,
                    crop = null,
                    isRelativeOf = false,
                    zOrderRelativeOfId = 0,
                    stackId = offDisplayStackId,
                    excludesCompositionState = true
                )
            )

        val displays =
            listOf(
                Display.from(
                    id = 1,
                    name = "display",
                    layerStackId = offDisplayStackId,
                    size = Size.EMPTY,
                    layerStackSpace = Rect.EMPTY,
                    transform = Transform.EMPTY,
                    isVirtual = false,
                    dpiX = 270.0,
                    dpiY = 270.0,
                )
            )

        val builder =
            LayerTraceEntryBuilder()
                .setElapsedTimestamp(100)
                .setLayers(layers)
                .setDisplays(displays)
                .setVSyncId(123)
        val entry = builder.build()

        Truth.assertThat(entry.displays.all { it.isOff })
        Truth.assertThat(entry.flattenedLayers).isEmpty()
    }

    companion object {
        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
