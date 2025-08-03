/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static org.testng.Assert.assertThrows;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.uirendering.cts.bitmapcomparers.MSSIMComparer;
import android.uirendering.cts.bitmapverifiers.GoldenImageVerifier;
import android.uirendering.cts.bitmapverifiers.SamplePointVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class GradientTests extends ActivityTestBase {

    @Test
    public void testAlphaPreMultiplication() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    Paint paint = new Paint();

                    // Add a red background to cover the activity's
                    paint.setColor(Color.RED);
                    canvas.drawRect(0.0f, 0.0f, width, height, paint);

                    paint.setColor(Color.WHITE);
                    paint.setShader(new LinearGradient(
                            0.0f, 0.0f, 0.0f, 40.0f,
                            0xffffffff, 0x00ffffff, Shader.TileMode.CLAMP)
                    );
                    canvas.drawRect(0.0f, 0.0f, width, height, paint);
                }, true)
                .runWithVerifier(new SamplePointVerifier(new Point[] {
                        new Point(0, 0), new Point(0, 39)
                }, new int[] {
                        // Opaque white on red, result is white
                        0xffffffff,
                        // Transparent white on red, result is red
                        // This means the source color (0x00ffffff) was
                        // properly pre-multiplied
                        0xffff0000
                }, 20)); // Tolerance set to account for dithering and interpolation
    }

    @Test
    public void testRadialGradientWithFocalPoint() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    Paint paint = new Paint();
                    float centerX = width / 2f;
                    float centerY = height / 2f;
                    float radius = Math.min(width, height) / 2f;
                    RadialGradient gradient = new RadialGradient(
                            centerX - 10f,
                            centerY - 10f,
                            20f,
                            centerX,
                            centerY,
                            radius,
                            new long[] { Color.pack(Color.RED), Color.pack(Color.CYAN) },
                            null,
                            Shader.TileMode.CLAMP
                    );
                    paint.setShader(gradient);

                    canvas.drawRect(0.0f, 0.0f, width, height, paint);
                }, true)
                .runWithVerifier(
                    new SamplePointVerifier(
                            new Point[]{
                                    new Point(0, 0),
                                    new Point(TEST_WIDTH - 1, 0),
                                    new Point(TEST_WIDTH - 1, TEST_HEIGHT - 1),
                                    new Point(0, TEST_HEIGHT - 1),
                                    new Point(TEST_WIDTH / 2 - 10, TEST_HEIGHT / 2 - 10)
                            },
                            new int[] {
                                    Color.CYAN, Color.CYAN, Color.CYAN, Color.CYAN, Color.RED
                            }
                    )
            );
    }

    @Test
    public void testRadialGradientSameStartEndCircles() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    Paint paint = new Paint();
                    float centerX = width / 2f;
                    float centerY = height / 2f;
                    float radius = Math.min(width, height) / 2f;

                    RadialGradient gradient = new RadialGradient(
                            centerX,
                            centerY,
                            radius,
                            centerX,
                            centerY,
                            radius,
                            new long[] { Color.pack(Color.RED), Color.pack(Color.CYAN) },
                            null,
                            Shader.TileMode.CLAMP
                    );
                    paint.setShader(gradient);

                    canvas.drawRect(0.0f, 0.0f, width, height, paint);
                }, true)
                .runWithVerifier(
                        new SamplePointVerifier(
                                new Point[]{
                                        new Point(0, 0),
                                        new Point(TEST_WIDTH - 1, 0),
                                        new Point(TEST_WIDTH - 1, TEST_HEIGHT - 1),
                                        new Point(0, TEST_HEIGHT - 1),
                                        new Point(TEST_WIDTH / 2, TEST_HEIGHT / 2),
                                        new Point(TEST_WIDTH / 2, 1),
                                        new Point(TEST_WIDTH / 2, TEST_HEIGHT - 1),
                                        new Point(TEST_WIDTH - 1, TEST_HEIGHT / 2),
                                        new Point(0, TEST_HEIGHT / 2)
                                },
                                new int[] {
                                        Color.CYAN,
                                        Color.CYAN,
                                        Color.CYAN,
                                        Color.CYAN,
                                        Color.RED,
                                        Color.RED,
                                        Color.RED,
                                        Color.RED,
                                        Color.RED
                                }
                        )
            );
    }

    @Test
    public void testNegativeFocalRadiusThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new RadialGradient(
                        0f,
                        0f,
                        -1f,
                        10f,
                        10f,
                        10f,
                        new long[] { Color.pack(Color.RED), Color.pack(Color.CYAN) },
                        null,
                        Shader.TileMode.CLAMP
        ));
    }

    @Test
    public void testMismatchColorsAndStopsThrows() {
        assertThrows(IllegalArgumentException.class, () -> new RadialGradient(
                0f,
                0f,
                10f,
                10f,
                10f,
                10f,
                new long[] { Color.pack(Color.RED), Color.pack(Color.CYAN) },
                new float[] { 0.5f},
                Shader.TileMode.CLAMP
        ));
    }

    private Bitmap createRadialGradientGoldenBitmap() {
        Bitmap srcBitmap = Bitmap.createBitmap(TEST_WIDTH, TEST_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas srcCanvas = new Canvas(srcBitmap);
        Paint srcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        srcPaint.setShader(new RadialGradient(
                TEST_WIDTH / 2f,
                TEST_HEIGHT / 2f,
                TEST_WIDTH / 2f,
                new int[] { Color.RED, Color.CYAN },
                null,
                Shader.TileMode.CLAMP
        ));
        srcCanvas.drawRect(0f, 0f, TEST_WIDTH, TEST_HEIGHT, srcPaint);
        return srcBitmap;
    }

    @Test
    public void testRadialGradientWithFocalPointMatchesRegularRadialGradient() {
        Bitmap golden = createRadialGradientGoldenBitmap();
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    Paint paint = new Paint();

                    RadialGradient gradient = new RadialGradient(
                            TEST_WIDTH / 2f,
                            TEST_HEIGHT / 2f,
                            0f,
                            TEST_WIDTH / 2f,
                            TEST_HEIGHT / 2f,
                            TEST_WIDTH / 2f,
                            new long[] { Color.pack(Color.RED), Color.pack(Color.CYAN) },
                            null,
                            Shader.TileMode.CLAMP
                    );
                    paint.setShader(gradient);

                    canvas.drawRect(0.0f, 0.0f, width, height, paint);
                }, true)
                .runWithVerifier(
                        new GoldenImageVerifier(golden, new MSSIMComparer(0.99f)));
    }

    @Test
    public void testZeroEndRadiusThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new RadialGradient(
                        10f,
                        10f,
                        0, // invalid
                        new long[] { Color.pack(Color.RED), Color.pack(Color.BLUE)},
                        null,
                        Shader.TileMode.CLAMP
                )
        );
    }

    @Test
    public void testNullColorsThrows() {
        assertThrows(NullPointerException.class, () ->
                new RadialGradient(
                        10f,
                        10f,
                        10f,
                        (long[]) null, // invalid
                        null,
                        Shader.TileMode.CLAMP
                )
        );
    }

    @Test
    public void testMatrixTransformation() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    Paint paint = new Paint();

                    float radius = TEST_WIDTH / 2f;
                    float centerX = TEST_WIDTH / 2f;
                    float centerY = TEST_HEIGHT / 2f;
                    // Pass in the same parameters for the start and end circles
                    // to get a similar result of drawing a circle which simplifies
                    // comparison use cases for testing
                    RadialGradient gradient = new RadialGradient(
                            centerX,
                            centerY,
                            radius,
                            centerX,
                            centerY,
                            radius,
                            new long[] { Color.pack(Color.RED), Color.pack(Color.CYAN) },
                            null,
                            Shader.TileMode.CLAMP
                    );
                    Matrix matrix = new Matrix();
                    matrix.postTranslate(radius, radius);
                    gradient.setLocalMatrix(matrix);
                    paint.setShader(gradient);

                    canvas.drawRect(0.0f, 0.0f, width, height, paint);
                }, true)
                .runWithVerifier(
                        new SamplePointVerifier(
                                new Point[]{
                                        new Point(TEST_WIDTH / 2, TEST_HEIGHT / 2),
                                        new Point(TEST_WIDTH - 1, TEST_HEIGHT - 1),
                                        new Point(TEST_WIDTH - 1, TEST_HEIGHT / 2 + 1),
                                        new Point(TEST_WIDTH / 2 + 1, TEST_HEIGHT - 1),
                                        new Point(TEST_WIDTH / 2 - 1, TEST_HEIGHT - 1)
                                },
                                new int[] {
                                        Color.CYAN,
                                        Color.RED,
                                        Color.RED,
                                        Color.RED,
                                        Color.CYAN
                                }
                        )
            );
    }
}
