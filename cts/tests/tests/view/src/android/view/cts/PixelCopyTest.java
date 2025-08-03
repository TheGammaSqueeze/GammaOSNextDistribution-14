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

import static com.android.compatibility.common.util.SynchronousPixelCopy.copySurface;
import static com.android.compatibility.common.util.SynchronousPixelCopy.copyWindow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.os.Debug;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.server.wm.SetRequestedOrientationRule;
import android.util.Half;
import android.util.Log;
import android.util.Pair;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.cts.util.BitmapDumper;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.SynchronousPixelCopy;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@MediumTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class PixelCopyTest {
    private static final String TAG = "PixelCopyTests";

    @ClassRule
    public static SetRequestedOrientationRule mSetRequestedOrientationRule =
            new SetRequestedOrientationRule();

    @Rule(order = 0)
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            androidx.test.platform.app.InstrumentationRegistry
                    .getInstrumentation().getUiAutomation(),
            Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);

    @Rule(order = 1)
    public ActivityTestRule<PixelCopyGLProducerCtsActivity> mGLSurfaceViewActivityRule =
            new ActivityTestRule<>(PixelCopyGLProducerCtsActivity.class, false, false);

    @Rule(order = 1)
    public ActivityTestRule<PixelCopyVideoSourceActivity> mVideoSourceActivityRule =
            new ActivityTestRule<>(PixelCopyVideoSourceActivity.class, false, false);

    public static class FullscreenActivityRule extends ExternalResource {
        private final ArrayList<Activity> mActivities = new ArrayList<>();

        public <T extends Activity> T launch(Class<T> klass) {
            final Pair<Intent, ActivityOptions> args =
                    SetRequestedOrientationRule.buildFullScreenLaunchArgs(klass);
            final T activity = (T) InstrumentationRegistry.getInstrumentation()
                    .startActivitySync(args.first, args.second.toBundle());
            mActivities.add(activity);
            return activity;
        }

        @Override
        protected void after() {
            if (mActivities.isEmpty()) return;
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (final Activity activity : mActivities) {
                    activity.finish();
                }
            });
        }
    }

    @Rule
    public FullscreenActivityRule mFullscreenActivityRule = new FullscreenActivityRule();

    @Rule
    public SurfaceTextureRule mSurfaceRule = new SurfaceTextureRule();

    @Rule
    public TestName mTestName = new TestName();

    private Instrumentation mInstrumentation;
    private SynchronousPixelCopy mCopyHelper;

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        assertNotNull(mInstrumentation);
        mCopyHelper = new SynchronousPixelCopy();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullDest() {
        Bitmap dest = null;
        mCopyHelper.request(mSurfaceRule.getSurface(), dest);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRecycledDest() {
        Bitmap dest = Bitmap.createBitmap(5, 5, Config.ARGB_8888);
        dest.recycle();
        mCopyHelper.request(mSurfaceRule.getSurface(), dest);
    }

    @Test
    public void testNoSourceData() {
        Bitmap dest = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888);
        int result = mCopyHelper.request(mSurfaceRule.getSurface(), dest);
        assertEquals(PixelCopy.ERROR_SOURCE_NO_DATA, result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptySourceRectSurface() {
        Bitmap dest = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888);
        mCopyHelper.request(mSurfaceRule.getSurface(), new Rect(), dest);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptySourceRectWindow() {
        Bitmap dest = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888);
        mCopyHelper.request(mock(Window.class), new Rect(), dest);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidSourceRectSurface() {
        Bitmap dest = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888);
        mCopyHelper.request(mSurfaceRule.getSurface(), new Rect(10, 10, 0, 0), dest);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidSourceRectWindow() {
        Bitmap dest = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888);
        mCopyHelper.request(mock(Window.class), new Rect(10, 10, 0, 0), dest);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoDecorView() {
        Bitmap dest = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888);
        Window mockWindow = mock(Window.class);
        mCopyHelper.request(mockWindow, dest);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoViewRoot() {
        Bitmap dest = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888);
        Window mockWindow = mock(Window.class);
        View view = new View(mInstrumentation.getTargetContext());
        when(mockWindow.peekDecorView()).thenReturn(view);
        mCopyHelper.request(mockWindow, dest);
    }

    @Test
    public void testRequestGetters() {
        PixelCopyViewProducerActivity activity = waitForWindowProducerActivity();
        Bitmap dest = Bitmap.createBitmap(5, 5, Config.ARGB_8888);
        Rect source = new Rect(3, 3, 40, 50);
        PixelCopy.Request request = PixelCopy.Request.Builder.ofWindow(activity.getWindow())
                .setSourceRect(source)
                .setDestinationBitmap(dest)
                .build();
        assertEquals(dest, request.getDestinationBitmap());
        assertEquals(source, request.getSourceRect());
    }

    private PixelCopyGLProducerCtsActivity waitForGlProducerActivity() {
        PixelCopyGLProducerCtsActivity activity =
                mGLSurfaceViewActivityRule.launchActivity(null);
        activity.waitForReady();
        return activity;
    }

    @Test
    public void testGlProducerFullsize() {
        PixelCopyGLProducerCtsActivity activity = waitForGlProducerActivity();
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        int result = mCopyHelper.request(activity.getView(), bitmap);
        assertEquals("Fullsize copy request failed", PixelCopy.SUCCESS, result);
        assertEquals(100, bitmap.getWidth());
        assertEquals(100, bitmap.getHeight());
        assertEquals(Config.ARGB_8888, bitmap.getConfig());
        assertBitmapQuadColor(bitmap,
                Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
    }

    @Test
    public void testGlProducerAutoSize() {
        PixelCopyGLProducerCtsActivity activity = waitForGlProducerActivity();
        PixelCopy.Result result = copySurface(activity.getView());
        assertEquals("Fullsize copy request failed", PixelCopy.SUCCESS, result.getStatus());
        Bitmap bitmap = result.getBitmap();
        assertEquals(100, bitmap.getWidth());
        assertEquals(100, bitmap.getHeight());
        assertEquals(Config.ARGB_8888, bitmap.getConfig());
        assertBitmapQuadColor(bitmap,
                Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
    }

    @Test
    public void testGlProducerCropTopLeft() {
        PixelCopyGLProducerCtsActivity activity = waitForGlProducerActivity();
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        int result = mCopyHelper.request(activity.getView(), new Rect(0, 0, 50, 50), bitmap);
        assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, result);
        assertBitmapQuadColor(bitmap,
                Color.RED, Color.RED, Color.RED, Color.RED);
    }

    @Test
    public void testGlProducerCropCenter() {
        PixelCopyGLProducerCtsActivity activity = waitForGlProducerActivity();
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        int result = mCopyHelper.request(activity.getView(), new Rect(25, 25, 75, 75), bitmap);
        assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, result);
        assertBitmapQuadColor(bitmap,
                Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
    }

    @Test
    public void testGlProducerCropBottomHalf() {
        PixelCopyGLProducerCtsActivity activity = waitForGlProducerActivity();
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        int result = mCopyHelper.request(activity.getView(), new Rect(0, 50, 100, 100), bitmap);
        assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, result);
        assertBitmapQuadColor(bitmap,
                Color.BLUE, Color.BLACK, Color.BLUE, Color.BLACK);
    }

    @Test
    public void testGlProducerCropClamping() {
        PixelCopyGLProducerCtsActivity activity = waitForGlProducerActivity();
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        int result = mCopyHelper.request(activity.getView(), new Rect(50, -50, 150, 50), bitmap);
        assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, result);
        assertBitmapQuadColor(bitmap,
                Color.GREEN, Color.GREEN, Color.GREEN, Color.GREEN);
    }

    @Test
    public void testGlProducerScaling() {
        // Since we only sample mid-pixel of each qudrant, filtering
        // quality isn't tested
        PixelCopyGLProducerCtsActivity activity = waitForGlProducerActivity();
        Bitmap bitmap = Bitmap.createBitmap(20, 20, Config.ARGB_8888);
        int result = mCopyHelper.request(activity.getView(), bitmap);
        assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, result);
        // Make sure nothing messed with the bitmap
        assertEquals(20, bitmap.getWidth());
        assertEquals(20, bitmap.getHeight());
        assertEquals(Config.ARGB_8888, bitmap.getConfig());
        assertBitmapQuadColor(bitmap,
                Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
    }

    @Test
    public void testReuseBitmap() {
        // Since we only sample mid-pixel of each qudrant, filtering
        // quality isn't tested
        PixelCopyGLProducerCtsActivity activity = waitForGlProducerActivity();
        Bitmap bitmap = Bitmap.createBitmap(20, 20, Config.ARGB_8888);
        int result = mCopyHelper.request(activity.getView(), bitmap);
        assertEquals(result, PixelCopy.SUCCESS);
        // Make sure nothing messed with the bitmap
        assertEquals(20, bitmap.getWidth());
        assertEquals(20, bitmap.getHeight());
        assertEquals(Config.ARGB_8888, bitmap.getConfig());
        assertBitmapQuadColor(bitmap,
                Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
        bitmap.eraseColor(Color.MAGENTA);
        int generationId = bitmap.getGenerationId();
        result = mCopyHelper.request(activity.getView(), bitmap);
        assertEquals(result, PixelCopy.SUCCESS);
        // Make sure nothing messed with the bitmap
        assertEquals(20, bitmap.getWidth());
        assertEquals(20, bitmap.getHeight());
        assertEquals(Config.ARGB_8888, bitmap.getConfig());
        assertBitmapQuadColor(bitmap,
                Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
        assertNotEquals(generationId, bitmap.getGenerationId());
    }

    private PixelCopyViewProducerActivity waitForWindowProducerActivity() {
        PixelCopyViewProducerActivity activity = mFullscreenActivityRule.launch(
                        PixelCopyViewProducerActivity.class);
        activity.waitForFirstDrawCompleted(10, TimeUnit.SECONDS);
        return activity;
    }

    private Rect makeWindowRect(
            PixelCopyViewProducerActivity activity, int left, int top, int right, int bottom) {
        Rect r = new Rect(left, top, right, bottom);
        activity.normalizedToSurface(r);
        return r;
    }

    @Test
    public void testViewProducer() {
        PixelCopyViewProducerActivity activity = waitForWindowProducerActivity();
        do {
            final Rect src = makeWindowRect(activity, 0, 0, 100, 100);
            final Bitmap bitmap = Bitmap.createBitmap(src.width(), src.height(),
                    Config.ARGB_8888);
            int result = copyWindow(activity.getContentView(), request -> {
                request.setDestinationBitmap(bitmap);
                request.setSourceRect(src);
            }).getStatus();
            assertEquals("Fullsize copy request failed", PixelCopy.SUCCESS, result);
            assertEquals(Config.ARGB_8888, bitmap.getConfig());
            assertBitmapQuadColor(bitmap,
                    Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
            assertBitmapEdgeColor(bitmap, Color.YELLOW);
        } while (activity.rotate());
    }

    @Test
    public void testWindowProducerAutoSize() {
        PixelCopyViewProducerActivity activity = waitForWindowProducerActivity();
        Window window = activity.getWindow();
        do {
            PixelCopy.Result result = copyWindow(window);
            assertEquals("Fullsize copy request failed", PixelCopy.SUCCESS,
                    result.getStatus());
            final Bitmap bitmap = result.getBitmap();
            assertEquals(Config.ARGB_8888, bitmap.getConfig());
            final View decorView = window.getDecorView();
            assertTrue(bitmap.getWidth() >= decorView.getWidth());
            assertTrue(bitmap.getHeight() >= decorView.getHeight());
            // We can't directly assert qualities of the bitmap because the View's location
            // is going to be affected by padding/insets.
        } while (activity.rotate());
    }

    @Test
    public void testViewProducerAutoSizeWithSrc() {
        PixelCopyViewProducerActivity activity = waitForWindowProducerActivity();
        do {
            final Rect src = makeWindowRect(activity, 0, 0, 100, 100);
            PixelCopy.Result result = copyWindow(activity.getContentView(), request -> {
                request.setSourceRect(src);
            });
            assertEquals("Fullsize copy request failed", PixelCopy.SUCCESS, result.getStatus());
            final Bitmap bitmap = result.getBitmap();
            assertEquals(Config.ARGB_8888, bitmap.getConfig());
            assertEquals(src.width(), bitmap.getWidth());
            assertEquals(src.height(), bitmap.getHeight());
            assertBitmapQuadColor(bitmap,
                    Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
            assertBitmapEdgeColor(bitmap, Color.YELLOW);
        } while (activity.rotate());
    }

    @Test
    public void testWindowProducerCropTopLeft() {
        PixelCopyViewProducerActivity activity = waitForWindowProducerActivity();
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        do {
            int result = mCopyHelper.request(
                    activity.getWindow(), makeWindowRect(activity, 0, 0, 50, 50), bitmap);
            assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, result);
            assertBitmapQuadColor(bitmap,
                    Color.RED, Color.RED, Color.RED, Color.RED);
        } while (activity.rotate());
    }

    @Test
    public void testWindowProducerCropCenter() {
        PixelCopyViewProducerActivity activity = waitForWindowProducerActivity();
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        do {
            int result = mCopyHelper.request(
                    activity.getWindow(), makeWindowRect(activity, 25, 25, 75, 75), bitmap);
            assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, result);
            assertBitmapQuadColor(bitmap,
                    Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
        } while (activity.rotate());
    }

    @Test
    public void testWindowProducerCropBottomHalf() {
        PixelCopyViewProducerActivity activity = waitForWindowProducerActivity();
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        do {
            int result = mCopyHelper.request(
                    activity.getWindow(), makeWindowRect(activity, 0, 50, 100, 100), bitmap);
            assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, result);
            assertBitmapQuadColor(bitmap,
                    Color.BLUE, Color.BLACK, Color.BLUE, Color.BLACK);
        } while (activity.rotate());
    }

    @Test
    public void testWindowProducerScaling() {
        // Since we only sample mid-pixel of each qudrant, filtering
        // quality isn't tested
        PixelCopyViewProducerActivity activity = waitForWindowProducerActivity();
        Bitmap bitmap = Bitmap.createBitmap(20, 20, Config.ARGB_8888);
        do {
            int result = mCopyHelper.request(activity.getWindow(), bitmap);
            assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, result);
            // Make sure nothing messed with the bitmap
            assertEquals(20, bitmap.getWidth());
            assertEquals(20, bitmap.getHeight());
            assertEquals(Config.ARGB_8888, bitmap.getConfig());
            assertBitmapQuadColor(bitmap,
                    Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
        } while (activity.rotate());
    }

    @Test
    public void testWindowProducerCopyToRGBA16F() {
        PixelCopyViewProducerActivity activity = waitForWindowProducerActivity();
        do {
            Rect src = makeWindowRect(activity, 0, 0, 100, 100);
            Bitmap bitmap = Bitmap.createBitmap(src.width(), src.height(), Config.RGBA_F16);
            int result = mCopyHelper.request(activity.getWindow(), src, bitmap);
            // On OpenGL ES 2.0 devices a copy to RGBA_F16 can fail because there's
            // not support for float textures
            if (result != PixelCopy.ERROR_DESTINATION_INVALID) {
                assertEquals("Fullsize copy request failed", PixelCopy.SUCCESS, result);
                assertEquals(Config.RGBA_F16, bitmap.getConfig());
                assertBitmapQuadColor(bitmap,
                        Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
                assertBitmapEdgeColor(bitmap, Color.YELLOW);
            }
        } while (activity.rotate());
    }

    @Test
    public void testWindowProducer() {
        Bitmap bitmap;
        PixelCopyViewProducerActivity activity = waitForWindowProducerActivity();
        Window window = activity.getWindow();
        do {
            Rect src = makeWindowRect(activity, 0, 0, 100, 100);
            bitmap = Bitmap.createBitmap(src.width(), src.height(), Config.ARGB_8888);
            int result = mCopyHelper.request(window, src, bitmap);
            assertEquals("Fullsize copy request failed", PixelCopy.SUCCESS, result);
            assertEquals(Config.ARGB_8888, bitmap.getConfig());
            assertBitmapQuadColor(bitmap,
                    Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
            assertBitmapEdgeColor(bitmap, Color.YELLOW);
        } while (activity.rotate());
    }

    private PixelCopyWideGamutViewProducerActivity waitForWideGamutWindowProducerActivity() {
        PixelCopyWideGamutViewProducerActivity activity = mFullscreenActivityRule.launch(
                        PixelCopyWideGamutViewProducerActivity.class);
        activity.waitForFirstDrawCompleted(10, TimeUnit.SECONDS);
        return activity;
    }

    private Rect makeWideGamutWindowRect(
            PixelCopyWideGamutViewProducerActivity activity,
            int left, int top, int right, int bottom) {
        Rect r = new Rect(left, top, right, bottom);
        activity.offsetForContent(r);
        return r;
    }

    @Test
    public void testWideGamutWindowProducerCopyToRGBA8888() {
        PixelCopyWideGamutViewProducerActivity activity = waitForWideGamutWindowProducerActivity();
        assertEquals(
                ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT,
                activity.getWindow().getAttributes().getColorMode()
        );

        // Early out if the device does not support wide color gamut rendering
        if (!activity.getWindow().isWideColorGamut()) {
            return;
        }

        do {
            Rect src = makeWideGamutWindowRect(activity, 0, 0, 128, 128);
            Bitmap bitmap = Bitmap.createBitmap(src.width(), src.height(), Config.ARGB_8888);
            int result = mCopyHelper.request(activity.getWindow(), src, bitmap);

            assertEquals("Fullsize copy request failed", PixelCopy.SUCCESS, result);
            assertEquals(Config.ARGB_8888, bitmap.getConfig());

            assertEquals("Top left", Color.RED, bitmap.getPixel(32, 32));
            assertEquals("Top right", Color.GREEN, bitmap.getPixel(96, 32));
            assertEquals("Bottom left", Color.BLUE, bitmap.getPixel(32, 96));
            assertEquals("Bottom right", Color.YELLOW, bitmap.getPixel(96, 96));
        } while (activity.rotate());
    }

    @Test
    public void testWideGamutWindowProducerCopyToRGBA16F() {
        PixelCopyWideGamutViewProducerActivity activity = waitForWideGamutWindowProducerActivity();
        assertEquals(
                ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT,
                activity.getWindow().getAttributes().getColorMode()
        );

        // Early out if the device does not support wide color gamut rendering
        if (!activity.getWindow().isWideColorGamut()) {
            return;
        }

        final WindowManager windowManager = (WindowManager) activity.getSystemService(
                Context.WINDOW_SERVICE);
        final ColorSpace colorSpace = windowManager.getDefaultDisplay()
                .getPreferredWideGamutColorSpace();
        final ColorSpace.Connector proPhotoToDisplayWideColorSpace = ColorSpace.connect(
                ColorSpace.get(ColorSpace.Named.PRO_PHOTO_RGB), colorSpace);
        final ColorSpace.Connector displayWideColorSpaceToExtendedSrgb = ColorSpace.connect(
                colorSpace, ColorSpace.get(ColorSpace.Named.EXTENDED_SRGB));

        final float[] intermediateRed =
                proPhotoToDisplayWideColorSpace.transform(1.0f, 0.0f, 0.0f);
        final float[] intermediateGreen = proPhotoToDisplayWideColorSpace
                .transform(0.0f, 1.0f, 0.0f);
        final float[] intermediateBlue = proPhotoToDisplayWideColorSpace
                .transform(0.0f, 0.0f, 1.0f);
        final float[] intermediateYellow = proPhotoToDisplayWideColorSpace
                .transform(1.0f, 1.0f, 0.0f);

        final float[] expectedRed =
                displayWideColorSpaceToExtendedSrgb.transform(intermediateRed);
        final float[] expectedGreen = displayWideColorSpaceToExtendedSrgb
                .transform(intermediateGreen);
        final float[] expectedBlue = displayWideColorSpaceToExtendedSrgb
                .transform(intermediateBlue);
        final float[] expectedYellow = displayWideColorSpaceToExtendedSrgb
                .transform(intermediateYellow);

        do {
            Rect src = makeWideGamutWindowRect(activity, 0, 0, 128, 128);
            Bitmap bitmap = Bitmap.createBitmap(src.width(), src.height(), Config.RGBA_F16,
                    true, ColorSpace.get(ColorSpace.Named.EXTENDED_SRGB));
            int result = mCopyHelper.request(activity.getWindow(), src, bitmap);

            assertEquals("Fullsize copy request failed", PixelCopy.SUCCESS, result);
            assertEquals(Config.RGBA_F16, bitmap.getConfig());

            ByteBuffer dst = ByteBuffer.allocateDirect(bitmap.getAllocationByteCount());
            bitmap.copyPixelsToBuffer(dst);
            dst.rewind();
            dst.order(ByteOrder.LITTLE_ENDIAN);

            // ProPhoto RGB red in scRGB-nl
            assertEqualsRgba16f("Top left", bitmap, 32, 32, dst, expectedRed[0],
                    expectedRed[1], expectedRed[2], 1.0f);
            // ProPhoto RGB green in scRGB-nl
            assertEqualsRgba16f("Top right", bitmap, 96, 32, dst,
                    expectedGreen[0], expectedGreen[1], expectedGreen[2], 1.0f);
            // ProPhoto RGB blue in scRGB-nl
            assertEqualsRgba16f("Bottom left",  bitmap, 32, 96, dst,
                    expectedBlue[0], expectedBlue[1], expectedBlue[2], 1.0f);
            // ProPhoto RGB yellow in scRGB-nl
            assertEqualsRgba16f("Bottom right", bitmap, 96, 96, dst,
                    expectedYellow[0], expectedYellow[1], expectedYellow[2], 1.0f);
        } while (activity.rotate());
    }

    private PixelCopyViewProducerDialogActivity waitForDialogProducerActivity() {
        PixelCopyViewProducerDialogActivity activity = mFullscreenActivityRule.launch(
                PixelCopyViewProducerDialogActivity.class);
        activity.waitForFirstDrawCompleted(10, TimeUnit.SECONDS);
        return activity;
    }

    private Rect makeDialogRect(
            PixelCopyViewProducerDialogActivity activity,
            int left, int top, int right, int bottom) {
        Rect r = new Rect(left, top, right, bottom);
        activity.normalizedToSurface(r);
        return r;
    }

    @Test
    public void testDialogProducer() {
        PixelCopyViewProducerDialogActivity activity = waitForDialogProducerActivity();
        do {
            Rect src = makeDialogRect(activity, 0, 0, 100, 100);
            Bitmap bitmap = Bitmap.createBitmap(src.width(), src.height(), Config.ARGB_8888);
            int result = mCopyHelper.request(activity.getWindow(), src, bitmap);
            assertEquals("Fullsize copy request failed", PixelCopy.SUCCESS, result);
            assertEquals(Config.ARGB_8888, bitmap.getConfig());
            assertBitmapQuadColor(bitmap,
                    Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
            assertBitmapEdgeColor(bitmap, Color.YELLOW);
        } while (activity.rotate());
    }

    @Test
    public void testDialogProducerCropTopLeft() {
        PixelCopyViewProducerDialogActivity activity = waitForDialogProducerActivity();
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        do {
            int result = mCopyHelper.request(
                    activity.getWindow(), makeDialogRect(activity, 0, 0, 50, 50), bitmap);
            assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, result);
            assertBitmapQuadColor(bitmap,
                    Color.RED, Color.RED, Color.RED, Color.RED);
        } while (activity.rotate());
    }

    @Test
    public void testDialogProducerCropCenter() {
        PixelCopyViewProducerDialogActivity activity = waitForDialogProducerActivity();
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        do {
            int result = mCopyHelper.request(
                    activity.getWindow(), makeDialogRect(activity, 25, 25, 75, 75), bitmap);
            assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, result);
            assertBitmapQuadColor(bitmap,
                    Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
        } while (activity.rotate());
    }

    @Test
    public void testDialogProducerCropBottomHalf() {
        PixelCopyViewProducerDialogActivity activity = waitForDialogProducerActivity();
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        do {
            int result = mCopyHelper.request(
                    activity.getWindow(), makeDialogRect(activity, 0, 50, 100, 100), bitmap);
            assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, result);
            assertBitmapQuadColor(bitmap,
                    Color.BLUE, Color.BLACK, Color.BLUE, Color.BLACK);
        } while (activity.rotate());
    }

    @Test
    public void testDialogProducerScaling() {
        // Since we only sample mid-pixel of each qudrant, filtering
        // quality isn't tested
        PixelCopyViewProducerDialogActivity activity = waitForDialogProducerActivity();
        Bitmap bitmap = Bitmap.createBitmap(20, 20, Config.ARGB_8888);
        do {
            int result = mCopyHelper.request(activity.getWindow(), bitmap);
            assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, result);
            // Make sure nothing messed with the bitmap
            assertEquals(20, bitmap.getWidth());
            assertEquals(20, bitmap.getHeight());
            assertEquals(Config.ARGB_8888, bitmap.getConfig());
            assertBitmapQuadColor(bitmap,
                    Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
        } while (activity.rotate());
    }

    @Test
    public void testDialogProducerCopyToRGBA16F() {
        PixelCopyViewProducerDialogActivity activity = waitForDialogProducerActivity();
        do {
            Rect src = makeDialogRect(activity, 0, 0, 100, 100);
            Bitmap bitmap = Bitmap.createBitmap(src.width(), src.height(), Config.RGBA_F16);
            int result = mCopyHelper.request(activity.getWindow(), src, bitmap);
            // On OpenGL ES 2.0 devices a copy to RGBA_F16 can fail because there's
            // not support for float textures
            if (result != PixelCopy.ERROR_DESTINATION_INVALID) {
                assertEquals("Fullsize copy request failed", PixelCopy.SUCCESS, result);
                assertEquals(Config.RGBA_F16, bitmap.getConfig());
                assertBitmapQuadColor(bitmap,
                        Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
                assertBitmapEdgeColor(bitmap, Color.YELLOW);
            }
        } while (activity.rotate());
    }

    private static void assertEqualsRgba16f(String message, Bitmap bitmap, int x, int y,
            ByteBuffer dst, float r, float g, float b, float a) {
        int index = y * bitmap.getRowBytes() + (x << 3);
        short cR = dst.getShort(index);
        short cG = dst.getShort(index + 2);
        short cB = dst.getShort(index + 4);
        short cA = dst.getShort(index + 6);

        assertEquals(message, r, Half.toFloat(cR), 0.01);
        assertEquals(message, g, Half.toFloat(cG), 0.01);
        assertEquals(message, b, Half.toFloat(cB), 0.01);
        assertEquals(message, a, Half.toFloat(cA), 0.01);
    }

    private static void runGcAndFinalizersSync() {
        Runtime.getRuntime().gc();
        Runtime.getRuntime().runFinalization();

        final CountDownLatch fence = new CountDownLatch(1);
        new Object() {
            @Override
            protected void finalize() throws Throwable {
                try {
                    fence.countDown();
                } finally {
                    super.finalize();
                }
            }
        };
        try {
            do {
                Runtime.getRuntime().gc();
                Runtime.getRuntime().runFinalization();
            } while (!fence.await(100, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static File sProcSelfFd = new File("/proc/self/fd");
    private static int getFdCount() {
        return sProcSelfFd.listFiles().length;
    }

    private static void assertNotLeaking(int iteration,
            Debug.MemoryInfo start, Debug.MemoryInfo end) {
        Debug.getMemoryInfo(end);
        assertNotEquals(0, start.getTotalPss());
        assertNotEquals(0, end.getTotalPss());
        if (end.getTotalPss() - start.getTotalPss() > 5000 /* kB */) {
            runGcAndFinalizersSync();
            Debug.getMemoryInfo(end);
            if (end.getTotalPss() - start.getTotalPss() > 7000 /* kB */) {
                // Guarded by if so we don't continually generate garbage for the
                // assertion string.
                assertEquals("Memory leaked, iteration=" + iteration,
                        start.getTotalPss(), end.getTotalPss(),
                        7000 /* kb */);
            }
        }
    }

    private static void runNotLeakingTest(Runnable test) {
        Debug.MemoryInfo meminfoStart = new Debug.MemoryInfo();
        Debug.MemoryInfo meminfoEnd = new Debug.MemoryInfo();
        int fdCount = -1;
        // Do a warmup to reach steady-state memory usage
        for (int i = 0; i < 50; i++) {
            test.run();
        }
        runGcAndFinalizersSync();
        Debug.getMemoryInfo(meminfoStart);
        fdCount = getFdCount();
        // Now run the test
        for (int i = 0; i < 2000; i++) {
            if (i % 100 == 5) {
                assertNotLeaking(i, meminfoStart, meminfoEnd);
                final int curFdCount = getFdCount();
                if (curFdCount - fdCount > 10) {
                    Assert.fail(String.format("FDs leaked. Expected=%d, current=%d, iteration=%d",
                            fdCount, curFdCount, i));
                }
            }
            test.run();
        }
        assertNotLeaking(2000, meminfoStart, meminfoEnd);
        final int curFdCount = getFdCount();
        if (curFdCount - fdCount > 10) {
            Assert.fail(String.format("FDs leaked. Expected=%d, current=%d", fdCount, curFdCount));
        }
    }

    @Test
    @LargeTest
    public void testNotLeaking() {
        PixelCopyGLProducerCtsActivity activity =
                mGLSurfaceViewActivityRule.launchActivity(null);
        activity.waitForReady();

        // Test a fullsize copy
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);

        runNotLeakingTest(() -> {
            int result = mCopyHelper.request(activity.getView(), bitmap);
            assertEquals("Copy request failed", PixelCopy.SUCCESS, result);
            // Make sure nothing messed with the bitmap
            assertEquals(100, bitmap.getWidth());
            assertEquals(100, bitmap.getHeight());
            assertEquals(Config.ARGB_8888, bitmap.getConfig());
            assertBitmapQuadColor(bitmap,
                    Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
        });
    }

    @Test
    public void testVideoProducer() throws InterruptedException {
        PixelCopyVideoSourceActivity activity =
                mVideoSourceActivityRule.launchActivity(null);

        Thread.sleep(2000);

        if (!activity.canPlayVideo()) {
            Log.i(TAG, "Skipping testVideoProducer, video codec isn't supported");
            return;
        }
        // This returns when the video has been prepared and playback has
        // been started, it doesn't necessarily means a frame has actually been
        // produced. There sadly isn't a callback for that.
        // So we'll try for up to 900ms after this event to acquire a frame, otherwise
        // it's considered a timeout.
        activity.waitForPlaying();
        assertTrue("Failed to start video playback", activity.canPlayVideo());
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        int copyResult = PixelCopy.ERROR_SOURCE_NO_DATA;
        for (int i = 0; i < 30; i++) {
            copyResult = mCopyHelper.request(activity.getVideoView(), bitmap);
            if (copyResult != PixelCopy.ERROR_SOURCE_NO_DATA) {
                break;
            }
            Thread.sleep(30);
        }
        assertEquals(PixelCopy.SUCCESS, copyResult);
        // A large threshold is used because decoder accuracy is covered in the
        // media CTS tests, so we are mainly interested in verifying that rotation
        // and YUV->RGB conversion were handled properly.
        assertBitmapQuadColor(bitmap, Color.RED, Color.GREEN, Color.BLUE, Color.BLACK, 30);

        // Test that cropping works.
        copyResult = mCopyHelper.request(activity.getVideoView(), new Rect(0, 0, 50, 50), bitmap);
        assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, copyResult);
        assertBitmapQuadColor(bitmap,
                Color.RED, Color.RED, Color.RED, Color.RED, 30);

        copyResult = mCopyHelper.request(activity.getVideoView(), new Rect(50, 0, 100, 50), bitmap);
        assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, copyResult);
        assertBitmapQuadColor(bitmap,
                Color.GREEN, Color.GREEN, Color.GREEN, Color.GREEN, 30);

        copyResult = mCopyHelper.request(activity.getVideoView(), new Rect(0, 50, 50, 100), bitmap);
        assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, copyResult);
        assertBitmapQuadColor(bitmap,
                Color.BLUE, Color.BLUE, Color.BLUE, Color.BLUE, 30);

        copyResult = mCopyHelper.request(activity.getVideoView(), new Rect(50, 50, 100, 100), bitmap);
        assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, copyResult);
        assertBitmapQuadColor(bitmap,
                Color.BLACK, Color.BLACK, Color.BLACK, Color.BLACK, 30);


        copyResult = mCopyHelper.request(activity.getVideoView(), new Rect(25, 25, 75, 75), bitmap);
        assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, copyResult);
        assertBitmapQuadColor(bitmap,
                Color.RED, Color.GREEN, Color.BLUE, Color.BLACK, 30);

        copyResult = mCopyHelper.request(activity.getVideoView(), new Rect(0, 50, 100, 100), bitmap);
        assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, copyResult);
        assertBitmapQuadColor(bitmap,
                Color.BLUE, Color.BLACK, Color.BLUE, Color.BLACK, 30);

        // Test that clamping works
        copyResult = mCopyHelper.request(activity.getVideoView(), new Rect(50, -50, 150, 50), bitmap);
        assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, copyResult);
        assertBitmapQuadColor(bitmap,
                Color.GREEN, Color.GREEN, Color.GREEN, Color.GREEN, 30);
    }

    @Test
    public void testBufferQueueCrop() throws InterruptedException {
        ImageReader reader = ImageReader.newInstance(100, 100, PixelFormat.RGBA_8888, 1,
                HardwareBuffer.USAGE_CPU_WRITE_OFTEN | HardwareBuffer.USAGE_CPU_READ_OFTEN
                        | HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
        ImageWriter writer = ImageWriter.newInstance(reader.getSurface(), 1);
        Image image = writer.dequeueInputImage();
        Image.Plane plane = image.getPlanes()[0];
        Bitmap bitmap = Bitmap.createBitmap(plane.getRowStride() / 4,
                image.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(false);
        canvas.drawColor(Color.MAGENTA);
        canvas.translate(20f, 70f);
        paint.setColor(Color.RED);
        canvas.drawRect(0f, 0f, 10f, 10f, paint);
        paint.setColor(Color.GREEN);
        canvas.drawRect(10f, 0f, 20f, 10f, paint);
        paint.setColor(Color.BLUE);
        canvas.drawRect(0f, 10f, 10f, 20f, paint);
        paint.setColor(Color.BLACK);
        canvas.drawRect(10f, 10f, 20f, 20f, paint);
        bitmap.copyPixelsToBuffer(plane.getBuffer());
        image.setCropRect(new Rect(20, 70, 40, 90));
        writer.queueInputImage(image);

        // implicit size
        Bitmap result = Bitmap.createBitmap(20, 20, Config.ARGB_8888);
        int status = mCopyHelper.request(reader.getSurface(), result);
        assertEquals("Copy request failed", PixelCopy.SUCCESS, status);
        assertBitmapQuadColor(result, Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);

        // specified size
        result = Bitmap.createBitmap(20, 20, Config.ARGB_8888);
        status = mCopyHelper.request(reader.getSurface(), new Rect(0, 0, 20, 20), result);
        assertEquals("Copy request failed", PixelCopy.SUCCESS, status);
        assertBitmapQuadColor(result, Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
    }

    @Test
    public void testAutoSize() throws InterruptedException {
        ImageReader reader = ImageReader.newInstance(100, 100, PixelFormat.RGBA_8888, 1,
                HardwareBuffer.USAGE_CPU_WRITE_OFTEN | HardwareBuffer.USAGE_CPU_READ_OFTEN
                        | HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
        ImageWriter writer = ImageWriter.newInstance(reader.getSurface(), 1);
        Image image = writer.dequeueInputImage();
        Image.Plane plane = image.getPlanes()[0];
        Bitmap bitmap = Bitmap.createBitmap(plane.getRowStride() / 4,
                image.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(false);
        paint.setColor(Color.RED);
        canvas.drawRect(0f, 0f, 50f, 50f, paint);
        paint.setColor(Color.GREEN);
        canvas.drawRect(50f, 0f, 100f, 50f, paint);
        paint.setColor(Color.BLUE);
        canvas.drawRect(0f, 50f, 50f, 100f, paint);
        paint.setColor(Color.BLACK);
        canvas.drawRect(50f, 50f, 100f, 100f, paint);
        bitmap.copyPixelsToBuffer(plane.getBuffer());
        writer.queueInputImage(image);

        PixelCopy.Result copyResult = copySurface(reader.getSurface());
        assertEquals("Copy request failed", PixelCopy.SUCCESS, copyResult.getStatus());
        Bitmap result = copyResult.getBitmap();
        assertEquals(100, result.getWidth());
        assertEquals(100, result.getHeight());
        assertBitmapQuadColor(result, Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
    }

    @Test
    public void testAutoSizeWithCrop() throws InterruptedException {
        ImageReader reader = ImageReader.newInstance(100, 100, PixelFormat.RGBA_8888, 1,
                HardwareBuffer.USAGE_CPU_WRITE_OFTEN | HardwareBuffer.USAGE_CPU_READ_OFTEN
                        | HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
        ImageWriter writer = ImageWriter.newInstance(reader.getSurface(), 1);
        Image image = writer.dequeueInputImage();
        Image.Plane plane = image.getPlanes()[0];
        Bitmap bitmap = Bitmap.createBitmap(plane.getRowStride() / 4,
                image.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(false);
        canvas.drawColor(Color.MAGENTA);
        canvas.translate(20f, 70f);
        paint.setColor(Color.RED);
        canvas.drawRect(0f, 0f, 10f, 10f, paint);
        paint.setColor(Color.GREEN);
        canvas.drawRect(10f, 0f, 20f, 10f, paint);
        paint.setColor(Color.BLUE);
        canvas.drawRect(0f, 10f, 10f, 20f, paint);
        paint.setColor(Color.BLACK);
        canvas.drawRect(10f, 10f, 20f, 20f, paint);
        bitmap.copyPixelsToBuffer(plane.getBuffer());
        image.setCropRect(new Rect(20, 70, 40, 90));
        writer.queueInputImage(image);

        PixelCopy.Result copyResult = copySurface(reader.getSurface());
        assertEquals("Copy request failed", PixelCopy.SUCCESS, copyResult.getStatus());
        Bitmap result = copyResult.getBitmap();
        assertEquals(20, result.getWidth());
        assertEquals(20, result.getHeight());
        assertBitmapQuadColor(result, Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
    }

    @Test
    public void testAutoSizeWithSrcRect() throws InterruptedException {
        ImageReader reader = ImageReader.newInstance(100, 100, PixelFormat.RGBA_8888, 1,
                HardwareBuffer.USAGE_CPU_WRITE_OFTEN | HardwareBuffer.USAGE_CPU_READ_OFTEN
                        | HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
        ImageWriter writer = ImageWriter.newInstance(reader.getSurface(), 1);
        Image image = writer.dequeueInputImage();
        Image.Plane plane = image.getPlanes()[0];
        Bitmap bitmap = Bitmap.createBitmap(plane.getRowStride() / 4,
                image.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(false);
        canvas.drawColor(Color.MAGENTA);
        canvas.translate(20f, 70f);
        paint.setColor(Color.RED);
        canvas.drawRect(0f, 0f, 10f, 10f, paint);
        paint.setColor(Color.GREEN);
        canvas.drawRect(10f, 0f, 20f, 10f, paint);
        paint.setColor(Color.BLUE);
        canvas.drawRect(0f, 10f, 10f, 20f, paint);
        paint.setColor(Color.BLACK);
        canvas.drawRect(10f, 10f, 20f, 20f, paint);
        bitmap.copyPixelsToBuffer(plane.getBuffer());
        writer.queueInputImage(image);

        PixelCopy.Result copyResult = copySurface(reader.getSurface(),
                request -> request.setSourceRect(new Rect(20, 70, 40, 90)));
        assertEquals("Copy request failed", PixelCopy.SUCCESS, copyResult.getStatus());
        Bitmap result = copyResult.getBitmap();
        assertEquals(20, result.getWidth());
        assertEquals(20, result.getHeight());
        assertBitmapQuadColor(result, Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
    }

    private static int getPixelFloatPos(Bitmap bitmap, float xpos, float ypos) {
        return bitmap.getPixel((int) (bitmap.getWidth() * xpos), (int) (bitmap.getHeight() * ypos));
    }

    private void assertBitmapQuadColor(Bitmap bitmap,
            int topLeft, int topRight, int bottomLeft, int bottomRight) {
        assertBitmapQuadColor(mTestName.getMethodName(), "PixelCopyTest", bitmap,
                topLeft, topRight, bottomLeft, bottomRight);
    }

    public static void assertBitmapQuadColor(String testName, String className, Bitmap bitmap,
                int topLeft, int topRight, int bottomLeft, int bottomRight) {
        try {
            // Just quickly sample 4 pixels in the various regions.
            assertEquals("Top left " + Integer.toHexString(topLeft) + ", actual= "
                            + Integer.toHexString(getPixelFloatPos(bitmap, .25f, .25f)),
                    topLeft, getPixelFloatPos(bitmap, .25f, .25f));
            assertEquals("Top right", topRight, getPixelFloatPos(bitmap, .75f, .25f));
            assertEquals("Bottom left", bottomLeft, getPixelFloatPos(bitmap, .25f, .75f));
            assertEquals("Bottom right", bottomRight, getPixelFloatPos(bitmap, .75f, .75f));

            // and some closer to the center point, to ensure that our quadrants are even
            float below = .45f;
            float above = .55f;
            assertEquals("Top left II " + Integer.toHexString(topLeft) + ", actual= "
                            + Integer.toHexString(getPixelFloatPos(bitmap, below, below)),
                    topLeft, getPixelFloatPos(bitmap, below, below));
            assertEquals("Top right II", topRight, getPixelFloatPos(bitmap, above, below));
            assertEquals("Bottom left II", bottomLeft, getPixelFloatPos(bitmap, below, above));
            assertEquals("Bottom right II", bottomRight, getPixelFloatPos(bitmap, above, above));
        } catch (AssertionError err) {
            BitmapDumper.dumpBitmap(bitmap, testName, className);
            throw err;
        }
    }

    private void assertBitmapQuadColor(Bitmap bitmap, int topLeft, int topRight,
            int bottomLeft, int bottomRight, int threshold) {
        Function<Float, Integer> getX = (Float x) -> (int) (bitmap.getWidth() * x);
        Function<Float, Integer> getY = (Float y) -> (int) (bitmap.getHeight() * y);

        // Just quickly sample 4 pixels in the various regions.
        assertBitmapColor("Top left", bitmap, topLeft,
                getX.apply(.25f), getY.apply(.25f), threshold);
        assertBitmapColor("Top right", bitmap, topRight,
                getX.apply(.75f), getY.apply(.25f), threshold);
        assertBitmapColor("Bottom left", bitmap, bottomLeft,
                getX.apply(.25f), getY.apply(.75f), threshold);
        assertBitmapColor("Bottom right", bitmap, bottomRight,
                getX.apply(.75f), getY.apply(.75f), threshold);

        float below = .4f;
        float above = .6f;
        assertBitmapColor("Top left II", bitmap, topLeft,
                getX.apply(below), getY.apply(below), threshold);
        assertBitmapColor("Top right II", bitmap, topRight,
                getX.apply(above), getY.apply(below), threshold);
        assertBitmapColor("Bottom left II", bitmap, bottomLeft,
                getX.apply(below), getY.apply(above), threshold);
        assertBitmapColor("Bottom right II", bitmap, bottomRight,
                getX.apply(above), getY.apply(above), threshold);
    }

    private void assertBitmapEdgeColor(Bitmap bitmap, int edgeColor) {
        // Just quickly sample a few pixels on the edge and assert
        // they are edge color, then assert that just inside the edge is a different color
        assertBitmapColor("Top edge", bitmap, edgeColor, bitmap.getWidth() / 2, 1);
        assertBitmapNotColor("Top edge", bitmap, edgeColor, bitmap.getWidth() / 2, 2);

        assertBitmapColor("Left edge", bitmap, edgeColor, 1, bitmap.getHeight() / 2);
        assertBitmapNotColor("Left edge", bitmap, edgeColor, 2, bitmap.getHeight() / 2);

        assertBitmapColor("Bottom edge", bitmap, edgeColor,
                bitmap.getWidth() / 2, bitmap.getHeight() - 2);
        assertBitmapNotColor("Bottom edge", bitmap, edgeColor,
                bitmap.getWidth() / 2, bitmap.getHeight() - 3);

        assertBitmapColor("Right edge", bitmap, edgeColor,
                bitmap.getWidth() - 2, bitmap.getHeight() / 2);
        assertBitmapNotColor("Right edge", bitmap, edgeColor,
                bitmap.getWidth() - 3, bitmap.getHeight() / 2);
    }

    private static boolean pixelsAreSame(int ideal, int given, int threshold) {
        int error = Math.abs(Color.red(ideal) - Color.red(given));
        error += Math.abs(Color.green(ideal) - Color.green(given));
        error += Math.abs(Color.blue(ideal) - Color.blue(given));
        return (error < threshold);
    }

    private void fail(Bitmap bitmap, String message) {
        BitmapDumper.dumpBitmap(bitmap, mTestName.getMethodName(), "PixelCopyTest");
        Assert.fail(message);
    }

    private void assertBitmapColor(String debug, Bitmap bitmap, int color, int x, int y) {
        assertBitmapColor(debug, bitmap, color,  x, y, 10);
    }

    private void assertBitmapColor(String debug, Bitmap bitmap, int color, int x, int y,
            int threshold) {
        int pixel = bitmap.getPixel(x, y);
        if (!pixelsAreSame(color, pixel, threshold)) {
            fail(bitmap, debug + "; expected=" + Integer.toHexString(color) + ", actual="
                    + Integer.toHexString(pixel));
        }
    }

    private void assertBitmapNotColor(String debug, Bitmap bitmap, int color, int x, int y) {
        int pixel = bitmap.getPixel(x, y);
        if (pixelsAreSame(color, pixel, 10)) {
            fail(bitmap, debug + "; actual=" + Integer.toHexString(pixel)
                    + " shouldn't have matched " + Integer.toHexString(color));
        }
    }

    private static class SurfaceTextureRule implements TestRule {
        private SurfaceTexture mSurfaceTexture = null;
        private Surface mSurface = null;

        private void createIfNecessary() {
            mSurfaceTexture = new SurfaceTexture(false);
            mSurface = new Surface(mSurfaceTexture);
        }

        public Surface getSurface() {
            createIfNecessary();
            return mSurface;
        }

        @Override
        public Statement apply(Statement base, Description description) {
            return new CreateSurfaceTextureStatement(base);
        }

        private class CreateSurfaceTextureStatement extends Statement {

            private final Statement mBase;

            public CreateSurfaceTextureStatement(Statement base) {
                mBase = base;
            }

            @Override
            public void evaluate() throws Throwable {
                try {
                    mBase.evaluate();
                } finally {
                    try {
                        if (mSurface != null) mSurface.release();
                    } catch (Throwable t) {}
                    try {
                        if (mSurfaceTexture != null) mSurfaceTexture.release();
                    } catch (Throwable t) {}
                }
            }
        }
    }
}
