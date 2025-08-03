/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static android.accessibilityservice.cts.utils.AccessibilityEventFilterUtils.filterForEventTypeWithAction;
import static android.accessibilityservice.cts.utils.AccessibilityEventFilterUtils.filterWindowsChangTypesAndWindowId;
import static android.accessibilityservice.cts.utils.AccessibilityEventFilterUtils.filterWindowsChangeTypesAndWindowTitle;
import static android.accessibilityservice.cts.utils.AccessibilityEventFilterUtils.filterWindowsChangedWithChangeTypes;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.findWindowByTitle;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.launchActivityAndWaitForItToBeOnscreen;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.launchActivityOnSpecifiedDisplayAndWaitForItToBeOnscreen;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.supportsMultiDisplay;
import static android.accessibilityservice.cts.utils.AsyncUtils.DEFAULT_TIMEOUT_MS;
import static android.accessibilityservice.cts.utils.DisplayUtils.VirtualDisplaySession;
import static android.accessibilityservice.cts.utils.DisplayUtils.getStatusBarHeight;
import static android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_LONG_CLICKED;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_ACCESSIBILITY_FOCUSED;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_ADDED;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_CLEAR_FOCUS;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_CLEAR_SELECTION;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_SELECT;

import static junit.framework.TestCase.assertNull;

import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.cts.activities.AccessibilityWindowQueryActivity;
import android.accessibilityservice.cts.activities.NonDefaultDisplayActivity;
import android.accessibilityservice.cts.utils.DisplayUtils;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.LocaleManager;
import android.app.PictureInPictureParams;
import android.app.UiAutomation;
import android.graphics.Rect;
import android.os.LocaleList;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.SystemUtil;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Test cases for testing the accessibility APIs for querying of the screen content.
 * These APIs allow exploring the screen and requesting an action to be performed
 * on a given view from an AccessibilityService.
 */
@AppModeFull
@RunWith(AndroidJUnit4.class)
@CddTest(requirements = {"3.10/C-1-1,C-1-2"})
@Presubmit
public class AccessibilityWindowQueryTest {
    private static final String LOG_TAG = "AccessibilityWindowQueryTest";
    private static String CONTENT_VIEW_RES_NAME =
            "android.accessibilityservice.cts:id/added_content";
    private static final long TIMEOUT_WINDOW_STATE_IDLE = 500;
    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;

    private AccessibilityWindowQueryActivity mActivity;
    private Activity mActivityOnVirtualDisplay;

    private ActivityTestRule<AccessibilityWindowQueryActivity> mActivityRule =
            new ActivityTestRule<>(AccessibilityWindowQueryActivity.class, false, false);

    private AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    @Rule
    public final RuleChain mRuleChain = RuleChain
            .outerRule(mActivityRule)
            .around(mDumpOnFailureRule);

    @BeforeClass
    public static void oneTimeSetup() throws Exception {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sUiAutomation = sInstrumentation.getUiAutomation();
        AccessibilityServiceInfo info = sUiAutomation.getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        sUiAutomation.setServiceInfo(info);
    }

    @AfterClass
    public static void postTestTearDown() {
        sUiAutomation.destroy();
    }

    @Before
    public void setUp() throws Exception {
        mActivity = launchActivityAndWaitForItToBeOnscreen(
                sInstrumentation, sUiAutomation, mActivityRule);
    }

    @MediumTest
    @Test
    public void testFindByText() throws Throwable {
        // First, make the root view of the activity an accessibility node. This allows us to
        // later exclude views that are part of the activity's DecorView.
        sInstrumentation.runOnMainSync(() -> mActivity.findViewById(R.id.added_content)
                .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES));

        // Start looking from the added content instead of from the root accessibility node so
        // that nodes that we don't expect (i.e. window control buttons) are not included in the
        // list of accessibility nodes returned by findAccessibilityNodeInfosByText.
        final AccessibilityNodeInfo addedContent = sUiAutomation
                .getRootInActiveWindow().findAccessibilityNodeInfosByViewId(CONTENT_VIEW_RES_NAME)
                .get(0);

        // find a view by text
        List<AccessibilityNodeInfo> buttons = addedContent.findAccessibilityNodeInfosByText("b");

        assertEquals(9, buttons.size());
    }

    @MediumTest
    @Test
    public void testFindByContentDescription() throws Exception {
        // find a view by text
        AccessibilityNodeInfo button = sUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByText(mActivity.getString(R.string.contentDescription))
                .get(0);
        assertNotNull(button);
    }

    @MediumTest
    @Test
    public void testTraverseWindow() throws Exception {
        verifyNodesInAppWindow(sUiAutomation.getRootInActiveWindow());
    }

    @MediumTest
    @Test
    public void testNoWindowsAccessIfFlagNotSet() throws Exception {
        // Clear window access flag
        AccessibilityServiceInfo info = sUiAutomation.getServiceInfo();
        info.flags &= ~AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        sUiAutomation.setServiceInfo(info);

        // Make sure the windows cannot be accessed.
        assertTrue(sUiAutomation.getWindows().isEmpty());

        // Find a button to click on.
        final AccessibilityNodeInfo button1 = sUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByViewId(
                        "android.accessibilityservice.cts:id/button1").get(0);

        // Click the button to generate an event
        AccessibilityEvent event =
                sUiAutomation.executeAndWaitForEvent(
                        () -> button1.performAction(ACTION_CLICK),
                        filterForEventTypeWithAction(TYPE_VIEW_CLICKED, ACTION_CLICK),
                        DEFAULT_TIMEOUT_MS);

        // Make sure the source window cannot be accessed.
        assertNull(event.getSource().getWindow());
    }

    @MediumTest
    @Test
    public void testTraverseAllWindows() throws Exception {
        List<AccessibilityWindowInfo> windows = sUiAutomation.getWindows();
        Rect boundsInScreen = new Rect();

        final int windowCount = windows.size();
        for (int i = 0; i < windowCount; i++) {
            AccessibilityWindowInfo window = windows.get(i);
            // Skip other Apps windows since their state might not be stable while querying.
            if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION
                    && !TextUtils.equals(window.getTitle(), mActivity.getTitle())) {
                continue;
            }
            window.getBoundsInScreen(boundsInScreen);
            assertFalse(boundsInScreen.isEmpty()); // Varies on screen size, emptiness check.
            assertNull(window.getParent());
            assertSame(0, window.getChildCount());
            assertNull(window.getParent());
            assertNotNull(window.getRoot());

            if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                assertTrue(window.isFocused());
                assertTrue(window.isActive());
                verifyNodesInAppWindow(window.getRoot());
            } else if (window.getType() == AccessibilityWindowInfo.TYPE_SYSTEM) {
                assertFalse(window.isFocused());
                assertFalse(window.isActive());
            }
        }
    }

    @MediumTest
    @Test
    public void testTraverseWindowFromEvent() throws Exception {
        // Find a button to click on.
        final AccessibilityNodeInfo button1 = sUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByViewId(
                        "android.accessibilityservice.cts:id/button1").get(0);

        // Click the button.
        AccessibilityEvent event =
                sUiAutomation.executeAndWaitForEvent(
                        () -> button1.performAction(ACTION_CLICK),
                        filterForEventTypeWithAction(TYPE_VIEW_CLICKED, ACTION_CLICK),
                        DEFAULT_TIMEOUT_MS);

        // Get the source window.
        AccessibilityWindowInfo window = event.getSource().getWindow();

        // Verify the application window.
        Rect boundsInScreen = new Rect();
        window.getBoundsInScreen(boundsInScreen);
        assertFalse(boundsInScreen.isEmpty()); // Varies on screen size, so just emptiness check
        assertSame(window.getType(), AccessibilityWindowInfo.TYPE_APPLICATION);
        assertTrue(window.isFocused());
        assertTrue(window.isActive());
        assertNull(window.getParent());
        assertSame(0, window.getChildCount());
        assertNotNull(window.getRoot());

        // Verify the window content.
        verifyNodesInAppWindow(window.getRoot());
    }

    @MediumTest
    @Test
    public void testInteractWithAppWindow() throws Exception {
        // Find a button to click on.
        final AccessibilityNodeInfo button1 = sUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByViewId(
                        "android.accessibilityservice.cts:id/button1").get(0);

        // Click the button.
        AccessibilityEvent event =
                sUiAutomation.executeAndWaitForEvent(
                        () -> button1.performAction(ACTION_CLICK),
                        filterForEventTypeWithAction(TYPE_VIEW_CLICKED, ACTION_CLICK),
                        DEFAULT_TIMEOUT_MS);

        // Get the source window.
        AccessibilityWindowInfo window = event.getSource().getWindow();

        // Find a another button from the event's window.
        final AccessibilityNodeInfo button2 = window.getRoot()
                .findAccessibilityNodeInfosByViewId(
                        "android.accessibilityservice.cts:id/button2").get(0);

        // Click the second button.
        sUiAutomation.executeAndWaitForEvent(
                () -> button2.performAction(ACTION_CLICK),
                filterForEventTypeWithAction(TYPE_VIEW_CLICKED, ACTION_CLICK),
                DEFAULT_TIMEOUT_MS);
    }

    @MediumTest
    @Test
    public void testSingleAccessibilityFocusAcrossWindows() throws Exception {
        try {
            // Add two more windows.
            final View views[];
            views = addTwoAppPanelWindows(mActivity);

            try {
                // Put accessibility focus in the first app window.
                ensureAppWindowFocusedOrFail(0);
                // Make sure there only one accessibility focus.
                assertSingleAccessibilityFocus();

                // Put accessibility focus in the second app window.
                ensureAppWindowFocusedOrFail(1);
                // Make sure there only one accessibility focus.
                assertSingleAccessibilityFocus();

                // Put accessibility focus in the third app window.
                ensureAppWindowFocusedOrFail(2);
                // Make sure there only one accessibility focus.
                assertSingleAccessibilityFocus();
            } finally {
                // Clean up panel windows
                sInstrumentation.runOnMainSync(() -> {
                    WindowManager wm =
                            sInstrumentation.getContext().getSystemService(WindowManager.class);
                    for (View view : views) {
                        wm.removeView(view);
                    }
                });
            }
        } finally {
            ensureAccessibilityFocusCleared();
        }
    }

    @MediumTest
    @Test
    public void testPerformActionSetAndClearFocus() throws Exception {
        // find a view and make sure it is not focused
        AccessibilityNodeInfo button = sUiAutomation
                .getRootInActiveWindow().findAccessibilityNodeInfosByText(
                        mActivity.getString(R.string.button5)).get(0);
        assertFalse(button.isFocused());

        // focus the view
        assertTrue(button.performAction(ACTION_FOCUS));

        // find the view again and make sure it is focused
        button = sUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByText(mActivity.getString(R.string.button5)).get(0);
        assertTrue(button.isFocused());

        // unfocus the view
        assertTrue(button.performAction(ACTION_CLEAR_FOCUS));

        // find the view again and make sure it is not focused
        button = sUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByText(mActivity.getString(R.string.button5)).get(0);
        assertFalse(button.isFocused());
    }

    @MediumTest
    @Test
    public void testPerformActionSelect() throws Exception {
        // find a view and make sure it is not selected
        AccessibilityNodeInfo button = sUiAutomation
                .getRootInActiveWindow().findAccessibilityNodeInfosByText(
                        mActivity.getString(R.string.button5)).get(0);
        assertFalse(button.isSelected());

        // select the view
        assertTrue(button.performAction(ACTION_SELECT));

        // find the view again and make sure it is selected
        button = sUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByText(mActivity.getString(R.string.button5)).get(0);
        assertTrue(button.isSelected());
    }

    @MediumTest
    @Test
    public void testPerformActionClearSelection() throws Exception {
        // find a view and make sure it is not selected
        AccessibilityNodeInfo button = sUiAutomation
                .getRootInActiveWindow().findAccessibilityNodeInfosByText(
                        mActivity.getString(R.string.button5)).get(0);
        assertFalse(button.isSelected());

        // select the view
        assertTrue(button.performAction(ACTION_SELECT));

        // find the view again and make sure it is selected
        button = sUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByText(mActivity.getString(R.string.button5)).get(0);

        assertTrue(button.isSelected());

        // unselect the view
        assertTrue(button.performAction(ACTION_CLEAR_SELECTION));

        // find the view again and make sure it is not selected
        button = sUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByText(mActivity.getString(R.string.button5)).get(0);
        assertFalse(button.isSelected());
    }

    @MediumTest
    @Test
    public void testPerformActionClick() throws Exception {
        // find a view and make sure it is not selected
        final AccessibilityNodeInfo button = sUiAutomation
                .getRootInActiveWindow().findAccessibilityNodeInfosByText(
                        mActivity.getString(R.string.button5)).get(0);
        assertFalse(button.isSelected());

        // Perform an action and wait for an event
        AccessibilityEvent expected =
                sUiAutomation.executeAndWaitForEvent(
                        () -> button.performAction(ACTION_CLICK),
                        filterForEventTypeWithAction(TYPE_VIEW_CLICKED, ACTION_CLICK),
                        DEFAULT_TIMEOUT_MS);

        // Make sure the expected event was received.
        assertNotNull(expected);
    }

    @MediumTest
    @Test
    public void testPerformActionLongClick() throws Exception {
        // find a view and make sure it is not selected
        final AccessibilityNodeInfo button = sUiAutomation
                .getRootInActiveWindow().findAccessibilityNodeInfosByText(
                        mActivity.getString(R.string.button5)).get(0);
        assertFalse(button.isSelected());

        // Perform an action and wait for an event.
        AccessibilityEvent expected =
                sUiAutomation.executeAndWaitForEvent(
                        () -> button.performAction(ACTION_LONG_CLICK),
                        filterForEventTypeWithAction(TYPE_VIEW_LONG_CLICKED, ACTION_LONG_CLICK),
                        DEFAULT_TIMEOUT_MS);

        // Make sure the expected event was received.
        assertNotNull(expected);
    }


    @MediumTest
    @Test
    public void testPerformCustomAction() throws Exception {
        // find a view and make sure it is not selected
        AccessibilityNodeInfo button = sUiAutomation
                .getRootInActiveWindow().findAccessibilityNodeInfosByText(
                        mActivity.getString(R.string.button5)).get(0);

        // find the custom action and perform it
        List<AccessibilityAction> actions = button.getActionList();
        final int actionCount = actions.size();
        for (int i = 0; i < actionCount; i++) {
            AccessibilityAction action = actions.get(i);
            if (action.getId() == R.id.foo_custom_action) {
                assertSame(action.getLabel(), "Foo");
                // perform the action
                assertTrue(button.performAction(action.getId()));
                return;
            }
        }
    }

    @MediumTest
    @Test
    public void testGetEventSource() throws Exception {
        // find a view and make sure it is not focused
        final AccessibilityNodeInfo button = sUiAutomation
                .getRootInActiveWindow().findAccessibilityNodeInfosByText(
                        mActivity.getString(R.string.button5)).get(0);
        assertFalse(button.isSelected());

        // focus and wait for the event
        AccessibilityEvent awaitedEvent =
                sUiAutomation.executeAndWaitForEvent(
                        () -> button.performAction(ACTION_FOCUS),
                        filterForEventTypeWithAction(TYPE_VIEW_FOCUSED, ACTION_FOCUS),
                        DEFAULT_TIMEOUT_MS);

        assertNotNull(awaitedEvent);

        // check that last event source
        AccessibilityNodeInfo source = awaitedEvent.getSource();
        assertNotNull(source);

        // bounds
        Rect buttonBounds = new Rect();
        button.getBoundsInParent(buttonBounds);
        Rect sourceBounds = new Rect();
        source.getBoundsInParent(sourceBounds);

        assertEquals(buttonBounds.left, sourceBounds.left);
        assertEquals(buttonBounds.right, sourceBounds.right);
        assertEquals(buttonBounds.top, sourceBounds.top);
        assertEquals(buttonBounds.bottom, sourceBounds.bottom);

        // char sequence attributes
        assertEquals(button.getPackageName(), source.getPackageName());
        assertEquals(button.getClassName(), source.getClassName());
        assertEquals(button.getText().toString(), source.getText().toString());
        assertSame(button.getContentDescription(), source.getContentDescription());

        // boolean attributes
        assertSame(button.isFocusable(), source.isFocusable());
        assertSame(button.isClickable(), source.isClickable());
        assertSame(button.isEnabled(), source.isEnabled());
        assertNotSame(button.isFocused(), source.isFocused());
        assertSame(button.isLongClickable(), source.isLongClickable());
        assertSame(button.isPassword(), source.isPassword());
        assertSame(button.isSelected(), source.isSelected());
        assertSame(button.isCheckable(), source.isCheckable());
        assertSame(button.isChecked(), source.isChecked());
    }

    @MediumTest
    @Test
    public void testObjectContract() throws Exception {
        try {
            AccessibilityServiceInfo info = sUiAutomation.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            sUiAutomation.setServiceInfo(info);

            // find a view and make sure it is not focused
            AccessibilityNodeInfo button = sUiAutomation
                    .getRootInActiveWindow().findAccessibilityNodeInfosByText(
                            mActivity.getString(R.string.button5)).get(0);
            AccessibilityNodeInfo parent = button.getParent();
            final int childCount = parent.getChildCount();
            for (int i = 0; i < childCount; i++) {
                AccessibilityNodeInfo child = parent.getChild(i);
                assertNotNull(child);
                if (child.equals(button)) {
                    assertEquals("Equal objects must have same hasCode.", button.hashCode(),
                            child.hashCode());
                    return;
                }
            }
            fail("Parent's children do not have the info whose parent is the parent.");
        } finally {
            AccessibilityServiceInfo info = sUiAutomation.getServiceInfo();
            info.flags &= ~AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            sUiAutomation.setServiceInfo(info);
        }
    }

    @Test
    @Ignore("b/325640120")
    public void testFindPictureInPictureWindow() throws Exception {
        if (!sInstrumentation.getContext().getPackageManager()
                .hasSystemFeature(FEATURE_PICTURE_IN_PICTURE)) {
            return;
        }
        sUiAutomation.executeAndWaitForEvent(() -> sInstrumentation.runOnMainSync(
                () -> mActivity.enterPictureInPictureMode(
                        new PictureInPictureParams.Builder().build())),
                event -> (event.getWindowChanges() & AccessibilityEvent.WINDOWS_CHANGE_PIP) != 0
                        && sUiAutomation.getWindows().stream().anyMatch(
                                info -> info.getId() == event.getWindowId()
                                        && info.isInPictureInPictureMode()
                ),
                DEFAULT_TIMEOUT_MS);

        // We should be able to find a picture-in-picture window now

        int numPictureInPictureWindows = 0;
        final List<AccessibilityWindowInfo> windows = sUiAutomation.getWindows();
        final int windowCount = windows.size();
        for (int i = 0; i < windowCount; i++) {
            final AccessibilityWindowInfo window = windows.get(i);
            if (window.isInPictureInPictureMode()) {
                numPictureInPictureWindows++;
            }
        }
        assertTrue(numPictureInPictureWindows > 0);
    }

    @Test
    public void testGetWindows_resultIsSortedByLayerDescending() throws TimeoutException {
        addTwoAppPanelWindows(mActivity);
        List<AccessibilityWindowInfo> windows = sUiAutomation.getWindows();

        AccessibilityWindowInfo windowAddedFirst = findWindow(windows, R.string.button1);
        AccessibilityWindowInfo windowAddedSecond = findWindow(windows, R.string.button2);
        assertThat(windowAddedFirst.getLayer(), lessThan(windowAddedSecond.getLayer()));

        assertThat(windows, new IsSortedBy<>(w -> w.getLayer(), /* ascending */ false));
    }

    @Test
    public void testGetWindowsOnAllDisplays_resultIsSortedByLayerDescending() throws Exception {
        assumeTrue(supportsMultiDisplay(sInstrumentation.getContext()));

        addTwoAppPanelWindows(mActivity);
        // Creates a virtual display.
        try (final VirtualDisplaySession displaySession = new VirtualDisplaySession()) {
            final int virtualDisplayId =
                    displaySession.createDisplayWithDefaultDisplayMetricsAndWait(
                            sInstrumentation.getContext(), false).getDisplayId();
            // Launches an activity on virtual display.
            mActivityOnVirtualDisplay = launchActivityOnSpecifiedDisplayAndWaitForItToBeOnscreen(
                    sInstrumentation, sUiAutomation,
                    NonDefaultDisplayActivity.class,
                    virtualDisplayId);
            // Adds two app panel windows on activity of virtual display.
            addTwoAppPanelWindows(mActivityOnVirtualDisplay);

            // Gets all windows.
            SparseArray<List<AccessibilityWindowInfo>> allWindows =
                    sUiAutomation.getWindowsOnAllDisplays();
            assertNotNull(allWindows);

            // Gets windows on default display.
            assertTrue(allWindows.contains(Display.DEFAULT_DISPLAY));
            List<AccessibilityWindowInfo> windowsOnDefaultDisplay =
                    allWindows.get(Display.DEFAULT_DISPLAY);
            assertNotNull(windowsOnDefaultDisplay);
            assertTrue(windowsOnDefaultDisplay.size() > 0);

            AccessibilityWindowInfo windowAddedFirst =
                    findWindow(windowsOnDefaultDisplay, R.string.button1);
            AccessibilityWindowInfo windowAddedSecond =
                    findWindow(windowsOnDefaultDisplay, R.string.button2);
            assertThat(windowAddedFirst.getLayer(), lessThan(windowAddedSecond.getLayer()));
            assertThat(windowsOnDefaultDisplay,
                    new IsSortedBy<>(w -> w.getLayer(), /* ascending */ false));

            // Gets windows on virtual display.
            assertTrue(allWindows.contains(virtualDisplayId));
            List<AccessibilityWindowInfo> windowsOnVirtualDisplay =
                    allWindows.get(virtualDisplayId);
            assertNotNull(windowsOnVirtualDisplay);
            assertTrue(windowsOnVirtualDisplay.size() > 0);

            AccessibilityWindowInfo windowAddedFirstOnVirtualDisplay =
                    findWindow(windowsOnVirtualDisplay, R.string.button1);
            AccessibilityWindowInfo windowAddedSecondOnVirtualDisplay =
                    findWindow(windowsOnVirtualDisplay, R.string.button2);
            assertThat(windowAddedFirstOnVirtualDisplay.getLayer(),
                    lessThan(windowAddedSecondOnVirtualDisplay.getLayer()));
            assertThat(windowsOnVirtualDisplay,
                    new IsSortedBy<>(w -> w.getLayer(), /* ascending */ false));

            mActivityOnVirtualDisplay.finish();
        }
    }

    @Test
    public void testShowInputMethodDialogWindow_resultIsApplicationType()
            throws TimeoutException {
        final WindowManager wm =
                sInstrumentation.getContext().getSystemService(WindowManager.class);
        final View view = new View(sInstrumentation.getContext());
        final String windowTitle = "Input Method Dialog";

        try {
            sUiAutomation.executeAndWaitForEvent(() -> sInstrumentation.runOnMainSync(
                    () -> {
                        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                                WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG);
                        params.accessibilityTitle = windowTitle;

                        SystemUtil.runWithShellPermissionIdentity(sUiAutomation,
                                () -> wm.addView(view, params),
                                "android.permission.INTERNAL_SYSTEM_WINDOW");
                    }),
                    filterWindowsChangeTypesAndWindowTitle(sUiAutomation,
                            WINDOWS_CHANGE_ADDED, windowTitle), DEFAULT_TIMEOUT_MS);


            final List<AccessibilityWindowInfo> windows = sUiAutomation.getWindows();
            assertTrue(windows.stream().anyMatch(window -> window.getType()
                    == AccessibilityWindowInfo.TYPE_APPLICATION));
        } finally {
            try {
                wm.removeView(view);
            } catch (IllegalStateException e) {
                Log.e(LOG_TAG, "remove view fail:" + e.toString());
            }
        }
    }

    @Test
    public void testChangeWindowSize_transitionTimeIsChanged()
            throws TimeoutException {
        final WindowManager wm =
                sInstrumentation.getContext().getSystemService(WindowManager.class);
        final View viewRoot = new View(sInstrumentation.getContext());
        final String windowTitle = "application sub-panel";
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL);
        params.accessibilityTitle = windowTitle;
        final Rect displayBounds = wm.getCurrentWindowMetrics().getBounds();
        params.width = displayBounds.width() / 2;
        params.height = displayBounds.height() / 2;
        params.token = mActivity.getWindow().getDecorView().getWindowToken();
        final WindowLocationFilter windowLocationFilter = new WindowLocationFilter(sUiAutomation,
                viewRoot, windowTitle);
        try {
            sUiAutomation.executeAndWaitForEvent(
                    () -> sInstrumentation.runOnMainSync(() -> wm.addView(viewRoot, params)),
                    windowLocationFilter, DEFAULT_TIMEOUT_MS);
            AccessibilityWindowInfo targetWindow = windowLocationFilter.getTargetWindow();
            final long firstTransitionTime = targetWindow.getTransitionTimeMillis();
            assertTrue(firstTransitionTime > 0);

            SystemClock.sleep(50);
            params.width = displayBounds.width() / 4;
            params.height = displayBounds.height() / 4;
            sUiAutomation.executeAndWaitForEvent(() -> sInstrumentation.runOnMainSync(
                    () -> wm.updateViewLayout(viewRoot, params)),
                    windowLocationFilter, DEFAULT_TIMEOUT_MS);
            targetWindow = windowLocationFilter.getTargetWindow();

            assertTrue(targetWindow.getTransitionTimeMillis() > (firstTransitionTime + 50L));
        } finally {
            wm.removeView(viewRoot);
        }
    }

    @Test
    @FlakyTest
    public void testWindowLocale_setGet() throws Exception {
        final LocaleManager manager = mActivity.getSystemService(LocaleManager.class);
        final LocaleList systemLocales = manager.getSystemLocales();
        final Locale testLocaleEn = Locale.ENGLISH;
        final Locale testLocaleDe = Locale.GERMAN;
        AccessibilityWindowInfo window;

        try {
            window = sUiAutomation.getRootInActiveWindow().getWindow();
            assertEquals(systemLocales, window.getLocales());

            // First returned locale should be the app locale.
            setActivityLocale(manager, new LocaleList(testLocaleEn));
            window = sUiAutomation.getRootInActiveWindow().getWindow();
            assertEquals(testLocaleEn, window.getLocales().get(0));

            // System locale is appended to the end, but order should be the same.
            setActivityLocale(manager, new LocaleList(testLocaleEn, testLocaleDe));
            window = sUiAutomation.getRootInActiveWindow().getWindow();
            LocaleList locales = window.getLocales();
            assertEquals(testLocaleEn, locales.get(0));
            assertEquals(testLocaleDe, locales.get(1));
        } finally {
            setActivityLocale(manager, LocaleList.getEmptyLocaleList());
        }
    }

    private void setActivityLocale(LocaleManager localeManager, LocaleList localeList)
            throws TimeoutException {
        sUiAutomation.executeAndWaitForEvent(
                () -> sInstrumentation.runOnMainSync(
                        () -> localeManager.setApplicationLocales(localeList)),
                event -> {
                    AccessibilityNodeInfo root = sUiAutomation.getRootInActiveWindow();
                    return root != null && root.getWindow() != null
                            && localeManager.getApplicationLocales().equals(localeList);
                }, DEFAULT_TIMEOUT_MS);
    }

    private AccessibilityWindowInfo findWindow(List<AccessibilityWindowInfo> windows,
            int btnTextRes) {
        return windows.stream()
                .filter(w -> w.getRoot()
                        .findAccessibilityNodeInfosByText(
                                sInstrumentation.getTargetContext().getString(btnTextRes))
                        .size() == 1)
                .findFirst()
                .get();
    }

    private void assertSingleAccessibilityFocus() {
        List<AccessibilityWindowInfo> windows = sUiAutomation.getWindows();
        AccessibilityWindowInfo focused = null;

        final int windowCount = windows.size();
        for (int i = 0; i < windowCount; i++) {
            AccessibilityWindowInfo window = windows.get(i);

            if (window.isAccessibilityFocused()) {
                if (focused == null) {
                    focused = window;

                    AccessibilityNodeInfo root = window.getRoot();
                    assertEquals(sUiAutomation.findFocus(
                            AccessibilityNodeInfo.FOCUS_ACCESSIBILITY), root);
                    assertEquals(root.findFocus(
                            AccessibilityNodeInfo.FOCUS_ACCESSIBILITY), root);
                } else {
                    throw new AssertionError("Duplicate accessibility focus");
                }
            } else {
                AccessibilityNodeInfo root = window.getRoot();
                if (root != null) {
                    assertNull(root.findFocus(
                            AccessibilityNodeInfo.FOCUS_ACCESSIBILITY));
                }
            }
        }
    }

    private void ensureAppWindowFocusedOrFail(int appWindowIndex) throws TimeoutException {
        List<AccessibilityWindowInfo> windows = sUiAutomation.getWindows();
        AccessibilityWindowInfo focusTarget = null;

        int visitedAppWindows = -1;
        final int windowCount = windows.size();
        for (int i = 0; i < windowCount; i++) {
            AccessibilityWindowInfo window = windows.get(i);
            if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                visitedAppWindows++;
                if (appWindowIndex <= visitedAppWindows) {
                    focusTarget = window;
                    break;
                }
            }
        }

        if (focusTarget == null) {
            throw new IllegalStateException("Couldn't find app window: " + appWindowIndex);
        }

        if (focusTarget.isAccessibilityFocused()) {
            return;
        }

        final AccessibilityWindowInfo finalFocusTarget = focusTarget;
        sUiAutomation.executeAndWaitForEvent(() -> assertTrue(finalFocusTarget.getRoot()
                        .performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)),
                filterWindowsChangTypesAndWindowId(finalFocusTarget.getId(),
                        WINDOWS_CHANGE_ACCESSIBILITY_FOCUSED),
                DEFAULT_TIMEOUT_MS);

        windows = sUiAutomation.getWindows();
        for (int i = 0; i < windowCount; i++) {
            AccessibilityWindowInfo window = windows.get(i);
            if (window.getId() == focusTarget.getId()) {
                assertTrue(window.isAccessibilityFocused());
                break;
            }
        }
    }

    private View[] addTwoAppPanelWindows(Activity activity) throws TimeoutException {
        sUiAutomation
                .waitForIdle(TIMEOUT_WINDOW_STATE_IDLE, DEFAULT_TIMEOUT_MS);

        return new View[]{
                addWindow(R.string.button1, params -> {
                    params.gravity = Gravity.TOP;
                    params.y = getStatusBarHeight(activity);
                }, activity),
                addWindow(R.string.button2, params -> {
                    params.gravity = Gravity.BOTTOM;
                }, activity)
        };
    }

    private Button addWindow(int btnTextRes, Consumer<WindowManager.LayoutParams> configure,
            Activity activity) throws TimeoutException {
        AtomicReference<Button> result = new AtomicReference<>();
        sUiAutomation.executeAndWaitForEvent(() -> {
            sInstrumentation.runOnMainSync(() -> {
                final WindowManager.LayoutParams params = new WindowManager.LayoutParams();
                params.width = WindowManager.LayoutParams.MATCH_PARENT;
                params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
                params.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
                params.token = activity.getWindow().getDecorView().getWindowToken();
                configure.accept(params);

                final Button button = new Button(activity);
                button.setText(btnTextRes);
                result.set(button);
                activity.getWindowManager().addView(button, params);
            });
        }, filterWindowsChangedWithChangeTypes(WINDOWS_CHANGE_ADDED), DEFAULT_TIMEOUT_MS);
        return result.get();
    }

    private void ensureAccessibilityFocusCleared() {
        try {
            sUiAutomation.executeAndWaitForEvent(
                    () -> {
                        List<AccessibilityWindowInfo> windows = sUiAutomation.getWindows();
                        final int windowCount = windows.size();
                        for (int i = 0; i < windowCount; i++) {
                            AccessibilityWindowInfo window = windows.get(i);
                            if (window.isAccessibilityFocused()) {
                                AccessibilityNodeInfo root = window.getRoot();
                                if (root != null) {
                                    root.performAction(
                                            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
                                }
                            }
                        }
                    },
                    filterForEventTypeWithAction(
                            TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED,
                            ACTION_CLEAR_ACCESSIBILITY_FOCUS),
                    DEFAULT_TIMEOUT_MS);
        } catch (TimeoutException te) {
            /* ignore */
        }
    }

    private void verifyNodesInAppWindow(AccessibilityNodeInfo root) throws Exception {
        try {
            AccessibilityServiceInfo info = sUiAutomation.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            sUiAutomation.setServiceInfo(info);

            root.refresh();

            // make list of expected nodes
            List<String> classNameAndTextList = new ArrayList<String>();
            classNameAndTextList.add("android.widget.LinearLayout");
            classNameAndTextList.add("android.widget.LinearLayout");
            classNameAndTextList.add("android.widget.LinearLayout");
            classNameAndTextList.add("android.widget.LinearLayout");
            final String btnClass = "android.widget.Button";
            classNameAndTextList.add(btnClass.concat(mActivity.getString(R.string.button1)));
            classNameAndTextList.add(btnClass.concat(mActivity.getString(R.string.button2)));
            classNameAndTextList.add(btnClass.concat(mActivity.getString(R.string.button3)));
            classNameAndTextList.add(btnClass.concat(mActivity.getString(R.string.button4)));
            classNameAndTextList.add(btnClass.concat(mActivity.getString(R.string.button5)));
            classNameAndTextList.add(btnClass.concat(mActivity.getString(R.string.button6)));
            classNameAndTextList.add(btnClass.concat(mActivity.getString(R.string.button7)));
            classNameAndTextList.add(btnClass.concat(mActivity.getString(R.string.button8)));
            classNameAndTextList.add(btnClass.concat(mActivity.getString(R.string.button9)));

            boolean verifyContent = false;

            Queue<AccessibilityNodeInfo> fringe = new LinkedList<AccessibilityNodeInfo>();
            fringe.add(root);

            // do a BFS traversal and check nodes
            while (!fringe.isEmpty()) {
                AccessibilityNodeInfo current = fringe.poll();

                if (!verifyContent
                        && CONTENT_VIEW_RES_NAME.equals(current.getViewIdResourceName())) {
                    verifyContent = true;
                }

                if (verifyContent) {
                    CharSequence text = current.getText();
                    String receivedClassNameAndText = current.getClassName().toString()
                            + ((text != null) ? text.toString() : "");
                    String expectedClassNameAndText = classNameAndTextList.remove(0);

                    assertEquals("Did not get the expected node info",
                            expectedClassNameAndText, receivedClassNameAndText);
                }

                final int childCount = current.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    AccessibilityNodeInfo child = current.getChild(i);
                    fringe.add(child);
                }
            }
        } finally {
            AccessibilityServiceInfo info = sUiAutomation.getServiceInfo();
            info.flags &= ~AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            sUiAutomation.setServiceInfo(info);
        }
    }

    private static class IsSortedBy<T> extends TypeSafeMatcher<List<T>> {

        private final Function<T, ? extends Comparable> mProperty;
        private final boolean mAscending;

        private IsSortedBy(Function<T, ? extends Comparable> comparator, boolean ascending) {
            mProperty = comparator;
            mAscending = ascending;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("is sorted");
        }

        @Override
        protected boolean matchesSafely(List<T> item) {
            for (int i = 0; i < item.size() - 1; i++) {
                Comparable a = mProperty.apply(item.get(i));
                Comparable b = mProperty.apply(item.get(i));
                int aMinusB = a.compareTo(b);
                if (aMinusB != 0 && (aMinusB < 0) != mAscending) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Finds the {@link AccessibilityWindowInfo} associated with the given window tile and match
     * the actual location of the viewRoot.
     */
    private static class WindowLocationFilter implements UiAutomation.AccessibilityEventFilter {

        private final View mViewRoot;
        private final String mWindowTitle;
        private final UiAutomation mUiAutomation;
        private final Rect mTempBounds = new Rect();
        private AccessibilityWindowInfo mTargetWindow;

        WindowLocationFilter(@NonNull UiAutomation uiAutomation, @NonNull View viewRoot,
                @NonNull String windowTitle) {
            mUiAutomation = uiAutomation;
            mViewRoot = viewRoot;
            mWindowTitle = windowTitle;
        }
        @Override
        public boolean accept(AccessibilityEvent event) {
            final AccessibilityWindowInfo window = findWindowByTitle(mUiAutomation, mWindowTitle);
            if (window == null) return false;

            window.getBoundsInScreen(mTempBounds);
            final int[] location = new int[2];
            mViewRoot.getLocationOnScreen(location);
            if (DisplayUtils.fuzzyBoundsInScreenSameOrigin(location, mTempBounds)) {
                mTargetWindow = window;
                return  true;
            }
            return false;
        }

        public void reset() {
            mTargetWindow = null;
        }

        public AccessibilityWindowInfo getTargetWindow() {
            return mTargetWindow;
        }
    }
}
