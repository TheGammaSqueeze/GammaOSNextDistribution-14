/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.accessibilityservice.cts;

import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.launchActivityAndWaitForItToBeOnscreen;
import static android.accessibilityservice.cts.utils.AsyncUtils.DEFAULT_TIMEOUT_MS;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.cts.activities.AccessibilityTestActivity;
import android.accessibilityservice.cts.utils.DisplayUtils;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.server.wm.CtsWindowInfoUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tests that AccessibilityNodeInfos from an embedded hierarchy that is present to another
 * hierarchy are properly populated.
 */
@CddTest(requirements = {"3.10/C-1-1,C-1-2"})
@RunWith(AndroidJUnit4.class)
@Presubmit
public class AccessibilityEmbeddedHierarchyTest {
    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;

    private final ActivityTestRule<AccessibilityEmbeddedHierarchyActivity> mActivityRule =
            new ActivityTestRule<>(AccessibilityEmbeddedHierarchyActivity.class, false, false);

    private static final String HOST_PARENT_RESOURCE_NAME =
            "android.accessibilityservice.cts:id/host_surfaceview";
    private static final String EMBEDDED_VIEW_RESOURCE_NAME =
            "android.accessibilityservice.cts:id/embedded_editText";

    private final AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    private AccessibilityEmbeddedHierarchyActivity mActivity;

    @Rule
    public final RuleChain mRuleChain = RuleChain
            .outerRule(mActivityRule)
            .around(mDumpOnFailureRule);

    @BeforeClass
    public static void oneTimeSetup() {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sUiAutomation = sInstrumentation.getUiAutomation();
        AccessibilityServiceInfo info = sUiAutomation.getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
        sUiAutomation.setServiceInfo(info);
    }

    @AfterClass
    public static void postTestTearDown() {
        sUiAutomation.destroy();
    }

    @Before
    public void setUp() throws Throwable {
        mActivity = launchActivityAndWaitForItToBeOnscreen(sInstrumentation, sUiAutomation,
                mActivityRule);
        mActivity.waitForEmbeddedHierarchy();
    }

    @Test
    public void testEmbeddedViewCanBeFound() {
        final AccessibilityNodeInfo target =
                findEmbeddedAccessibilityNodeInfo(sUiAutomation.getRootInActiveWindow());
        assertThat(target).isNotNull();
    }

    @Test
    public void testEmbeddedView_PerformActionTransfersWindowInputFocus() {
        final View hostView = mActivity.mInputFocusableView;
        final View embeddedView = mActivity.mViewHost.getView();
        final AccessibilityNodeInfo embeddedNode =
                findEmbeddedAccessibilityNodeInfo(sUiAutomation.getRootInActiveWindow());
        assertThat(hostView.isFocusable()).isTrue();
        assertThat(embeddedView.isFocusable()).isTrue();

        // Start by ensuring the host-side view has window input focus.
        hostView.performClick();
        assertThat(CtsWindowInfoUtils.waitForWindowFocus(hostView, true)).isTrue();
        assertThat(embeddedView.hasWindowFocus()).isFalse();

        // ACTION_ACCESSIBILITY_FOCUS should not transfer window input focus.
        embeddedNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        assertThat(CtsWindowInfoUtils.waitForWindowFocus(embeddedView, true)).isFalse();

        // Other actions like ACTION_CLICK should transfer window input focus.
        embeddedNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        assertThat(CtsWindowInfoUtils.waitForWindowFocus(embeddedView, true)).isTrue();
        assertThat(hostView.hasWindowFocus()).isFalse();
    }

    @Test
    public void testEmbeddedViewCanFindItsHostParent() {
        final AccessibilityNodeInfo target =
                findEmbeddedAccessibilityNodeInfo(sUiAutomation.getRootInActiveWindow());
        final AccessibilityNodeInfo parent = target.getParent();
        assertThat(parent.getViewIdResourceName()).isEqualTo(HOST_PARENT_RESOURCE_NAME);
    }

    @Test
    public void testEmbeddedViewHasCorrectBound() {
        final AccessibilityNodeInfo target =
                findEmbeddedAccessibilityNodeInfo(sUiAutomation.getRootInActiveWindow());
        final AccessibilityNodeInfo parent = target.getParent();

        final Rect hostViewBoundsInScreen = new Rect();
        final Rect embeddedViewBoundsInScreen = new Rect();
        parent.refresh();
        target.refresh();
        parent.getBoundsInScreen(hostViewBoundsInScreen);
        target.getBoundsInScreen(embeddedViewBoundsInScreen);

        assertWithMessage(
                "hostViewBoundsInScreen" + hostViewBoundsInScreen.toShortString()
                        + " doesn't contain embeddedViewBoundsInScreen"
                        + embeddedViewBoundsInScreen.toShortString()).that(
                DisplayUtils.fuzzyBoundsInScreenContains(
                        hostViewBoundsInScreen, embeddedViewBoundsInScreen)).isTrue();
    }

    @Test
    public void testEmbeddedViewHasCorrectBoundAfterHostViewMove() throws TimeoutException {
        final AccessibilityNodeInfo target =
                findEmbeddedAccessibilityNodeInfo(sUiAutomation.getRootInActiveWindow());
        final AccessibilityNodeInfo parent = target.getParent();

        final Rect hostViewBoundsInScreen = new Rect();
        final Rect newEmbeddedViewBoundsInScreen = new Rect();
        final Rect oldEmbeddedViewBoundsInScreen = new Rect();
        target.refresh();
        target.getBoundsInScreen(oldEmbeddedViewBoundsInScreen);

        // Move the host's SurfaceView away from (0,0).
        int moveAmountPx = mActivity.getResources().getDimensionPixelSize(
                R.dimen.embedded_hierarchy_embedded_layout_movement_size);
        mActivity.moveSurfaceViewLayoutPosition(moveAmountPx, moveAmountPx, false);

        parent.refresh();
        target.refresh();
        parent.getBoundsInScreen(hostViewBoundsInScreen);
        target.getBoundsInScreen(newEmbeddedViewBoundsInScreen);

        assertWithMessage(
                "hostViewBoundsInScreen" + hostViewBoundsInScreen.toShortString()
                        + " doesn't contain newEmbeddedViewBoundsInScreen"
                        + newEmbeddedViewBoundsInScreen.toShortString()).that(
                DisplayUtils.fuzzyBoundsInScreenContains(hostViewBoundsInScreen,
                        newEmbeddedViewBoundsInScreen)).isTrue();
        assertWithMessage(
                "newEmbeddedViewBoundsInScreen" + newEmbeddedViewBoundsInScreen.toShortString()
                        + " shouldn't be the same with oldEmbeddedViewBoundsInScreen"
                        + oldEmbeddedViewBoundsInScreen.toShortString()).that(
                newEmbeddedViewBoundsInScreen.equals(oldEmbeddedViewBoundsInScreen)).isFalse();
    }

    @Test
    public void testEmbeddedViewIsInvisibleAfterMovingOutOfScreen() throws TimeoutException {
        final AccessibilityNodeInfo target =
                findEmbeddedAccessibilityNodeInfo(sUiAutomation.getRootInActiveWindow());
        assertWithMessage("Embedded view should be visible at beginning.").that(
                target.isVisibleToUser()).isTrue();

        // Move Host SurfaceView out of screen
        final Point screenSize = getScreenSize();
        mActivity.moveSurfaceViewLayoutPosition(screenSize.x * 2, screenSize.y * 2, true);

        target.refresh();
        assertWithMessage("Embedded view should be invisible after moving out of screen.").that(
                target.isVisibleToUser()).isFalse();
    }

    private AccessibilityNodeInfo findEmbeddedAccessibilityNodeInfo(AccessibilityNodeInfo root) {
        final int childCount = root.getChildCount();
        for (int i = 0; i < childCount; i++) {
            final AccessibilityNodeInfo info = root.getChild(i);
            if (info == null) {
                continue;
            }
            if (EMBEDDED_VIEW_RESOURCE_NAME.equals(info.getViewIdResourceName())) {
                return info;
            }
            if (info.getChildCount() != 0) {
                return findEmbeddedAccessibilityNodeInfo(info);
            }
        }
        return null;
    }

    private static AccessibilityNodeInfo findHostAccessibilityNodeInfo(
            AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes =
                root.findAccessibilityNodeInfosByViewId(HOST_PARENT_RESOURCE_NAME);
        return nodes.isEmpty() ? null : nodes.get(0);
    }

    private Point getScreenSize() {
        final DisplayManager dm = sInstrumentation.getContext().getSystemService(
                DisplayManager.class);
        final Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);
        final DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);
        return new Point(metrics.widthPixels, metrics.heightPixels);
    }

    /**
     * This class is an placeholder {@link android.app.Activity} used to perform embedded hierarchy
     * testing of the accessibility feature by interaction with the UI widgets.
     */
    public static class AccessibilityEmbeddedHierarchyActivity extends
            AccessibilityTestActivity implements SurfaceHolder.Callback {
        private final CountDownLatch mCountDownLatch = new CountDownLatch(1);

        private SurfaceView mSurfaceView;
        private View mInputFocusableView;
        private SurfaceControlViewHost mViewHost;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.accessibility_embedded_hierarchy_test_host_side);
            mSurfaceView = findViewById(R.id.host_surfaceview);
            mSurfaceView.getHolder().addCallback(this);
            mInputFocusableView = findViewById(R.id.host_editText);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            mViewHost = new SurfaceControlViewHost(this, this.getDisplay(),
                    mSurfaceView.getHostToken());

            mSurfaceView.setChildSurfacePackage(mViewHost.getSurfacePackage());

            View layout = getLayoutInflater().inflate(
                    R.layout.accessibility_embedded_hierarchy_test_embedded_side, null);
            final int viewSizePx = getResources().getDimensionPixelSize(
                    R.dimen.embedded_hierarchy_embedded_layout_size);
            mViewHost.setView(layout, viewSizePx, viewSizePx);
            mCountDownLatch.countDown();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            // No-op
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // No-op
        }

        private void waitForEmbeddedHierarchy() {
            try {
                assertWithMessage("timed out waiting for embedded hierarchy to init.").that(
                        mCountDownLatch.await(3, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }

        private void moveSurfaceViewLayoutPosition(int x, int y, boolean offScreen)
                throws TimeoutException {
            final AccessibilityNodeInfo surfaceViewNode = findHostAccessibilityNodeInfo(
                    sUiAutomation.getRootInActiveWindow());
            final Rect expectedBounds = new Rect(), boundsAfter = new Rect();
            surfaceViewNode.getBoundsInScreen(expectedBounds);
            expectedBounds.offset(x, y);
            sUiAutomation.executeAndWaitForEvent(
                    () -> sInstrumentation.runOnMainSync(() -> {
                        mSurfaceView.setTranslationX(x);
                        mSurfaceView.setTranslationY(y);
                    }),
                    (event) -> {
                        surfaceViewNode.refresh();
                        surfaceViewNode.getBoundsInScreen(boundsAfter);
                        final boolean hasExpectedPosition;
                        if (offScreen) {
                            hasExpectedPosition = !surfaceViewNode.isVisibleToUser();
                        } else {
                            hasExpectedPosition = DisplayUtils.fuzzyBoundsInScreenSameOrigin(
                                    expectedBounds, boundsAfter);
                        }
                        if (!hasExpectedPosition) {
                            Log.i(AccessibilityEmbeddedHierarchyTest.class.getSimpleName(),
                                    "mSurfaceView expected bounds: " + expectedBounds
                                            + "\tActual bounds: " + boundsAfter);
                        }
                        return hasExpectedPosition;
                    }, DEFAULT_TIMEOUT_MS);
        }
    }
}
