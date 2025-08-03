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

package android.view.cts;

import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_SCISSOR_TEST;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glClearColor;
import static android.opengl.GLES20.glEnable;
import static android.opengl.GLES20.glScissor;
import static android.view.WindowInsets.Type.captionBar;
import static android.view.WindowInsets.Type.navigationBars;
import static android.view.WindowInsets.Type.statusBars;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.DataSpace;
import android.media.Image;
import android.media.ImageWriter;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.util.Half;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.cts.util.BitmapDumper;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.SynchronousPixelCopy;
import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@MediumTest
@RunWith(JUnitParamsRunner.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class TextureViewTest {

    static final int EGL_GL_COLORSPACE_SRGB_KHR = 0x3089;
    static final int EGL_GL_COLORSPACE_LINEAR_KHR = 0x308A;
    static final int EGL_GL_COLORSPACE_DISPLAY_P3_EXT = 0x3363;
    static final int EGL_GL_COLORSPACE_DISPLAY_P3_LINEAR_EXT = 0x3362;
    static final int EGL_GL_COLORSPACE_DISPLAY_P3_PASSTHROUGH_EXT = 0x3490;
    static final int EGL_GL_COLORSPACE_SCRGB_EXT = 0x3351;
    static final int EGL_GL_COLORSPACE_SCRGB_LINEAR_EXT = 0x3350;

    private Instrumentation mInstrumentation;

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        assertNotNull(mInstrumentation);
    }

    @Rule(order = 0)
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            androidx.test.platform.app.InstrumentationRegistry
                    .getInstrumentation().getUiAutomation(),
            Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);

    @Rule(order = 1)
    public ActivityTestRule<TextureViewCtsActivity> mActivityRule =
            new ActivityTestRule<>(TextureViewCtsActivity.class, false, false);

    @Rule(order = 1)
    public ActivityTestRule<SDRTestActivity> mSDRActivityRule =
            new ActivityTestRule<>(SDRTestActivity.class, false, false);

    @Rule
    public TestName mTestName = new TestName();

    @Test
    public void testFirstFrames() throws Throwable {
        final TextureViewCtsActivity activity = mActivityRule.launchActivity(null);
        activity.waitForEnterAnimationComplete();

        final Point center = new Point();
        final Window[] windowRet = new Window[1];
        mActivityRule.runOnUiThread(() -> {
            View content = activity.findViewById(android.R.id.content);
            int[] outLocation = new int[2];
            content.getLocationInWindow(outLocation);
            center.x = outLocation[0] + (content.getWidth() / 2);
            center.y = outLocation[1] + (content.getHeight() / 2);
            windowRet[0] = activity.getWindow();
        });
        final Window window = windowRet[0];
        assertTrue(center.x > 0);
        assertTrue(center.y > 0);
        waitForColor(window, center, Color.WHITE);
        activity.waitForSurface();
        activity.initGl();
        int updatedCount;
        // If the caption bar is present, the surface update counts increase by 1
        int extraSurfaceOffset =
                window.getDecorView().getRootWindowInsets().getInsets(captionBar()).top == 0
                ? 0 : 1;
        updatedCount = activity.waitForSurfaceUpdateCount(0 + extraSurfaceOffset);
        assertEquals(0 + extraSurfaceOffset, updatedCount);
        activity.drawColor(Color.GREEN);
        updatedCount = activity.waitForSurfaceUpdateCount(1 + extraSurfaceOffset);
        assertEquals(1 + extraSurfaceOffset, updatedCount);
        assertEquals(Color.WHITE, getPixel(window, center));
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule,
                activity.findViewById(android.R.id.content), () -> activity.removeCover());

        int color = waitForChange(window, center, Color.WHITE);
        assertEquals(Color.GREEN, color);
        activity.drawColor(Color.BLUE);
        updatedCount = activity.waitForSurfaceUpdateCount(2 + extraSurfaceOffset);
        assertEquals(2 + extraSurfaceOffset, updatedCount);
        color = waitForChange(window, center, color);
        assertEquals(Color.BLUE, color);
    }

    @Test
    public void testScaling() throws Throwable {
        final TextureViewCtsActivity activity = mActivityRule.launchActivity(null);
        activity.drawFrame(TextureViewTest::drawGlQuad);
        final Bitmap bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        mActivityRule.runOnUiThread(() -> {
            activity.getTextureView().getBitmap(bitmap);
        });
        assertBitmapQuadColor(bitmap,
                Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
    }

    @Test
    public void testRotateScale() throws Throwable {
        final TextureViewCtsActivity activity = mActivityRule.launchActivity(null);
        final TextureView textureView = activity.getTextureView();
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, activity.getTextureView(), null);
        Matrix rotate = new Matrix();
        rotate.setRotate(180, textureView.getWidth() / 2, textureView.getHeight() / 2);
        activity.drawFrame(rotate, TextureViewTest::drawGlQuad);
        final Bitmap bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        mActivityRule.runOnUiThread(() -> {
            activity.getTextureView().getBitmap(bitmap);
        });
        // Verify the matrix did not rotate content of getTextureView.getBitmap().
        assertBitmapQuadColor(bitmap,
                Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);

        // Remove cover and calculate TextureView position on the screen.
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule,
                activity.findViewById(android.R.id.content), () -> activity.removeCover());
        final Rect viewPos = new Rect();
        mActivityRule.runOnUiThread(() -> {
            int[] outLocation = new int[2];
            textureView.getLocationInWindow(outLocation);
            viewPos.left = outLocation[0];
            viewPos.top = outLocation[1];
            viewPos.right = viewPos.left + textureView.getWidth();
            viewPos.bottom = viewPos.top + textureView.getHeight();
        });

        // Capture the portion of the screen that contains the texture view only.
        Window window = activity.getWindow();
        Bitmap screenshot = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        int result = new SynchronousPixelCopy().request(window, viewPos, screenshot);
        assertEquals("Copy request failed", PixelCopy.SUCCESS, result);
        // Verify the matrix rotated the TextureView content drawn on the screen.
        assertBitmapQuadColor(screenshot,
                Color.BLACK, Color.BLUE, Color.GREEN, Color.RED);
    }

    @Test
    public void testTransformScale() throws Throwable {
        final TextureViewCtsActivity activity = mActivityRule.launchActivity(null);
        final TextureView textureView = activity.getTextureView();
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, activity.getTextureView(), null);
        Matrix transform = new Matrix();
        final float translateY = 100.0f;
        final float scaleY = 0.25f;
        float[] values = {1, 0, 0, 0, scaleY, translateY, 0, 0, 1};
        transform.setValues(values);
        activity.drawFrame(transform, TextureViewTest::drawGlQuad);
        final Bitmap bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        mActivityRule.runOnUiThread(() -> {
            activity.getTextureView().getBitmap(bitmap);
        });
        // Verify the matrix did not affect the content of getTextureView.getBitmap().
        assertBitmapQuadColor(bitmap,
                Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);

        // Remove cover and calculate TextureView position on the screen.
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule,
                activity.findViewById(android.R.id.content), () -> activity.removeCover());
        final Rect viewPos = new Rect();
        mActivityRule.runOnUiThread(() -> {
            int[] outLocation = new int[2];
            textureView.getLocationInWindow(outLocation);
            viewPos.left = outLocation[0];
            viewPos.top = outLocation[1];
            viewPos.right = viewPos.left + textureView.getWidth();
            viewPos.bottom = viewPos.top + textureView.getHeight();
        });

        // Capture the portion of the screen that contains the texture view only.
        Window window = activity.getWindow();
        Bitmap screenshot = Bitmap.createBitmap(viewPos.width(), viewPos.height(),
                Bitmap.Config.ARGB_8888);
        int result = new SynchronousPixelCopy().request(window, viewPos, screenshot);
        assertEquals("Copy request failed", PixelCopy.SUCCESS, result);
        // Verify the matrix scaled and translated the TextureView content drawn on the screen.
        // "texturePos" has SurfaceTexture position inside the TextureView.
        final Rect texturePos = new Rect(0, (int) translateY, viewPos.width(),
                (int) (viewPos.height() * scaleY + translateY));

        // Areas not covered by the texture are black, because FrameLayout background is set to
        // Color.BLACK in TextureViewCtsActivity.onCreate.
        assertEquals("above texture", Color.BLACK,
                screenshot.getPixel(10, texturePos.top - 10));
        assertEquals("below texture", Color.BLACK,
                screenshot.getPixel(10, texturePos.bottom + 10));
        assertEquals("top left", Color.RED,
                screenshot.getPixel(texturePos.left + 10, texturePos.top + 10));
        assertEquals("top right", Color.GREEN,
                screenshot.getPixel(texturePos.right - 10, texturePos.top + 10));
        assertEquals("Bottom left", Color.BLUE,
                screenshot.getPixel(texturePos.left + 10, texturePos.bottom - 10));
        assertEquals("Bottom right", Color.BLACK,
                screenshot.getPixel(texturePos.right - 10, texturePos.bottom - 10));
    }

    // TODO(b/229173479): understand why DCI_P3 and BT2020 do not match with certain colors.
    // TODO(b/230400473): Add in BT2020 and BT709 and BT601 once SurfaceFlinger reliably color
    // converts.
    private static Object[] testDataSpaces() {
        return new Integer[]{
            DataSpace.DATASPACE_SCRGB_LINEAR,
            DataSpace.DATASPACE_SRGB,
            DataSpace.DATASPACE_SCRGB,
            DataSpace.DATASPACE_DISPLAY_P3,
            DataSpace.DATASPACE_ADOBE_RGB,
            DataSpace.DATASPACE_DCI_P3,
            DataSpace.DATASPACE_SRGB_LINEAR
        };
    }

    @Test
    @Parameters(method = "testDataSpaces")
    public void testSDRFromSurfaceViewAndTextureView(int dataSpace) throws Throwable {
        final int grayishYellow = 0xFFBABAB9;
        long converted = Color.convert(grayishYellow, ColorSpace.getFromDataSpace(dataSpace));

        final SDRTestActivity activity =
                mSDRActivityRule.launchActivity(/*startIntent*/ null);
        activity.waitForEnterAnimationComplete();

        TextureView textureView = activity.getTextureView();
        // SurfaceView and TextureView dimensions are the same so we reuse variables
        int width = textureView.getWidth();
        int height = textureView.getHeight();

        // paint surfaceView layer
        SurfaceView surfaceView = activity.getSurfaceView();
        final CountDownLatch latch = new CountDownLatch(1);
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                ImageWriter writer = new ImageWriter
                        .Builder(holder.getSurface())
                        .setHardwareBufferFormat(PixelFormat.RGBA_8888)
                        .setDataSpace(dataSpace)
                        .build();
                // spawn a thread here to iterate 10 times from image dequeue to queue
                // so that we can be stalled until the first frame has been displayed.
                new Thread(() -> {
                    Bitmap bitmap = null;
                    for (int i = 0; i < 10; i++) {
                        Image image = writer.dequeueInputImage();
                        assertEquals(dataSpace, image.getDataSpace());
                        Image.Plane plane = image.getPlanes()[0];
                        // only make bitmap the first time to improve the performation
                        // if the bitmap is large.
                        if (bitmap == null) {
                            bitmap = Bitmap.createBitmap(plane.getRowStride() / 4,
                                image.getHeight(),
                                Bitmap.Config.ARGB_8888, true,
                                ColorSpace.getFromDataSpace(dataSpace));
                            Canvas canvas = new Canvas(bitmap);
                            canvas.drawColor(converted);
                        }
                        bitmap.copyPixelsToBuffer(plane.getBuffer());
                        writer.queueInputImage(image);
                    }
                    latch.countDown();
                }).start();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {}
        });

        WidgetTestUtils.runOnMainAndDrawSync(mSDRActivityRule, surfaceView, () -> {
            ((ViewGroup) surfaceView.getParent()).removeView(surfaceView);
            activity.setContentView(surfaceView);
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        Bitmap surfaceViewScreenshot = mInstrumentation
                .getUiAutomation()
                .takeScreenshot(activity.getWindow());

        WidgetTestUtils.runOnMainAndDrawSync(mSDRActivityRule, textureView, () -> {
            ((ViewGroup) textureView.getParent()).removeView(textureView);
            activity.setContentView(textureView);
        });

         // paint textureView layer
        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
        Surface surface = new Surface(surfaceTexture);
        assertTrue(surface.isValid());

        ImageWriter writer = new ImageWriter
                .Builder(surface)
                .setHardwareBufferFormat(PixelFormat.RGBA_8888)
                .setDataSpace(dataSpace)
                .build();
        Image image = writer.dequeueInputImage();
        assertEquals(dataSpace, image.getDataSpace());
        Image.Plane plane = image.getPlanes()[0];
        Bitmap bitmap = Bitmap.createBitmap(plane.getRowStride() / 4, image.getHeight(),
                Bitmap.Config.ARGB_8888, true, ColorSpace.getFromDataSpace(dataSpace));
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(converted);
        bitmap.copyPixelsToBuffer(plane.getBuffer());
        writer.queueInputImage(image);

        final Bitmap textureViewScreenshot =
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        // surfaceViewScreenshot colorspace depends on SF colormode selection,
        // i.e., Display_P3 or sRGB, therefore, change textureViewScreenshot's bitmap
        // colorspace to be aligned with it
        textureViewScreenshot.setColorSpace(surfaceViewScreenshot.getColorSpace());

        WidgetTestUtils.runOnMainAndDrawSync(
                mSDRActivityRule, textureView, () -> textureView.getBitmap(textureViewScreenshot));

        WindowInsets rootWindowInsets = activity.getWindow().getDecorView().getRootWindowInsets();
        int extraSurfaceTopOffset = rootWindowInsets.getInsets(captionBar()).top;
        // If the caption bar is present, the surface top edge in the screenshot is shifted.
        extraSurfaceTopOffset += rootWindowInsets.getInsets(statusBars()).top;

        int extraSurfaceBottomOffset = rootWindowInsets.getInsets(navigationBars()).bottom;

        // sample 5 pixels on the edge for bitmap comparison.
        // TextureView and SurfaceView use different shaders, so compare these two with tolerance.
        // TODO(b/229173479): These shaders shouldn't be very different. Figure out why we need
        // this tolerance in the first place.
        final int threshold = 3;
        try {
            assertPixelsAreSame(surfaceViewScreenshot.getPixel(width / 2, extraSurfaceTopOffset),
                    textureViewScreenshot.getPixel(width / 2, 0), threshold);
            assertPixelsAreSame(surfaceViewScreenshot.getPixel(0, height / 2),
                    textureViewScreenshot.getPixel(0, height / 2), threshold);
            assertPixelsAreSame(surfaceViewScreenshot.getPixel(width / 2, height / 2),
                    textureViewScreenshot.getPixel(width / 2, height / 2), threshold);
            assertPixelsAreSame(
                    surfaceViewScreenshot.getPixel(width / 2,
                            height - 1 - extraSurfaceBottomOffset),
                    textureViewScreenshot.getPixel(width / 2,
                            height - 1),
                    threshold);
            assertPixelsAreSame(surfaceViewScreenshot.getPixel(width - 1, height / 2),
                    textureViewScreenshot.getPixel(width - 1, height / 2), threshold);
        } catch (AssertionError err) {
            BitmapDumper.dumpBitmap(textureViewScreenshot,
                    mTestName.getMethodName() + "_textureView", "TextureViewTest");
            BitmapDumper.dumpBitmap(surfaceViewScreenshot,
                    mTestName.getMethodName() + "_surfaceView", "TextureViewTest");
            throw err;
        }
    }

    @Test
    public void testCropRect() throws Throwable {
        final TextureViewCtsActivity activity = mActivityRule.launchActivity(/*startIntent*/ null);
        activity.waitForSurface();
        final TextureView textureView = activity.getTextureView();
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, textureView, () -> {
            activity.removeCover();
            // This test is sensitive to GPU sampling precision, so cap the size of the textureview
            // to a known small value that will not have float precision issues when being
            // sampled, specifically when running on GPUs limited to fp16 precision
            ViewGroup.LayoutParams params = textureView.getLayoutParams();
            params.width = 100;
            params.height = 100;
            textureView.setLayoutParams(params);
        });
        int textureWidth = textureView.getWidth();
        int textureHeight = textureView.getHeight();
        assertEquals(100, textureWidth);
        assertEquals(100, textureHeight);
        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
        Surface surface = new Surface(surfaceTexture);
        assertTrue(surface.isValid());
        ImageWriter writer = ImageWriter.newInstance(surface, /*maxImages*/ 1);
        Image image = writer.dequeueInputImage();
        assertEquals(100, image.getWidth());
        assertEquals(100, image.getHeight());
        Image.Plane plane = image.getPlanes()[0];
        Bitmap bitmap = Bitmap.createBitmap(plane.getRowStride() / 4, image.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(false);
        paint.setColor(Color.YELLOW);
        canvas.drawRect(0f, 0f, textureWidth, textureHeight, paint);
        paint.setColor(Color.BLACK);
        canvas.drawRect(2f, 2f, textureWidth - 2f, textureHeight - 2f, paint);

        image.setCropRect(new Rect(1, 1, textureWidth - 1, textureHeight - 1));
        bitmap.copyPixelsToBuffer(plane.getBuffer());
        writer.queueInputImage(image);
        waitForDraw(textureView);

        final Rect viewPos = new Rect();
        mActivityRule.runOnUiThread(() -> {
            int[] outLocation = new int[2];
            textureView.getLocationInSurface(outLocation);
            viewPos.left = outLocation[0];
            viewPos.top = outLocation[1];
            viewPos.right = viewPos.left + textureView.getWidth();
            viewPos.bottom = viewPos.top + textureView.getHeight();
        });
        SynchronousPixelCopy pixelCopy = new SynchronousPixelCopy();
        // Capture the portion of the screen that contains the texture view only.
        Window window = activity.getWindow();
        bitmap = Bitmap.createBitmap(viewPos.width(), viewPos.height(),
                Bitmap.Config.ARGB_8888);
        int result = pixelCopy.request(window, viewPos, bitmap);
        assertEquals("Copy request failed", PixelCopy.SUCCESS, result);
        assertBitmapEdgeColor(bitmap, Color.YELLOW);
    }

    // TODO(b/220361081) replace with runOnMainAndDrawSync once we have the
    // runOnMainAndDrawSync updated to use the registerFrameCommitCallback
    private void waitForDraw(final View view) throws Throwable {
        final CountDownLatch latch = new CountDownLatch(1);
        mActivityRule.runOnUiThread(() -> {
            view.getViewTreeObserver().registerFrameCommitCallback(latch::countDown);
            view.invalidate();
        });
        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    private boolean pixelsAreSame(int ideal, int given, int threshold) {
        int error = Math.abs(Color.red(ideal) - Color.red(given));
        error += Math.abs(Color.green(ideal) - Color.green(given));
        error += Math.abs(Color.blue(ideal) - Color.blue(given));
        return (error < threshold);
    }

    private void assertPixelsAreSame(int ideal, int given, int threshold) {
        if (!pixelsAreSame(ideal, given, threshold)) {
            fail("expected=" + Integer.toHexString(ideal) + ", actual="
                    + Integer.toHexString(given));
        }
    }

    @Test
    public void testGetBitmap_8888_P3() throws Throwable {
        testGetBitmap(EGL_GL_COLORSPACE_DISPLAY_P3_EXT, ColorSpace.get(ColorSpace.Named.DISPLAY_P3),
                false, false, new FP16Compare(ColorSpace.Named.EXTENDED_SRGB));
    }

    @Test
    public void testGetBitmap_8888_PassthroughP3() throws Throwable {
        testGetBitmap(EGL_GL_COLORSPACE_DISPLAY_P3_PASSTHROUGH_EXT,
                ColorSpace.get(ColorSpace.Named.DISPLAY_P3), false, true,
                new FP16Compare(ColorSpace.Named.EXTENDED_SRGB));
    }

    @Test
    public void testGetBitmap_FP16_PassthroughP3() throws Throwable {
        testGetBitmap(EGL_GL_COLORSPACE_DISPLAY_P3_PASSTHROUGH_EXT,
                ColorSpace.get(ColorSpace.Named.DISPLAY_P3), true, true,
                new FP16Compare(ColorSpace.Named.EXTENDED_SRGB));
    }

    @Test
    public void testGetBitmap_FP16_LinearP3() throws Throwable {
        ColorSpace.Rgb displayP3 = (ColorSpace.Rgb) ColorSpace.get(ColorSpace.Named.DISPLAY_P3);
        ColorSpace.Rgb linearDisplayP3 = new ColorSpace.Rgb(
                "Display P3 Linear",
                displayP3.getTransform(),
                displayP3.getWhitePoint(),
                x -> x,
                x -> x,
                0.0f, 1.0f
        );

        testGetBitmap(EGL_GL_COLORSPACE_DISPLAY_P3_LINEAR_EXT, linearDisplayP3, true,
                true, new FP16Compare(ColorSpace.Named.EXTENDED_SRGB));
    }

    @Test
    public void testGetBitmap_FP16_ExtendedSRGB() throws Throwable {
        // isLinear is "true", because the spec says
        // GL_FRAMEBUFFER_ATTACHMENT_COLOR_ENCODING is GL_LINEAR for EGL_GL_COLORSPACE_SCRGB_EXT.
        // See https://www.khronos.org/registry/EGL/extensions/EXT/EGL_EXT_gl_colorspace_scrgb.txt.
        testGetBitmap(EGL_GL_COLORSPACE_SCRGB_EXT,
                ColorSpace.get(ColorSpace.Named.EXTENDED_SRGB), true,
                true, new FP16Compare(ColorSpace.Named.EXTENDED_SRGB));
    }

    @Test
    public void testGetBitmap_FP16_LinearExtendedSRGB() throws Throwable {
        testGetBitmap(EGL_GL_COLORSPACE_SCRGB_LINEAR_EXT,
                ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB), true,
                true, new FP16Compare(ColorSpace.Named.EXTENDED_SRGB));
    }

    @Test
    public void testGet565Bitmap_SRGB() throws Throwable {
        testGetBitmap(EGL_GL_COLORSPACE_SRGB_KHR, ColorSpace.get(ColorSpace.Named.SRGB),
                false, false, new SRGBCompare(Bitmap.Config.RGB_565));
    }

    @Test
    public void testGetBitmap_SRGB() throws Throwable {
        testGetBitmap(EGL_GL_COLORSPACE_SRGB_KHR, ColorSpace.get(ColorSpace.Named.SRGB),
                false, false, new SRGBCompare(Bitmap.Config.ARGB_8888));
    }

    @Test
    public void testGetBitmap_SRGBLinear() throws Throwable {
        testGetBitmap(EGL_GL_COLORSPACE_LINEAR_KHR, ColorSpace.get(ColorSpace.Named.LINEAR_SRGB),
                false, true, new SRGBCompare(Bitmap.Config.ARGB_8888));
    }

    /**
     *  Test that verifies TextureView is drawn with bilerp sampling, when the matrix is not
     *  an integer translate or identity.
     */
    @Test
    public void testSamplingWithTransform() throws Throwable {
        final TextureViewCtsActivity activity = mActivityRule.launchActivity(null);
        final TextureView textureView = activity.getTextureView();
        final int viewWidth = textureView.getWidth();
        final int viewHeight = textureView.getHeight();
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, activity.getTextureView(), null);
        // Remove cover and calculate TextureView position on the screen.
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule,
                activity.findViewById(android.R.id.content), () -> activity.removeCover());

        float[][] matrices = {
            {1, 0, 0, 0, 1, 0, 0, 0, 1},            // identity matrix
            {1, 0, 0, 0, 1, 10.3f, 0, 0, 1},        // translation matrix with a fractional offset
            {1, 0, 0, 0, 0.75f, 0, 0, 0, 1},        // scaling matrix
            {1, 0, 0, 0, 1, 10f, 0, 0, 1},          // translation matrix with an integer offset
            {0, -1, viewWidth, 1, 0, 0, 0, 0, 1},   // 90 rotation matrix + integer translate X
            {0, 1, 0, -1, 0, viewWidth, 0, 0, 1},   // 270 rotation matrix + integer translate Y
            {-1, 0, viewWidth, 0, 1, 0, 0, 0, 1},   // H flip matrix + integer translate X
            {1, 0, 0, 0, -1, viewHeight, 0, 0, 1},  // V flip matrix + integer translate Y
            {-1, 0, viewWidth, 0, -1, viewHeight, 0, 0, 1}, // 180 rotation + integer translate X Y
            {0, -1, viewWidth - 10.3f, 1, 0, 0, 0, 0, 1},  // 90 rotation matrix with a fractional
                                                           // offset
        };
        boolean[] nearestSampling = {
            true,  // nearest sampling for identity
            false, // bilerp sampling for fractional translate
            false, // bilerp sampling for scaling
            true,  // nearest sampling for integer translate
            true,  // nearest sampling for 90 rotation with integer translate
            true,  // nearest sampling for 270 rotation with integer translate
            true,  // nearest sampling for H flip with integer translate
            true,  // nearest sampling for V flip with integer translate
            true,  // nearest sampling for 180 rotation with integer translate
            false, // bilerp sampling for 90 rotation matrix with a fractional offset
        };
        for (int i = 0; i < nearestSampling.length; i++) {

            Matrix transform = new Matrix();
            transform.setValues(matrices[i]);

            // Test draws a set of black & white alternating lines.
            activity.drawFrame(transform, TextureViewTest::drawGlBlackWhiteLines);

            final Rect viewPos = new Rect();
            mActivityRule.runOnUiThread(() -> {
                int[] outLocation = new int[2];
                textureView.getLocationInWindow(outLocation);
                viewPos.left = outLocation[0];
                viewPos.top = outLocation[1];
                viewPos.right = viewPos.left + textureView.getWidth();
                viewPos.bottom = viewPos.top + textureView.getHeight();
            });

            // Capture the portion of the screen that contains the texture view only.
            Window window = activity.getWindow();
            Bitmap screenshot = Bitmap.createBitmap(viewPos.width(), viewPos.height(),
                    Bitmap.Config.ARGB_8888);
            int result = new SynchronousPixelCopy().request(window, viewPos, screenshot);
            assertEquals("Copy request failed", PixelCopy.SUCCESS, result);

            // "texturePos" has SurfaceTexture position inside the TextureView.
            RectF texturePosF = new RectF(0, 0, viewPos.width(), viewPos.height());
            transform.mapRect(texturePosF);
            // Clip parts outside TextureView.
            // Matrices are picked, so that the drawing area is not empty.
            assertTrue("empty test area",
                    texturePosF.intersect(0, 0, viewPos.width(), viewPos.height()));
            Rect texturePos = new Rect((int) Math.ceil(texturePosF.left),
                    (int) Math.ceil(texturePosF.top), (int) Math.floor(texturePosF.right),
                    (int) Math.floor(texturePosF.bottom));

            int[] pixels = new int[texturePos.width() * texturePos.height()];
            screenshot.getPixels(pixels, 0, texturePos.width(), texturePos.left, texturePos.top,
                    texturePos.width(), texturePos.height());

            boolean success = true;
            int failPosition = 0;
            if (nearestSampling[i]) {
                // Check all pixels are either black or white.
                for (int j = 0; j < pixels.length; j++) {
                    if (pixels[j] != Color.BLACK && pixels[j] != Color.WHITE) {
                        success = false;
                        failPosition = j;
                        break;
                    }
                }
            } else {
                // Check that a third of pixels are not black nor white, because bilerp sampling
                // changed pure black/white to a variety of gray intermediates.
                int nonBlackWhitePixels = 0;
                for (int j = 0; j < pixels.length; j++) {
                    if (pixels[j] != Color.BLACK && pixels[j] != Color.WHITE) {
                        nonBlackWhitePixels++;
                    } else {
                        failPosition = j;
                    }
                }
                if (nonBlackWhitePixels < pixels.length / 3) {
                    success = false;
                }
            }
            assertTrue("Unexpected color at position " + failPosition + " = "
                    + Integer.toHexString(pixels[failPosition]) + " " + transform.toString(),
                    success);
        }
    }

    interface CompareFunction {
        Bitmap.Config getConfig();
        ColorSpace getColorSpace();
        void verify(float[] srcColor, ColorSpace srcColorSpace, Bitmap dstBitmap);
    }

    private class FP16Compare implements CompareFunction {
        private ColorSpace mDstColorSpace;

        FP16Compare(ColorSpace.Named namedCS) {
            mDstColorSpace = ColorSpace.get(namedCS);
        }

        public Bitmap.Config getConfig() {
            return Bitmap.Config.RGBA_F16;
        }

        public ColorSpace getColorSpace() {
            return mDstColorSpace;
        }

        public void verify(float[] srcColor, ColorSpace srcColorSpace, Bitmap dstBitmap) {
            // read pixels into buffer and compare using colorspace connector
            ByteBuffer buffer = ByteBuffer.allocate(dstBitmap.getAllocationByteCount());
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            dstBitmap.copyPixelsToBuffer(buffer);
            Half alpha = Half.valueOf(buffer.getShort(6));
            assertEquals(1.0f, alpha.floatValue(), 0.0f);

            final ColorSpace dstSpace = getColorSpace();
            float[] expectedColor = ColorSpace.connect(srcColorSpace, dstSpace).transform(srcColor);
            float[] outputColor = {
                    Half.valueOf(buffer.getShort(0)).floatValue(),
                    Half.valueOf(buffer.getShort(2)).floatValue(),
                    Half.valueOf(buffer.getShort(4)).floatValue() };

            assertEquals(expectedColor[0], outputColor[0], 0.01f);
            assertEquals(expectedColor[1], outputColor[1], 0.01f);
            assertEquals(expectedColor[2], outputColor[2], 0.01f);
        }
    }

    private class SRGBCompare implements CompareFunction {
        private Bitmap.Config mConfig;

        SRGBCompare(Bitmap.Config config) {
            mConfig = config;
        }

        public Bitmap.Config getConfig() {
            return mConfig;
        }

        public ColorSpace getColorSpace() {
            return ColorSpace.get(ColorSpace.Named.SRGB);
        }

        public void verify(float[] srcColor, ColorSpace srcColorSpace, Bitmap dstBitmap) {
            int color = dstBitmap.getPixel(0, 0);
            assertEquals(1.0f, Color.alpha(color) / 255.0f, 0.0f);
            assertEquals(srcColor[0], Color.red(color) / 255.0f, 0.01f);
            assertEquals(srcColor[1], Color.green(color) / 255.0f, 0.01f);
            assertEquals(srcColor[2], Color.blue(color) / 255.0f, 0.01f);
        }
    }

    // isFramebufferLinear is true, when GL_FRAMEBUFFER_ATTACHMENT_COLOR_ENCODING is GL_LINEAR.
    // It is false, when GL_FRAMEBUFFER_ATTACHMENT_COLOR_ENCODING is GL_SRGB.
    private void testGetBitmap(int eglColorSpace, ColorSpace colorSpace,
            boolean useHalfFloat, boolean isFramebufferLinear,
            CompareFunction compareFunction) throws Throwable {
        final TextureViewCtsActivity activity = mActivityRule.launchActivity(null);
        activity.waitForSurface();

        try {
            activity.initGl(eglColorSpace, useHalfFloat);
        } catch (RuntimeException e) {
            // failure to init GL with the right colorspace is not a TextureView failure as some
            // devices may not support 16-bits or the colorspace extension
            if (!activity.initGLExtensionUnsupported()) {
                fail("Unable to initGL : " + e);
            }
            return;
        }

        final float[] inputColor = { 1.0f, 128 / 255.0f, 0.0f};

        int updatedCount;
        updatedCount = activity.waitForSurfaceUpdateCount(0);
        assertEquals(0, updatedCount);
        activity.drawColor(inputColor[0], inputColor[1], inputColor[2], 1.0f);
        updatedCount = activity.waitForSurfaceUpdateCount(1);
        assertEquals(1, updatedCount);

        final Bitmap bitmap = activity.getContents(compareFunction.getConfig(),
                compareFunction.getColorSpace());

        // If GL_FRAMEBUFFER_ATTACHMENT_COLOR_ENCODING is GL_SRGB, then glClear will treat the input
        // color as linear and write a converted sRGB color into the framebuffer.
        if (isFramebufferLinear) {
            compareFunction.verify(inputColor, colorSpace, bitmap);
        } else {
            ColorSpace.Connector connector;
            connector = ColorSpace.connect(ColorSpace.get(ColorSpace.Named.LINEAR_SRGB),
                    ColorSpace.get(ColorSpace.Named.SRGB));
            float[] outputColor = connector.transform(inputColor);
            compareFunction.verify(outputColor, colorSpace, bitmap);
        }
    }

    private static void drawGlQuad(int width, int height) {
        int cx = width / 2;
        int cy = height / 2;

        glEnable(GL_SCISSOR_TEST);

        glScissor(0, cy, cx, height - cy);
        clearColor(Color.RED);

        glScissor(cx, cy, width - cx, height - cy);
        clearColor(Color.GREEN);

        glScissor(0, 0, cx, cy);
        clearColor(Color.BLUE);

        glScissor(cx, 0, width - cx, cy);
        clearColor(Color.BLACK);
    }

    private static void drawGlBlackWhiteLines(int width, int height) {
        final int lineHeight = 1;
        glEnable(GL_SCISSOR_TEST);
        for (int y = 0; y < height / lineHeight; y++) {
            glScissor(0, lineHeight * y, width, lineHeight);
            clearColor((y % 2 == 0) ? Color.BLACK : Color.WHITE);
        }
    }

    private static void clearColor(int color) {
        glClearColor(Color.red(color) / 255.0f,
                Color.green(color) / 255.0f,
                Color.blue(color) / 255.0f,
                Color.alpha(color) / 255.0f);
        glClear(GL_COLOR_BUFFER_BIT);
    }

    private int getPixel(Window window, Point point) {
        Bitmap screenshot = Bitmap.createBitmap(window.getDecorView().getWidth(),
                window.getDecorView().getHeight(), Bitmap.Config.ARGB_8888);
        int result = new SynchronousPixelCopy().request(window, screenshot);
        assertEquals("Copy request failed", PixelCopy.SUCCESS, result);
        int pixel = screenshot.getPixel(point.x, point.y);
        screenshot.recycle();
        return pixel;
    }

    private void waitForColor(Window window, Point point, int color)
            throws InterruptedException, TimeoutException {
        for (int i = 0; i < 20; i++) {
            int pixel = getPixel(window, point);
            if (pixel == color) {
                return;
            }
            Thread.sleep(16);
        }
        throw new TimeoutException();
    }

    private int waitForChange(Window window, Point point, int color)
            throws InterruptedException, TimeoutException {
        for (int i = 0; i < 30; i++) {
            int pixel = getPixel(window, point);
            if (pixel != color) {
                return pixel;
            }
            Thread.sleep(16);
        }
        throw new TimeoutException();
    }

    private void assertBitmapQuadColor(Bitmap bitmap,
            int topLeft, int topRight, int bottomLeft, int bottomRight) {
        PixelCopyTest.assertBitmapQuadColor(mTestName.getMethodName(), "TextureViewTest",
                bitmap, topLeft, topRight, bottomLeft, bottomRight);
    }

    private void assertBitmapEdgeColor(Bitmap bitmap, int edgeColor) {
        // Just quickly sample a few pixels on the edge and assert
        // they are edge color, then assert that just inside the edge is a different color
        assertBitmapColor("Top edge", bitmap, edgeColor, bitmap.getWidth() / 2, 0);
        assertBitmapNotColor("Top edge", bitmap, edgeColor, bitmap.getWidth() / 2, 2);

        assertBitmapColor("Left edge", bitmap, edgeColor, 0, bitmap.getHeight() / 2);
        assertBitmapNotColor("Left edge", bitmap, edgeColor, 2, bitmap.getHeight() / 2);

        assertBitmapColor("Bottom edge", bitmap, edgeColor,
                bitmap.getWidth() / 2, bitmap.getHeight() - 1);
        assertBitmapNotColor("Bottom edge", bitmap, edgeColor,
                bitmap.getWidth() / 2, bitmap.getHeight() - 3);

        assertBitmapColor("Right edge", bitmap, edgeColor,
                bitmap.getWidth() - 1, bitmap.getHeight() / 2);
        assertBitmapNotColor("Right edge", bitmap, edgeColor,
                bitmap.getWidth() - 3, bitmap.getHeight() / 2);
    }

    private void failBitmap(Bitmap bitmap, String message) {
        BitmapDumper.dumpBitmap(bitmap, mTestName.getMethodName(), "TextureViewTest");
        Assert.fail(message);
    }

    private void assertBitmapColor(String debug, Bitmap bitmap, int color, int x, int y) {
        int pixel = bitmap.getPixel(x, y);
        if (!pixelsAreSame(color, pixel, 10)) {
            failBitmap(bitmap, debug + "; expected=" + Integer.toHexString(color) + ", actual="
                    + Integer.toHexString(pixel));
        }
    }

    private void assertBitmapNotColor(String debug, Bitmap bitmap, int color, int x, int y) {
        int pixel = bitmap.getPixel(x, y);
        if (pixelsAreSame(color, pixel, 10)) {
            failBitmap(bitmap, debug + "; actual=" + Integer.toHexString(pixel)
                    + " shouldn't have matched " + Integer.toHexString(color));
        }
    }
}
