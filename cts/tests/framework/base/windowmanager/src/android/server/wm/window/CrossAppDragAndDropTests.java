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

package android.server.wm.window;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.server.wm.CliIntentExtra.extraString;
import static android.server.wm.UiDeviceUtils.dragPointer;
import static android.server.wm.dndsourceapp.Components.DRAG_SOURCE;
import static android.server.wm.dndtargetapp.Components.DROP_TARGET;
import static android.server.wm.dndtargetappsdk23.Components.DROP_TARGET_SDK23;
import static android.view.Display.DEFAULT_DISPLAY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.DeprecatedTargetSdkUtils;
import android.server.wm.TestLogService;
import android.server.wm.WindowManagerState.Task;
import android.util.Log;
import android.view.Display;

import com.google.common.collect.ImmutableSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Map;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceWindow:CrossAppDragAndDropTests
 */
@Presubmit
@AppModeFull(reason = "Requires android.permission.MANAGE_ACTIVITY_TASKS")
@RunWith(Parameterized.class)
@android.server.wm.annotation.Group2
public class CrossAppDragAndDropTests extends ActivityManagerTestBase {
    private static final String TAG = "CrossAppDragAndDrop";

    private static final int SWIPE_STEPS = 100;

    private static final String FILE_GLOBAL = "file_global";
    private static final String FILE_LOCAL = "file_local";
    private static final String DISALLOW_GLOBAL = "disallow_global";
    private static final String CANCEL_SOON = "cancel_soon";
    private static final String GRANT_NONE = "grant_none";
    private static final String GRANT_READ = "grant_read";
    private static final String GRANT_WRITE = "grant_write";
    private static final String GRANT_READ_PREFIX = "grant_read_prefix";
    private static final String GRANT_READ_NOPREFIX = "grant_read_noprefix";
    private static final String GRANT_READ_PERSISTABLE = "grant_read_persistable";

    private static final String REQUEST_NONE = "request_none";
    private static final String REQUEST_READ = "request_read";
    private static final String REQUEST_READ_NESTED = "request_read_nested";
    private static final String REQUEST_TAKE_PERSISTABLE = "request_take_persistable";
    private static final String REQUEST_WRITE = "request_write";

    private static final String TARGET_ON_RECEIVE_CONTENT_LISTENER_TEXT_VIEW =
            "textview_on_receive_content_listener";
    private static final String TARGET_ON_RECEIVE_CONTENT_LISTENER_EDIT_TEXT =
            "edittext_on_receive_content_listener";
    private static final String TARGET_ON_RECEIVE_CONTENT_LISTENER_LINEAR_LAYOUT =
            "linearlayout_on_receive_content_listener";
    private static final ImmutableSet<String> ON_RECEIVE_CONTENT_LISTENER_MODES = ImmutableSet.of(
            TARGET_ON_RECEIVE_CONTENT_LISTENER_TEXT_VIEW,
            TARGET_ON_RECEIVE_CONTENT_LISTENER_EDIT_TEXT,
            TARGET_ON_RECEIVE_CONTENT_LISTENER_LINEAR_LAYOUT
    );

    private static final String SOURCE_LOG_TAG = "DragSource";
    private static final String TARGET_LOG_TAG = "DropTarget";

    private static final String RESULT_KEY_START_DRAG = "START_DRAG";
    private static final String RESULT_KEY_DRAG_STARTED = "DRAG_STARTED";
    private static final String RESULT_KEY_DRAG_ENDED = "DRAG_ENDED";
    private static final String RESULT_KEY_EXTRAS = "EXTRAS";
    private static final String RESULT_KEY_DROP_RESULT = "DROP";
    private static final String RESULT_KEY_ACCESS_BEFORE = "BEFORE";
    private static final String RESULT_KEY_ACCESS_AFTER = "AFTER";
    private static final String RESULT_KEY_CLIP_DATA_ERROR = "CLIP_DATA_ERROR";
    private static final String RESULT_KEY_CLIP_DESCR_ERROR = "CLIP_DESCR_ERROR";
    private static final String RESULT_KEY_LOCAL_STATE_ERROR = "LOCAL_STATE_ERROR";

    private static final String RESULT_MISSING = "Missing";
    private static final String RESULT_OK = "OK";
    private static final String RESULT_EXCEPTION = "Exception";
    private static final String RESULT_NULL_DROP_PERMISSIONS = "Null DragAndDropPermissions";

    private static final String EXTRA_MODE = "mode";
    private static final String EXTRA_LOGTAG = "logtag";

    private static final ActivityLaunchModeContext SPLIT_SCREEN_LAUNCH_MODE_CONTEXT =
            new ActivityLaunchModeContext() {
                @Override
                void assumeDeviceSupportsLaunchMode() {
                    assumeTrue(mTestCase.supportsSplitScreenMultiWindow());
                }

                @Override
                void launchActivities(ComponentName sourceComponentName, String sourceMode,
                        ComponentName targetComponentName, String targetMode) {
                    mTestCase.launchSplitScreenActivities(
                            sourceComponentName, sourceMode, targetComponentName, targetMode);
                }

                @Override
                public String toString() {
                    return "SplitScreen";
                }
            };

    private static final ActivityLaunchModeContext FREEFORM_LAUNCH_MODE_CONTEXT =
            new ActivityLaunchModeContext() {
                @Override
                void assumeDeviceSupportsLaunchMode() {
                    assumeTrue(mTestCase.supportsFreeform());
                }

                @Override
                void launchActivities(ComponentName sourceComponentName, String sourceMode,
                        ComponentName targetComponentName, String targetMode) throws Exception {
                    mTestCase.launchFreeformActivities(
                            sourceComponentName, sourceMode, targetComponentName, targetMode);
                }

                @Override
                public String toString() {
                    return "Freeform";
                }
            };


    @Parameterized.Parameters(name = "{0}")
    public static Object[] activityLaunchModes() {
        return new Object[] {
                FREEFORM_LAUNCH_MODE_CONTEXT,
                SPLIT_SCREEN_LAUNCH_MODE_CONTEXT
        };
    }

    @Parameterized.Parameter
    public ActivityLaunchModeContext mActivityLaunchModeContext;

    private Map<String, String> mSourceResults;
    private Map<String, String> mTargetResults;

    private String mSessionId;
    private String mSourceLogTag;
    private String mTargetLogTag;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mActivityLaunchModeContext.mTestCase = this;
        mActivityLaunchModeContext.assumeDeviceSupportsLaunchMode();

        // Use uptime in seconds as unique test invocation id.
        mSessionId = Long.toString(SystemClock.uptimeMillis() / 1000);
        mSourceLogTag = SOURCE_LOG_TAG + mSessionId;
        mTargetLogTag = TARGET_LOG_TAG + mSessionId;

        cleanupState();
    }

    @After
    public void tearDown() {
        cleanupState();
    }

    /**
     * Make sure that the special activity stacks are removed and the ActivityManager/WindowManager
     * is in a good state.
     */
    private void cleanupState() {
        stopTestPackage(DRAG_SOURCE.getPackageName());
        stopTestPackage(DROP_TARGET.getPackageName());
        stopTestPackage(DROP_TARGET_SDK23.getPackageName());
    }

    private void launchFreeformActivities(ComponentName sourceComponentName,
            String sourceMode, ComponentName targetComponentName, String targetMode)
            throws Exception {
        // Fallback to try to launch two freeform windows side by side.
        Point displaySize = getDisplaySize();
        launchFreeformActivity(sourceComponentName, sourceMode, mSourceLogTag,
                displaySize, true /* leftSide */);
        launchFreeformActivity(targetComponentName, targetMode, mTargetLogTag,
                displaySize, false /* leftSide */);
    }

    /**
     * @param displaySize size of the display
     * @param leftSide    {@code true} to launch the app taking up the left half of the display,
     *                    {@code false} to launch the app taking up the right half of the display.
     */
    private void launchFreeformActivity(ComponentName componentName, String mode,
            String logtag, Point displaySize, boolean leftSide) throws Exception {
        launchActivity(componentName, WINDOWING_MODE_FREEFORM, extraString("mode", mode),
                extraString("logtag", logtag));
        Point topLeft = new Point(leftSide ? 0 : displaySize.x / 2, 0);
        Point bottomRight = new Point(leftSide ? displaySize.x / 2 : displaySize.x, displaySize.y);
        resizeActivityTask(componentName, topLeft.x, topLeft.y, bottomRight.x, bottomRight.y);
        waitAndAssertTopResumedActivity(componentName, DEFAULT_DISPLAY,
                "Activity launched as freeform should be resumed");
    }

    private void launchSplitScreenActivities(ComponentName sourceComponentName,
            String sourceMode, ComponentName targetComponentName, String targetMode) {
        // Launch primary activity.
        getLaunchActivityBuilder()
                .setTargetActivity(sourceComponentName)
                .setUseInstrumentation()
                .setWaitForLaunched(true)
                .setIntentExtra(bundle -> {
                    bundle.putString(EXTRA_MODE, sourceMode);
                    bundle.putString(EXTRA_LOGTAG, mSourceLogTag);
                }).execute();

        // Launch secondary activity.
        getLaunchActivityBuilder().setTargetActivity(targetComponentName)
                .setUseInstrumentation()
                .setWaitForLaunched(true)
                .setIntentExtra(bundle -> {
                    bundle.putString(EXTRA_MODE, targetMode);
                    bundle.putString(EXTRA_LOGTAG, mTargetLogTag);
                }).execute();
        moveActivitiesToSplitScreen(sourceComponentName, targetComponentName);
    }

    private void injectInput(Point from, Point to, int steps) throws Exception {
        dragPointer(from, to, steps);
    }

    private Point getDisplaySize() throws Exception {
        final Point displaySize = new Point();
        mDm.getDisplay(Display.DEFAULT_DISPLAY).getRealSize(displaySize);
        return displaySize;
    }

    private Point getWindowCenter(ComponentName name) throws Exception {
        final Task sideTask = mWmState.getTaskByActivity(name);
        Rect bounds = sideTask.getBounds();
        if (bounds != null) {
            return new Point(bounds.centerX(), bounds.centerY());
        }
        return null;
    }

    private void assertDropResult(String sourceMode, String targetMode, String expectedDropResult)
            throws Exception {
        assertDragAndDropResults(DRAG_SOURCE, sourceMode, DROP_TARGET, targetMode,
                RESULT_OK, expectedDropResult, RESULT_OK);
    }

    private void assertNoGlobalDragEvents(ComponentName sourceComponentName, String sourceMode,
            ComponentName targetComponentName, String expectedStartDragResult)
            throws Exception {
        assertDragAndDropResults(
                sourceComponentName, sourceMode, targetComponentName, REQUEST_NONE,
                expectedStartDragResult, RESULT_MISSING, RESULT_MISSING);
    }

    private void assertDragAndDropResults(ComponentName sourceComponentName, String sourceMode,
            ComponentName targetComponentName, String targetMode,
            String expectedStartDragResult, String expectedDropResult,
            String expectedListenerResults) throws Exception {
        Log.e(TAG, "session: " + mSessionId + ", source: " + sourceMode
                + ", target: " + targetMode);

        mActivityLaunchModeContext.launchActivities(
                sourceComponentName, sourceMode, targetComponentName, targetMode);
        if (DROP_TARGET_SDK23.equals(targetComponentName)) {
            DeprecatedTargetSdkUtils.waitAndDismissDeprecatedTargetSdkDialog(mWmState);
        }

        Point p1 = getWindowCenter(sourceComponentName);
        assertNotNull(p1);
        Point p2 = getWindowCenter(targetComponentName);
        assertNotNull(p2);

        TestLogService.registerClient(mSourceLogTag, RESULT_KEY_START_DRAG);
        TestLogService.registerClient(mTargetLogTag, RESULT_KEY_DRAG_ENDED);

        injectInput(p1, p2, SWIPE_STEPS);

        mSourceResults = TestLogService.getResultsForClient(mSourceLogTag, 1000);
        assertSourceResult(RESULT_KEY_START_DRAG, expectedStartDragResult);

        mTargetResults = TestLogService.getResultsForClient(mTargetLogTag, 1000);
        assertTargetResult(RESULT_KEY_DROP_RESULT, expectedDropResult);

        // Skip the following assertions when testing OnReceiveContentListener, since it only
        // handles drop events.
        if (!ON_RECEIVE_CONTENT_LISTENER_MODES.contains(targetMode)) {
            if (!RESULT_MISSING.equals(expectedDropResult)) {
                assertTargetResult(RESULT_KEY_ACCESS_BEFORE, RESULT_EXCEPTION);
                assertTargetResult(RESULT_KEY_ACCESS_AFTER, RESULT_EXCEPTION);
            }
            assertListenerResults(expectedListenerResults);
        }
    }

    private void assertListenerResults(String expectedResult) throws Exception {
        assertTargetResult(RESULT_KEY_DRAG_STARTED, expectedResult);
        assertTargetResult(RESULT_KEY_DRAG_ENDED, expectedResult);
        assertTargetResult(RESULT_KEY_EXTRAS, expectedResult);

        assertTargetResult(RESULT_KEY_CLIP_DATA_ERROR, RESULT_MISSING);
        assertTargetResult(RESULT_KEY_CLIP_DESCR_ERROR, RESULT_MISSING);
        assertTargetResult(RESULT_KEY_LOCAL_STATE_ERROR, RESULT_MISSING);
    }

    private void assertSourceResult(String resultKey, String expectedResult) throws Exception {
        assertResult(mSourceResults, resultKey, expectedResult);
    }

    private void assertTargetResult(String resultKey, String expectedResult) throws Exception {
        assertResult(mTargetResults, resultKey, expectedResult);
    }

    private void assertResult(Map<String, String> results, String resultKey, String expectedResult)
            throws Exception {
        if (RESULT_MISSING.equals(expectedResult)) {
            if (results.containsKey(resultKey)) {
                fail("Unexpected " + resultKey + "=" + results.get(resultKey));
            }
        } else {
            assertTrue("Missing " + resultKey, results.containsKey(resultKey));
            assertEquals(resultKey + " result mismatch,", expectedResult, results.get(resultKey));
        }
    }

    @Test
    public void testCancelSoon() throws Exception {
        assertDropResult(CANCEL_SOON, REQUEST_NONE, RESULT_MISSING);
    }

    @Test
    public void testDisallowGlobal() throws Exception {
        assertNoGlobalDragEvents(DRAG_SOURCE, DISALLOW_GLOBAL, DROP_TARGET, RESULT_OK);
    }

    @Test
    public void testDisallowGlobalBelowSdk24() throws Exception {
        assertNoGlobalDragEvents(DRAG_SOURCE, GRANT_NONE, DROP_TARGET_SDK23, RESULT_OK);
    }

    @Test
    public void testFileUriLocal() throws Exception {
        assertNoGlobalDragEvents(DRAG_SOURCE, FILE_LOCAL, DROP_TARGET, RESULT_OK);
    }

    @Test
    public void testFileUriGlobal() throws Exception {
        assertNoGlobalDragEvents(DRAG_SOURCE, FILE_GLOBAL, DROP_TARGET, RESULT_EXCEPTION);
    }

    @Test
    public void testGrantNoneRequestNone() throws Exception {
        assertDropResult(GRANT_NONE, REQUEST_NONE, RESULT_EXCEPTION);
    }

    @Test
    public void testGrantNoneRequestRead() throws Exception {
        assertDropResult(GRANT_NONE, REQUEST_READ, RESULT_NULL_DROP_PERMISSIONS);
    }

    @Test
    public void testGrantNoneRequestWrite() throws Exception {
        assertDropResult(GRANT_NONE, REQUEST_WRITE, RESULT_NULL_DROP_PERMISSIONS);
    }

    @Test
    public void testGrantReadRequestNone() throws Exception {
        assertDropResult(GRANT_READ, REQUEST_NONE, RESULT_EXCEPTION);
    }

    @Test
    public void testGrantReadRequestRead() throws Exception {
        assertDropResult(GRANT_READ, REQUEST_READ, RESULT_OK);
    }

    @Test
    public void testGrantReadRequestWrite() throws Exception {
        assertDropResult(GRANT_READ, REQUEST_WRITE, RESULT_EXCEPTION);
    }

    @Test
    public void testGrantReadNoPrefixRequestReadNested() throws Exception {
        assertDropResult(GRANT_READ_NOPREFIX, REQUEST_READ_NESTED, RESULT_EXCEPTION);
    }

    @Test
    public void testGrantReadPrefixRequestReadNested() throws Exception {
        assertDropResult(GRANT_READ_PREFIX, REQUEST_READ_NESTED, RESULT_OK);
    }

    @Test
    public void testGrantPersistableRequestTakePersistable() throws Exception {
        assertDropResult(GRANT_READ_PERSISTABLE, REQUEST_TAKE_PERSISTABLE, RESULT_OK);
    }

    @Test
    public void testGrantReadRequestTakePersistable() throws Exception {
        assertDropResult(GRANT_READ, REQUEST_TAKE_PERSISTABLE, RESULT_EXCEPTION);
    }

    @Test
    public void testGrantWriteRequestNone() throws Exception {
        assertDropResult(GRANT_WRITE, REQUEST_NONE, RESULT_EXCEPTION);
    }

    @Test
    public void testGrantWriteRequestRead() throws Exception {
        assertDropResult(GRANT_WRITE, REQUEST_READ, RESULT_EXCEPTION);
    }

    @Test
    public void testGrantWriteRequestWrite() throws Exception {
        assertDropResult(GRANT_WRITE, REQUEST_WRITE, RESULT_OK);
    }

    @Test
    public void testOnReceiveContentListener_TextView_GrantRead() throws Exception {
        assertDropResult(GRANT_READ, TARGET_ON_RECEIVE_CONTENT_LISTENER_TEXT_VIEW, RESULT_OK);
    }

    @Test
    public void testOnReceiveContentListener_TextView_GrantNone() throws Exception {
        assertDropResult(GRANT_NONE, TARGET_ON_RECEIVE_CONTENT_LISTENER_TEXT_VIEW,
                RESULT_EXCEPTION);
    }

    @Test
    public void testOnReceiveContentListener_EditText_GrantRead() throws Exception {
        assertDropResult(GRANT_READ, TARGET_ON_RECEIVE_CONTENT_LISTENER_EDIT_TEXT, RESULT_OK);
    }

    @Test
    public void testOnReceiveContentListener_EditText_GrantNone() throws Exception {
        assertDropResult(GRANT_NONE, TARGET_ON_RECEIVE_CONTENT_LISTENER_EDIT_TEXT,
                RESULT_EXCEPTION);
    }

    @Test
    public void testOnReceiveContentListener_LinearLayout_GrantRead() throws Exception {
        assertDropResult(GRANT_READ, TARGET_ON_RECEIVE_CONTENT_LISTENER_LINEAR_LAYOUT, RESULT_OK);
    }

    @Test
    public void testOnReceiveContentListener_LinearLayout_GrantNone() throws Exception {
        assertDropResult(GRANT_NONE, TARGET_ON_RECEIVE_CONTENT_LISTENER_LINEAR_LAYOUT,
                RESULT_EXCEPTION);
    }

    private abstract static class ActivityLaunchModeContext {
        CrossAppDragAndDropTests mTestCase;

        abstract void assumeDeviceSupportsLaunchMode() throws Exception;

        abstract void launchActivities(ComponentName sourceComponentName,
                String sourceMode, ComponentName targetComponentName, String targetMode)
                throws Exception;
    }
}
