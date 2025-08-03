/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package android.server.wm;

import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.MODE_ERRORED;
import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;
import static android.server.wm.ShellCommandHelper.executeShellCommand;
import static android.server.wm.UiDeviceUtils.pressHomeButton;
import static android.server.wm.backgroundactivity.common.CommonComponents.EVENT_NOTIFIER_EXTRA;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.Manifest;
import android.app.ActivityOptions;
import android.app.BroadcastOptions;
import android.app.PendingIntent;
import android.app.RemoteAction;
import android.app.UiAutomation;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.AsbSecurityTest;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Settings;
import android.server.wm.backgroundactivity.appa.Components;
import android.server.wm.backgroundactivity.common.CommonComponents.Event;
import android.server.wm.backgroundactivity.common.EventReceiver;
import android.util.Log;
import android.view.textclassifier.TextClassification;

import androidx.test.filters.FlakyTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.AppOpsUtils;
import com.android.window.flags.Flags;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * This class covers all test cases for starting/blocking background activities.
 * As instrumentation tests started by shell are allowlisted to allow starting background activity,
 * tests can't be done in this app alone.
 * Hence, there are 2 extra apps, appA and appB. This class will send commands to appA/appB, for
 * example, send a broadcast to appA and ask it to start a background activity, and we will monitor
 * the result and see if it starts an activity successfully.
 */
@Presubmit
public class BackgroundActivityLaunchTest extends BackgroundActivityTestBase {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TAG = "BackgroundActivityLaunchTest";

    private static final Icon EMPTY_ICON = Icon.createWithBitmap(
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888));

    private static final long ACTIVITY_BG_START_GRACE_PERIOD_MS = 10 * 1000;
    private static final int ACTIVITY_START_TIMEOUT_MS = 5000;
    private static final int ACTIVITY_NOT_RESUMED_TIMEOUT_MS = 5000;

    /**
     * Tests can be executed as soon as the device has booted. When that happens the broadcast queue
     * is long and it takes some time to process the broadcast we just sent.
     */
    private static final int BROADCAST_DELIVERY_TIMEOUT_MS = 60000;
    public static final Bundle SEND_OPTIONS_ALLOW_BAL = ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle();
    public static final Bundle SEND_BROADCAST_OPTIONS_ALLOW_BAL = BroadcastOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle();
    public static final Bundle CREATE_OPTIONS_DENY_BAL =
            ActivityOptions.makeBasic().setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_DENIED).toBundle();
    public static final Bundle CREATE_OPTIONS_ALLOW_BAL =
            ActivityOptions.makeBasic().setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle();

    @Test
    public void testBackgroundActivityBlocked() throws Exception {
        // Start AppA background activity and blocked
        sendBroadcastAndWait(APP_A.SIMPLE_BROADCAST_RECEIVER);
        startBackgroundActivity(APP_A);
        assertActivityNotFocused(APP_A.BACKGROUND_ACTIVITY);
        assertTaskStackIsEmpty(APP_A.BACKGROUND_ACTIVITY);
    }

    @Test
    public void testBackgroundActivityBlockedInStartNextMatchingActivity() throws TimeoutException {
        EventReceiver receiver = new EventReceiver(
                Event.APP_A_LAUNCHER_MOVING_TO_BACKGROUND_ACTIVITY);
        Intent intent = new Intent("StartNextMatchingActivityAction");
        intent.setComponent(APP_A.START_NEXT_MATCHING_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(EVENT_NOTIFIER_EXTRA, receiver.getNotifier());
        mContext.startActivity(intent);
        receiver.waitForEventOrThrow(ACTIVITY_START_TIMEOUT_MS);
        assertActivityNotFocused(APP_A.BACKGROUND_ACTIVITY);
    }

    @Test
    public void testStartBgActivity_usingStartActivitiesFromBackgroundPermission()
            throws Exception {
        // Disable SAW app op for shell, since that can also allow starting activities from bg.
        grantSystemAlertWindow(SHELL_PACKAGE, false);

        // Launch the activity via a shell command, this way the system doesn't have info on which
        // app launched the activity and thus won't use instrumentation privileges to launch it. But
        // the shell has the START_ACTIVITIES_FROM_BACKGROUND permission, so we expect it to
        // succeed.
        // See testBackgroundActivityBlocked() for a case where an app without the
        // START_ACTIVITIES_FROM_BACKGROUND permission is blocked from launching the activity from
        // the background.
        launchActivity(APP_A.BACKGROUND_ACTIVITY);

        // If the activity launches, it means the START_ACTIVITIES_FROM_BACKGROUND permission works.
        assertWithMessage("Launched activity should be at the top")
                .that(mWmState.getTopActivityName(0))
                .isEqualTo(ComponentNameUtils.getActivityName(APP_A.BACKGROUND_ACTIVITY));
    }

    @Test
    public void testBackgroundActivity_withinGracePeriodAndSdk33_isNotBlocked() throws Exception {
        // Start AppA foreground activity
        startActivity(APP_A_33.FOREGROUND_ACTIVITY);
        // Don't press home button to avoid stop app switches
        mContext.sendBroadcast(new Intent(APP_A_33.FOREGROUND_ACTIVITY_ACTIONS.FINISH_ACTIVITY));
        mWmState.waitAndAssertActivityRemoved(APP_A_33.FOREGROUND_ACTIVITY);
        startBackgroundActivity(APP_A_33);
        assertActivityFocused(APP_A_33.BACKGROUND_ACTIVITY);
    }

    @Test
    public void testBackgroundActivity_withinASMGracePeriod_isBlocked() throws Exception {
        assumeSdkNewerThanUpsideDownCake();
        // Start AppA foreground activity
        startActivity(APP_A.FOREGROUND_ACTIVITY);
        // Don't press home button to avoid stop app switches
        mContext.sendBroadcast(new Intent(APP_A.FOREGROUND_ACTIVITY_ACTIONS.FINISH_ACTIVITY));
        mWmState.waitAndAssertActivityRemoved(APP_A.FOREGROUND_ACTIVITY);
        startBackgroundActivity(APP_A);
        assertActivityNotFocused(APP_A.BACKGROUND_ACTIVITY);
    }

    @Test
    @FlakyTest(bugId = 297339382)
    public void testBackgroundActivity_withinBalAfterAsmGracePeriod_isBlocked()
            throws Exception {
        assumeSdkNewerThanUpsideDownCake();
        // Start AppA foreground activity
        startActivity(APP_A.FOREGROUND_ACTIVITY);
        // Don't press home button to avoid stop app switches
        mContext.sendBroadcast(new Intent(APP_A.FOREGROUND_ACTIVITY_ACTIONS.FINISH_ACTIVITY));
        mWmState.waitAndAssertActivityRemoved(APP_A.FOREGROUND_ACTIVITY);
        Thread.sleep(1000 * 5);
        startBackgroundActivity(APP_A);
        assertActivityNotFocused(APP_A.BACKGROUND_ACTIVITY);
    }

    @Test
    public void testBackgroundActivityWhenSystemAlertWindowGranted_isNotBlocked()
            throws Exception {
        grantSystemAlertWindow(APP_A_33);

        // Start AppA background activity successfully as the package has SAW
        startBackgroundActivity(APP_A_33);
        assertActivityFocused(APP_A_33.BACKGROUND_ACTIVITY);
    }

    @Test
    public void testBackgroundActivityBlockedWhenForegroundActivityNotTop() throws Exception {
        assumeSdkNewerThanUpsideDownCake();

        startActivity(APP_A.FOREGROUND_ACTIVITY);
        mContext.sendBroadcast(getLaunchActivitiesBroadcast(APP_A, APP_B.FOREGROUND_ACTIVITY));
        mWmState.waitForValidState(APP_B.FOREGROUND_ACTIVITY);
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
        assertTaskStackHasComponents(APP_A.FOREGROUND_ACTIVITY,
                APP_B.FOREGROUND_ACTIVITY,
                APP_A.FOREGROUND_ACTIVITY);

        // Start AppA background activity fails as AppA not on top of stack
        startBackgroundActivity(APP_A);
        mWmState.waitForValidState(APP_A.BACKGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_A.FOREGROUND_ACTIVITY,
                APP_B.FOREGROUND_ACTIVITY,
                APP_A.FOREGROUND_ACTIVITY);
        assertTaskStackIsEmpty(APP_A.BACKGROUND_ACTIVITY);
    }

    @Test
    public void testBackgroundActivityNotBlockedWhenForegroundActivityTop() throws Exception {
        startActivity(APP_A.FOREGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_A.FOREGROUND_ACTIVITY, APP_A.FOREGROUND_ACTIVITY);

        // Start AppA background activity successfully in new task as there's a foreground activity
        startBackgroundActivity(APP_A);
        mWmState.waitForValidState(APP_A.BACKGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_A.FOREGROUND_ACTIVITY, APP_A.FOREGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_A.BACKGROUND_ACTIVITY, APP_A.BACKGROUND_ACTIVITY);
    }

    @Test
    public void testBackgroundActivityWhenForegroundActivityNotTopUsingSdk33_IsNotBlocked()
            throws Exception {
        startActivity(APP_A_33.FOREGROUND_ACTIVITY);
        mContext.sendBroadcast(getLaunchActivitiesBroadcast(APP_A_33, APP_B.FOREGROUND_ACTIVITY));
        mWmState.waitForValidState(APP_B.FOREGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_A_33.FOREGROUND_ACTIVITY,
                        APP_B.FOREGROUND_ACTIVITY,
                        APP_A_33.FOREGROUND_ACTIVITY);

        // Start AppA background activity successfully as there's a foreground activity
        startBackgroundActivity(APP_A_33);
        mWmState.waitForValidState(APP_A_33.BACKGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_A_33.FOREGROUND_ACTIVITY,
                APP_B.FOREGROUND_ACTIVITY,
                APP_A_33.FOREGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_A_33.BACKGROUND_ACTIVITY, APP_A_33.BACKGROUND_ACTIVITY);
    }

    @Test
    public void testActivityNotBlockedWhenForegroundActivityLaunch() throws Exception {
        // Start foreground activity, and foreground activity able to launch background activity
        // successfully
        startActivity(APP_A.FOREGROUND_ACTIVITY);

        mContext.sendBroadcast(getLaunchActivitiesBroadcast(APP_A, APP_A.BACKGROUND_ACTIVITY));
        assertActivityFocused(APP_A.BACKGROUND_ACTIVITY);

        assertTaskStackHasComponents(APP_A.FOREGROUND_ACTIVITY,
                APP_A.BACKGROUND_ACTIVITY,
                APP_A.FOREGROUND_ACTIVITY);
    }

    @Test
    public void testActivityBroughtToTopOfTaskWhenLaunchedInTheBackground() throws Exception {
        // Start foreground activity, and foreground activity able to launch background activity
        // successfully
        startActivity(APP_A.FOREGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_A.FOREGROUND_ACTIVITY, APP_A.FOREGROUND_ACTIVITY);
        // We can't resume app switching after pressing home button, otherwise the grace period
        // will allow the starts.
        pressHomeAndWaitHomeResumed();

        mContext.sendBroadcast(getLaunchActivitiesBroadcast(APP_A, APP_A.BACKGROUND_ACTIVITY));

        assertActivityNotFocused(APP_A.FOREGROUND_ACTIVITY);
        assertActivityNotFocused(APP_A.BACKGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_A.FOREGROUND_ACTIVITY,
                APP_A.BACKGROUND_ACTIVITY,
                APP_A.FOREGROUND_ACTIVITY);
    }

    @Test
    public void testActivityBlockedFromBgActivityInFgTask() {
        assumeSdkNewerThanUpsideDownCake();
        // Launch Activity A, B in the same task with different processes.
        startActivity(APP_A.FOREGROUND_ACTIVITY);
        mContext.sendBroadcast(getLaunchActivitiesBroadcast(APP_A, APP_B.FOREGROUND_ACTIVITY));
        mWmState.waitForValidState(APP_B.FOREGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_A.FOREGROUND_ACTIVITY,
                APP_B.FOREGROUND_ACTIVITY,
                APP_A.FOREGROUND_ACTIVITY);

        // Refresh last-stop-app-switch-time by returning to home and then make the task foreground.
        pressHomeAndResumeAppSwitch();
        startActivityUnchecked(APP_A.FOREGROUND_ACTIVITY);
        mWmState.waitForValidState(APP_B.FOREGROUND_ACTIVITY);
        // As A is not visible, it can not start activities.
        mContext.sendBroadcast(new Intent(
                APP_A.FOREGROUND_ACTIVITY_ACTIONS.LAUNCH_BACKGROUND_ACTIVITIES)
                .putExtra(APP_A.FOREGROUND_ACTIVITY_EXTRA.LAUNCH_INTENTS,
                        new Intent[]{ new Intent()
                                .setComponent(APP_A.BACKGROUND_ACTIVITY)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }));
        assertActivityNotFocused(APP_A.BACKGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_A.FOREGROUND_ACTIVITY,
                APP_B.FOREGROUND_ACTIVITY,
                APP_A.FOREGROUND_ACTIVITY);
    }

    @Test
    public void testActivityFromBgActivityInFgTaskSdk33_isNotBlocked() {
        // Launch Activity A, B in the same task with different processes.
        startActivity(APP_A_33.FOREGROUND_ACTIVITY);
        mContext.sendBroadcast(getLaunchActivitiesBroadcast(APP_A_33, APP_B.FOREGROUND_ACTIVITY));
        mWmState.waitForValidState(APP_B.FOREGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_A_33.FOREGROUND_ACTIVITY,
                APP_B.FOREGROUND_ACTIVITY,
                APP_A_33.FOREGROUND_ACTIVITY);

        // Refresh last-stop-app-switch-time by returning to home and then make the task foreground.
        pressHomeAndResumeAppSwitch();
        startActivityUnchecked(APP_A_33.FOREGROUND_ACTIVITY);
        mWmState.waitForValidState(APP_B.FOREGROUND_ACTIVITY);
        // Though process A is in background, it is in a visible Task (top is B) so it should be
        // able to start activity successfully.
        mContext.sendBroadcast(new Intent(
                APP_A_33.FOREGROUND_ACTIVITY_ACTIONS.LAUNCH_BACKGROUND_ACTIVITIES)
                .putExtra(APP_A_33.FOREGROUND_ACTIVITY_EXTRA.LAUNCH_INTENTS,
                        new Intent[]{ new Intent()
                                .setComponent(APP_A_33.BACKGROUND_ACTIVITY)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }));
        mWmState.waitForValidState(APP_A_33.BACKGROUND_ACTIVITY);
        mWmState.assertFocusedActivity(
                "The background activity must be able to launch from a visible task",
                APP_A_33.BACKGROUND_ACTIVITY);
    }

    @Test
    @FlakyTest(bugId = 130800326)
    @Ignore  // TODO(b/145981637): Make this test work
    public void testActivityBlockedWhenForegroundActivityRestartsItself() throws Exception {
        // Start AppA foreground activity
        startActivity(APP_A.FOREGROUND_ACTIVITY,
                APP_A.FOREGROUND_ACTIVITY_EXTRA.RELAUNCH_FOREGROUND_ACTIVITY_EXTRA);
        assertTaskStackHasComponents(APP_A.FOREGROUND_ACTIVITY, APP_A.FOREGROUND_ACTIVITY);

        // The foreground activity will be paused but will attempt to restart itself in onPause()
        pressHomeAndResumeAppSwitch();

        assertActivityNotFocused(APP_A.FOREGROUND_ACTIVITY);
    }

    @Test
    public void testSecondActivityNotBlockedWhenForegroundActivityLaunch() throws Exception {
        // Start AppA foreground activity, which will immediately launch one activity
        // and then the second.
        startActivity(APP_A.FOREGROUND_ACTIVITY);

        mContext.sendBroadcast(getLaunchActivitiesBroadcast(APP_A, APP_A.BACKGROUND_ACTIVITY));
        assertActivityFocused(APP_A.BACKGROUND_ACTIVITY);
        mContext.sendBroadcast(getLaunchActivitiesBroadcast(APP_A, APP_A.SECOND_BACKGROUND_ACTIVITY));
        assertActivityFocused(APP_A.SECOND_BACKGROUND_ACTIVITY);

        assertTaskStackHasComponents(APP_A.FOREGROUND_ACTIVITY,
                APP_A.SECOND_BACKGROUND_ACTIVITY,
                APP_A.BACKGROUND_ACTIVITY,
                APP_A.FOREGROUND_ACTIVITY);
    }

    @Test
    public void testSecondActivityBlockedWhenBackgroundActivityLaunch() throws Exception {
        startActivity(APP_A.FOREGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_A.FOREGROUND_ACTIVITY, APP_A.FOREGROUND_ACTIVITY);
        // We can't resume app switching after pressing home button, otherwise the grace period
        // will allow the starts.
        pressHomeAndWaitHomeResumed();

        // The activity, now in the background, will attempt to start 2 activities in quick
        // succession
        mContext.sendBroadcast(getLaunchActivitiesBroadcast(APP_A,
                APP_A.BACKGROUND_ACTIVITY,
                APP_A.SECOND_BACKGROUND_ACTIVITY));

        // There should be 2 activities in the background (not focused) INITIALIZING
        assertActivityNotFocused(APP_A.BACKGROUND_ACTIVITY);
        assertActivityNotFocused(APP_A.SECOND_BACKGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_A.FOREGROUND_ACTIVITY,
                APP_A.SECOND_BACKGROUND_ACTIVITY,
                APP_A.BACKGROUND_ACTIVITY,
                APP_A.FOREGROUND_ACTIVITY);
    }

    @Test
    public void testPendingIntentActivityBlocked() throws Exception {
        TestServiceClient serviceA = getTestService(APP_A);
        TestServiceClient serviceB = getTestService(APP_B);

        // Cannot start activity by pending intent, as both appA and appB are in background
        PendingIntent pi = generatePendingIntent(serviceA, APP_A.BACKGROUND_ACTIVITY);
        sendPendingIntent(pi, serviceB);
        assertActivityNotFocused(APP_A.BACKGROUND_ACTIVITY);
        assertTaskStackIsEmpty(APP_A.BACKGROUND_ACTIVITY);
    }

    @Test
    public void testPendingIntentActivity_whenSenderAllowsBal_isNotBlocked() throws Exception {
        // creator (appa) is not privileged
        grantSystemAlertWindow(APP_A, false);
        // sender (appb) is privileged, and grants
        grantSystemAlertWindow(APP_B);

        startPendingIntentSenderActivity(APP_A, APP_B, /* allowBalBySender */ true);
        assertActivityFocused(APP_A.BACKGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_A.BACKGROUND_ACTIVITY, APP_A.BACKGROUND_ACTIVITY);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_BAL_DONT_BRING_EXISTING_BACKGROUND_TASK_STACK_TO_FG)
    public void testPI_onlyCreatorAllowsBALInSameTask_isNotBlocked() throws Exception {
        // creator (appa) is not privileged
        grantSystemAlertWindow(APP_A, false);
        // sender (appb) is privileged, and grants
        grantSystemAlertWindow(APP_B);

        startActivity(APP_A.FOREGROUND_ACTIVITY);

        pressHomeAndWaitHomeResumed();

        TestServiceClient serviceB = getTestService(APP_B);
        PendingIntent pi = serviceB.generatePendingIntent(APP_B.BACKGROUND_ACTIVITY,
                CREATE_OPTIONS_ALLOW_BAL);
        TestServiceClient serviceA = getTestService(APP_A);
        // send the pending intent in the same task.
        serviceA.sendPendingIntentWithActivity(pi, Bundle.EMPTY);

        assertActivityFocused(APP_B.BACKGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_A.FOREGROUND_ACTIVITY, APP_B.BACKGROUND_ACTIVITY,
                APP_A.FOREGROUND_ACTIVITY);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_BAL_DONT_BRING_EXISTING_BACKGROUND_TASK_STACK_TO_FG)
    public void testPI_onlyCreatorAllowsBALInSameTask_isBlocked() throws Exception {
        // sender (appa) is not privileged
        grantSystemAlertWindow(APP_A, false);
        // creator (appb) is privileged, and grants
        grantSystemAlertWindow(APP_B);

        startActivity(APP_A.FOREGROUND_ACTIVITY);

        pressHomeAndWaitHomeResumed();

        TestServiceClient serviceB = getTestService(APP_B);
        PendingIntent pi = serviceB.generatePendingIntent(APP_B.BACKGROUND_ACTIVITY,
                CREATE_OPTIONS_ALLOW_BAL);
        TestServiceClient serviceA = getTestService(APP_A);
        // send the pending intent in the same task.
        serviceA.sendPendingIntentWithActivity(pi, Bundle.EMPTY);

        assertActivityNotFocused(APP_B.BACKGROUND_ACTIVITY);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_BAL_REQUIRE_OPT_IN_BY_PENDING_INTENT_CREATOR)
    @RequiresFlagsDisabled(Flags.FLAG_BAL_DONT_BRING_EXISTING_BACKGROUND_TASK_STACK_TO_FG)
    public void testPI_onlyCreatorAllowsBALwithOptIn_isNotBlocked() throws Exception {
        // sender (appa) is not privileged
        grantSystemAlertWindow(APP_A, false);
        // creator (appb) is privileged, and grants
        grantSystemAlertWindow(APP_B);

        startActivity(APP_A.FOREGROUND_ACTIVITY);

        pressHomeAndWaitHomeResumed();

        TestServiceClient serviceB = getTestService(APP_B);
        PendingIntent pi = serviceB.generatePendingIntent(APP_B.BACKGROUND_ACTIVITY,
                CREATE_OPTIONS_ALLOW_BAL);
        TestServiceClient serviceA = getTestService(APP_A);
        serviceA.sendPendingIntentWithActivity(pi, Bundle.EMPTY);

        assertActivityFocused(APP_B.BACKGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_A.FOREGROUND_ACTIVITY, APP_B.BACKGROUND_ACTIVITY,
                APP_A.FOREGROUND_ACTIVITY);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_BAL_REQUIRE_OPT_IN_BY_PENDING_INTENT_CREATOR)
    @RequiresFlagsDisabled(Flags.FLAG_BAL_DONT_BRING_EXISTING_BACKGROUND_TASK_STACK_TO_FG)
    public void testPI_onlyCreatorAllowsBALwithoutOptInSdk33_isNotBlocked() throws Exception {
        // A (privileged) creates PI, B (non-privileged) sends PI from BG, C is started
        grantSystemAlertWindow(APP_A_33);
        grantSystemAlertWindow(APP_B, false);

        startActivity(APP_B.FOREGROUND_ACTIVITY);

        pressHomeAndWaitHomeResumed();

        TestServiceClient servicePiCreator = getTestService(APP_A_33);
        PendingIntent pi = servicePiCreator.generatePendingIntent(APP_C.BACKGROUND_ACTIVITY,
                CREATE_OPTIONS_ALLOW_BAL);
        TestServiceClient servicePiSender = getTestService(APP_B);
        servicePiSender.sendPendingIntentWithActivity(pi, Bundle.EMPTY);

        assertActivityFocused(APP_C.BACKGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_B.FOREGROUND_ACTIVITY, APP_C.BACKGROUND_ACTIVITY,
                APP_B.FOREGROUND_ACTIVITY);
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_BAL_REQUIRE_OPT_IN_BY_PENDING_INTENT_CREATOR,
            Flags.FLAG_BAL_DONT_BRING_EXISTING_BACKGROUND_TASK_STACK_TO_FG})
    public void testPI_onlyCreatorAllowsBALwithOptIn_isStartedInBackground() throws Exception {
        // sender (appa) is not privileged
        grantSystemAlertWindow(APP_A, false);
        // creator (appb) is privileged, and grants
        grantSystemAlertWindow(APP_B);

        startActivity(APP_A.FOREGROUND_ACTIVITY);

        pressHomeAndWaitHomeResumed();

        TestServiceClient serviceB = getTestService(APP_B);
        PendingIntent pi = serviceB.generatePendingIntent(APP_B.BACKGROUND_ACTIVITY,
                CREATE_OPTIONS_ALLOW_BAL);
        TestServiceClient serviceA = getTestService(APP_A);
        serviceA.sendPendingIntentWithActivity(pi, Bundle.EMPTY);

        assertActivityNotFocused(APP_B.BACKGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_A.FOREGROUND_ACTIVITY, APP_B.BACKGROUND_ACTIVITY,
                APP_A.FOREGROUND_ACTIVITY);
    }

    @Test
    public void testPI_onlySenderAllowsBALwithoutOptInForResult_isNotBlocked() throws Exception {
        startActivity(APP_A.FOREGROUND_ACTIVITY);

        TestServiceClient serviceB = getTestService(APP_B);
        PendingIntent pi = serviceB.generatePendingIntent(APP_B.BACKGROUND_ACTIVITY);
        TestServiceClient serviceA = getTestService(APP_A);
        // there is no explicit opt-in, but using sendPendingIntentForResult implicitly grants
        serviceA.sendPendingIntentWithActivityForResult(pi, Bundle.EMPTY);

        assertActivityFocused(APP_B.BACKGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_A.FOREGROUND_ACTIVITY, APP_B.BACKGROUND_ACTIVITY,
                APP_A.FOREGROUND_ACTIVITY);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_BAL_REQUIRE_OPT_IN_BY_PENDING_INTENT_CREATOR)
    public void testPI_onlyCreatorAllowsBALwithoutOptInForResult_isBlocked() throws Exception {
        // creator (appb) is privileged, and grants
        grantSystemAlertWindow(APP_B);

        startActivity(APP_A.FOREGROUND_ACTIVITY);
        pressHomeAndWaitHomeResumed();

        TestServiceClient serviceB = getTestService(APP_B);
        PendingIntent pi = serviceB.generatePendingIntent(APP_B.BACKGROUND_ACTIVITY);
        TestServiceClient serviceA = getTestService(APP_A);
        // sendPendingIntentForResult does not opt in the creator of the PI
        serviceA.sendPendingIntentWithActivityForResult(pi, Bundle.EMPTY);

        assertActivityNotFocused(APP_B.BACKGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_A.FOREGROUND_ACTIVITY, APP_A.FOREGROUND_ACTIVITY);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_BAL_REQUIRE_OPT_IN_BY_PENDING_INTENT_CREATOR)
    public void testPI_onlyCreatorAllowsBALwithoutOptIn_isBlocked() throws Exception {
        // sender (appa) is not privileged
        grantSystemAlertWindow(APP_A, false);
        // creator (appb) is privileged, and grants
        grantSystemAlertWindow(APP_B);

        startActivity(APP_A.FOREGROUND_ACTIVITY);

        pressHomeAndWaitHomeResumed();

        TestServiceClient serviceB = getTestService(APP_B);
        PendingIntent pi = serviceB.generatePendingIntent(APP_B.BACKGROUND_ACTIVITY);
        TestServiceClient serviceA = getTestService(APP_A);
        serviceA.sendPendingIntentWithActivity(pi, Bundle.EMPTY);

        assertActivityNotFocused(APP_B.BACKGROUND_ACTIVITY);
    }

    @Test
    public void testPendingIntentActivity_whenSenderDoesNotAllowBal_isBlocked() throws Exception {
        // creator (appa) is not privileged
        grantSystemAlertWindow(APP_A, false);
        // sender (appb) is privileged, but revokes
        grantSystemAlertWindow(APP_B);

        startPendingIntentSenderActivity(APP_A, APP_B, /* allowBalBySender */ false);

        assertActivityNotFocused(APP_A.BACKGROUND_ACTIVITY);
    }

    @Test
    public void testPI_appAIsForegroundDenyCreatorPrivilege_launchAppB_isBlocked()
            throws Exception {
        // Start AppA foreground activity
        startActivity(APP_A.FOREGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_A.FOREGROUND_ACTIVITY, APP_A.FOREGROUND_ACTIVITY);

        // App A create a PendingIntent with ActivityOption that denies PendingIntent sender to use
        // creator's privilege to launch itself. The PendingIntent itself is to launch App B. Since
        // App B is in the background, it should be blocked even though the creator (App A) is in
        // the foreground.
        TestServiceClient serviceA = getTestService(APP_A);
        PendingIntent pi = serviceA.generatePendingIntent(APP_B.BACKGROUND_ACTIVITY,
                CREATE_OPTIONS_DENY_BAL);
        TestServiceClient serviceB = getTestService(APP_B);
        sendPendingIntent(pi, serviceB);
        assertActivityNotFocused(APP_B.FOREGROUND_ACTIVITY);
    }

    @Test
    @SuppressWarnings("MissingFail") // TODO(b/320664730) expect Exception after V release
    public void testPI_appAIsFgDenyCreatorPrivilege_appBTryOverrideCreatorPrivilege_isBlocked()
            throws Exception {
        // Start AppB foreground activity
        startActivity(APP_A.FOREGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_A.FOREGROUND_ACTIVITY, APP_A.FOREGROUND_ACTIVITY);

        // App A create a PendingIntent with ActivityOption that denies PendingIntent sender to use
        // creator's privilege to launch itself. The PendingIntent itself is to launch App B.
        // App B is in the background, it should be blocked even though the creator (App A) is in
        // the foreground. However, The sender (App B) also tries to override the creator option by
        // setting the creator option from the sender side. This should not work. Creator option
        // cannot be set from the sender side.
        TestServiceClient serviceA = getTestService(APP_A);
        PendingIntent pi =
                serviceA.generatePendingIntent(APP_B.BACKGROUND_ACTIVITY,
                        CREATE_OPTIONS_DENY_BAL);
        TestServiceClient serviceB = getTestService(APP_B);
        try {
            serviceB.sendPendingIntent(pi, CREATE_OPTIONS_ALLOW_BAL);
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessageThat()
                    .contains("pendingIntentCreatorBackgroundActivityStartMode");
            // If sending the PendingIntent failed because we detected a mismatch in the bundle,
            // the result should be the same as suppressing the start (just earlier).
        }
        assertActivityNotFocused(APP_B.FOREGROUND_ACTIVITY);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_BAL_REQUIRE_OPT_IN_BY_PENDING_INTENT_CREATOR)
    public void testPendingIntentActivity_appAIsForeground_isNotBlocked() throws Exception {
        // Start AppA foreground activity
        Components appA = APP_A;
        startActivity(appA.FOREGROUND_ACTIVITY);
        assertTaskStackHasComponents(appA.FOREGROUND_ACTIVITY, appA.FOREGROUND_ACTIVITY);

        // Send pendingIntent from AppA to AppB, and the AppB launch the pending intent to start
        // activity in App A
        TestServiceClient serviceA = getTestService(appA);
        PendingIntent pi = generatePendingIntent(serviceA, appA.BACKGROUND_ACTIVITY);
        TestServiceClient serviceB = getTestService(APP_B);
        sendPendingIntent(pi, serviceB);

        assertActivityFocused(appA.BACKGROUND_ACTIVITY);
        assertTaskStackHasComponents(appA.FOREGROUND_ACTIVITY, appA.FOREGROUND_ACTIVITY);
        assertTaskStackHasComponents(appA.BACKGROUND_ACTIVITY, appA.BACKGROUND_ACTIVITY);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_BAL_REQUIRE_OPT_IN_BY_PENDING_INTENT_CREATOR)
    public void testPendingIntentActivity_appAIsForeground_isBlocked() throws Exception {
        // Start AppA foreground activity
        Components appA = APP_A;
        startActivity(appA.FOREGROUND_ACTIVITY);
        assertTaskStackHasComponents(appA.FOREGROUND_ACTIVITY, appA.FOREGROUND_ACTIVITY);

        // Send pendingIntent from AppA to AppB, and the AppB launch the pending intent to start
        // activity in App A
        TestServiceClient serviceA = getTestService(appA);
        PendingIntent pi = generatePendingIntent(serviceA, appA.BACKGROUND_ACTIVITY);
        TestServiceClient serviceB = getTestService(APP_B);
        sendPendingIntent(pi, serviceB);

        assertActivityNotFocused(appA.BACKGROUND_ACTIVITY);
    }

    @Test
    public void testPendingIntentActivity_appA33IsForeground_isNotBlocked() throws Exception {
        // Start AppA foreground activity
        Components appA = APP_A_33;
        startActivity(appA.FOREGROUND_ACTIVITY);
        assertTaskStackHasComponents(appA.FOREGROUND_ACTIVITY, appA.FOREGROUND_ACTIVITY);

        // Send pendingIntent from AppA to AppB, and the AppB launch the pending intent to start
        // activity in App A
        TestServiceClient serviceA = getTestService(appA);
        PendingIntent pi = generatePendingIntent(serviceA, appA.BACKGROUND_ACTIVITY);
        TestServiceClient serviceB = getTestService(APP_B);
        sendPendingIntent(pi, serviceB);

        assertActivityFocused(appA.BACKGROUND_ACTIVITY);
        assertTaskStackHasComponents(appA.FOREGROUND_ACTIVITY, appA.FOREGROUND_ACTIVITY);
        assertTaskStackHasComponents(appA.BACKGROUND_ACTIVITY, appA.BACKGROUND_ACTIVITY);
    }

    @Test
    public void testPendingIntentBroadcastActivity_appBIsForeground_isBlocked() throws Exception {
        // Start AppB foreground activity
        startActivity(APP_B.FOREGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_B.FOREGROUND_ACTIVITY, APP_B.FOREGROUND_ACTIVITY);

        // Send pendingIntent from AppA to AppB, and the AppB launch the pending intent to start
        // activity in App A
        TestServiceClient serviceA = getTestService(APP_A);
        PendingIntent pi = generatePendingIntent(serviceA, APP_A.BACKGROUND_ACTIVITY);
        TestServiceClient serviceB = getTestService(APP_B);
        sendPendingIntent(pi, serviceB);

        assertActivityNotFocused(APP_A.BACKGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_B.FOREGROUND_ACTIVITY, APP_B.FOREGROUND_ACTIVITY);
    }

    @Test
    public void testPendingIntentBroadcastActivity_appBIsForegroundAndSdk33_isNotBlocked()
            throws Exception {

        // setup
        TestServiceClient appATestService = getTestService(APP_A);
        TestServiceClient appBTestService = getTestService(APP_B_33);

        // create PI in appA
        PendingIntent pi = generatePendingIntent(appATestService, APP_A.BACKGROUND_ACTIVITY);

        // bring app B to foreground
        startActivity(APP_B_33.FOREGROUND_ACTIVITY);

        // pass to appB and send PI
        appBTestService.sendPendingIntent(pi, null);

        // assert that start succeeded
        assertActivityFocused(APP_A.BACKGROUND_ACTIVITY);
    }

    @Test
    @SuppressWarnings("MissingFail") // TODO(b/320664730) expect Exception after V release
    public void testPendingIntentBroadcastActivity_appBIsForegroundAndTryPassBalOnIntent_isBlocked()
            throws Exception {
        // Start AppB foreground activity
        startActivity(APP_B.FOREGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_B.FOREGROUND_ACTIVITY, APP_B.FOREGROUND_ACTIVITY);

        // Send pendingIntent from AppA to AppB, and the AppB launch the pending intent to start
        // activity in App A.
        // ALLOW_BAL_EXTRA_ON_PENDING_INTENT will trigger AppA (the creator) to try to allow BAL on
        // behalf of the sender by adding the BAL option to the Intent's extras, which should have
        // no effect.
        TestServiceClient serviceA = getTestService(APP_A);
        try {
            PendingIntent pi = serviceA.generatePendingIntent(APP_A.BACKGROUND_ACTIVITY,
                            SEND_OPTIONS_ALLOW_BAL);
            TestServiceClient serviceB = getTestService(APP_B);
            sendPendingIntent(pi, serviceB);
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessageThat().contains("pendingIntentBackgroundActivityStartMode");
            // If generating the PendingIntent failed because we detected a mismatch in the bundle,
            // the result should be the same as suppressing the start (just earlier).
        }
        assertActivityNotFocused(APP_A.BACKGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_B.FOREGROUND_ACTIVITY, APP_B.FOREGROUND_ACTIVITY);
    }

    @Test
    @SuppressWarnings("MissingFail") // TODO(b/320664730) expect Exception after V release
    public void testPendingIntentBroadcastActivity_appBIsFgAndTryPassBalOnIntentWithNullBundleOnPendingIntent_isBlocked()
            throws Exception {
        // Start AppB foreground activity
        startActivity(APP_B.FOREGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_B.FOREGROUND_ACTIVITY, APP_B.FOREGROUND_ACTIVITY);

        // Send pendingIntent from AppA to AppB, and the AppB launch the pending intent to start
        // activity in App A
        TestServiceClient serviceA = getTestService(APP_A);
        try {
            PendingIntent pi = serviceA.generatePendingIntent(APP_A.BACKGROUND_ACTIVITY,
                    SEND_OPTIONS_ALLOW_BAL);
            TestServiceClient serviceB = getTestService(APP_B);
            serviceB.sendPendingIntent(pi, null);
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessageThat().contains("pendingIntentBackgroundActivityStartMode");
            // If generating the PendingIntent failed because we detected a mismatch in the bundle,
            // the result should be the same as suppressing the start (just earlier).
        }

        assertActivityNotFocused(APP_A.BACKGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_B.FOREGROUND_ACTIVITY, APP_B.FOREGROUND_ACTIVITY);
    }

    @Test
    public void testPendingIntentBroadcastActivity_appBIsForegroundAndAllowsBal_isNotBlocked()
            throws Exception {
        // setup
        TestServiceClient appATestService = getTestService(APP_A);
        TestServiceClient appBTestService = getTestService(APP_B);

        // create PI in appA
        PendingIntent pi = generatePendingIntent(appATestService, APP_A.BACKGROUND_ACTIVITY);

        // bring app B to foreground
        startActivity(APP_B.FOREGROUND_ACTIVITY);

        // pass to appB and send PI
        appBTestService.sendPendingIntent(pi, SEND_OPTIONS_ALLOW_BAL);

        // assert that start succeeded
        assertActivityFocused(APP_A.BACKGROUND_ACTIVITY);
    }

    @Test
    public void testPendingIntentBroadcastTimeout_noDelay_isNotBlocked() throws Exception {
        TestServiceClient serviceA = getTestService(APP_A);
        TestServiceClient serviceB = getTestService(APP_B);

        // Start AppB foreground activity
        startActivity(APP_B.FOREGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_B.FOREGROUND_ACTIVITY, APP_B.FOREGROUND_ACTIVITY);

        EventReceiver receiver = new EventReceiver(Event.BROADCAST_RECEIVED);
        PendingIntent pi = serviceA.generatePendingIntentBroadcast(APP_A.SIMPLE_BROADCAST_RECEIVER,
                receiver.getNotifier());
        // PI broadcast should create token to allow serviceA to start activities later
        serviceB.sendPendingIntent(pi, SEND_BROADCAST_OPTIONS_ALLOW_BAL);
        receiver.waitForEventOrThrow(ACTIVITY_START_TIMEOUT_MS);

        // Grace period is still active.
        startBackgroundActivity(serviceA, APP_A);

        assertActivityFocused(APP_A.BACKGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_A.BACKGROUND_ACTIVITY, APP_A.BACKGROUND_ACTIVITY);
    }

    @Test
    public void testPendingIntentBroadcastTimeout_delay1s_isNotBlocked() throws Exception {
        TestServiceClient serviceA = getTestService(APP_A);
        TestServiceClient serviceB = getTestService(APP_B);

        // Start AppB foreground activity
        startActivity(APP_B.FOREGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_B.FOREGROUND_ACTIVITY, APP_B.FOREGROUND_ACTIVITY);

        EventReceiver receiver = new EventReceiver(Event.BROADCAST_RECEIVED);
        PendingIntent pi = serviceA.generatePendingIntentBroadcast(APP_A.SIMPLE_BROADCAST_RECEIVER,
                receiver.getNotifier());
        // PI broadcast should create token to allow serviceA to start activities later
        serviceB.sendPendingIntent(pi, SEND_BROADCAST_OPTIONS_ALLOW_BAL);
        receiver.waitForEventOrThrow(ACTIVITY_START_TIMEOUT_MS);

        SystemClock.sleep(1000);
        // Grace period is still active.
        startBackgroundActivity(serviceA, APP_A);

        assertActivityFocused(APP_A.BACKGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_A.BACKGROUND_ACTIVITY, APP_A.BACKGROUND_ACTIVITY);
    }

    @Test
    public void testPendingIntentBroadcastTimeout_delay12s_isBlocked() throws Exception {
        // This test is testing that activity start is blocked after broadcast allowlist token
        // timeout. Before the timeout, the start would be allowed because app B (the PI sender) was
        // in the foreground during PI send, so app A (the PI creator) would have
        // (10s * hw_multiplier) to start background activity starts.
        TestServiceClient serviceA = getTestService(APP_A);
        TestServiceClient serviceB = getTestService(APP_B);

        // Start AppB foreground activity
        startActivity(APP_B.FOREGROUND_ACTIVITY);
        assertTaskStackHasComponents(APP_B.FOREGROUND_ACTIVITY, APP_B.FOREGROUND_ACTIVITY);

        EventReceiver receiver = new EventReceiver(Event.BROADCAST_RECEIVED);
        PendingIntent pi = serviceA.generatePendingIntentBroadcast(APP_A.SIMPLE_BROADCAST_RECEIVER,
                receiver.getNotifier());
        // PI broadcast should create token to allow serviceA to start activities later
        serviceB.sendPendingIntent(pi, SEND_BROADCAST_OPTIONS_ALLOW_BAL);
        receiver.waitForEventOrThrow(ACTIVITY_START_TIMEOUT_MS);

        SystemClock.sleep(12000L * HW_TIMEOUT_MULTIPLIER);
        // Grace period is expired.
        startBackgroundActivity(serviceA, APP_A);

        assertActivityNotFocused(APP_A.BACKGROUND_ACTIVITY);
        assertTaskStackIsEmpty(APP_A.BACKGROUND_ACTIVITY);
    }

    @Test
    public void testPendingIntentBroadcast_appBIsBackground_isBlocked() throws Exception {
        // Send pendingIntent from AppA to AppB, and the AppB launch the pending intent to start
        // activity in App A. Since AppB is not in foreground and has no other privileges to start
        // an activity the start should be blocked.
        TestServiceClient serviceA = getTestService(APP_A);
        PendingIntent pi = serviceA.generatePendingIntentBroadcast(APP_B.FOREGROUND_ACTIVITY);
        TestServiceClient serviceB = getTestService(APP_B);
        serviceB.sendPendingIntent(pi, null);

        assertActivityNotFocused(APP_A.BACKGROUND_ACTIVITY);
        assertTaskStackIsEmpty(APP_A.BACKGROUND_ACTIVITY);
    }

    /**
     * Returns a list of alive users on the device
     */
    private List<UserInfo> getAliveUsers() {
        // Setting the CREATE_USERS permission in AndroidManifest.xml has no effect when the test
        // is run through the CTS harness, so instead adopt it as a shell permission. We use
        // the CREATE_USERS permission instead of MANAGE_USERS because the shell can never use
        // MANAGE_USERS.
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity(Manifest.permission.CREATE_USERS);
        List<UserInfo> userList = mContext.getSystemService(UserManager.class).getAliveUsers();
        uiAutomation.dropShellPermissionIdentity();
        return userList;
    }

    /**
     * Removes the guest user from the device if present
     */
    private void removeGuestUser() {
        List<UserInfo> userList = getAliveUsers();
        for (UserInfo info : userList) {
            if (info.isGuest()) {
                removeUser(info.id);
                // Device is only allowed to have one alive guest user, so stop if it's found
                break;
            }
        }
    }

    /**
     * Removes a user from the device given their ID
     */
    private void removeUser(int userId) {
        executeShellCommand(String.format("pm remove-user %d", userId));
    }

    @Test
    public void testDeviceOwner() throws Exception {
        assume().withMessage("Device doesn't support FEATURE_DEVICE_ADMIN")
                .that(mContext.getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN))
                .isTrue();

        // Remove existing guest user. The device may already have a guest present if it is
        // configured with config_guestUserAutoCreated.
        //
        // In production flow the DO can only be created before device provisioning finishes
        // (e.g. during SUW), and we make sure the guest user in only created after the device
        // provision is finished. Ideally this test would use the provisioning flow and Device
        // Owner (DO) creation in a similar manner as that of production flow.
        removeGuestUser();

        // This test might be running as current user (on devices that use headless system user
        // mode), so it needs to get the context for the system user.
        Context context = runWithShellPermissionIdentity(
                () -> mContext.createContextAsUser(UserHandle.SYSTEM, /* flags= */ 0),
                INTERACT_ACROSS_USERS);

        String doComponent = APP_A.SIMPLE_ADMIN_RECEIVER.flattenToString();
        Log.d(TAG, "Setting DO as " + doComponent);
        String cmd = "dpm set-device-owner --user " + UserHandle.USER_SYSTEM + " " + doComponent;
        try {
            String cmdResult = runShellCommandOrThrow(cmd);
            assertWithMessage("Result of '%s'", cmd).that(cmdResult).contains("Success");
        } catch (AssertionError e) {
            // If failed to set the device owner, stop proceeding to the test case.
            // Log the error info so that we can investigate further in the future.
            Log.d(TAG, "Failed to set device owner.", e);
            String cmdResult = runShellCommandOrThrow("pm list user");
            Log.d(TAG, "users: " + cmdResult);
            cmdResult = runShellCommandOrThrow("dpm list-owner");
            Log.d(TAG, "device owners: " + cmdResult);
            assume().withMessage("This test needs to be able to set device owner")
                    .fail();
        }

        // Send pendingIntent from AppA to AppB, and the AppB launch the pending intent to start
        // activity in App A
        Log.d(TAG, "Launching " + APP_A.BACKGROUND_ACTIVITY + " on " + context.getUser());
        // Must run with IAC permission as it might be a context from other user
        runWithShellPermissionIdentity(() -> startBackgroundActivity(APP_A), INTERACT_ACROSS_USERS);

        // Waits for final hoop in AppA to start looking for activity
        if (UserManager.isHeadlessSystemUserMode()) {
            assertActivityNotFocused(APP_A.BACKGROUND_ACTIVITY);
            assertTaskDoesNotHaveVisibleComponents(APP_A.BACKGROUND_ACTIVITY,
                    APP_A.BACKGROUND_ACTIVITY);
        } else {
            assertActivityFocused(APP_A.BACKGROUND_ACTIVITY);
            assertTaskStackHasComponents(APP_A.BACKGROUND_ACTIVITY, APP_A.BACKGROUND_ACTIVITY);
        }
    }

    @Test
    public void testAppCannotStartBgActivityAfterHomeButton() throws Exception {
        startActivity(APP_A.RELAUNCHING_ACTIVITY);

        // Click home button, and test app activity onPause() will try to start a background
        // activity, but we expect this will be blocked BAL logic in system, as app cannot start
        // any background activity even within grace period after pressing home button.
        pressHomeAndWaitHomeResumed();

        assertActivityNotFocused(APP_A.BACKGROUND_ACTIVITY);
    }

    // Check picture-in-picture(PIP) won't allow to start BAL after pressing home.
    @Test
    public void testPipCannotStartAfterHomeButton() throws Exception {
        startActivity(APP_A.PIP_ACTIVITY);

        // Click home button, and test app activity onPause() will trigger pip window,
        // test will will try to start background activity, but we expect the background activity
        // will be blocked even the app has a visible pip window, as we do not allow background
        // activity to be started after pressing home button.
        pressHomeAndWaitHomeResumed();

        assertActivityNotFocused(APP_A.BACKGROUND_ACTIVITY);
    }

    @Test
    @AsbSecurityTest(cveBugId = 271576718)
    public void testPipCannotStartFromBackground() throws Exception {
        startActivity(APP_A.LAUNCH_INTO_PIP_ACTIVITY);

        pressHomeAndWaitHomeResumed();
        assertActivityNotFocused(APP_A.LAUNCH_INTO_PIP_ACTIVITY);

        Intent broadcast = new Intent(APP_A.LAUNCH_INTO_PIP_ACTIONS.LAUNCH_INTO_PIP);
        mContext.sendBroadcast(broadcast);
        assertActivityNotFocused(APP_A.BACKGROUND_ACTIVITY);

        assertPinnedStackDoesNotExist();
    }

    // Check that a presentation on a virtual display won't allow BAL after pressing home.
    @Test
    public void testPrivateVirtualDisplayCannotStartAfterHomeButton() throws Exception {
        startActivity(APP_A.VIRTUAL_DISPLAY_ACTIVITY);

        // Click home button, and test app activity onPause() will trigger which tries to launch
        // the background activity.
        pressHomeAndWaitHomeResumed();

        assertActivityNotFocused(APP_A.BACKGROUND_ACTIVITY);
    }

    // Check that a presentation on a virtual display won't allow BAL after pressing home.
    @Test
    public void testPublicVirtualDisplayCannotStartAfterHomeButton() throws Exception {
        startActivity(APP_A.VIRTUAL_DISPLAY_ACTIVITY,
                APP_A.VIRTUAL_DISPLAY_ACTIVITY_EXTRA.USE_PUBLIC_PRESENTATION);

        // Click home button, and test app activity onPause() will trigger which tries to launch
        // the background activity.
        pressHomeAndWaitHomeResumed();

        assertActivityNotFocused(APP_A.BACKGROUND_ACTIVITY);
    }


    // Test manage space pending intent created by system cannot bypass BAL check.
    @Test
    public void testManageSpacePendingIntentNoBalAllowed() throws Exception {
        TestServiceClient appATestService = getTestService(APP_A);
        runWithShellPermissionIdentity(() -> {
            runShellCommandOrThrow("cmd appops set " + APP_A.APP_PACKAGE_NAME
                    + " android:manage_external_storage allow");
        });
        // Make sure AppA paused at least 10s so it can't start activity because of grace period.
        Thread.sleep(1000 * 10);
        appATestService.startManageSpaceActivity();
        assertActivityNotFocused(APP_A.BACKGROUND_ACTIVITY);
        assertTaskStackIsEmpty(APP_A.BACKGROUND_ACTIVITY);
    }

    @Test
    public void testAppWidgetConfigNoBalBypass() throws Exception {
        // Click bind widget button and then go home screen so app A will enter background state
        // with bind widget ability.
        EventReceiver receiver = new EventReceiver(Event.APP_A_START_WIDGET_CONFIG_ACTIVITY);
        clickAllowBindWidget(APP_A, receiver.getNotifier());
        pressHomeAndWaitHomeResumed();

        // After pressing home button, wait for appA to start widget config activity.
        receiver.waitForEventOrThrow(1000 * 30);

        assertActivityNotFocused(APP_A.BACKGROUND_ACTIVITY);
        assertTaskStackIsEmpty(APP_A.BACKGROUND_ACTIVITY);
    }

    @Test
    public void testBalOptInBindToService_whenOptedIn_allowsActivityStarts() {
        startActivityUnchecked(APP_C.BIND_SERVICE_ACTIVITY,
                "android.server.wm.backgroundactivity.appc.ALLOW_BAL");
        assertActivityFocused(APP_A.BACKGROUND_ACTIVITY);
    }

    @Test
    public void testBalOptInBindToService_whenNotOptedIn_blocksActivityStarts() {
        startActivityUnchecked(APP_C.BIND_SERVICE_ACTIVITY);
        assertActivityNotFocused(APP_A.BACKGROUND_ACTIVITY);
    }

    @Test
    public void testBalOptInBindToService_whenNotOptedInAndSdk33_allowsActivityStart() {
        startActivityUnchecked(APP_C_33.BIND_SERVICE_ACTIVITY);
        assertActivityFocused(APP_A.BACKGROUND_ACTIVITY);
    }

    @Test
    public void testActivityStartbyTextClassifier_appBInFg_allowsActivityStart() throws Exception {
        TestServiceClient appATestService = getTestService(APP_A);
        TestServiceClient appBTestService = getTestService(APP_B);
        // create PI in appA
        PendingIntent pi = generatePendingIntent(appATestService, APP_A.BACKGROUND_ACTIVITY);

        // app B in foreground
        startActivity(APP_B.FOREGROUND_ACTIVITY);
        // pass to appB and send PI
        TextClassification tc = new TextClassification.Builder()
                .addAction(new RemoteAction(EMPTY_ICON, "myAction",
                        "classifiedContentDescription", pi))
                .build();
        appBTestService.sendByTextClassification(tc);

        // assert that start succeeded
        assertActivityFocused(APP_A.BACKGROUND_ACTIVITY);
    }

    @Test
    public void testActivityStartbyTextClassifier_appBInBg_blocksActivityStart() throws Exception {
        TestServiceClient appATestService = getTestService(APP_A);
        TestServiceClient appBTestService = getTestService(APP_B);
        // create PI in appA
        PendingIntent pi = generatePendingIntent(appATestService, APP_A.BACKGROUND_ACTIVITY);

        // app B not in FG
        // pass to appB and send PI
        TextClassification tc = new TextClassification.Builder()
                .addAction(new RemoteAction(EMPTY_ICON, "myAction",
                        "classifiedContentDescription", pi))
                .build();
        appBTestService.sendByTextClassification(tc);

        // assert that start is blocked
        assertActivityNotFocused(APP_A.BACKGROUND_ACTIVITY);
    }

    private void clickAllowBindWidget(Components app, ResultReceiver resultReceiver)
            throws Exception {
        PackageManager pm = mContext.getPackageManager();
        assume().that(pm.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS)).isTrue();
        // Skip on auto and TV devices only as they don't support appwidget bind.
        assume().that(pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)).isFalse();
        assume().that(pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY)).isFalse();

        // Create appWidgetId so we can send it to app, to request bind widget and start config
        // activity.
        UiDevice device = UiDevice.getInstance(mInstrumentation);
        AppWidgetHost appWidgetHost = new AppWidgetHost(mContext, 0);
        final int appWidgetId = appWidgetHost.allocateAppWidgetId();
        Intent appWidgetIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
        appWidgetIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        appWidgetIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,
                app.WIDGET_PROVIDER);

        Intent intent = new Intent();
        intent.setComponent(app.WIDGET_CONFIG_TEST_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(Intent.EXTRA_INTENT, appWidgetIntent);
        intent.putExtra(EVENT_NOTIFIER_EXTRA, resultReceiver);
        mContext.startActivity(intent);

        // Find settings package and bind widget activity and click the create button.
        String settingsPkgName = "";
        List<ResolveInfo> ris = pm.queryIntentActivities(appWidgetIntent,
                PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo ri : ris) {
            if (ri.activityInfo.name.contains("AllowBindAppWidgetActivity")) {
                settingsPkgName = ri.activityInfo.packageName;
            }
        }
        assertWithMessage("Cannot find settings app").that(settingsPkgName).isNotEmpty();
        assertWithMessage("Unable to start AllowBindAppWidgetActivity")
                .that(device.wait(Until.hasObject(By.pkg(settingsPkgName)), 1000 * 10)).isTrue();
        boolean buttonClicked = false;
        BySelector selector = By.clickable(true);
        List<UiObject2> objects = device.findObjects(selector);
        for (UiObject2 object : objects) {
            String objectText = object.getText();
            if (objectText == null) {
                continue;
            }
            if (objectText.equalsIgnoreCase("CREATE") || objectText.equalsIgnoreCase("ALLOW")) {
                object.click();
                buttonClicked = true;
                break;
            }
        }
        assertWithMessage("Create' button not found/clicked")
                .that(device.wait(Until.gone(By.pkg(settingsPkgName)), 1000 * 10) && buttonClicked)
                .isTrue();

        // Wait the bind widget activity goes away.
        waitUntilForegroundChanged(settingsPkgName, false,
                ACTIVITY_NOT_RESUMED_TIMEOUT_MS);
    }

    private void pressHomeAndWaitHomeResumed() {
        assumeSetupComplete();
        pressHomeButton();
        mWmState.waitForHomeActivityVisible();
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
    }

    private void assumeSetupComplete() {
        assume().that(Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.USER_SETUP_COMPLETE, 0)).isEqualTo(1);
    }

    private boolean checkPackageResumed(String pkg) {
        WindowManagerStateHelper helper = new WindowManagerStateHelper();
        helper.computeState();
        return ComponentName.unflattenFromString(
                helper.getFocusedActivity()).getPackageName().equals(pkg);
    }

    // Return true if the state of the package is changed to target state.
    private boolean waitUntilForegroundChanged(String targetPkg, boolean toBeResumed, int timeout)
            throws Exception {
        long startTime = System.currentTimeMillis();
        while (checkPackageResumed(targetPkg) != toBeResumed) {
            if (System.currentTimeMillis() - startTime < timeout) {
                Thread.sleep(100);
            } else {
                return false;
            }
        }
        return true;
    }

    private void assertActivityNotResumed(Components app) throws Exception {
        assertWithMessage("Test activity is resumed").that(
                waitUntilForegroundChanged(app.APP_PACKAGE_NAME, true,
                        ACTIVITY_NOT_RESUMED_TIMEOUT_MS)).isFalse();
    }

    private void pressHomeAndResumeAppSwitch() {
        // Press home key to ensure stopAppSwitches is called because the last-stop-app-switch-time
        // is a criteria of allowing background start.
        pressHomeButton();
        // Resume the stopped state (it won't affect last-stop-app-switch-time) so we don't need to
        // wait extra time to prevent the next launch from being delayed.
        resumeAppSwitches();
        mWmState.waitForHomeActivityVisible();
        // Resuming app switches again after home became visible because the previous call might
        // have raced with pressHomeButton().
        // TODO(b/155454710): Remove previous call after making sure all the tests don't depend on
        // the timing here.
        resumeAppSwitches();
    }

    private void startPendingIntentSenderActivity(Components appToCreatePendingIntent,
            Components appToSendPendingIntent, boolean allowBal) throws Exception {
        TestServiceClient testServiceToCreatePendingIntent =
                getTestService(appToCreatePendingIntent);
        // Get a PendingIntent created by appToCreatePendingIntent.
        final PendingIntent pi;
        try {
            pi = generatePendingIntent(testServiceToCreatePendingIntent,
                    appToCreatePendingIntent.BACKGROUND_ACTIVITY);
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        // Start app B's activity so it runs send() on PendingIntent created by app A.
        Intent secondIntent = new Intent();
        secondIntent.setComponent(appToSendPendingIntent.START_PENDING_INTENT_ACTIVITY);
        secondIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        secondIntent.putExtra(
                appToSendPendingIntent.START_PENDING_INTENT_ACTIVITY_EXTRA.PENDING_INTENT, pi);
        secondIntent.putExtra(
                appToSendPendingIntent.START_PENDING_INTENT_ACTIVITY_EXTRA.START_BUNDLE,
                ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(
                        allowBal ? ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                                : ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_DENIED)
                        .toBundle());
        mContext.startActivity(secondIntent);
    }

    /**
     * Start the given activity in a new task.
     *
     * After starting the activity this method asserts that the activity is actually started and is
     * shown as the focused activity in the foreground.
     *
     * @param componentName activity to start
     * @param extraTrueNames (optional) names of extras that should be set to <code>true</code>
     */
    private void startActivity(ComponentName componentName, String... extraTrueNames) {
        startActivityUnchecked(componentName, extraTrueNames);
        assertActivityFocused(componentName);
    }

    /**
     * Start the given activity in a new task.
     *
     * There is no check that the activity actually got started or that it is now in the foreground.
     *
     * @param componentName activity to start
     * @param extraTrueNames (optional) names of extras that should be set to <code>true</code>
     */
    private void startActivityUnchecked(ComponentName componentName, String... extraTrueNames) {
        Intent intent = new Intent();
        intent.setComponent(componentName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        for (String extraTrueName : extraTrueNames) {
            intent.putExtra(extraTrueName, true);
        }
        mContext.startActivity(intent);
    }

    private static void grantSystemAlertWindow(Components app) throws Exception {
        grantSystemAlertWindow(app, true);
    }

    private static void grantSystemAlertWindow(Components app, boolean allow) throws Exception {
        grantSystemAlertWindow(app.APP_PACKAGE_NAME, allow);
    }

    private static void grantSystemAlertWindow(String packageName, boolean allow) throws Exception {
        final int mode = allow ? MODE_ALLOWED : MODE_ERRORED;
        final String opStr = "android:system_alert_window";
        AppOpsUtils.setOpMode(packageName, opStr, mode);
        assertThat(AppOpsUtils.getOpMode(packageName, opStr)).isEqualTo(mode);
    }

    private static void startBackgroundActivity(TestServiceClient service, Components app)
            throws Exception {
        service.startActivityIntent(new Intent().setComponent(app.BACKGROUND_ACTIVITY)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void startBackgroundActivity(Components app) throws Exception {
        startBackgroundActivity(getTestService(app), app);
    }

    void sendBroadcastAndWait(ComponentName componentName) throws Exception {
        EventReceiver receiver = new EventReceiver(Event.BROADCAST_RECEIVED);
        Intent intent = new Intent();
        intent.setComponent(componentName);
        intent.putExtra(EVENT_NOTIFIER_EXTRA, receiver.getNotifier());
        mContext.sendBroadcast(intent);
        receiver.waitForEventOrThrow(ACTIVITY_START_TIMEOUT_MS);
    }

    private static PendingIntent generatePendingIntent(TestServiceClient testService,
            ComponentName activity) throws RemoteException {
        return testService.generatePendingIntent(activity, null);
    }

    private static void sendPendingIntent(PendingIntent pi, TestServiceClient service)
            throws RemoteException {
        service.sendPendingIntent(pi, Bundle.EMPTY);
    }

    static void assumeSdkNewerThanUpsideDownCake() {
        // Feature flag "ActivitySecurity__asm_restrictions_enabled" is set to 1 in
        // BackgroundActivityTestBase. For backward compatibility reasons, it is only enabled
        // for apps with targetSdkVersion starting Android V.
        // TODO remove this assumption after V released.
        assume().that(Build.VERSION.SDK_INT).isGreaterThan(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
    }
}
