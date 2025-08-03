/*
 * Copyright (C) 2022 The Android Open Source Project
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
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.HardwareBufferRenderer
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.uirendering.cts.bitmapverifiers.ColorVerifier
import android.uirendering.cts.bitmapverifiers.PerPixelBitmapVerifier
import android.uirendering.cts.bitmapverifiers.RegionVerifier
import android.uirendering.cts.testinfrastructure.ActivityTestBase
import android.uirendering.cts.util.CompareUtils
import android.util.Half
import android.view.SurfaceControl
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class HardwareBufferRendererTests : ActivityTestBase() {

    private val mExecutor = Executors.newSingleThreadExecutor()

    @Test
    fun testRenderAfterCloseReturnsError() = hardwareBufferRendererTest{ _, renderer ->
        renderer.close()
        assertThrows(IllegalStateException::class.java) {
            renderer.obtainRenderRequest().draw(mExecutor) { _ -> /* NO-OP */ }
        }
    }

    @Test
    fun testIsClosed() = hardwareBufferRendererTest { _, renderer ->
        assertFalse(renderer.isClosed())
        renderer.close()
        assertTrue(renderer.isClosed())
    }

    @Test
    fun testMultipleClosesDoesNotCrash() = hardwareBufferRendererTest { _, renderer ->
        renderer.close()
        renderer.close()
        renderer.close()
    }

    @Test
    fun testHardwareBufferRender() = hardwareBufferRendererTest { hardwareBuffer, renderer ->
        val contentRoot = RenderNode("content").apply {
            setPosition(0, 0, TEST_WIDTH, TEST_HEIGHT)
            record { canvas -> canvas.drawColor(Color.BLUE) }
        }
        renderer.setContentRoot(contentRoot)

        val colorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        val latch = CountDownLatch(1)
        renderer.obtainRenderRequest().setColorSpace(colorSpace).draw(mExecutor) { renderResult ->
            renderResult.fence.awaitForever()
            latch.countDown()
        }

        assertTrue(latch.await(3000, TimeUnit.MILLISECONDS))

        val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)!!
            .copy(Bitmap.Config.ARGB_8888, false)

        assertEquals(TEST_WIDTH, bitmap.width)
        assertEquals(TEST_HEIGHT, bitmap.height)
        assertEquals(0xFF0000FF.toInt(), bitmap.getPixel(0, 0))
    }

    @Test
    fun testContentsPreservedSRGB() = preservedContentsTest() { bitmap ->
        assertEquals(Color.RED, bitmap.getPixel(TEST_WIDTH / 2, TEST_HEIGHT / 4))
        assertEquals(Color.BLUE, bitmap.getPixel(TEST_WIDTH / 2, TEST_HEIGHT / 2 + TEST_HEIGHT / 4))
    }

    @Test
    fun testContentsPreservedF16() = preservedContentsTest(
            format = PixelFormat.RGBA_F16,
            bitmapConfig = Bitmap.Config.RGBA_F16
    ) { bitmap ->
        val buffer = ByteBuffer.allocateDirect(bitmap.allocationByteCount).apply {
            bitmap.copyPixelsToBuffer(this)
            rewind()
            order(ByteOrder.LITTLE_ENDIAN)
        }
        val srcColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        val srcToDst = ColorSpace.connect(srcColorSpace, ColorSpace.get(ColorSpace.Named.SRGB))

        val expectedRed = srcToDst.transform(1.0f, 0.0f, 0.0f)
        val expectedBlue = srcToDst.transform(0.0f, 0.0f, 1.0f)

        assertEqualsRgba16f(
                "TopMiddle",
                bitmap,
                TEST_WIDTH / 2,
                TEST_HEIGHT / 4,
                buffer,
                expectedRed[0],
                expectedRed[1],
                expectedRed[2],
                1.0f
        )
        assertEqualsRgba16f(
                "BottomMiddle",
                bitmap,
                TEST_WIDTH / 2,
                TEST_HEIGHT / 2 + TEST_HEIGHT / 4,
                buffer,
                expectedBlue[0],
                expectedBlue[1],
                expectedBlue[2],
                1.0f
        )
    }

    @Test
    fun testContentsPreserved1010102() = preservedContentsTest(
            format = PixelFormat.RGBA_1010102,
            bitmapConfig = Bitmap.Config.RGBA_1010102
    ) { bitmap ->
        assertEquals(Color.RED, bitmap.getPixel(TEST_WIDTH / 2, TEST_HEIGHT / 4))
        assertEquals(Color.BLUE, bitmap.getPixel(TEST_WIDTH / 2, TEST_HEIGHT / 2 + TEST_HEIGHT / 4))
    }

    private fun preservedContentsTest(
            format: Int = PixelFormat.RGBA_8888,
            colorSpace: ColorSpace = ColorSpace.get(ColorSpace.Named.SRGB),
            bitmapConfig: Bitmap.Config = Bitmap.Config.ARGB_8888,
            block: (Bitmap) -> Unit
    ) = hardwareBufferRendererTest(format = format) { hardwareBuffer, renderer ->
        val contentRoot = RenderNode("content").apply {
            setPosition(0, 0, TEST_WIDTH, TEST_HEIGHT)
            record { canvas -> canvas.drawColor(Color.BLUE) }
        }
        renderer.setContentRoot(contentRoot)
        val latch = CountDownLatch(1)
        renderer.obtainRenderRequest().setColorSpace(colorSpace).draw(mExecutor) { renderResult ->
            renderResult.fence.awaitForever()
            latch.countDown()
        }

        assertTrue(latch.await(3000, TimeUnit.MILLISECONDS))

        val latch2 = CountDownLatch(1)
        contentRoot.record { canvas ->
            val paint = Paint().apply { color = Color.RED }
            canvas.drawRect(0f, 0f, TEST_WIDTH.toFloat(), TEST_HEIGHT / 2f, paint)
        }
        renderer.setContentRoot(contentRoot)

        renderer.obtainRenderRequest().setColorSpace(colorSpace).draw(mExecutor) { renderResult ->
            renderResult.fence.awaitForever()
            latch2.countDown()
        }

        assertTrue(latch2.await(3000, TimeUnit.MILLISECONDS))

        val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)!!
                .copy(bitmapConfig, false)

        assertEquals(TEST_WIDTH, bitmap.width)
        assertEquals(TEST_HEIGHT, bitmap.height)
        block(bitmap)
    }

    private fun quadTest(
            width: Int = TEST_WIDTH,
            height: Int = TEST_HEIGHT,
            transform: Int = SurfaceControl.BUFFER_TRANSFORM_IDENTITY,
            colorSpace: ColorSpace = ColorSpace.get(ColorSpace.Named.SRGB),
            format: Int = PixelFormat.RGBA_8888,
            bitmapConfig: Bitmap.Config = Bitmap.Config.ARGB_8888,
            block: (Bitmap) -> Unit,
    ) {
        val bufferWidth: Int
        val bufferHeight: Int
        if (transform == SurfaceControl.BUFFER_TRANSFORM_ROTATE_90 ||
                transform == SurfaceControl.BUFFER_TRANSFORM_ROTATE_270) {
            bufferWidth = height
            bufferHeight = width
        } else {
            bufferWidth = width
            bufferHeight = height
        }
        hardwareBufferRendererTest(width = bufferWidth, height = bufferHeight, format = format) {
            hardwareBuffer, renderer ->
            val root = RenderNode("content").apply {
                setPosition(0, 0, width, height)
                record { canvas ->
                    val widthF = width.toFloat()
                    val heightF = height.toFloat()
                    val paint = Paint().apply { color = Color.RED }
                    canvas.drawRect(0f, 0f, widthF / 2f, heightF / 2f, paint)
                    paint.color = Color.BLUE
                    canvas.drawRect(widthF / 2f, 0f, widthF, heightF / 2f, paint)
                    paint.color = Color.GREEN
                    canvas.drawRect(0f, heightF / 2f, widthF / 2f, heightF, paint)
                    paint.color = Color.YELLOW
                    canvas.drawRect(widthF / 2f, heightF / 2f, widthF, heightF, paint)
                }
            }
            renderer.setContentRoot(root)

            val latch = CountDownLatch(1)
            renderer.obtainRenderRequest()
                    .setColorSpace(colorSpace)
                    .setBufferTransform(transform)
                    .draw(mExecutor) { renderResult ->
                        renderResult.fence.awaitForever()
                        latch.countDown()
                    }

            assertTrue(latch.await(3000, TimeUnit.MILLISECONDS))

            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)!!
                    .copy(bitmapConfig, false)

            assertEquals(bufferWidth, bitmap.width)
            assertEquals(bufferHeight, bitmap.height)

            block(bitmap)
        }
    }

    private fun assertBitmapQuadColors(
        bitmap: Bitmap,
        topLeft: Int,
        topRight: Int,
        bottomLeft: Int,
        bottomRight: Int,
    ) {
        val width = bitmap.width
        val height = bitmap.height

        val topLeftStartX = 0
        val topLeftEndX = width / 2 - 2
        val topLeftStartY = 0
        val topLeftEndY = height / 2 - 2

        val topRightStartX = width / 2 + 2
        val topRightEndX = width - 1
        val topRightStartY = 0
        val topRightEndY = height / 2 - 2

        val bottomRightStartX = width / 2 + 2
        val bottomRightEndX = width - 1
        val bottomRightStartY = height / 2 + 2
        val bottomRightEndY = height - 1

        val bottomLeftStartX = 0
        val bottomLeftEndX = width / 2 - 2
        val bottomLeftStartY = height / 2 + 2
        val bottomLeftEndY = height - 1
        assertEquals(topLeft, bitmap.getPixel(topLeftStartX, topLeftStartY))
        assertEquals(topLeft, bitmap.getPixel(topLeftEndX, topLeftStartY))
        assertEquals(topLeft, bitmap.getPixel(topLeftEndX, topLeftEndY))
        assertEquals(topLeft, bitmap.getPixel(topLeftStartX, topLeftEndY))

        assertEquals(topRight, bitmap.getPixel(topRightStartX, topRightStartY))
        assertEquals(topRight, bitmap.getPixel(topRightEndX, topRightStartY))
        assertEquals(topRight, bitmap.getPixel(topRightEndX, topRightEndY))
        assertEquals(topRight, bitmap.getPixel(topRightStartX, topRightEndY))

        assertEquals(bottomRight, bitmap.getPixel(bottomRightStartX, bottomRightStartY))
        assertEquals(bottomRight, bitmap.getPixel(bottomRightEndX, bottomRightStartY))
        assertEquals(bottomRight, bitmap.getPixel(bottomRightEndX, bottomRightEndY))
        assertEquals(bottomRight, bitmap.getPixel(bottomRightStartX, bottomRightEndY))

        assertEquals(bottomLeft, bitmap.getPixel(bottomLeftStartX, bottomLeftStartY))
        assertEquals(bottomLeft, bitmap.getPixel(bottomLeftEndX, bottomLeftStartY))
        assertEquals(bottomLeft, bitmap.getPixel(bottomLeftEndX, bottomLeftEndY))
        assertEquals(bottomLeft, bitmap.getPixel(bottomLeftStartX, bottomLeftEndY))
    }

    @Test
    fun testTransformRotate0TallWide() = quadTest(
            width = TEST_WIDTH * 2,
            height = TEST_HEIGHT,
            transform = SurfaceControl.BUFFER_TRANSFORM_IDENTITY
    ) { bitmap ->
        assertBitmapQuadColors(
                bitmap,
                topLeft = Color.RED,
                topRight = Color.BLUE,
                bottomRight = Color.YELLOW,
                bottomLeft = Color.GREEN
        )
    }

    @Test
    fun testTransformRotate0TallRect() = quadTest(
            width = TEST_WIDTH,
            height = TEST_HEIGHT * 2,
            transform = SurfaceControl.BUFFER_TRANSFORM_IDENTITY
    ) { bitmap ->
        assertBitmapQuadColors(
                bitmap,
                topLeft = Color.RED,
                topRight = Color.BLUE,
                bottomRight = Color.YELLOW,
                bottomLeft = Color.GREEN
        )
    }

    @Test
    fun testTransformRotate90WideRect() = quadTest(
            width = TEST_WIDTH * 2,
            height = TEST_HEIGHT,
            transform = SurfaceControl.BUFFER_TRANSFORM_ROTATE_90
    ) { bitmap ->
        assertBitmapQuadColors(
                bitmap,
                topLeft = Color.GREEN,
                topRight = Color.RED,
                bottomRight = Color.BLUE,
                bottomLeft = Color.YELLOW
        )
    }

    @Test
    fun testTransformRotate90TallRect() = quadTest(
            width = TEST_WIDTH,
            height = TEST_HEIGHT * 2,
            transform = SurfaceControl.BUFFER_TRANSFORM_ROTATE_90
    ) { bitmap ->
        assertBitmapQuadColors(
                bitmap,
                topLeft = Color.GREEN,
                topRight = Color.RED,
                bottomLeft = Color.YELLOW,
                bottomRight = Color.BLUE
        )
    }

    @Test
    fun testTransformRotate180WideRect() = quadTest(
            width = TEST_WIDTH * 2,
            height = TEST_HEIGHT,
            transform = SurfaceControl.BUFFER_TRANSFORM_ROTATE_180
    ) { bitmap ->
        assertBitmapQuadColors(
                bitmap,
                topLeft = Color.YELLOW,
                topRight = Color.GREEN,
                bottomLeft = Color.BLUE,
                bottomRight = Color.RED
        )
    }

    @Test
    fun testTransformRotate180TallRect() = quadTest(
            width = TEST_WIDTH,
            height = TEST_HEIGHT * 2,
            transform = SurfaceControl.BUFFER_TRANSFORM_ROTATE_180
    ) { bitmap ->
        assertBitmapQuadColors(
                bitmap,
                topLeft = Color.YELLOW,
                topRight = Color.GREEN,
                bottomLeft = Color.BLUE,
                bottomRight = Color.RED
        )
    }
    @Test
    fun testTransformRotate270WideRect() = quadTest(
            width = TEST_WIDTH * 2,
            height = TEST_HEIGHT,
            transform = SurfaceControl.BUFFER_TRANSFORM_ROTATE_270
    ) { bitmap ->
        assertBitmapQuadColors(
                bitmap,
                topLeft = Color.BLUE,
                topRight = Color.YELLOW,
                bottomRight = Color.GREEN,
                bottomLeft = Color.RED
        )
    }

    @Test
    fun testTransformRotate270TallRect() = quadTest(
            width = TEST_WIDTH,
            height = TEST_HEIGHT * 2,
            transform = SurfaceControl.BUFFER_TRANSFORM_ROTATE_270
    ) { bitmap ->
        assertBitmapQuadColors(
                bitmap,
                topLeft = Color.BLUE,
                topRight = Color.YELLOW,
                bottomRight = Color.GREEN,
                bottomLeft = Color.RED
        )
    }

    @Test
    fun testUnknownTransformThrows() = hardwareBufferRendererTest { _, renderer ->
        val root = RenderNode("content").apply {
            setPosition(0, 0, TEST_WIDTH, TEST_HEIGHT)
            record { canvas ->
                with(canvas) {
                    drawColor(Color.BLUE)
                    val paint = Paint().apply { color = Color.RED }
                    canvas.drawRect(0f, 0f, TEST_WIDTH / 2f, TEST_HEIGHT / 2f, paint)
                }
            }
        }
        renderer.setContentRoot(root)

        val colorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        val latch = CountDownLatch(1)

        assertThrows(IllegalArgumentException::class.java) {
            renderer.obtainRenderRequest()
                .setColorSpace(colorSpace)
                .setBufferTransform(42)
                .draw(mExecutor) { renderResult ->
                    renderResult.fence.awaitForever()
                    latch.countDown()
                }
        }
    }

    private fun colorSpaceTest(dstColorSpace: ColorSpace) =
        quadTest(
            format = PixelFormat.RGBA_F16,
            colorSpace = dstColorSpace,
            bitmapConfig = Bitmap.Config.RGBA_F16
        ) { bitmap ->
        val buffer = ByteBuffer.allocateDirect(bitmap.allocationByteCount).apply {
            bitmap.copyPixelsToBuffer(this)
            rewind()
            order(ByteOrder.LITTLE_ENDIAN)
        }
        val srcColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        val srcToDst = ColorSpace.connect(srcColorSpace, dstColorSpace)

        val expectedRed = srcToDst.transform(1.0f, 0.0f, 0.0f)
        val expectedBlue = srcToDst.transform(0.0f, 0.0f, 1.0f)
        val expectedGreen = srcToDst.transform(0.0f, 1.0f, 0.0f)
        val expectedYellow = srcToDst.transform(1.0f, 1.0f, 0.0f)

        assertEqualsRgba16f(
            "TopLeft",
            bitmap,
            TEST_WIDTH / 4,
            TEST_HEIGHT / 4,
            buffer,
            expectedRed[0],
            expectedRed[1],
            expectedRed[2],
            1.0f
        )

        assertEqualsRgba16f(
            "TopRight",
            bitmap,
            (TEST_WIDTH * 3f / 4f).toInt(),
            TEST_HEIGHT / 4,
            buffer,
            expectedBlue[0],
            expectedBlue[1],
            expectedBlue[2],
            1.0f
        )

        assertEqualsRgba16f(
            "BottomLeft",
            bitmap,
            TEST_WIDTH / 4,
            (TEST_HEIGHT * 3f / 4f).toInt(),
            buffer,
            expectedGreen[0],
            expectedGreen[1],
            expectedGreen[2],
            1.0f
        )
        assertEqualsRgba16f(
            "BottomRight",
            bitmap,
            (TEST_WIDTH * 3f / 4f).toInt(),
            (TEST_HEIGHT * 3f / 4f).toInt(),
            buffer,
            expectedYellow[0],
            expectedYellow[1],
            expectedYellow[2],
            1.0f
        )
    }

    @Test
    fun testColorSpaceDisplayP3() = colorSpaceTest(ColorSpace.get(ColorSpace.Named.DISPLAY_P3))

    @Test
    fun testColorSpaceProPhotoRGB() = colorSpaceTest(ColorSpace.get(ColorSpace.Named.PRO_PHOTO_RGB))

    @Test
    fun testColorSpaceAdobeRGB() = colorSpaceTest(ColorSpace.get(ColorSpace.Named.ADOBE_RGB))

    @Test
    fun testColorSpaceDciP3() = colorSpaceTest(ColorSpace.get(ColorSpace.Named.DCI_P3))

    private fun assertEqualsRgba16f(
        message: String,
        bitmap: Bitmap,
        x: Int,
        y: Int,
        dst: ByteBuffer,
        r: Float,
        g: Float,
        b: Float,
        a: Float,
    ) {
        val index = y * bitmap.rowBytes + (x shl 3)
        val cR = dst.getShort(index)
        val cG = dst.getShort(index + 2)
        val cB = dst.getShort(index + 4)
        val cA = dst.getShort(index + 6)
        assertEquals(message, r, Half.toFloat(cR), 0.01f)
        assertEquals(message, g, Half.toFloat(cG), 0.01f)
        assertEquals(message, b, Half.toFloat(cB), 0.01f)
        assertEquals(message, a, Half.toFloat(cA), 0.01f)
    }

    private fun spotShadowTest(
        transform: Int = SurfaceControl.BUFFER_TRANSFORM_IDENTITY,
    ) = hardwareBufferRendererTest { hardwareBuffer, renderer ->
        val content = RenderNode("content")
        val colorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        val renderer = renderer.apply {
            setLightSourceAlpha(0.0f, 1.0f)
            setLightSourceGeometry(TEST_WIDTH / 2f, 0f, 800.0f, 20.0f)
            setContentRoot(content)
        }
        val childRect = Rect(25, 25, 65, 65)
        content.setPosition(0, 0, TEST_WIDTH, TEST_HEIGHT)
        content.record { parentCanvas ->
            val childNode = RenderNode("shadowCaster")
            childNode.setPosition(childRect)
            val outline = Outline()
            outline.setRect(Rect(0, 0, childRect.width(), childRect.height()))
            outline.alpha = 1f
            childNode.setOutline(outline)
            val childCanvas = childNode.beginRecording()
            childCanvas.drawColor(Color.RED)
            childNode.endRecording()
            childNode.elevation = 20f

            parentCanvas.drawColor(Color.WHITE)
            parentCanvas.enableZ()
            parentCanvas.drawRenderNode(childNode)
            parentCanvas.disableZ()
        }

        val latch = CountDownLatch(1)
        var renderStatus = HardwareBufferRenderer.RenderResult.ERROR_UNKNOWN
        renderer.obtainRenderRequest()
            .setColorSpace(colorSpace)
            .setBufferTransform(transform)
            .draw(mExecutor) { renderResult ->
                renderStatus = renderResult.status
                renderResult.fence.awaitForever()
                latch.countDown()
            }

        assertTrue(latch.await(3000, TimeUnit.MILLISECONDS))
        assertEquals(renderStatus, HardwareBufferRenderer.RenderResult.SUCCESS)
        val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)!!
            .copy(Bitmap.Config.ARGB_8888, false)

        val rectF = Rect(childRect.left,
                         childRect.bottom,
                         childRect.right,
                         childRect.bottom + 10)
        val result = RegionVerifier().addVerifier(childRect, ColorVerifier(Color.RED, 10))
            .addVerifier(
                rectF.applyBufferTransform(
                    bitmap.width,
                    bitmap.height,
                    transform
                ),
                object : PerPixelBitmapVerifier() {
                    override fun verifyPixel(x: Int, y: Int, observedColor: Int): Boolean {
                        return CompareUtils.verifyPixelGrayScale(observedColor, 1)
                    }
                }).verify(bitmap)

        assertTrue(result)
    }

    @Test
    fun testSpotShadowSetup() = spotShadowTest()

    @Test
    fun testSpotShadowRotate90() = spotShadowTest(SurfaceControl.BUFFER_TRANSFORM_ROTATE_90)

    @Test
    fun testSpotShadowRotate180() = spotShadowTest(SurfaceControl.BUFFER_TRANSFORM_ROTATE_180)

    @Test
    fun testSpotShadowRotate270() = spotShadowTest(SurfaceControl.BUFFER_TRANSFORM_ROTATE_270)

    private fun Rect.applyBufferTransform(width: Int, height: Int, transform: Int): Rect {
        val rectF = RectF(this)
        val matrix = Matrix()
        when (transform) {
            SurfaceControl.BUFFER_TRANSFORM_ROTATE_90 -> {
                matrix.apply {
                    setRotate(90f)
                    postTranslate(width.toFloat(), 0f)
                }
            }
            SurfaceControl.BUFFER_TRANSFORM_ROTATE_180 -> {
                matrix.apply {
                    setRotate(180f)
                    postTranslate(width.toFloat(), height.toFloat())
                }
            }
            SurfaceControl.BUFFER_TRANSFORM_ROTATE_270 -> {
                matrix.apply {
                    setRotate(270f)
                    postTranslate(0f, width.toFloat())
                }
            }
            SurfaceControl.BUFFER_TRANSFORM_IDENTITY -> {
                matrix.reset()
            }
            else -> throw IllegalArgumentException("Invalid transform value")
        }
        matrix.mapRect(rectF)
        return Rect(
            rectF.left.toInt(),
            rectF.top.toInt(),
            rectF.right.toInt(),
            rectF.bottom.toInt()
        )
    }

    private fun hardwareBufferRendererTest(
        width: Int = TEST_WIDTH,
        height: Int = TEST_HEIGHT,
        format: Int = PixelFormat.RGBA_8888,
        block: (hardwareBuffer: HardwareBuffer, renderer: HardwareBufferRenderer) -> Unit,
    ) {
        val usage = HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
        if (format != PixelFormat.RGBA_8888 &&
            !HardwareBuffer.isSupported(width, height, format, 1, usage)) {
            // Early out if the hardware configuration is not supported.
            // PixelFormat.RGBA_8888 should always be supported
            return
        }
        val hardwareBuffer = HardwareBuffer.create(
            width,
            height,
            format,
            1,
            usage
        )
        val renderer = HardwareBufferRenderer(hardwareBuffer)
        try {
            block(hardwareBuffer, renderer)
        } finally {
            hardwareBuffer.close()
            renderer.close()
        }
    }

    private inline fun RenderNode.record(block: (canvas: Canvas) -> Unit): RenderNode {
        beginRecording().let { canvas ->
            block(canvas)
        }
        endRecording()
        return this
    }
}
