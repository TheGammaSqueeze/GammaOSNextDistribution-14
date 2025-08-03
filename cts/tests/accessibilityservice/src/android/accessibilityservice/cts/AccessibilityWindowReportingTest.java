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

package android.accessibilityservice.cts;

import static android.accessibilityservice.cts.utils.AccessibilityEventFilterUtils.filterWaitForAll;
import static android.accessibilityservice.cts.utils.AccessibilityEventFilterUtils.filterWindowsChangeTypesAndWindowTitle;
import static android.accessibilityservice.cts.utils.AccessibilityEventFilterUtils.filterWindowsChangedWithChangeTypes;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.findWindowByTitle;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.findWindowByTitleAndDisplay;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.getActivityTitle;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.launchActivityAndWaitForItToBeOnscreen;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.launchActivityOnSpecifiedDisplayAndWaitForItToBeOnscreen;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.supportsMultiDisplay;
import static android.accessibilityservice.cts.utils.DisplayUtils.VirtualDisplaySession;
import static android.accessibilityservice.cts.utils.WindowCreationUtils.TOP_WINDOW_TITLE;
import static android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE;
import static android.view.accessibility.AccessibilityEvent.TYPE_WINDOWS_CHANGED;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_ACCESSIBILITY_FOCUSED;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_ACTIVE;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_ADDED;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_BOUNDS;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_CHILDREN;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_FOCUSED;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_TITLE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.cts.activities.AccessibilityWindowReportingActivity;
import android.accessibilityservice.cts.activities.NonDefaultDisplayActivity;
import android.accessibilityservice.cts.activities.NotTouchableWindowTestActivity;
import android.accessibilityservice.cts.utils.ActivityLaunchUtils;
import android.accessibilityservice.cts.utils.DisplayUtils;
import android.accessibilityservice.cts.utils.WindowCreationUtils;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Tests that window changes produce the correct events and that AccessibilityWindowInfos are
 * properly populated
 */
@RunWith(AndroidJUnit4.class)
@CddTest(requirements = {"3.10/C-1-1,C-1-2"})
public class AccessibilityWindowReportingTest {
    private static final int TIMEOUT_ASYNC_PROCESSING = 5000;
    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;
    private Activity mActivity;
    private CharSequence mActivityTitle;

    private final ActivityTestRule<AccessibilityWindowReportingActivity> mActivityRule =
            new ActivityTestRule<>(AccessibilityWindowReportingActivity.class, false, false);

    private final AccessibilityDumpOnFailureRule mDumpOnFailureRule =
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
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        sUiAutomation.setServiceInfo(info);
    }

    @AfterClass
    public static void finalTearDown() {
        sUiAutomation.destroy();
    }

    @Before
    public void setUp() throws Exception {
        mActivity = launchActivityAndWaitForItToBeOnscreen(
                sInstrumentation, sUiAutomation, mActivityRule);
        mActivityTitle = getActivityTitle(sInstrumentation, mActivity);
    }

    private static boolean perDisplayFocusEnabled() {
        return sInstrumentation.getTargetContext().getResources()
                .getBoolean(android.R.bool.config_perDisplayFocusEnabled);
    }

    @Test
    @Presubmit
    public void testUpdatedWindowTitle_generatesEventAndIsReturnedByGetTitle() {
        final String updatedTitle = "Updated Title";
        try {
            sUiAutomation.executeAndWaitForEvent(
                    () -> sInstrumentation.runOnMainSync(() -> mActivity.setTitle(updatedTitle)),
                    filterWindowsChangedWithChangeTypes(WINDOWS_CHANGE_TITLE),
                    TIMEOUT_ASYNC_PROCESSING);
        } catch (TimeoutException exception) {
            throw new RuntimeException(
                    "Failed to get windows changed event for title update", exception);
        }
        final AccessibilityWindowInfo window = findWindowByTitle(sUiAutomation, updatedTitle);
        assertWithMessage("Updated window title not reported to accessibility")
                .that(window).isNotNull();
        window.recycle();
    }

    @Test
    @Presubmit
    public void testWindowAddedMovedAndRemoved_generatesEventsForAllThree() throws Exception {
        final WindowManager.LayoutParams paramsForTop =
                WindowCreationUtils.layoutParamsForWindowOnTop(
                        sInstrumentation, mActivity, TOP_WINDOW_TITLE);
        final WindowManager.LayoutParams paramsForBottom = layoutParamsForWindowOnBottom();
        final Button button = new Button(mActivity);
        button.setText(R.string.button1);

        WindowCreationUtils.addWindowAndWaitForEvent(sUiAutomation, sInstrumentation, mActivity,
                button, paramsForTop, filterWindowsChangedWithChangeTypes(WINDOWS_CHANGE_ADDED));

        // Move window from top to bottom
        sUiAutomation.executeAndWaitForEvent(() -> sInstrumentation.runOnMainSync(
                () -> mActivity.getWindowManager().updateViewLayout(button, paramsForBottom)),
                filterWindowsChangedWithChangeTypes(WINDOWS_CHANGE_BOUNDS),
                TIMEOUT_ASYNC_PROCESSING);
        // Remove the view
        WindowCreationUtils.removeWindow(sUiAutomation, sInstrumentation, mActivity, button);
    }

    @Test
    @Ignore("b/325640120")
    public void putWindowInPictureInPicture_generatesEventAndReportsProperty() throws Exception {
        if (!sInstrumentation.getContext().getPackageManager()
                .hasSystemFeature(FEATURE_PICTURE_IN_PICTURE)) {
            return;
        }
        sUiAutomation.executeAndWaitForEvent(
                () -> sInstrumentation.runOnMainSync(() -> mActivity.enterPictureInPictureMode()),
                (event) -> {
                    if (event.getEventType() != TYPE_WINDOWS_CHANGED) return false;
                    // Look for a picture-in-picture window
                    final List<AccessibilityWindowInfo> windows = sUiAutomation.getWindows();
                    final int windowCount = windows.size();
                    for (int i = 0; i < windowCount; i++) {
                        if (windows.get(i).isInPictureInPictureMode()) {
                            return true;
                        }
                    }
                    return false;
                }, TIMEOUT_ASYNC_PROCESSING);

        // There should be exactly one picture-in-picture window now
        int numPictureInPictureWindows = 0;
        final List<AccessibilityWindowInfo> windows = sUiAutomation.getWindows();
        final int windowCount = windows.size();
        for (int i = 0; i < windowCount; i++) {
            final AccessibilityWindowInfo window = windows.get(i);
            if (window.isInPictureInPictureMode()) {
                numPictureInPictureWindows++;
            }
        }
        assertThat(numPictureInPictureWindows).isAtLeast(1);
    }

    @Test
    @Presubmit
    public void moveFocusToAnotherWindow_generatesEventsAndMovesActiveAndFocus() throws Exception {
        final View topWindowView = showTopWindowAndWaitForItToShowUp();
        final AccessibilityWindowInfo topWindow =
                findWindowByTitle(sUiAutomation, TOP_WINDOW_TITLE);

        AccessibilityWindowInfo activityWindow = findWindowByTitle(sUiAutomation, mActivityTitle);
        final AccessibilityNodeInfo buttonNode =
                topWindow.getRoot().findAccessibilityNodeInfosByText(
                        sInstrumentation.getContext().getString(R.string.button1)).get(0);

        // Make sure activityWindow is not focused
        if (activityWindow.isFocused()) {
            sUiAutomation.executeAndWaitForEvent(
                    () -> buttonNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS),
                    filterWindowsChangedWithChangeTypes(WINDOWS_CHANGE_FOCUSED),
                    TIMEOUT_ASYNC_PROCESSING);
        }

        // Windows may have changed - refresh
        activityWindow = findWindowByTitle(sUiAutomation, mActivityTitle);
        assertThat(activityWindow.isActive()).isFalse();
        assertThat(activityWindow.isFocused()).isFalse();

        // Find a focusable view in the main activity menu
        final AccessibilityNodeInfo autoCompleteTextInfo = activityWindow.getRoot()
                .findAccessibilityNodeInfosByViewId(
                        "android.accessibilityservice.cts:id/autoCompleteLayout")
                .get(0);
        assertThat(autoCompleteTextInfo).isNotNull();

        // Remove the top window and focus on the main activity
        sUiAutomation.executeAndWaitForEvent(
                () -> {
                    sInstrumentation.runOnMainSync(
                            () -> mActivity.getWindowManager().removeView(topWindowView));
                    buttonNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                },
                filterWindowsChangedWithChangeTypes(WINDOWS_CHANGE_FOCUSED | WINDOWS_CHANGE_ACTIVE),
                TIMEOUT_ASYNC_PROCESSING);
    }

    @Test
    @Presubmit
    public void moveFocusToAnotherDisplay_movesActiveAndFocusWindow() throws Exception {
        assumeTrue(supportsMultiDisplay(sInstrumentation.getContext()));

        // Makes sure activityWindow on default display is focused
        AccessibilityWindowInfo activityWindow = findWindowByTitle(sUiAutomation, mActivityTitle);
        assertThat(activityWindow.isActive()).isTrue();
        assertThat(activityWindow.isFocused()).isTrue();

        // Creates a virtual display.
        try (VirtualDisplaySession displaySession = new VirtualDisplaySession()) {
            final int virtualDisplayId =
                    displaySession.createDisplayWithDefaultDisplayMetricsAndWait(
                            sInstrumentation.getContext(), false).getDisplayId();
            // Launches an activity on virtual display.
            final Activity activityOnVirtualDisplay =
                    launchActivityOnSpecifiedDisplayAndWaitForItToBeOnscreen(sInstrumentation,
                            sUiAutomation,
                            NonDefaultDisplayActivity.class,
                            virtualDisplayId);

            final CharSequence activityTitle = getActivityTitle(sInstrumentation,
                    activityOnVirtualDisplay);

            // Window manager changed the behavior of focused window at a virtual display. A window
            // at virtual display needs to be touched then it becomes to be focused one. Adding this
            // touch event on the activity window of the virtual display to pass this test case.
            sUiAutomation.executeAndWaitForEvent(
                    () -> DisplayUtils.touchDisplay(sUiAutomation, virtualDisplayId, activityTitle),
                    filterWindowsChangedWithChangeTypes(WINDOWS_CHANGE_FOCUSED |
                            WINDOWS_CHANGE_ACTIVE),
                    TIMEOUT_ASYNC_PROCESSING);

            // Make sure activityWindow on virtual display is focused.
            AccessibilityWindowInfo activityWindowOnVirtualDisplay =
                    findWindowByTitleAndDisplay(sUiAutomation, activityTitle, virtualDisplayId);
            // Windows may have changed - refresh.
            activityWindow = findWindowByTitle(sUiAutomation, mActivityTitle);
            try {
                if (!perDisplayFocusEnabled()) {
                    assertThat(activityWindow.isActive()).isFalse();
                    assertThat(activityWindow.isFocused()).isFalse();
                } else {
                    assertThat(activityWindow.isActive()).isTrue();
                    assertThat(activityWindow.isFocused()).isTrue();
                }
                assertThat(activityWindowOnVirtualDisplay.isActive()).isTrue();
                assertThat(activityWindowOnVirtualDisplay.isFocused()).isTrue();
            } finally {
                sUiAutomation.executeAndWaitForEvent(
                        () -> sInstrumentation.runOnMainSync(activityOnVirtualDisplay::finish),
                        filterWaitForAll(
                                filterWindowsChangedWithChangeTypes(
                                        WINDOWS_CHANGE_FOCUSED | WINDOWS_CHANGE_ACTIVE),
                                event -> {
                                    // The focused window should be returned to activity at
                                    // default display after
                                    // the activity at virtual display is destroyed.
                                    AccessibilityWindowInfo window = findWindowByTitle(
                                            sUiAutomation, mActivityTitle);
                                    return window.isActive() && window.isFocused();
                                }),
                        TIMEOUT_ASYNC_PROCESSING);
            }
        }
    }

    @Test
    @Presubmit
    public void testChangeAccessibilityFocusWindow_getEvent() throws Exception {
        final AccessibilityServiceInfo info = sUiAutomation.getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
        sUiAutomation.setServiceInfo(info);
        View topWindowView = null;
        try {
            topWindowView = showTopWindowAndWaitForItToShowUp();

            final AccessibilityWindowInfo activityWindow =
                    findWindowByTitle(sUiAutomation, mActivityTitle);
            final AccessibilityWindowInfo topWindow =
                    findWindowByTitle(sUiAutomation, TOP_WINDOW_TITLE);
            final AccessibilityNodeInfo win2Node =
                    topWindow.getRoot().findAccessibilityNodeInfosByText(
                            sInstrumentation.getContext().getString(R.string.button1)).get(0);
            final AccessibilityNodeInfo win1Node = activityWindow.getRoot()
                    .findAccessibilityNodeInfosByViewId(
                            "android.accessibilityservice.cts:id/autoCompleteLayout")
                    .get(0);

            sUiAutomation.executeAndWaitForEvent(
                    () -> {
                        win2Node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
                        win1Node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
                    },
                    filterWindowsChangedWithChangeTypes(WINDOWS_CHANGE_ACCESSIBILITY_FOCUSED),
                    TIMEOUT_ASYNC_PROCESSING);
        } finally {
            info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
            sUiAutomation.setServiceInfo(info);
            // Remove the view
            if (topWindowView != null) {
                WindowCreationUtils.removeWindow(sUiAutomation, sInstrumentation, mActivity,
                        topWindowView);
            }
        }
    }

    @Test
    public void testGetAnchorForDropDownForAutoCompleteTextView_returnsTextViewNode() {
        final AutoCompleteTextView autoCompleteTextView =
                mActivity.findViewById(R.id.autoCompleteLayout);
        final AccessibilityNodeInfo autoCompleteTextInfo = sUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByViewId(
                        "android.accessibilityservice.cts:id/autoCompleteLayout")
                .get(0);

        // For the drop-down
        final String[] countries = new String[]{"Belgium", "France", "Italy", "Germany", "Spain"};

        try {
            sUiAutomation.executeAndWaitForEvent(() -> sInstrumentation.runOnMainSync(
                    () -> {
                        final ArrayAdapter<String> adapter = new ArrayAdapter<>(
                                mActivity, android.R.layout.simple_dropdown_item_1line, countries);
                        autoCompleteTextView.setAdapter(adapter);
                        autoCompleteTextView.showDropDown();
                    }),
                    filterWindowsChangeTypesAndWindowTitle(sUiAutomation, WINDOWS_CHANGE_CHILDREN,
                            mActivityTitle.toString()), TIMEOUT_ASYNC_PROCESSING);
        } catch (TimeoutException exception) {
            throw new RuntimeException(
                    "Failed to get window changed event when showing dropdown", exception);
        }

        // Find the pop-up window
        boolean foundPopup = false;
        final List<AccessibilityWindowInfo> windows = sUiAutomation.getWindows();
        for (int i = 0; i < windows.size(); i++) {
            final AccessibilityWindowInfo window = windows.get(i);
            if (window.getAnchor() == null) {
                continue;
            }
            assertThat(window.getAnchor()).isEqualTo(autoCompleteTextInfo);
            assertWithMessage("Found multiple pop-ups anchored to one text view")
                    .that(foundPopup).isFalse();
            foundPopup = true;
        }
        assertWithMessage("Failed to find accessibility window for auto-complete pop-up")
                .that(foundPopup).isTrue();
    }

    @AppModeFull
    @Test
    public void showNotTouchableWindow_activityWindowIsNotVisible() throws TimeoutException {
        try {
            launchNotTouchableWindowTestActivityFromShell();

            Intent intent = new Intent();
            intent.setAction(NotTouchableWindowTestActivity.ADD_WINDOW);
            intent.setPackage(sInstrumentation.getContext().getPackageName());

            // Waits for two events, whose order is nondeterministic:
            //  (1) the test activity is covered by the untrusted non-touchable window.
            //  (2) the untrusted non-touchable window is added.
            sendIntentAndWaitForEvent(intent,
                    filterWaitForAll(
                            event -> {
                                final AccessibilityWindowInfo coveredWindow =
                                        findWindowByTitle(sUiAutomation,
                                                NotTouchableWindowTestActivity.TITLE);
                                return coveredWindow == null;
                            },
                            filterWindowsChangeTypesAndWindowTitle(sUiAutomation,
                                    WINDOWS_CHANGE_ADDED,
                                    NotTouchableWindowTestActivity.NON_TOUCHABLE_WINDOW_TITLE)
                    ));
        } finally {
            closeNotTouchableWindowTestActivity();
        }
    }

    @AppModeFull
    @Test
    public void showNotTouchableTrustedWindow_activityWindowIsVisible() {
        try {
            launchNotTouchableWindowTestActivityFromShell();

            Intent intent = new Intent();
            intent.setAction(NotTouchableWindowTestActivity.ADD_TRUSTED_WINDOW);
            intent.setPackage(sInstrumentation.getContext().getPackageName());

            SystemUtil.runWithShellPermissionIdentity(sUiAutomation,
                    () -> sendIntentAndWaitForEvent(intent,
                            filterWindowsChangeTypesAndWindowTitle(sUiAutomation,
                                    WINDOWS_CHANGE_ADDED,
                                    NotTouchableWindowTestActivity.NON_TOUCHABLE_WINDOW_TITLE)),
                    Manifest.permission.INTERNAL_SYSTEM_WINDOW);

            assertThat(findWindowByTitle(sUiAutomation, NotTouchableWindowTestActivity.TITLE))
                    .isNotNull();
        } finally {
            closeNotTouchableWindowTestActivity();
        }
    }

    // We want to test WindowState#isTrustedOverlay which refers to flag stored in the
    // Session class and is not updated since the Session is created.
    // Use shell command instead of ActivityLaunchUtils to get INTERNAL_SYSTEM_WINDOW
    // permission when the Session is created.
    private void launchNotTouchableWindowTestActivityFromShell() {
        SystemUtil.runWithShellPermissionIdentity(sUiAutomation,
                () -> sUiAutomation.executeAndWaitForEvent(
                        () -> {
                            final ComponentName componentName = new ComponentName(
                                    sInstrumentation.getContext(),
                                    NotTouchableWindowTestActivity.class);

                            String command = "am start -n " + componentName.flattenToString();
                            try {
                                SystemUtil.runShellCommand(sInstrumentation, command);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        },
                        (event) -> {
                            final AccessibilityWindowInfo window =
                                    findWindowByTitleAndDisplay(sUiAutomation,
                                            NotTouchableWindowTestActivity.TITLE, 0);
                            return window != null;
                        }, TIMEOUT_ASYNC_PROCESSING), Manifest.permission.INTERNAL_SYSTEM_WINDOW);
    }

    private void closeNotTouchableWindowTestActivity() {
        final Intent intent = new Intent();
        intent.setAction(NotTouchableWindowTestActivity.FINISH_ACTIVITY);
        intent.setPackage(sInstrumentation.getContext().getPackageName());
        // Call finish() on the window. This is required to launch more activities in any subsequent
        // tests from this same app process.
        sInstrumentation.runOnMainSync(() -> sInstrumentation.getContext().sendBroadcast(intent));
        // Ensure we're at the home screen before continuing to other tests.
        // finish() should do this, but sometimes takes longer than expected.
        ActivityLaunchUtils.homeScreenOrBust(sInstrumentation.getContext(), sUiAutomation);
    }

    /**
     * Test whether we can successfully enable and disable window animations.
     */
    @Test
    public void testDisableWindowAnimations() {
        setAndAssertAnimationScale(0.0f);
        setAndAssertAnimationScale(0.5f);
        setAndAssertAnimationScale(1.0f);
    }

    /** Sets the animation scale to a specified value and asserts that the value has been set. */
    private void setAndAssertAnimationScale(float value) {
        Context context = sInstrumentation.getContext();
        sUiAutomation.setAnimationScale(value);
        assertThat(getGlobalFloat(context, Settings.Global.WINDOW_ANIMATION_SCALE))
                .isEqualTo(value);
        assertThat(getGlobalFloat(context, Settings.Global.TRANSITION_ANIMATION_SCALE))
                .isEqualTo(value);
        assertThat(getGlobalFloat(context, Settings.Global.ANIMATOR_DURATION_SCALE))
                .isEqualTo(value);
    }

    /** Returns value of constants in Settings.Global. */
    private static float getGlobalFloat(Context context, String constantName) {
        return Settings.Global.getFloat(context.getContentResolver(), constantName, -1);
    }

    private View showTopWindowAndWaitForItToShowUp() throws TimeoutException {
        final WindowManager.LayoutParams paramsForTop =
                WindowCreationUtils.layoutParamsForWindowOnTop(
                        sInstrumentation, mActivity, TOP_WINDOW_TITLE);
        final Button button = new Button(mActivity);
        button.setText(R.string.button1);

        WindowCreationUtils.addWindowAndWaitForEvent(sUiAutomation, sInstrumentation, mActivity,
                button, paramsForTop, (event) -> (event.getEventType() == TYPE_WINDOWS_CHANGED)
                        && (findWindowByTitle(sUiAutomation, mActivityTitle) != null)
                        && (findWindowByTitle(sUiAutomation, TOP_WINDOW_TITLE) != null));
        return button;
    }

    private WindowManager.LayoutParams layoutParamsForWindowOnBottom() {
        final WindowManager.LayoutParams params = WindowCreationUtils.layoutParamsForTestWindow(
                sInstrumentation, mActivity);
        params.gravity = Gravity.BOTTOM;
        return params;
    }

    private void sendIntentAndWaitForEvent(Intent intent,
            UiAutomation.AccessibilityEventFilter filter) throws TimeoutException {
        sUiAutomation.executeAndWaitForEvent(() -> sInstrumentation.runOnMainSync(
                () -> sInstrumentation.getContext().sendBroadcast(intent)),
                filter,
                TIMEOUT_ASYNC_PROCESSING);
    }

}
