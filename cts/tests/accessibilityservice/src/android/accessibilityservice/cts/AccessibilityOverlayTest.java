/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.launchActivityOnSpecifiedDisplayAndWaitForItToBeOnscreen;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityService;
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.cts.activities.AccessibilityWindowQueryActivity;
import android.accessibilityservice.cts.utils.ActivityLaunchUtils;
import android.accessibilityservice.cts.utils.AsyncUtils;
import android.accessibilityservice.cts.utils.DisplayUtils;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Binder;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.CtsWindowInfoUtils;
import android.util.SparseArray;
import android.view.Display;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.window.WindowInfosListenerForTest;

import androidx.test.filters.FlakyTest;
import androidx.test.platform.app.InstrumentationRegistry;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

// Test that an AccessibilityService can display an accessibility overlay
@RunWith(AndroidJUnit4.class)
@CddTest(requirements = {"3.10/C-1-1,C-1-2"})
@Presubmit
public class AccessibilityOverlayTest {

    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;
    InstrumentedAccessibilityService mService;

    private InstrumentedAccessibilityServiceTestRule<StubAccessibilityButtonService> mServiceRule =
            new InstrumentedAccessibilityServiceTestRule<>(StubAccessibilityButtonService.class);

    private AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    private CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule(sUiAutomation);

    @Rule
    public final RuleChain mRuleChain =
            RuleChain.outerRule(mServiceRule).around(mDumpOnFailureRule).around(mCheckFlagsRule);

    private Executor mExecutor = Executors.newSingleThreadExecutor();
    private ResultCapturingCallback mCallback = new ResultCapturingCallback();

    @BeforeClass
    public static void oneTimeSetUp() {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sUiAutomation =
                sInstrumentation.getUiAutomation(
                        UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        AccessibilityServiceInfo info = sUiAutomation.getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        sUiAutomation.setServiceInfo(info);
    }

    @AfterClass
    public static void postTestTearDown() {
        sUiAutomation.destroy();
    }

    @Before
    public void setUp() {
        mService = mServiceRule.getService();
    }

    @Test
    public void testA11yServiceShowsOverlay_shouldAppear() throws Exception {
        final String overlayTitle = "Overlay title";
        sUiAutomation.executeAndWaitForEvent(
                () ->
                        mService.runOnServiceSync(
                                () -> {
                                    addOverlayWindow(mService, overlayTitle);
                                }),
                (event) -> findOverlayWindow(Display.DEFAULT_DISPLAY) != null,
                AsyncUtils.DEFAULT_TIMEOUT_MS);

        assertThat(findOverlayWindow(Display.DEFAULT_DISPLAY).getTitle().toString())
                .isEqualTo(overlayTitle);
    }

    @Test
    public void testA11yServiceShowsOverlayOnVirtualDisplay_shouldAppear() throws Exception {
        addOverlayToVirtualDisplayAndCheck(
                display -> mService.createDisplayContext(display), /* expectException= */ false);
    }

    @Test
    public void testA11yServiceShowOverlay_withDerivedWindowContext_shouldAppear()
            throws Exception {
        addOverlayToVirtualDisplayAndCheck(
                display ->
                        mService.createDisplayContext(display)
                                .createWindowContext(
                                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                                        /* options= */ null),
                /* expectException= */ false);
    }

    @Test
    public void testA11yServiceShowOverlay_withDerivedWindowContextWithDisplay_shouldAppear()
            throws Exception {
        addOverlayToVirtualDisplayAndCheck(
                display ->
                        mService.createWindowContext(
                                display,
                                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                                /* options= */ null),
                /* expectException= */ false);
    }

    @Test
    public void testA11yServiceShowOverlay_withDerivedWindowContextWithTypeMismatch_throwException()
            throws Exception {
        addOverlayToVirtualDisplayAndCheck(
                display ->
                        mService.createWindowContext(
                                display,
                                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                                /* options= */ null),
                /* expectException= */ true);
    }

    @Test
    public void testA11yServiceShowsDisplayEmbeddedOverlayWithoutCallback_shouldAppearAndDisappear()
            throws Exception {
        // Set up a view that will become our accessibility overlay.
        final String overlayTitle = "Overlay title";
        final SurfaceControl sc = createDisplayOverlay(overlayTitle);
        attachOverlayToDisplayAndCheck(sc, overlayTitle, null, null);
        removeOverlayAndCheck(sc, overlayTitle);
    }

    @Test
    @RequiresFlagsEnabled(com.android.server.accessibility.Flags.FLAG_CLEANUP_A11Y_OVERLAYS)
    public void testEmbeddedDisplayOverlayWithServiceExit_shouldAppearAndDisappear()
            throws Exception {
        // Set up a view that will become our accessibility overlay.
        final String overlayTitle = "Overlay title";
        final SurfaceControl sc = createDisplayOverlay(overlayTitle);
        attachOverlayToDisplayAndCheck(sc, overlayTitle, null, null);
        disableServiceAndCheckForOverlay(overlayTitle);
    }

    @Test
    @RequiresFlagsEnabled(android.view.accessibility.Flags.FLAG_A11Y_OVERLAY_CALLBACKS)
    public void testA11yServiceShowsDisplayEmbeddedOverlayWithCallback_shouldAppearAndDisappear()
            throws Exception {
        // Set up a view that will become our accessibility overlay.
        final String overlayTitle = "Overlay title";
        final SurfaceControl sc = createDisplayOverlay(overlayTitle);
        attachOverlayToDisplayAndCheck(sc, overlayTitle, mExecutor, mCallback);
        mCallback.assertCallbackReceived(AccessibilityService.OVERLAY_RESULT_SUCCESS);
        removeOverlayAndCheck(sc, overlayTitle);
    }

    @Test
    @FlakyTest
    public void testA11yServiceShowsWindowEmbeddedOverlayWithoutCallback_shouldAppearAndDisappear()
            throws Exception {
        final String overlayTitle = "App Overlay title";
        Activity activity = showActivity();
        try {
            SurfaceControl sc = doOverlayWindowTest(overlayTitle, null, null);
            removeOverlayAndCheck(sc, overlayTitle);
        } finally {
            if (activity != null) {
                activity.finish();
            }
        }
    }

    @Test
    @FlakyTest
    @RequiresFlagsEnabled(android.view.accessibility.Flags.FLAG_A11Y_OVERLAY_CALLBACKS)
    public void testA11yServiceShowsWindowEmbeddedOverlayWithCallback_shouldAppearAndDisappear()
            throws Exception {
        final String overlayTitle = "App Overlay title";
        Activity activity = showActivity();
        try {
            SurfaceControl sc = doOverlayWindowTest(overlayTitle, mExecutor, mCallback);
            mCallback.assertCallbackReceived(AccessibilityService.OVERLAY_RESULT_SUCCESS);
            removeOverlayAndCheck(sc, overlayTitle);
        } finally {
            if (activity != null) {
                activity.finish();
            }
        }
    }

    @Test
    @FlakyTest
    @RequiresFlagsEnabled(com.android.server.accessibility.Flags.FLAG_CLEANUP_A11Y_OVERLAYS)
    public void testEmbeddedWindowOverlayWithServiceExit_shouldAppearAndDisappear()
            throws Exception {
        final String overlayTitle = "App Overlay title";
        Activity activity = showActivity();
        try {
            doOverlayWindowTest(overlayTitle, null, null);
            disableServiceAndCheckForOverlay(overlayTitle);
        } finally {
            if (activity != null) {
                activity.finish();
            }
        }
    }

    private SurfaceControl doOverlayWindowTest(
            String overlayTitle, Executor executor, ResultCapturingCallback callback)
            throws Exception {
        final StringBuilder timeoutExceptionRecords = new StringBuilder();
        try {
            final Display display =
                    mService.getSystemService(DisplayManager.class)
                            .getDisplay(Display.DEFAULT_DISPLAY);
            final Context context = mService.createDisplayContext(display);
            final SurfaceControlViewHost viewHost =
                    mService.getOnService(
                            () -> new SurfaceControlViewHost(context, display, new Binder()));
            final SurfaceControl sc = viewHost.getSurfacePackage().getSurfaceControl();
            final SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
            transaction.setVisibility(sc, true).apply();
            transaction.close();

            // Create an accessibility overlay hosting a FrameLayout with the same size
            // as the activity's root node bounds.
            final AccessibilityNodeInfo activityRootNode = sUiAutomation.getRootInActiveWindow();
            final Rect activityRootNodeBounds = new Rect();
            activityRootNode.getBoundsInWindow(activityRootNodeBounds);
            final WindowManager.LayoutParams overlayParams = new WindowManager.LayoutParams();

            overlayParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            overlayParams.format = PixelFormat.TRANSLUCENT;
            overlayParams.setTitle(overlayTitle);
            overlayParams.accessibilityTitle = overlayTitle;
            overlayParams.height = activityRootNodeBounds.height();
            overlayParams.width = activityRootNodeBounds.width();
            final FrameLayout overlayLayout = new FrameLayout(context);
            mService.runOnServiceSync(() -> viewHost.setView(overlayLayout, overlayParams));

            // Add a new Button view inside the overlay's FrameLayout, directly on top of
            // the window-space bounds of a node within the activity.
            final AccessibilityNodeInfo activityNodeToDrawOver =
                    activityRootNode
                            .findAccessibilityNodeInfosByViewId(
                                    "android.accessibilityservice.cts:id/button1")
                            .get(0);
            final Rect activityNodeToDrawOverBounds = new Rect();
            activityNodeToDrawOver.getBoundsInWindow(activityNodeToDrawOverBounds);
            Button overlayButton = new Button(context);
            final String buttonText = "overlay button";
            overlayButton.setText(buttonText);
            overlayButton.setX(activityNodeToDrawOverBounds.left);
            overlayButton.setY(activityNodeToDrawOverBounds.top);
            mService.runOnServiceSync(
                    () ->
                            overlayLayout.addView(
                                    overlayButton,
                                    new FrameLayout.LayoutParams(
                                            activityNodeToDrawOverBounds.width(),
                                            activityNodeToDrawOverBounds.height())));

            // Attach the SurfaceControlViewHost as an accessibility overlay to the activity window.
            sUiAutomation.executeAndWaitForEvent(
                    () -> {
                        if (callback != null && executor != null) {
                            mService.attachAccessibilityOverlayToWindow(
                                    activityRootNode.getWindowId(), sc, executor, callback);
                        } else {
                            mService.attachAccessibilityOverlayToWindow(
                                    activityRootNode.getWindowId(), sc);
                        }
                    },
                    (event) -> {
                        // Wait until the overlay window is added
                        final AccessibilityWindowInfo overlayWindow =
                                ActivityLaunchUtils.findWindowByTitle(sUiAutomation, overlayTitle);
                        if (overlayWindow == null
                                || overlayWindow.getType()
                                        != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) {
                            return false;
                        }
                        // Refresh the activity's button node to ensure the AccessibilityNodeInfo
                        // is the latest
                        activityNodeToDrawOver.refresh();

                        // Wait until overlay is drawn on correct location
                        final AccessibilityNodeInfo overlayButtonNode =
                                overlayWindow
                                        .getRoot()
                                        .findAccessibilityNodeInfosByText(buttonText)
                                        .get(0);
                        final Rect expectedBoundsInWindow = new Rect();
                        final Rect actualBoundsInWindow = new Rect();
                        activityNodeToDrawOver.getBoundsInWindow(expectedBoundsInWindow);
                        overlayButtonNode.getBoundsInWindow(actualBoundsInWindow);

                        final Rect expectedBoundsInScreen = new Rect();
                        final Rect actualBoundsInScreen = new Rect();
                        activityNodeToDrawOver.getBoundsInScreen(expectedBoundsInScreen);
                        overlayButtonNode.getBoundsInScreen(actualBoundsInScreen);

                        // Stores the related information including event, bounds
                        // as a timeout exception record.
                        timeoutExceptionRecords.append(
                                String.format(
                                        """
                                        { Received event: %s }
                                        Expected bounds in window: %s, actual bounds in window: %s
                                        Expected bounds in screen: %s, actual bounds in screen: %s
                                        """,
                                        event,
                                        expectedBoundsInWindow,
                                        actualBoundsInWindow,
                                        expectedBoundsInScreen,
                                        actualBoundsInScreen));

                        // The overlay button should have the same window-space and screen-space
                        // bounds as the view in the activity, as configured above.
                        return actualBoundsInWindow.equals(expectedBoundsInWindow)
                                && actualBoundsInScreen.equals(expectedBoundsInScreen);
                    },
                    AsyncUtils.DEFAULT_TIMEOUT_MS);

            checkTrustedOverlayExists(overlayTitle);
            return sc;
        } catch (TimeoutException timeout) {
            throw new TimeoutException(
                    timeout.getMessage()
                            + "\n\nTimeout exception records : \n"
                            + timeoutExceptionRecords);
        }
    }

    private void checkTrustedOverlayExists(String overlayTitle) throws Exception {
        try {
            Predicate<List<WindowInfosListenerForTest.WindowInfo>> windowPredicate =
                    windows ->
                            windows.stream()
                                    .anyMatch(
                                            window ->
                                                    window.name.contains(overlayTitle)
                                                            && window.isTrustedOverlay);
            assertWithMessage("Expected to find trusted overlay window")
                    .that(
                            CtsWindowInfoUtils.waitForWindowInfos(
                                    windowPredicate,
                                    AsyncUtils.DEFAULT_TIMEOUT_MS,
                                    TimeUnit.MILLISECONDS,
                                    sUiAutomation))
                    .isTrue();
        } finally {
            sUiAutomation.dropShellPermissionIdentity();
        }
    }

    private void addOverlayWindow(Context context, String overlayTitle) {
        final Button button = new Button(context);
        button.setText("Button");
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.flags =
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
        params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        params.setTitle(overlayTitle);
        context.getSystemService(WindowManager.class).addView(button, params);
    }

    private AccessibilityWindowInfo findOverlayWindow(int displayId) {
        final SparseArray<List<AccessibilityWindowInfo>> allWindows =
                sUiAutomation.getWindowsOnAllDisplays();
        final List<AccessibilityWindowInfo> windows = allWindows.get(displayId);

        if (windows != null) {
            for (AccessibilityWindowInfo window : windows) {
                if (window.getType() == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) {
                    return window;
                }
            }
        }
        return null;
    }

    private void addOverlayToVirtualDisplayAndCheck(
            Function<Display, Context> createContext, boolean expectException) throws Exception {
        assumeTrue(
                "Device does not support activities on secondary displays",
                sInstrumentation
                        .getContext()
                        .getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS));

        try (DisplayUtils.VirtualDisplaySession displaySession =
                new DisplayUtils.VirtualDisplaySession()) {
            final Display newDisplay =
                    displaySession.createDisplayWithDefaultDisplayMetricsAndWait(
                            mService, /* isPrivate= */ false);
            final int displayId = newDisplay.getDisplayId();
            final String overlayTitle = "Overlay title on virtualDisplay";

            // Create an initial activity window on the virtual display to ensure that
            // AccessibilityWindowManager is tracking windows for the display.
            launchActivityOnSpecifiedDisplayAndWaitForItToBeOnscreen(
                    sInstrumentation,
                    sUiAutomation,
                    AccessibilityWindowQueryActivity.class,
                    displayId);

            if (expectException) {
                assertThrows(
                        IllegalArgumentException.class,
                        () -> addOverlayWindow(createContext.apply(newDisplay), overlayTitle));
                assertThat(findOverlayWindow(displayId)).isNull();
            } else {
                sUiAutomation.executeAndWaitForEvent(
                        () ->
                                mService.runOnServiceSync(
                                        () ->
                                                addOverlayWindow(
                                                        createContext.apply(newDisplay),
                                                        overlayTitle)),
                        (event) -> findOverlayWindow(displayId) != null,
                        AsyncUtils.DEFAULT_TIMEOUT_MS);

                assertThat(findOverlayWindow(displayId).getTitle()).isEqualTo(overlayTitle);
            }
        }
    }

    class ResultCapturingCallback implements IntConsumer {

        private BlockingQueue<Integer> mResults = new LinkedBlockingQueue<>();

        @Override
        public void accept(int result) {
            mResults.offer(result);
        }

        public void assertCallbackReceived(int expected) {
            Integer received = null;
            try {
                received = mResults.poll(AsyncUtils.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertThat(expected).isEqualTo(received);
        }
    }

    private SurfaceControl createDisplayOverlay(String overlayTitle) {
        final Button button = new Button(mService);
        button.setText("Button");
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.width = 10;
        params.height = 10;
        params.flags =
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
        params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        params.setTitle(overlayTitle);

        // Create a SurfaceControlViewHost to host the overlay.
        Display display =
                mService.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        Context context = mService.createDisplayContext(display);
        final SurfaceControl sc =
                mService.getOnService(
                        () -> {
                            SurfaceControlViewHost scvh =
                                    new SurfaceControlViewHost(context, display, new Binder());
                            scvh.setView(button, params);
                            return scvh.getSurfacePackage().getSurfaceControl();
                        });

        // Make sure the overlay is visible.
        SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        t.setVisibility(sc, true).setLayer(sc, 1).apply();
        return sc;
    }

    private void attachOverlayToDisplayAndCheck(
            SurfaceControl sc, String overlayTitle, Executor executor, IntConsumer callback)
            throws Exception {
        sUiAutomation.executeAndWaitForEvent(
                () -> {
                    if (executor != null && callback != null) {
                        mService.attachAccessibilityOverlayToDisplay(
                                Display.DEFAULT_DISPLAY, sc, executor, callback);
                    } else {
                        mService.attachAccessibilityOverlayToDisplay(Display.DEFAULT_DISPLAY, sc);
                    }
                },
                (event) ->
                        ActivityLaunchUtils.findWindowByTitle(sUiAutomation, overlayTitle) != null,
                AsyncUtils.DEFAULT_TIMEOUT_MS);
        checkTrustedOverlayExists(overlayTitle);
    }

    private void removeOverlayAndCheck(SurfaceControl sc, String overlayTitle) throws Exception {
        // Remove the overlay.
        sUiAutomation.executeAndWaitForEvent(
                () -> {
                    SurfaceControl.Transaction t = new SurfaceControl.Transaction();
                    t.reparent(sc, null).apply();
                    t.close();
                    sc.release();
                },
                (event) ->
                        ActivityLaunchUtils.findWindowByTitle(sUiAutomation, overlayTitle) == null,
                AsyncUtils.DEFAULT_TIMEOUT_MS);
    }

    /** Disables the service and makes sure the overlay is not on the screen. */
    private void disableServiceAndCheckForOverlay(String overlayTitle) throws Exception {
        sUiAutomation.executeAndWaitForEvent(
                () -> mService.disableSelfAndRemove(),
                (event) ->
                        ActivityLaunchUtils.findWindowByTitle(sUiAutomation, overlayTitle) == null,
                AsyncUtils.DEFAULT_TIMEOUT_MS);
    }

    private Activity showActivity() throws Exception {
        final Activity activity =
                launchActivityOnSpecifiedDisplayAndWaitForItToBeOnscreen(
                        sInstrumentation,
                        sUiAutomation,
                        AccessibilityWindowQueryActivity.class,
                        Display.DEFAULT_DISPLAY);
        return activity;
    }
}
