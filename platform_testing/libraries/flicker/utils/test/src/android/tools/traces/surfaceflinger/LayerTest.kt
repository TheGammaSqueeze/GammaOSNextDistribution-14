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

import android.tools.Cache
import android.tools.datatypes.ActiveBuffer
import android.tools.datatypes.Color
import android.tools.datatypes.RectF
import android.tools.datatypes.Region
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** Contains [Layer] tests. To run this test: `atest FlickerLibTest:LayerTest` */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LayerTest {
    @Before
    fun before() {
        Cache.clear()
    }

    @Test
    fun hasVerboseFlagsProperty() {
        assertThat(makeLayerWithDefaults(0x0).verboseFlags).isEqualTo("")

        assertThat(makeLayerWithDefaults(0x1).verboseFlags).isEqualTo("HIDDEN (0x1)")

        assertThat(makeLayerWithDefaults(0x2).verboseFlags).isEqualTo("OPAQUE (0x2)")

        assertThat(makeLayerWithDefaults(0x40).verboseFlags).isEqualTo("SKIP_SCREENSHOT (0x40)")

        assertThat(makeLayerWithDefaults(0x80).verboseFlags).isEqualTo("SECURE (0x80)")

        assertThat(makeLayerWithDefaults(0x100).verboseFlags)
            .isEqualTo("ENABLE_BACKPRESSURE (0x100)")

        assertThat(makeLayerWithDefaults(0x200).verboseFlags)
            .isEqualTo("DISPLAY_DECORATION (0x200)")

        assertThat(makeLayerWithDefaults(0x400).verboseFlags)
            .isEqualTo("IGNORE_DESTINATION_FRAME (0x400)")

        assertThat(makeLayerWithDefaults(0xc3).verboseFlags)
            .isEqualTo("HIDDEN|OPAQUE|SKIP_SCREENSHOT|SECURE (0xc3)")
    }

    @Test
    fun useVisibleRegionIfCompositionStateIsAvailableForVisibility() {
        assertThat(
                makeLayerWithDefaults(
                        excludeCompositionState = false,
                        visibleRegion = Region.EMPTY,
                        activeBuffer = ActiveBuffer.from(100, 100, 1, 0)
                    )
                    .isVisible
            )
            .isFalse()
        assertThat(
                makeLayerWithDefaults(
                        excludeCompositionState = false,
                        visibleRegion = Region.from(0, 0, 100, 100),
                        activeBuffer = ActiveBuffer.from(100, 100, 1, 0)
                    )
                    .isVisible
            )
            .isTrue()
    }

    @Test
    fun fallbackOnLayerBoundsIfCompositionStateIsNotAvailableForVisibility() {
        assertThat(
                makeLayerWithDefaults(
                        excludeCompositionState = true,
                        bounds = RectF.EMPTY,
                        activeBuffer = ActiveBuffer.from(100, 100, 1, 0)
                    )
                    .isVisible
            )
            .isFalse()
        assertThat(
                makeLayerWithDefaults(
                        excludeCompositionState = true,
                        bounds = RectF.from(0f, 0f, 100f, 100f),
                        activeBuffer = ActiveBuffer.from(100, 100, 1, 0)
                    )
                    .isVisible
            )
            .isTrue()
        assertThat(
                makeLayerWithDefaults(
                        excludeCompositionState = true,
                        visibleRegion = Region.from(0, 0, 100, 100),
                        bounds = RectF.EMPTY,
                        activeBuffer = ActiveBuffer.from(100, 100, 1, 0)
                    )
                    .isVisible
            )
            .isFalse()
    }

    private fun makeLayerWithDefaults(
        flags: Int = 0x0,
        excludeCompositionState: Boolean = false,
        visibleRegion: Region = Region.EMPTY,
        bounds: RectF = RectF.EMPTY,
        activeBuffer: ActiveBuffer = ActiveBuffer.EMPTY
    ): Layer {
        return Layer.from(
            "",
            0,
            0,
            0,
            visibleRegion,
            activeBuffer,
            flags,
            bounds,
            Color.EMPTY,
            false,
            -1f,
            -1f,
            RectF.EMPTY,
            Transform.EMPTY,
            -1,
            -1,
            Transform.EMPTY,
            HwcCompositionType.HWC_TYPE_UNSPECIFIED,
            -1,
            null,
            false,
            -1,
            -1,
            excludeCompositionState
        )
    }
}
