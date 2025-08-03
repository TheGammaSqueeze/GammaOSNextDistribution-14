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

package android.display.cts;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowInsets.Type.systemBars;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.util.Log;
import android.view.SurfaceControl;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.cts.surfacevalidator.PixelChecker;
import android.view.cts.surfacevalidator.PixelColor;
import android.view.cts.surfacevalidator.SaveBitmapHelper;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class DisplayManagerTest {
    private static final String TAG = "DisplayManagerTest";
    @Rule
    public TestName mTestName = new TestName();

    private static final int MAX_RETRIES = 3;

    @Rule
    public ActivityScenarioRule<TestActivity> mActivityRule =
            new ActivityScenarioRule<>(TestActivity.class);

    private TestActivity mActivity;
    private Instrumentation mInstrumentation;

    private int mNumRetries;

    final HandlerThread mWorkerThread = new HandlerThread("DisplayManagerTest");

    private VirtualDisplay mVirtualDisplay;

    @Before
    public void setUp() {
        mActivityRule.getScenario().onActivity(activity -> mActivity = activity);
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mNumRetries = 0;
    }

    @After
    public void tearDown() {
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        mWorkerThread.quitSafely();
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
    }

    @Test
    public void testCreateVirtualDisplayFromShell() throws InterruptedException {
        // b/317812433: Disable test until test can be fixed for specific devices
        assumeTrue(false);

        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity();
        mActivity.waitForReady();
        mInstrumentation.waitForIdleSync();

        mWorkerThread.start();

        Rect displayBounds = mActivity.getSystemService(WindowManager.class)
                .getCurrentWindowMetrics().getBounds();
        final CountDownLatch doneLatch = new CountDownLatch(1);
        final AtomicBoolean results = new AtomicBoolean(false);

        ImageReader imageReader = ImageReader.newInstance(displayBounds.width(),
                displayBounds.height(), HardwareBuffer.RGBA_8888, 1,
                HardwareBuffer.USAGE_GPU_COLOR_OUTPUT | HardwareBuffer.USAGE_CPU_READ_OFTEN
                        | HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
        final Rect boundsToCheck = mActivity.getBoundsToCheck();
        imageReader.setOnImageAvailableListener(new OnImageAvailableListener(image -> {
            // It's possible we get another image available before we finish the test. In that case
            // ignore the image and just return.
            if (doneLatch.getCount() == 0) {
                return;
            }
            int size = boundsToCheck.width() * boundsToCheck.height();
            int matchingPixels = PixelChecker.getNumMatchingPixels(new PixelColor(Color.RED),
                    image.getPlanes()[0], boundsToCheck);
            boolean success = matchingPixels >= size - 100 && matchingPixels <= size + 100;
            if (!success) {
                Log.w(TAG, "boundsToCheck=" + boundsToCheck + " expectedPixels=" + size
                        + " matchingPixels=" + matchingPixels);

                // Allow for retries because the recording is racing the launching animation.
                // There's a chance the system is still running the launch animation which would
                // cause the recording pixels to not match. Allow a few frames to be captured to
                // ensure the Activity is fully done animating.
                Bitmap capture = Bitmap.wrapHardwareBuffer(
                                image.getHardwareBuffer(), null)
                        .copy(Bitmap.Config.ARGB_8888, false);
                SaveBitmapHelper.saveBitmap(capture, getClass(), mTestName, "failedImage");
            }

            if (success || mNumRetries >= MAX_RETRIES) {
                results.set(success);
                imageReader.setOnImageAvailableListener(null, null);
                doneLatch.countDown();
            }
            mNumRetries++;
        }), new Handler(mWorkerThread.getLooper()));

        mVirtualDisplay = DisplayManager.createVirtualDisplay("Test",
                displayBounds.width(), displayBounds.height(), DEFAULT_DISPLAY,
                imageReader.getSurface());

        doneLatch.await();
        imageReader.close();

        assertTrue("Failed to successfully record display content", results.get());
    }

    @Test
    public void createVirtualDisplayNoPermission() {
        Exception exception = null;
        try {
            mVirtualDisplay = DisplayManager.createVirtualDisplay("Test", 100, 100,
                    DEFAULT_DISPLAY, null);
        } catch (RuntimeException e) {
            exception = e;
        }
        assertTrue(exception instanceof SecurityException);
    }


    private static class OnImageAvailableListener implements ImageReader.OnImageAvailableListener {
        private final Consumer<Image> mImageConsumer;

        OnImageAvailableListener(Consumer<Image> imageConsumer) {
            mImageConsumer = imageConsumer;
        }

        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireNextImage();
            if (image == null) {
                return;
            }
            mImageConsumer.accept(image);
            image.close();
        }
    }

    public static class TestActivity extends Activity {
        private final CountDownLatch mReadyLatch = new CountDownLatch(2);
        private FrameLayout mFrameLayout;

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mFrameLayout = new FrameLayout(this);
            mFrameLayout.setBackgroundColor(Color.RED);
            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);

            setContentView(mFrameLayout, layoutParams);

            // Prevent certain devices from adding a left and right border
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

            mFrameLayout.getViewTreeObserver().addOnWindowAttachListener(
                    new ViewTreeObserver.OnWindowAttachListener() {
                        @Override
                        public void onWindowAttached() {
                            SurfaceControl.Transaction commitTransaction =
                                    new SurfaceControl.Transaction();
                            commitTransaction.addTransactionCommittedListener(getMainExecutor(),
                                    mReadyLatch::countDown);
                            mFrameLayout.getRootSurfaceControl().applyTransactionOnDraw(
                                    commitTransaction);
                        }

                        @Override
                        public void onWindowDetached() {

                        }
                    });
        }

        @Override
        public void onEnterAnimationComplete() {
            mReadyLatch.countDown();
        }

        public void waitForReady() throws InterruptedException {
            mReadyLatch.await();
        }

        public Rect getBoundsToCheck() {
            int testAreaWidth = mFrameLayout.getWidth();
            int testAreaHeight = mFrameLayout.getHeight();

            Insets systemBarInsets = getWindow()
                    .getDecorView()
                    .getRootWindowInsets()
                    .getInsets(systemBars());

            return new Rect(systemBarInsets.left, systemBarInsets.top,
                    testAreaWidth - systemBarInsets.right,
                    testAreaHeight - systemBarInsets.bottom);
        }
    }
}
