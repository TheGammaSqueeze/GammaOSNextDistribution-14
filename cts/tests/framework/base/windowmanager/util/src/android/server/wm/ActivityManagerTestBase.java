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
 * limitations under the License
 */

package android.server.wm;

import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW;
import static android.app.Instrumentation.ActivityMonitor;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_ASSISTANT;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_RECENTS;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.content.Intent.ACTION_MAIN;
import static android.content.Intent.CATEGORY_HOME;
import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION;
import static android.content.Intent.FLAG_ACTIVITY_NO_USER_ACTION;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static android.content.pm.PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS;
import static android.content.pm.PackageManager.FEATURE_AUTOMOTIVE;
import static android.content.pm.PackageManager.FEATURE_EMBEDDED;
import static android.content.pm.PackageManager.FEATURE_EXPANDED_PICTURE_IN_PICTURE;
import static android.content.pm.PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT;
import static android.content.pm.PackageManager.FEATURE_INPUT_METHODS;
import static android.content.pm.PackageManager.FEATURE_LEANBACK;
import static android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE;
import static android.content.pm.PackageManager.FEATURE_SCREEN_LANDSCAPE;
import static android.content.pm.PackageManager.FEATURE_SCREEN_PORTRAIT;
import static android.content.pm.PackageManager.FEATURE_SECURE_LOCK_SCREEN;
import static android.content.pm.PackageManager.FEATURE_TELEVISION;
import static android.content.pm.PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE;
import static android.content.pm.PackageManager.FEATURE_WATCH;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.content.res.Configuration.ORIENTATION_UNDEFINED;
import static android.os.UserHandle.USER_ALL;
import static android.provider.Settings.Secure.IMMERSIVE_MODE_CONFIRMATIONS;
import static android.server.wm.ActivityLauncher.KEY_ACTIVITY_TYPE;
import static android.server.wm.ActivityLauncher.KEY_DISPLAY_ID;
import static android.server.wm.ActivityLauncher.KEY_INTENT_EXTRAS;
import static android.server.wm.ActivityLauncher.KEY_INTENT_FLAGS;
import static android.server.wm.ActivityLauncher.KEY_LAUNCH_ACTIVITY;
import static android.server.wm.ActivityLauncher.KEY_LAUNCH_TASK_BEHIND;
import static android.server.wm.ActivityLauncher.KEY_LAUNCH_TO_SIDE;
import static android.server.wm.ActivityLauncher.KEY_MULTIPLE_INSTANCES;
import static android.server.wm.ActivityLauncher.KEY_MULTIPLE_TASK;
import static android.server.wm.ActivityLauncher.KEY_NEW_TASK;
import static android.server.wm.ActivityLauncher.KEY_RANDOM_DATA;
import static android.server.wm.ActivityLauncher.KEY_REORDER_TO_FRONT;
import static android.server.wm.ActivityLauncher.KEY_SUPPRESS_EXCEPTIONS;
import static android.server.wm.ActivityLauncher.KEY_TARGET_COMPONENT;
import static android.server.wm.ActivityLauncher.KEY_TASK_DISPLAY_AREA_FEATURE_ID;
import static android.server.wm.ActivityLauncher.KEY_USE_APPLICATION_CONTEXT;
import static android.server.wm.ActivityLauncher.KEY_WINDOWING_MODE;
import static android.server.wm.ActivityLauncher.launchActivityFromExtras;
import static android.server.wm.CommandSession.KEY_FORWARD;
import static android.server.wm.ComponentNameUtils.getActivityName;
import static android.server.wm.ComponentNameUtils.getLogTag;
import static android.server.wm.ShellCommandHelper.executeShellCommand;
import static android.server.wm.ShellCommandHelper.executeShellCommandAndGetStdout;
import static android.server.wm.StateLogger.log;
import static android.server.wm.StateLogger.logE;
import static android.server.wm.UiDeviceUtils.pressSleepButton;
import static android.server.wm.UiDeviceUtils.pressUnlockButton;
import static android.server.wm.UiDeviceUtils.pressWakeupButton;
import static android.server.wm.WindowManagerState.STATE_PAUSED;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.server.wm.WindowManagerState.STATE_STOPPED;
import static android.server.wm.app.Components.BROADCAST_RECEIVER_ACTIVITY;
import static android.server.wm.app.Components.BroadcastReceiverActivity.ACTION_TRIGGER_BROADCAST;
import static android.server.wm.app.Components.BroadcastReceiverActivity.EXTRA_BROADCAST_ORIENTATION;
import static android.server.wm.app.Components.BroadcastReceiverActivity.EXTRA_CUTOUT_EXISTS;
import static android.server.wm.app.Components.BroadcastReceiverActivity.EXTRA_DISMISS_KEYGUARD;
import static android.server.wm.app.Components.BroadcastReceiverActivity.EXTRA_DISMISS_KEYGUARD_METHOD;
import static android.server.wm.app.Components.BroadcastReceiverActivity.EXTRA_FINISH_BROADCAST;
import static android.server.wm.app.Components.BroadcastReceiverActivity.EXTRA_MOVE_BROADCAST_TO_BACK;
import static android.server.wm.app.Components.LAUNCHING_ACTIVITY;
import static android.server.wm.app.Components.LaunchingActivity.KEY_FINISH_BEFORE_LAUNCH;
import static android.server.wm.app.Components.PipActivity.ACTION_CHANGE_ASPECT_RATIO;
import static android.server.wm.app.Components.PipActivity.ACTION_ENTER_PIP;
import static android.server.wm.app.Components.PipActivity.ACTION_ENTER_PIP_AND_WAIT_FOR_UI_STATE;
import static android.server.wm.app.Components.PipActivity.ACTION_EXPAND_PIP;
import static android.server.wm.app.Components.PipActivity.ACTION_SET_REQUESTED_ORIENTATION;
import static android.server.wm.app.Components.PipActivity.ACTION_UPDATE_PIP_STATE;
import static android.server.wm.app.Components.PipActivity.EXTRA_PIP_ORIENTATION;
import static android.server.wm.app.Components.PipActivity.EXTRA_SET_ASPECT_RATIO_DENOMINATOR;
import static android.server.wm.app.Components.PipActivity.EXTRA_SET_ASPECT_RATIO_NUMERATOR;
import static android.server.wm.app.Components.PipActivity.EXTRA_SET_ASPECT_RATIO_WITH_DELAY_DENOMINATOR;
import static android.server.wm.app.Components.PipActivity.EXTRA_SET_ASPECT_RATIO_WITH_DELAY_NUMERATOR;
import static android.server.wm.app.Components.PipActivity.EXTRA_SET_PIP_CALLBACK;
import static android.server.wm.app.Components.PipActivity.EXTRA_SET_PIP_STASHED;
import static android.server.wm.app.Components.TEST_ACTIVITY;
import static android.server.wm.second.Components.SECOND_ACTIVITY;
import static android.server.wm.third.Components.THIRD_ACTIVITY;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.INVALID_DISPLAY;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_90;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_BASE_APPLICATION;
import static android.window.DisplayAreaOrganizer.FEATURE_UNDEFINED;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import static java.lang.Integer.toHexString;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.app.WallpaperManager;
import android.app.WindowConfiguration;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.display.AmbientDisplayConfiguration;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Process;
import android.os.RemoteCallback;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings;
import android.server.wm.CommandSession.ActivityCallback;
import android.server.wm.CommandSession.ActivitySession;
import android.server.wm.CommandSession.ActivitySessionClient;
import android.server.wm.CommandSession.ConfigInfo;
import android.server.wm.CommandSession.LaunchInjector;
import android.server.wm.CommandSession.LaunchProxy;
import android.server.wm.CommandSession.SizeInfo;
import android.server.wm.TestJournalProvider.TestJournalContainer;
import android.server.wm.WindowManagerState.Task;
import android.server.wm.WindowManagerState.WindowState;
import android.server.wm.settings.SettingsSession;
import android.util.DisplayMetrics;
import android.util.EventLog;
import android.util.EventLog.Event;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import com.android.compatibility.common.util.AppOpsUtils;
import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.GestureNavSwitchHelper;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ErrorCollector;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class ActivityManagerTestBase {
    private static final String TAG = ActivityManagerTestBase.class.getSimpleName();
    private static final boolean PRETEND_DEVICE_SUPPORTS_PIP = false;
    private static final boolean PRETEND_DEVICE_SUPPORTS_FREEFORM = false;
    private static final String LOG_SEPARATOR = "LOG_SEPARATOR";
    // Use one of the test tags as a separator
    private static final int EVENT_LOG_SEPARATOR_TAG = 42;

    protected static final int[] ALL_ACTIVITY_TYPE_BUT_HOME = {
            ACTIVITY_TYPE_STANDARD, ACTIVITY_TYPE_ASSISTANT, ACTIVITY_TYPE_RECENTS,
            ACTIVITY_TYPE_UNDEFINED
    };

    private static final String TEST_PACKAGE = TEST_ACTIVITY.getPackageName();
    private static final String SECOND_TEST_PACKAGE = SECOND_ACTIVITY.getPackageName();
    private static final String THIRD_TEST_PACKAGE = THIRD_ACTIVITY.getPackageName();
    private static final List<String> TEST_PACKAGES;

    static {
        final List<String> testPackages = new ArrayList<>();
        testPackages.add(TEST_PACKAGE);
        testPackages.add(SECOND_TEST_PACKAGE);
        testPackages.add(THIRD_TEST_PACKAGE);
        testPackages.add("android.server.wm.cts");
        testPackages.add("android.server.wm.jetpack");
        testPackages.add("android.server.wm.jetpack.second");
        TEST_PACKAGES = Collections.unmodifiableList(testPackages);
    }

    protected static final String AM_START_HOME_ACTIVITY_COMMAND =
            "am start -a android.intent.action.MAIN -c android.intent.category.HOME";

    protected static final String MSG_NO_MOCK_IME =
            "MockIme cannot be used for devices that do not support installable IMEs";

    private static final String AM_BROADCAST_CLOSE_SYSTEM_DIALOGS =
            "am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS --user " + USER_ALL;

    protected static final String LOCK_CREDENTIAL = "1234";

    private static final int UI_MODE_TYPE_MASK = 0x0f;
    private static final int UI_MODE_TYPE_VR_HEADSET = 0x07;

    public static final boolean ENABLE_SHELL_TRANSITIONS =
            SystemProperties.getBoolean("persist.wm.debug.shell_transit", true);

    private static Boolean sHasHomeScreen = null;
    private static Boolean sSupportsSystemDecorsOnSecondaryDisplays = null;
    private static Boolean sIsAssistantOnTop = null;
    private static Boolean sIsTablet = null;
    private static Boolean sDismissDreamOnActivityStart = null;
    private static GestureNavSwitchHelper sGestureNavSwitchHelper = null;
    private static boolean sIllegalTaskStateFound;

    protected static final int INVALID_DEVICE_ROTATION = -1;

    protected final Instrumentation mInstrumentation = getInstrumentation();
    protected final Context mContext = getInstrumentation().getContext();
    protected final ActivityManager mAm = mContext.getSystemService(ActivityManager.class);
    protected final ActivityTaskManager mAtm = mContext.getSystemService(ActivityTaskManager.class);
    protected final DisplayManager mDm = mContext.getSystemService(DisplayManager.class);
    protected final WindowManager mWm = mContext.getSystemService(WindowManager.class);
    protected final KeyguardManager mKm = mContext.getSystemService(KeyguardManager.class);

    /** The tracker to manage objects (especially {@link AutoCloseable}) in a test method. */
    protected final ObjectTracker mObjectTracker = new ObjectTracker();

    /** The last rule to handle all errors. */
    private final ErrorCollector mPostAssertionRule = new PostAssertionRule();

    /** The necessary procedures of set up and tear down. */
    @Rule
    public final TestRule mBaseRule = RuleChain.outerRule(mPostAssertionRule)
            .around(new WrapperRule(null /* before */, this::tearDownBase));

    /**
     * Whether to wait for the rotation to be stable state after testing. It can be set if the
     * display rotation may be changed by test.
     */
    protected boolean mWaitForRotationOnTearDown;

    /** Indicate to wait for all non-home activities to be destroyed when test finished. */
    protected boolean mShouldWaitForAllNonHomeActivitiesToDestroyed = false;

    /**
     * @return the am command to start the given activity with the following extra key/value pairs.
     * {@param extras} a list of {@link CliIntentExtra} representing a generic intent extra
     */
    // TODO: Make this more generic, for instance accepting flags or extras of other types.
    protected static String getAmStartCmd(final ComponentName activityName,
            final CliIntentExtra... extras) {
        return getAmStartCmdInternal(getActivityName(activityName), extras);
    }

    private static String getAmStartCmdInternal(final String activityName,
            final CliIntentExtra... extras) {
        return appendKeyValuePairs(
                new StringBuilder("am start --user ").append(Process.myUserHandle().getIdentifier())
                        .append(" -n ").append(activityName), extras);
    }

    private static String appendKeyValuePairs(
            final StringBuilder cmd, final CliIntentExtra... extras) {
        for (int i = 0; i < extras.length; i++) {
            extras[i].appendTo(cmd);
        }
        return cmd.toString();
    }

    protected static String getAmStartCmd(final ComponentName activityName, final int displayId,
            final CliIntentExtra... extras) {
        return getAmStartCmdInternal(getActivityName(activityName), displayId, extras);
    }

    private static String getAmStartCmdInternal(final String activityName, final int displayId,
            final CliIntentExtra... extras) {
        return appendKeyValuePairs(
                new StringBuilder("am start -n ")
                        .append(activityName)
                        .append(" -f 0x")
                        .append(toHexString(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK))
                        .append(" --display ")
                        .append(displayId),
                extras);
    }

    protected static String getAmStartCmdInNewTask(final ComponentName activityName) {
        return "am start -n " + getActivityName(activityName) + " -f 0x18000000";
    }

    protected static String getAmStartCmdWithData(final ComponentName activityName, String data) {
        return "am start -n " + getActivityName(activityName) + " -d " + data;
    }

    protected static String getAmStartCmdWithNoAnimation(final ComponentName activityName,
            final CliIntentExtra... extras) {
        return appendKeyValuePairs(
                new StringBuilder("am start -n ")
                        .append(getActivityName(activityName))
                        .append(" -f 0x")
                        .append(toHexString(FLAG_ACTIVITY_NO_ANIMATION)),
                extras);
    }

    protected static String getAmStartCmdWithDismissKeyguardIfInsecure(
            final ComponentName activityName) {
        return "am start --dismiss-keyguard-if-insecure -n " + getActivityName(activityName);
    }

    protected static String getAmStartCmdWithNoUserAction(final ComponentName activityName,
            final CliIntentExtra... extras) {
        return appendKeyValuePairs(
                new StringBuilder("am start -n ")
                        .append(getActivityName(activityName))
                        .append(" -f 0x")
                        .append(toHexString(FLAG_ACTIVITY_NO_USER_ACTION)),
                extras);
    }

    protected static String getAmStartCmdWithWindowingMode(
            final ComponentName activityName, int windowingMode) {
        return getAmStartCmdInNewTask(activityName) + " --windowingMode " + windowingMode;
    }

    protected WindowManagerStateHelper mWmState = new WindowManagerStateHelper();
    protected TouchHelper mTouchHelper = new TouchHelper(mInstrumentation, mWmState);
    // Initialized in setUp to execute with proper permission, such as MANAGE_ACTIVITY_TASKS
    public TestTaskOrganizer mTaskOrganizer;

    public WindowManagerStateHelper getWmState() {
        return mWmState;
    }

    protected BroadcastActionTrigger mBroadcastActionTrigger = new BroadcastActionTrigger();

    /** Runs a runnable with shell permissions. These can be nested. */
    protected void runWithShellPermission(Runnable runnable) {
        NestedShellPermission.run(runnable);
    }

    /**
     * Returns true if the activity is shown before timeout.
     */
    protected boolean waitForActivityFocused(int timeoutMs, ComponentName componentName) {
        waitForActivityResumed(timeoutMs, componentName);
        return getActivityName(componentName).equals(mWmState.getFocusedActivity());
    }

    protected void waitForActivityResumed(int timeoutMs, ComponentName componentName) {
        long endTime = System.currentTimeMillis() + timeoutMs;
        while (endTime > System.currentTimeMillis()) {
            mWmState.computeState();
            if (mWmState.hasActivityState(componentName, STATE_RESUMED)) {
                SystemClock.sleep(200);
                mWmState.computeState();
                break;
            }
            SystemClock.sleep(200);
            mWmState.computeState();
        }
    }

    /**
     * Helper class to process test actions by broadcast.
     */
    protected class BroadcastActionTrigger {

        private Intent createIntentWithAction(String broadcastAction) {
            return new Intent(broadcastAction)
                    .setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        }

        public void doAction(String broadcastAction) {
            mContext.sendBroadcast(createIntentWithAction(broadcastAction));
        }

        public void doActionWithRemoteCallback(
                String broadcastAction, String callbackName, RemoteCallback callback) {
            try {
                // We need also a RemoteCallback to ensure the callback passed in is properly set
                // in the Activity before moving forward.
                final CompletableFuture<Boolean> future = new CompletableFuture<>();
                final RemoteCallback setCallback = new RemoteCallback(
                        (Bundle result) -> future.complete(true));
                mContext.sendBroadcast(createIntentWithAction(broadcastAction)
                        .putExtra(callbackName, callback)
                        .putExtra(EXTRA_SET_PIP_CALLBACK, setCallback));
                assertTrue(future.get(5000, TimeUnit.MILLISECONDS));
            } catch (Exception e) {
                logE("doActionWithRemoteCallback failed", e);
            }
        }

        public void finishBroadcastReceiverActivity() {
            mContext.sendBroadcast(createIntentWithAction(ACTION_TRIGGER_BROADCAST)
                    .putExtra(EXTRA_FINISH_BROADCAST, true));
        }

        public void launchActivityNewTask(String launchComponent) {
            mContext.sendBroadcast(createIntentWithAction(ACTION_TRIGGER_BROADCAST)
                    .putExtra(KEY_LAUNCH_ACTIVITY, true)
                    .putExtra(KEY_NEW_TASK, true)
                    .putExtra(KEY_TARGET_COMPONENT, launchComponent));
        }

        public void moveTopTaskToBack() {
            mContext.sendBroadcast(createIntentWithAction(ACTION_TRIGGER_BROADCAST)
                    .putExtra(EXTRA_MOVE_BROADCAST_TO_BACK, true));
        }

        public void requestOrientation(int orientation) {
            mContext.sendBroadcast(createIntentWithAction(ACTION_TRIGGER_BROADCAST)
                    .putExtra(EXTRA_BROADCAST_ORIENTATION, orientation));
        }

        public void dismissKeyguardByFlag() {
            mContext.sendBroadcast(createIntentWithAction(ACTION_TRIGGER_BROADCAST)
                    .putExtra(EXTRA_DISMISS_KEYGUARD, true));
        }

        public void dismissKeyguardByMethod() {
            mContext.sendBroadcast(createIntentWithAction(ACTION_TRIGGER_BROADCAST)
                    .putExtra(EXTRA_DISMISS_KEYGUARD_METHOD, true));
        }

        public void enterPipAndWait() {
            try {
                final CompletableFuture<Boolean> future = new CompletableFuture<>();
                final RemoteCallback remoteCallback = new RemoteCallback(
                        (Bundle result) -> future.complete(true));
                mContext.sendBroadcast(createIntentWithAction(ACTION_ENTER_PIP)
                        .putExtra(EXTRA_SET_PIP_CALLBACK, remoteCallback));
                assertTrue(future.get(5000, TimeUnit.MILLISECONDS));
            } catch (Exception e) {
                logE("enterPipAndWait failed", e);
            }
        }

        public void expandPip() {
            mContext.sendBroadcast(createIntentWithAction(ACTION_EXPAND_PIP));
        }

        public void expandPipWithAspectRatio(String extraNum, String extraDenom) {
            mContext.sendBroadcast(createIntentWithAction(ACTION_EXPAND_PIP)
                    .putExtra(EXTRA_SET_ASPECT_RATIO_WITH_DELAY_NUMERATOR, extraNum)
                    .putExtra(EXTRA_SET_ASPECT_RATIO_WITH_DELAY_DENOMINATOR, extraDenom));
        }

        public void sendPipStateUpdate(RemoteCallback callback, boolean stashed) {
            mContext.sendBroadcast(createIntentWithAction(ACTION_UPDATE_PIP_STATE)
                    .putExtra(EXTRA_SET_PIP_CALLBACK, callback)
                    .putExtra(EXTRA_SET_PIP_STASHED, stashed));
        }

        public void enterPipAndWaitForPipUiStateChange(RemoteCallback callback) {
            mContext.sendBroadcast(createIntentWithAction(ACTION_ENTER_PIP_AND_WAIT_FOR_UI_STATE)
                    .putExtra(EXTRA_SET_PIP_CALLBACK, callback));
        }

        public void requestOrientationForPip(int orientation) {
            mContext.sendBroadcast(createIntentWithAction(ACTION_SET_REQUESTED_ORIENTATION)
                    .putExtra(EXTRA_PIP_ORIENTATION, String.valueOf(orientation)));
        }

        public void changeAspectRatio(int numerator, int denominator) {
            mContext.sendBroadcast(createIntentWithAction(ACTION_CHANGE_ASPECT_RATIO)
                    .putExtra(EXTRA_SET_ASPECT_RATIO_NUMERATOR, Integer.toString(numerator))
                    .putExtra(EXTRA_SET_ASPECT_RATIO_DENOMINATOR, Integer.toString(denominator)));
        }
    }

    /**
     * Helper class to launch / close test activity by instrumentation way.
     */
    protected class TestActivitySession<T extends Activity> implements AutoCloseable {
        private T mTestActivity;
        boolean mFinishAfterClose;
        private static final int ACTIVITY_LAUNCH_TIMEOUT = 10000;
        private static final int WAIT_SLICE = 50;

        public void setFinishAfterClose(boolean value) {
            mFinishAfterClose = value;
        }

        /**
         * Launches an {@link Activity} on a target display synchronously.
         * @param activityClass The {@link Activity} class to be launched
         * @param displayId ID of the target display
         */
        public void launchTestActivityOnDisplaySync(Class<T> activityClass, int displayId) {
            launchTestActivityOnDisplaySync(activityClass, displayId, WINDOWING_MODE_UNDEFINED);
        }

        /**
         * Launches an {@link Activity} on a target display synchronously.
         *
         * @param activityClass The {@link Activity} class to be launched
         * @param displayId ID of the target display
         * @param windowingMode Windowing mode at launch
         */
        public void launchTestActivityOnDisplaySync(
                Class<T> activityClass, int displayId, int windowingMode) {
            final Intent intent = new Intent(mContext, activityClass)
                    .addFlags(FLAG_ACTIVITY_NEW_TASK);
            final String className = intent.getComponent().getClassName();
            launchTestActivityOnDisplaySync(className, intent, displayId, windowingMode);
        }

        /**
         * Launches an {@link Activity} synchronously on a target display. The class name needs to
         * be provided either implicitly through the {@link Intent} or explicitly as a parameter
         *
         * @param className Optional class name of expected activity
         * @param intent Intent to launch an activity
         * @param displayId ID for the target display
         */
        public void launchTestActivityOnDisplaySync(
                @Nullable String className, Intent intent, int displayId) {
            launchTestActivityOnDisplaySync(className, intent, displayId, WINDOWING_MODE_UNDEFINED);
        }

        /**
         * Launches an {@link Activity} synchronously on a target display. The class name needs to
         * be provided either implicitly through the {@link Intent} or explicitly as a parameter
         *
         * @param className Optional class name of expected activity
         * @param intent Intent to launch an activity
         * @param displayId ID for the target display
         * @param windowingMode Windowing mode at launch
         */
        public void launchTestActivityOnDisplaySync(
                @Nullable String className, Intent intent, int displayId, int windowingMode) {
            runWithShellPermission(
                    () -> {
                        mTestActivity =
                                launchActivityOnDisplay(
                                        className, intent, displayId, windowingMode);
                        // Check activity is launched and resumed.
                        final ComponentName testActivityName = mTestActivity.getComponentName();
                        waitAndAssertTopResumedActivity(
                                testActivityName, displayId, "Activity must be resumed");
                    });
        }

        /**
         * Launches an {@link Activity} on a target display asynchronously.
         * @param activityClass The {@link Activity} class to be launched
         * @param displayId ID of the target display
         */
        public void launchTestActivityOnDisplay(Class<T> activityClass, int displayId) {
            final Intent intent = new Intent(mContext, activityClass)
                    .addFlags(FLAG_ACTIVITY_NEW_TASK);
            final String className = intent.getComponent().getClassName();
            runWithShellPermission(
                    () -> {
                        mTestActivity =
                                launchActivityOnDisplay(
                                        className, intent, displayId, WINDOWING_MODE_UNDEFINED);
                        assertNotNull(mTestActivity);
                    });
        }

        /**
         * Launches an {@link Activity} on a target display. In order to return the correct activity
         * the class name or an explicit {@link Intent} must be provided.
         *
         * @param className Optional class name of expected activity
         * @param intent {@link Intent} to launch an activity
         * @param displayId ID for the target display
         * @param windowingMode Windowing mode at launch
         * @return The {@link Activity} that was launched
         */
        private T launchActivityOnDisplay(
                @Nullable String className, Intent intent, int displayId, int windowingMode) {
            final String localClassName = className != null ? className :
              (intent.getComponent() != null ? intent.getComponent().getClassName() : null);
            if (localClassName == null || localClassName.isEmpty()) {
                fail("Must provide either a class name or an intent with a component");
            }
            final ActivityOptions launchOptions = ActivityOptions.makeBasic();
            launchOptions.setLaunchDisplayId(displayId);
            launchOptions.setLaunchWindowingMode(windowingMode);
            final Bundle bundle = launchOptions.toBundle();
            final ActivityMonitor monitor = mInstrumentation.addMonitor(localClassName, null,
                    false);
            mContext.startActivity(intent.addFlags(FLAG_ACTIVITY_NEW_TASK), bundle);
            // Wait for activity launch with timeout.
            mTestActivity = (T) mInstrumentation.waitForMonitorWithTimeout(monitor,
                    ACTIVITY_LAUNCH_TIMEOUT);
            assertNotNull(mTestActivity);
            return mTestActivity;
        }

        public void finishCurrentActivityNoWait() {
            if (mTestActivity != null) {
                mTestActivity.finishAndRemoveTask();
                mTestActivity = null;
            }
        }

        public void runOnMainSyncAndWait(Runnable runnable) {
            mInstrumentation.runOnMainSync(runnable);
            mInstrumentation.waitForIdleSync();
        }

        public void runOnMainAndAssertWithTimeout(
                @NonNull BooleanSupplier condition, long timeoutMs, String message) {
            final AtomicBoolean result = new AtomicBoolean();
            final long expiredTime = System.currentTimeMillis() + timeoutMs;
            while (!result.get()) {
                if (System.currentTimeMillis() >= expiredTime) {
                    fail(message);
                }
                runOnMainSyncAndWait(() -> {
                    if (condition.getAsBoolean()) {
                        result.set(true);
                    }
                });
                SystemClock.sleep(WAIT_SLICE);
            }
        }

        public T getActivity() {
            return mTestActivity;
        }

        @Override
        public void close() {
            if (mTestActivity != null && mFinishAfterClose) {
                mTestActivity.finishAndRemoveTask();
            }
        }
    }

    @Before
    public void setUp() throws Exception {
        UiDeviceUtils.wakeUpAndUnlock(mContext);
        if (isKeyguardLocked()) {
            unlockUnexpectedLockedKeyguard();
        }

        launchHomeActivityNoWait();
        // TODO(b/242933292): Consider removing all the tasks belonging to android.server.wm
        // instead of removing all and then waiting for allActivitiesResumed.
        removeRootTasksWithActivityTypes(ALL_ACTIVITY_TYPE_BUT_HOME);

        runWithShellPermission(() -> {
            // TaskOrganizer ctor requires MANAGE_ACTIVITY_TASKS permission
            mTaskOrganizer = new TestTaskOrganizer();
            // Clear launch params for all test packages to make sure each test is run in a clean
            // state.
            mAtm.clearLaunchParamsForPackages(TEST_PACKAGES);
        });

        // removeRootTaskWithActivityTypes() removes all the tasks apart from home. In a few cases,
        // the systemUI might have a few tasks that need to be displayed all the time.
        // For such tasks, systemUI might have a restart-logic that restarts those tasks. Those
        // restarts can interfere with the test state. To avoid that, its better to wait for all
        // the activities to come in the resumed state.
        mWmState.waitForWithAmState(WindowManagerState::allActivitiesResumed, "Root Tasks should "
                + "be either empty or resumed");
    }

    /** It always executes after {@link org.junit.After}. */
    private void tearDownBase() {
        mObjectTracker.tearDown(mPostAssertionRule::addError);

        if (mTaskOrganizer != null) {
            mTaskOrganizer.unregisterOrganizerIfNeeded();
        }
        // Synchronous execution of removeRootTasksWithActivityTypes() ensures that all
        // activities but home are cleaned up from the root task at the end of each test. Am force
        // stop shell commands might be asynchronous and could interrupt the task cleanup
        // process if executed first.
        UiDeviceUtils.wakeUpAndUnlock(mContext);
        launchHomeActivityNoWait();
        removeRootTasksWithActivityTypes(ALL_ACTIVITY_TYPE_BUT_HOME);
        stopTestPackage(TEST_PACKAGE);
        stopTestPackage(SECOND_TEST_PACKAGE);
        stopTestPackage(THIRD_TEST_PACKAGE);
        if (mShouldWaitForAllNonHomeActivitiesToDestroyed) {
            mWmState.waitForAllNonHomeActivitiesToDestroyed();
        }

        if (mWaitForRotationOnTearDown) {
            mWmState.waitForDisplayUnfrozen();
        }

        if (ENABLE_SHELL_TRANSITIONS
                && !mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY)) {
            mPostAssertionRule.addError(
                    new IllegalStateException("Shell transition left unfinished!"));
        }
    }

    /** This should only be called if keyguard is still locked unexpectedly. */
    private void unlockUnexpectedLockedKeyguard() {
        logE("Try to recover unexpected locked keyguard");
        // To clear the credential immediately, the screen need to be turned on.
        pressWakeupButton();
        if (supportsSecureLock()) {
            removeLockCredential();
        }
        // Off/on to refresh the keyguard state.
        pressSleepButton();
        pressWakeupButton();
        pressUnlockButton();
    }

    /**
     * After home key is pressed ({@link #pressHomeButton} is called), the later launch may be
     * deferred if the calling uid doesn't have android.permission.STOP_APP_SWITCHES. This method
     * will resume the temporary stopped state, so the launch won't be affected.
     */
    protected void resumeAppSwitches() {
        SystemUtil.runWithShellPermissionIdentity(ActivityManager::resumeAppSwitches);
    }

    protected void startActivityOnDisplay(int displayId, ComponentName component) {
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(displayId);

        mContext.startActivity(new Intent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setComponent(component), options.toBundle());
    }

    protected boolean noHomeScreen() {
        try {
            return mContext.getResources().getBoolean(
                    Resources.getSystem().getIdentifier("config_noHomeScreen", "bool",
                            "android"));
        } catch (Resources.NotFoundException e) {
            // Assume there's a home screen.
            return false;
        }
    }

    private boolean getSupportsSystemDecorsOnSecondaryDisplays() {
        try {
            return mContext.getResources().getBoolean(
                    Resources.getSystem().getIdentifier(
                            "config_supportsSystemDecorsOnSecondaryDisplays", "bool", "android"));
        } catch (Resources.NotFoundException e) {
            // Assume this device support system decorations.
            return true;
        }
    }

    protected Intent createHomeIntent(String category) {
        int resId = Resources.getSystem().getIdentifier(
                "config_secondaryHomePackage", "string", "android");
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(category);
        intent.setPackage(mContext.getResources().getString(resId));
        return intent;
    }

    protected ComponentName getDefaultSecondaryHomeComponent() {
        assumeTrue(supportsMultiDisplay());
        final Intent intent = createHomeIntent(Intent.CATEGORY_SECONDARY_HOME);
        final ResolveInfo resolveInfo =
                mContext.getPackageManager().resolveActivity(intent, MATCH_DEFAULT_ONLY);
        assertNotNull("Should have default secondary home activity", resolveInfo);

        return new ComponentName(resolveInfo.activityInfo.packageName,
                resolveInfo.activityInfo.name);
    }

    /**
     * Insert an input event (ACTION_DOWN -> ACTION_CANCEL) to ensures the display to be focused
     * without triggering potential clicked to impact the test environment.
     * (e.g: Keyguard credential activated unexpectedly.)
     *
     * @param displayId the display ID to gain focused by inject swipe action
     */
    protected void touchAndCancelOnDisplayCenterSync(int displayId) {
        mTouchHelper.touchAndCancelOnDisplayCenterSync(displayId);
    }

    protected void tapOnDisplaySync(int x, int y, int displayId) {
        mTouchHelper.tapOnDisplaySync(x, y, displayId);
    }

    private void tapOnDisplay(int x, int y, int displayId, boolean sync) {
        mTouchHelper.tapOnDisplay(x, y, displayId, sync);
    }

    protected void tapOnCenter(Rect bounds, int displayId) {
        mTouchHelper.tapOnCenter(bounds, displayId);
    }

    protected void tapOnViewCenter(View view) {
        mTouchHelper.tapOnViewCenter(view);
    }

    protected void tapOnTaskCenter(Task task) {
        mTouchHelper.tapOnTaskCenter(task);
    }

    protected void tapOnDisplayCenter(int displayId) {
        mTouchHelper.tapOnDisplayCenter(displayId);
    }

    protected void tapOnDisplayCenterAsync(int displayId) {
        mTouchHelper.tapOnDisplayCenterAsync(displayId);
    }

    public static void injectKey(int keyCode, boolean longPress, boolean sync) {
        TouchHelper.injectKey(keyCode, longPress, sync);
    }

    protected void removeRootTasksWithActivityTypes(int... activityTypes) {
        runWithShellPermission(() -> mAtm.removeRootTasksWithActivityTypes(activityTypes));
        waitForIdle();
    }

    protected void removeRootTasksInWindowingModes(int... windowingModes) {
        runWithShellPermission(() -> mAtm.removeRootTasksInWindowingModes(windowingModes));
        waitForIdle();
    }

    protected void removeRootTask(int taskId) {
        runWithShellPermission(() -> mAtm.removeTask(taskId));
        waitForIdle();
    }

    protected Bitmap takeScreenshot() {
        return mInstrumentation.getUiAutomation().takeScreenshot();
    }

    /**
     * Do a back gesture and trigger a back event from it.
     * Attempt to simulate human behavior, so don't wait for animations.
     */
    protected void triggerBackEventByGesture(int displayId) {
        mTouchHelper.triggerBackEventByGesture(
                displayId, true /* sync */, false /* waitForAnimations */);
    }

    protected Bitmap takeScreenshotForBounds(Rect rect) {
        Bitmap fullBitmap = takeScreenshot();
        return Bitmap.createBitmap(fullBitmap, rect.left, rect.top,
                rect.width(), rect.height());
    }

    protected void launchActivity(final ComponentName activityName,
            final CliIntentExtra... extras) {
        launchActivityNoWait(activityName, extras);
        mWmState.waitForValidState(activityName);
    }

    protected void launchActivityNoWait(final ComponentName activityName,
            final CliIntentExtra... extras) {
        executeShellCommand(getAmStartCmd(activityName, extras));
    }

    protected void launchActivityInNewTask(final ComponentName activityName) {
        executeShellCommand(getAmStartCmdInNewTask(activityName));
        mWmState.waitForValidState(activityName);
    }

    protected void launchActivityWithData(final ComponentName activityName, String data) {
        executeShellCommand(getAmStartCmdWithData(activityName, data));
        mWmState.waitForValidState(activityName);
    }

    protected void launchActivityWithNoAnimation(final ComponentName activityName,
            final CliIntentExtra... extras) {
        executeShellCommand(getAmStartCmdWithNoAnimation(activityName, extras));
        mWmState.waitForValidState(activityName);
    }

    protected void launchActivityWithDismissKeyguardIfInsecure(
            final ComponentName activityName) {
        executeShellCommand(getAmStartCmdWithDismissKeyguardIfInsecure(activityName));
        mWmState.waitForValidState(activityName);
    }

    protected void launchActivityWithNoUserAction(final ComponentName activityName,
            final CliIntentExtra... extras) {
        executeShellCommand(getAmStartCmdWithNoUserAction(activityName, extras));
        mWmState.waitForValidState(activityName);
    }

    protected void launchActivityInFullscreen(final ComponentName activityName) {
        executeShellCommand(
                getAmStartCmdWithWindowingMode(activityName, WINDOWING_MODE_FULLSCREEN));
        mWmState.waitForValidState(activityName);
    }

    protected static void waitForIdle() {
        getInstrumentation().waitForIdleSync();
    }

    public static void waitForOrFail(String message, BooleanSupplier condition) {
        Condition.waitFor(new Condition<>(message, condition)
                .setRetryIntervalMs(500)
                .setRetryLimit(20)
                .setOnFailure(unusedResult -> fail("FAILED because unsatisfied: " + message)));
    }

    /** Returns the root task that contains the provided leaf task id. */
    protected Task getRootTaskForLeafTaskId(int taskId) {
        mWmState.computeState();
        final List<Task> rootTasks = mWmState.getRootTasks();
        for (Task rootTask : rootTasks) {
            if (rootTask.getTask(taskId) != null) {
                return rootTask;
            }
        }
        return null;
    }

    protected Task getRootTask(int taskId) {
        mWmState.computeState();
        final List<Task> rootTasks = mWmState.getRootTasks();
        for (Task rootTask : rootTasks) {
            if (rootTask.getRootTaskId() == taskId) {
                return rootTask;
            }
        }
        return null;
    }

    protected int getDefaultWindowingModeByActivity(ComponentName activity) {
        return mWmState.getTaskDisplayArea(activity).getWindowingMode();
    }

    public static void closeSystemDialogs() {
        executeShellCommand(AM_BROADCAST_CLOSE_SYSTEM_DIALOGS);
    }

    /**
     * Launches the home activity directly. If there is no specific reason to simulate a home key
     * (which will trigger stop-app-switches), it is the recommended method to go home.
     */
    public static void launchHomeActivityNoWait() {
        // dismiss all system dialogs before launch home.
        closeSystemDialogs();
        executeShellCommand(AM_START_HOME_ACTIVITY_COMMAND);
    }

    protected static void launchHomeActivityNoWaitExpectFailure() {
        closeSystemDialogs();
        try {
            executeShellCommand(AM_START_HOME_ACTIVITY_COMMAND);
        } catch (AssertionError e) {
            if (e.getMessage().contains("Error: Activity not started")) {
                // expected
                return;
            }
            throw new AssertionError("Expected activity start to fail, but got", e);
        }
        fail("Expected home activity launch to fail but didn't.");
    }

    /** Launches the home activity directly with waiting for it to be visible. */
    protected void launchHomeActivity() {
        launchHomeActivityNoWait();
        mWmState.waitForHomeActivityVisible();
    }

    protected void launchActivityNoWait(ComponentName activityName, int windowingMode,
            final CliIntentExtra... extras) {
        executeShellCommand(getAmStartCmd(activityName, extras)
                + " --windowingMode " + windowingMode);
    }

    protected void launchActivity(ComponentName activityName, int windowingMode,
            final CliIntentExtra... keyValuePairs) {
        launchActivityNoWait(activityName, windowingMode, keyValuePairs);
        mWmState.waitForValidState(new WaitForValidActivityState.Builder(activityName)
                .setWindowingMode(windowingMode)
                .build());
    }

    protected void launchActivityOnDisplay(ComponentName activityName, int windowingMode,
            int displayId, final CliIntentExtra... extras) {
        executeShellCommand(getAmStartCmd(activityName, displayId, extras)
                + " --windowingMode " + windowingMode);
        mWmState.waitForValidState(new WaitForValidActivityState.Builder(activityName)
                .setWindowingMode(windowingMode)
                .build());
    }

    protected void launchActivityOnTaskDisplayArea(ComponentName activityName, int windowingMode,
            int launchTaskDisplayAreaFeatureId, final CliIntentExtra... extras) {
        executeShellCommand(getAmStartCmd(activityName, extras)
                + " --task-display-area-feature-id " + launchTaskDisplayAreaFeatureId
                + " --windowingMode " + windowingMode);
        mWmState.waitForValidState(new WaitForValidActivityState.Builder(activityName)
                .setWindowingMode(windowingMode)
                .build());
    }

    protected void launchActivityOnTaskDisplayArea(ComponentName activityName, int windowingMode,
            int launchTaskDisplayAreaFeatureId, int displayId, final CliIntentExtra... extras) {
        executeShellCommand(getAmStartCmd(activityName, displayId, extras)
                + " --task-display-area-feature-id " + launchTaskDisplayAreaFeatureId
                + " --windowingMode " + windowingMode);
        mWmState.waitForValidState(new WaitForValidActivityState.Builder(activityName)
                .setWindowingMode(windowingMode)
                .build());
    }

    protected void launchActivityOnDisplay(ComponentName activityName, int displayId,
            CliIntentExtra... extras) {
        launchActivityOnDisplayNoWait(activityName, displayId, extras);
        mWmState.waitForValidState(activityName);
    }

    protected void launchActivityOnDisplayNoWait(ComponentName activityName, int displayId,
            CliIntentExtra... extras) {
        executeShellCommand(getAmStartCmd(activityName, displayId, extras));
    }

    protected void launchActivityInPrimarySplit(ComponentName activityName) {
        runWithShellPermission(() -> {
            launchActivity(activityName);
            final int taskId = mWmState.getTaskByActivity(activityName).mTaskId;
            mTaskOrganizer.putTaskInSplitPrimary(taskId);
            mWmState.waitForValidState(activityName);
        });
    }

    protected void launchActivityInSecondarySplit(ComponentName activityName) {
        runWithShellPermission(() -> {
            launchActivity(activityName);
            final int taskId = mWmState.getTaskByActivity(activityName).mTaskId;
            mTaskOrganizer.putTaskInSplitSecondary(taskId);
            mWmState.waitForValidState(activityName);
        });
    }

    protected void putActivityInPrimarySplit(ComponentName activityName) {
        final int taskId = mWmState.getTaskByActivity(activityName).mTaskId;
        mTaskOrganizer.putTaskInSplitPrimary(taskId);
        mWmState.waitForValidState(activityName);
    }

    protected void putActivityInSecondarySplit(ComponentName activityName) {
        final int taskId = mWmState.getTaskByActivity(activityName).mTaskId;
        mTaskOrganizer.putTaskInSplitSecondary(taskId);
        mWmState.waitForValidState(activityName);
    }

    /**
     * Launches {@param primaryActivity} into split-screen primary windowing mode
     * and {@param secondaryActivity} to the side in split-screen secondary windowing mode.
     */
    protected void launchActivitiesInSplitScreen(LaunchActivityBuilder primaryActivity,
            LaunchActivityBuilder secondaryActivity) {
        // Launch split-screen primary.
        primaryActivity
                .setUseInstrumentation()
                .setWaitForLaunched(true)
                .execute();

        final int primaryTaskId = mWmState.getTaskByActivity(
                primaryActivity.mTargetActivity).mTaskId;
        mTaskOrganizer.putTaskInSplitPrimary(primaryTaskId);

        // Launch split-screen secondary
        secondaryActivity
                .setUseInstrumentation()
                .setWaitForLaunched(true)
                .setNewTask(true)
                .setMultipleTask(true)
                .execute();

        final int secondaryTaskId = mWmState.getTaskByActivity(
                secondaryActivity.mTargetActivity).mTaskId;
        mTaskOrganizer.putTaskInSplitSecondary(secondaryTaskId);
        mWmState.computeState(primaryActivity.getTargetActivity(),
                secondaryActivity.getTargetActivity());
        log("launchActivitiesInSplitScreen(), primaryTaskId=" + primaryTaskId +
                ", secondaryTaskId=" + secondaryTaskId);
    }

    /**
     * Move the task of {@param primaryActivity} into split-screen primary and the task of
     * {@param secondaryActivity} to the side in split-screen secondary.
     */
    protected void moveActivitiesToSplitScreen(ComponentName primaryActivity,
            ComponentName secondaryActivity) {
        final int primaryTaskId = mWmState.getTaskByActivity(primaryActivity).mTaskId;
        mTaskOrganizer.putTaskInSplitPrimary(primaryTaskId);

        final int secondaryTaskId = mWmState.getTaskByActivity(secondaryActivity).mTaskId;
        mTaskOrganizer.putTaskInSplitSecondary(secondaryTaskId);

        mWmState.computeState(primaryActivity, secondaryActivity);
        log("moveActivitiesToSplitScreen(), primaryTaskId=" + primaryTaskId +
                ", secondaryTaskId=" + secondaryTaskId);
    }

    protected void dismissSplitScreen(boolean primaryOnTop) {
        if (mTaskOrganizer != null) {
            mTaskOrganizer.dismissSplitScreen(primaryOnTop);
        }
    }

    /**
     * Move activity to root task or on top of the given root task when the root task is also a leaf
     * task.
     */
    protected void moveActivityToRootTaskOrOnTop(ComponentName activityName, int rootTaskId) {
        moveActivityToRootTaskOrOnTop(activityName, rootTaskId, FEATURE_UNDEFINED);
    }

    protected void moveActivityToRootTaskOrOnTop(ComponentName activityName, int rootTaskId,
                                                 int taskDisplayAreaFeatureId) {
        mWmState.computeState(activityName);
        Task rootTask = getRootTask(rootTaskId);
        if (rootTask.getActivities().size() != 0) {
            // If the root task is a 1-level task, start the activity on top of given task.
            getLaunchActivityBuilder()
                    .setDisplayId(rootTask.mDisplayId)
                    .setWindowingMode(rootTask.getWindowingMode())
                    .setActivityType(rootTask.getActivityType())
                    .setLaunchTaskDisplayAreaFeatureId(taskDisplayAreaFeatureId)
                    .setTargetActivity(activityName)
                    .allowMultipleInstances(false)
                    .setUseInstrumentation()
                    .execute();
        } else {
            final int taskId = mWmState.getTaskByActivity(activityName).mTaskId;
            runWithShellPermission(() -> mAtm.moveTaskToRootTask(taskId, rootTaskId, true));
        }
        mWmState.waitForValidState(new WaitForValidActivityState.Builder(activityName)
                .setRootTaskId(rootTaskId)
                .build());
    }

    protected void resizeActivityTask(
            ComponentName activityName, int left, int top, int right, int bottom) {
        mWmState.computeState(activityName);
        final int taskId = mWmState.getTaskByActivity(activityName).mTaskId;
        runWithShellPermission(() -> mAtm.resizeTask(taskId, new Rect(left, top, right, bottom)));
    }

    protected boolean supportsVrMode() {
        return hasDeviceFeature(FEATURE_VR_MODE_HIGH_PERFORMANCE);
    }

    protected boolean supportsPip() {
        return hasDeviceFeature(FEATURE_PICTURE_IN_PICTURE)
                || PRETEND_DEVICE_SUPPORTS_PIP;
    }

    protected boolean supportsExpandedPip() {
        return hasDeviceFeature(FEATURE_EXPANDED_PICTURE_IN_PICTURE);
    }

    protected boolean supportsFreeform() {
        return hasDeviceFeature(FEATURE_FREEFORM_WINDOW_MANAGEMENT)
                || PRETEND_DEVICE_SUPPORTS_FREEFORM;
    }

    /** Whether or not the device supports lock screen. */
    protected boolean supportsLockScreen() {
        return supportsInsecureLock() || supportsSecureLock();
    }

    /** Whether or not the device supports pin/pattern/password lock. */
    protected boolean supportsSecureLock() {
        return FeatureUtil.hasSystemFeature(FEATURE_SECURE_LOCK_SCREEN);
    }

    /** Whether or not the device supports "swipe" lock. */
    protected boolean supportsInsecureLock() {
        return !FeatureUtil.hasAnySystemFeature(
                FEATURE_LEANBACK, FEATURE_WATCH, FEATURE_EMBEDDED, FEATURE_AUTOMOTIVE)
                && getSupportsInsecureLockScreen();
    }

    /** Try to enable gesture navigation mode */
    protected void enableAndAssumeGestureNavigationMode() {
        if (sGestureNavSwitchHelper == null) {
            sGestureNavSwitchHelper = new GestureNavSwitchHelper();
        }
        assumeTrue(sGestureNavSwitchHelper.enableGestureNavigationMode());
    }

    protected boolean supportsBlur() {
        return SystemProperties.get("ro.surface_flinger.supports_background_blur", "default")
                .equals("1");
    }

    protected boolean isWatch() {
        return hasDeviceFeature(FEATURE_WATCH);
    }

    protected boolean isCar() {
        return hasDeviceFeature(FEATURE_AUTOMOTIVE);
    }

    protected boolean isLeanBack() {
        return hasDeviceFeature(FEATURE_TELEVISION);
    }

    public static boolean isTablet() {
        if (sIsTablet == null) {
            // Use WindowContext with type application overlay to prevent the metrics overridden by
            // activity bounds. Note that process configuration may still be overridden by
            // foreground Activity.
            final Context appContext = ApplicationProvider.getApplicationContext();
            final Display defaultDisplay = appContext.getSystemService(DisplayManager.class)
                    .getDisplay(DEFAULT_DISPLAY);
            final Context windowContext = appContext.createWindowContext(defaultDisplay,
                    TYPE_APPLICATION_OVERLAY, null /* options */);
            sIsTablet = windowContext.getResources().getConfiguration().smallestScreenWidthDp
                    >= WindowManager.LARGE_SCREEN_SMALLEST_SCREEN_WIDTH_DP;
        }
        return sIsTablet;
    }

    protected void waitAndAssertActivityState(ComponentName activityName,
            String state, String message) {
        mWmState.waitForActivityState(activityName, state);

        assertTrue(message, mWmState.hasActivityState(activityName, state));
    }

    protected boolean isKeyguardLocked() {
        return mKm != null && mKm.isKeyguardLocked();
    }

    protected void waitAndAssertActivityStateOnDisplay(ComponentName activityName, String state,
            int displayId, String message) {
        waitAndAssertActivityState(activityName, state, message);
        assertEquals(message,
                /* expected = */ displayId,
                /* actual = */ mWmState.getDisplayByActivity(activityName));
    }

    public void waitAndAssertTopResumedActivity(ComponentName activityName, int displayId,
            String message) {
        final String activityClassName = getActivityName(activityName);
        mWmState.waitForWithAmState(state -> activityClassName.equals(state.getFocusedActivity()),
                "activity to be on top");
        waitAndAssertResumedActivity(activityName, "Activity must be resumed");
        mWmState.assertFocusedActivity(message, activityName);

        final int frontRootTaskId = mWmState.getFrontRootTaskId(displayId);
        Task frontRootTaskOnDisplay = mWmState.getRootTask(frontRootTaskId);
        assertEquals(
                "Resumed activity of front root task of the target display must match. " + message,
                activityClassName,
                frontRootTaskOnDisplay.isLeafTask() ? frontRootTaskOnDisplay.mResumedActivity
                        : frontRootTaskOnDisplay.getTopTask().mResumedActivity);
        mWmState.assertFocusedRootTask("Top activity's rootTask must also be on top",
                frontRootTaskId);
    }

    /**
     * Waits and asserts that the activity represented by the given activity name is resumed and
     * visible, but is not necessarily the top activity.
     *
     * @param activityName the activity name
     */
    public void waitAndAssertResumedActivity(ComponentName activityName) {
        waitAndAssertResumedActivity(
                activityName, activityName.toShortString() + " must be resumed");
    }

    /**
     * Waits and asserts that the activity represented by the given activity name is resumed and
     * visible, but is not necessarily the top activity.
     *
     * @param activityName the activity name
     * @param message the error message
     */
    public void waitAndAssertResumedActivity(ComponentName activityName, String message) {
        mWmState.waitForActivityState(activityName, STATE_RESUMED);
        mWmState.waitForValidState(activityName);
        mWmState.assertValidity();
        assertTrue(message, mWmState.hasActivityState(activityName, STATE_RESUMED));
        mWmState.assertVisibility(activityName, true /* visible */);
    }

    /**
     * Waits and asserts that the activity represented by the given activity name is stopped and
     * invisible.
     *
     * @param activityName the activity name
     */
    public void waitAndAssertStoppedActivity(ComponentName activityName) {
        waitAndAssertStoppedActivity(
                activityName, activityName.toShortString() + " must be stopped");
    }

    /**
     * Waits and asserts that the activity represented by the given activity name is stopped and
     * invisible.
     *
     * @param activityName the activity name
     * @param message the error message
     */
    public void waitAndAssertStoppedActivity(ComponentName activityName, String message) {
        mWmState.waitForValidState(activityName);
        mWmState.waitForActivityState(activityName, STATE_STOPPED);
        mWmState.assertValidity();
        assertTrue(message, mWmState.hasActivityState(activityName, STATE_STOPPED));
        mWmState.assertVisibility(activityName, false /* visible */);
    }

    // TODO: Switch to using a feature flag, when available.
    protected static boolean isUiModeLockedToVrHeadset() {
        final String output = runCommandAndPrintOutput("dumpsys uimode");

        Integer curUiMode = null;
        Boolean uiModeLocked = null;
        for (String line : output.split("\\n")) {
            line = line.trim();
            Matcher matcher = sCurrentUiModePattern.matcher(line);
            if (matcher.find()) {
                curUiMode = Integer.parseInt(matcher.group(1), 16);
            }
            matcher = sUiModeLockedPattern.matcher(line);
            if (matcher.find()) {
                uiModeLocked = matcher.group(1).equals("true");
            }
        }

        boolean uiModeLockedToVrHeadset = (curUiMode != null) && (uiModeLocked != null)
                && ((curUiMode & UI_MODE_TYPE_MASK) == UI_MODE_TYPE_VR_HEADSET) && uiModeLocked;

        if (uiModeLockedToVrHeadset) {
            log("UI mode is locked to VR headset");
        }

        return uiModeLockedToVrHeadset;
    }

    protected boolean supportsMultiWindow() {
        Display defaultDisplay = mDm.getDisplay(DEFAULT_DISPLAY);
        return supportsMultiWindow(mContext.createDisplayContext(defaultDisplay));
    }

    /**
     * Returns true if the Context supports multi-window-mode
     */
    protected final boolean supportsMultiWindow(Context context) {
        return ActivityTaskManager.supportsSplitScreenMultiWindow(context);
    }

    /** Returns true if the default display supports split screen multi-window. */
    protected boolean supportsSplitScreenMultiWindow() {
        Display defaultDisplay = mDm.getDisplay(DEFAULT_DISPLAY);
        return supportsSplitScreenMultiWindow(mContext.createDisplayContext(defaultDisplay));
    }

    /**
     * Returns true if the display associated with the supplied {@code context} supports split
     * screen multi-window.
     */
    protected boolean supportsSplitScreenMultiWindow(Context context) {
        return ActivityTaskManager.supportsSplitScreenMultiWindow(context);
    }

    protected boolean hasHomeScreen() {
        if (sHasHomeScreen == null) {
            sHasHomeScreen = !noHomeScreen();
        }
        return sHasHomeScreen;
    }

    protected boolean supportsSystemDecorsOnSecondaryDisplays() {
        if (sSupportsSystemDecorsOnSecondaryDisplays == null) {
            sSupportsSystemDecorsOnSecondaryDisplays = getSupportsSystemDecorsOnSecondaryDisplays();
        }
        return sSupportsSystemDecorsOnSecondaryDisplays;
    }

    protected boolean getSupportsInsecureLockScreen() {
        boolean insecure;
        try {
            insecure = mContext.getResources().getBoolean(
                    Resources.getSystem().getIdentifier(
                            "config_supportsInsecureLockScreen", "bool", "android"));
        } catch (Resources.NotFoundException e) {
            insecure = true;
        }
        return insecure;
    }

    protected boolean isAssistantOnTopOfDream() {
        if (sIsAssistantOnTop == null) {
            sIsAssistantOnTop = mContext.getResources().getBoolean(
                    android.R.bool.config_assistantOnTopOfDream);
        }
        return sIsAssistantOnTop;
    }

    protected boolean dismissDreamOnActivityStart() {
        if (sDismissDreamOnActivityStart == null) {
            try {
                sDismissDreamOnActivityStart = mContext.getResources().getBoolean(
                        Resources.getSystem().getIdentifier(
                                "config_dismissDreamOnActivityStart", "bool", "android"));
            } catch (Resources.NotFoundException e) {
                sDismissDreamOnActivityStart = true;
            }
        }
        return sDismissDreamOnActivityStart;
    }

    /**
     * Rotation support is indicated by explicitly having both landscape and portrait
     * features or not listing either at all.
     */
    protected boolean supportsRotation() {
        final boolean supportsLandscape = hasDeviceFeature(FEATURE_SCREEN_LANDSCAPE);
        final boolean supportsPortrait = hasDeviceFeature(FEATURE_SCREEN_PORTRAIT);
        return (supportsLandscape && supportsPortrait)
                || (!supportsLandscape && !supportsPortrait);
    }

    /**
     * The device should support orientation request from apps if it supports rotation and the
     * display is not close to square.
     */
    protected boolean supportsOrientationRequest() {
        return supportsRotation() && !isCloseToSquareDisplay();
    }

    /** Checks whether the display dimension is close to square. */
    protected boolean isCloseToSquareDisplay() {
        return isCloseToSquareDisplay(mContext);
    }

    /** Checks whether the display dimension is close to square. */
    public static boolean isCloseToSquareDisplay(Context context) {
        final Resources resources = context.getResources();
        final float closeToSquareMaxAspectRatio;
        try {
            closeToSquareMaxAspectRatio = resources.getFloat(resources.getIdentifier(
                    "config_closeToSquareDisplayMaxAspectRatio", "dimen", "android"));
        } catch (Resources.NotFoundException e) {
            // Assume device is not close to square.
            return false;
        }
        final DisplayMetrics displayMetrics = new DisplayMetrics();
        context.getSystemService(DisplayManager.class).getDisplay(DEFAULT_DISPLAY)
                .getRealMetrics(displayMetrics);
        final int w = displayMetrics.widthPixels;
        final int h = displayMetrics.heightPixels;
        final float aspectRatio = Math.max(w, h) / (float) Math.min(w, h);
        return aspectRatio <= closeToSquareMaxAspectRatio;
    }

    protected boolean hasDeviceFeature(final String requiredFeature) {
        return mContext.getPackageManager()
                .hasSystemFeature(requiredFeature);
    }

    protected static boolean isDisplayPortrait() {
        final DisplayManager displayManager = getInstrumentation()
                .getContext().getSystemService(DisplayManager.class);
        final Display display = displayManager.getDisplay(DEFAULT_DISPLAY);
        final DisplayMetrics displayMetrics = new DisplayMetrics();
        display.getRealMetrics(displayMetrics);
        return displayMetrics.widthPixels < displayMetrics.heightPixels;
    }

    protected static boolean isDisplayOn(int displayId) {
        final DisplayManager displayManager = getInstrumentation()
                .getContext().getSystemService(DisplayManager.class);
        final Display display = displayManager.getDisplay(displayId);
        return display != null && display.getState() == Display.STATE_ON;
    }

    protected static boolean perDisplayFocusEnabled() {
        return getInstrumentation().getTargetContext().getResources()
                .getBoolean(android.R.bool.config_perDisplayFocusEnabled);
    }

    protected static void removeLockCredential() {
        runCommandAndPrintOutput("locksettings clear --old " + LOCK_CREDENTIAL);
    }

    protected static boolean remoteInsetsControllerControlsSystemBars() {
        return getInstrumentation().getTargetContext().getResources()
                .getBoolean(android.R.bool.config_remoteInsetsControllerControlsSystemBars);
    }

    /** @see ObjectTracker#manage(AutoCloseable) */
    protected HomeActivitySession createManagedHomeActivitySession(ComponentName homeActivity) {
        return mObjectTracker.manage(new HomeActivitySession(homeActivity));
    }

    /** @see ObjectTracker#manage(AutoCloseable) */
    protected ActivitySessionClient createManagedActivityClientSession() {
        return mObjectTracker.manage(new ActivitySessionClient(mContext));
    }

    /** @see ObjectTracker#manage(AutoCloseable) */
    protected LockScreenSession createManagedLockScreenSession() {
        return mObjectTracker.manage(new LockScreenSession(mInstrumentation, mWmState));
    }

    /** @see ObjectTracker#manage(AutoCloseable) */
    protected LockScreenSession createManagedLockScreenSessionAndClearActivitiesOnClose() {
        return mObjectTracker.manage(new LockScreenSession(mInstrumentation, mWmState) {
            @Override
            public void close() {
                removeRootTasksWithActivityTypes(ALL_ACTIVITY_TYPE_BUT_HOME);
                super.close();
            }
        });
    }

    /** @see ObjectTracker#manage(AutoCloseable) */
    protected RotationSession createManagedRotationSession() {
        mWaitForRotationOnTearDown = true;
        return mObjectTracker.manage(new RotationSession(mWmState));
    }

    /** @see ObjectTracker#manage(AutoCloseable) */
    protected AodSession createManagedAodSession() {
        return mObjectTracker.manage(new AodSession());
    }

    /** @see ObjectTracker#manage(AutoCloseable) */
    protected DevEnableNonResizableMultiWindowSession
    createManagedDevEnableNonResizableMultiWindowSession() {
        return mObjectTracker.manage(new DevEnableNonResizableMultiWindowSession());
    }

    /** @see ObjectTracker#manage(AutoCloseable) */
    protected <T extends Activity> TestActivitySession<T> createManagedTestActivitySession() {
        return new TestActivitySession<T>();
    }

    /** @see ObjectTracker#manage(AutoCloseable) */
    protected SystemAlertWindowAppOpSession createAllowSystemAlertWindowAppOpSession() {
        return mObjectTracker.manage(
                new SystemAlertWindowAppOpSession(mContext.getOpPackageName(), MODE_ALLOWED));
    }

    /** @see ObjectTracker#manage(AutoCloseable) */
    protected FontScaleSession createManagedFontScaleSession() {
        return mObjectTracker.manage(new FontScaleSession());
    }

    /** Allows requesting orientation in case ignore_orientation_request is set to true. */
    protected void disableIgnoreOrientationRequest() {
        mObjectTracker.manage(new IgnoreOrientationRequestSession(false /* enable */));
    }

    /**
     * Test @Rule class that disables Immersive mode confirmation dialog.
     */
    public static class DisableImmersiveModeConfirmationRule implements TestRule {
        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    try (SettingsSession<String> immersiveModeConfirmationSetting =
                                 new SettingsSession<>(
                            Settings.Secure.getUriFor(IMMERSIVE_MODE_CONFIRMATIONS),
                            Settings.Secure::getString, Settings.Secure::putString)) {
                        immersiveModeConfirmationSetting.set("confirmed");
                        base.evaluate();
                    }
                }
            };
        }
    }

    /**
     * Test @Rule class that disables screen doze settings before each test method running and
     * restoring to initial values after test method finished.
     */
    protected class DisableScreenDozeRule implements TestRule {
        AmbientDisplayConfiguration mConfig;

        public DisableScreenDozeRule() {
            mConfig = new AmbientDisplayConfiguration(mContext);
        }

        @Override
        public Statement apply(final Statement base, final Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    try {
                        SystemUtil.runWithShellPermissionIdentity(() -> {
                            // disable current doze settings
                            mConfig.disableDozeSettings(true /* shouldDisableNonUserConfigurable */,
                                    android.os.Process.myUserHandle().getIdentifier());
                        });
                        base.evaluate();
                    } finally {
                        SystemUtil.runWithShellPermissionIdentity(() -> {
                            // restore doze settings
                            mConfig.restoreDozeSettings(
                                    android.os.Process.myUserHandle().getIdentifier());
                        });
                    }
                }
            };
        }
    }

    public ComponentName getDefaultHomeComponent() {
        final Intent intent = new Intent(ACTION_MAIN);
        intent.addCategory(CATEGORY_HOME);
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
        final ResolveInfo resolveInfo =
                mContext.getPackageManager().resolveActivity(intent, MATCH_DEFAULT_ONLY);
        if (resolveInfo == null) {
            throw new AssertionError("Home activity not found");
        }
        return new ComponentName(resolveInfo.activityInfo.packageName,
                resolveInfo.activityInfo.name);
    }

    /**
     * HomeActivitySession is used to replace the default home component, so that you can use
     * your preferred home for testing within the session. The original default home will be
     * restored automatically afterward.
     */
    protected class HomeActivitySession implements AutoCloseable {
        private PackageManager mPackageManager;
        private ComponentName mOrigHome;
        private ComponentName mSessionHome;

        HomeActivitySession(ComponentName sessionHome) {
            mSessionHome = sessionHome;
            mPackageManager = mContext.getPackageManager();
            mOrigHome = getDefaultHomeComponent();

            runWithShellPermission(
                    () -> mPackageManager.setComponentEnabledSetting(mSessionHome,
                            COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP));
            setDefaultHome(mSessionHome);
        }

        @Override
        public void close() {
            runWithShellPermission(
                    () -> mPackageManager.setComponentEnabledSetting(mSessionHome,
                            COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP));
            if (mOrigHome != null) {
                setDefaultHome(mOrigHome);
            }
        }

        private void setDefaultHome(ComponentName componentName) {
            executeShellCommand("cmd package set-home-activity --user "
                    + android.os.Process.myUserHandle().getIdentifier() + " "
                    + componentName.flattenToString());
        }
    }

    /** Helper class to set and restore appop mode "android:system_alert_window". */
    protected static class SystemAlertWindowAppOpSession implements AutoCloseable {
        private final String mPackageName;
        private final int mPreviousOpMode;

        SystemAlertWindowAppOpSession(String packageName, int mode) {
            mPackageName = packageName;
            try {
                mPreviousOpMode = AppOpsUtils.getOpMode(mPackageName, OPSTR_SYSTEM_ALERT_WINDOW);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            setOpMode(mode);
        }

        @Override
        public void close() {
            setOpMode(mPreviousOpMode);
        }

        void setOpMode(int mode) {
            try {
                AppOpsUtils.setOpMode(mPackageName, OPSTR_SYSTEM_ALERT_WINDOW, mode);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected class AodSession extends SettingsSession<Integer> {
        private AmbientDisplayConfiguration mConfig;

        AodSession() {
            super(Settings.Secure.getUriFor(Settings.Secure.DOZE_ALWAYS_ON),
                    Settings.Secure::getInt,
                    Settings.Secure::putInt);
            mConfig = new AmbientDisplayConfiguration(mContext);
        }

        public boolean isAodAvailable() {
            return mConfig.alwaysOnAvailable();
        }

        public void setAodEnabled(boolean enabled) {
            set(enabled ? 1 : 0);
        }
    }

    protected class DevEnableNonResizableMultiWindowSession extends SettingsSession<Integer> {
        DevEnableNonResizableMultiWindowSession() {
            super(Settings.Global.getUriFor(
                    Settings.Global.DEVELOPMENT_ENABLE_NON_RESIZABLE_MULTI_WINDOW),
                    (cr, name) -> Settings.Global.getInt(cr, name, 0 /* def */),
                    Settings.Global::putInt);
        }
    }

    /** Helper class to save, set, and restore font_scale preferences. */
    protected static class FontScaleSession extends SettingsSession<Float> {
        FontScaleSession() {
            super(Settings.System.getUriFor(Settings.System.FONT_SCALE),
                    Settings.System::getFloat,
                    Settings.System::putFloat);
        }

        @Override
        public Float get() {
            Float value = super.get();
            return value == null ? 1f : value;
        }
    }

    protected ChangeWallpaperSession createManagedChangeWallpaperSession() {
        return mObjectTracker.manage(new ChangeWallpaperSession());
    }

    protected class ChangeWallpaperSession implements AutoCloseable {
        private final WallpaperManager mWallpaperManager;
        private Bitmap mTestBitmap;

        public ChangeWallpaperSession() {
            mWallpaperManager = WallpaperManager.getInstance(mContext);
        }

        public Bitmap getTestBitmap() {
            if (mTestBitmap == null) {
                mTestBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
                final Canvas canvas = new Canvas(mTestBitmap);
                canvas.drawColor(Color.BLUE);
            }
            return mTestBitmap;
        }

        public void setImageWallpaper(Bitmap bitmap) {
            SystemUtil.runWithShellPermissionIdentity(() ->
                    mWallpaperManager.setBitmap(bitmap));
        }

        public void setWallpaperComponent(ComponentName componentName) {
            SystemUtil.runWithShellPermissionIdentity(() ->
                    mWallpaperManager.setWallpaperComponent(componentName));
        }

        @Override
        public void close() {
            SystemUtil.runWithShellPermissionIdentity(() -> mWallpaperManager.clearWallpaper());
            if (mTestBitmap != null) {
                mTestBitmap.recycle();
            }
            // Turning screen off/on to flush deferred color events due to wallpaper changed.
            pressSleepButton();
            pressWakeupButton();
            pressUnlockButton();
        }
    }
    /**
     * Returns whether the test device respects settings of locked user rotation mode.
     *
     * The method sets the locked user rotation settings to the rotation that rotates the display by
     * 180 degrees and checks if the actual display rotation changes after that.
     *
     * This is a necessary assumption check before leveraging user rotation mode to force display
     * rotation, because there is no requirement that an Android device that supports both
     * orientations needs to support user rotation mode.
     *
     * @param session   the rotation session used to set user rotation
     * @param displayId the display ID to check rotation against
     * @return {@code true} if test device respects settings of locked user rotation mode;
     * {@code false} if not.
     */
    protected boolean supportsLockedUserRotation(RotationSession session, int displayId) {
        final int origRotation = getDeviceRotation(displayId);
        // Use the same orientation as target rotation to avoid affect of app-requested orientation.
        final int targetRotation = (origRotation + 2) % 4;
        session.set(targetRotation);
        final boolean result = (getDeviceRotation(displayId) == targetRotation);
        session.set(origRotation);
        return result;
    }

    protected int getDeviceRotation(int displayId) {
        final String displays = runCommandAndPrintOutput("dumpsys display displays").trim();
        Pattern pattern = Pattern.compile(
                "(mDisplayId=" + displayId + ")([\\s\\S]*?)(mOverrideDisplayInfo)(.*)"
                        + "(rotation)(\\s+)(\\d+)");
        Matcher matcher = pattern.matcher(displays);
        if (matcher.find()) {
            final String match = matcher.group(7);
            return Integer.parseInt(match);
        }

        return INVALID_DEVICE_ROTATION;
    }

    /**
     * Creates a {#link ActivitySessionClient} instance with instrumentation context. It is used
     * when the caller doen't need try-with-resource.
     */
    public static ActivitySessionClient createActivitySessionClient() {
        return new ActivitySessionClient(getInstrumentation().getContext());
    }

    /** Empties the test journal so the following events won't be mixed-up with previous records. */
    protected void separateTestJournal() {
        TestJournalContainer.start();
    }

    protected static String runCommandAndPrintOutput(String command) {
        final String output = executeShellCommandAndGetStdout(command);
        log(output);
        return output;
    }

    protected static class LogSeparator {
        private final String mUniqueString;

        private LogSeparator() {
            mUniqueString = UUID.randomUUID().toString();
        }

        @Override
        public String toString() {
            return mUniqueString;
        }
    }

    /**
     * Inserts a log separator so we can always find the starting point from where to evaluate
     * following logs.
     *
     * @return Unique log separator.
     */
    protected LogSeparator separateLogs() {
        final LogSeparator logSeparator = new LogSeparator();
        executeShellCommand("log -t " + LOG_SEPARATOR + " " + logSeparator);
        EventLog.writeEvent(EVENT_LOG_SEPARATOR_TAG, logSeparator.mUniqueString);
        return logSeparator;
    }

    protected static String[] getDeviceLogsForComponents(
            LogSeparator logSeparator, String... logTags) {
        String filters = LOG_SEPARATOR + ":I ";
        for (String component : logTags) {
            filters += component + ":I ";
        }
        final String[] result = executeShellCommandAndGetStdout(
                "logcat -v brief -d " + filters + " *:S").split("\\n");
        if (logSeparator == null) {
            return result;
        }

        // Make sure that we only check logs after the separator.
        int i = 0;
        boolean lookingForSeparator = true;
        while (i < result.length && lookingForSeparator) {
            if (result[i].contains(logSeparator.toString())) {
                lookingForSeparator = false;
            }
            i++;
        }
        final String[] filteredResult = new String[result.length - i];
        for (int curPos = 0; i < result.length; curPos++, i++) {
            filteredResult[curPos] = result[i];
        }
        return filteredResult;
    }

    protected static List<Event> getEventLogsForComponents(LogSeparator logSeparator, int... tags) {
        List<Event> events = new ArrayList<>();

        int[] searchTags = Arrays.copyOf(tags, tags.length + 1);
        searchTags[searchTags.length - 1] = EVENT_LOG_SEPARATOR_TAG;

        try {
            EventLog.readEvents(searchTags, events);
        } catch (IOException e) {
            fail("Could not read from event log." + e);
        }

        for (Iterator<Event> itr = events.iterator(); itr.hasNext(); ) {
            Event event = itr.next();
            itr.remove();
            if (event.getTag() == EVENT_LOG_SEPARATOR_TAG &&
                    logSeparator.mUniqueString.equals(event.getData())) {
                break;
            }
        }
        return events;
    }

    protected boolean supportsMultiDisplay() {
        return mContext.getPackageManager().hasSystemFeature(
                FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS);
    }

    protected boolean supportsInstallableIme() {
        return mContext.getPackageManager().hasSystemFeature(FEATURE_INPUT_METHODS);
    }

    /**
     * Waits until the given activity has entered picture-in-picture mode (allowing for the
     * subsequent animation to start).
     */
    protected void waitForEnterPip(@NonNull ComponentName activityName) {
        mWmState.waitForWithAmState(wmState -> {
            Task task = wmState.getTaskByActivity(activityName);
            return task != null
                    && task.getActivity(activityName).getWindowingMode() == WINDOWING_MODE_PINNED
                    && task.isVisible();
        }, "checking task windowing mode");
    }

    /**
     * Waits until the picture-in-picture animation has finished.
     */
    protected void waitForEnterPipAnimationComplete(@NonNull ComponentName activityName) {
        waitForEnterPip(activityName);
        mWmState.waitForWithAmState(wmState -> {
            Task task = wmState.getTaskByActivity(activityName);
            if (task == null) {
                return false;
            }
            WindowManagerState.Activity activity = task.getActivity(activityName);
            return activity.getWindowingMode() == WINDOWING_MODE_PINNED
                    && activity.getState().equals(STATE_PAUSED);
        }, "checking activity windowing mode");
        if (ENABLE_SHELL_TRANSITIONS) {
            mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
        }
    }

    public static class CountSpec<T> {
        static final int DONT_CARE = Integer.MIN_VALUE;
        public static final int EQUALS = 1;
        public static final int GREATER_THAN = 2;
        static final int LESS_THAN = 3;
        public static final int GREATER_THAN_OR_EQUALS = 4;

        final T mEvent;
        final int mRule;
        final int mCount;
        final String mMessage;

        CountSpec(T event, int rule, int count, String message) {
            mEvent = event;
            mRule = count == DONT_CARE ? DONT_CARE : rule;
            mCount = count;
            if (message != null) {
                mMessage = message;
            } else {
                switch (rule) {
                    case EQUALS:
                        mMessage = event + " must equal to " + count;
                        break;
                    case GREATER_THAN:
                        mMessage = event + " must be greater than " + count;
                        break;
                    case LESS_THAN:
                        mMessage = event + " must be less than " + count;
                        break;
                    case GREATER_THAN_OR_EQUALS:
                        mMessage = event + " must be greater than (or equals to) " + count;
                        break;
                    default:
                        mMessage = "Don't care";
                }
            }
        }

        /** @return {@code true} if the given value is satisfied the condition. */
        boolean validate(int value) {
            switch (mRule) {
                case DONT_CARE:
                    return true;
                case EQUALS:
                    return value == mCount;
                case GREATER_THAN:
                    return value > mCount;
                case LESS_THAN:
                    return value < mCount;
                case GREATER_THAN_OR_EQUALS:
                    return value >= mCount;
                default:
            }
            throw new RuntimeException("Unknown CountSpec rule");
        }
    }

    static <T> CountSpec<T> countSpec(T event, int rule, int count, String message) {
        return new CountSpec<>(event, rule, count, message);
    }

    public static <T> CountSpec<T> countSpec(T event, int rule, int count) {
        return new CountSpec<>(event, rule, count, null /* message */);
    }

    static void assertLifecycleCounts(ComponentName activityName, String message,
            int createCount, int startCount, int resumeCount, int pauseCount, int stopCount,
            int destroyCount, int configChangeCount) {
        new ActivityLifecycleCounts(activityName).assertCountWithRetry(
                message,
                countSpec(ActivityCallback.ON_CREATE, CountSpec.EQUALS, createCount),
                countSpec(ActivityCallback.ON_START, CountSpec.EQUALS, startCount),
                countSpec(ActivityCallback.ON_RESUME, CountSpec.EQUALS, resumeCount),
                countSpec(ActivityCallback.ON_PAUSE, CountSpec.EQUALS, pauseCount),
                countSpec(ActivityCallback.ON_STOP, CountSpec.EQUALS, stopCount),
                countSpec(ActivityCallback.ON_DESTROY, CountSpec.EQUALS, destroyCount),
                countSpec(ActivityCallback.ON_CONFIGURATION_CHANGED, CountSpec.EQUALS,
                        configChangeCount));
    }

    public static void assertLifecycleCounts(
            ComponentName activityName,
            int createCount,
            int startCount,
            int resumeCount,
            int pauseCount,
            int stopCount,
            int destroyCount,
            int configChangeCount) {
        assertLifecycleCounts(activityName, "Assert lifecycle of " + getLogTag(activityName),
                createCount, startCount, resumeCount, pauseCount, stopCount,
                destroyCount, configChangeCount);
    }

    public static void assertSingleLaunch(ComponentName activityName) {
        assertLifecycleCounts(activityName,
                "activity create, start, and resume",
                1 /* createCount */, 1 /* startCount */, 1 /* resumeCount */,
                0 /* pauseCount */, 0 /* stopCount */, 0 /* destroyCount */,
                CountSpec.DONT_CARE /* configChangeCount */);
    }

    public static void assertSingleLaunchAndStop(ComponentName activityName) {
        assertLifecycleCounts(activityName,
                "activity create, start, resume, pause, and stop",
                1 /* createCount */, 1 /* startCount */, 1 /* resumeCount */,
                1 /* pauseCount */, 1 /* stopCount */, 0 /* destroyCount */,
                CountSpec.DONT_CARE /* configChangeCount */);
    }

    public static void assertSingleStartAndStop(ComponentName activityName) {
        assertLifecycleCounts(activityName,
                "activity start, resume, pause, and stop",
                0 /* createCount */, 1 /* startCount */, 1 /* resumeCount */,
                1 /* pauseCount */, 1 /* stopCount */, 0 /* destroyCount */,
                CountSpec.DONT_CARE /* configChangeCount */);
    }

    protected static void assertSingleStart(ComponentName activityName) {
        assertLifecycleCounts(activityName,
                "activity start and resume",
                0 /* createCount */, 1 /* startCount */, 1 /* resumeCount */,
                0 /* pauseCount */, 0 /* stopCount */, 0 /* destroyCount */,
                CountSpec.DONT_CARE /* configChangeCount */);
    }

    /** Assert the activity is either relaunched or received configuration changed. */
    protected static void assertActivityLifecycle(ComponentName activityName, boolean relaunched) {
        Condition.<String>waitForResult(
                activityName + (relaunched ? " relaunched" : " config changed"),
                condition -> condition
                .setResultSupplier(() -> checkActivityIsRelaunchedOrConfigurationChanged(
                        getActivityName(activityName),
                        TestJournalContainer.get(activityName).callbacks, relaunched))
                .setResultValidator(failedReasons -> failedReasons == null)
                .setOnFailure(failedReasons -> fail(failedReasons)));
    }

    /** Assert the activity is either relaunched or received configuration changed. */
    public static List<ActivityCallback> assertActivityLifecycle(
            ActivitySession activitySession, boolean relaunched) {
        final String name = activitySession.getName().flattenToShortString();
        final List<ActivityCallback> callbackHistory = activitySession.takeCallbackHistory();
        String failedReason = checkActivityIsRelaunchedOrConfigurationChanged(
                name, callbackHistory, relaunched);
        if (failedReason != null) {
            fail(failedReason);
        }
        return callbackHistory;
    }

    private static String checkActivityIsRelaunchedOrConfigurationChanged(String name,
            List<ActivityCallback> callbackHistory, boolean relaunched) {
        final ActivityLifecycleCounts lifecycles = new ActivityLifecycleCounts(callbackHistory);
        if (relaunched) {
            return lifecycles.validateCount(
                    countSpec(ActivityCallback.ON_DESTROY, CountSpec.GREATER_THAN, 0,
                            name + " must have been destroyed."),
                    countSpec(ActivityCallback.ON_CREATE, CountSpec.GREATER_THAN, 0,
                            name + " must have been (re)created."));
        }
        return lifecycles.validateCount(
                countSpec(ActivityCallback.ON_DESTROY, CountSpec.LESS_THAN, 1,
                        name + " must *NOT* have been destroyed."),
                countSpec(ActivityCallback.ON_CREATE, CountSpec.LESS_THAN, 1,
                        name + " must *NOT* have been (re)created."),
                countSpec(ActivityCallback.ON_CONFIGURATION_CHANGED, CountSpec.GREATER_THAN, 0,
                                name + " must have received configuration changed."));
    }

    public static void assertRelaunchOrConfigChanged(
            ComponentName activityName, int numRelaunch, int numConfigChange) {
        new ActivityLifecycleCounts(activityName).assertCountWithRetry("relaunch or config changed",
                countSpec(ActivityCallback.ON_DESTROY, CountSpec.EQUALS, numRelaunch),
                countSpec(ActivityCallback.ON_CREATE, CountSpec.EQUALS, numRelaunch),
                countSpec(ActivityCallback.ON_CONFIGURATION_CHANGED, CountSpec.EQUALS,
                        numConfigChange));
    }

    public static void assertActivityDestroyed(ComponentName activityName) {
        new ActivityLifecycleCounts(activityName).assertCountWithRetry("activity destroyed",
                countSpec(ActivityCallback.ON_DESTROY, CountSpec.EQUALS, 1),
                countSpec(ActivityCallback.ON_CREATE, CountSpec.EQUALS, 0),
                countSpec(ActivityCallback.ON_CONFIGURATION_CHANGED, CountSpec.EQUALS, 0));
    }

    public static void assertSecurityExceptionFromActivityLauncher() {
        waitForOrFail("SecurityException from " + ActivityLauncher.TAG,
                ActivityLauncher::hasCaughtSecurityException);
    }

    private static final Pattern sCurrentUiModePattern = Pattern.compile("mCurUiMode=0x(\\d+)");
    private static final Pattern sUiModeLockedPattern =
            Pattern.compile("mUiModeLocked=(true|false)");

    @NonNull
    public SizeInfo getLastReportedSizesForActivity(ComponentName activityName) {
        return Condition.waitForResult("sizes of " + activityName + " to be reported",
                condition -> condition.setResultSupplier(() -> {
                    final ConfigInfo info = TestJournalContainer.get(activityName).lastConfigInfo;
                    return info != null ? info.sizeInfo : null;
                }).setResultValidator(Objects::nonNull).setOnFailure(unusedResult ->
                        fail("No config reported from " + activityName)));
    }

    /** Check if a device has display cutout. */
    public boolean hasDisplayCutout() {
        // Launch an activity to report cutout state
        separateTestJournal();
        launchActivity(BROADCAST_RECEIVER_ACTIVITY);

        // Read the logs to check if cutout is present
        final Boolean displayCutoutPresent = getCutoutStateForActivity(BROADCAST_RECEIVER_ACTIVITY);
        assertNotNull("The activity should report cutout state", displayCutoutPresent);

        // Finish activity
        mBroadcastActionTrigger.finishBroadcastReceiverActivity();
        mWmState.waitForWithAmState(
                (state) -> !state.containsActivity(BROADCAST_RECEIVER_ACTIVITY),
                "activity to be removed");

        return displayCutoutPresent;
    }

    /**
     * Wait for activity to report cutout state in logs and return it. Will return {@code null}
     * after timeout.
     */
    @Nullable
    private Boolean getCutoutStateForActivity(ComponentName activityName) {
        return Condition.waitForResult("cutout state to be reported", condition -> condition
                .setResultSupplier(() -> {
                    final Bundle extras = TestJournalContainer.get(activityName).extras;
                    return extras.containsKey(EXTRA_CUTOUT_EXISTS)
                            ? extras.getBoolean(EXTRA_CUTOUT_EXISTS)
                            : null;
                }).setResultValidator(cutoutExists -> cutoutExists != null));
    }

    /** Waits for at least one onMultiWindowModeChanged event. */
    public ActivityLifecycleCounts waitForOnMultiWindowModeChanged(ComponentName activityName) {
        final ActivityLifecycleCounts counts = new ActivityLifecycleCounts(activityName);
        Condition.waitFor(counts.countWithRetry("waitForOnMultiWindowModeChanged", countSpec(
                ActivityCallback.ON_MULTI_WINDOW_MODE_CHANGED, CountSpec.GREATER_THAN, 0)));
        return counts;
    }

    protected WindowState getPackageWindowState(String packageName) {
        final WindowManagerState.WindowState window =
                mWmState.getWindowByPackageName(packageName, TYPE_BASE_APPLICATION);
        assertNotNull(window);
        return window;
    }

    public static class ActivityLifecycleCounts {
        private final int[] mCounts = new int[ActivityCallback.SIZE];
        private final int[] mFirstIndexes = new int[ActivityCallback.SIZE];
        private final int[] mLastIndexes = new int[ActivityCallback.SIZE];
        private ComponentName mActivityName;

        public ActivityLifecycleCounts(ComponentName componentName) {
            mActivityName = componentName;
            updateCount(TestJournalContainer.get(componentName).callbacks);
        }

        public ActivityLifecycleCounts(List<ActivityCallback> callbacks) {
            updateCount(callbacks);
        }

        private void updateCount(List<ActivityCallback> callbacks) {
            // The callback list could be from the reference of TestJournal. If we are counting for
            // retrying, there may be new data added to the list from other threads.
            TestJournalContainer.withThreadSafeAccess(() -> {
                Arrays.fill(mFirstIndexes, -1);
                for (int i = 0; i < callbacks.size(); i++) {
                    final ActivityCallback callback = callbacks.get(i);
                    final int ordinal = callback.ordinal();
                    mCounts[ordinal]++;
                    mLastIndexes[ordinal] = i;
                    if (mFirstIndexes[ordinal] == -1) {
                        mFirstIndexes[ordinal] = i;
                    }
                }
            });
        }

        public int getCount(ActivityCallback callback) {
            return mCounts[callback.ordinal()];
        }

        public int getFirstIndex(ActivityCallback callback) {
            return mFirstIndexes[callback.ordinal()];
        }

        public int getLastIndex(ActivityCallback callback) {
            return mLastIndexes[callback.ordinal()];
        }

        @SafeVarargs
        public final Condition<String> countWithRetry(
                String message, CountSpec<ActivityCallback>... countSpecs) {
            if (mActivityName == null) {
                throw new IllegalStateException(
                        "It is meaningless to retry without specified activity");
            }
            return new Condition<String>(message)
                    .setOnRetry(() -> {
                        Arrays.fill(mCounts, 0);
                        Arrays.fill(mLastIndexes, 0);
                        updateCount(TestJournalContainer.get(mActivityName).callbacks);
                    })
                    .setResultSupplier(() -> validateCount(countSpecs))
                    .setResultValidator(failedReasons -> failedReasons == null);
        }

        @SafeVarargs
        public final void assertCountWithRetry(
                String message, CountSpec<ActivityCallback>... countSpecs) {
            if (mActivityName == null) {
                throw new IllegalStateException(
                        "It is meaningless to retry without specified activity");
            }
            Condition.<String>waitForResult(countWithRetry(message, countSpecs)
                    .setOnFailure(failedReasons -> fail(message + ": " + failedReasons)));
        }

        @SafeVarargs
        final String validateCount(CountSpec<ActivityCallback>... countSpecs) {
            ArrayList<String> failedReasons = null;
            for (CountSpec<ActivityCallback> spec : countSpecs) {
                final int realCount = mCounts[spec.mEvent.ordinal()];
                if (!spec.validate(realCount)) {
                    if (failedReasons == null) {
                        failedReasons = new ArrayList<>();
                    }
                    failedReasons.add(spec.mMessage + " (got " + realCount + ")");
                }
            }
            return failedReasons == null ? null : String.join("\n", failedReasons);
        }
    }

    protected void stopTestPackage(final String packageName) {
        runWithShellPermission(() -> mAm.forceStopPackage(packageName));
    }

    protected LaunchActivityBuilder getLaunchActivityBuilder() {
        return new LaunchActivityBuilder(mWmState);
    }

    public static <T extends Activity>
    ActivityScenarioRule<T> createFullscreenActivityScenarioRule(Class<T> clazz) {
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WindowConfiguration.WINDOWING_MODE_FULLSCREEN);
        return new ActivityScenarioRule<>(clazz, options.toBundle());
    }

    protected static class LaunchActivityBuilder implements LaunchProxy {
        private final WindowManagerStateHelper mAmWmState;

        // The activity to be launched
        private ComponentName mTargetActivity = TEST_ACTIVITY;
        private boolean mUseApplicationContext;
        private boolean mToSide;
        private boolean mRandomData;
        private boolean mNewTask;
        private boolean mMultipleTask;
        private boolean mAllowMultipleInstances = true;
        private boolean mLaunchTaskBehind;
        private boolean mFinishBeforeLaunch;
        private int mDisplayId = INVALID_DISPLAY;
        private int mWindowingMode = -1;
        private int mActivityType = ACTIVITY_TYPE_UNDEFINED;
        // A proxy activity that launches other activities including mTargetActivityName
        private ComponentName mLaunchingActivity = LAUNCHING_ACTIVITY;
        private boolean mReorderToFront;
        private boolean mWaitForLaunched;
        private boolean mSuppressExceptions;
        private boolean mWithShellPermission;
        // Use of the following variables indicates that a broadcast receiver should be used instead
        // of a launching activity;
        private ComponentName mBroadcastReceiver;
        private String mBroadcastReceiverAction;
        private int mIntentFlags;
        private Bundle mExtras;
        private LaunchInjector mLaunchInjector;
        private ActivitySessionClient mActivitySessionClient;
        private int mLaunchTaskDisplayAreaFeatureId = FEATURE_UNDEFINED;

        private enum LauncherType {
            INSTRUMENTATION, LAUNCHING_ACTIVITY, BROADCAST_RECEIVER
        }

        private LauncherType mLauncherType = LauncherType.LAUNCHING_ACTIVITY;

        public LaunchActivityBuilder(WindowManagerStateHelper amWmState) {
            mAmWmState = amWmState;
            mWaitForLaunched = true;
            mWithShellPermission = true;
        }

        public LaunchActivityBuilder setToSide(boolean toSide) {
            mToSide = toSide;
            return this;
        }

        public LaunchActivityBuilder setRandomData(boolean randomData) {
            mRandomData = randomData;
            return this;
        }

        public LaunchActivityBuilder setNewTask(boolean newTask) {
            mNewTask = newTask;
            return this;
        }

        public LaunchActivityBuilder setMultipleTask(boolean multipleTask) {
            mMultipleTask = multipleTask;
            return this;
        }

        public LaunchActivityBuilder allowMultipleInstances(boolean allowMultipleInstances) {
            mAllowMultipleInstances = allowMultipleInstances;
            return this;
        }

        public LaunchActivityBuilder setLaunchTaskBehind(boolean launchTaskBehind) {
            mLaunchTaskBehind = launchTaskBehind;
            return this;
        }

        public LaunchActivityBuilder setReorderToFront(boolean reorderToFront) {
            mReorderToFront = reorderToFront;
            return this;
        }

        public LaunchActivityBuilder setUseApplicationContext(boolean useApplicationContext) {
            mUseApplicationContext = useApplicationContext;
            return this;
        }

        public LaunchActivityBuilder setFinishBeforeLaunch(boolean finishBeforeLaunch) {
            mFinishBeforeLaunch = finishBeforeLaunch;
            return this;
        }

        public ComponentName getTargetActivity() {
            return mTargetActivity;
        }

        public boolean isTargetActivityTranslucent() {
            return mAmWmState.isActivityTranslucent(mTargetActivity);
        }

        public LaunchActivityBuilder setTargetActivity(ComponentName targetActivity) {
            mTargetActivity = targetActivity;
            return this;
        }

        public LaunchActivityBuilder setDisplayId(int id) {
            mDisplayId = id;
            return this;
        }

        public LaunchActivityBuilder setWindowingMode(int windowingMode) {
            mWindowingMode = windowingMode;
            return this;
        }

        public LaunchActivityBuilder setActivityType(int type) {
            mActivityType = type;
            return this;
        }

        public LaunchActivityBuilder setLaunchingActivity(ComponentName launchingActivity) {
            mLaunchingActivity = launchingActivity;
            mLauncherType = LauncherType.LAUNCHING_ACTIVITY;
            return this;
        }

        public LaunchActivityBuilder setWaitForLaunched(boolean shouldWait) {
            mWaitForLaunched = shouldWait;
            return this;
        }

        public LaunchActivityBuilder setLaunchTaskDisplayAreaFeatureId(
                int launchTaskDisplayAreaFeatureId) {
            mLaunchTaskDisplayAreaFeatureId = launchTaskDisplayAreaFeatureId;
            return this;
        }

        /** Use broadcast receiver as a launchpad for activities. */
        public LaunchActivityBuilder setUseBroadcastReceiver(final ComponentName broadcastReceiver,
                final String broadcastAction) {
            mBroadcastReceiver = broadcastReceiver;
            mBroadcastReceiverAction = broadcastAction;
            mLauncherType = LauncherType.BROADCAST_RECEIVER;
            return this;
        }

        /** Use {@link android.app.Instrumentation} as a launchpad for activities. */
        public LaunchActivityBuilder setUseInstrumentation() {
            mLauncherType = LauncherType.INSTRUMENTATION;
            // Calling startActivity() from outside of an Activity context requires the
            // FLAG_ACTIVITY_NEW_TASK flag.
            setNewTask(true);
            return this;
        }

        public LaunchActivityBuilder setSuppressExceptions(boolean suppress) {
            mSuppressExceptions = suppress;
            return this;
        }

        public LaunchActivityBuilder setWithShellPermission(boolean withShellPermission) {
            mWithShellPermission = withShellPermission;
            return this;
        }

        public LaunchActivityBuilder setActivitySessionClient(ActivitySessionClient sessionClient) {
            mActivitySessionClient = sessionClient;
            return this;
        }

        @Override
        public boolean shouldWaitForLaunched() {
            return mWaitForLaunched;
        }

        public LaunchActivityBuilder setIntentFlags(int flags) {
            mIntentFlags = flags;
            return this;
        }

        public LaunchActivityBuilder setIntentExtra(Consumer<Bundle> extrasConsumer) {
            if (extrasConsumer != null) {
                mExtras = new Bundle();
                extrasConsumer.accept(mExtras);
            }
            return this;
        }

        @Override
        public Bundle getExtras() {
            return mExtras;
        }

        @Override
        public void setLaunchInjector(LaunchInjector injector) {
            mLaunchInjector = injector;
        }

        @Override
        public void execute() {
            if (mActivitySessionClient != null) {
                final ActivitySessionClient client = mActivitySessionClient;
                // Clear the session client so its startActivity can call the real execute().
                mActivitySessionClient = null;
                client.startActivity(this);
                return;
            }
            switch (mLauncherType) {
                case INSTRUMENTATION:
                    if (mWithShellPermission) {
                        NestedShellPermission.run(this::launchUsingInstrumentation);
                    } else {
                        launchUsingInstrumentation();
                    }
                    break;
                case LAUNCHING_ACTIVITY:
                case BROADCAST_RECEIVER:
                    launchUsingShellCommand();
            }

            if (mWaitForLaunched) {
                mAmWmState.waitForValidState(mTargetActivity);
            }
        }

        /** Launch an activity using instrumentation. */
        private void launchUsingInstrumentation() {
            final Bundle b = new Bundle();
            b.putBoolean(KEY_LAUNCH_ACTIVITY, true);
            b.putBoolean(KEY_LAUNCH_TO_SIDE, mToSide);
            b.putBoolean(KEY_RANDOM_DATA, mRandomData);
            b.putBoolean(KEY_NEW_TASK, mNewTask);
            b.putBoolean(KEY_MULTIPLE_TASK, mMultipleTask);
            b.putBoolean(KEY_MULTIPLE_INSTANCES, mAllowMultipleInstances);
            b.putBoolean(KEY_LAUNCH_TASK_BEHIND, mLaunchTaskBehind);
            b.putBoolean(KEY_REORDER_TO_FRONT, mReorderToFront);
            b.putInt(KEY_DISPLAY_ID, mDisplayId);
            b.putInt(KEY_WINDOWING_MODE, mWindowingMode);
            b.putInt(KEY_ACTIVITY_TYPE, mActivityType);
            b.putBoolean(KEY_USE_APPLICATION_CONTEXT, mUseApplicationContext);
            b.putString(KEY_TARGET_COMPONENT, getActivityName(mTargetActivity));
            b.putBoolean(KEY_SUPPRESS_EXCEPTIONS, mSuppressExceptions);
            b.putInt(KEY_INTENT_FLAGS, mIntentFlags);
            b.putBundle(KEY_INTENT_EXTRAS, getExtras());
            b.putInt(KEY_TASK_DISPLAY_AREA_FEATURE_ID, mLaunchTaskDisplayAreaFeatureId);
            final Context context = getInstrumentation().getContext();
            launchActivityFromExtras(context, b, mLaunchInjector);
        }

        /** Build and execute a shell command to launch an activity. */
        private void launchUsingShellCommand() {
            StringBuilder commandBuilder = new StringBuilder();
            if (mBroadcastReceiver != null && mBroadcastReceiverAction != null) {
                // Use broadcast receiver to launch the target.
                commandBuilder.append("am broadcast -a ").append(mBroadcastReceiverAction)
                        .append(" -p ").append(mBroadcastReceiver.getPackageName())
                        // Include stopped packages
                        .append(" -f 0x00000020");
            } else {
                // If new task flag isn't set the windowing mode of launcher activity will be the
                // windowing mode of the target activity, so we need to launch launcher activity in
                // it.
                String amStartCmd =
                        (mWindowingMode == -1 || mNewTask)
                                ? getAmStartCmd(mLaunchingActivity)
                                : getAmStartCmd(mLaunchingActivity, mDisplayId)
                                        + " --windowingMode " + mWindowingMode;
                // Use launching activity to launch the target.
                commandBuilder.append(amStartCmd)
                        .append(" -f 0x20000020");
            }

            // Add a flag to ensure we actually mean to launch an activity.
            commandBuilder.append(" --ez " + KEY_LAUNCH_ACTIVITY + " true");

            if (mToSide) {
                commandBuilder.append(" --ez " + KEY_LAUNCH_TO_SIDE + " true");
            }
            if (mRandomData) {
                commandBuilder.append(" --ez " + KEY_RANDOM_DATA + " true");
            }
            if (mNewTask) {
                commandBuilder.append(" --ez " + KEY_NEW_TASK + " true");
            }
            if (mMultipleTask) {
                commandBuilder.append(" --ez " + KEY_MULTIPLE_TASK + " true");
            }
            if (mAllowMultipleInstances) {
                commandBuilder.append(" --ez " + KEY_MULTIPLE_INSTANCES + " true");
            }
            if (mReorderToFront) {
                commandBuilder.append(" --ez " + KEY_REORDER_TO_FRONT + " true");
            }
            if (mFinishBeforeLaunch) {
                commandBuilder.append(" --ez " + KEY_FINISH_BEFORE_LAUNCH + " true");
            }
            if (mDisplayId != INVALID_DISPLAY) {
                commandBuilder.append(" --ei " + KEY_DISPLAY_ID + " ").append(mDisplayId);
            }
            if (mWindowingMode != -1) {
                commandBuilder.append(" --ei " + KEY_WINDOWING_MODE + " ").append(mWindowingMode);
            }
            if (mActivityType != ACTIVITY_TYPE_UNDEFINED) {
                commandBuilder.append(" --ei " + KEY_ACTIVITY_TYPE + " ").append(mActivityType);
            }

            if (mUseApplicationContext) {
                commandBuilder.append(" --ez " + KEY_USE_APPLICATION_CONTEXT + " true");
            }

            if (mTargetActivity != null) {
                // {@link ActivityLauncher} parses this extra string by
                // {@link ComponentName#unflattenFromString(String)}.
                commandBuilder.append(" --es " + KEY_TARGET_COMPONENT + " ")
                        .append(getActivityName(mTargetActivity));
            }

            if (mSuppressExceptions) {
                commandBuilder.append(" --ez " + KEY_SUPPRESS_EXCEPTIONS + " true");
            }

            if (mIntentFlags != 0) {
                commandBuilder.append(" --ei " + KEY_INTENT_FLAGS + " ").append(mIntentFlags);
            }

            if (mLaunchTaskDisplayAreaFeatureId != FEATURE_UNDEFINED) {
                commandBuilder.append(" --task-display-area-feature-id ")
                        .append(mLaunchTaskDisplayAreaFeatureId);
                commandBuilder.append(" --ei " + KEY_TASK_DISPLAY_AREA_FEATURE_ID + " ")
                        .append(mLaunchTaskDisplayAreaFeatureId);
            }

            if (mLaunchInjector != null) {
                commandBuilder.append(" --ez " + KEY_FORWARD + " true");
                mLaunchInjector.setupShellCommand(commandBuilder);
            }
            executeShellCommand(commandBuilder.toString());
        }
    }

    /**
     * The actions which wraps a test method. It is used to set necessary rules that cannot be
     * overridden by subclasses. It executes in the outer scope of {@link Before} and {@link After}.
     */
    protected class WrapperRule implements TestRule {
        private final Runnable mBefore;
        private final Runnable mAfter;

        protected WrapperRule(Runnable before, Runnable after) {
            mBefore = before;
            mAfter = after;
        }

        @Override
        public Statement apply(final Statement base, final Description description) {
            return new Statement() {
                @Override
                public void evaluate()  {
                    if (mBefore != null) {
                        mBefore.run();
                    }
                    try {
                        base.evaluate();
                    } catch (Throwable e) {
                        mPostAssertionRule.addError(e);
                    } finally {
                        if (mAfter != null) {
                            mAfter.run();
                        }
                    }
                }
            };
        }
    }

    /**
     * The post assertion to ensure all test methods don't violate the generic rule. It is also used
     * to collect multiple errors.
     */
    private class PostAssertionRule extends ErrorCollector {
        private Throwable mLastError;

        @Override
        protected void verify() throws Throwable {
            if (mLastError != null) {
                // Try to recover the bad state of device to avoid subsequent test failures.
                if (isKeyguardLocked()) {
                    mLastError.addSuppressed(new IllegalStateException("Keyguard is locked"));
                    unlockUnexpectedLockedKeyguard();
                }
                final String overlayDisplaySettings = Settings.Global.getString(
                        mContext.getContentResolver(), Settings.Global.OVERLAY_DISPLAY_DEVICES);
                if (overlayDisplaySettings != null && overlayDisplaySettings.length() > 0) {
                    mLastError.addSuppressed(new IllegalStateException(
                            "Overlay display is found: " + overlayDisplaySettings));
                    // Remove the overlay display because it may obscure the screen and causes the
                    // next tests to fail.
                    SettingsSession.delete(Settings.Global.getUriFor(
                            Settings.Global.OVERLAY_DISPLAY_DEVICES));
                }
            }
            if (!sIllegalTaskStateFound) {
                // Skip if a illegal task state was already found in previous test, or all tests
                // afterward could also fail and fire unnecessary false alarms.
                try {
                    mWmState.assertIllegalTaskState();
                } catch (Throwable t) {
                    sIllegalTaskStateFound = true;
                    addError(t);
                }
            }
            super.verify();
        }

        @Override
        public void addError(Throwable error) {
            super.addError(error);
            logE("addError: " + error);
            mLastError = error;
        }
    }

    /** Activity that can handle all config changes. */
    public static class ConfigChangeHandlingActivity extends CommandSession.BasicTestActivity {
    }

    public static class ReportedDisplayMetrics {
        private static final String WM_SIZE = "wm size";
        private static final String WM_DENSITY = "wm density";
        private static final Pattern PHYSICAL_SIZE =
                Pattern.compile("Physical size: (\\d+)x(\\d+)");
        private static final Pattern OVERRIDE_SIZE =
                Pattern.compile("Override size: (\\d+)x(\\d+)");
        private static final Pattern PHYSICAL_DENSITY =
                Pattern.compile("Physical density: (\\d+)");
        private static final Pattern OVERRIDE_DENSITY =
                Pattern.compile("Override density: (\\d+)");

        /** The size of the physical display. */
        @NonNull
        final Size physicalSize;
        /** The density of the physical display. */
        final int physicalDensity;

        /** The pre-existing size override applied to a logical display. */
        @Nullable
        final Size overrideSize;
        /** The pre-existing density override applied to a logical display. */
        @Nullable
        final Integer overrideDensity;

        final int mDisplayId;

        @NonNull
        public Size getPhysicalSize() {
            return physicalSize;
        }

        public int getPhysicalDensity() {
            return physicalDensity;
        }

        @Nullable
        public Size getOverrideSize() {
            return overrideSize;
        }

        public Integer getOverrideDensity() {
            return overrideDensity;
        }

        /** Get physical and override display metrics from WM for specified display. */
        public static ReportedDisplayMetrics getDisplayMetrics(int displayId) {
            return new ReportedDisplayMetrics(
                    executeShellCommandAndGetStdout(WM_SIZE + " -d " + displayId)
                    + executeShellCommandAndGetStdout(WM_DENSITY + " -d " + displayId), displayId);
        }

        public void setDisplayMetrics(final Size size, final int density) {
            setSize(size);
            setDensity(density);
        }

        public void restoreDisplayMetrics() {
            if (overrideSize != null) {
                setSize(overrideSize);
            } else {
                executeShellCommand(WM_SIZE + " reset -d " + mDisplayId);
            }
            if (overrideDensity != null) {
                setDensity(overrideDensity);
            } else {
                executeShellCommand(WM_DENSITY + " reset -d " + mDisplayId);
            }
        }

        public void setSize(final Size size) {
            executeShellCommand(
                    WM_SIZE + " " + size.getWidth() + "x" + size.getHeight() + " -d " + mDisplayId);
        }

        public void setDensity(final int density) {
            executeShellCommand(WM_DENSITY + " " + density + " -d " + mDisplayId);
        }

        /** Get display size that WM operates with. */
        public Size getSize() {
            return overrideSize != null ? overrideSize : physicalSize;
        }

        /** Get density that WM operates with. */
        public int getDensity() {
            return overrideDensity != null ? overrideDensity : physicalDensity;
        }

        private ReportedDisplayMetrics(final String lines, int displayId) {
            mDisplayId = displayId;
            Matcher matcher = PHYSICAL_SIZE.matcher(lines);
            assertTrue("Physical display size must be reported", matcher.find());
            log(matcher.group());
            physicalSize = new Size(
                    Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));

            matcher = PHYSICAL_DENSITY.matcher(lines);
            assertTrue("Physical display density must be reported", matcher.find());
            log(matcher.group());
            physicalDensity = Integer.parseInt(matcher.group(1));

            matcher = OVERRIDE_SIZE.matcher(lines);
            if (matcher.find()) {
                log(matcher.group());
                overrideSize = new Size(
                        Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
            } else {
                overrideSize = null;
            }

            matcher = OVERRIDE_DENSITY.matcher(lines);
            if (matcher.find()) {
                log(matcher.group());
                overrideDensity = Integer.parseInt(matcher.group(1));
            } else {
                overrideDensity = null;
            }
        }
    }

    /**
     * Either launches activity via {@link CommandSession.ActivitySessionClient} in case it is
     * a subclass of {@link CommandSession.BasicTestActivity} (then activity can be destroyed
     * by means of sending the finish command). Otherwise, launches activity via ADB commands
     * ({@link #launchActivityOnDisplay}), in this case the activity can be destroyed only as part
     * of the app package with ADB command `am stop-app`. In this case the activity can be destroyed
     * only if it is defined in another apk, so the test suit is not destroyed, this is detected
     * when catching {@link ClassNotFoundException} exception.
     */
    public class ActivitySessionCloseable implements AutoCloseable {
        private final ComponentName mActivityName;
        @Nullable
        protected CommandSession.ActivitySession mActivity;
        @Nullable
        private CommandSession.ActivitySessionClient mSession;

        public ActivitySessionCloseable(final ComponentName activityName) {
            this(activityName, WINDOWING_MODE_FULLSCREEN);
        }

        public ActivitySessionCloseable(final ComponentName activityName, final int windowingMode) {
            this(activityName, windowingMode, /* forceCommandActivity */ false);
        }

        /**
         * @param activityName can be created with
         *              {@link android.server.wm.component.ComponentsBase#component}.
         * @param windowingMode {@link WindowConfiguration.WindowingMode}
         * @param forceCommandActivity sometimes Activity implements
         *              {@link CommandSession.BasicTestActivity} but is defined in a different apk,
         *              so can not be verified if it is a subclass of
         *              {@link CommandSession.BasicTestActivity}. In this case forceCommandActivity
         *              argument can be used to ensure that this activity is managed as
         *              {@link CommandSession.BasicTestActivity}.
         */
        public ActivitySessionCloseable(
                final ComponentName activityName,
                final int windowingMode,
                final boolean forceCommandActivity) {
            mActivityName = activityName;

            if (forceCommandActivity || isCommandActivity()) {
                mSession = new CommandSession.ActivitySessionClient(mContext);
                mActivity = mSession.startActivity(getLaunchActivityBuilder()
                                .setUseInstrumentation()
                                .setWaitForLaunched(true)
                                .setNewTask(true)
                                .setMultipleTask(true)
                                .setWindowingMode(windowingMode)
                                .setTargetActivity(activityName));
            } else {
                launchActivityOnDisplay(activityName, windowingMode, DEFAULT_DISPLAY);
                mWmState.computeState(new WaitForValidActivityState(activityName));
            }
        }

        private boolean isAnotherApp() {
            try {
                Class.forName(mActivityName.getClassName());
                return false;
            } catch (ClassNotFoundException e) {
                return true;
            }
        }

        private boolean isCommandActivity() {
            try {
                var c = Class.forName(mActivityName.getClassName());
                return CommandSession.BasicTestActivity.class.isAssignableFrom(c);
            } catch (ClassNotFoundException e) {
                Log.w(TAG, "Class " + mActivityName.getClassName() + " is not found", e);
                return false;
            }
        }

        @Override
        public void close() {
            if (mSession != null && mActivity != null) {
                mSession.close();
                mWmState.waitForActivityRemoved(mActivityName);
            } else if (isAnotherApp()) {
                executeShellCommand("am stop-app " + mActivityName.getPackageName());
                mWmState.waitForActivityRemoved(mActivityName);
            } else {
                Log.w(TAG, "No explicit cleanup possible for " + mActivityName);
            }
        }

        public WindowManagerState.Activity getActivityState() {
            return getActivityWaitState(mActivityName);
        }

        /**
         * Not null only for {@link CommandSession.BasicTestActivity} activities.
         */
        @Nullable
        public CommandSession.ActivitySession getActivitySession() {
            return mActivity;
        }
    }

    /**
     * Same as ActivitySessionCloseable, but with forceCommandActivity = true
     */
    public class BaseActivitySessionCloseable extends ActivitySessionCloseable {
        public BaseActivitySessionCloseable(ComponentName activityName) {
            this(activityName, WINDOWING_MODE_FULLSCREEN);
        }

        public BaseActivitySessionCloseable(
                final ComponentName activityName, final int windowingMode) {
            super(activityName, windowingMode, /* forceCommandActivity */ true);
        }

        @Override
        @NonNull
        public CommandSession.ActivitySession getActivitySession() {
            assertNotNull(mActivity);
            return mActivity;
        }
    }

    /**
     * Launches primary and secondary activities in split-screen.
     */
    public class SplitScreenActivitiesCloseable implements AutoCloseable {
        private final ActivitySessionCloseable mPrimarySession;
        private final ActivitySessionCloseable mSecondarySession;

        public SplitScreenActivitiesCloseable(
                final ComponentName primaryActivityName,
                final ComponentName secondaryActivityName) {
            this(primaryActivityName, WINDOWING_MODE_FULLSCREEN,
                    /* forcePrimaryCommandActivity */ false,
                    secondaryActivityName, WINDOWING_MODE_FULLSCREEN,
                    /* forceSecondaryCommandActivity */ false);
        }

        public SplitScreenActivitiesCloseable(
                final ComponentName primaryActivityName,
                final int primaryWindowingMode,
                final boolean forcePrimaryCommandActivity,
                final ComponentName secondaryActivityName,
                final int secondaryWindowingMode,
                final boolean forceSecondaryCommandActivity) {
            mPrimarySession = new ActivitySessionCloseable(primaryActivityName,
                    primaryWindowingMode, forcePrimaryCommandActivity);
            mTaskOrganizer.putTaskInSplitPrimary(
                    mWmState.getTaskByActivity(primaryActivityName).mTaskId);
            mSecondarySession = new ActivitySessionCloseable(secondaryActivityName,
                    secondaryWindowingMode, forceSecondaryCommandActivity);
            mTaskOrganizer.putTaskInSplitSecondary(
                    mWmState.getTaskByActivity(secondaryActivityName).mTaskId);
            mWmState.computeState(new WaitForValidActivityState(primaryActivityName),
                    new WaitForValidActivityState(secondaryActivityName));
        }

        @Override
        public void close() {
            mPrimarySession.close();
            mSecondarySession.close();
        }

        public ActivitySessionCloseable getPrimaryActivity() {
            return mPrimarySession;
        }

        public ActivitySessionCloseable getSecondaryActivity() {
            return mSecondarySession;
        }
    }

    /**
     * Ensures the device is rotated to portrait orientation.
     */
    public class DeviceOrientationCloseable implements AutoCloseable {
        @Nullable
        private final RotationSession mRotationSession;

        /** Needed to restore the previous orientation in {@link #close} */
        private final Integer mPreviousRotation;

        /**
         * @param requestedOrientation values are Configuration#Orientation
         *          either {@link ORIENTATION_PORTRAIT} or {@link ORIENTATION_LANDSCAPE}
         */
        public DeviceOrientationCloseable(int requestedOrientation) {
            // Need to use window to get the size of the screen taking orientation into account.
            // mWmState.getDisplay(DEFAULT_DISPLAY).getFullConfiguration().orientation
            // can not be used because returned orientation can be {@link ORIENTATION_UNDEFINED}
            final Size windowSize = asSize(mWm.getMaximumWindowMetrics().getBounds());

            boolean isRotationRequired = false;
            if (ORIENTATION_PORTRAIT == requestedOrientation) {
                isRotationRequired = windowSize.getHeight() < windowSize.getWidth();
            } else if (ORIENTATION_LANDSCAPE == requestedOrientation) {
                isRotationRequired = windowSize.getHeight() > windowSize.getWidth();
            }

            if (isRotationRequired) {
                mPreviousRotation = mWmState.getRotation();
                mRotationSession = new RotationSession(mWmState);
                mRotationSession.set(ROTATION_90);
                assertTrue("display rotation must be ROTATION_90 now",
                        mWmState.waitForRotation(ROTATION_90));
            } else {
                mRotationSession = null;
                mPreviousRotation = ROTATION_0;
            }
        }

        @Override
        public void close() {
            if (mRotationSession != null) {
                mRotationSession.close();
                mWmState.waitForRotation(mPreviousRotation);
            }
        }

        public boolean isRotationApplied() {
            return mRotationSession != null;
        }
    }

    /**
     * Makes sure {@link DisplayMetricsSession} is closed with waitFor original display content
     * is restored.
     */
    public class DisplayMetricsWaitCloseable extends DisplayMetricsSession {
        private final int mDisplayId;
        private final WindowManagerState.DisplayContent mOriginalDC;

        public DisplayMetricsWaitCloseable() {
            this(DEFAULT_DISPLAY);
        }

        public DisplayMetricsWaitCloseable(int displayId) {
            super(displayId);
            mDisplayId = displayId;
            mOriginalDC = mWmState.getDisplay(displayId);
        }

        @Override
        public void restoreDisplayMetrics() {
            mWmState.waitForWithAmState(wmState -> {
                super.restoreDisplayMetrics();
                return mWmState.getDisplay(mDisplayId).equals(mOriginalDC);
            }, "waiting for display to be restored");
        }
    }

    /**
     * AutoClosable class used for try-with-resources compat change tests, which require a separate
     * application task to be started.
     */
    public static class CompatChangeCloseable implements AutoCloseable {
        private final String mChangeName;
        private final String mPackageName;

        public CompatChangeCloseable(final Long changeId, String packageName) {
            this(changeId.toString(), packageName);
        }

        public CompatChangeCloseable(final String changeName, String packageName) {
            this.mChangeName = changeName;
            this.mPackageName = packageName;

            // Enable change
            executeShellCommand("am compat enable " + changeName + " " + packageName);
        }

        @Override
        public void close() {
            executeShellCommand("am compat disable " + mChangeName + " " + mPackageName);
        }
    }

    /**
     * Scales the display size
     */
    public class DisplaySizeScaleCloseable extends DisplaySizeCloseable {
        /**
         * @param sizeScaleFactor display size scaling factor.
         * @param activity can be null, the activity which is currently on the screen.
         */
        public DisplaySizeScaleCloseable(double sizeScaleFactor, @Nullable ComponentName activity) {
            super(sizeScaleFactor, /* densityScaleFactor */ 1, ORIENTATION_UNDEFINED,
                    /* aspectRatio */ -1, asList(activity));
        }
    }

    /**
     * Changes aspectRatio of the display.
     */
    public class DisplayAspectRatioCloseable extends DisplaySizeCloseable {
        /**
         * @param requestedOrientation orientation.
         * @param aspectRatio aspect ratio of the screen.
         */
        public DisplayAspectRatioCloseable(int requestedOrientation, double aspectRatio) {
            super(/* sizeScaleFactor */ 1, /* densityScaleFactor */ 1, requestedOrientation,
                    aspectRatio, /* activities */ List.of());
        }

        /**
         * @param requestedOrientation orientation.
         * @param aspectRatio aspect ratio of the screen.
         * @param activity the current activity.
         */
        public DisplayAspectRatioCloseable(int requestedOrientation, double aspectRatio,
                @Nullable ComponentName activity) {
            super(/* sizeScaleFactor */ 1, /* densityScaleFactor */ 1, requestedOrientation,
                    aspectRatio, asList(activity));
        }
    }

    public class DisplaySizeCloseable extends DisplayMetricsWaitCloseable {

        private List<Pair<ComponentName, Rect>> mNewBounds = List.of();

        private static boolean isLandscape(Size s) {
            return s.getWidth() > s.getHeight();
        }

        protected static <T> List<T> asList(@Nullable T v) {
            return (v != null) ? List.of(v) : List.of();
        }

        /**
         * @param sizeScaleFactor display size scaling factor.
         * @param densityScaleFactor density scaling factor.
         * @param activities can be empty, the activities which are currently on the screen.
         */
        public DisplaySizeCloseable(double sizeScaleFactor, double densityScaleFactor,
                final int requestedOrientation, final double aspectRatio,
                @NonNull List<ComponentName> activities) {
            if (sizeScaleFactor != 1 || densityScaleFactor != 1) {
                var originalBounds = activities.stream()
                        .map(a -> new Pair<>(a, getActivityWaitState(a).getBounds()))
                        .toList();

                final var origDisplaySize = getDisplayMetrics().getSize();

                changeDisplayMetrics(sizeScaleFactor, densityScaleFactor);
                waitForDisplaySizeChanged(origDisplaySize, sizeScaleFactor);

                originalBounds.forEach(activityAndBounds -> {
                    waitForActivityBoundsChanged(activityAndBounds.first, activityAndBounds.second);
                    mWmState.computeState(new WaitForValidActivityState(activityAndBounds.first));
                });

                mNewBounds = activities.stream()
                        .map(a -> new Pair<>(a, getActivityWaitState(a).getBounds()))
                        .toList();
            }

            if (ORIENTATION_UNDEFINED != requestedOrientation && aspectRatio > 0) {
                final Size maxWindowSize = asSize(mWm.getMaximumWindowMetrics().getBounds());
                final var origDisplaySize = getDisplayMetrics().getSize();

                var isMatchingOrientation =
                        isLandscape(origDisplaySize) == isLandscape(maxWindowSize);
                if (ORIENTATION_LANDSCAPE == requestedOrientation) {
                    changeAspectRatio(aspectRatio,
                            isMatchingOrientation ? ORIENTATION_LANDSCAPE : ORIENTATION_PORTRAIT);
                    waitForDisplaySizeChanged(origDisplaySize, aspectRatio);
                } else if (ORIENTATION_PORTRAIT == requestedOrientation) {
                    changeAspectRatio(aspectRatio,
                            isMatchingOrientation ? ORIENTATION_PORTRAIT : ORIENTATION_LANDSCAPE);
                    waitForDisplaySizeChanged(origDisplaySize, aspectRatio);
                }
            }
        }

        @Override
        public void close() {
            super.close();
            mNewBounds.forEach(activityAndBounds -> {
                if (mWmState.isActivityVisible(activityAndBounds.first)) {
                    waitForActivityBoundsChanged(activityAndBounds.first, activityAndBounds.second);
                    mWmState.computeState(new WaitForValidActivityState(activityAndBounds.first));
                }
            });
        }


        /**
         * Waits until the given activity has updated task bounds.
         */
        private void waitForActivityBoundsChanged(ComponentName activityName,
                Rect priorActivityBounds) {
            mWmState.waitForWithAmState(wmState -> {
                mWmState.computeState(new WaitForValidActivityState(activityName));
                WindowManagerState.Activity activity = wmState.getActivity(activityName);
                return activity != null && !activity.getBounds().equals(priorActivityBounds);
            }, "checking activity bounds updated");
        }

        /**
         * Waits until the display bounds changed.
         */
        private void waitForDisplaySizeChanged(final Size originalDisplaySize, final double ratio) {
            if (!mWmState.waitForWithAmState(wmState ->
                    !originalDisplaySize.equals(getDisplayMetrics().getSize()),
                    "waiting for display changing aspect ratio")) {

                final Size currentDisplaySize = getDisplayMetrics().getSize();
                // Sometimes display size can be capped, making it impossible to scale the size up
                // b/192406238.
                if (ratio >= 1f) {
                    assumeFalse("If a display size is capped, resizing may be a no-op",
                            originalDisplaySize.equals(currentDisplaySize));
                } else {
                    assertNotEquals("Display size must change if sizeRatio < 1f",
                            originalDisplaySize, currentDisplaySize);
                }
            }
        }

        public float getInitialDisplayAspectRatio() {
            Size size = getInitialDisplayMetrics().getSize();
            return Math.max(size.getHeight(), size.getWidth())
                    / (float) (Math.min(size.getHeight(), size.getWidth()));
        }
    }

    public static Size asSize(Rect r) {
        return new Size(r.width(), r.height());
    }

    public <T> void waitAssertEquals(final String message, final T expected, Supplier<T> actual) {
        assertTrue(message, mWmState.waitFor(state -> expected.equals(actual.get()),
                "wait for correct result"));
    }

    public WindowManagerState.Activity getActivityWaitState(ComponentName activityName) {
        mWmState.computeState(new WaitForValidActivityState(activityName));
        return mWmState.getActivity(activityName);
    }

    /**
     * Inset given frame if the insets source exist.
     *
     * @param windowState The window which have the insets source.
     * @param predicate Inset source predicate.
     * @param inOutBounds In/out the given frame from the inset source.
     */
    public static void insetGivenFrame(WindowManagerState.WindowState windowState,
            Predicate<WindowManagerState.InsetsSource> predicate, Rect inOutBounds) {
        Optional<WindowManagerState.InsetsSource> insetsOptional =
                windowState.getMergedLocalInsetsSources().stream().filter(
                        predicate).findFirst();
        insetsOptional.ifPresent(insets -> insets.insetGivenFrame(inOutBounds));
    }

    /**
     * Checks whether the device has automotive split-screen multitasking feature enabled
     */
    protected boolean hasAutomotiveSplitscreenMultitaskingFeature() {
        return mContext.getPackageManager()
                .hasSystemFeature(/* PackageManager.FEATURE_CAR_SPLITSCREEN_MULTITASKING */
                        "android.software.car.splitscreen_multitasking") && isCar();
    }
}
