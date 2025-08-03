/*
 * Copyright (C) 2023 The Android Open Source Project
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
package android.media.projection.cts;

import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR;
import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.media.cts.MediaProjectionActivity;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link MediaProjection} lifecycle & callbacks.
 *
 * Note that there are other tests verifying that screen capturing actually works correctly in
 * CtsWindowManagerDeviceTestCases.
 *
 * Run with:
 * atest CtsMediaProjectionTestCases:MediaProjectionTest
 */
@NonMainlineTest
public class MediaProjectionTest {
    private static final String TAG = "MediaProjectionTest";
    private static final int RECORDING_WIDTH = 500;
    private static final int RECORDING_HEIGHT = 700;
    private static final int RECORDING_DENSITY = 200;

    @Rule
    public ActivityTestRule<MediaProjectionActivity> mActivityRule =
            new ActivityTestRule<>(MediaProjectionActivity.class, false, false);

    private MediaProjectionActivity mActivity;
    private MediaProjection mMediaProjection;
    private MediaProjection.Callback mCallback = null;
    private ImageReader mImageReader;
    private VirtualDisplay mVirtualDisplay;
    private Context mContext;
    private int mTimeoutMs;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        runWithShellPermissionIdentity(() -> {
            mContext.getPackageManager().revokeRuntimePermission(
                    mContext.getPackageName(),
                    android.Manifest.permission.SYSTEM_ALERT_WINDOW,
                    new UserHandle(ActivityManager.getCurrentUser()));
        });
        mTimeoutMs = 1000 * HW_TIMEOUT_MULTIPLIER;
    }

    @After
    public void cleanup() {
        if (mMediaProjection != null) {
            if (mCallback != null) {
                mMediaProjection.unregisterCallback(mCallback);
                mCallback = null;
            }
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }

    /**
     * This test starts and stops a MediaProjection screen capture session using
     * MediaProjectionActivity.
     *
     * Currently, we check that we are able to draw overlay windows during the session but not
     * before
     * or after. (We request SYSTEM_ALERT_WINDOW permission, but it is not granted, so by default
     * we
     * cannot).
     */
    @Test
    public void testOverlayAllowedDuringScreenCapture() throws Exception {
        assertFalse(Settings.canDrawOverlays(mContext));

        startMediaProjection();
        assertTrue(Settings.canDrawOverlays(mContext));

        CountDownLatch latch = new CountDownLatch(1);
        mCallback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                latch.countDown();
            }
        };
        mMediaProjection.registerCallback(mCallback, new Handler(Looper.getMainLooper()));
        mMediaProjection.stop();

        assertTrue("Could not stop the MediaProjection in " + mTimeoutMs + "ms",
                latch.await(mTimeoutMs, TimeUnit.MILLISECONDS));

        assertFalse(Settings.canDrawOverlays(mContext));
    }

    @ApiTest(apis = "android.media.projection.MediaProjection#createVirtualDisplay")
    @Test
    public void testCreateVirtualDisplay() throws Exception {
        startMediaProjection();
        createVirtualDisplay();

        assertThat(mVirtualDisplay).isNotNull();
        Point virtualDisplayDimensions = new Point();
        mVirtualDisplay.getDisplay().getSize(virtualDisplayDimensions);
        assertThat(virtualDisplayDimensions).isEqualTo(
                new Point(RECORDING_WIDTH, RECORDING_HEIGHT));
    }

    @ApiTest(apis = "android.media.projection.MediaProjection#unregisterCallback")
    @Test
    public void testUnregisterCallback() throws Exception {
        startMediaProjection();

        CountDownLatch latch = new CountDownLatch(1);
        mCallback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                latch.countDown();
            }
        };
        mMediaProjection.registerCallback(mCallback, new Handler(Looper.getMainLooper()));
        mMediaProjection.unregisterCallback(mCallback);

        createVirtualDisplay();
        mMediaProjection.stop();
        assertFalse("Callback is not invoked after " + mTimeoutMs + " ms if unregistere",
                latch.await(mTimeoutMs, TimeUnit.MILLISECONDS));
    }

    @ApiTest(apis = {
            "android.media.projection.MediaProjection#registerCallback",
            "android.media.projection.MediaProjection#stop",
            "android.media.projection.MediaProjection.Callback#onStop"
    })
    @Test
    public void testCallbackOnStop() throws Exception {
        startMediaProjection();

        CountDownLatch latch = new CountDownLatch(1);
        mCallback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                latch.countDown();
            }
        };
        mMediaProjection.registerCallback(mCallback, new Handler(Looper.getMainLooper()));
        createVirtualDisplay();
        mMediaProjection.stop();

        assertTrue("Could not stop the MediaProjection in " + mTimeoutMs + "ms",
                latch.await(mTimeoutMs, TimeUnit.MILLISECONDS));
    }

    @ApiTest(apis = "android.media.projection.MediaProjection.Callback#onCapturedContentResize")
    @Test
    public void testCallbackOnCapturedContentResize() throws Exception {
        startMediaProjection();

        CountDownLatch latch = new CountDownLatch(1);
        Point mContentSize = new Point();
        mCallback = new MediaProjection.Callback() {
            @Override
            public void onCapturedContentResize(int width, int height) {
                mContentSize.x = width;
                mContentSize.y = height;
                latch.countDown();
            }
        };
        createVirtualDisplayAndWaitForCallback(latch);
        Rect maxWindowMetrics = mActivity.getSystemService(
                WindowManager.class).getMaximumWindowMetrics().getBounds();
        assertThat(mContentSize).isEqualTo(
                new Point(maxWindowMetrics.width(), maxWindowMetrics.height()));
    }

    @ApiTest(apis = "android.media.projection.MediaProjection"
            + ".Callback#onCapturedContentVisibilityChanged")
    @Test
    public void testCallbackOnCapturedContentVisibilityChanged() throws Exception {
        startMediaProjection();
        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] isVisibleUpdate = {false};
        mCallback = new MediaProjection.Callback() {
            @Override
            public void onCapturedContentVisibilityChanged(boolean isVisible) {
                super.onCapturedContentVisibilityChanged(isVisible);
                isVisibleUpdate[0] = isVisible;
                latch.countDown();
            }
        };
        createVirtualDisplayAndWaitForCallback(latch);
        assertThat(isVisibleUpdate[0]).isTrue();
    }

    /**
     * Given mCallback invokes {@link CountDownLatch#countDown()} when triggered, start recording
     * and wait for the callback to be invoked.
     */
    private void createVirtualDisplayAndWaitForCallback(CountDownLatch latch) throws Exception {
        mMediaProjection.registerCallback(mCallback, new Handler(Looper.getMainLooper()));
        createVirtualDisplay();
        assertTrue("Did not get callback after starting recording on the MediaProjection in "
                        + mTimeoutMs + "ms",
                latch.await(mTimeoutMs, TimeUnit.MILLISECONDS));
    }

    private void startMediaProjection() throws Exception {
        mActivityRule.launchActivity(null);
        mActivity = mActivityRule.getActivity();
        mMediaProjection = mActivity.waitForMediaProjection();
    }

    private void createVirtualDisplay() {
        mImageReader = ImageReader.newInstance(RECORDING_WIDTH, RECORDING_HEIGHT,
                PixelFormat.RGBA_8888, /* maxImages= */ 1);
        mVirtualDisplay = mMediaProjection.createVirtualDisplay(TAG + "VirtualDisplay",
                RECORDING_WIDTH, RECORDING_HEIGHT, RECORDING_DENSITY,
                VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader.getSurface(),
                new VirtualDisplay.Callback() {
                    @Override
                    public void onStopped() {
                        super.onStopped();
                        // VirtualDisplay stopped by the system; no more frames incoming. Must
                        // release VirtualDisplay
                        Log.v(TAG, "handleVirtualDisplayStopped");
                        cleanupVirtualDisplay();
                    }
                }, new Handler(Looper.getMainLooper()));
    }

    private void cleanupVirtualDisplay() {
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }

        if (mVirtualDisplay != null) {
            final Surface surface = mVirtualDisplay.getSurface();
            if (surface != null) {
                surface.release();
            }
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
    }
}
