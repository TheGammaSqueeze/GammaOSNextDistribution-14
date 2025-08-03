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
package android.view.cts.surfacevalidator;

import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;
import static android.server.wm.CtsWindowInfoUtils.getWindowBoundsInWindowSpace;
import static android.view.WindowInsets.Type.statusBars;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.server.wm.settings.SettingsSession;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.PointerIcon;
import android.view.SurfaceControl;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.FrameLayout;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.rules.TestName;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CapturedActivity extends Activity {
    public static final String STORAGE_DIR = "CtsSurfaceControl";

    public static class TestResult {
        public int passFrames;
        public int failFrames;
        public final SparseArray<Bitmap> failures = new SparseArray<>();
    }
    private static class ImmersiveConfirmationSetting extends SettingsSession<String> {
        ImmersiveConfirmationSetting() {
            super(Settings.Secure.getUriFor(
                            Settings.Secure.IMMERSIVE_MODE_CONFIRMATIONS),
                    Settings.Secure::getString, Settings.Secure::putString);
        }
    }

    private ImmersiveConfirmationSetting mSettingsSession;

    private static final String TAG = "CapturedActivity";
    private VirtualDisplay mVirtualDisplay;

    private SurfacePixelValidator2 mSurfacePixelValidator;

    private static final long WAIT_TIMEOUT_S = 5L * HW_TIMEOUT_MULTIPLIER;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private volatile boolean mOnEmbedded;

    private final Point mTestAreaSize = new Point();

    private FrameLayout mParentLayout;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final PackageManager packageManager = getPackageManager();
        mParentLayout = new FrameLayout(this);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        setContentView(mParentLayout, layoutParams);

        // Embedded devices are significantly slower, and are given
        // longer duration to capture the expected number of frames
        mOnEmbedded = packageManager.hasSystemFeature(PackageManager.FEATURE_EMBEDDED);

        mSettingsSession = new ImmersiveConfirmationSetting();
        mSettingsSession.set("confirmed");

        WindowInsetsController windowInsetsController = getWindow().getInsetsController();
        windowInsetsController.hide(
                WindowInsets.Type.navigationBars() | WindowInsets.Type.statusBars());
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        getWindow().setAttributes(params);
        getWindow().setDecorFitsSystemWindows(false);

        // Set the NULL pointer icon so that it won't obstruct the captured image.
        getWindow().getDecorView().setPointerIcon(
                PointerIcon.getSystemIcon(this, PointerIcon.TYPE_NULL));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        KeyguardManager keyguardManager = getSystemService(KeyguardManager.class);
        if (keyguardManager != null) {
            keyguardManager.requestDismissKeyguard(this, null);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        restoreSettings();
    }

    public long getCaptureDurationMs() {
        return mOnEmbedded ? 100000 : 50000;
    }

    public TestResult runTest(ISurfaceValidatorTestCase animationTestCase) throws Throwable {
        TestResult testResult = new TestResult();
        Runnable cleanupRunnable = () -> {
            Log.d(TAG, "Stopping capture and ending test case");
            if (mVirtualDisplay != null) {
                mVirtualDisplay.release();
                mVirtualDisplay = null;
            }

            animationTestCase.end();
            FrameLayout contentLayout = findViewById(android.R.id.content);
            contentLayout.removeAllViews();
            if (mSurfacePixelValidator != null) {
                mSurfacePixelValidator.finish(testResult);
                mSurfacePixelValidator = null;
            }
        };

        try {
            final int numFramesRequired = animationTestCase.getNumFramesRequired();
            final long maxCapturedDuration = getCaptureDurationMs();

            CountDownLatch frameDrawnLatch = new CountDownLatch(1);
            mHandler.post(() -> {
                Log.d(TAG, "Setting up test case");

                // See b/216583939. On some devices, hiding system bars is disabled. In those cases,
                // adjust the area that is rendering the test content to be outside the status bar
                // margins to ensure capturing and comparing frames skips the status bar area.
                Insets statusBarInsets = getWindow()
                        .getDecorView()
                        .getRootWindowInsets()
                        .getInsets(statusBars());
                FrameLayout.LayoutParams layoutParams =
                        (FrameLayout.LayoutParams) mParentLayout.getLayoutParams();
                layoutParams.setMargins(statusBarInsets.left, statusBarInsets.top,
                        statusBarInsets.right, statusBarInsets.bottom);
                mParentLayout.setLayoutParams(layoutParams);

                animationTestCase.start(getApplicationContext(), mParentLayout);

                Runnable runnable = () -> {
                    SurfaceControl.Transaction t = new SurfaceControl.Transaction();
                    t.addTransactionCommittedListener(Runnable::run, frameDrawnLatch::countDown);
                    mParentLayout.getRootSurfaceControl().applyTransactionOnDraw(t);
                };

                if (mParentLayout.isAttachedToWindow()) {
                    runnable.run();
                } else {
                    mParentLayout.getViewTreeObserver().addOnWindowAttachListener(
                            new ViewTreeObserver.OnWindowAttachListener() {
                                @Override
                                public void onWindowAttached() {
                                    runnable.run();
                                }

                                @Override
                                public void onWindowDetached() {
                                }
                            });
                }
            });

            assertTrue("Failed to wait for animation to start", animationTestCase.waitForReady());
            assertTrue("Failed to wait for frame draw",
                    frameDrawnLatch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS));

            Rect bounds = getWindowBoundsInWindowSpace(mParentLayout::getWindowToken);
            assertNotNull("Failed to wait for test window bounds", bounds);
            mTestAreaSize.set(bounds.width(), bounds.height());

            CountDownLatch setupLatch = new CountDownLatch(1);
            mHandler.post(() -> {
                Log.d(TAG, "Starting capture");

                Log.d(TAG, "testAreaWidth: " + mTestAreaSize.x
                        + ", testAreaHeight: " + mTestAreaSize.y);

                Rect boundsToCheck = animationTestCase.getBoundsToCheck(mParentLayout);
                if (boundsToCheck != null && (boundsToCheck.width() < 40
                        || boundsToCheck.height() < 40)) {
                    fail("capture bounds too small to be a fullscreen activity: " + boundsToCheck);
                }

                Log.d(TAG, "Size is " + mTestAreaSize + ", bounds are "
                        + (boundsToCheck == null ? "full screen" : boundsToCheck.toShortString()));

                mSurfacePixelValidator = new SurfacePixelValidator2(mTestAreaSize,
                        boundsToCheck, animationTestCase.getChecker(), numFramesRequired);

                WindowMetrics metrics = getWindowManager().getCurrentWindowMetrics();
                Log.d(TAG, "Starting capture: metrics=" + metrics);
                int densityDpi = (int) (metrics.getDensity() * DisplayMetrics.DENSITY_DEFAULT);

                DisplayManager dm = getSystemService(DisplayManager.class);
                mVirtualDisplay = dm.createVirtualDisplay("CtsCapturedActivity",
                        mTestAreaSize.x, mTestAreaSize.y, densityDpi,
                        mSurfacePixelValidator.getSurface(), 0, null /*Callbacks*/,
                        null /*Handler*/);
                assertNotNull("Failed to create VirtualDisplay", mVirtualDisplay);
                SystemUtil.runWithShellPermissionIdentity(
                        () -> assertTrue("Failed to mirror content onto display",
                                getWindowManager().replaceContentOnDisplayWithMirror(
                                        mVirtualDisplay.getDisplay().getDisplayId(), getWindow())),
                        Manifest.permission.ACCESS_SURFACE_FLINGER);

                setupLatch.countDown();
            });

            assertTrue("Failed to complete creating and setting up VD",
                    setupLatch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS));
            assertTrue("Failed to wait for required number of frames",
                    mSurfacePixelValidator.waitForAllFrames(maxCapturedDuration));
            final CountDownLatch testRunLatch = new CountDownLatch(1);
            mHandler.post(() -> {
                cleanupRunnable.run();
                testRunLatch.countDown();
            });

            assertTrue("Failed to wait for test to complete",
                    testRunLatch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS));

            Log.d(TAG, "Test finished, passFrames " + testResult.passFrames
                    + ", failFrames " + testResult.failFrames);
            return testResult;
        } catch (Throwable throwable) {
            mHandler.post(cleanupRunnable);
            Log.e(TAG, "Test Failed, passFrames " + testResult.passFrames + ", failFrames "
                    + testResult.failFrames);
            throw throwable;
        }
    }

    private void saveFailureCaptures(SparseArray<Bitmap> failFrames, TestName name) {
        if (failFrames.size() == 0) return;

        String directoryName = Environment.getExternalStorageDirectory()
                + "/" + STORAGE_DIR
                + "/" + getClass().getSimpleName()
                + "/" + name.getMethodName();
        File testDirectory = new File(directoryName);
        if (testDirectory.exists()) {
            String[] children = testDirectory.list();
            if (children == null) {
                return;
            }
            for (String file : children) {
                new File(testDirectory, file).delete();
            }
        } else {
            testDirectory.mkdirs();
        }

        for (int i = 0; i < failFrames.size(); i++) {
            int frameNr = failFrames.keyAt(i);
            Bitmap bitmap = failFrames.valueAt(i);

            String bitmapName =  "frame_" + frameNr + ".png";
            Log.d(TAG, "Saving file : " + bitmapName + " in directory : " + directoryName);

            File file = new File(directoryName, bitmapName);
            try (FileOutputStream fileStream = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 85, fileStream);
                fileStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void verifyTest(ISurfaceValidatorTestCase testCase, TestName name) throws Throwable {
        CapturedActivity.TestResult result = runTest(testCase);
        saveFailureCaptures(result.failures, name);

        float failRatio = 1.0f * result.failFrames / (result.failFrames + result.passFrames);
        assertTrue("Error: " + failRatio + " fail ratio - extremely high, is activity obstructed?",
                failRatio < 0.95f);
        assertEquals("Error: " + result.failFrames
                + " incorrect frames observed - incorrect positioning", 0, result.failFrames);
    }

    public void restoreSettings() {
        // Adding try/catch due to bug with UiAutomation crashing the test b/272370325
        try {
            if (mSettingsSession != null) {
                mSettingsSession.close();
                mSettingsSession = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Crash occurred when closing settings session. See b/272370325", e);
        }
    }
}
