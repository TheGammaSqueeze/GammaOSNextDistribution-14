/*
 * Copyright 2021 The Android Open Source Project
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

package android.uirendering.cts.testclasses

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.NinePatch
import android.uirendering.cts.R
import android.uirendering.cts.bitmapcomparers.MSSIMComparer
import android.uirendering.cts.testinfrastructure.ActivityTestBase
import android.uirendering.cts.testinfrastructure.CanvasClient
import androidx.test.runner.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class NinePatchTests : ActivityTestBase() {

    class NinePatchCanvasClient(
        private val ninepatch: NinePatch,
        private val paint: Paint?
    ) : CanvasClient {
        override fun draw(canvas: Canvas, width: Int, height: Int) {
            val scale = 6.0f
            canvas.scale(scale, scale)
            ninepatch.draw(canvas, Rect(0, 0, 15, 15), paint)
        }
    }

    @Test
    fun testNinePatchAlwaysFiltersInHW() {
        val np = with(ImageDecoder.createSource(activity.resources, R.drawable.padding_0)) {
            val bitmap = ImageDecoder.decodeBitmap(this)
            NinePatch(bitmap, bitmap.ninePatchChunk)
        }

        val hw = true
        with(createTest()) {
            for (paint in arrayOf(null, Paint(), Paint().apply { isFilterBitmap = false })) {
                addCanvasClientWithoutUsingPicture(NinePatchCanvasClient(np, paint), hw)
            }
            runWithComparer(MSSIMComparer(0.99))
        }

        np.bitmap.recycle()
    }

    @Test
    fun testNinePatchRespectsFilterBitmapFlagInSW() {
        val np = with(ImageDecoder.createSource(activity.resources, R.drawable.padding_0)) {
            val bitmap = ImageDecoder.decodeBitmap(this) {
                decoder, info, src ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            NinePatch(bitmap, bitmap.ninePatchChunk)
        }

        fun makeBitmap(paint: Paint?) = Bitmap.createBitmap(TEST_WIDTH, TEST_HEIGHT,
            Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            NinePatchCanvasClient(np, paint).draw(canvas, TEST_WIDTH, TEST_HEIGHT)
        }

        val filtered = makeBitmap(Paint())
        val unfiltered = makeBitmap(Paint().apply { isFilterBitmap = false })
        val noPaint = makeBitmap(null)

        assertFalse(filtered.sameAs(unfiltered))
        assertTrue(unfiltered.sameAs(noPaint))

        for (bitmap in arrayOf(filtered, unfiltered, noPaint, np.bitmap)) {
            bitmap.recycle()
        }
    }
}
