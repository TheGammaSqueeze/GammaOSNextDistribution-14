/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.cluster.home;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.car.CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER;
import static android.car.cluster.ClusterHomeManager.ClusterStateListener;
import static android.car.cluster.ClusterHomeManager.UI_TYPE_CLUSTER_HOME;
import static android.car.cluster.ClusterHomeManager.UI_TYPE_CLUSTER_NONE;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STARTING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;
import static android.content.Intent.ACTION_MAIN;
import static android.hardware.input.InputManager.INJECT_INPUT_EVENT_MODE_ASYNC;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.Application;
import android.app.IActivityTaskManager;
import android.app.TaskInfo;
import android.app.TaskStackListener;
import android.car.Car;
import android.car.CarAppFocusManager;
import android.car.CarAppFocusManager.OnAppFocusChangedListener;
import android.car.CarOccupantZoneManager;
import android.car.cluster.ClusterActivityState;
import android.car.cluster.ClusterHomeManager;
import android.car.cluster.ClusterState;
import android.car.input.CarInputManager;
import android.car.input.CarInputManager.CarInputCaptureCallback;
import android.car.user.CarUserManager;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.car.user.UserLifecycleEventFilter;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.ResolveInfoFlags;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArraySet;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

public final class ClusterHomeApplication extends Application {
    public static final String TAG = "ClusterHome";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    private static final int UI_TYPE_HOME = UI_TYPE_CLUSTER_HOME;
    private static final int UI_TYPE_MAPS = UI_TYPE_HOME + 1;
    private static final int UI_TYPE_MUSIC = UI_TYPE_HOME + 2;
    private static final int UI_TYPE_PHONE = UI_TYPE_HOME + 3;
    private static final int UI_TYPE_START = UI_TYPE_MAPS;

    private static final byte UI_UNAVAILABLE = 0;
    private static final byte UI_AVAILABLE = 1;

    private PackageManager mPackageManager;
    private UserManager mUserManager;
    private IActivityTaskManager mAtm;
    private InputManager mInputManager;
    private ClusterHomeManager mHomeManager;
    private CarUserManager mCarUserManager;
    private CarInputManager mCarInputManager;
    private CarAppFocusManager mAppFocusManager;
    private ClusterState mClusterState;
    private byte mUiAvailability[];
    private int mUserLifeCycleEvent = USER_LIFECYCLE_EVENT_TYPE_STARTING;

    private ArrayList<ComponentName> mClusterActivities = new ArrayList<>();
    private int mDefaultClusterActivitySize = 0;

    private int mLastLaunchedUiType = UI_TYPE_CLUSTER_NONE;
    private int mLastReportedUiType = UI_TYPE_CLUSTER_NONE;

    private boolean mIsLightMode = false;
    private boolean mIsInitialized = false;

    // Note that we use this callback to detect which cluster service mode (either FULL or LIGHT),
    // by looking at what cluster activity is being created. This is a hack to support both service
    // modes with a single sample application. In actual production scenarios, only one service
    // will be supported on a given device, thus there is no need for this callback mechanism.
    private final ActivityLifecycleCallbacks mActivityLifecycleCallbacks =
            new ActivityLifecycleCallbacks() {
                @Override
                public void onActivityPreCreated(Activity activity, Bundle savedInstanceState) {
                    // Set the mode based on the home activity class that is being created.
                    if (activity instanceof ClusterHomeActivityInterface) {
                        mIsLightMode =
                                ((ClusterHomeActivityInterface) activity).isClusterInLightMode();
                    }
                    // Initialize before the first activity is created.
                    if (!mIsInitialized) {
                        mIsInitialized = true;
                        initClusterHome();
                    }
                }

                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                }

                @Override
                public void onActivityStarted(Activity activity) {
                }

                @Override
                public void onActivityResumed(Activity activity) {
                }

                @Override
                public void onActivityPaused(Activity activity) {
                }

                @Override
                public void onActivityStopped(Activity activity) {
                }

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                }

                @Override
                public void onActivityDestroyed(Activity activity) {
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        mClusterActivities.add(UI_TYPE_HOME,
                new ComponentName(getApplicationContext(), ClusterHomeActivity.class));
        mClusterActivities.add(UI_TYPE_MAPS,
                ComponentName.unflattenFromString(getString(R.string.config_clusterMapActivity)));
        mClusterActivities.add(UI_TYPE_MUSIC,
                ComponentName.unflattenFromString(getString(R.string.config_clusterMusicActivity)));
        mClusterActivities.add(UI_TYPE_PHONE,
                ComponentName.unflattenFromString(getString(R.string.config_clusterPhoneActivity)));
        mDefaultClusterActivitySize = mClusterActivities.size();
        mPackageManager = getApplicationContext().getPackageManager();
        mUserManager = getApplicationContext().getSystemService(UserManager.class);
        mAtm = ActivityTaskManager.getService();
        try {
            mAtm.registerTaskStackListener(mTaskStackListener);
        } catch (RemoteException e) {
            Log.e(TAG, "remote exception from AM", e);
        }
        mInputManager = getApplicationContext().getSystemService(InputManager.class);

        Car.createCar(getApplicationContext(), /* handler= */ null,
                Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                (car, ready) -> {
                    if (!ready) return;
                    mHomeManager = (ClusterHomeManager) car.getCarManager(Car.CLUSTER_HOME_SERVICE);
                    mCarUserManager = (CarUserManager) car.getCarManager(Car.CAR_USER_SERVICE);
                    mCarInputManager = (CarInputManager) car.getCarManager(Car.CAR_INPUT_SERVICE);
                    mAppFocusManager = (CarAppFocusManager) car.getCarManager(
                            Car.APP_FOCUS_SERVICE);
                });

        registerActivityLifecycleCallbacks(mActivityLifecycleCallbacks);
    }

    private void initClusterHome() {
        Log.i(TAG, "initClusterHome() in " + (mIsLightMode ? "LIGHT" : "FULL") + " mode");
        if (mHomeManager == null) {
            Log.e(TAG, "ClusterHome is null (ClusterHomeService may not be enabled), "
                    + "Stopping ClusterHomeSample.");
            return;
        }
        // In the LIGHT mode, the HOME activity (DriverUI) takes care of everything, so we just
        // stay as the UI_TYPE_HOME, and do not need any logic to switch activities to different
        // types.
        if (mIsLightMode) {
            return;
        }

        mHomeManager.registerClusterStateListener(getMainExecutor(), mClusterHomeCallback);
        mClusterState = mHomeManager.getClusterState();
        if (!mClusterState.on) {
            mHomeManager.requestDisplay(UI_TYPE_HOME);
        }
        mUiAvailability = buildUiAvailability(ActivityManager.getCurrentUser());
        mHomeManager.reportState(mClusterState.uiType, UI_TYPE_CLUSTER_NONE, mUiAvailability);

        // Using the filter, only listens to the current user starting or unlocked events.
        UserLifecycleEventFilter filter = new UserLifecycleEventFilter.Builder()
                .addUser(UserHandle.CURRENT)
                .addEventType(USER_LIFECYCLE_EVENT_TYPE_STARTING)
                .addEventType(USER_LIFECYCLE_EVENT_TYPE_UNLOCKED).build();
        mCarUserManager.addListener(getMainExecutor(), filter, mUserLifecycleListener);
        if (mUserManager.isUserUnlocked(UserHandle.of(ActivityManager.getCurrentUser()))) {
            mUserLifeCycleEvent = USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;
        }

        mAppFocusManager.addFocusListener(mAppFocusChangedListener,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);

        int r = mCarInputManager.requestInputEventCapture(
                DISPLAY_TYPE_INSTRUMENT_CLUSTER,
                new int[]{CarInputManager.INPUT_TYPE_ALL_INPUTS},
                CarInputManager.CAPTURE_REQ_FLAGS_TAKE_ALL_EVENTS_FOR_DISPLAY,
                mInputCaptureCallback);
        if (r != CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED) {
            Log.e(TAG, "Failed to capture InputEvent on Cluster: r=" + r);
        }

        if (mClusterState.uiType != UI_TYPE_HOME) {
            startClusterActivity(mClusterState.uiType);
        }
    }

    @Override
    public void onTerminate() {
        if (!mIsLightMode) {
            mCarInputManager.releaseInputEventCapture(DISPLAY_TYPE_INSTRUMENT_CLUSTER);
            mCarUserManager.removeListener(mUserLifecycleListener);
            mHomeManager.unregisterClusterStateListener(mClusterHomeCallback);
            try {
                mAtm.unregisterTaskStackListener(mTaskStackListener);
            } catch (RemoteException e) {
                Log.e(TAG, "remote exception from AM", e);
            }
        }
        unregisterActivityLifecycleCallbacks(mActivityLifecycleCallbacks);
        super.onTerminate();
    }

    private void startClusterActivity(int uiType) {
        // Because ClusterHomeActivity runs as a user 0, so it can run in the locked state.
        if (uiType != UI_TYPE_HOME && mUserLifeCycleEvent != USER_LIFECYCLE_EVENT_TYPE_UNLOCKED) {
            Log.i(TAG, "Ignore to start Activity(" + uiType + ") during user-switching");
            return;
        }
        if (mClusterState == null || mClusterState.displayId == Display.INVALID_DISPLAY) {
            Log.w(TAG, "Cluster display is not ready");
            return;
        }

        // If this is the first activity to start, and the user is already unlocked,
        // use UI_TYPE_START activity instead of UI_TYPE_HOME activity.
        if (mLastLaunchedUiType == UI_TYPE_CLUSTER_NONE && uiType == UI_TYPE_HOME
                && mUserLifeCycleEvent == USER_LIFECYCLE_EVENT_TYPE_UNLOCKED) {
            Log.i(TAG, "Starting START UI instead of HOME UI, since user is already unlocked.");
            uiType = UI_TYPE_START;
        }
        mLastLaunchedUiType = uiType;
        ComponentName activity = mClusterActivities.get(uiType);

        Intent intent = new Intent(ACTION_MAIN).setComponent(activity);
        if (mClusterState.bounds != null && mClusterState.insets != null) {
            Rect unobscured = new Rect(mClusterState.bounds);
            unobscured.inset(mClusterState.insets);
            ClusterActivityState state = ClusterActivityState.create(mClusterState.on, unobscured);
            intent.putExtra(Car.CAR_EXTRA_CLUSTER_ACTIVITY_STATE, state.toBundle());
        }
        ActivityOptions options = ActivityOptions.makeBasic();

        // This sample assumes the Activities in this package are running as the system user,
        // and the other Activities are running as a current user.
        int userId = ActivityManager.getCurrentUser();
        if (getApplicationContext().getPackageName().equals(activity.getPackageName())) {
            userId = UserHandle.USER_SYSTEM;
        }
        mHomeManager.startFixedActivityModeAsUser(intent, options.toBundle(), userId);
    }

    private void add3PNavigationActivities(int currentUser) {
        // Clean up the 3P Navigations from the previous user.
        mClusterActivities.subList(mDefaultClusterActivitySize, mClusterActivities.size()).clear();

        ArraySet<String> clusterPackages = new ArraySet<>();
        for (int i = mDefaultClusterActivitySize - 1; i >= 0; --i) {
            clusterPackages.add(mClusterActivities.get(i).getPackageName());
        }
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Car.CAR_CATEGORY_NAVIGATION);
        List<ResolveInfo> resolveList = mPackageManager.queryIntentActivitiesAsUser(
                intent, ResolveInfoFlags.of(PackageManager.GET_RESOLVED_FILTER),
                UserHandle.of(currentUser));
        for (int i = resolveList.size() - 1; i >= 0; --i) {
            ActivityInfo activityInfo = resolveList.get(i).activityInfo;
            if (DBG) Log.d(TAG, "Found: " + activityInfo.packageName + "/" + activityInfo.name);
            // Some package can have multiple navigation Activities, we choose the default one only.
            if (clusterPackages.contains(activityInfo.packageName)) {
                if (DBG) {
                    Log.d(TAG, "Skip this, because another Activity in the package is registered.");
                };
                continue;
            }
            mClusterActivities.add(new ComponentName(activityInfo.packageName, activityInfo.name));
        }
        mUiAvailability = buildUiAvailability(currentUser);
    }

    private byte[] buildUiAvailability(int currentUser) {
        byte[] availability = new byte[mClusterActivities.size()];
        Intent intent = new Intent(ACTION_MAIN);
        for (int i = mClusterActivities.size() - 1; i >= 0; --i) {
            ComponentName clusterActivity = mClusterActivities.get(i);
            if (clusterActivity.getPackageName().equals(getPackageName())) {
                // Assume that all Activities in ClusterHome are available.
                availability[i] = UI_AVAILABLE;
                continue;
            }
            intent.setComponent(clusterActivity);
            ResolveInfo resolveInfo = mPackageManager.resolveActivityAsUser(
                    intent, PackageManager.MATCH_DEFAULT_ONLY, currentUser);
            availability[i] = resolveInfo == null ? UI_UNAVAILABLE : UI_AVAILABLE;
            if (DBG) {
                Log.d(TAG, "availability=" + availability[i] + ", activity=" + clusterActivity
                        + ", userId=" + currentUser);
            }
        }
        return availability;
    }

    private final ClusterStateListener mClusterHomeCallback = new ClusterStateListener() {
        @Override
        public void onClusterStateChanged(
                ClusterState state, @ClusterHomeManager.Config int changes) {
            if (DBG) {
                Log.d(TAG, "onClusterStateChanged: changes=" + Integer.toHexString(changes) +
                        ", state=" + clusterStateToString(state));
            }
            mClusterState = state;
            // We'll restart Activity when the display bounds or insets are changed, to let Activity
            // redraw itself to fit the changed attributes.
            if ((changes & ClusterHomeManager.CONFIG_DISPLAY_BOUNDS) != 0
                    || (changes & ClusterHomeManager.CONFIG_DISPLAY_INSETS) != 0
                    || ((changes & ClusterHomeManager.CONFIG_UI_TYPE) != 0
                            && mLastLaunchedUiType != state.uiType)) {
                startClusterActivity(state.uiType);
            }
        }
    };

    private final TaskStackListener mTaskStackListener = new TaskStackListener() {
        // onTaskMovedToFront isn't called when Activity-change happens within the same task.
        @Override
        public void onTaskStackChanged()  {
            getMainExecutor().execute(ClusterHomeApplication.this::handleTaskStackChanged);
        }
    };

    private void handleTaskStackChanged() {
        if (mClusterState == null || mClusterState.displayId == Display.INVALID_DISPLAY) {
            return;
        }
        TaskInfo taskInfo;
        try {
             taskInfo = mAtm.getRootTaskInfoOnDisplay(
                    WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_UNDEFINED, mClusterState.displayId);
        } catch (RemoteException e) {
            Log.e(TAG, "remote exception from AM", e);
            return;
        }
        if (taskInfo == null) {
            return;
        }
        int uiType = identifyTopTask(taskInfo);
        if (uiType == UI_TYPE_CLUSTER_NONE) {
            Log.w(TAG, "Unexpected top Activity on Cluster: " + taskInfo.topActivity);
            return;
        }
        if (mLastReportedUiType == uiType) {
            // Don't report the same UI type repeatedly.
            return;
        }
        mLastReportedUiType = uiType;
        mHomeManager.reportState(uiType, UI_TYPE_CLUSTER_NONE, mUiAvailability);
    }

    private int identifyTopTask(TaskInfo taskInfo) {
        for (int i = mClusterActivities.size() - 1; i >=0; --i) {
            if (mClusterActivities.get(i).equals(taskInfo.topActivity)) {
                return i;
            }
        }
        return UI_TYPE_CLUSTER_NONE;
    }

    private final UserLifecycleListener mUserLifecycleListener = (event) -> {
        if (DBG) Log.d(TAG, "UserLifecycleListener.onEvent: event=" + event);

        mUserLifeCycleEvent = event.getEventType();
        if (mUserLifeCycleEvent == USER_LIFECYCLE_EVENT_TYPE_STARTING) {
            startClusterActivity(UI_TYPE_HOME);
        } else if (mUserLifeCycleEvent == USER_LIFECYCLE_EVENT_TYPE_UNLOCKED) {
            add3PNavigationActivities(event.getUserId());
            if (UI_TYPE_START != UI_TYPE_HOME) {
                startClusterActivity(UI_TYPE_START);
            }
        }
    };

    private final CarInputCaptureCallback mInputCaptureCallback = new CarInputCaptureCallback() {
        @Override
        public void onKeyEvents(@CarOccupantZoneManager.DisplayTypeEnum int targetDisplayType,
                List<KeyEvent> keyEvents) {
            keyEvents.forEach((keyEvent) -> onKeyEvent(keyEvent));
        }
    };

    private void onKeyEvent(KeyEvent keyEvent) {
        if (DBG) Log.d(TAG, "onKeyEvent: " + keyEvent);
        if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            if (keyEvent.getAction() != KeyEvent.ACTION_DOWN) return;
            int nextUiType;
            do {
                // Select the Cluster Activity within the preinstalled ones.
                nextUiType = mLastLaunchedUiType + 1;
                if (nextUiType >= mDefaultClusterActivitySize) nextUiType = 0;
            } while (mUiAvailability[nextUiType] == UI_UNAVAILABLE);
            startClusterActivity(nextUiType);
            return;
        }
        // Use Android InputManager to forward KeyEvent.
        mInputManager.injectInputEvent(keyEvent, INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    private OnAppFocusChangedListener mAppFocusChangedListener = new OnAppFocusChangedListener() {
        @Override
        public void onAppFocusChanged(int appType, boolean active) {
            if (!active || appType != CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION) {
                return;
            }
            int navigationUi = getFocusedNavigationUi();
            if (navigationUi != UI_TYPE_CLUSTER_NONE) {
                startClusterActivity(navigationUi);
            }
        }
    };

    private int getFocusedNavigationUi() {
        List<String> focusOwnerPackageNames = mAppFocusManager.getAppTypeOwner(
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
        if (focusOwnerPackageNames == null || focusOwnerPackageNames.isEmpty()) {
            Log.e(TAG, "Can't find the navigation owner");
            return UI_TYPE_CLUSTER_NONE;
        }
        for (int i = 0; i < focusOwnerPackageNames.size(); ++i) {
            String focusOwnerPackage = focusOwnerPackageNames.get(i);
            for (int j = mClusterActivities.size() - 1; j >= 0; --j) {
                if (mUiAvailability[j] == UI_UNAVAILABLE) {
                    continue;
                }
                if (mClusterActivities.get(j).getPackageName().equals(focusOwnerPackage)) {
                    if (DBG) {
                        Log.d(TAG, "Found focused NavigationUI: " + j
                                + ", package=" + focusOwnerPackage);
                    }
                    return j;
                }
            }
        }
        Log.e(TAG, "Can't find the navigation UI for "
                + String.join(", ", focusOwnerPackageNames) + ".");
        return UI_TYPE_CLUSTER_NONE;
    }

    private static String clusterStateToString(ClusterState state) {
        StringBuilder sb = new StringBuilder("ClusterState[");
        sb.append("on="); sb.append(state.on);
        if (state.bounds != null) {
            sb.append(", bounds="); sb.append(state.bounds);
        }
        if (state.insets != null) {
            sb.append(", insets="); sb.append(state.insets);
        }
        if (state.insets != null) {
            sb.append(", insets="); sb.append(state.insets);
        }
        sb.append(", uiType="); sb.append(state.uiType);
        sb.append(", displayId="); sb.append(state.displayId);
        sb.append(']');
        return sb.toString();
    }
}
