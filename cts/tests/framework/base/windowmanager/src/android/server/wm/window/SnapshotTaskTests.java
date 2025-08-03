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

package android.server.wm.window;

import static android.server.wm.WindowManagerTestBase.startActivity;
import static android.view.WindowInsets.Type.captionBar;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.UiAutomation;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.WindowManagerState;
import android.server.wm.WindowManagerTestBase;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.cts.surfacevalidator.BitmapPixelChecker;
import android.window.SplashScreen;

import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Build/Install/Run:
 *     atest CtsWindowManagerDeviceWindow:SnapshotTaskTests
 */
public class SnapshotTaskTests extends ActivityManagerTestBase {
    private static final String TAG = "SnapshotTaskTests";
    private static final ComponentName TEST_ACTIVITY = new ComponentName(
            getInstrumentation().getContext(), TestActivity.class);

    private TestActivity mActivity;
    private WindowManager mWindowManager;
    private UiAutomation mUiAutomation;

    private static final int MATCHING_PIXEL_MISMATCH_ALLOWED = 100;

    @Before
    public void setup() throws Exception {
        super.setUp();
        mActivity = startActivity(TestActivity.class);

        mWindowManager = mActivity.getSystemService(WindowManager.class);
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity();

        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        mActivity.waitUntilReady();
    }

    @After
    public void cleanup() {
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void testSetDisablePreviewScreenshots() throws Exception {
        final View decor = mActivity.getWindow().getDecorView();
        final int captionBarHeight = decor.getRootWindowInsets().getInsets(captionBar()).top;

        BitmapPixelChecker pixelChecker = new BitmapPixelChecker(Color.RED);

        int retries = 0;
        boolean matchesPixels = false;
        while (retries < 5) {
            Bitmap bitmap = mWindowManager.snapshotTaskForRecents(mActivity.getTaskId());
            if (bitmap != null) {
                int expectedMatching =
                        bitmap.getWidth() * bitmap.getHeight() - MATCHING_PIXEL_MISMATCH_ALLOWED
                                - (captionBarHeight * decor.getWidth());
                Rect boundToCheck = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
                int matchingPixels = pixelChecker.getNumMatchingPixels(bitmap, boundToCheck);
                matchesPixels = matchingPixels >= expectedMatching;
            }
            if (matchesPixels) {
                break;
            }
            retries++;
            Thread.sleep(1000);
        }

        assertTrue(matchesPixels);

        mActivity.setRecentsScreenshotEnabled(false);

        retries = 0;
        WindowManagerState.Activity activityContainer = null;
        boolean enableScreenshot = true;
        while (retries < 3) {
            mWmState.computeState();
            activityContainer = mWmState.getActivity(TEST_ACTIVITY);
            if (activityContainer != null) {
                enableScreenshot = activityContainer.enableRecentsScreenshot();
            }
            if (enableScreenshot) {
                break;
            }
            retries++;
            Thread.sleep(500);
        }
        assertFalse("Recents screenshots should be disabled", enableScreenshot);

        Bitmap bitmap = mWindowManager.snapshotTaskForRecents(mActivity.getTaskId());
        assertNotNull(bitmap);
        Rect boundToCheck = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        int matchingPixels = pixelChecker.getNumMatchingPixels(bitmap, boundToCheck);
        assertTrue(
                "Expected <=" + MATCHING_PIXEL_MISMATCH_ALLOWED + " matched " + matchingPixels,
                matchingPixels <= MATCHING_PIXEL_MISMATCH_ALLOWED);
    }

    public static class TestActivity extends WindowManagerTestBase.FocusableActivity {
        private final CountDownLatch mReadyToStart = new CountDownLatch(3);

        @Override
        public void onEnterAnimationComplete() {
            mReadyToStart.countDown();
        }

        public void waitUntilReady() throws InterruptedException {
            mReadyToStart.await(5, TimeUnit.SECONDS);
        }

        @Override
        public void onAttachedToWindow() {
            super.onAttachedToWindow();

            SurfaceControl.Transaction t = new SurfaceControl.Transaction();
            t.addTransactionCommittedListener(getMainExecutor(),
                    mReadyToStart::countDown);
            getWindow().getDecorView().getRootSurfaceControl().applyTransactionOnDraw(t);
        }

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            View view = new View(this);
            view.setBackgroundColor(Color.RED);
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            setContentView(view, layoutParams);

            WindowInsetsController windowInsetsController = getWindow().getInsetsController();
            windowInsetsController.hide(
                    WindowInsets.Type.navigationBars() | WindowInsets.Type.statusBars());
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            getWindow().setAttributes(params);
            getWindow().setDecorFitsSystemWindows(false);

            SplashScreen splashscreen = getSplashScreen();
            splashscreen.setOnExitAnimationListener(splashView -> {
                splashView.remove();
                mReadyToStart.countDown();
            });
        }
    }
}
