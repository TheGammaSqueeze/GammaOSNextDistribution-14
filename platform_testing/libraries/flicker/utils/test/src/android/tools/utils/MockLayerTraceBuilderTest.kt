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

package android.tools.utils

import android.tools.datatypes.Rect
import com.google.common.truth.Truth
import org.junit.ClassRule
import org.junit.Test

/**
 * Test for the MockLayerTraceBuilder utilities. To run this test: `atest
 * FlickerLibTest:MockLayerTraceBuilderTest`
 */
class MockLayerTraceBuilderTest {
    @Test
    fun containerLayerIsInvisible() {
        val mockLayer =
            MockLayerBuilder("Mock Layer")
                .setAbsoluteBounds(Rect.from(0, 0, 200, 200))
                .setContainerLayer()
                .build()

        Truth.assertThat(mockLayer.isVisible).isFalse()
    }

    @Test
    fun childrenLayerInheritsParentBounds() {
        val mockLayer =
            MockLayerBuilder("Parent Mock Layer")
                .setContainerLayer()
                .setAbsoluteBounds(Rect.from(0, 0, 200, 200))
                .addChild(MockLayerBuilder("Child Mock Layer"))
                .build()

        Truth.assertThat(mockLayer.children.first().screenBounds).isEqualTo(mockLayer.screenBounds)
        Truth.assertThat(mockLayer.children.first().bounds).isEqualTo(mockLayer.bounds)
    }

    @Test
    fun canAddChildLayer() {
        val mockLayer =
            MockLayerBuilder("Parent Mock Layer")
                .setAbsoluteBounds(Rect.from(0, 0, 200, 200))
                .addChild(MockLayerBuilder("Child Mock Layer"))
                .build()

        Truth.assertThat(mockLayer.children).isNotEmpty()
    }

    @Test
    fun canSetLayerVisibility() {
        val mockLayer =
            MockLayerBuilder("Mock Layer")
                .setAbsoluteBounds(Rect.from(0, 0, 200, 200))
                .setInvisible()
                .build()

        Truth.assertThat(mockLayer.isVisible).isFalse()
    }

    @Test
    fun invisibleLayerHasNoVisibleBounds() {
        val mockLayer =
            MockLayerBuilder("Mock Layer")
                .setAbsoluteBounds(Rect.from(0, 0, 200, 200))
                .setInvisible()
                .build()

        Truth.assertThat(mockLayer.visibleRegion?.isEmpty ?: true).isTrue()
    }

    companion object {
        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }
}
