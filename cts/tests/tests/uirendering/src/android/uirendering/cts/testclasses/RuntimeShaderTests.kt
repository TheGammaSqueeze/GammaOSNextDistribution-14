/*
 * Copyright (C) 2021 The Android Open Source Project
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
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.ComposeShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.Rect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.uirendering.cts.bitmapverifiers.RectVerifier
import android.uirendering.cts.testinfrastructure.ActivityTestBase
import android.uirendering.cts.testinfrastructure.CanvasClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.android.compatibility.common.util.ApiTest
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class RuntimeShaderTests : ActivityTestBase() {

    @Test(expected = NullPointerException::class)
    fun createWithNullInput() {
        RuntimeShader(Nulls.type<String>())
    }

    @Test(expected = IllegalArgumentException::class)
    fun createWithEmptyInput() {
        RuntimeShader("")
    }

    val bitmapShader = BitmapShader(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
                                    Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

    @Test(expected = NullPointerException::class)
    fun setNullUniformName() {
        val shader = RuntimeShader(simpleShader)
        shader.setFloatUniform(Nulls.type<String>(), 0.0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setEmptyUniformName() {
        val shader = RuntimeShader(simpleShader)
        shader.setFloatUniform("", 0.0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setInvalidUniformName() {
        val shader = RuntimeShader(simpleShader)
        shader.setFloatUniform("invalid", 0.0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setInvalidUniformType() {
        val shader = RuntimeShader(simpleShader)
        shader.setFloatUniform("inputInt", 1.0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setInvalidUniformLength() {
        val shader = RuntimeShader(simpleColorShader)
        shader.setFloatUniform("inputNonColor", 1.0f, 1.0f, 1.0f)
    }

    @Test(expected = NullPointerException::class)
    fun setNullIntUniformName() {
        val shader = RuntimeShader(simpleShader)
        shader.setIntUniform(Nulls.type<String>(), 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setEmptyIntUniformName() {
        val shader = RuntimeShader(simpleShader)
        shader.setIntUniform("", 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setInvalidIntUniformName() {
        val shader = RuntimeShader(simpleShader)
        shader.setIntUniform("invalid", 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setInvalidIntUniformType() {
        val shader = RuntimeShader(simpleShader)
        shader.setIntUniform("inputFloat", 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setInvalidIntUniformLength() {
        val shader = RuntimeShader(simpleShader)
        shader.setIntUniform("inputInt", 1, 2)
    }

    @Test(expected = NullPointerException::class)
    fun setNullColorName() {
        val shader = RuntimeShader(simpleColorShader)
        shader.setColorUniform(Nulls.type<String>(), 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setEmptyColorName() {
        val shader = RuntimeShader(simpleColorShader)
        shader.setColorUniform("", 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setInvalidColorName() {
        val shader = RuntimeShader(simpleColorShader)
        shader.setColorUniform("invalid", 0)
    }

    @Test(expected = NullPointerException::class)
    fun setNullColorValue() {
        val shader = RuntimeShader(simpleColorShader)
        shader.setColorUniform("inputColor", Nulls.type<Color>())
    }

    @Test(expected = IllegalArgumentException::class)
    fun setColorValueNonColorUniform() {
        val shader = RuntimeShader(simpleColorShader)
        shader.setColorUniform("inputNonColor", Color.BLUE)
    }

    @Test(expected = NullPointerException::class)
    fun setNullShaderName() {
        val shader = RuntimeShader(simpleShader)
        shader.setInputShader(Nulls.type<String>(), bitmapShader)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setEmptyShaderName() {
        val shader = RuntimeShader(simpleShader)
        shader.setInputShader("", bitmapShader)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setInvalidShaderName() {
        val shader = RuntimeShader(simpleShader)
        shader.setInputShader("invalid", bitmapShader)
    }

    @Test(expected = NullPointerException::class)
    fun setNullShaderValue() {
        val shader = RuntimeShader(simpleShader)
        shader.setInputShader("inputShader", Nulls.type<Shader>())
    }

    @Test(expected = NullPointerException::class)
    fun setNullBufferName() {
        val shader = RuntimeShader(simpleShader)
        shader.setInputBuffer(Nulls.type<String>(), bitmapShader)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setEmptyBufferName() {
        val shader = RuntimeShader(simpleShader)
        shader.setInputBuffer("", bitmapShader)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setInvalidBufferName() {
        val shader = RuntimeShader(simpleShader)
        shader.setInputBuffer("invalid", bitmapShader)
    }

    @Test(expected = NullPointerException::class)
    fun setNullBufferValue() {
        val shader = RuntimeShader(simpleShader)
        shader.setInputBuffer("inputShader", Nulls.type<BitmapShader>())
    }

    @Test
    fun testDefaultUniform() {
        val shader = RuntimeShader(simpleShader)
        shader.setInputShader("inputShader", RuntimeShader(simpleRedShader))

        val paint = Paint()
        paint.shader = shader

        val rect = Rect(10, 10, 80, 80)

        createTest().addCanvasClient(CanvasClient
                { canvas: Canvas, width: Int, height: Int -> canvas.drawRect(rect, paint) },
                true).runWithVerifier(RectVerifier(Color.WHITE, Color.BLACK, rect))
    }

    @Test
    fun testDefaultColorUniform() {
        val shader = RuntimeShader(simpleColorShader)

        val paint = Paint()
        paint.shader = shader
        paint.blendMode = BlendMode.SRC

        val rect = Rect(10, 10, 80, 80)

        createTest().addCanvasClient(CanvasClient
                { canvas: Canvas, width: Int, height: Int -> canvas.drawRect(rect, paint) },
                true).runWithVerifier(RectVerifier(Color.WHITE, Color.TRANSPARENT, rect))
    }

    @Test
    @Ignore("Evaluating null shaders is undefined (and changing) behavior. b/40045168, b/300305231")
    fun testDefaultInputShader() {
        val paint = Paint()
        paint.color = Color.BLUE
        paint.shader = RuntimeShader(mBlackIfInputNotOpaqueShader)

        val rect = Rect(10, 10, 80, 80)

        createTest().addCanvasClient(CanvasClient
                { canvas: Canvas, width: Int, height: Int -> canvas.drawRect(rect, paint) },
                true).runWithVerifier(RectVerifier(Color.WHITE, paint.color, rect))
    }

    @Test
    @Ignore("Evaluating null shaders is undefined (and changing) behavior. b/40045168, b/300305231")
    fun testDefaultInputShaderWithPaintAlpha() {
        val paint = Paint()
        paint.color = Color.argb(0.5f, 0.0f, 0.0f, 1.0f)
        paint.shader = RuntimeShader(mBlackIfInputNotOpaqueShader)
        paint.blendMode = BlendMode.SRC

        val rect = Rect(10, 10, 80, 80)

        // The shader should be evaluated with an opaque paint color and the paint's alpha will be
        // applied after the shader returns but before it is blended into the destination
        createTest().addCanvasClient(CanvasClient
                { canvas: Canvas, width: Int, height: Int -> canvas.drawRect(rect, paint) },
                true).runWithVerifier(RectVerifier(Color.WHITE, paint.color, rect))
    }

    @Test
    fun testInputShaderWithPaintAlpha() {
        val shader = RuntimeShader(mBlackIfInputNotOpaqueShader)
        shader.setInputShader("inputShader", RuntimeShader(mSemiTransparentBlueShader))

        val paint = Paint()
        paint.color = Color.argb(0.5f, 0.0f, 1.0f, .0f)
        paint.shader = shader
        paint.blendMode = BlendMode.SRC

        val rect = Rect(10, 10, 80, 80)

        // The shader should be evaluated first then the paint's alpha will be applied after the
        // shader returns but before it is blended into the destination
        createTest().addCanvasClient(CanvasClient
                { canvas: Canvas, width: Int, height: Int -> canvas.drawRect(rect, paint) },
                true).runWithVerifier(RectVerifier(Color.WHITE, Color.BLACK, rect))
    }

    @Test
    fun testInputShaderWithFiltering() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, Color.RED)
        bitmap.setPixel(1, 0, Color.BLUE)
        bitmap.setPixel(0, 1, Color.BLUE)
        bitmap.setPixel(1, 1, Color.RED)

        val bitmapShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

        // use slightly left than half to avoid any confusion on which pixel
        // is sampled with FILTER_MODE_NEAREST
        val matrix = Matrix()
        matrix.postScale(0.49f, 0.49f)
        bitmapShader.setLocalMatrix(matrix)

        val shader = RuntimeShader(samplingShader)
        shader.setInputShader("inputShader", bitmapShader)

        val rect = Rect(0, 0, 1, 1)
        val paint = Paint()

        // The bitmap shader should be sampled with FILTER_MODE_NEAREST as the paint's filtering
        // flag is not respected
        paint.shader = shader
        paint.blendMode = BlendMode.SRC
        createTest().addCanvasClient(CanvasClient
                { canvas: Canvas, width: Int, height: Int -> canvas.drawRect(rect, paint) },
                true).runWithVerifier(RectVerifier(Color.WHITE, Color.RED, rect))

        // The bitmap shader should be sampled with FILTER_MODE_LINEAR as the paint's filtering
        // flag is not respected
        paint.isFilterBitmap = false
        bitmapShader.filterMode = BitmapShader.FILTER_MODE_LINEAR
        shader.setInputShader("inputShader", bitmapShader)
        createTest().addCanvasClient(CanvasClient
                { canvas: Canvas, width: Int, height: Int -> canvas.drawRect(rect, paint) },
                true).runWithVerifier(RectVerifier(Color.WHITE,
                Color.valueOf(0.5f, 0.0f, 0.5f).toArgb(), rect))
    }

    private fun getRotateSrgb(): ColorSpace {
        val srgb = ColorSpace.get(ColorSpace.Named.SRGB) as ColorSpace.Rgb
        val rotatedPrimaries = FloatArray(srgb.primaries.size)
        for (i in rotatedPrimaries.indices) {
            rotatedPrimaries[i] = srgb.primaries[(i + 2) % srgb.primaries.size]
        }
        return ColorSpace.Rgb("Rotated sRGB", rotatedPrimaries, srgb.whitePoint,
                              srgb.transferParameters!!)
    }

    @Test
    fun testInputBuffer() {
        val unpremulColor = Color.valueOf(0.0f, 0.0f, 1.0f, 0.5f)

        // create a bitmap with the unpremul value and set a colorspace to something that is
        // different from the destination
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888, true)
        bitmap.setPremultiplied(false)
        bitmap.setPixel(0, 0, unpremulColor.toArgb())
        bitmap.setColorSpace(getRotateSrgb())

        val bitmapShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val shader = RuntimeShader(sampleComparisonShader)
        shader.setFloatUniform("expectedSample", unpremulColor.components)

        val rect = Rect(10, 10, 80, 80)
        val paint = Paint()
        paint.shader = shader
        paint.blendMode = BlendMode.SRC

        // Use setInputBuffer and let the shader verify that the sample contents were unaltered.
        shader.setInputBuffer("inputShader", bitmapShader)
        createTest().addCanvasClient(CanvasClient
                { canvas: Canvas, width: Int, height: Int -> canvas.drawRect(rect, paint) },
                true).runWithVerifier(RectVerifier(Color.WHITE, Color.GREEN, rect))

        // Use setInputShader to treating it like a normal bitmap instead of data to verify
        // everything is working as expected
        shader.setInputShader("inputShader", bitmapShader)
        createTest().addCanvasClient(CanvasClient
                { canvas: Canvas, width: Int, height: Int -> canvas.drawRect(rect, paint) },
                true).runWithVerifier(RectVerifier(Color.WHITE, Color.RED, rect))
    }

    @Test
    fun testBasicColorUniform() {
        val color = Color.valueOf(Color.BLUE).convert(ColorSpace.get(ColorSpace.Named.BT2020))
        val shader = RuntimeShader(simpleColorShader)
        shader.setColorUniform("inputColor", color)

        val paint = Paint()
        paint.shader = shader

        val rect = Rect(10, 10, 80, 80)

        createTest().addCanvasClient(CanvasClient
                { canvas: Canvas, width: Int, height: Int -> canvas.drawRect(rect, paint) },
                true).runWithVerifier(RectVerifier(Color.WHITE, Color.BLUE, rect))
    }

    @Test
    @ApiTest(apis = arrayOf("android.graphics.Shader#setLocalMatrix"))
    fun testComposeShaderLocalMatrix() {
        val shaderA = RuntimeShader(simpleRedShader)

        // Create a runtime shader that calls a decal image shader with translation local matrix.
        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888, true)
        bitmap.eraseColor(Color.GREEN)
        val bitmapShader = BitmapShader(bitmap, Shader.TileMode.DECAL, Shader.TileMode.DECAL)
        val matrix = Matrix()
        matrix.setTranslate(100f, 0f)
        bitmapShader.setLocalMatrix(matrix)
        val shaderB = RuntimeShader(samplingShader)
        shaderB.setInputShader("inputShader", bitmapShader)

        // This compose shader will have a local matrix that compensates for the image shader's
        // translation.
        val composeShader = ComposeShader(shaderA, shaderB, BlendMode.SRC_OVER)
        matrix.setTranslate(-100f, 0f)
        composeShader.setLocalMatrix(matrix)

        val paint = Paint()
        paint.shader = composeShader

        val rect = Rect(0, 0, 20, 20)

        createTest().addCanvasClient(CanvasClient
                { canvas: Canvas, width: Int, height: Int -> canvas.drawRect(rect, paint) },
                true).runWithVerifier(RectVerifier(Color.WHITE, Color.GREEN, rect, 0))
    }

    @Test
    fun testLinearColorIntrinsic() {
        val colorA = Color.valueOf(0.75f, 0.25f, 0.0f, 1.0f)
        val colorB = Color.valueOf(0.0f, 0.75f, 0.25f, 1.0f)
        val shader = RuntimeShader(linearMixShader)
        shader.setColorUniform("inputColorA", colorA)
        shader.setColorUniform("inputColorB", colorB)

        val linearExtendedSRGB = ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB)
        val linearColorA = colorA.convert(linearExtendedSRGB)
        val linearColorB = colorB.convert(linearExtendedSRGB)
        val linearColorMix = Color.valueOf((linearColorA.red() + linearColorB.red()) / 2.0f,
                (linearColorA.green() + linearColorB.green()) / 2.0f,
                (linearColorA.blue() + linearColorB.blue()) / 2.0f,
                (linearColorA.alpha() + linearColorB.alpha()) / 2.0f,
                linearExtendedSRGB)

        val paint = Paint()
        paint.shader = shader

        val rect = Rect(10, 10, 80, 80)

        createTest().addCanvasClient(CanvasClient
        { canvas: Canvas, width: Int, height: Int -> canvas.drawRect(rect, paint) },
                true).runWithVerifier(RectVerifier(Color.WHITE, linearColorMix.toArgb(), rect, 4))
    }

    @Test
    fun testDrawThroughPicture() {
        val rect = Rect(10, 10, 80, 80)
        val picture = Picture()
        run {
            val paint = Paint()
            paint.shader = RuntimeShader(simpleRedShader)

            val canvas = picture.beginRecording(TEST_WIDTH, TEST_HEIGHT)
            canvas.clipRect(rect)
            canvas.drawPaint(paint)
            picture.endRecording()
        }
        Assert.assertTrue(picture.requiresHardwareAcceleration())

        createTest().addCanvasClient(CanvasClient
        { canvas: Canvas, width: Int, height: Int -> canvas.drawPicture(picture) },
                true).runWithVerifier(RectVerifier(Color.WHITE, Color.RED, rect))
    }

    @Test
    fun testDrawThroughPictureWithComposeShader() {
        val rect = Rect(10, 10, 80, 80)
        val picture = Picture()
        run {
            val paint = Paint()
            val runtimeShader = RuntimeShader(simpleRedShader)
            paint.shader = ComposeShader(runtimeShader, bitmapShader, BlendMode.DST)

            val canvas = picture.beginRecording(TEST_WIDTH, TEST_HEIGHT)
            canvas.clipRect(rect)
            canvas.drawPaint(paint)
            picture.endRecording()
        }
        Assert.assertTrue(picture.requiresHardwareAcceleration())

        createTest().addCanvasClient(CanvasClient
        { canvas: Canvas, width: Int, height: Int -> canvas.drawPicture(picture) },
                true).runWithVerifier(RectVerifier(Color.WHITE, Color.RED, rect))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testDrawIntoSoftwareCanvas() {
        val paint = Paint()
        paint.shader = RuntimeShader(simpleRedShader)

        val canvas = Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        canvas.drawRect(0f, 0f, 10f, 10f, paint)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testDrawIntoSoftwareCanvasWithComposeShader() {
        val paint = Paint()
        paint.shader = ComposeShader(RuntimeShader(simpleRedShader), bitmapShader, BlendMode.SRC)

        val canvas = Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        canvas.drawRect(0f, 0f, 10f, 10f, paint)
    }

    val mSemiTransparentBlueShader = """
        vec4 main(vec2 coord) {
          return vec4(0.0, 0.0, 0.5, 0.5);
        }"""
    val simpleRedShader = """
       vec4 main(vec2 coord) {
          return vec4(1.0, 0.0, 0.0, 1.0);
       }"""
    val simpleColorShader = """
        layout(color) uniform vec4 inputColor;
        uniform vec4 inputNonColor;
        uniform int useNonColor;
       vec4 main(vec2 coord) {
          vec4 outputColor = inputColor;
          if (useNonColor != 0) {
            outputColor = inputNonColor;
          }
          return outputColor;
       }"""
    val linearMixShader = """
        layout(color) uniform vec4 inputColorA;
        layout(color) uniform vec4 inputColorB;
       vec4 main(vec2 coord) {
          vec3 linColorA = toLinearSrgb(inputColorA.rgb);
          vec3 linColorB = toLinearSrgb(inputColorB.rgb);
          if (linColorA == inputColorA.rgb) {
            return vec4(1.0, 0.0, 0.0, 1.0);
          }
          if (linColorB == inputColorB.rgb) {
            return vec4(0.0, 0.0, 0.0, 1.0);
          }
          vec3 linMixedColor = mix(linColorA, linColorB, 0.5);
          return fromLinearSrgb(linMixedColor).rgb1;
       }"""
    val samplingShader = """
        uniform shader inputShader;
        vec4 main(vec2 coord) {
          return inputShader.eval(coord).rgba;
        }"""
    val sampleComparisonShader = """
        uniform shader inputShader;
        uniform vec4 expectedSample;
        vec4 main(vec2 coord) {
          vec4 sampledValue = inputShader.eval(coord);
          if (sampledValue.rgb == expectedSample.rgb) {
            return vec4(0.0, 1.0, 0.0, 1.0);
          }
          return vec4(1.0, 0.0, 0.0, 1.0);
        }"""
    val simpleShader = """
        uniform shader inputShader;
        uniform float inputFloat;
        uniform int inputInt;
       vec4 main(vec2 coord) {
          float alpha = float(100 - inputInt) / 100.0;
          return vec4(inputShader.eval(coord).rgb * inputFloat, alpha);
       }"""
    val mBlackIfInputNotOpaqueShader = """
        uniform shader inputShader;
        vec4 main(vec2 coord) {
          vec4 color = inputShader.eval(coord);
          float multiplier = 1.0;
          if (color.a != 1.0) {
            multiplier = 0.0;
          }
          return vec4(color.rgb * multiplier, 1.0);
        }"""
}
