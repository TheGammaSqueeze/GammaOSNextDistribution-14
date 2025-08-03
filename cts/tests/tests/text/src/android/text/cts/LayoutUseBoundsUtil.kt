/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package android.text.cts

import android.graphics.Canvas
import android.text.Layout
import com.google.common.truth.Truth.assertThat
import org.mockito.ArgumentCaptor
import org.mockito.Mockito

object LayoutUseBoundsUtil {

    fun getDrawingHorizontalOffset(layout: Layout): Float {
        val canvas = Mockito.mock(Canvas::class.java)
        layout.draw(canvas)
        val dxArgumentCaptor = ArgumentCaptor.forClass(Float::class.java)
        val dyArgumentCaptor = ArgumentCaptor.forClass(Float::class.java)
        Mockito.verify(canvas, Mockito.atLeast(0))
                .translate(dxArgumentCaptor.capture(), dyArgumentCaptor.capture())
        val dyArgs = dyArgumentCaptor.allValues
        val dxArgs = dxArgumentCaptor.allValues

        if (dyArgs.size == 0) {
            return 0f // Treating no call of Canvas#translate as 0 px shifting.
        }

        assertThat(dyArgs[0]).isEqualTo(0)
        return dxArgs[0]
    }
}
