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
 * limitations under the License.
 */
package com.android.car.am;

import static android.car.builtin.app.ActivityManagerHelper.INVALID_TASK_ID;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;
import static android.os.Process.INVALID_UID;

import static com.android.car.CarLog.TAG_AM;
import static com.android.car.CarServiceUtils.getHandlerThread;
import static com.android.car.CarServiceUtils.isEventOfType;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.TaskInfo;
import android.car.builtin.app.ActivityManagerHelper;
import android.car.builtin.app.ActivityManagerHelper.ProcessObserverCallback;
import android.car.builtin.app.TaskInfoHelper;
import android.car.builtin.content.ContextHelper;
import android.car.builtin.content.pm.PackageManagerHelper;
import android.car.builtin.util.Slogf;
import android.car.hardware.power.CarPowerManager;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.car.user.UserLifecycleEventFilter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.net.Uri;
import android.os.BaseBundle;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.util.SparseArray;
import android.util.proto.ProtoOutputStream;
import android.view.Display;

import com.android.car.CarLocalServices;
import com.android.car.CarServiceBase;
import com.android.car.R;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.user.CarUserService;
import com.android.car.user.UserHandleHelper;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Monitors top activity for a display and guarantee activity in fixed mode is re-launched if it has
 * crashed or gone to background for whatever reason.
 *
 * <p>This component also monitors the update of the target package and re-launch it once
 * update is complete.</p>
 */
public final class FixedActivityService implements CarServiceBase {

    private static final boolean DBG = Slogf.isLoggable(TAG_AM, Log.DEBUG);

    private static final long RECHECK_INTERVAL_MS = 500;
    private static final int MAX_NUMBER_OF_CONSECUTIVE_CRASH_RETRY = 5;
    // If process keep running without crashing, will reset consecutive crash counts.
    private static final long CRASH_FORGET_INTERVAL_MS = 2 * 60 * 1000; // 2 mins

    private static class RunningActivityInfo {
        @NonNull
        public final Intent intent;

        @NonNull
        public final Bundle activityOptions;

        @UserIdInt
        public final int userId;

        public boolean isVisible;
        // Whether startActivity was called for this Activity. If the flag is false,
        // FixedActivityService will call startActivity() even if the Activity is currently visible.
        public boolean isStarted;

        public long lastLaunchTimeMs;

        public int consecutiveRetries;

        public int taskId = INVALID_TASK_ID;

        public int previousTaskId = INVALID_TASK_ID;

        public boolean inBackground;

        public boolean failureLogged;

        RunningActivityInfo(@NonNull Intent intent, @NonNull Bundle activityOptions,
                @UserIdInt int userId) {
            this.intent = intent;
            this.activityOptions = activityOptions;
            this.userId = userId;
        }

        private void resetCrashCounterLocked() {
            consecutiveRetries = 0;
            failureLogged = false;
        }

        @Override
        public String toString() {
            return "RunningActivityInfo{intent:" + intent + ",activityOptions:" + activityOptions
                    + ",userId:" + userId + ",isVisible:" + isVisible + ",isStarted:" + isStarted
                    + ",lastLaunchTimeMs:" + lastLaunchTimeMs
                    + ",consecutiveRetries:" + consecutiveRetries + ",taskId:" + taskId
                    + ",previousTaskId:" + previousTaskId + ",inBackground:" + inBackground + "}";
        }
    }

    private final Context mContext;

    private final CarActivityService mActivityService;

    private final DisplayManager mDm;

    private final UserLifecycleListener mUserLifecycleListener = event -> {
        if (!isEventOfType(TAG_AM, event, USER_LIFECYCLE_EVENT_TYPE_SWITCHING)) {
            return;
        }
        if (DBG) {
            Slogf.d(TAG_AM, "onEvent(" + event + ")");
        }

        synchronized (FixedActivityService.this.mLock) {
            clearRunningActivitiesLocked();
        }
    };

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_PACKAGE_CHANGED.equals(action)
                    || Intent.ACTION_PACKAGE_REPLACED.equals(action)
                    || Intent.ACTION_PACKAGE_ADDED.equals(action)
                    || Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                Uri packageData = intent.getData();
                if (packageData == null) {
                    Slogf.w(TAG_AM, "null packageData");
                    return;
                }
                String packageName = packageData.getSchemeSpecificPart();
                if (packageName == null) {
                    Slogf.w(TAG_AM, "null packageName");
                    return;
                }
                int uid = intent.getIntExtra(Intent.EXTRA_UID, INVALID_UID);
                int userId = UserHandle.getUserHandleForUid(uid).getIdentifier();
                boolean tryLaunch = false;
                synchronized (mLock) {
                    for (int i = 0; i < mRunningActivities.size(); i++) {
                        RunningActivityInfo info = mRunningActivities.valueAt(i);
                        // Should do this for all activities as it can happen for multiple
                        // displays. Package name is ignored as one package can affect
                        // others.
                        if (info.userId == userId) {
                            Slogf.i(TAG_AM, "Package changed:" + packageName
                                    + ",user:" + userId + ",action:" + action);
                            info.resetCrashCounterLocked();
                            tryLaunch = true;
                            break;
                        }
                    }
                }
                if (tryLaunch) {
                    launchIfNecessary();
                }
            }
        }
    };

    private final ProcessObserverCallback mProcessObserver = new ProcessObserverCallback() {
        @Override
        public void onForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) {
            launchIfNecessary();
        }
        @Override
        public void onProcessDied(int pid, int uid) {
            launchIfNecessary();
        }
    };

    private final DisplayListener mDisplayListener = new DisplayListener() {

        @Override
        public void onDisplayChanged(int displayId) {
            if (DBG) {
                Slogf.d(TAG_AM, "onDisplayChanged(%d)", displayId);
            }
            launchForDisplay(displayId);
        }

        @Override
        public void onDisplayAdded(int displayId) {
            // do nothing.
        }

        @Override
        public void onDisplayRemoved(int displayId) {
            // do nothing.
        }
    };

    private final Handler mHandler;

    private final Runnable mActivityCheckRunnable = () -> {
        launchIfNecessary();
    };

    private final Object mLock = new Object();

    // key: displayId
    @GuardedBy("mLock")
    private final SparseArray<RunningActivityInfo> mRunningActivities =
            new SparseArray<>(/* capacity= */ 1); // default to one cluster only case

    @GuardedBy("mLock")
    private boolean mEventMonitoringActive;

    @GuardedBy("mLock")
    private CarPowerManager mCarPowerManager;

    private final CarPowerManager.CarPowerStateListener mCarPowerStateListener = (state) -> {
        if (state != CarPowerManager.STATE_ON) {
            return;
        }
        synchronized (mLock) {
            for (int i = 0; i < mRunningActivities.size(); i++) {
                RunningActivityInfo info = mRunningActivities.valueAt(i);
                info.resetCrashCounterLocked();
            }
        }
        launchIfNecessary();
    };

    private final UserHandleHelper mUserHandleHelper;

    public FixedActivityService(Context context, CarActivityService activityService) {
        this(context, activityService,
                context.getSystemService(DisplayManager.class),
                new UserHandleHelper(context, context.getSystemService(UserManager.class)));
    }

    @VisibleForTesting
    FixedActivityService(Context context, CarActivityService activityService,
            DisplayManager displayManager, UserHandleHelper userHandleHelper) {
        mContext = context;
        mActivityService = activityService;
        mDm = displayManager;
        mHandler = new Handler(getHandlerThread(
                FixedActivityService.class.getSimpleName()).getLooper());
        mUserHandleHelper = userHandleHelper;
    }

    @Override
    public void init() {
        // Register display listener here, not in startMonitoringEvents(), because we need
        // display listener even when no activity is launched.
        mDm.registerDisplayListener(mDisplayListener, mHandler);
    }

    @Override
    public void release() {
        stopMonitoringEvents();
        mDm.unregisterDisplayListener(mDisplayListener);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.println("*FixedActivityService*");
        synchronized (mLock) {
            writer.println("mRunningActivities:" + mRunningActivities
                    + " ,mEventMonitoringActive:" + mEventMonitoringActive);
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {}

    @GuardedBy("mLock")
    private void clearRunningActivitiesLocked() {
        for (int i = mRunningActivities.size() - 1; i >= 0; i--) {
            RunningActivityInfo info = mRunningActivities.valueAt(i);
            if (info == null || !isUserAllowedToLaunchActivity(info.userId)) {
                mRunningActivities.removeAt(i);
            }
        }
    }

    private void postRecheck(long delayMs) {
        mHandler.postDelayed(mActivityCheckRunnable, delayMs);
    }

    private void startMonitoringEvents() {
        CarPowerManager carPowerManager;
        synchronized (mLock) {
            if (mEventMonitoringActive) {
                return;
            }
            mEventMonitoringActive = true;
            carPowerManager = CarLocalServices.createCarPowerManager(mContext);
            mCarPowerManager = carPowerManager;
        }
        CarUserService userService = CarLocalServices.getService(CarUserService.class);
        UserLifecycleEventFilter userSwitchingEventFilter = new UserLifecycleEventFilter.Builder()
                .addEventType(USER_LIFECYCLE_EVENT_TYPE_SWITCHING).build();
        userService.addUserLifecycleListener(userSwitchingEventFilter, mUserLifecycleListener);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        mContext.registerReceiverForAllUsers(mBroadcastReceiver, filter,
                /* broadcastPermission= */ null, /* scheduler= */ null,
                Context.RECEIVER_NOT_EXPORTED);
        ActivityManagerHelper.registerProcessObserverCallback(mProcessObserver);
        try {
            carPowerManager.setListener(mContext.getMainExecutor(), mCarPowerStateListener);
        } catch (Exception e) {
            // should not happen
            Slogf.e(TAG_AM, "Got exception from CarPowerManager", e);
        }
    }

    private void stopMonitoringEvents() {
        CarPowerManager carPowerManager;
        synchronized (mLock) {
            if (!mEventMonitoringActive) {
                return;
            }
            mEventMonitoringActive = false;
            carPowerManager = mCarPowerManager;
            mCarPowerManager = null;
        }
        if (carPowerManager != null) {
            carPowerManager.clearListener();
        }
        mHandler.removeCallbacks(mActivityCheckRunnable);
        CarUserService userService = CarLocalServices.getService(CarUserService.class);
        userService.removeUserLifecycleListener(mUserLifecycleListener);
        ActivityManagerHelper.unregisterProcessObserverCallback(mProcessObserver);
        mContext.unregisterReceiver(mBroadcastReceiver);
    }

    /**
     * Launches all stored fixed mode activities if necessary.
     *
     * @param displayId Display id to check if it is visible. If check is not necessary, should pass
     *         {@link Display#INVALID_DISPLAY}.
     * @return true if fixed Activity for given {@code displayId} is visible / successfully
     *         launched. It will return false for {@link Display#INVALID_DISPLAY} {@code displayId}.
     */
    private boolean launchIfNecessary(int displayId) {
        if (DBG) {
            Slogf.d(TAG_AM, "launchIfNecessary(%d)", displayId);
        }
        // Get the visible tasks on the specified display. INVALID_DISPLAY means all displays.
        List<? extends TaskInfo> infos = mActivityService.getVisibleTasksInternal(displayId);
        if (infos == null) {
            Slogf.e(TAG_AM, "cannot get RootTaskInfo from AM");
            return false;
        }
        long now = SystemClock.elapsedRealtime();
        synchronized (mLock) {
            for (int i = mRunningActivities.size() - 1; i >= 0; i--) {
                int displayIdForActivity = mRunningActivities.keyAt(i);
                Display display = mDm.getDisplay(displayIdForActivity);
                if (display == null) {
                    Slogf.e(TAG_AM, "Stop fixed activity for unavailable display%d",
                            displayIdForActivity);
                    mRunningActivities.removeAt(i);
                    continue;
                }

                RunningActivityInfo activityInfo = mRunningActivities.valueAt(i);
                // Do not reset isVisible flag when the recheck interval has not passed yet,
                // since the last launch attempt.
                if (!activityInfo.isStarted
                        || (now - activityInfo.lastLaunchTimeMs) > RECHECK_INTERVAL_MS) {
                    activityInfo.isVisible = false;
                }
                if (isUserAllowedToLaunchActivity(activityInfo.userId)) {
                    continue;
                }
                if (activityInfo.taskId != INVALID_TASK_ID) {
                    Slogf.i(TAG_AM, "Finishing fixed activity on user switching:"
                            + activityInfo);
                    ActivityManagerHelper.removeTask(activityInfo.taskId);
                    Intent intent = new Intent()
                            .setComponent(
                                    ComponentName.unflattenFromString(
                                            mContext.getString(R.string.continuousBlankActivity)
                                    ))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                    ActivityOptions activityOptions = ActivityOptions.makeBasic()
                            .setLaunchDisplayId(displayIdForActivity);
                    ContextHelper.startActivityAsUser(mContext, intent, activityOptions.toBundle(),
                            UserHandle.of(ActivityManager.getCurrentUser()));
                }
                mRunningActivities.removeAt(i);
            }
            if (mRunningActivities.size() == 0) {
                // it must have been stopped.
                Slogf.i(TAG_AM, "empty activity list");
                stopMonitoringEvents();
                return false;
            }
            if (DBG) {
                Slogf.d(TAG_AM, "Visible Tasks: %d", infos.size());
            }
            for (int i = 0, size = infos.size(); i < size; ++i) {
                TaskInfo taskInfo = infos.get(i);
                int taskDisplayId = TaskInfoHelper.getDisplayId(taskInfo);
                RunningActivityInfo activityInfo = mRunningActivities.get(taskDisplayId);
                if (activityInfo == null) {
                    continue;
                }
                if (DBG) {
                    Slogf.i(TAG_AM, "Task#%d: U%d top=%s orig=%s", i, activityInfo.userId,
                            taskInfo.topActivity, taskInfo.origActivity);
                }
                int taskUserId = TaskInfoHelper.getUserId(taskInfo);
                ComponentName expectedTopActivity = activityInfo.intent.getComponent();
                if ((expectedTopActivity.equals(taskInfo.topActivity)
                        || expectedTopActivity.equals(taskInfo.origActivity))  // for Activity-alias
                        && activityInfo.userId == taskUserId) {
                    // top one is matching.
                    activityInfo.isVisible = true;
                    activityInfo.taskId = taskInfo.taskId;
                    continue;
                }
                activityInfo.previousTaskId = taskInfo.taskId;
                Slogf.i(TAG_AM, "Unmatched top activity will be removed:"
                        + taskInfo.topActivity + " top task id:" + activityInfo.previousTaskId
                        + " user:" + taskUserId + " display:" + taskDisplayId);
                activityInfo.inBackground = expectedTopActivity.equals(taskInfo.baseActivity);
                if (!activityInfo.inBackground) {
                    activityInfo.taskId = INVALID_TASK_ID;
                }
            }

            for (int i = 0; i < mRunningActivities.size(); i++) {
                RunningActivityInfo activityInfo = mRunningActivities.valueAt(i);
                long timeSinceLastLaunchMs = now - activityInfo.lastLaunchTimeMs;
                if (activityInfo.isVisible && activityInfo.isStarted) {
                    if (timeSinceLastLaunchMs >= CRASH_FORGET_INTERVAL_MS) {
                        activityInfo.consecutiveRetries = 0;
                    }
                    continue;
                }
                if (!isComponentAvailable(activityInfo.intent.getComponent(),
                        activityInfo.userId)) {
                    continue;
                }
                // For 1st call (consecutiveRetries == 0), do not wait as there can be no posting
                // for recheck.
                if (activityInfo.consecutiveRetries > 0 && (timeSinceLastLaunchMs
                        < RECHECK_INTERVAL_MS)) {
                    // wait until next check interval comes.
                    continue;
                }
                if (activityInfo.consecutiveRetries >= MAX_NUMBER_OF_CONSECUTIVE_CRASH_RETRY) {
                    // re-tried too many times, give up for now.
                    if (!activityInfo.failureLogged) {
                        activityInfo.failureLogged = true;
                        Slogf.w(TAG_AM, "Too many relaunch failure of fixed activity:"
                                + activityInfo);
                    }
                    continue;
                }

                Slogf.i(TAG_AM, "Launching Activity for fixed mode. Intent:" + activityInfo.intent
                        + ",userId:" + UserHandle.of(activityInfo.userId) + ",displayId:"
                        + mRunningActivities.keyAt(i));
                // Increase retry count if task is not in background. In case like other app is
                // launched and the target activity is still in background, do not consider it
                // as retry.
                if (!activityInfo.inBackground) {
                    activityInfo.consecutiveRetries++;
                }
                try {
                    postRecheck(RECHECK_INTERVAL_MS);
                    postRecheck(CRASH_FORGET_INTERVAL_MS);
                    ContextHelper.startActivityAsUser(mContext, activityInfo.intent,
                            activityInfo.activityOptions, UserHandle.of(activityInfo.userId));
                    activityInfo.isVisible = true;
                    activityInfo.isStarted = true;
                    activityInfo.lastLaunchTimeMs = SystemClock.elapsedRealtime();
                } catch (Exception e) { // Catch all for any app related issues.
                    Slogf.w(TAG_AM, "Cannot start activity:" + activityInfo.intent, e);
                }
            }
            RunningActivityInfo activityInfo = mRunningActivities.get(displayId);
            if (DBG) {
                Slogf.d(TAG_AM, "ActivityInfo for display %d: %s", displayId, activityInfo);
            }
            if (activityInfo == null) {
                return false;
            }
            return activityInfo.isVisible;
        }
    }

    @VisibleForTesting
    void launchIfNecessary() {
        launchIfNecessary(Display.INVALID_DISPLAY);
    }

    private void logComponentNotFound(ComponentName component, @UserIdInt int userId,
            Exception e) {
        Slogf.e(TAG_AM, "Specified Component not found:" + component
                + " for userid:" + userId, e);
    }

    private boolean isComponentAvailable(ComponentName component, @UserIdInt int userId) {
        PackageInfo packageInfo;
        try {
            packageInfo = PackageManagerHelper.getPackageInfoAsUser(mContext.getPackageManager(),
                    component.getPackageName(), PackageManager.GET_ACTIVITIES, userId);
        } catch (PackageManager.NameNotFoundException e) {
            logComponentNotFound(component, userId, e);
            return false;
        }
        if (packageInfo == null || packageInfo.activities == null) {
            // may not be necessary but additional safety check
            logComponentNotFound(component, userId, new RuntimeException());
            return false;
        }
        String fullName = component.getClassName();
        String shortName = component.getShortClassName();
        for (ActivityInfo info : packageInfo.activities) {
            if (info.name.equals(fullName) || info.name.equals(shortName)) {
                return true;
            }
        }
        logComponentNotFound(component, userId, new RuntimeException());
        return false;
    }

    private boolean isUserAllowedToLaunchActivity(@UserIdInt int userId) {
        int currentUser = ActivityManager.getCurrentUser();
        if (userId == currentUser || userId == UserHandle.SYSTEM.getIdentifier()) {
            return true;
        }
        List<UserHandle> profiles = mUserHandleHelper.getEnabledProfiles(currentUser);
        // null can happen in test env when UserManager is mocked. So this check is not necessary
        // in real env but add it to make test impl easier.
        if (profiles == null) {
            return false;
        }
        for (UserHandle profile : profiles) {
            if (profile.getIdentifier() == userId) {
                return true;
            }
        }
        return false;
    }

    private boolean isDisplayAllowedForFixedMode(int displayId) {
        if (displayId == Display.DEFAULT_DISPLAY || displayId == Display.INVALID_DISPLAY) {
            Slogf.w(TAG_AM, "Target display cannot be used for fixed mode, displayId:" + displayId,
                    new RuntimeException());
            return false;
        }
        return true;
    }

    @VisibleForTesting
    boolean hasRunningFixedActivity(int displayId) {
        synchronized (mLock) {
            return mRunningActivities.get(displayId) != null;
        }
    }

    private boolean launchForDisplay(int displayId) {
        Display display = mDm.getDisplay(displayId);
        // Skip launching the activity if the display is not ON. It can be launched later when
        // the display turns on, by the display listener.
        if (display != null && display.getState() != Display.STATE_ON) {
            if (DBG) {
                Slogf.d(TAG_AM, "Display %d is not on. The activity is not launched this time.",
                        displayId);
            }
            return false;
        }
        synchronized (mLock) {
            // Do nothing if there is no activity for the given display.
            if (!mRunningActivities.contains(displayId)) {
                return false;
            }
        }

        boolean launched = launchIfNecessary(displayId);
        if (launched) {
            startMonitoringEvents();
        } else {
            synchronized (mLock) {
                Slogf.w(TAG_AM, "Activity was not launched on display %d, and removed "
                        + " from the running activity list.", displayId);
                mRunningActivities.remove(displayId);
            }
        }
        return launched;
    }

    /**
     * Checks {@link InstrumentClusterRenderingService#startFixedActivityModeForDisplayAndUser(
     * Intent, ActivityOptions, int)}
     */
    public boolean startFixedActivityModeForDisplayAndUser(@NonNull Intent intent,
            @NonNull ActivityOptions options, int displayId, @UserIdInt int userId) {
        if (!isDisplayAllowedForFixedMode(displayId)) {
            return false;
        }
        if (options == null) {
            Slogf.e(TAG_AM, "startFixedActivityModeForDisplayAndUser, null options");
            return false;
        }
        if (!isUserAllowedToLaunchActivity(userId)) {
            Slogf.e(TAG_AM, "startFixedActivityModeForDisplayAndUser, requested user:" + userId
                    + " cannot launch activity, Intent:" + intent);
            return false;
        }
        ComponentName component = intent.getComponent();
        if (component == null) {
            Slogf.e(TAG_AM, "startFixedActivityModeForDisplayAndUser: No component specified for "
                    + "requested Intent" + intent);
            return false;
        }
        if (!isComponentAvailable(component, userId)) {
            return false;
        }
        Bundle optionsBundle = options.toBundle();
        synchronized (mLock) {
            RunningActivityInfo activityInfo = mRunningActivities.get(displayId);
            boolean replaceEntry = true;
            if (activityInfo != null && intentEquals(activityInfo.intent, intent)
                    && bundleEquals(optionsBundle, activityInfo.activityOptions)
                    && userId == activityInfo.userId) {
                replaceEntry = false;
                if (activityInfo.isVisible) { // already shown.
                    return true;
                }
            }
            if (replaceEntry) {
                activityInfo = new RunningActivityInfo(intent, optionsBundle, userId);
                mRunningActivities.put(displayId, activityInfo);
            }
        }

        return launchForDisplay(displayId);
    }

    /** Check {@link InstrumentClusterRenderingService#stopFixedActivityMode(int)} */
    public void stopFixedActivityMode(int displayId) {
        if (!isDisplayAllowedForFixedMode(displayId)) {
            return;
        }
        boolean stopMonitoringEvents = false;
        synchronized (mLock) {
            mRunningActivities.remove(displayId);
            if (mRunningActivities.size() == 0) {
                stopMonitoringEvents = true;
            }
        }
        if (stopMonitoringEvents) {
            stopMonitoringEvents();
        }
    }

    // Intent doesn't have the deep equals method.
    private static boolean intentEquals(Intent intent1, Intent intent2) {
        // both are null? return true
        if (intent1 == null && intent2 == null) {
            return true;
        }
        // Only one is null? return false
        if (intent1 == null || intent2 == null) {
            return false;
        }
        return intent1.getComponent().equals(intent2.getComponent())
                && bundleEquals(intent1.getExtras(), intent2.getExtras());
    }

    private static boolean bundleEquals(BaseBundle bundle1, BaseBundle bundle2) {
        // both are null? return true
        if (bundle1 == null && bundle2 == null) {
            return true;
        }
        // Only one is null? return false
        if (bundle1 == null || bundle2 == null) {
            return false;
        }
        if (bundle1.size() != bundle2.size()) {
            return false;
        }
        Set<String> keys = bundle1.keySet();
        for (String key : keys) {
            Object value1 = bundle1.get(key);
            Object value2 = bundle2.get(key);
            if (value1 != null && value1.getClass().isArray()
                    && value2 != null && value2.getClass().isArray()) {
                if (!arrayEquals(value1, value2)) {
                    return false;
                }
            } else if (value1 instanceof BaseBundle && value2 instanceof BaseBundle) {
                if (!bundleEquals((BaseBundle) value1, (BaseBundle) value2)) {
                    return false;
                }
            } else if (!Objects.equals(value1, value2)) {
                return false;
            }
        }
        return true;
    }

    private static boolean arrayEquals(Object value1, Object value2) {
        final int length = Array.getLength(value1);
        if (length != Array.getLength(value2)) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (!Objects.equals(Array.get(value1, i), Array.get(value2, i))) {
                return false;
            }
        }
        return true;
    }
}
