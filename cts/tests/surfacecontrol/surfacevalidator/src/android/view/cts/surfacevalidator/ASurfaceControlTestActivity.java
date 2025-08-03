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

package android.view.cts.surfacevalidator;

import static android.view.cts.surfacevalidator.CapturedActivity.STORAGE_DIR;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.AttachedSurfaceControl;
import android.view.Gravity;
import android.view.PointerIcon;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimation;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.junit.Assert;
import org.junit.rules.TestName;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ASurfaceControlTestActivity extends Activity {
    private static final String TAG = "ASurfaceControlTestActivity";
    private static final boolean DEBUG = true;

    private static final int DEFAULT_LAYOUT_WIDTH = 100;
    private static final int DEFAULT_LAYOUT_HEIGHT = 100;
    private static final int OFFSET_X = 100;
    private static final int OFFSET_Y = 100;
    public static final long WAIT_TIMEOUT_S = 5;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private SurfaceView mSurfaceView;
    private FrameLayout.LayoutParams mLayoutParams;
    private FrameLayout mParent;

    private Bitmap mScreenshot;

    private Instrumentation mInstrumentation;

    private final InsetsAnimationCallback mInsetsAnimationCallback = new InsetsAnimationCallback();
    private final CountDownLatch mReadyToStart = new CountDownLatch(1);
    private CountDownLatch mTransactionCommittedLatch;

    @Override
    public void onEnterAnimationComplete() {
        mReadyToStart.countDown();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final View decorView = getWindow().getDecorView();
        decorView.setWindowInsetsAnimationCallback(mInsetsAnimationCallback);
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
        // Set the NULL pointer icon so that it won't obstruct the captured image.
        decorView.setPointerIcon(
                PointerIcon.getSystemIcon(this, PointerIcon.TYPE_NULL));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mLayoutParams = new FrameLayout.LayoutParams(DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                Gravity.LEFT | Gravity.TOP);

        mLayoutParams.topMargin = OFFSET_Y;
        mLayoutParams.leftMargin = OFFSET_X;
        mSurfaceView = new SurfaceView(this);
        mSurfaceView.getHolder().setFixedSize(DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT);

        mParent = findViewById(android.R.id.content);

        mInstrumentation = getInstrumentation();
    }

    public SurfaceControl getSurfaceControl() {
        return mSurfaceView.getSurfaceControl();
    }

    public void verifyTest(SurfaceHolder.Callback surfaceHolderCallback,
            PixelChecker pixelChecker, TestName name) {
        verifyTest(surfaceHolderCallback, pixelChecker, name, 0);
    }

    public void verifyTest(SurfaceHolder.Callback surfaceHolderCallback,
            PixelChecker pixelChecker, TestName name, int numOfTransactionToListen) {
        final boolean waitForTransactionLatch = numOfTransactionToListen > 0;
        final CountDownLatch readyFence = new CountDownLatch(1);
        if (waitForTransactionLatch) {
            mTransactionCommittedLatch = new CountDownLatch(numOfTransactionToListen);
        }
        SurfaceHolderCallback surfaceHolderCallbackWrapper = new SurfaceHolderCallback(
                surfaceHolderCallback,
                readyFence, mParent.getRootSurfaceControl());
        createSurface(surfaceHolderCallbackWrapper);
        try {
            if (waitForTransactionLatch) {
                assertTrue("timeout",
                        mTransactionCommittedLatch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS));
            }
            assertTrue("timeout", readyFence.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Assert.fail("interrupted");
        }
        verifyScreenshot(pixelChecker, name);
        mHandler.post(() -> mSurfaceView.getHolder().removeCallback(surfaceHolderCallback));
    }

    public void awaitReadyState() {
        try {
            assertTrue(mReadyToStart.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void createSurface(SurfaceHolderCallback surfaceHolderCallback) {
        awaitReadyState();

        mHandler.post(() -> {
            mSurfaceView.getHolder().addCallback(surfaceHolderCallback);
            mParent.addView(mSurfaceView, mLayoutParams);
        });
    }

    public void verifyScreenshot(PixelChecker pixelChecker, TestName name) {
        // Wait for the stable insets update. The position of the surface view is in correct before
        // the update. Sometimes this callback isn't called, so we don't want to fail the test
        // because it times out.
        if (!mInsetsAnimationCallback.waitForInsetsAnimation()) {
            Log.w(TAG, "Insets animation wait timed out.");
        }

        final CountDownLatch countDownLatch = new CountDownLatch(1);
        UiAutomation uiAutomation = mInstrumentation.getUiAutomation();
        mHandler.post(() -> {
            mScreenshot = uiAutomation.takeScreenshot(getWindow());
            mParent.removeAllViews();
            countDownLatch.countDown();
        });

        try {
            countDownLatch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS);
        } catch (Exception e) {
        }

        assertNotNull(mScreenshot);

        Bitmap swBitmap = mScreenshot.copy(Bitmap.Config.ARGB_8888, false);
        mScreenshot.recycle();

        int numMatchingPixels = pixelChecker.getNumMatchingPixels(swBitmap);

        int checkedPixels = 0;
        for (Rect bounds : pixelChecker.getBoundsToCheck(swBitmap)) {
            checkedPixels += bounds.width() * bounds.height();
        }

        boolean success = pixelChecker.checkPixels(numMatchingPixels, swBitmap.getWidth(),
                swBitmap.getHeight());
        if (!success) {
            saveFailureCapture(swBitmap, name);
        }
        swBitmap.recycle();

        assertTrue("Actual matched pixels:" + numMatchingPixels
                + " Number of pixels checked:" + checkedPixels, success);
    }

    public SurfaceView getSurfaceView() {
        return mSurfaceView;
    }

    public FrameLayout getParentFrameLayout() {
        return mParent;
    }

    public void transactionCommitted() {
        mTransactionCommittedLatch.countDown();
    }

    public static class RectChecker extends PixelChecker {
        private final List<Rect> mBoundsToCheck;

        public RectChecker(List<Rect> boundsToCheck) {
            super();
            mBoundsToCheck = boundsToCheck;
        }

        public RectChecker(Rect boundsToCheck) {
            this(List.of(boundsToCheck));
        }

        public RectChecker(Rect boundsToCheck, int expectedColor) {
            super(expectedColor);
            mBoundsToCheck = List.of(boundsToCheck);
        }

        @Override
        public boolean checkPixels(int matchingPixelCount, int width, int height) {
            int expectedPixelCount = 0;
            for (Rect bounds : mBoundsToCheck) {
                expectedPixelCount += bounds.width() * bounds.height();
            }
            return expectedPixelCount - 100 < matchingPixelCount
                    && matchingPixelCount <= expectedPixelCount;
        }

        @Override
        public List<Rect> getBoundsToCheck(Bitmap bitmap) {
            return mBoundsToCheck;
        }
    }

    public abstract static class PixelChecker {
        private final PixelColor mPixelColor;
        private final boolean mLogWhenNoMatch;

        public PixelChecker() {
            this(Color.BLACK, true);
        }

        public PixelChecker(int color) {
            this(color, true);
        }

        public PixelChecker(int color, boolean logWhenNoMatch) {
            mPixelColor = new PixelColor(color);
            mLogWhenNoMatch = logWhenNoMatch;
        }

        int getNumMatchingPixels(Bitmap bitmap) {
            int numMatchingPixels = 0;
            int numErrorsLogged = 0;
            for (Rect boundsToCheck : getBoundsToCheck(bitmap)) {
                for (int x = boundsToCheck.left; x < boundsToCheck.right; x++) {
                    for (int y = boundsToCheck.top; y < boundsToCheck.bottom; y++) {
                        int color = bitmap.getPixel(x + OFFSET_X, y + OFFSET_Y);
                        if (getExpectedColor(x, y).matchesColor(color)) {
                            numMatchingPixels++;
                        } else if (DEBUG && mLogWhenNoMatch && numErrorsLogged < 100) {
                            // We don't want to spam the logcat with errors if something is really
                            // broken. Only log the first 100 errors.
                            PixelColor expected = getExpectedColor(x, y);
                            int expectedColor = Color.argb(expected.mAlpha, expected.mRed,
                                    expected.mGreen, expected.mBlue);
                            Log.e(TAG, String.format(
                                    "Failed to match (%d, %d) color=0x%08X expected=0x%08X", x, y,
                                    color, expectedColor));
                            numErrorsLogged++;
                        }
                    }
                }
            }
            return numMatchingPixels;
        }

        public abstract boolean checkPixels(int matchingPixelCount, int width, int height);

        public List<Rect> getBoundsToCheck(Bitmap bitmap) {
            return List.of(new Rect(1, 1, DEFAULT_LAYOUT_WIDTH - 1, DEFAULT_LAYOUT_HEIGHT - 1));
        }

        public PixelColor getExpectedColor(int x, int y) {
            return mPixelColor;
        }
    }

    public static class SurfaceHolderCallback implements SurfaceHolder.Callback {
        private final SurfaceHolder.Callback mTestCallback;
        private final CountDownLatch mSurfaceCreatedLatch;
        private final AttachedSurfaceControl mAttachedSurfaceControl;

        public SurfaceHolderCallback(SurfaceHolder.Callback callback, CountDownLatch readyFence,
                AttachedSurfaceControl attachedSurfaceControl) {
            mTestCallback = callback;
            mSurfaceCreatedLatch = readyFence;
            mAttachedSurfaceControl = attachedSurfaceControl;
        }

        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            mTestCallback.surfaceCreated(holder);
            try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
                transaction.addTransactionCommittedListener(Runnable::run,
                        mSurfaceCreatedLatch::countDown);
                mAttachedSurfaceControl.applyTransactionOnDraw(transaction);
            }
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width,
                int height) {
            mTestCallback.surfaceChanged(holder, format, width, height);
        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            mTestCallback.surfaceDestroyed(holder);
        }
    }

    private void saveFailureCapture(Bitmap failFrame, TestName name) {
        String directoryName = Environment.getExternalStorageDirectory()
                + "/" + STORAGE_DIR
                + "/" + getClass().getSimpleName()
                + "/" + name.getMethodName();
        File testDirectory = new File(directoryName);
        if (testDirectory.exists()) {
            String[] children = testDirectory.list();
            for (String file : children) {
                new File(testDirectory, file).delete();
            }
        } else {
            testDirectory.mkdirs();
        }

        String bitmapName = "frame.png";
        Log.d(TAG, "Saving file : " + bitmapName + " in directory : " + directoryName);

        File file = new File(directoryName, bitmapName);
        try (FileOutputStream fileStream = new FileOutputStream(file)) {
            failFrame.compress(Bitmap.CompressFormat.PNG, 85, fileStream);
            fileStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class InsetsAnimationCallback extends WindowInsetsAnimation.Callback {
        private CountDownLatch mLatch = new CountDownLatch(1);

        private InsetsAnimationCallback() {
            super(DISPATCH_MODE_CONTINUE_ON_SUBTREE);
        }

        @Override
        public WindowInsets onProgress(
                WindowInsets insets, List<WindowInsetsAnimation> runningAnimations) {
            return insets;
        }

        @Override
        public void onEnd(WindowInsetsAnimation animation) {
            mLatch.countDown();
        }

        private boolean waitForInsetsAnimation() {
            try {
                return mLatch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // Should never happen
                throw new RuntimeException(e);
            }
        }
    }
}
