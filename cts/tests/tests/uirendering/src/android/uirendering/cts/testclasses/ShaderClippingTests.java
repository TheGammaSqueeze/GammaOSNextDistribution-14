/*
 * Copyright 2024 The Android Open Source Project
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

package android.uirendering.cts.testclasses;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapcomparers.MSSIMComparer;
import android.uirendering.cts.bitmapverifiers.GoldenImageVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.graphics.hwui.flags.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;


@MediumTest
@RunWith(AndroidJUnit4.class)
public class ShaderClippingTests extends ActivityTestBase {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @RequiresFlagsEnabled(Flags.FLAG_CLIP_SHADER)
    @Test
    public void testBitmapShader() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    Paint bitmapPaint = new Paint();
                    bitmapPaint.setColor(Color.RED);
                    bitmapPaint.setAntiAlias(false);

                    // makes a bitmap 1/4 of the size of canvas that will be repeated in the shader
                    Bitmap bitmap = Bitmap.createBitmap(width / 2, height / 2,
                            Bitmap.Config.ARGB_8888);
                    Canvas bitmapCanvas = new Canvas(bitmap);
                    bitmapCanvas.drawColor(Color.TRANSPARENT);
                    bitmapCanvas.drawRect(11.25f, 11.25f, 33.75f, 33.75f, bitmapPaint);
                    BitmapShader s = new BitmapShader(bitmap, Shader.TileMode.REPEAT,
                            Shader.TileMode.REPEAT);

                    clipAndDraw(canvas, width, height, s, true);
                })
                .runWithVerifier(new GoldenImageVerifier(getActivity(),
                        R.drawable.clipshadertest_bitmapshader, new MSSIMComparer(0.7)));
    }

    @RequiresFlagsEnabled(Flags.FLAG_CLIP_SHADER)
    @Test
    public void testLinearGradient() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    // make the shader
                    LinearGradient s = new LinearGradient(width / 2f, 0, width / 2f, height,
                            Color.TRANSPARENT, Color.RED, Shader.TileMode.DECAL);

                    clipAndDraw(canvas, width, height, s, true);
                })
                .runWithVerifier(new GoldenImageVerifier(getActivity(),
                        R.drawable.clipshadertest_lineargradient, new MSSIMComparer(0.7)));
    }

    @RequiresFlagsEnabled(Flags.FLAG_CLIP_SHADER)
    @Test
    public void testCompose() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    // make the shader
                    LinearGradient s1 = new LinearGradient(width / 2f, 0, width / 2f, height,
                            Color.TRANSPARENT, Color.RED, Shader.TileMode.DECAL);
                    LinearGradient s2 = new LinearGradient(0, height / 2f, width, height / 2f,
                            Color.TRANSPARENT, Color.RED, Shader.TileMode.DECAL);
                    ComposeShader s = new ComposeShader(s1, s2, PorterDuff.Mode.ADD);

                    clipAndDraw(canvas, width, height, s, true);
                })
                .runWithVerifier(new GoldenImageVerifier(getActivity(),
                        R.drawable.clipshadertest_composeshader, new MSSIMComparer(0.7)));
    }

    @RequiresFlagsEnabled(Flags.FLAG_CLIP_SHADER)
    @Test
    public void testRadialGradient() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    RadialGradient s = new RadialGradient(30f, 30f, 20f,
                            Color.RED, Color.TRANSPARENT, Shader.TileMode.CLAMP);

                    clipAndDraw(canvas, width, height, s, true);
                })
                .runWithVerifier(new GoldenImageVerifier(getActivity(),
                        R.drawable.clipshadertest_radialgradient, new MSSIMComparer(0.7)));
    }

    @RequiresFlagsEnabled(Flags.FLAG_CLIP_SHADER)
    @Test
    public void testRuntimeShader() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    RuntimeShader s = new RuntimeShader("""
                         vec4 main(vec2 coord) {
                            float alpha = coord.x / 90.0;
                            return vec4(1.0, 1.0, 1.0, alpha);
                         }
                                 """); // taken from RuntimeShaderTests

                    clipAndDraw(canvas, width, height, s, true);
                })
                .runWithVerifier(new GoldenImageVerifier(getActivity(),
                        R.drawable.clipshadertest_runtimeshader, new MSSIMComparer(0.7)));
    }

    @RequiresFlagsEnabled(Flags.FLAG_CLIP_SHADER)
    @Test
    public void testSweepGradient() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    SweepGradient s = new SweepGradient(width / 2f, height / 2f,
                            Color.RED, Color.TRANSPARENT);

                    clipAndDraw(canvas, width, height, s, true);
                })
                .runWithVerifier(new GoldenImageVerifier(getActivity(),
                        R.drawable.clipshadertest_sweetgradient, new MSSIMComparer(0.7)));
    }

    @RequiresFlagsEnabled(Flags.FLAG_CLIP_SHADER)
    @Test
    public void testClipOutShader() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    // make the shader
                    LinearGradient s = new LinearGradient(width / 2f, 0, width / 2f, height,
                            Color.TRANSPARENT, Color.RED, Shader.TileMode.DECAL);

                    clipAndDraw(canvas, width, height, s, false);
                })
                .runWithVerifier(new GoldenImageVerifier(getActivity(),
                        R.drawable.clipshadertest_clipout, new MSSIMComparer(0.7)));
    }

    /**
     * Takes the given shader, clips the canvas, and draws a centered blue circle.
     */
    private void clipAndDraw(Canvas canvas, int width, int height, Shader s, boolean clipIn) {
        Paint paint = new Paint();
        paint.setAntiAlias(false);
        paint.setColor(Color.BLUE);
        paint.setStyle(Paint.Style.FILL);

        canvas.save();
        if (clipIn) {
            canvas.clipShader(s);
        } else {
            canvas.clipOutShader(s);
        }
        canvas.drawCircle(width / 2f, height / 2f, 40, paint);
        canvas.restore();
    }
}
