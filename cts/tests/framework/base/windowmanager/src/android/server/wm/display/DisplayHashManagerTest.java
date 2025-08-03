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

package android.server.wm.display;

import static android.server.wm.UiDeviceUtils.pressUnlockButton;
import static android.server.wm.UiDeviceUtils.pressWakeupButton;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
import static android.view.displayhash.DisplayHashResultCallback.DISPLAY_HASH_ERROR_INVALID_BOUNDS;
import static android.view.displayhash.DisplayHashResultCallback.DISPLAY_HASH_ERROR_INVALID_HASH_ALGORITHM;
import static android.view.displayhash.DisplayHashResultCallback.DISPLAY_HASH_ERROR_NOT_VISIBLE_ON_SCREEN;
import static android.view.displayhash.DisplayHashResultCallback.DISPLAY_HASH_ERROR_TOO_MANY_REQUESTS;
import static android.widget.LinearLayout.VERTICAL;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.server.wm.WindowManagerState;
import android.server.wm.WindowManagerStateHelper;
import android.service.displayhash.DisplayHashParams;
import android.util.Size;
import android.view.Gravity;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.displayhash.DisplayHash;
import android.view.displayhash.DisplayHashManager;
import android.view.displayhash.DisplayHashResultCallback;
import android.view.displayhash.VerifiedDisplayHash;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.test.filters.FlakyTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Presubmit
public class DisplayHashManagerTest {
    private static final int WAIT_TIME_S = 5;
    private static final int TIMEOUT_MS = 1000;
    private static final int SLEEP_TIMEOUT_MS = 200;

    private final Point mCenter = new Point();
    private final Point mTestViewSize = new Point(200, 300);

    private Instrumentation mInstrumentation;
    private RelativeLayout mMainView;
    private TestActivity mActivity;

    private View mTestView;

    private DisplayHashManager mDisplayHashManager;
    private String mPhashAlgorithm;

    private Executor mExecutor;

    private SyncDisplayHashResultCallback mSyncDisplayHashResultCallback;

    protected WindowManagerStateHelper mWmState = new WindowManagerStateHelper();

    @Rule
    public ActivityTestRule<TestActivity> mActivityRule =
            new ActivityTestRule<>(TestActivity.class);

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        final Context context = mInstrumentation.getContext();
        final KeyguardManager km = context.getSystemService(KeyguardManager.class);
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClass(context, TestActivity.class);
        mActivity = mActivityRule.getActivity();

        if (km != null && km.isKeyguardLocked() || !Objects.requireNonNull(
                context.getSystemService(PowerManager.class)).isInteractive()) {
            pressWakeupButton();
            pressUnlockButton();
        }

        mActivity.runOnUiThread(() -> {
            mMainView = new RelativeLayout(mActivity);
            mActivity.setContentView(mMainView);
        });
        mInstrumentation.waitForIdleSync();
        mActivity.runOnUiThread(() -> {
            mCenter.set((mMainView.getWidth() - mTestViewSize.x) / 2,
                    (mMainView.getHeight() - mTestViewSize.y) / 2);
        });
        mDisplayHashManager = context.getSystemService(DisplayHashManager.class);

        Set<String> algorithms = mDisplayHashManager.getSupportedHashAlgorithms();
        assertNotNull(algorithms);
        assertNotEquals(0, algorithms.size());
        for (String algorithm : algorithms) {
            if ("pHash".equalsIgnoreCase(algorithm)) {
                mPhashAlgorithm = algorithm;
                break;
            }
        }
        assertNotNull(mPhashAlgorithm);

        mExecutor = context.getMainExecutor();
        mSyncDisplayHashResultCallback = new SyncDisplayHashResultCallback();
        SystemUtil.runWithShellPermissionIdentity(
                () -> mDisplayHashManager.setDisplayHashThrottlingEnabled(false));
    }

    @After
    public void tearDown() {
        SystemUtil.runWithShellPermissionIdentity(
                () -> mDisplayHashManager.setDisplayHashThrottlingEnabled(true));
    }

    @FlakyTest(bugId = 292291447)
    @Test
    public void testGenerateAndVerifyDisplayHash() {
        setupChildView();

        // A solid color image has expected hash of all 0s
        byte[] expectedImageHash = new byte[8];

        DisplayHash displayHash = generateDisplayHash(null);
        VerifiedDisplayHash verifiedDisplayHash = mDisplayHashManager.verifyDisplayHash(
                displayHash);
        assertNotNull(verifiedDisplayHash);

        assertEquals(mTestViewSize.x, verifiedDisplayHash.getBoundsInWindow().width());
        assertEquals(mTestViewSize.y, verifiedDisplayHash.getBoundsInWindow().height());
        assertArrayEquals(expectedImageHash, verifiedDisplayHash.getImageHash());
    }

    @Test
    public void testGenerateAndVerifyDisplayHash_BoundsInView() {
        setupChildView();

        Rect bounds = new Rect(10, 20, mTestViewSize.x / 2, mTestViewSize.y / 2);
        DisplayHash displayHash = generateDisplayHash(new Rect(bounds));

        VerifiedDisplayHash verifiedDisplayHash = mDisplayHashManager.verifyDisplayHash(
                displayHash);
        assertNotNull(verifiedDisplayHash);
        assertEquals(bounds.width(), verifiedDisplayHash.getBoundsInWindow().width());
        assertEquals(bounds.height(), verifiedDisplayHash.getBoundsInWindow().height());
    }

    @Test
    public void testGenerateAndVerifyDisplayHash_EmptyBounds() {
        setupChildView();

        mTestView.generateDisplayHash(mPhashAlgorithm, new Rect(), mExecutor,
                mSyncDisplayHashResultCallback);

        int errorCode = mSyncDisplayHashResultCallback.getError();
        assertEquals(DISPLAY_HASH_ERROR_INVALID_BOUNDS, errorCode);
    }

    @Test
    public void testGenerateAndVerifyDisplayHash_BoundsBiggerThanView() {
        setupChildView();

        Rect bounds = new Rect(0, 0, mTestViewSize.x + 100, mTestViewSize.y + 100);

        DisplayHash displayHash = generateDisplayHash(new Rect(bounds));

        VerifiedDisplayHash verifiedDisplayHash = mDisplayHashManager.verifyDisplayHash(
                displayHash);
        assertNotNull(verifiedDisplayHash);
        assertEquals(mTestViewSize.x, verifiedDisplayHash.getBoundsInWindow().width());
        assertEquals(mTestViewSize.y, verifiedDisplayHash.getBoundsInWindow().height());
    }

    @Test
    public void testGenerateDisplayHash_BoundsOutOfView() {
        setupChildView();

        Rect bounds = new Rect(mTestViewSize.x + 1, mTestViewSize.y + 1, mTestViewSize.x + 100,
                mTestViewSize.y + 100);

        mTestView.generateDisplayHash(mPhashAlgorithm, new Rect(bounds),
                mExecutor, mSyncDisplayHashResultCallback);
        int errorCode = mSyncDisplayHashResultCallback.getError();
        assertEquals(DISPLAY_HASH_ERROR_NOT_VISIBLE_ON_SCREEN, errorCode);
    }

    @Test
    public void testGenerateDisplayHash_ViewOffscreen() {
        final CountDownLatch committedCallbackLatch = new CountDownLatch(1);
        final SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        t.addTransactionCommittedListener(mExecutor, committedCallbackLatch::countDown);

        mInstrumentation.runOnMainSync(() -> {
            final RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(mTestViewSize.x,
                    mTestViewSize.y);
            mTestView = new View(mActivity);
            mTestView.setBackgroundColor(Color.BLUE);
            mTestView.setX(-mTestViewSize.x);

            mMainView.addView(mTestView, p);
            mMainView.getRootSurfaceControl().applyTransactionOnDraw(t);
        });
        mInstrumentation.waitForIdleSync();
        try {
            committedCallbackLatch.await(WAIT_TIME_S, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        }

        mTestView.generateDisplayHash(mPhashAlgorithm, null, mExecutor,
                mSyncDisplayHashResultCallback);

        int errorCode = mSyncDisplayHashResultCallback.getError();
        assertEquals(DISPLAY_HASH_ERROR_NOT_VISIBLE_ON_SCREEN, errorCode);
    }

    @Test
    public void testGenerateDisplayHash_WindowOffscreen() {
        final WindowManager wm = mActivity.getWindowManager();
        final WindowManager.LayoutParams windowParams = new WindowManager.LayoutParams();

        final CountDownLatch committedCallbackLatch = new CountDownLatch(1);
        final SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        t.addTransactionCommittedListener(mExecutor, committedCallbackLatch::countDown);
        mInstrumentation.runOnMainSync(() -> {
            mMainView = new RelativeLayout(mActivity);
            windowParams.width = mTestViewSize.x;
            windowParams.height = mTestViewSize.y;
            windowParams.gravity = Gravity.LEFT | Gravity.TOP;
            windowParams.flags = FLAG_LAYOUT_NO_LIMITS;
            mActivity.addWindow(mMainView, windowParams);

            mMainView.getViewTreeObserver().addOnWindowAttachListener(
                    new ViewTreeObserver.OnWindowAttachListener() {
                        @Override
                        public void onWindowAttached() {
                            final RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(
                                    mTestViewSize.x,
                                    mTestViewSize.y);
                            mTestView = new View(mActivity);
                            mTestView.setBackgroundColor(Color.BLUE);
                            mMainView.addView(mTestView, p);
                            mMainView.getRootSurfaceControl().applyTransactionOnDraw(t);
                        }

                        @Override
                        public void onWindowDetached() {
                        }
                    });
        });
        mInstrumentation.waitForIdleSync();
        try {
            committedCallbackLatch.await(WAIT_TIME_S, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        }

        waitForAllActivitiesResumed();

        generateDisplayHash(null);

        mInstrumentation.runOnMainSync(() -> {
            int[] mainViewLocationOnScreen = new int[2];
            mMainView.getLocationOnScreen(mainViewLocationOnScreen);

            windowParams.x = -mTestViewSize.x - mainViewLocationOnScreen[0];
            wm.updateViewLayout(mMainView, windowParams);
        });
        mInstrumentation.waitForIdleSync();

        mSyncDisplayHashResultCallback.reset();
        mTestView.generateDisplayHash(mPhashAlgorithm, null, mExecutor,
                mSyncDisplayHashResultCallback);

        int errorCode = mSyncDisplayHashResultCallback.getError();
        assertEquals(DISPLAY_HASH_ERROR_NOT_VISIBLE_ON_SCREEN, errorCode);
    }

    @Test
    public void testGenerateDisplayHash_InvalidHashAlgorithm() {
        setupChildView();

        mTestView.generateDisplayHash("fake hash", null, mExecutor,
                mSyncDisplayHashResultCallback);
        int errorCode = mSyncDisplayHashResultCallback.getError();
        assertEquals(DISPLAY_HASH_ERROR_INVALID_HASH_ALGORITHM, errorCode);
    }

    @Test
    public void testVerifyDisplayHash_ValidDisplayHash() {
        setupChildView();

        DisplayHash displayHash = generateDisplayHash(null);
        VerifiedDisplayHash verifiedDisplayHash = mDisplayHashManager.verifyDisplayHash(
                displayHash);

        assertNotNull(verifiedDisplayHash);
        assertEquals(displayHash.getTimeMillis(), verifiedDisplayHash.getTimeMillis());
        assertEquals(displayHash.getBoundsInWindow(), verifiedDisplayHash.getBoundsInWindow());
        assertEquals(displayHash.getHashAlgorithm(), verifiedDisplayHash.getHashAlgorithm());
        assertArrayEquals(displayHash.getImageHash(), verifiedDisplayHash.getImageHash());
    }

    @Test
    public void testVerifyDisplayHash_InvalidDisplayHash() {
        setupChildView();

        DisplayHash displayHash = generateDisplayHash(null);
        DisplayHash fakeDisplayHash = new DisplayHash(
                displayHash.getTimeMillis(), displayHash.getBoundsInWindow(),
                displayHash.getHashAlgorithm(), new byte[32], displayHash.getHmac());
        VerifiedDisplayHash verifiedDisplayHash = mDisplayHashManager.verifyDisplayHash(
                fakeDisplayHash);

        assertNull(verifiedDisplayHash);
    }

    @Test
    public void testVerifiedDisplayHash() {
        long timeMillis = 1000;
        Rect boundsInWindow = new Rect(0, 0, 50, 100);
        String hashAlgorithm = "hashAlgorithm";
        byte[] imageHash = new byte[]{2, 4, 1, 5, 6, 2};
        VerifiedDisplayHash verifiedDisplayHash = new VerifiedDisplayHash(timeMillis,
                boundsInWindow, hashAlgorithm, imageHash);

        assertEquals(timeMillis, verifiedDisplayHash.getTimeMillis());
        assertEquals(boundsInWindow, verifiedDisplayHash.getBoundsInWindow());
        assertEquals(hashAlgorithm, verifiedDisplayHash.getHashAlgorithm());
        assertArrayEquals(imageHash, verifiedDisplayHash.getImageHash());
    }

    @Test
    public void testGenerateDisplayHash_Throttle() {
        SystemUtil.runWithShellPermissionIdentity(
                () -> mDisplayHashManager.setDisplayHashThrottlingEnabled(true));

        setupChildView();

        mTestView.generateDisplayHash(mPhashAlgorithm, null, mExecutor,
                mSyncDisplayHashResultCallback);
        mSyncDisplayHashResultCallback.getDisplayHash();
        mSyncDisplayHashResultCallback.reset();
        // Generate a second display hash right away.
        mTestView.generateDisplayHash(mPhashAlgorithm, null, mExecutor,
                mSyncDisplayHashResultCallback);
        int errorCode = mSyncDisplayHashResultCallback.getError();
        assertEquals(DISPLAY_HASH_ERROR_TOO_MANY_REQUESTS, errorCode);
    }

    @Test
    @FlakyTest(bugId = 292291447)
    public void testGenerateAndVerifyDisplayHash_MultiColor() {
        final CountDownLatch committedCallbackLatch = new CountDownLatch(1);
        final SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        t.addTransactionCommittedListener(mExecutor, committedCallbackLatch::countDown);
        mInstrumentation.runOnMainSync(() -> {
            final RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(mTestViewSize.x,
                    mTestViewSize.y);
            LinearLayout linearLayout = new LinearLayout(mActivity);
            linearLayout.setOrientation(VERTICAL);
            LinearLayout.LayoutParams blueParams = new LinearLayout.LayoutParams(mTestViewSize.x,
                    mTestViewSize.y / 2);
            View blueView = new View(mActivity);
            blueView.setBackgroundColor(Color.BLUE);
            LinearLayout.LayoutParams redParams = new LinearLayout.LayoutParams(mTestViewSize.x,
                    mTestViewSize.y / 2);
            View redView = new View(mActivity);
            redView.setBackgroundColor(Color.RED);

            linearLayout.addView(blueView, blueParams);
            linearLayout.addView(redView, redParams);
            mTestView = linearLayout;

            mTestView.setX(mCenter.x);
            mTestView.setY(mCenter.y);
            mMainView.addView(mTestView, p);
            mMainView.getRootSurfaceControl().applyTransactionOnDraw(t);
        });
        mInstrumentation.waitForIdleSync();
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
        try {
            committedCallbackLatch.await(WAIT_TIME_S, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        }

        ComponentName componentName = ComponentName.unflattenFromString(
                "android.server.wm/android.server.wm.display.DisplayHashManagerTest$TestActivity");
        waitForActivityResumed(TIMEOUT_MS, componentName);

        byte[] expectedImageHash = new byte[]{-1, -1, 127, -1, -1, -1, 127, 127};

        DisplayHash displayHash = generateDisplayHash(null);
        VerifiedDisplayHash verifiedDisplayHash = mDisplayHashManager.verifyDisplayHash(
                displayHash);
        assertNotNull(verifiedDisplayHash);

        assertEquals(mTestViewSize.x, verifiedDisplayHash.getBoundsInWindow().width());
        assertEquals(mTestViewSize.y, verifiedDisplayHash.getBoundsInWindow().height());
        assertArrayEquals(expectedImageHash, verifiedDisplayHash.getImageHash());
    }

    private void waitForActivityResumed(int timeoutMs, ComponentName componentName) {
        long endTime = System.currentTimeMillis() + timeoutMs;
        while (endTime > System.currentTimeMillis()) {
            mWmState.computeState();
            if (mWmState.hasActivityState(componentName, STATE_RESUMED)) {
                SystemClock.sleep(SLEEP_TIMEOUT_MS);
                mWmState.computeState();
                break;
            }
            SystemClock.sleep(SLEEP_TIMEOUT_MS);
            mWmState.computeState();
        }
    }

    @Test
    public void testDisplayHashParams() {
        int width = 10;
        int height = 20;
        boolean isGrayscale = true;
        DisplayHashParams displayHashParams = new DisplayHashParams.Builder()
                .setBufferSize(width, height)
                .setGrayscaleBuffer(isGrayscale)
                .build();

        Size bufferSize = displayHashParams.getBufferSize();
        assertEquals(width, bufferSize.getWidth());
        assertEquals(height, bufferSize.getHeight());
        assertEquals(isGrayscale, displayHashParams.isGrayscaleBuffer());
    }

    private DisplayHash generateDisplayHash(Rect bounds) {
        mTestView.generateDisplayHash(mPhashAlgorithm, bounds, mExecutor,
                mSyncDisplayHashResultCallback);
        DisplayHash displayHash = mSyncDisplayHashResultCallback.getDisplayHash();

        assertNotNull(displayHash);
        return displayHash;
    }

    private void setupChildView() {
        final CountDownLatch committedCallbackLatch = new CountDownLatch(1);
        final SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        t.addTransactionCommittedListener(mExecutor, committedCallbackLatch::countDown);

        mInstrumentation.runOnMainSync(() -> {
            final RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(mTestViewSize.x,
                    mTestViewSize.y);
            mTestView = new View(mActivity);
            mTestView.setX(mCenter.x);
            mTestView.setY(mCenter.y);
            mTestView.setBackgroundColor(Color.BLUE);
            mMainView.addView(mTestView, p);
            mMainView.getRootSurfaceControl().applyTransactionOnDraw(t);
        });
        mInstrumentation.waitForIdleSync();
        try {
            committedCallbackLatch.await(WAIT_TIME_S, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        }
        waitForAllActivitiesResumed();
    }

    public static class TestActivity extends Activity {
        private final ArrayList<View> mViews = new ArrayList<>();

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
        }

        void addWindow(View view, WindowManager.LayoutParams attrs) {
            getWindowManager().addView(view, attrs);
            mViews.add(view);
        }

        void removeAllWindows() {
            for (View view : mViews) {
                getWindowManager().removeViewImmediate(view);
            }
            mViews.clear();
        }

        @Override
        protected void onPause() {
            super.onPause();
            removeAllWindows();
        }
    }

    private static class SyncDisplayHashResultCallback implements DisplayHashResultCallback {
        private static final int SCREENSHOT_WAIT_TIME_S = 1;
        private DisplayHash mDisplayHash;
        private int mError;
        private CountDownLatch mCountDownLatch = new CountDownLatch(1);

        public void reset() {
            mCountDownLatch = new CountDownLatch(1);
        }

        public DisplayHash getDisplayHash() {
            try {
                mCountDownLatch.await(SCREENSHOT_WAIT_TIME_S, TimeUnit.SECONDS);
            } catch (Exception e) {
            }
            return mDisplayHash;
        }

        public int getError() {
            try {
                mCountDownLatch.await(SCREENSHOT_WAIT_TIME_S, TimeUnit.SECONDS);
            } catch (Exception e) {
            }
            return mError;
        }

        @Override
        public void onDisplayHashResult(@NonNull DisplayHash displayHash) {
            mDisplayHash = displayHash;
            mCountDownLatch.countDown();
        }

        @Override
        public void onDisplayHashError(int errorCode) {
            mError = errorCode;
            mCountDownLatch.countDown();
        }
    }

    /**
     * Waits for all activities to be resumed since image hash could be calculated before the
     * activity is resumed.
     */
    private void waitForAllActivitiesResumed() {
        mWmState.waitForWithAmState(WindowManagerState::allActivitiesResumed,
                "All activities should be resumed");
    }
}
