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
import static android.media.cts.MediaProjectionActivity.ACCEPT_RESOURCE_ID;
import static android.media.cts.MediaProjectionActivity.SPINNER_RESOURCE_ID;
import static android.media.cts.MediaProjectionActivity.getEntireScreenString;
import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.media.cts.MediaProjectionActivity;
import android.os.Handler;
import android.os.Looper;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiScrollable;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.Until;
import android.util.Log;
import android.view.Surface;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link MediaProjection} detecting when the consent token is re-used by an app with target
 * SDK below U. The platform should re-show the consent dialog and not start capturing until the
 * user has reviewed the dialog.
 * <p>
 * See MediaProjectionTest for general functional tests.
 * <p>
 * Run with:
 * atest CtsMediaProjectionSDK33TestCases:MediaProjectionSDK33Test
 */
public class MediaProjectionSDK33Test {
    private static final String TAG = "MediaProjectionSDK33Test";

    private static final int RECORDING_WIDTH = 500;
    private static final int RECORDING_HEIGHT = 700;
    private static final int RECORDING_DENSITY = 200;
    private static final int MAX_IMAGES = 20;

    @Rule
    public ActivityTestRule<MediaProjectionActivity> mActivityRule =
            new ActivityTestRule<>(MediaProjectionActivity.class, false, false);

    private MediaProjectionActivity mActivity;

    // Define separate objects for each capture.
    private ImageReader mImageReader;
    private VirtualDisplay mVirtualDisplay;
    private final MediaProjection.Callback mCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            cleanupImageReader(mImageReader);
            cleanupVirtualDisplay(mVirtualDisplay);
        }
    };

    private ImageReader mSecondImageReader;
    private VirtualDisplay mSecondVirtualDisplay;

    private final MediaProjection.Callback mSecondCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            cleanupImageReader(mSecondImageReader);
            cleanupVirtualDisplay(mSecondVirtualDisplay);
        }
    };
    private MediaProjection mMediaProjection;
    private Context mContext;
    private int mTimeoutMs;


    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mTimeoutMs = 1000 * HW_TIMEOUT_MULTIPLIER;
    }

    @After
    public void cleanup() {
        UiDevice uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        uiDevice.pressHome();
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection.unregisterCallback(mCallback);
            mMediaProjection.unregisterCallback(mSecondCallback);
            mMediaProjection = null;
        }
    }

    /**
     * Test that an app re-using the user's consent token is prevented, by the platform re-showing
     * the permission dialog.
     * <p>
     * The user's consent token is bundled in the result Intent returned after the user navigates
     * the consent dialogs. An app may cache this Intent & pass it to
     * MediaProjectionManager#getMediaProjection multiple times to get different
     * MediaProjection instances tied to the same consent token.
     * <p>
     * During the second capture, the app should not receive any buffers until the user reviews the
     * re-shown permission dialog.
     */
    @ApiTest(apis = "android.media.projection.MediaProjection#createVirtualDisplay")
    @Test
    public void testCreateVirtualDisplay_reusedResultData_reshowsPermissionDialog()
            throws Exception {
        assumeFalse(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH));

        // Navigate the dialog and retrieve the first projection instance.
        startMediaProjection();
        assertNotNull("MediaProjection should be a non-null object if projection started "
                + "successfully", mMediaProjection);

        // Re-use the result data to retrieve a new media projection instance.
        Intent resultWithConsent = mActivity.getResultData();
        final MediaProjection anotherProjection = mContext.getSystemService(
                MediaProjectionManager.class).getMediaProjection(RESULT_OK, resultWithConsent);
        assertNotNull("MediaProjection should be a non-null object if projection started "
                + "successfully", anotherProjection);

        // Prepare latches to validate that nothing is captured before the user reviews the second
        // permission dialog.
        final CountDownLatch firstBufferLatch = new CountDownLatch(1);
        final CountDownLatch dismissPermissionDialogLatch = new CountDownLatch(1);

        // Prepare for capturing a single buffer from the entire screen.
        anotherProjection.registerCallback(mCallback, new Handler(Looper.getMainLooper()));
        mImageReader = ImageReader.newInstance(RECORDING_WIDTH, RECORDING_HEIGHT,
                PixelFormat.RGBA_8888, /* maxImages= */ 1);
        mImageReader.setOnImageAvailableListener((ImageReader imageReader) ->
                firstBufferLatch.countDown(), new Handler(Looper.getMainLooper()));

        // The permission dialog should be launched again when trying to start capture on the second
        // projection.
        mVirtualDisplay = anotherProjection.createVirtualDisplay(TAG + "VirtualDisplay",
                RECORDING_WIDTH, RECORDING_HEIGHT, RECORDING_DENSITY,
                VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader.getSurface(),
                /* VirtualDisplay.Callback= */ null,
                new Handler(Looper.getMainLooper()));

        // Start recording the entire screen on the re-shown permission dialog.
        // This will wait for the UI elements to appear.
        final UiObject2 startRecordingButton = navigatePermissionDialogToStartButton(mContext);
        if (startRecordingButton == null) {
            Log.e(TAG, "Couldn't find start recording button, something is really wrong");
        } else {
            Log.d(TAG, "found permission dialog after searching all windows, clicked");
            // No image buffers should have arrived before we dismiss the dialog.
            assertThat(firstBufferLatch.getCount()).isEqualTo(1);
            dismissPermissionDialogLatch.countDown();
            startRecordingButton.click();
        }

        // Validate that the permission dialog is dismissed before the first screenshot arrives.
        assertTrue("Could not dismiss the second permission dialog in " + mTimeoutMs + "ms",
                dismissPermissionDialogLatch.await(mTimeoutMs, TimeUnit.MILLISECONDS));
        assertTrue("No buffers arrived from a screen capture after " + mTimeoutMs
                + " ms", firstBufferLatch.await(mTimeoutMs, TimeUnit.MILLISECONDS));
    }

    /**
     * Test that an app re-using the user's consent token is prevented, by the platform re-showing
     * the permission dialog.
     * <p>
     * The user's consent token is bundled within MediaProjection. An app may try to capture
     * multiple times without re-showing the permission dialog by invoking
     * MediaProjection#createVirtualDisplay more than once on the same MediaProjection instance.
     */
    @ApiTest(apis = "android.media.projection.MediaProjection#createVirtualDisplay")
    @Test
    public void testCreateVirtualDisplay_reusedMediaProjection_reshowPermissionDialog()
            throws Exception {
        assumeFalse(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH));

        // Start capture once.
        startMediaProjection();
        mMediaProjection.registerCallback(mCallback, new Handler(Looper.getMainLooper()));
        createVirtualDisplay();

        // Prepare latches to validate that nothing is captured before the user reviews the second
        // permission dialog.
        final CountDownLatch firstBufferLatch = new CountDownLatch(1);
        final CountDownLatch dismissPermissionDialogLatch = new CountDownLatch(1);

        // Now try to capture again on the same mMediaProjection instance; this should re-show the
        // permission dialog.
        // Prepare for capturing a single buffer from the entire screen.
        mMediaProjection.registerCallback(mSecondCallback, new Handler(Looper.getMainLooper()));
        mSecondImageReader = ImageReader.newInstance(RECORDING_WIDTH, RECORDING_HEIGHT,
                PixelFormat.RGBA_8888, MAX_IMAGES);
        mSecondImageReader.setOnImageAvailableListener((ImageReader imageReader) ->
                firstBufferLatch.countDown(), new Handler(Looper.getMainLooper()));
        mSecondVirtualDisplay = mMediaProjection.createVirtualDisplay(TAG + "VirtualDisplay",
                RECORDING_WIDTH, RECORDING_HEIGHT, RECORDING_DENSITY,
                VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mSecondImageReader.getSurface(),
                /* VirtualDisplay.Callback= */ null,
                new Handler(Looper.getMainLooper()));

        // Start recording the entire screen on the re-shown permission dialog.
        // This will wait for the UI elements to appear.
        final UiObject2 startRecordingButton = navigatePermissionDialogToStartButton(mContext);
        if (startRecordingButton == null) {
            Log.e(TAG, "Couldn't find start recording button, something is really wrong");
        } else {
            Log.d(TAG, "Found permission dialog after searching all windows, clicked");
            // No image buffers should have arrived before we dismiss the dialog.
            assertThat(firstBufferLatch.getCount()).isEqualTo(1);
            dismissPermissionDialogLatch.countDown();
            startRecordingButton.click();
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressHome();
        }

        // Validate that the permission dialog is dismissed before the first screenshot arrives.
        assertTrue("Could not dismiss the second permission dialog in " + mTimeoutMs + "ms",
                dismissPermissionDialogLatch.await(mTimeoutMs, TimeUnit.MILLISECONDS));
        assertTrue("No buffers arrived from a screen capture after " + mTimeoutMs
                + " ms", firstBufferLatch.await(mTimeoutMs, TimeUnit.MILLISECONDS));
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
                mImageReader.getSurface(), /* callback= */ null,
                new Handler(Looper.getMainLooper()));
    }

    private static void cleanupImageReader(ImageReader imageReader) {
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    private static void cleanupVirtualDisplay(VirtualDisplay virtualDisplay) {
        if (virtualDisplay != null) {
            final Surface surface = virtualDisplay.getSurface();
            if (surface != null) {
                surface.release();
            }
            virtualDisplay.release();
            virtualDisplay = null;
        }
    }

    /**
     * The permission dialog will be auto-opened by the activity - find it and navigate to the
     * start recording button
     *
     * @return start recording button, or null if it wasn't found.
     */
    public UiObject2 navigatePermissionDialogToStartButton(Context context) {
        // Ensure the device is initialized before interacting with any UI elements.
        UiDevice.getInstance(androidx.test.InstrumentationRegistry.getInstrumentation());
        final boolean isWatch = context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_WATCH);
        if (!isWatch) {
            // if not testing on a watch device, then we need to select the entire screen option
            // before pressing "Start recording" button.
            if (!selectEntireScreenOption(getEntireScreenString(context))) {
                Log.e(TAG, "Couldn't select entire screen option");
            }
        }

        if (isWatch) {
            scrollToStartRecordingButton();
        }

        return waitForObject(By.res(ACCEPT_RESOURCE_ID));
    }

    private boolean selectEntireScreenOption(String entireScreenString) {
        UiObject2 spinner = waitForObject(By.res(SPINNER_RESOURCE_ID));
        if (spinner == null) {
            Log.e(TAG, "Couldn't find spinner to select projection mode");
            return false;
        }
        spinner.click();

        UiObject2 entireScreenOption = waitForObject(By.text(entireScreenString));
        if (entireScreenOption == null) {
            Log.e(TAG, "Couldn't find entire screen option");
            return false;
        }
        entireScreenOption.click();
        return true;
    }

    /** When testing on a small screen device, scrolls to a Start Recording button. */
    private void scrollToStartRecordingButton() {
        // Scroll down the dialog; on a device with a small screen the elements may not be visible.
        final UiScrollable scrollable = new UiScrollable(new UiSelector().scrollable(true));
        try {
            if (!scrollable.scrollIntoView(new UiSelector().resourceId(ACCEPT_RESOURCE_ID))) {
                Log.e(TAG, "Didn't find " + ACCEPT_RESOURCE_ID + " when scrolling");
                return;
            }
            Log.d(TAG, "This is a watch; we finished scrolling down to the ui elements");
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "This is a watch, but there was no scrolling (UI may not be scrollable");
        }
    }

    private UiObject2 waitForObject(BySelector selector) {
        UiDevice uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        return uiDevice.wait(Until.findObject(selector), mTimeoutMs);
    }
}
