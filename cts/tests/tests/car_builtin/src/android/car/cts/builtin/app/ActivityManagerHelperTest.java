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

package android.car.cts.builtin.app;

import static android.car.builtin.app.ActivityManagerHelper.ProcessObserverCallback;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.app.TaskInfo;
import android.car.builtin.app.ActivityManagerHelper;
import android.car.cts.builtin.activity.ActivityManagerTestActivityBase;
import android.car.test.PermissionsCheckerRule;
import android.car.test.PermissionsCheckerRule.EnsureHasPermission;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Process;
import android.os.UserHandle;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.WindowManagerState;
import android.util.Log;

import androidx.test.filters.FlakyTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public final class ActivityManagerHelperTest extends ActivityManagerTestBase {

    @Rule
    public final PermissionsCheckerRule mPermissionsCheckerRule = new PermissionsCheckerRule();

    // type values from frameworks/base/core/java/android/app/WindowConfiguration
    enum ActivityType {
        ACTIVITY_TYPE_UNDEFINED,
        ACTIVITY_TYPE_STANDARD
    }

    private static final String TAG = ActivityManagerHelperTest.class.getSimpleName();

    private static final String PERMISSION_SET_ACTIVITY_WATCHER =
            "android.permission.SET_ACTIVITY_WATCHER";
    private static final String NOT_REQUESTED_PERMISSION_CAR_MILEAGE =
            "android.car.permission.CAR_MILEAGE";
    private static final String NOT_REQUESTED_PERMISSION_READ_CAR_POWER_POLICY =
            "android.car.permission.READ_CAR_POWER_POLICY";

    private static final String GRANTED_PERMISSION_INTERACT_ACROSS_USERS =
            "android.permission.INTERACT_ACROSS_USERS";

    // ActivityManagerHelper.removeTask needs this permission
    private static final String PERMISSION_REMOVE_TASKS = "android.permission.REMOVE_TASKS";
    // IActivityManager.getAllRootTaskInfos called in ActivityManagerHelper.stopAllTaskForUser
    // needs this permission.
    private static final String PERMISSION_MANAGE_ACTIVITY_TASKS =
            "android.permission.MANAGE_ACTIVITY_TASKS";
    // ActivityManager.getRunningAppProcess called in isAppRunning needs this permission
    private static final String PERMISSION_INTERACT_ACROSS_USERS_FULL =
            "android.permission.INTERACT_ACROSS_USERS_FULL";
    private static final String PERMISSION_REAL_GET_TASKS =
            "android.permission.REAL_GET_TASKS";

    private static final String SIMPLE_APP_PACKAGE_NAME = "android.car.cts.builtin.apps.simple";
    private static final String SIMPLE_ACTIVITY_RELATIVE_NAME = ".SimpleActivity";
    private static final String SIMPLE_ACTIVITY_NAME = SIMPLE_APP_PACKAGE_NAME
            + SIMPLE_ACTIVITY_RELATIVE_NAME;
    private static final String START_SIMPLE_ACTIVITY_COMMAND = "am start -W -n "
            + SIMPLE_APP_PACKAGE_NAME + "/" + SIMPLE_ACTIVITY_RELATIVE_NAME;
    private static final ComponentName SIMPLE_ACTIVITY_COMPONENT_NAME =
            new ComponentName(SIMPLE_APP_PACKAGE_NAME, SIMPLE_ACTIVITY_RELATIVE_NAME);

    // TODO(b/230757942): replace following shell commands with direct API calls
    private static final String CREATE_USER_COMMAND = "cmd car_service create-user ";
    private static final String SWITCH_USER_COMMAND = "cmd car_service switch-user ";
    private static final String REMOVE_USER_COMMAND = "cmd car_service remove-user ";
    private static final String START_USER_COMMAND = "am start-user -w ";
    private static final String GET_CURRENT_USER_COMMAND = "am get-current-user ";
    private static final String CTS_CAR_TEST_USER_NAME = "CtsCarTestUser";
    // the value from UserHandle.USER_NULL
    private static final int INVALID_USER_ID = -10_000;

    private static final int OWNING_UID = UserHandle.ALL.getIdentifier();
    private static final int MAX_NUM_TASKS = 1_000;
    private static final int TIMEOUT_MS = 4_000;

    // x coordinate of the left boundary line of the animation rectangle
    private static final int ANIMATION_RECT_LEFT = 0;
    // y coordinate of the top boundary line of the animation rectangle
    private static final int ANIMATION_RECT_TOP = 200;
    // x coordinate of the right boundary line of the animation rectangle
    private static final int ANIMATION_RECT_RIGHT = 400;
    // y coordinate of the bottom boundary line of the animation rectangle
    private static final int ANIMATION_RECT_BOTTOM = 0;

    private static final int RANDOM_NON_DEFAULT_DISPLAY_ID = 1;
    private static final boolean NON_DEFAULT_LOCK_TASK_MODE = true;
    private static final boolean
            NON_DEFAULT_PENDING_INTENT_BACKGROUND_ACTIVITY_LAUNCH_ALLOWED = true;


    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context mContext = mInstrumentation.getContext();

    @Before
    public void setUp() throws Exception {
        // Home was launched in ActivityManagerTestBase#setUp, wait until it is stable,
        // in order not to mix the event of its TaskView Activity with the TestActivity.
        mWmState.waitForHomeActivityVisible();
    }

    @Test
    @EnsureHasPermission(GRANTED_PERMISSION_INTERACT_ACROSS_USERS)
    public void testCheckComponentPermission() throws Exception {
        // not requested from Manifest
        assertComponentPermissionNotGranted(NOT_REQUESTED_PERMISSION_CAR_MILEAGE);
        assertComponentPermissionNotGranted(NOT_REQUESTED_PERMISSION_READ_CAR_POWER_POLICY);

        // requested from Manifest and granted
        assertComponentPermissionGranted(GRANTED_PERMISSION_INTERACT_ACROSS_USERS);
    }

    @Test
    public void testSetFocusedRootTask() throws Exception {
        // Don't run this test on automotive targets with splitscreen multitasking enabled due to
        // it having root tasks which are not the leaf nodes in the view hierarchy. Instead use
        // setFocusedTask for those targets.
        assumeFalse(hasAutomotiveSplitscreenMultitaskingFeature());
        // setup
        ActivityA task1BottomActivity = launchTestActivity(ActivityA.class);
        ActivityB task1TopActivity = launchTestActivity(ActivityB.class);
        ActivityC task2TopActivity = launchTestActivity(ActivityC.class);

        logActivityStack("amTestActivitys ",
                task1BottomActivity, task1TopActivity, task2TopActivity);

        assertWithMessage("bottom activity is the task root")
                .that(task1BottomActivity.isTaskRoot()).isTrue();
        assertWithMessage("task id of the top activity in the task1")
                .that(task1TopActivity.getTaskId()).isEqualTo(task1BottomActivity.getTaskId());
        assertWithMessage("task id of the top activity in the task2")
                .that(task2TopActivity.getTaskId()).isNotEqualTo(task1TopActivity.getTaskId());
        assertWithMessage("task1 top activity is visible")
                .that(task1TopActivity.isVisible()).isFalse();
        assertWithMessage("task2 top activity is visible")
                .that(task2TopActivity.isVisible()).isTrue();

        // execute
        try {
            mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                    PERMISSION_MANAGE_ACTIVITY_TASKS);

            ActivityManagerHelper.setFocusedRootTask(task1BottomActivity.getTaskId());
        } finally {
            mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        }


        // assert
        ComponentName activityName = task1TopActivity.getComponentName();
        waitAndAssertTopResumedActivity(activityName, DEFAULT_DISPLAY,
                "Activity must be resumed");
        waitAndAssertFocusStatusChanged(task1TopActivity, true);
        assertWithMessage("task1 top activity is visible")
                .that(task1TopActivity.isVisible()).isTrue();

        // teardown
        task1TopActivity.finish();
        task1BottomActivity.finish();
        task2TopActivity.finish();
    }

    @Test
    @FlakyTest(bugId = 274818424)
    public void testRemoveTask() throws Exception {
        // setup
        ActivityC testActivity = launchTestActivity(ActivityC.class);
        int taskId = testActivity.getTaskId();
        assertThat(doesTaskExist(taskId)).isTrue();

        // execute
        try {
            mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                    PERMISSION_REMOVE_TASKS);

            ActivityManagerHelper.removeTask(taskId);
        } finally {
            mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        }

        // assert
        PollingCheck.waitFor(TIMEOUT_MS, () -> testActivity.isDestroyed());
        assertThat(doesTaskExist(taskId)).isFalse();
    }

    @Test
    public void testProcessObserverCallback() throws Exception {
        // setup
        ProcessObserverCallbackTestImpl callbackImpl = new ProcessObserverCallbackTestImpl();

        // execute
        try {
            mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                    PERMISSION_SET_ACTIVITY_WATCHER);  // for registerProcessObserverCallback

            ActivityManagerHelper.registerProcessObserverCallback(callbackImpl);

            launchSimpleActivity();

            // assert
            assertThat(callbackImpl.waitForForegroundActivitiesChanged()).isTrue();
        } finally {
            // teardown
            ActivityManagerHelper.unregisterProcessObserverCallback(callbackImpl);
            mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testCreateActivityOptions() {
        Rect expectedRect = new Rect(ANIMATION_RECT_LEFT,
                ANIMATION_RECT_TOP,
                ANIMATION_RECT_RIGHT,
                ANIMATION_RECT_BOTTOM);
        int expectedDisplayId = RANDOM_NON_DEFAULT_DISPLAY_ID;
        boolean expectedLockTaskMode = NON_DEFAULT_LOCK_TASK_MODE;
        boolean expectedLaunchAllowed =
                NON_DEFAULT_PENDING_INTENT_BACKGROUND_ACTIVITY_LAUNCH_ALLOWED;

        ActivityOptions originalOptions =
                ActivityOptions.makeCustomAnimation(mContext,
                /* entResId= */ android.R.anim.fade_in,
                /* exitResId= */ android.R.anim.fade_out);
        originalOptions.setLaunchBounds(expectedRect);
        originalOptions.setLaunchDisplayId(expectedDisplayId);
        originalOptions.setLockTaskEnabled(expectedLockTaskMode);
        originalOptions.setPendingIntentBackgroundActivityLaunchAllowed(expectedLaunchAllowed);

        ActivityOptions createdOptions =
                ActivityManagerHelper.createActivityOptions(originalOptions.toBundle());

        assertThat(createdOptions.getLaunchBounds()).isEqualTo(expectedRect);
        assertThat(createdOptions.getLaunchDisplayId()).isEqualTo(expectedDisplayId);
        assertThat(createdOptions.getLockTaskMode()).isEqualTo(expectedLockTaskMode);
        assertThat(createdOptions.isPendingIntentBackgroundActivityLaunchAllowed())
                .isEqualTo(expectedLaunchAllowed);
    }

    @Ignore("b/304870066")
    @Test
    public void testStopAllTasksForUser() throws Exception {
        int initialCurrentUserId = getCurrentUserId();
        int testUserId = INVALID_USER_ID;

        try {
            mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                    PERMISSION_MANAGE_ACTIVITY_TASKS,
                    PERMISSION_REMOVE_TASKS,
                    PERMISSION_REAL_GET_TASKS,
                    PERMISSION_INTERACT_ACROSS_USERS_FULL);

            testUserId = createUser(CTS_CAR_TEST_USER_NAME);
            startUser(testUserId);

            switchUser(testUserId);
            waitUntilUserCurrent(testUserId);

            installPackageForUser(testUserId);

            launchSimpleActivityInCurrentUser();
            assertIsAppRunning(true, SIMPLE_APP_PACKAGE_NAME);

            switchUser(initialCurrentUserId);
            waitUntilUserCurrent(initialCurrentUserId);

            stopAllTasksForUser(testUserId);
            assertIsAppRunning(false, SIMPLE_APP_PACKAGE_NAME);

            removeUser(testUserId);
            testUserId = INVALID_USER_ID;
        } finally {
            mInstrumentation.getUiAutomation().dropShellPermissionIdentity();

            deepCleanTestStopAllTasksForUser(testUserId, initialCurrentUserId);
        }
    }

    private void deepCleanTestStopAllTasksForUser(int testUserId, int initialCurrentUserId)
            throws Exception {
        try {
            if (initialCurrentUserId != getCurrentUserId()) {
                switchUser(initialCurrentUserId);
                waitUntilUserCurrent(initialCurrentUserId);
            }
        } finally {
            if (testUserId != INVALID_USER_ID) {
                removeUser(testUserId);
            }
        }
    }

    private void assertComponentPermissionGranted(String permission) throws Exception {
        assertThat(ActivityManagerHelper.checkComponentPermission(permission,
                Process.myUid(), /* owningUid= */ OWNING_UID, /* exported= */ true))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
    }

    private void assertComponentPermissionNotGranted(String permission) throws Exception {
        assertThat(ActivityManagerHelper.checkComponentPermission(permission,
                Process.myUid(), /* owningUid= */ OWNING_UID, /* exported= */ true))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    private static final class ProcessObserverCallbackTestImpl extends ProcessObserverCallback {
        private final CountDownLatch mLatch = new CountDownLatch(1);

        // Use onForegroundActivitiesChanged(), because onProcessDied() can be called
        // in very long time later even if the task was removed.
        @Override
        public void onForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) {
            Log.d(TAG, "onForegroundActivitiesChanged: pid " + pid + " uid " + uid);
            mLatch.countDown();
        }

        public boolean waitForForegroundActivitiesChanged() throws Exception {
            return mLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void launchSimpleActivity() {
        ComponentName simpleActivity = new ComponentName(
                SIMPLE_APP_PACKAGE_NAME, SIMPLE_ACTIVITY_NAME);
        Intent intent = new Intent()
                .setComponent(simpleActivity)
                .addFlags(FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent, /* options = */ null);
        waitAndAssertTopResumedActivity(simpleActivity, DEFAULT_DISPLAY, "Activity isn't resumed");
    }

    // launchSimpleActivity in the current user space via the car shell instead of the calling user.
    // The calling user could be in the background.
    private static void launchSimpleActivityInCurrentUser() {
        Log.d(TAG, "launchSimpleActivityInCurrentUser: " + START_SIMPLE_ACTIVITY_COMMAND);
        String retStr = SystemUtil.runShellCommand(START_SIMPLE_ACTIVITY_COMMAND);
        Log.d(TAG, "launchSimpleActivityInCurrentUser return: " + retStr);
    }

    private static void installPackageForUser(int userId) {
        String fullCommand = String.format("pm install-existing --user %d %s",
                userId, SIMPLE_APP_PACKAGE_NAME);
        Log.d(TAG, "installPackageForUser: " + fullCommand);
        String retStr = SystemUtil.runShellCommand(fullCommand);
        Log.d(TAG, "installPackageForUser return: " + retStr);
    }

    private void assertIsAppRunning(boolean isRunning, String pkgName) {
        PollingCheck.waitFor(TIMEOUT_MS, () -> isAppRunning(pkgName) == isRunning);
    }

    private boolean isAppRunning(String pkgName) {
        ActivityManager am = mContext.getSystemService(ActivityManager.class);

        List<ActivityManager.RunningTaskInfo> runningTasks = am.getRunningTasks(MAX_NUM_TASKS);

        for (ActivityManager.RunningTaskInfo taskInfo : runningTasks) {
            if (pkgName.equals(taskInfo.baseActivity.getPackageName())) {
                return true;
            }
        }

        return false;
    }

    private <T> T launchTestActivity(Class<T> type) {
        Intent startIntent = new Intent(mContext, type)
                .addFlags(FLAG_ACTIVITY_NEW_TASK);

        Activity testActivity = (Activity) mInstrumentation
                .startActivitySync(startIntent, /* options = */ null);

        ComponentName testActivityName = testActivity.getComponentName();
        waitAndAssertTopResumedActivity(testActivityName, DEFAULT_DISPLAY,
                "Activity must be resumed");

        return type.cast(testActivity);
    }

    // The logging order of the Activities follows the stack order. The first Activity
    // in the parameter list is logged at last.
    private static void logActivityStack(String msg, Activity... activityStack) {
        for (int index = activityStack.length - 1; index >= 0; index--) {
            String logMsg = String.format("%s\tindex=%d taskId=%d",
                    msg, index, activityStack[index].getTaskId());
            Log.d(TAG, logMsg);
        }
    }

    private boolean doesTaskExist(int taskId) {
        boolean retVal = false;
        try {
            mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                    PERMISSION_REMOVE_TASKS);
            ActivityManager am = mContext.getSystemService(ActivityManager.class);
            List<ActivityManager.RunningTaskInfo> taskList = am.getRunningTasks(MAX_NUM_TASKS);
            for (TaskInfo taskInfo : taskList) {
                if (taskInfo.taskId == taskId) {
                    retVal = true;
                    break;
                }
            }
        } finally {
            mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
        return retVal;
    }

    public static final class ActivityA extends ActivityManagerTestActivityBase {
    }

    public static final class ActivityB extends ActivityManagerTestActivityBase {
    }

    public static final class ActivityC extends ActivityManagerTestActivityBase {
    }

    private static void waitAndAssertFocusStatusChanged(ActivityManagerTestActivityBase activity,
            boolean expectedStatus) throws Exception {
        PollingCheck.waitFor(TIMEOUT_MS, () -> activity.hasFocus() == expectedStatus);
    }

    private static int createUser(String userName) throws Exception {
        Log.d(TAG, "createUser: " + userName);
        String retStr = SystemUtil.runShellCommand(CREATE_USER_COMMAND + userName);
        Pattern userIdPattern = Pattern.compile("id=(\\d+)");
        Matcher matcher = userIdPattern.matcher(retStr);
        if (!matcher.find()) {
            throw new Exception("failed to create user: " + userName);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static void switchUser(int userId) throws Exception {
        Log.d(TAG, "switchUser: " + userId);
        String retStr = SystemUtil.runShellCommand(SWITCH_USER_COMMAND + userId);
        if (!retStr.contains("STATUS_SUCCESSFUL")) {
            throw new Exception("failed to switch to user: " + userId);
        }
        Log.d(TAG, "switchUser: " + retStr);
    }

    private static void removeUser(int userId) throws Exception {
        Log.d(TAG, "removeUser: " + userId);
        String retStr = SystemUtil.runShellCommand(REMOVE_USER_COMMAND + userId);
        if (!retStr.contains("STATUS_SUCCESSFUL")) {
            throw new Exception("failed to remove user: " + userId);
        }
        Log.d(TAG, "removeUser: " + retStr);
    }

    private static void startUser(int userId) throws Exception {
        String retStr = SystemUtil.runShellCommand(START_USER_COMMAND + userId);
        if (!retStr.contains("Success: user started")) {
            throw new Exception("failed to start user: " + userId + " with return: " + retStr);
        }
        Log.d(TAG, "startUser: " + retStr);
    }

    private static int getCurrentUserId() {
        String retStr = SystemUtil.runShellCommand(GET_CURRENT_USER_COMMAND);
        Log.d(TAG, "getCurrentUserId: " + retStr);
        return Integer.parseInt(retStr.trim());
    }

    private static void waitUntilUserCurrent(int userId) throws Exception {
        PollingCheck.waitFor(TIMEOUT_MS, () -> userId == getCurrentUserId());
    }

    // need to get the permission in the same user
    private static void stopAllTasksForUser(int userId) {
        ActivityManagerHelper.stopAllTasksForUser(userId);
    }

    private static boolean checkSimpleActivityExistence() {
        boolean foundSimpleActivity = false;

        Log.d(TAG, "checkSimpleActivityExistence --- Begin");
        WindowManagerState wmState = new WindowManagerState();
        wmState.computeState();
        for (ActivityType activityType : ActivityType.values()) {
            if (findSimpleActivityInType(activityType, wmState)) {
                foundSimpleActivity = true;
                break;
            }
        }
        Log.d(TAG, "checkSimpleActivityExistence --- End with --- " + foundSimpleActivity);

        return foundSimpleActivity;
    }

    private static boolean findSimpleActivityInType(ActivityType activityType,
            WindowManagerState wmState) {
        boolean foundRootTask = false;
        boolean foundSimpleActivity = false;

        WindowManagerState.Task rootTask =
                wmState.getRootTaskByActivityType(activityType.ordinal());
        if (rootTask != null) {
            foundRootTask = true;
            List<WindowManagerState.Activity> allActivities = rootTask.getActivities();
            if (rootTask.getActivity(SIMPLE_ACTIVITY_COMPONENT_NAME) != null) {
                foundSimpleActivity = true;
            }

            // for debugging purpose only
            for (WindowManagerState.Activity act : allActivities) {
                Log.d(TAG, activityType.name() + ": activity name -- " + act.getName());
            }
        }

        Log.d(TAG, activityType.name() + " has simple activity root task:" + foundRootTask);
        Log.d(TAG, activityType.name() + " has simple activity: " + foundSimpleActivity);

        return foundSimpleActivity;
    }
}
