/*
 * Copyright (C) 2024 The Android Open Source Project
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
package android.media.projection;

import static android.app.Activity.RESULT_OK;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.media.cts.MediaProjectionActivity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test {@link MediaProjection} detecting when the consent token is re-used by an app with target
 * SDK U+. The platform should thrown an exception.
 * <p>
 * See MediaProjectionTest for general functional tests.
 * <p>
 * Run with:
 * atest CtsMediaProjectionSDK34TestCases:MediaProjectionSDK34Test
 */
public class MediaProjectionSDK34Test {
    private static final String TAG = "MediaProjectionSDK34Test";

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

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    @After
    public void cleanup() {
        if (mMediaProjection != null) {
            if (mCallback != null) {
                mMediaProjection.unregisterCallback(mCallback);
            }
            mMediaProjection.stop();
        }
    }

    @ApiTest(apis = "android.media.projection.MediaProjection#createVirtualDisplay")
    @Test
    public void testCreateVirtualDisplay_withoutCallback_throwsException() throws Exception {
        startMediaProjection();

        // Try to start capture with no callback registered.
        assertThrows(IllegalStateException.class, this::createVirtualDisplay);

        mCallback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                cleanupVirtualDisplay();
            }
        };
        mMediaProjection.registerCallback(mCallback, new Handler(Looper.getMainLooper()));

        // Now starting capture should succeed.
        createVirtualDisplay();
        assertNotNull("VirtualDisplay should be a non-null object if capture started "
                + "successfully", mVirtualDisplay);
    }

    /**
     * Test that an app re-using the user's consent token is prevented, by the platform throwing
     * an exception.
     * <p>
     * The user's consent token is bundled in the result Intent returned after the user navigates
     * the consent dialogs. An app may cache this Intent & pass it to
     * MediaProjectionManager#getMediaProjection multiple times to get different
     * MediaProjection instances tied to the same consent token.
     */
    @ApiTest(apis = "android.media.projection.MediaProjection#createVirtualDisplay")
    @Test
    public void testCreateVirtualDisplay_reusedResultData_throwsException() throws Exception {
        // Navigate the dialog and retrieve the first projection instance.
        startMediaProjection();
        assertNotNull("MediaProjection should be a non-null object if projection started "
                + "successfully", mMediaProjection);

        // Retrieve the result data used to create the first media projection instance.
        Intent resultWithConsent = mActivity.getResultData();
        MediaProjectionManager projectionManager = mContext.getSystemService(
                MediaProjectionManager.class);

        // Re-use the result data to retrieve a new media projection instance.
        MediaProjection anotherProjection = projectionManager.getMediaProjection(RESULT_OK,
                resultWithConsent);
        assertNotNull("MediaProjection should be a non-null object if projection started "
                + "successfully", anotherProjection);

        mCallback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                cleanupVirtualDisplay();
            }
        };
        anotherProjection.registerCallback(mCallback, new Handler(Looper.getMainLooper()));
        mImageReader = ImageReader.newInstance(RECORDING_WIDTH, RECORDING_HEIGHT,
                PixelFormat.RGBA_8888, /* maxImages= */ 1);

        // There should be an exception thrown when trying to start capture on the second
        // projection.
        assertThrows(SecurityException.class,
                () -> anotherProjection.createVirtualDisplay(TAG + "VirtualDisplay",
                        RECORDING_WIDTH, RECORDING_HEIGHT, RECORDING_DENSITY,
                        VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        mImageReader.getSurface(),
                        /* VirtualDisplay.Callback= */ null,
                        new Handler(Looper.getMainLooper())));
    }

    /**
     * Test that an app re-using the user's consent token is prevented, by the platform throwing
     * an exception.
     * <p>
     * The user's consent token is bundled within MediaProjection. An app may try to capture
     * multiple times without re-showing the permission dialog by invoking
     * MediaProjection#createVirtualDisplay more than once on the same MediaProjection instance.
     */
    @ApiTest(apis = "android.media.projection.MediaProjection#createVirtualDisplay")
    @Test
    public void testCreateVirtualDisplay_reusedMediaProjection_throwsException() throws Exception {
        startMediaProjection();

        // Start capture once.
        mCallback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                cleanupVirtualDisplay();
            }
        };
        mMediaProjection.registerCallback(mCallback, new Handler(Looper.getMainLooper()));
        createVirtualDisplay();

        // Now try to capture again on the same mMediaProjection instance; this should throw an
        // exception.
        assertThrows(SecurityException.class,
                () -> mMediaProjection.createVirtualDisplay(TAG + "VirtualDisplay",
                        RECORDING_WIDTH, RECORDING_HEIGHT, RECORDING_DENSITY,
                        VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        mImageReader.getSurface(),
                        /* VirtualDisplay.Callback= */ null,
                        new Handler(Looper.getMainLooper())));
    }

    void startMediaProjection() throws Exception {
        mActivityRule.launchActivity(null);
        mActivity = mActivityRule.getActivity();
        mMediaProjection = mActivity.waitForMediaProjection();
    }

    void createVirtualDisplay() {
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

    void cleanupVirtualDisplay() {
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
