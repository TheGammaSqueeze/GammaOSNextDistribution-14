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

import androidx.test.InstrumentationRegistry

import android.content.res.AssetManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.uirendering.cts.bitmapcomparers.MSSIMComparer
import android.uirendering.cts.bitmapverifiers.BitmapVerifier
import android.uirendering.cts.bitmapverifiers.GoldenImageVerifier
import android.uirendering.cts.testinfrastructure.ActivityTestBase
import android.uirendering.cts.testinfrastructure.CanvasClient
import android.view.View
import junitparams.JUnitParamsRunner
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(JUnitParamsRunner::class)
class AnimatedImageDrawableTest : ActivityTestBase() {
    private fun getAssets(): AssetManager {
        return InstrumentationRegistry.getTargetContext().getAssets()
    }

    private val TEST_FILE = "stoplight.webp"

    private fun makeVerifier(testName: String, mssim: Double): BitmapVerifier {
        val source = ImageDecoder.createSource(getAssets(),
                "AnimatedImageDrawableTest/${testName}_golden.png")
        val bitmap = ImageDecoder.decodeBitmap(source) {
            decoder, info, source ->
                // Use software so the verifier can read the pixels.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val verifier = GoldenImageVerifier(bitmap, MSSIMComparer(mssim))

        // The Verifier stored the pixels in an array, so the Bitmap is no longer need.
        bitmap.recycle()
        return verifier
    }

    /**
     * Test AnimatedImageDrawable#setBounds.
     *
     * @param testName Name of the test; used to find the golden image.
     * @param mssim Structural Similarity Index for how similar the animated version should look to
     *              the golden image. It should be close to 1.0; differences come from the drawing
     *              backend (software vs hardware vs Picture).
     * @param resizer Function that calls setBounds (and potentially other modifications) on the
     *             provided drawable based on the provided width and height of the Canvas.
     * @param listener OnHeaderDecodedListener to pass to decodeDrawable.
     */
    private fun internalTestBoundsAndListener(
        testName: String,
        mssim: Double,
        resizer: (Drawable, Int, Int) -> Unit,
        listener: ImageDecoder.OnHeaderDecodedListener? = null
    ) {
        val source = ImageDecoder.createSource(getAssets(), TEST_FILE)
        class Client(val drawable: Drawable) : CanvasClient {
            override fun draw(canvas: Canvas, width: Int, height: Int) {
                canvas.drawColor(Color.WHITE)
                resizer(drawable, width, height)
                drawable.draw(canvas)
            }
        }
        val animatedDrawable = if (listener == null) ImageDecoder.decodeDrawable(source)
                else ImageDecoder.decodeDrawable(source, listener)
        createTest().addCanvasClient(Client(animatedDrawable)).runWithVerifier(
                makeVerifier(testName, mssim))
    }

    private fun internalTestBounds(
        testName: String,
        mssim: Double,
        resizer: (Drawable, Int, Int) -> Unit
    ) = internalTestBoundsAndListener(testName, mssim, resizer)

    @Test
    fun testSetBounds() = internalTestBounds("testSetBounds", .998) {
        drawable, width, height ->
            drawable.setBounds(1, 1, width - 1, height - 1)
    }

    @Test
    fun testSetBounds2() = internalTestBounds("testSetBounds2", .999) {
        drawable, width, height ->
            drawable.setBounds(width / 2, height / 2, width, height)
    }

    @Test
    fun testSetBoundsMirrored() = internalTestBounds("testSetBoundsMirrored", .999) {
        drawable, width, height ->
            drawable.isAutoMirrored = true
            drawable.layoutDirection = View.LAYOUT_DIRECTION_RTL
            drawable.setBounds(0, 0, width / 2, height / 2)
    }

    @Test
    fun testSetBoundsRTLUnmirrored() = internalTestBounds("testSetBoundsRTLUnmirrored", .999) {
        drawable, width, height ->
            drawable.isAutoMirrored = false
            drawable.layoutDirection = View.LAYOUT_DIRECTION_RTL
            drawable.setBounds(0, 0, width / 2, height / 2)
    }

    @Test
    fun testSetBoundsLTRMirrored() = internalTestBounds("testSetBoundsLTRMirrored", .999) {
        drawable, width, height ->
            drawable.isAutoMirrored = false
            drawable.layoutDirection = View.LAYOUT_DIRECTION_LTR
            drawable.setBounds(0, 0, width / 2, height / 2)
    }

    @Test
    fun testSetBoundsAlpha() = internalTestBounds("testSetBoundsAlpha", .996) {
        drawable, width, height ->
            drawable.alpha = 128
            drawable.setBounds(5, 5, width - 5, height - 5)
    }

    @Test
    fun testSetBoundsAlphaMirrored() = internalTestBounds("testSetBoundsAlphaMirrored", .999) {
        drawable, width, height ->
            drawable.alpha = 128
            drawable.isAutoMirrored = true
            drawable.layoutDirection = View.LAYOUT_DIRECTION_RTL
            drawable.setBounds(width / 3, 0, (width * 2 / 3.0).roundToInt(), height / 2)
    }

    @Test
    fun testSetBoundsColorFilter() = internalTestBounds("testSetBoundsColorFilter", .999) {
        drawable, width, height ->
            drawable.colorFilter = LightingColorFilter(Color.RED, Color.BLUE)
            drawable.setBounds(7, 7, width - 7, height - 7)
    }

    @Test
    fun testSetBoundsCrop() = internalTestBoundsAndListener("testSetBoundsCrop", .996, {
        drawable, width, height ->
            drawable.setBounds(2, 2, width - 2, height - 2)
    }, {
        decoder, info, source ->
            decoder.setCrop(Rect(100, 0, 145, 55))
    })

    @Test
    fun testSetBoundsPostProcess() = internalTestBoundsAndListener("testSetBoundsPostProcess", .996,
    {
        drawable, width, height ->
            drawable.setBounds(3, 3, width - 3, height - 3)
    }, {
        decoder, info, source ->
            decoder.setPostProcessor {
                canvas ->
                    val path = Path()
                    path.fillType = Path.FillType.INVERSE_EVEN_ODD
                    val width = canvas.getWidth().toFloat()
                    val height = canvas.getHeight().toFloat()
                    path.addRoundRect(0.0f, 0.0f, width, height, 20.0f, 20.0f, Path.Direction.CW)
                    val paint = Paint()
                    paint.setAntiAlias(true)
                    paint.color = Color.TRANSPARENT
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
                    canvas.drawPath(path, paint)
                    PixelFormat.TRANSLUCENT
            }
    })
}
