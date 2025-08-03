/**
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.accessibilityservice.cts;

import static android.accessibilityservice.cts.utils.AccessibilityEventFilterUtils.filterForEventTypeWithAction;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.launchActivityAndWaitForItToBeOnscreen;
import static android.accessibilityservice.cts.utils.AsyncUtils.DEFAULT_TIMEOUT_MS;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.cts.activities.AccessibilityFocusAndInputFocusSyncActivity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Environment;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.view.Display;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.BitmapUtils;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test cases for testing the accessibility focus APIs exposed to accessibility
 * services. These APIs allow moving accessibility focus in the view tree from
 * an AccessiiblityService. Specifically, this activity is for verifying the the
 * sync between accessibility and input focus.
 */
@RunWith(AndroidJUnit4.class)
@CddTest(requirements = {"3.10/C-1-1,C-1-2"})
@Presubmit
public class AccessibilityFocusAndInputFocusSyncTest {
    /**
     * The delay time is for next UI frame rendering out.
     */
    private static final long SCREEN_FRAME_RENDERING_OUT_TIME_MILLIS = 500;

    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;
    private static Context sContext;
    private static AccessibilityManager sAccessibilityManager;
    private static int sFocusStrokeWidthDefaultValue;
    private static int sFocusColorDefaultValue;

    private AccessibilityFocusAndInputFocusSyncActivity mActivity;

    private ActivityTestRule<AccessibilityFocusAndInputFocusSyncActivity> mActivityRule =
            new ActivityTestRule<>(AccessibilityFocusAndInputFocusSyncActivity.class, false, false);

    private InstrumentedAccessibilityServiceTestRule<StubFocusIndicatorService>
            mFocusIndicatorServiceRule = new InstrumentedAccessibilityServiceTestRule<>(
            StubFocusIndicatorService.class, false);

    private AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    @Rule
    public final RuleChain mRuleChain = RuleChain
            .outerRule(mActivityRule)
            .around(mFocusIndicatorServiceRule)
            .around(mDumpOnFailureRule);

    /* Test name rule that tracks the current test method under execution */
    @Rule public TestName mTestName = new TestName();

    @BeforeClass
    public static void oneTimeSetup() throws Exception {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sUiAutomation = sInstrumentation.getUiAutomation(
                UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);

        sContext = sInstrumentation.getContext();
        sAccessibilityManager = sContext.getSystemService(AccessibilityManager.class);
        assertNotNull(sAccessibilityManager);
        sFocusStrokeWidthDefaultValue = sAccessibilityManager.getAccessibilityFocusStrokeWidth();
        sFocusColorDefaultValue = sAccessibilityManager.getAccessibilityFocusColor();
    }

    @AfterClass
    public static void postTestTearDown() {
        sUiAutomation.destroy();
    }

    @Before
    public void setUp() throws Exception {
        AccessibilityServiceInfo info = sUiAutomation.getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
        info.flags &= ~AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        sUiAutomation.setServiceInfo(info);

        mActivity = launchActivityAndWaitForItToBeOnscreen(
                sInstrumentation, sUiAutomation, mActivityRule);
    }

    @MediumTest
    @Test
    public void testFindAccessibilityFocus() throws Exception {
        sInstrumentation.runOnMainSync(() -> {
            mActivity.findViewById(R.id.firstEditText).requestFocus();
        });
        // Get the view that has input and accessibility focus.
        final AccessibilityNodeInfo expected = sUiAutomation
                .getRootInActiveWindow().findAccessibilityNodeInfosByText(
                        sContext.getString(R.string.firstEditText)).get(0);
        assertNotNull(expected);
        assertFalse(expected.isAccessibilityFocused());
        assertTrue(expected.isFocused());

        sUiAutomation.executeAndWaitForEvent(
                () -> assertTrue(expected.performAction(ACTION_ACCESSIBILITY_FOCUS)),
                filterForEventTypeWithAction(
                        TYPE_VIEW_ACCESSIBILITY_FOCUSED, ACTION_ACCESSIBILITY_FOCUS),
                DEFAULT_TIMEOUT_MS);

        // Get the second expected node info.
        AccessibilityNodeInfo received = sUiAutomation
                .getRootInActiveWindow().findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        assertNotNull(received);
        assertTrue(received.isAccessibilityFocused());

        // Make sure we got the expected focusable.
        assertEquals(expected, received);
    }

    @MediumTest
    @Test
    public void testInitialStateNoAccessibilityFocus() throws Exception {
        // Get the root which is only accessibility focused.
        AccessibilityNodeInfo focused = sUiAutomation
                .getRootInActiveWindow().findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        assertNull(focused);
    }

    @MediumTest
    @Test
    public void testActionAccessibilityFocus() throws Exception {
        // Get the root linear layout info.
        final AccessibilityNodeInfo rootLinearLayout = sUiAutomation
                .getRootInActiveWindow().findAccessibilityNodeInfosByText(
                        sContext.getString(R.string.rootLinearLayout)).get(0);
        assertNotNull(rootLinearLayout);
        assertFalse(rootLinearLayout.isAccessibilityFocused());

        sUiAutomation.executeAndWaitForEvent(
                () -> assertTrue(rootLinearLayout.performAction(ACTION_ACCESSIBILITY_FOCUS)),
                filterForEventTypeWithAction(
                        TYPE_VIEW_ACCESSIBILITY_FOCUSED, ACTION_ACCESSIBILITY_FOCUS),
                DEFAULT_TIMEOUT_MS);

        // Get the node info again.
        rootLinearLayout.refresh();

        // Check if the node info is focused.
        assertTrue(rootLinearLayout.isAccessibilityFocused());
    }

    @MediumTest
    @Test
    public void testActionClearAccessibilityFocus() throws Exception {
        // Get the root linear layout info.
        final AccessibilityNodeInfo rootLinearLayout = sUiAutomation
                .getRootInActiveWindow().findAccessibilityNodeInfosByText(
                        sContext.getString(R.string.rootLinearLayout)).get(0);
        assertNotNull(rootLinearLayout);

        sUiAutomation.executeAndWaitForEvent(
                () -> assertTrue(rootLinearLayout.performAction(ACTION_ACCESSIBILITY_FOCUS)),
                filterForEventTypeWithAction(
                        TYPE_VIEW_ACCESSIBILITY_FOCUSED, ACTION_ACCESSIBILITY_FOCUS),
                DEFAULT_TIMEOUT_MS);

        // Refresh the node info.
        rootLinearLayout.refresh();

        // Check if the node info is focused.
        assertTrue(rootLinearLayout.isAccessibilityFocused());

        sUiAutomation.executeAndWaitForEvent(
                () -> assertTrue(rootLinearLayout.performAction(ACTION_CLEAR_ACCESSIBILITY_FOCUS)),
                filterForEventTypeWithAction(
                        TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED, ACTION_CLEAR_ACCESSIBILITY_FOCUS),
                DEFAULT_TIMEOUT_MS);

        // Refresh the node info.
        rootLinearLayout.refresh();

        // Check if the node info is not focused.
        assertFalse(rootLinearLayout.isAccessibilityFocused());
    }

    @MediumTest
    @Test
    public void testOnlyOneNodeHasAccessibilityFocus() throws Exception {
        // Get the first not focused edit text.
        final AccessibilityNodeInfo firstEditText = sUiAutomation
                .getRootInActiveWindow().findAccessibilityNodeInfosByText(
                        sContext.getString(R.string.firstEditText)).get(0);
        assertNotNull(firstEditText);
        assertTrue(firstEditText.isFocusable());
        assertFalse(firstEditText.isAccessibilityFocused());

        sUiAutomation.executeAndWaitForEvent(
                () -> assertTrue(firstEditText.performAction(ACTION_ACCESSIBILITY_FOCUS)),
                filterForEventTypeWithAction(
                        TYPE_VIEW_ACCESSIBILITY_FOCUSED, ACTION_ACCESSIBILITY_FOCUS),
                DEFAULT_TIMEOUT_MS);

        // Get the second not focused edit text.
        final AccessibilityNodeInfo secondEditText = sUiAutomation
                .getRootInActiveWindow().findAccessibilityNodeInfosByText(
                        sContext.getString(R.string.secondEditText)).get(0);
        assertNotNull(secondEditText);
        assertTrue(secondEditText.isFocusable());
        assertFalse(secondEditText.isFocused());
        assertFalse(secondEditText.isAccessibilityFocused());

        sUiAutomation.executeAndWaitForEvent(
                () -> assertTrue(secondEditText.performAction(ACTION_ACCESSIBILITY_FOCUS)),
                filterForEventTypeWithAction(
                        TYPE_VIEW_ACCESSIBILITY_FOCUSED, ACTION_ACCESSIBILITY_FOCUS),
                DEFAULT_TIMEOUT_MS);

        // Get the node info again.
        secondEditText.refresh();

        // Make sure no other node has accessibility focus.
        AccessibilityNodeInfo root = sUiAutomation.getRootInActiveWindow();
        Queue<AccessibilityNodeInfo> workQueue = new LinkedList<AccessibilityNodeInfo>();
        workQueue.add(root);
        while (!workQueue.isEmpty()) {
            AccessibilityNodeInfo current = workQueue.poll();
            if (current.isAccessibilityFocused() && !current.equals(secondEditText)) {
                fail();
            }
            final int childCount = current.getChildCount();
            for (int i = 0; i < childCount; i++) {
                AccessibilityNodeInfo child = current.getChild(i);
                if (child != null) {
                    workQueue.offer(child);
                }
            }
        }
    }

    @Test
    public void testScreenReaderFocusableAttribute_reportedToAccessibility() {
        final AccessibilityNodeInfo secondButton = sUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByText(
                        sContext.getString(R.string.secondButton)).get(0);
        assertTrue("Screen reader focusability not propagated from xml to accessibility",
                secondButton.isScreenReaderFocusable());

        // Verify the setter and getter work
        final AtomicBoolean isScreenReaderFocusableAtomic = new AtomicBoolean(false);
        sInstrumentation.runOnMainSync(() -> {
            View secondButtonView = mActivity.findViewById(R.id.secondButton);
            secondButtonView.setScreenReaderFocusable(false);
            isScreenReaderFocusableAtomic.set(secondButtonView.isScreenReaderFocusable());
        });

        assertFalse("isScreenReaderFocusable did not change after value set",
                isScreenReaderFocusableAtomic.get());

        secondButton.refresh();
        assertFalse(
                "Screen reader focusability not propagated to accessibility after calling setter",
                secondButton.isScreenReaderFocusable());
    }

    @Test
    public void testSetFocusAppearanceDataAfterServiceEnabled() {
        final StubFocusIndicatorService service =
                mFocusIndicatorServiceRule.enableService();
        final int focusColor = sFocusColorDefaultValue == Color.BLUE ? Color.RED : Color.BLUE;

        try {
            setFocusAppearanceDataAndCheckItCorrect(service, sFocusStrokeWidthDefaultValue + 10,
                    focusColor);
        } finally {
            setFocusAppearanceDataAndCheckItCorrect(service, sFocusStrokeWidthDefaultValue,
                    sFocusColorDefaultValue);

            service.disableSelfAndRemove();
        }
    }

    @Test
    public void testChangeFocusColor_expectedColorIsChanged() throws Exception {
        final StubFocusIndicatorService service =
                mFocusIndicatorServiceRule.enableService();

        try {
            // Get the root linear layout info.
            final AccessibilityNodeInfo rootLinearLayout = sUiAutomation
                    .getRootInActiveWindow().findAccessibilityNodeInfosByText(
                            sContext.getString(R.string.rootLinearLayout)).get(0);

            final Bitmap blueColorFocusScreenshot = screenshotAfterChangeFocusColor(service,
                    rootLinearLayout, Color.BLUE);

            final Bitmap redColorFocusScreenshot = screenshotAfterChangeFocusColor(service,
                    rootLinearLayout, Color.RED);

            assertTrue(isBitmapDifferent(blueColorFocusScreenshot, redColorFocusScreenshot));
        } finally {
            setFocusAppearanceDataAndCheckItCorrect(service, sFocusStrokeWidthDefaultValue,
                    sFocusColorDefaultValue);

            service.disableSelfAndRemove();
        }
    }

    private Bitmap screenshotAfterChangeFocusColor(StubFocusIndicatorService service,
            AccessibilityNodeInfo unAccessibilityFocusedNode, int color) throws Exception {
        assertFalse(unAccessibilityFocusedNode.isAccessibilityFocused());

        setFocusAppearanceDataAndCheckItCorrect(service, sFocusStrokeWidthDefaultValue, color);
        sUiAutomation.executeAndWaitForEvent(
                () -> assertTrue(unAccessibilityFocusedNode.performAction(
                        ACTION_ACCESSIBILITY_FOCUS)),
                filterForEventTypeWithAction(TYPE_VIEW_ACCESSIBILITY_FOCUSED,
                        ACTION_ACCESSIBILITY_FOCUS),
                DEFAULT_TIMEOUT_MS);
        Thread.sleep(SCREEN_FRAME_RENDERING_OUT_TIME_MILLIS);

        final Bitmap screenshot = sUiAutomation.takeScreenshot();

        sUiAutomation.executeAndWaitForEvent(
                () -> assertTrue(unAccessibilityFocusedNode.performAction(
                        ACTION_CLEAR_ACCESSIBILITY_FOCUS)),
                filterForEventTypeWithAction(TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED,
                        ACTION_CLEAR_ACCESSIBILITY_FOCUS),
                DEFAULT_TIMEOUT_MS);

        return screenshot;
    }

    private boolean isBitmapDifferent(Bitmap bitmap1, Bitmap bitmap2) {
        final Display display = mActivity.getWindowManager().getDefaultDisplay();
        final Point displaySize = new Point();
        display.getRealSize(displaySize);

        final int[] pixelsOne = new int[displaySize.x * displaySize.y];
        final Bitmap bitmapOne = bitmap1.copy(Bitmap.Config.ARGB_8888, false);
        bitmapOne.getPixels(pixelsOne, 0, displaySize.x, 0, 0, displaySize.x,
                displaySize.y);

        final int[] pixelsTwo = new int[displaySize.x * displaySize.y];
        final Bitmap bitmapTwo = bitmap2.copy(Bitmap.Config.ARGB_8888, false);
        bitmapTwo.getPixels(pixelsTwo, 0, displaySize.x, 0, 0, displaySize.x,
                displaySize.y);

        for (int i = pixelsOne.length - 1; i > 0; i--) {
            if ((Color.red(pixelsOne[i]) != Color.red(pixelsTwo[i]))
                    || (Color.green(pixelsOne[i]) != Color.green(pixelsTwo[i]))
                    || (Color.blue(pixelsOne[i]) != Color.blue(pixelsTwo[i]))) {
                return true;
            }
        }

        saveFailureScreenshot(bitmap1, bitmap2);
        return false;
    }

    private void saveFailureScreenshot(Bitmap bitmap1, Bitmap bitmap2) {
        final String directoryName = Environment.getExternalStorageDirectory()
                + "/" + getClass().getSimpleName();

        final String fileName1 = String.format("%s_%s_%s.png", mTestName.getMethodName(), "Bitmap1",
                SystemClock.uptimeMillis());
        BitmapUtils.saveBitmap(bitmap1, directoryName, fileName1);

        final String fileName2 = String.format("%s_%s_%s.png", mTestName.getMethodName(), "Bitmap2",
                SystemClock.uptimeMillis());
        BitmapUtils.saveBitmap(bitmap2, directoryName, fileName2);
    }

    private void setFocusAppearanceDataAndCheckItCorrect(StubFocusIndicatorService service,
            int focusStrokeWidthValue, int focusColorValue) {
        service.setAccessibilityFocusAppearance(focusStrokeWidthValue,
                focusColorValue);
        // Checks if the color and the stroke values from AccessibilityManager is
        // updated as in expectation.
        PollingCheck.waitFor(()->isFocusAppearanceDataUpdated(sAccessibilityManager,
                focusStrokeWidthValue, focusColorValue));
    }

    private static boolean isFocusAppearanceDataUpdated(AccessibilityManager manager,
            int strokeWidth, int color) {
        return manager.getAccessibilityFocusStrokeWidth() == strokeWidth
                && manager.getAccessibilityFocusColor() == color;
    }
}
