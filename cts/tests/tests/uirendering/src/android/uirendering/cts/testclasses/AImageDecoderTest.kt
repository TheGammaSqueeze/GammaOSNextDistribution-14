/*
 * Copyright 2020 The Android Open Source Project
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

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.uirendering.cts.bitmapcomparers.MSSIMComparer
import android.uirendering.cts.bitmapverifiers.BitmapVerifier
import android.uirendering.cts.bitmapverifiers.ColorVerifier
import android.uirendering.cts.bitmapverifiers.GoldenImageVerifier
import android.uirendering.cts.bitmapverifiers.RectVerifier
import android.uirendering.cts.bitmapverifiers.RegionVerifier
import android.uirendering.cts.differencevisualizers.PassFailVisualizer
import android.uirendering.cts.util.BitmapDumper
import androidx.test.InstrumentationRegistry
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(JUnitParamsRunner::class)
class AImageDecoderTest {
    init {
        System.loadLibrary("ctsuirendering_jni")
    }

    private val ANDROID_IMAGE_DECODER_SUCCESS = 0
    private val ANDROID_IMAGE_DECODER_INVALID_CONVERSION = -3
    private val ANDROID_IMAGE_DECODER_INVALID_SCALE = -4
    private val ANDROID_IMAGE_DECODER_BAD_PARAMETER = -5
    private val ANDROID_IMAGE_DECODER_FINISHED = -10
    private val ANDROID_IMAGE_DECODER_INVALID_STATE = -11

    private val DEBUG_CAPTURE_IMAGES = false

    private fun getAssets(): AssetManager {
        return InstrumentationRegistry.getTargetContext().getAssets()
    }

    @Test
    fun testNullDecoder() = nTestNullDecoder()

    @Test
    fun testToString() = nTestToString()

    private enum class Crop {
        Top, // Crop a section of the image that contains the top
        Left, // Crop a section of the image that contains the left
        None,
    }

    /**
     * Helper class to decode a scaled, cropped image to compare to AImageDecoder.
     *
     * Includes properties for getting the right scale and crop values to use in
     * AImageDecoder.
     */
    private inner class DecodeAndCropper constructor(
        image: String,
        scale: Float,
        crop: Crop
    ) {
        val bitmap: Bitmap
        var targetWidth: Int = 0
            private set
        var targetHeight: Int = 0
            private set
        val cropRect: Rect?

        init {
            val source = ImageDecoder.createSource(getAssets(), image)
            val tmpBm = ImageDecoder.decodeBitmap(source) {
                decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    if (scale == 1.0f) {
                        targetWidth = info.size.width
                        targetHeight = info.size.height
                    } else {
                        targetWidth = (info.size.width * scale).toInt()
                        targetHeight = (info.size.height * scale).toInt()
                        decoder.setTargetSize(targetWidth, targetHeight)
                    }
            }
            cropRect = when (crop) {
                Crop.Top -> Rect((targetWidth / 3.0f).toInt(), 0,
                        (targetWidth * 2 / 3.0f).toInt(),
                        (targetHeight / 2.0f).toInt())
                Crop.Left -> Rect(0, (targetHeight / 3.0f).toInt(),
                        (targetWidth / 2.0f).toInt(),
                        (targetHeight * 2 / 3.0f).toInt())
                Crop.None -> null
            }
            if (cropRect == null) {
                bitmap = tmpBm
            } else {
                // Crop using Bitmap, rather than ImageDecoder, because it uses
                // the same code as AImageDecoder for cropping.
                bitmap = Bitmap.createBitmap(tmpBm, cropRect.left, cropRect.top,
                        cropRect.width(), cropRect.height())
                if (bitmap !== tmpBm) {
                    tmpBm.recycle()
                }
            }
        }
    }

    // Create a Bitmap with the same size and colorspace as bitmap.
    private fun makeEmptyBitmap(bitmap: Bitmap) = Bitmap.createBitmap(
        bitmap.width,
        bitmap.height,
                bitmap.config!!,
        true,
        bitmap.colorSpace!!
    )

    private fun setCrop(decoder: Long, rect: Rect): Int = with(rect) {
        nSetCrop(decoder, left, top, right, bottom)
    }

    /**
     * Test that all frames in the image look as expected.
     *
     * @param image Name of the animated image file.
     * @param frameName Template for creating the name of the expected image
     *                  file for the i'th frame.
     * @param numFrames Total number of frames in the animated image.
     * @param scaleFactor The factor by which to scale the image.
     * @param crop The crop setting to use.
     * @param mssimThreshold The minimum MSSIM value to accept as similar. Some
     *                       images do not match exactly, but they've been
     *                       manually verified to look the same.
     * @param testName Optional name of the calling test for BitmapDumper.
     */
    private fun decodeAndCropFrames(
        image: String,
        frameName: String,
        numFrames: Int,
        scaleFactor: Float,
        crop: Crop,
        mssimThreshold: Double,
        testName: String = ""
    ) {
        val decodeAndCropper = DecodeAndCropper(image, scaleFactor, crop)
        var expectedBm = decodeAndCropper.bitmap

        val asset = nOpenAsset(getAssets(), image)
        val decoder = nCreateFromAsset(asset)
        if (scaleFactor != 1.0f) {
            with(decodeAndCropper) {
                assertEquals(
                    nSetTargetSize(decoder, targetWidth, targetHeight),
                        ANDROID_IMAGE_DECODER_SUCCESS
                )
            }
        }
        with(decodeAndCropper.cropRect) {
            this?.let {
                assertEquals(setCrop(decoder, this), ANDROID_IMAGE_DECODER_SUCCESS)
            }
        }

        val testBm = makeEmptyBitmap(decodeAndCropper.bitmap)

        var i = 0
        while (true) {
            nDecode(decoder, testBm, ANDROID_IMAGE_DECODER_SUCCESS)
            val verifier = GoldenImageVerifier(expectedBm, MSSIMComparer(mssimThreshold))
            if (!verifier.verify(testBm)) {
                if (DEBUG_CAPTURE_IMAGES) {
                    BitmapDumper.dumpBitmaps(expectedBm, testBm, PassFailVisualizer())
                }
                fail("$image has mismatch in frame $i")
            }
            expectedBm.recycle()

            i++
            when (val result = nAdvanceFrame(decoder)) {
                ANDROID_IMAGE_DECODER_SUCCESS -> {
                    assertTrue(i < numFrames, "Unexpected frame $i in $image")
                    expectedBm = DecodeAndCropper(frameName.format(i), scaleFactor, crop).bitmap
                }
                ANDROID_IMAGE_DECODER_FINISHED -> {
                    assertEquals(i, numFrames, "Expected $numFrames frames in $image; found $i")
                    break
                }
                else -> fail("Unexpected error $result when advancing $image to frame $i")
            }
        }

        nDeleteDecoder(decoder)
        nCloseAsset(asset)
    }

    fun animationsAndFrames() = arrayOf(
        arrayOf<Any>("animated.gif", "animated_%03d.gif", 4),
        arrayOf<Any>("animated_webp.webp", "animated_%03d.gif", 4),
        arrayOf<Any>("required_gif.gif", "required_%03d.png", 7),
        arrayOf<Any>("required_webp.webp", "required_%03d.png", 7),
        arrayOf<Any>("alphabetAnim.gif", "alphabetAnim_%03d.png", 13),
        arrayOf<Any>("blendBG.webp", "blendBG_%03d.png", 7),
        arrayOf<Any>("stoplight.webp", "stoplight_%03d.png", 3)
    )

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testDecodeFrames(image: String, frameName: String, numFrames: Int) {
        decodeAndCropFrames(image, frameName, numFrames, 1.0f, Crop.None, .955)
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testDecodeFramesScaleDown(image: String, frameName: String, numFrames: Int) {
        // Perceptually, this image looks reasonable, but the MSSIM is low enough to be
        // meaningless. It has been manually verified.
        if (image == "alphabetAnim.gif") return
        decodeAndCropFrames(
            image,
            frameName,
            numFrames,
            .5f,
            Crop.None,
            .749,
                "testDecodeFramesScaleDown"
        )
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testDecodeFramesScaleDown2(image: String, frameName: String, numFrames: Int) {
        decodeAndCropFrames(image, frameName, numFrames, .75f, Crop.None, .749)
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testDecodeFramesScaleUp(image: String, frameName: String, numFrames: Int) {
        decodeAndCropFrames(image, frameName, numFrames, 2.0f, Crop.None, .875)
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testDecodeFramesAndCropTop(image: String, frameName: String, numFrames: Int) {
        decodeAndCropFrames(image, frameName, numFrames, 1.0f, Crop.Top, .934)
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testDecodeFramesAndCropTopScaleDown(image: String, frameName: String, numFrames: Int) {
        // Perceptually, this image looks reasonable, but the MSSIM is low enough to be
        // meaningless. It has been manually verified.
        if (image == "alphabetAnim.gif") return
        decodeAndCropFrames(
            image,
            frameName,
            numFrames,
            .5f,
            Crop.Top,
            .749,
                "testDecodeFramesAndCropTopScaleDown"
        )
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testDecodeFramesAndCropTopScaleDown2(image: String, frameName: String, numFrames: Int) {
        decodeAndCropFrames(image, frameName, numFrames, .75f, Crop.Top, .749)
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testDecodeFramesAndCropTopScaleUp(image: String, frameName: String, numFrames: Int) {
        decodeAndCropFrames(image, frameName, numFrames, 3.0f, Crop.Top, .908)
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testDecodeFramesAndCropLeft(image: String, frameName: String, numFrames: Int) {
        decodeAndCropFrames(image, frameName, numFrames, 1.0f, Crop.Left, .924)
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testDecodeFramesAndCropLeftScaleDown(image: String, frameName: String, numFrames: Int) {
        // Perceptually, this image looks reasonable, but the MSSIM is low enough to be
        // meaningless. It has been manually verified.
        if (image == "alphabetAnim.gif") return
        decodeAndCropFrames(
            image,
            frameName,
            numFrames,
            .5f,
            Crop.Left,
            .596,
                "testDecodeFramesAndCropLeftScaleDown"
        )
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testDecodeFramesAndCropLeftScaleDown2(image: String, frameName: String, numFrames: Int) {
        decodeAndCropFrames(image, frameName, numFrames, .75f, Crop.Left, .596)
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testDecodeFramesAndCropLeftScaleUp(image: String, frameName: String, numFrames: Int) {
        decodeAndCropFrames(image, frameName, numFrames, 3.0f, Crop.Left, .894)
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testRewind(image: String, unused: String, numFrames: Int) {
        val frame0 = with(ImageDecoder.createSource(getAssets(), image)) {
            ImageDecoder.decodeBitmap(this) {
                decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }

        // Regardless of the current frame, calling rewind and decoding should
        // look like frame_0.
        for (framesBeforeReset in 0 until numFrames) {
            val asset = nOpenAsset(getAssets(), image)
            val decoder = nCreateFromAsset(asset)
            val testBm = makeEmptyBitmap(frame0)
            for (i in 1..framesBeforeReset) {
                nDecode(decoder, testBm, ANDROID_IMAGE_DECODER_SUCCESS)
                assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, nAdvanceFrame(decoder))
            }

            assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, nRewind(decoder))
            nDecode(decoder, testBm, ANDROID_IMAGE_DECODER_SUCCESS)

            val verifier = GoldenImageVerifier(frame0, MSSIMComparer(1.0))
            assertTrue(
                verifier.verify(testBm),
                "Mismatch in $image after " +
                        "decoding $framesBeforeReset and then rewinding!"
            )

            nDeleteDecoder(decoder)
            nCloseAsset(asset)
        }
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testDecodeReturnsFinishedAtEnd(image: String, unused: String, numFrames: Int) {
        val asset = nOpenAsset(getAssets(), image)
        val decoder = nCreateFromAsset(asset)
        for (i in 0 until (numFrames - 1)) {
            assertEquals(nAdvanceFrame(decoder), ANDROID_IMAGE_DECODER_SUCCESS)
        }

        assertEquals(nAdvanceFrame(decoder), ANDROID_IMAGE_DECODER_FINISHED)

        // Create a Bitmap to decode into and verify that no decoding occurred.
        val width = nGetWidth(decoder)
        val height = nGetHeight(decoder)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888, true)
        nDecode(decoder, bitmap, ANDROID_IMAGE_DECODER_FINISHED)

        nDeleteDecoder(decoder)
        nCloseAsset(asset)

        // Every pixel should be transparent black, as no decoding happened.
        assertTrue(ColorVerifier(0, 0).verify(bitmap))
        bitmap.recycle()
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testAdvanceReturnsFinishedAtEnd(image: String, unused: String, numFrames: Int) {
        val asset = nOpenAsset(getAssets(), image)
        val decoder = nCreateFromAsset(asset)
        for (i in 0 until (numFrames - 1)) {
            assertEquals(nAdvanceFrame(decoder), ANDROID_IMAGE_DECODER_SUCCESS)
        }

        for (i in 0..1000) {
            assertEquals(nAdvanceFrame(decoder), ANDROID_IMAGE_DECODER_FINISHED)
        }

        nDeleteDecoder(decoder)
        nCloseAsset(asset)
    }

    fun nonAnimatedAssets() = arrayOf(
        "blue-16bit-prophoto.png",
        "green-p3.png",
        "linear-rgba16f.png",
        "orange-prophotorgb.png",
        "animated_001.gif",
        "animated_002.gif",
        "sunset1.jpg"
    )

    @Test
    @Parameters(method = "nonAnimatedAssets")
    fun testAdvanceFrameFailsNonAnimated(image: String) {
        val asset = nOpenAsset(getAssets(), image)
        val decoder = nCreateFromAsset(asset)
        assertEquals(ANDROID_IMAGE_DECODER_BAD_PARAMETER, nAdvanceFrame(decoder))
        nDeleteDecoder(decoder)
        nCloseAsset(asset)
    }

    @Test
    @Parameters(method = "nonAnimatedAssets")
    fun testRewindFailsNonAnimated(image: String) {
        val asset = nOpenAsset(getAssets(), image)
        val decoder = nCreateFromAsset(asset)
        assertEquals(ANDROID_IMAGE_DECODER_BAD_PARAMETER, nRewind(decoder))
        nDeleteDecoder(decoder)
        nCloseAsset(asset)
    }

    fun imagesAndSetters(): ArrayList<Any> {
        val setters = arrayOf<(Long) -> Int>(
            { decoder -> nSetUnpremultipliedRequired(decoder, true) },
            { decoder ->
                val rect = Rect(0, 0, nGetWidth(decoder) / 2, nGetHeight(decoder) / 2)
                setCrop(decoder, rect)
            },
            { decoder ->
                val ANDROID_BITMAP_FORMAT_RGBA_F16 = 9
                nSetAndroidBitmapFormat(decoder, ANDROID_BITMAP_FORMAT_RGBA_F16)
            },
            { decoder ->
                nSetTargetSize(decoder, nGetWidth(decoder) / 2, nGetHeight(decoder) / 2)
            },
            { decoder ->
                val ADATASPACE_DISPLAY_P3 = 143261696
                nSetDataSpace(decoder, ADATASPACE_DISPLAY_P3)
            }
        )
        val list = ArrayList<Any>()
        for (animations in animationsAndFrames()) {
            for (setter in setters) {
                list.add(arrayOf(animations[0], animations[2], setter))
            }
        }
        return list
    }

    @Test
    @Parameters(method = "imagesAndSetters")
    fun testSettersFailOnLatterFrames(image: String, numFrames: Int, setter: (Long) -> Int) {
        // Verify that the setter succeeds on the first frame.
        with(nOpenAsset(getAssets(), image)) {
            val decoder = nCreateFromAsset(this)
            assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, setter(decoder))
            nDeleteDecoder(decoder)
            nCloseAsset(this)
        }

        for (framesBeforeSet in 1 until numFrames) {
            val asset = nOpenAsset(getAssets(), image)
            val decoder = nCreateFromAsset(asset)
            for (i in 1..framesBeforeSet) {
                assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, nAdvanceFrame(decoder))
            }

            // Not on the first frame, so the setter fails.
            assertEquals(ANDROID_IMAGE_DECODER_INVALID_STATE, setter(decoder))

            // Rewind to the beginning. Now the setter can succeed.
            assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, nRewind(decoder))
            assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, setter(decoder))

            nDeleteDecoder(decoder)
            nCloseAsset(asset)
        }
    }

    fun unpremulTestFiles() = arrayOf(
        "alphabetAnim.gif",
        "animated_webp.webp",
        "stoplight.webp"
    )

    @Test
    @Parameters(method = "unpremulTestFiles")
    fun testUnpremul(image: String) {
        val expectedBm = with(ImageDecoder.createSource(getAssets(), image)) {
            ImageDecoder.decodeBitmap(this) {
                decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setUnpremultipliedRequired(true)
            }
        }

        val testBm = makeEmptyBitmap(expectedBm)

        val asset = nOpenAsset(getAssets(), image)
        val decoder = nCreateFromAsset(asset)
        assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, nSetUnpremultipliedRequired(decoder, true))
        nDecode(decoder, testBm, ANDROID_IMAGE_DECODER_SUCCESS)

        val verifier = GoldenImageVerifier(expectedBm, MSSIMComparer(1.0))
        assertTrue(verifier.verify(testBm), "$image did not match in unpremul")

        nDeleteDecoder(decoder)
        nCloseAsset(asset)
    }

    fun imagesWithAlpha() = arrayOf(
        "alphabetAnim.gif",
        "animated_webp.webp",
        "animated.gif"
    )

    @Test
    @Parameters(method = "imagesWithAlpha")
    fun testUnpremulThenScaleFailsWithAlpha(image: String) {
        val asset = nOpenAsset(getAssets(), image)
        val decoder = nCreateFromAsset(asset)
        val width = nGetWidth(decoder)
        val height = nGetHeight(decoder)

        assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, nSetUnpremultipliedRequired(decoder, true))
        assertEquals(
            ANDROID_IMAGE_DECODER_INVALID_SCALE,
                nSetTargetSize(decoder, width * 2, height * 2)
        )
        nDeleteDecoder(decoder)
        nCloseAsset(asset)
    }

    @Test
    @Parameters(method = "imagesWithAlpha")
    fun testScaleThenUnpremulFailsWithAlpha(image: String) {
        val asset = nOpenAsset(getAssets(), image)
        val decoder = nCreateFromAsset(asset)
        val width = nGetWidth(decoder)
        val height = nGetHeight(decoder)

        assertEquals(
            ANDROID_IMAGE_DECODER_SUCCESS,
                nSetTargetSize(decoder, width * 2, height * 2)
        )
        assertEquals(
            ANDROID_IMAGE_DECODER_INVALID_CONVERSION,
                nSetUnpremultipliedRequired(decoder, true)
        )
        nDeleteDecoder(decoder)
        nCloseAsset(asset)
    }

    fun opaquePlusScale(): ArrayList<Any> {
        val opaqueImages = arrayOf("sunset1.jpg", "blendBG.webp", "stoplight.webp")
        val scales = arrayOf(.5f, .75f, 2.0f)
        val list = ArrayList<Any>()
        for (image in opaqueImages) {
            for (scale in scales) {
                list.add(arrayOf(image, scale))
            }
        }
        return list
    }

    @Test
    @Parameters(method = "opaquePlusScale")
    fun testUnpremulPlusScaleOpaque(image: String, scale: Float) {
        val expectedBm = with(ImageDecoder.createSource(getAssets(), image)) {
            ImageDecoder.decodeBitmap(this) {
                decoder, info, _ ->
                    decoder.isUnpremultipliedRequired = true
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val width = (info.size.width * scale).toInt()
                    val height = (info.size.height * scale).toInt()
                    decoder.setTargetSize(width, height)
            }
        }
        val verifier = GoldenImageVerifier(expectedBm, MSSIMComparer(1.0))

        // Flipping the order of setting unpremul and scaling results in taking
        // a different code path. Ensure both succeed.
        val ops = listOf(
            { decoder: Long -> nSetUnpremultipliedRequired(decoder, true) },
            { decoder: Long -> nSetTargetSize(decoder, expectedBm.width, expectedBm.height) }
        )

        for (order in setOf(ops, ops.asReversed())) {
            val testBm = makeEmptyBitmap(expectedBm)
            val asset = nOpenAsset(getAssets(), image)
            val decoder = nCreateFromAsset(asset)
            for (op in order) {
                assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, op(decoder))
            }
            nDecode(decoder, testBm, ANDROID_IMAGE_DECODER_SUCCESS)
            assertTrue(verifier.verify(testBm))

            nDeleteDecoder(decoder)
            nCloseAsset(asset)
            testBm.recycle()
        }
        expectedBm.recycle()
    }

    @Test
    fun testUnpremulPlusScaleWithFrameWithAlpha() {
        // The first frame of this image is opaque, so unpremul + scale succeeds.
        // But frame 3 has alpha, so decoding it with unpremul + scale fails.
        val image = "blendBG.webp"
        val scale = 2.0f
        val asset = nOpenAsset(getAssets(), image)
        val decoder = nCreateFromAsset(asset)
        val width = (nGetWidth(decoder) * scale).toInt()
        val height = (nGetHeight(decoder) * scale).toInt()

        assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, nSetUnpremultipliedRequired(decoder, true))
        assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, nSetTargetSize(decoder, width, height))

        val testBm = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888, true)
        for (i in 0 until 3) {
            nDecode(decoder, testBm, ANDROID_IMAGE_DECODER_SUCCESS)
            assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, nAdvanceFrame(decoder))
        }
        nDecode(decoder, testBm, ANDROID_IMAGE_DECODER_INVALID_SCALE)

        nDeleteDecoder(decoder)
        nCloseAsset(asset)
    }

    @Test
    @Parameters(method = "nonAnimatedAssets")
    fun testGetFrameInfoSucceedsNonAnimated(image: String) {
        val asset = nOpenAsset(getAssets(), image)
        val decoder = nCreateFromAsset(asset)
        val frameInfo = nCreateFrameInfo()
        assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, nGetFrameInfo(decoder, frameInfo))

        if (image.startsWith("animated")) {
            // Although these images have only one frame, they still contain encoded frame info.
            val ANDROID_IMAGE_DECODER_INFINITE = Integer.MAX_VALUE
            assertEquals(ANDROID_IMAGE_DECODER_INFINITE, nGetRepeatCount(decoder))
            assertEquals(250_000_000L, nGetDuration(frameInfo))
            assertEquals(ANDROID_IMAGE_DECODER_DISPOSE_OP_BACKGROUND, nGetDisposeOp(frameInfo))
        } else {
            // Since these are not animated and have no encoded frame info, they should use
            // defaults.
            assertEquals(0, nGetRepeatCount(decoder))
            assertEquals(0L, nGetDuration(frameInfo))
            assertEquals(ANDROID_IMAGE_DECODER_DISPOSE_OP_NONE, nGetDisposeOp(frameInfo))
        }

        nTestGetFrameRect(frameInfo, 0, 0, nGetWidth(decoder), nGetHeight(decoder))
        if (image.endsWith("gif")) {
            // GIFs do not support SRC, so they always report SRC_OVER.
            assertEquals(ANDROID_IMAGE_DECODER_BLEND_OP_SRC_OVER, nGetBlendOp(frameInfo))
        } else {
            assertEquals(ANDROID_IMAGE_DECODER_BLEND_OP_SRC, nGetBlendOp(frameInfo))
        }
        assertEquals(nGetAlpha(decoder), nGetFrameAlpha(frameInfo))

        nDeleteFrameInfo(frameInfo)
        nDeleteDecoder(decoder)
        nCloseAsset(asset)
    }

    @Test
    fun testNullFrameInfo() = nTestNullFrameInfo(getAssets(), "animated.gif")

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testGetFrameInfo(image: String, frameName: String, numFrames: Int) {
        val asset = nOpenAsset(getAssets(), image)
        val decoder = nCreateFromAsset(asset)
        val frameInfo = nCreateFrameInfo()
        for (i in 0 until numFrames) {
            assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, nGetFrameInfo(decoder, frameInfo))
            val result = nAdvanceFrame(decoder)
            val expectedResult = if (i == numFrames - 1) {
                ANDROID_IMAGE_DECODER_FINISHED
            } else {
                ANDROID_IMAGE_DECODER_SUCCESS
            }
            assertEquals(expectedResult, result)
        }

        assertEquals(ANDROID_IMAGE_DECODER_FINISHED, nGetFrameInfo(decoder, frameInfo))

        nDeleteFrameInfo(frameInfo)
        nDeleteDecoder(decoder)
        nCloseAsset(asset)
    }

    fun animationsAndDurations() = arrayOf(
        arrayOf<Any>("animated.gif", LongArray(4) { 250_000_000 }),
        arrayOf<Any>("animated_webp.webp", LongArray(4) { 250_000_000 }),
        arrayOf<Any>("required_gif.gif", LongArray(7) { 100_000_000 }),
        arrayOf<Any>("required_webp.webp", LongArray(7) { 100_000_000 }),
        arrayOf<Any>("alphabetAnim.gif", LongArray(13) { 100_000_000 }),
        arrayOf<Any>("blendBG.webp", longArrayOf(
            525_000_000,
            500_000_000,
                525_000_000,
            437_000_000,
            609_000_000,
            729_000_000,
            444_000_000
        )),
        arrayOf<Any>("stoplight.webp", longArrayOf(
            1_000_000_000,
            500_000_000,
                                                    1_000_000_000
        ))
    )

    @Test
    @Parameters(method = "animationsAndDurations")
    fun testDurations(image: String, durations: LongArray) = testFrameInfo(image) {
        frameInfo, i ->
            assertEquals(durations[i], nGetDuration(frameInfo))
    }

    /**
     * Iterate through all frames and call a lambda that tests an individual frame's info.
     *
     * @param image Name of the image asset to test
     * @param test Lambda with two parameters: A pointer to the native decoder, and the
     *             current frame number.
     */
    private fun testFrameInfo(image: String, test: (Long, Int) -> Unit) {
        val asset = nOpenAsset(getAssets(), image)
        val decoder = nCreateFromAsset(asset)
        val frameInfo = nCreateFrameInfo()
        var frame = 0
        do {
            assertEquals(
                ANDROID_IMAGE_DECODER_SUCCESS,
                nGetFrameInfo(decoder, frameInfo),
                "Failed to getFrameInfo for frame $frame of $image!"
            )
            test(frameInfo, frame)
            frame++
        } while (ANDROID_IMAGE_DECODER_SUCCESS == nAdvanceFrame(decoder))

        nDeleteFrameInfo(frameInfo)
        nDeleteDecoder(decoder)
        nCloseAsset(asset)
    }

    fun animationsAndRects() = arrayOf(
        // Each group of four Ints represents a frame's rectangle
        arrayOf<Any>("animated.gif", intArrayOf(0, 0, 278, 183,
                                                0, 0, 278, 183,
                                                0, 0, 278, 183,
                                                0, 0, 278, 183)),
        arrayOf<Any>("animated_webp.webp", intArrayOf(0, 0, 278, 183,
                                                      0, 0, 278, 183,
                                                      0, 0, 278, 183,
                                                      0, 0, 278, 183)),
        arrayOf<Any>("required_gif.gif", intArrayOf(0, 0, 100, 100,
                                                    0, 0, 75, 75,
                                                    0, 0, 50, 50,
                                                    0, 0, 60, 60,
                                                    0, 0, 100, 100,
                                                    0, 0, 50, 50,
                                                    0, 0, 75, 75)),
        arrayOf<Any>("required_webp.webp", intArrayOf(0, 0, 100, 100,
                                                      0, 0, 75, 75,
                                                      0, 0, 50, 50,
                                                      0, 0, 60, 60,
                                                      0, 0, 100, 100,
                                                      0, 0, 50, 50,
                                                      0, 0, 75, 75)),
        arrayOf<Any>("alphabetAnim.gif", intArrayOf(25, 25, 75, 75,
                                                    25, 25, 75, 75,
                                                    25, 25, 75, 75,
                                                    37, 37, 62, 62,
                                                    37, 37, 62, 62,
                                                    25, 25, 75, 75,
                                                    0, 0, 50, 50,
                                                    0, 0, 100, 100,
                                                    25, 25, 75, 75,
                                                    25, 25, 75, 75,
                                                    0, 0, 100, 100,
                                                    25, 25, 75, 75,
                                                    37, 37, 62, 62)),

        arrayOf<Any>("blendBG.webp", intArrayOf(0, 0, 200, 200,
                                                0, 0, 200, 200,
                                                0, 0, 200, 200,
                                                0, 0, 200, 200,
                                                0, 0, 200, 200,
                                                100, 100, 200, 200,
                                                100, 100, 200, 200)),
        arrayOf<Any>("stoplight.webp", intArrayOf(0, 0, 145, 55,
                                                  0, 0, 145, 55,
                                                  0, 0, 145, 55))
    )

    @Test
    @Parameters(method = "animationsAndRects")
    fun testFrameRects(image: String, rects: IntArray) = testFrameInfo(image) {
        frameInfo, i ->
            val left = rects[i * 4]
            val top = rects[i * 4 + 1]
            val right = rects[i * 4 + 2]
            val bottom = rects[i * 4 + 3]
            try {
                nTestGetFrameRect(frameInfo, left, top, right, bottom)
            } catch (t: Throwable) {
                throw AssertionError("$image, frame $i: ${t.message}", t)
            }
    }

    fun animationsAndAlphas() = arrayOf(
        arrayOf<Any>("animated.gif", BooleanArray(4) { true }),
        arrayOf<Any>("animated_webp.webp", BooleanArray(4) { true }),
        arrayOf<Any>("required_gif.gif", booleanArrayOf(
            false,
            true,
            true,
            true,
                true,
            true,
            true,
            true
        )),
        arrayOf<Any>("required_webp.webp", BooleanArray(7) { false }),
        arrayOf<Any>("alphabetAnim.gif", booleanArrayOf(true, false, true, false,
                true, true, true, true, true, true, true, true, true)),
        arrayOf<Any>("blendBG.webp", booleanArrayOf(
            false,
            true,
            false,
            true,
                                                 false,
            true,
            true
        )),
        arrayOf<Any>("stoplight.webp", BooleanArray(3) { false })
    )

    @Test
    @Parameters(method = "animationsAndAlphas")
    fun testAlphas(image: String, alphas: BooleanArray) = testFrameInfo(image) {
        frameInfo, i ->
            assertEquals(
                alphas[i],
                nGetFrameAlpha(frameInfo),
                "Mismatch in alpha for $image frame $i " +
                    "expected ${alphas[i]}"
            )
    }

    private val ANDROID_IMAGE_DECODER_DISPOSE_OP_NONE = 1
    private val ANDROID_IMAGE_DECODER_DISPOSE_OP_BACKGROUND = 2
    private val ANDROID_IMAGE_DECODER_DISPOSE_OP_PREVIOUS = 3

    fun animationsAndDisposeOps() = arrayOf(
        arrayOf<Any>("animated.gif", IntArray(4) { ANDROID_IMAGE_DECODER_DISPOSE_OP_BACKGROUND }),
        arrayOf<Any>("animated_webp.webp", IntArray(4) { ANDROID_IMAGE_DECODER_DISPOSE_OP_NONE }),
        arrayOf<Any>("required_gif.gif", intArrayOf(
            ANDROID_IMAGE_DECODER_DISPOSE_OP_NONE,
                ANDROID_IMAGE_DECODER_DISPOSE_OP_BACKGROUND,
            ANDROID_IMAGE_DECODER_DISPOSE_OP_NONE,
                ANDROID_IMAGE_DECODER_DISPOSE_OP_NONE,
            ANDROID_IMAGE_DECODER_DISPOSE_OP_NONE,
                ANDROID_IMAGE_DECODER_DISPOSE_OP_BACKGROUND,
                ANDROID_IMAGE_DECODER_DISPOSE_OP_NONE
        )),
        arrayOf<Any>("required_webp.webp", intArrayOf(
            ANDROID_IMAGE_DECODER_DISPOSE_OP_NONE,
                ANDROID_IMAGE_DECODER_DISPOSE_OP_BACKGROUND,
            ANDROID_IMAGE_DECODER_DISPOSE_OP_NONE,
                ANDROID_IMAGE_DECODER_DISPOSE_OP_NONE,
            ANDROID_IMAGE_DECODER_DISPOSE_OP_NONE,
                ANDROID_IMAGE_DECODER_DISPOSE_OP_BACKGROUND,
                ANDROID_IMAGE_DECODER_DISPOSE_OP_NONE
        )),
        arrayOf<Any>("alphabetAnim.gif", intArrayOf(ANDROID_IMAGE_DECODER_DISPOSE_OP_NONE,
                ANDROID_IMAGE_DECODER_DISPOSE_OP_PREVIOUS,
                ANDROID_IMAGE_DECODER_DISPOSE_OP_PREVIOUS,
                ANDROID_IMAGE_DECODER_DISPOSE_OP_PREVIOUS,
                ANDROID_IMAGE_DECODER_DISPOSE_OP_PREVIOUS,
                ANDROID_IMAGE_DECODER_DISPOSE_OP_BACKGROUND, ANDROID_IMAGE_DECODER_DISPOSE_OP_NONE,
                ANDROID_IMAGE_DECODER_DISPOSE_OP_BACKGROUND,
                ANDROID_IMAGE_DECODER_DISPOSE_OP_BACKGROUND, ANDROID_IMAGE_DECODER_DISPOSE_OP_NONE,
                ANDROID_IMAGE_DECODER_DISPOSE_OP_NONE, ANDROID_IMAGE_DECODER_DISPOSE_OP_BACKGROUND,
                ANDROID_IMAGE_DECODER_DISPOSE_OP_NONE)),
        arrayOf<Any>("blendBG.webp", IntArray(7) { ANDROID_IMAGE_DECODER_DISPOSE_OP_NONE }),
        arrayOf<Any>("stoplight.webp", IntArray(4) { ANDROID_IMAGE_DECODER_DISPOSE_OP_NONE })
    )

    @Test
    @Parameters(method = "animationsAndDisposeOps")
    fun testDisposeOps(image: String, disposeOps: IntArray) = testFrameInfo(image) {
        frameInfo, i ->
            assertEquals(disposeOps[i], nGetDisposeOp(frameInfo))
    }

    private val ANDROID_IMAGE_DECODER_BLEND_OP_SRC = 1
    private val ANDROID_IMAGE_DECODER_BLEND_OP_SRC_OVER = 2

    fun animationsAndBlendOps() = arrayOf(
        arrayOf<Any>("animated.gif", IntArray(4) { ANDROID_IMAGE_DECODER_BLEND_OP_SRC_OVER }),
        arrayOf<Any>("animated_webp.webp", IntArray(4) { ANDROID_IMAGE_DECODER_BLEND_OP_SRC }),
        arrayOf<Any>("required_gif.gif", IntArray(7) { ANDROID_IMAGE_DECODER_BLEND_OP_SRC_OVER }),
        arrayOf<Any>("required_webp.webp", intArrayOf(
            ANDROID_IMAGE_DECODER_BLEND_OP_SRC,
                ANDROID_IMAGE_DECODER_BLEND_OP_SRC_OVER,
            ANDROID_IMAGE_DECODER_BLEND_OP_SRC_OVER,
                ANDROID_IMAGE_DECODER_BLEND_OP_SRC_OVER,
            ANDROID_IMAGE_DECODER_BLEND_OP_SRC,
                ANDROID_IMAGE_DECODER_BLEND_OP_SRC_OVER,
            ANDROID_IMAGE_DECODER_BLEND_OP_SRC_OVER
        )),
        arrayOf<Any>("alphabetAnim.gif", IntArray(13) { ANDROID_IMAGE_DECODER_BLEND_OP_SRC_OVER }),
        arrayOf<Any>("blendBG.webp", intArrayOf(
            ANDROID_IMAGE_DECODER_BLEND_OP_SRC,
                ANDROID_IMAGE_DECODER_BLEND_OP_SRC_OVER,
            ANDROID_IMAGE_DECODER_BLEND_OP_SRC,
                ANDROID_IMAGE_DECODER_BLEND_OP_SRC,
            ANDROID_IMAGE_DECODER_BLEND_OP_SRC,
                ANDROID_IMAGE_DECODER_BLEND_OP_SRC,
            ANDROID_IMAGE_DECODER_BLEND_OP_SRC
        )),
        arrayOf<Any>("stoplight.webp", IntArray(4) { ANDROID_IMAGE_DECODER_BLEND_OP_SRC_OVER })
    )

    @Test
    @Parameters(method = "animationsAndBlendOps")
    fun testBlendOps(image: String, blendOps: IntArray) = testFrameInfo(image) {
        frameInfo, i ->
            assertEquals(
                blendOps[i],
                nGetBlendOp(frameInfo),
                "Mismatch in blend op for $image " +
                        "frame $i, expected: ${blendOps[i]}"
            )
    }

    @Test
    fun testHandleDisposePrevious() {
        // The first frame is ANDROID_IMAGE_DECODER_DISPOSE_OP_NONE, followed by a single
        // ANDROID_IMAGE_DECODER_DISPOSE_OP_PREVIOUS frame. The third frame looks different
        // depending on whether that is respected.
        val image = "RestorePrevious.gif"
        val disposeOps = intArrayOf(
            ANDROID_IMAGE_DECODER_DISPOSE_OP_NONE,
                                    ANDROID_IMAGE_DECODER_DISPOSE_OP_PREVIOUS,
                                    ANDROID_IMAGE_DECODER_DISPOSE_OP_NONE
        )
        val asset = nOpenAsset(getAssets(), image)
        val decoder = nCreateFromAsset(asset)

        val width = nGetWidth(decoder)
        val height = nGetHeight(decoder)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888, true)

        val verifiers = arrayOf<BitmapVerifier>(
            ColorVerifier(Color.BLACK, 0),
            RectVerifier(Color.BLACK, Color.RED, Rect(0, 0, 100, 80), 0),
            RectVerifier(Color.BLACK, Color.GREEN, Rect(0, 0, 100, 50), 0)
        )

        with(nCreateFrameInfo()) {
            for (i in 0..2) {
                nGetFrameInfo(decoder, this)
                assertEquals(disposeOps[i], nGetDisposeOp(this))

                nDecode(decoder, bitmap, ANDROID_IMAGE_DECODER_SUCCESS)
                assertTrue(verifiers[i].verify(bitmap))
                nAdvanceFrame(decoder)
            }
            nDeleteFrameInfo(this)
        }

        // Now redecode without letting AImageDecoder handle
        // ANDROID_IMAGE_DECODER_DISPOSE_OP_PREVIOUS.
        bitmap.eraseColor(Color.TRANSPARENT)
        assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, nRewind(decoder))
        nSetHandleDisposePrevious(decoder, false)

        // If the client does not handle ANDROID_IMAGE_DECODER_DISPOSE_OP_PREVIOUS
        // the final frame does not match.
        for (i in 0..2) {
            nDecode(decoder, bitmap, ANDROID_IMAGE_DECODER_SUCCESS)
            assertEquals(i != 2, verifiers[i].verify(bitmap))

            if (i == 2) {
                // Not only can we verify that frame 2 does not look as expected, but it
                // should look as if we decoded frame 1 and did not revert it.
                val verifier = RegionVerifier()
                verifier.addVerifier(Rect(0, 0, 100, 50), ColorVerifier(Color.GREEN, 0))
                verifier.addVerifier(Rect(0, 50, 100, 80), ColorVerifier(Color.RED, 0))
                verifier.addVerifier(Rect(0, 80, 100, 100), ColorVerifier(Color.BLACK, 0))
                assertTrue(verifier.verify(bitmap))
            }
            nAdvanceFrame(decoder)
        }

        // Now redecode and manually store/restore the first frame.
        bitmap.eraseColor(Color.TRANSPARENT)
        assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, nRewind(decoder))
        nDecode(decoder, bitmap, ANDROID_IMAGE_DECODER_SUCCESS)
        val storedFrame = bitmap
        for (i in 1..2) {
            assertEquals(nAdvanceFrame(decoder), ANDROID_IMAGE_DECODER_SUCCESS)
            val frame = storedFrame.copy(storedFrame.config!!, true)
            nDecode(decoder, frame, ANDROID_IMAGE_DECODER_SUCCESS)
            assertTrue(verifiers[i].verify(frame))
            frame.recycle()
        }

        // This setting can be switched back, so that AImageDecoder handles it.
        bitmap.eraseColor(Color.TRANSPARENT)
        assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, nRewind(decoder))
        nSetHandleDisposePrevious(decoder, true)

        for (i in 0..2) {
            nDecode(decoder, bitmap, ANDROID_IMAGE_DECODER_SUCCESS)
            assertTrue(verifiers[i].verify(bitmap))
            nAdvanceFrame(decoder)
        }

        bitmap.recycle()
        nDeleteDecoder(decoder)
        nCloseAsset(asset)
    }

    @Test
    @Parameters(method = "animationsAndAlphas")
    fun test565NoAnimation(image: String, alphas: BooleanArray) {
        val asset = nOpenAsset(getAssets(), image)
        val decoder = nCreateFromAsset(asset)
        val ANDROID_BITMAP_FORMAT_RGB_565 = 4
        if (alphas[0]) {
            assertEquals(
                ANDROID_IMAGE_DECODER_INVALID_CONVERSION,
                    nSetAndroidBitmapFormat(decoder, ANDROID_BITMAP_FORMAT_RGB_565)
            )
        } else {
            assertEquals(
                ANDROID_IMAGE_DECODER_SUCCESS,
                    nSetAndroidBitmapFormat(decoder, ANDROID_BITMAP_FORMAT_RGB_565)
            )
            assertEquals(
                ANDROID_IMAGE_DECODER_INVALID_STATE,
                    nAdvanceFrame(decoder)
            )
        }

        nDeleteDecoder(decoder)
        nCloseAsset(asset)
    }

    private fun handleRotation(original: Bitmap, image: String): Bitmap {
        // ExifInterface does not support GIF.
        if (image.endsWith("gif")) return original

        val inputStream = getAssets().open(image)
        val exifInterface = ExifInterface(inputStream)
        var rotation = 0
        when (exifInterface.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED -> return original
            ExifInterface.ORIENTATION_ROTATE_90 -> rotation = 90
            ExifInterface.ORIENTATION_ROTATE_180 -> rotation = 180
            ExifInterface.ORIENTATION_ROTATE_270 -> rotation = 270
            else -> fail("Unexpected orientation for $image!")
        }

        val m = Matrix()
        m.setRotate(rotation.toFloat(), original.width / 2.0f, original.height / 2.0f)
        return Bitmap.createBitmap(original, 0, 0, original.width, original.height, m, false)
    }

    private fun decodeF16(image: String): Bitmap {
        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.RGBA_F16
        val inputStream = getAssets().open(image)
        val bm = BitmapFactory.decodeStream(inputStream, null, options)
        if (bm == null) {
            fail("Failed to decode $image to RGBA_F16!")
        }
        return bm
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testDecodeFramesF16(image: String, frameName: String, numFrames: Int) {
        var expectedBm = handleRotation(decodeF16(image), image)

        val asset = nOpenAsset(getAssets(), image)
        val decoder = nCreateFromAsset(asset)
        val ANDROID_BITMAP_FORMAT_RGBA_F16 = 9
        nSetAndroidBitmapFormat(decoder, ANDROID_BITMAP_FORMAT_RGBA_F16)

        val testBm = makeEmptyBitmap(expectedBm)

        val mssimThreshold = .95
        var i = 0
        while (true) {
            nDecode(decoder, testBm, ANDROID_IMAGE_DECODER_SUCCESS)
            val verifier = GoldenImageVerifier(expectedBm, MSSIMComparer(mssimThreshold))
            assertTrue(verifier.verify(testBm), "$image has mismatch in frame $i")
            expectedBm.recycle()

            i++
            when (val result = nAdvanceFrame(decoder)) {
                ANDROID_IMAGE_DECODER_SUCCESS -> {
                    assertTrue(i < numFrames, "Unexpected frame $i in $image")
                    expectedBm = decodeF16(frameName.format(i))
                }
                ANDROID_IMAGE_DECODER_FINISHED -> {
                    assertEquals(i, numFrames, "Expected $numFrames frames in $image; found $i")
                    break
                }
                else -> fail("Unexpected error $result when advancing $image to frame $i")
            }
        }

        nDeleteDecoder(decoder)
        nCloseAsset(asset)
    }

    private external fun nTestNullDecoder()
    private external fun nTestToString()
    private external fun nOpenAsset(assets: AssetManager, name: String): Long
    private external fun nCloseAsset(asset: Long)
    private external fun nCreateFromAsset(asset: Long): Long
    private external fun nGetWidth(decoder: Long): Int
    private external fun nGetHeight(decoder: Long): Int
    private external fun nDeleteDecoder(decoder: Long)
    private external fun nSetTargetSize(decoder: Long, width: Int, height: Int): Int
    private external fun nSetCrop(decoder: Long, left: Int, top: Int, right: Int, bottom: Int): Int
    private external fun nDecode(decoder: Long, dst: Bitmap, expectedResult: Int)
    private external fun nAdvanceFrame(decoder: Long): Int
    private external fun nRewind(decoder: Long): Int
    private external fun nSetUnpremultipliedRequired(decoder: Long, required: Boolean): Int
    private external fun nSetAndroidBitmapFormat(decoder: Long, format: Int): Int
    private external fun nSetDataSpace(decoder: Long, format: Int): Int
    private external fun nCreateFrameInfo(): Long
    private external fun nDeleteFrameInfo(frameInfo: Long)
    private external fun nGetFrameInfo(decoder: Long, frameInfo: Long): Int
    private external fun nTestNullFrameInfo(assets: AssetManager, name: String)
    private external fun nGetDuration(frameInfo: Long): Long
    private external fun nTestGetFrameRect(
        frameInfo: Long,
        expectedLeft: Int,
        expectedTop: Int,
        expectedRight: Int,
        expectedBottom: Int
    )
    private external fun nGetFrameAlpha(frameInfo: Long): Boolean
    private external fun nGetAlpha(decoder: Long): Boolean
    private external fun nGetDisposeOp(frameInfo: Long): Int
    private external fun nGetBlendOp(frameInfo: Long): Int
    private external fun nGetRepeatCount(decoder: Long): Int
    private external fun nSetHandleDisposePrevious(decoder: Long, handle: Boolean)
}
