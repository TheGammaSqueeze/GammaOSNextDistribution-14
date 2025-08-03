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

package com.android.car.portraitlauncher.homeactivities;

import static android.content.pm.ActivityInfo.CONFIG_UI_MODE;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY;
import static android.window.DisplayAreaOrganizer.FEATURE_DEFAULT_TASK_CONTAINER;

import static com.android.car.caruiportrait.common.service.CarUiPortraitService.INTENT_EXTRA_IMMERSIVE_MODE_REQUESTED_SOURCE;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.MSG_APP_GRID_VISIBILITY_CHANGE;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.MSG_COLLAPSE_NOTIFICATION;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.MSG_COLLAPSE_RECENTS;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.MSG_FG_TASK_VIEW_READY;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.MSG_HIDE_SYSTEM_BAR_FOR_IMMERSIVE;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.MSG_IMMERSIVE_MODE_CHANGE;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.MSG_IMMERSIVE_MODE_REQUESTED;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.MSG_NOTIFICATIONS_VISIBILITY_CHANGE;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.MSG_RECENTS_VISIBILITY_CHANGE;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.MSG_SUW_IN_PROGRESS;
import static com.android.car.portraitlauncher.panel.TaskViewPanelStateChangeReason.ON_ACTIVITY_RESTART_ATTEMPT;
import static com.android.car.portraitlauncher.panel.TaskViewPanelStateChangeReason.ON_CALM_MODE_STARTED;
import static com.android.car.portraitlauncher.panel.TaskViewPanelStateChangeReason.ON_COLLAPSE_MSG;
import static com.android.car.portraitlauncher.panel.TaskViewPanelStateChangeReason.ON_DRIVE_STATE_CHANGED;
import static com.android.car.portraitlauncher.panel.TaskViewPanelStateChangeReason.ON_HOME_INTENT;
import static com.android.car.portraitlauncher.panel.TaskViewPanelStateChangeReason.ON_HOME_SCREEN_LAYOUT_CHANGED;
import static com.android.car.portraitlauncher.panel.TaskViewPanelStateChangeReason.ON_IMMERSIVE_REQUEST;
import static com.android.car.portraitlauncher.panel.TaskViewPanelStateChangeReason.ON_MEDIA_INTENT;
import static com.android.car.portraitlauncher.panel.TaskViewPanelStateChangeReason.ON_PANEL_STATE_CHANGE_END;
import static com.android.car.portraitlauncher.panel.TaskViewPanelStateChangeReason.ON_SUW_STATE_CHANGED;
import static com.android.car.portraitlauncher.panel.TaskViewPanelStateChangeReason.ON_TASK_INFO_CHANGED;
import static com.android.car.portraitlauncher.panel.TaskViewPanelStateChangeReason.ON_TASK_MOVED_TO_FRONT;
import static com.android.car.portraitlauncher.panel.TaskViewPanelStateChangeReason.ON_TASK_REMOVED;
import static com.android.car.portraitlauncher.panel.TaskViewPanelStateChangeReason.createReason;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.IActivityTaskManager;
import android.app.TaskInfo;
import android.app.TaskStackListener;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.hardware.input.InputManager;
import android.hardware.input.InputManagerGlobal;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.android.car.carlauncher.CarLauncher;
import com.android.car.carlauncher.CarLauncherUtils;
import com.android.car.carlauncher.homescreen.HomeCardModule;
import com.android.car.carlauncher.homescreen.audio.IntentHandler;
import com.android.car.carlauncher.homescreen.audio.media.MediaIntentRouter;
import com.android.car.carlauncher.taskstack.TaskStackChangeListeners;
import com.android.car.portraitlauncher.R;
import com.android.car.portraitlauncher.common.CarUiPortraitServiceManager;
import com.android.car.portraitlauncher.common.UserEventReceiver;
import com.android.car.portraitlauncher.panel.TaskViewPanel;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Set;

/**
 * This home screen has maps running in the background hosted in a TaskView. At the bottom, there
 * is a control bar view that will display information regarding media/dialer. Any other
 * application other than assistant voice activity will be launched in the rootTaskView that is
 * displayed in the lower half of the screen.
 *
 * —--------------------------------------------------------------------
 * |                              Status Bar                            |
 * —--------------------------------------------------------------------
 * |                                                                    |
 * |                                                                    |
 * |                                                                    |
 * |                            Navigation app                          |
 * |                        (BackgroundTask View)                       |
 * |                                                                    |
 * |                                                                    |
 * |                                                                    |
 * |                                                                    |
 * —--------------------------------------------------------------------
 * |                                                                    |
 * |                                                                    |
 * |                                                                    |
 * |                      App Space (Root Task View Panel)              |
 * |                      (This layer is above maps)                    |
 * |                                                                    |
 * |                                                                    |
 * |                                                                    |
 * |                                                                    |
 * —--------------------------------------------------------------------
 * |                          Control Bar                               |
 * |                                                                    |
 * —--------------------------------------------------------------------
 * |                             Nav Bar                                |
 * —--------------------------------------------------------------------
 *
 * In total this Activity has 3 TaskViews.
 * Background Task view:
 * - It only contains maps app.
 * - Maps app is manually started in this taskview.
 *
 * Root Task View (Part of Root Task View Panel):
 * - It acts as the default container. Which means all the apps will run inside it by default.
 *
 * Note: Root Task View Panel always overlap over the Background TaskView.
 *
 * Fullscreen Task View
 * - Used for voice assist applications
 */
public final class CarUiPortraitHomeScreen extends FragmentActivity {
    public static final String TAG = CarUiPortraitHomeScreen.class.getSimpleName();

    private static final boolean DBG = Build.IS_DEBUGGABLE;
    /** Identifiers for panels. */
    private static final int APP_GRID = 1;
    private static final int APPLICATION = 2;
    private static final int BACKGROUND = 3;
    private static final int FULLSCREEN = 4;
    private static final long IMMERSIVE_MODE_REQUEST_TIMEOUT = 500;
    private static final String SAVED_BACKGROUND_APP_COMPONENT_NAME =
            "SAVED_BACKGROUND_APP_COMPONENT_NAME";
    private static final IActivityTaskManager sActivityTaskManager =
            ActivityTaskManager.getService();
    private final UserEventReceiver mUserEventReceiver = new UserEventReceiver();
    private final Configuration mConfiguration = new Configuration();
    private int mStatusBarHeight;
    private FrameLayout mContainer;
    private LinearLayout mControlBarView;
    // All the TaskViews & corresponding helper instance variables.
    private TaskViewControllerWrapper mTaskViewControllerWrapper;
    private ViewGroup mBackgroundAppArea;
    private ViewGroup mFullScreenAppArea;
    private boolean mIsBackgroundTaskViewReady;
    private boolean mIsFullScreenTaskViewReady;
    private int mNavBarHeight;
    private boolean mIsSUWInProgress;
    private TaskCategoryManager mTaskCategoryManager;
    private TaskInfo mCurrentTaskInRootTaskView;
    private boolean mIsNotificationCenterOnTop;
    private boolean mIsRecentsOnTop;
    private boolean mIsAppGridOnTop;
    private boolean mIsCalmMode;
    private TaskInfoCache mTaskInfoCache;
    private TaskViewPanel mAppGridTaskViewPanel;
    private TaskViewPanel mRootTaskViewPanel;
    private SparseArray<ActivityManager.RunningTaskInfo> mRunningTaskInfoRecords;
    private final IntentHandler mMediaIntentHandler = new IntentHandler() {
        @Override
        public void handleIntent(Intent intent) {
            if (TaskCategoryManager.isMediaApp(mCurrentTaskInRootTaskView)
                    && mRootTaskViewPanel.isOpen()) {
                mRootTaskViewPanel.closePanel(createReason(ON_MEDIA_INTENT, intent.getComponent()));
                return;
            }

            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(getDisplay().getDisplayId());

            startActivity(intent, options.toBundle());
        }
    };
    /**
     * Only resize the size of rootTaskView when SUW is in progress. This is to resize the height of
     * rootTaskView after status bar hide on SUW start.
     */
    private final View.OnLayoutChangeListener mHomeScreenLayoutChangeListener =
            (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                int newHeight = bottom - top;
                int oldHeight = oldBottom - oldTop;
                if (oldHeight == newHeight) {
                    return;
                }
                logIfDebuggable("container height change from " + oldHeight + " to " + newHeight);
                if (mIsSUWInProgress) {
                    mRootTaskViewPanel.openFullScreenPanel(/* animated = */ false,
                            /* showToolBar = */ false, /* bottomAdjustment= */ 0,
                            createReason(ON_HOME_SCREEN_LAYOUT_CHANGED));
                }
            };
    private final View.OnLayoutChangeListener mControlBarOnLayoutChangeListener =
            (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (bottom == oldBottom && top == oldTop) {
                    return;
                }

                logIfDebuggable("Control Bar layout changed!");
                updateTaskViewInsets();
                updateBackgroundTaskViewInsets();
                updateObscuredTouchRegion();
            };

    private ComponentName mUnhandledImmersiveModeRequestComponent;
    private long mUnhandledImmersiveModeRequestTimestamp;
    private boolean mUnhandledImmersiveModeRequest;
    // This listener lets us know when actives are added and removed from any of the display regions
    // we care about, so we can trigger the opening and closing of the app containers as needed.
    private final TaskStackListener mTaskStackListener = new TaskStackListener() {
        @Override
        public void onTaskCreated(int taskId, ComponentName componentName) throws RemoteException {
            logIfDebuggable("On task created, task = " + taskId);
            if (componentName != null) {
                logIfDebuggable("On task created, task = " + taskId
                        + " componentName " + componentName);
            }
        }

        @Override
        public void onTaskFocusChanged(int taskId, boolean focused) {
            super.onTaskFocusChanged(taskId, focused);
            logIfDebuggable("On task focus changed, task = " + taskId);
            boolean hostFocused = taskId == getTaskId() && focused;
            if (hostFocused && mTaskViewControllerWrapper != null) {
                mTaskViewControllerWrapper.showEmbeddedTasks(new int[]{BACKGROUND, FULLSCREEN});
            }
        }

        @Override
        public void onTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo)
                throws RemoteException {
            logIfDebuggable("On task moved to front, task = " + taskInfo.taskId + ", cmp = "
                    + taskInfo.baseActivity + ", isVisible=" + taskInfo.isVisible);
            if (!mRootTaskViewPanel.isReady()) {
                logIfDebuggable("Root Task View is not ready yet.");
                if (!TaskCategoryManager.isHomeIntent(taskInfo)
                        && !mTaskCategoryManager.isBackgroundApp(taskInfo)
                        && !mTaskCategoryManager.isAppGridActivity(taskInfo)) {

                    cacheTask(taskInfo);
                }
                return;
            }

            adjustFullscreenSpacing(mTaskCategoryManager.isFullScreenActivity(taskInfo));

            mIsNotificationCenterOnTop = mTaskCategoryManager.isNotificationActivity(taskInfo);
            mIsRecentsOnTop = mTaskCategoryManager.isRecentsActivity(taskInfo);
            mIsAppGridOnTop = mTaskCategoryManager.isAppGridActivity(taskInfo);

            if (mTaskCategoryManager.isBackgroundApp(taskInfo)) {
                mTaskCategoryManager.setCurrentBackgroundApp(taskInfo.baseActivity);
                mIsCalmMode = mTaskCategoryManager.isCalmModeActivity(taskInfo);
                int windowInsetsType = WindowInsets.Type.systemBars();
                if (mIsCalmMode) {

                    if (mRootTaskViewPanel.isOpen()) {
                        mRootTaskViewPanel.closePanel(createReason(ON_CALM_MODE_STARTED,
                                taskInfo.taskId, getVisibleActivity(taskInfo)));
                    }
                    if (mAppGridTaskViewPanel.isOpen()) {
                        mRootTaskViewPanel.closePanel(createReason(ON_CALM_MODE_STARTED,
                                taskInfo.taskId, getVisibleActivity(taskInfo)));
                    }
                    setControlBarVisibility(/* isVisible = */ false, /* animate = */ true);
                    windowInsetsType = WindowInsets.Type.navigationBars();
                } else {
                    setControlBarVisibility(/* isVisible = */ true, /* animate = */ true);
                }
                notifySystemUI(MSG_HIDE_SYSTEM_BAR_FOR_IMMERSIVE, windowInsetsType);
                return;
            }

            if (shouldTaskShowOnRootTaskView(taskInfo)) {
                logIfDebuggable("Opening in root task view: " + taskInfo);
                mRootTaskViewPanel.setComponentName(getVisibleActivity(taskInfo));
                mCurrentTaskInRootTaskView = taskInfo;
                // Open immersive mode if there is unhandled immersive mode request.
                if (shouldOpenFullScreenPanel(taskInfo)) {
                    mRootTaskViewPanel.openFullScreenPanel(/* animated= */ true,
                            /* showToolBar= */ true, mNavBarHeight,
                            createReason(ON_TASK_MOVED_TO_FRONT, taskInfo.taskId,
                                    getVisibleActivity(taskInfo)));
                    setUnhandledImmersiveModeRequest(/* componentName= */ null, /* timestamp= */ 0,
                            /* requested= */ false);
                } else if (mAppGridTaskViewPanel.isOpen()) {
                    // Animate the root task view to expand on top of the app grid task view.
                    mRootTaskViewPanel.expandPanel(
                            createReason(ON_TASK_MOVED_TO_FRONT, taskInfo.taskId,
                                    getVisibleActivity(taskInfo)));
                } else {
                    // Open the root task view.
                    mRootTaskViewPanel.openPanel(/* animated = */ true,
                            createReason(ON_TASK_MOVED_TO_FRONT, taskInfo.taskId,
                                    getVisibleActivity(taskInfo)));
                }
            } else {
                logIfDebuggable("Not showing task in rootTaskView");
            }
        }

        /**
         * Called when a task is removed.
         * @param taskId id of the task.
         * @throws RemoteException
         */
        @Override
        public void onTaskRemoved(int taskId) throws RemoteException {
            super.onTaskRemoved(taskId);
            logIfDebuggable("onTaskRemoved taskId=" + taskId);
            // Notification center dies during the transition to deferred SUW, don't respond to it.
            if (mRootTaskViewPanel == null || mIsSUWInProgress) {
                return;
            }

            // It's possible that mTaskViewControllerWrapper still thinks it has one task but
            // that task is actually removed. This typically happens on deferred SUW quits.
            ActivityManager.RunningTaskInfo taskInfo = mTaskViewControllerWrapper.getRootTaskInfo();
            boolean isRootTaskViewEmpty = taskInfo == null || (mCurrentTaskInRootTaskView != null
                    && mCurrentTaskInRootTaskView.taskId == taskId);

            // Hide the root task view panel if it is empty.
            if (isRootTaskViewEmpty && mRootTaskViewPanel.isVisible()) {
                logIfDebuggable("Close panel as no task is available.");
                mRootTaskViewPanel.closePanel(/* animated= */ false,
                        createReason(ON_TASK_REMOVED, taskId));
            }
        }

        /**
         * Called whenever IActivityManager.startActivity is called on an activity that is already
         * running, but the task is either brought to the front or a new Intent is delivered to it.
         *
         * @param taskInfo information about the task the activity was relaunched into
         * @param homeTaskVisible whether or not the home task is visible
         * @param clearedTask whether or not the launch activity also cleared the task as a part of
         * starting
         * @param wasVisible whether the activity was visible before the restart attempt
         */
        @Override
        public void onActivityRestartAttempt(ActivityManager.RunningTaskInfo taskInfo,
                boolean homeTaskVisible, boolean clearedTask, boolean wasVisible)
                throws RemoteException {
            super.onActivityRestartAttempt(taskInfo, homeTaskVisible, clearedTask, wasVisible);

            logIfDebuggable("On Activity restart attempt, task = " + taskInfo.taskId + ", cmp ="
                    + taskInfo.baseActivity + " wasVisible = " + wasVisible);
            if (taskInfo.baseIntent == null || taskInfo.baseIntent.getComponent() == null) {
                return;
            }

            // TODO(b/314398373): find out if CTS can be satisfied without this.
            if (TaskCategoryManager.isMediaApp(taskInfo) || mAppGridTaskViewPanel.isOpen()) {
                mTaskViewControllerWrapper.updateTaskVisibility(/* visibility= */ true,
                        APPLICATION);
            } else if (mTaskCategoryManager.isAppGridActivity(taskInfo)) {
                mTaskViewControllerWrapper.updateTaskVisibility(/* visibility= */ true, APP_GRID);
            } else if (!wasVisible) {
                logIfDebuggable("return for not visible");
                return;
            }

            adjustFullscreenSpacing(mTaskCategoryManager.isFullScreenActivity(taskInfo));

            if (mTaskCategoryManager.isBackgroundApp(taskInfo)) {
                return;
            }

            if (mIsSUWInProgress) {
                return;
            }

            logIfDebuggable("Update UI state on app restart attempt");
            if (mTaskCategoryManager.isAppGridActivity(taskInfo)) {
                if (mRootTaskViewPanel.isAnimating()) {
                    mRootTaskViewPanel.closePanel(/* animated = */ false,
                            createReason(ON_ACTIVITY_RESTART_ATTEMPT, taskInfo.taskId,
                                    getVisibleActivity(taskInfo)));
                }

                // If the new task is an app grid then toggle the app grid panel:
                // 1 - Close the app grid panel if it is open.
                // 2 - Open the app grid panel if it is closed:
                //    a) If the root task view is open on top of the app grid then use a fade
                //       animation to hide the root task view panel and show the app grid panel.
                //    b) Otherwise, simply open the app grid panel.
                if (mAppGridTaskViewPanel.isOpen()) {
                    mAppGridTaskViewPanel.closePanel(
                            createReason(ON_ACTIVITY_RESTART_ATTEMPT, taskInfo.taskId,
                                    getVisibleActivity(taskInfo)));
                } else if (mRootTaskViewPanel.isOpen()) {
                    mAppGridTaskViewPanel.fadeInPanel(
                            createReason(ON_ACTIVITY_RESTART_ATTEMPT, taskInfo.taskId,
                                    getVisibleActivity(taskInfo)));
                    mRootTaskViewPanel.fadeOutPanel(
                            createReason(ON_ACTIVITY_RESTART_ATTEMPT, taskInfo.taskId,
                                    getVisibleActivity(taskInfo)));
                } else if (mRootTaskViewPanel.isFullScreen()) {
                    mAppGridTaskViewPanel.openPanel(
                            createReason(ON_ACTIVITY_RESTART_ATTEMPT, taskInfo.taskId,
                                    getVisibleActivity(taskInfo)));
                    mRootTaskViewPanel.closePanel(
                            createReason(ON_ACTIVITY_RESTART_ATTEMPT, taskInfo.taskId,
                                    getVisibleActivity(taskInfo)));
                } else {
                    mAppGridTaskViewPanel.openPanel(
                            createReason(ON_ACTIVITY_RESTART_ATTEMPT, taskInfo.taskId,
                                    getVisibleActivity(taskInfo)));
                }
            } else if (shouldTaskShowOnRootTaskView(taskInfo)) {
                mRootTaskViewPanel.setComponentName(getVisibleActivity(taskInfo));
                if (mAppGridTaskViewPanel.isAnimating() && mAppGridTaskViewPanel.isOpen()) {
                    mAppGridTaskViewPanel.openPanel(/* animated = */ false,
                            createReason(ON_ACTIVITY_RESTART_ATTEMPT, taskInfo.taskId,
                                    getVisibleActivity(taskInfo)));
                }

                // If the new task should be launched in the root task view panel:
                // 1 - Close the root task view panel if it is open and the task is notification
                //    center. Make sure the app grid panel is closed already in case we are
                //    interrupting a running animation.
                // 2 - Open the root task view panel if it is closed:
                //    a) If there is a fresh unhandled immersive mode request, open the root task
                //       view panel to full screen.
                //    b) If the app grid panel is already open then use an expand animation
                //       to open the root task view on top of the app grid task view.
                //    c) Otherwise, simply open the root task view panel.
                if (mRootTaskViewPanel.isOpen()
                        && mTaskCategoryManager.isNotificationActivity(taskInfo)) {
                    if (mAppGridTaskViewPanel.isOpen()) {
                        mAppGridTaskViewPanel.closePanel(/* animated = */ false,
                                createReason(ON_ACTIVITY_RESTART_ATTEMPT, taskInfo.taskId,
                                        getVisibleActivity(taskInfo)));
                    }
                    mRootTaskViewPanel.closePanel(
                            createReason(ON_ACTIVITY_RESTART_ATTEMPT, taskInfo.taskId,
                                    getVisibleActivity(taskInfo)));
                } else {
                    if (shouldOpenFullScreenPanel(taskInfo)) {
                        mRootTaskViewPanel.openFullScreenPanel(/* animated= */ true,
                                /* showToolBar= */ true, mNavBarHeight,
                                createReason(ON_ACTIVITY_RESTART_ATTEMPT, taskInfo.taskId,
                                        getVisibleActivity(taskInfo)));
                        setUnhandledImmersiveModeRequest(/* componentName= */ null,
                                /* timestamp= */ 0, /* requested= */ false);
                    } else if (mAppGridTaskViewPanel.isOpen()) {
                        mRootTaskViewPanel.expandPanel(
                                createReason(ON_ACTIVITY_RESTART_ATTEMPT, taskInfo.taskId,
                                        getVisibleActivity(taskInfo)));
                    } else {
                        mRootTaskViewPanel.openPanel(
                                createReason(ON_ACTIVITY_RESTART_ATTEMPT, taskInfo.taskId,
                                        getVisibleActivity(taskInfo)));
                    }
                }
            }
        }
    };
    private CarUiPortraitServiceManager mCarUiPortraitServiceManager;
    private CarUiPortraitDriveStateController mCarUiPortraitDriveStateController;
    private boolean mReceivedNewIntent;

    private static void logIfDebuggable(String message) {
        if (DBG) {
            Log.d(TAG, message);
        }
    }

    /**
     * Send both action down and up to be qualified as a back press. Set time for key events, so
     * they are not staled.
     */
    public static void sendVirtualBackPress() {
        long downEventTime = SystemClock.uptimeMillis();
        long upEventTime = downEventTime + 1;

        final KeyEvent keydown = new KeyEvent(downEventTime, downEventTime, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_BACK, /* repeat= */ 0, /* metaState= */ 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, /* scancode= */ 0, KeyEvent.FLAG_FROM_SYSTEM);
        final KeyEvent keyup = new KeyEvent(upEventTime, upEventTime, KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_BACK, /* repeat= */ 0, /* metaState= */ 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, /* scancode= */ 0, KeyEvent.FLAG_FROM_SYSTEM);

        InputManagerGlobal inputManagerGlobal = InputManagerGlobal.getInstance();
        inputManagerGlobal.injectInputEvent(keydown, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        inputManagerGlobal.injectInputEvent(keyup, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    private static int boolToInt(Boolean b) {
        return b ? 1 : 0;
    }

    private static boolean intToBool(int val) {
        return val == 1;
    }

    private void setFocusToBackgroundApp() {
        if (mCurrentTaskInRootTaskView == null) {
            return;
        }
        try {
            sActivityTaskManager.setFocusedTask(mCurrentTaskInRootTaskView.taskId);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to set focus on background app: ", e);
        }
    }

    private boolean shouldOpenFullScreenPanel(ActivityManager.RunningTaskInfo taskInfo) {
        return taskInfo.baseActivity != null
                && taskInfo.baseActivity.equals(mUnhandledImmersiveModeRequestComponent)
                && mUnhandledImmersiveModeRequest
                && System.currentTimeMillis() - mUnhandledImmersiveModeRequestTimestamp
                < IMMERSIVE_MODE_REQUEST_TIMEOUT;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getApplicationContext().getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            Intent launcherIntent = new Intent(this, CarLauncher.class);
            launcherIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launcherIntent);
            finish();
            return;
        }

        setContentView(R.layout.car_ui_portrait_launcher);

        registerUserEventReceiver();
        mRunningTaskInfoRecords = new SparseArray<>();
        mTaskCategoryManager = new TaskCategoryManager(getApplicationContext());
        if (savedInstanceState != null) {
            String savedBackgroundAppName = savedInstanceState.getString(
                    SAVED_BACKGROUND_APP_COMPONENT_NAME);
            if (savedBackgroundAppName != null) {
                mTaskCategoryManager.setCurrentBackgroundApp(
                        ComponentName.unflattenFromString(savedBackgroundAppName));
            }
        }

        mTaskInfoCache = new TaskInfoCache(getApplicationContext());

        // Make the window fullscreen as GENERIC_OVERLAYS are supplied to the background task view
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Activity is running fullscreen to allow background task to bleed behind status bar
        mNavBarHeight = getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.navigation_bar_height);
        logIfDebuggable("Navbar height: " + mNavBarHeight);
        mContainer = findViewById(R.id.container);
        setHomeScreenBottomPadding(mNavBarHeight);
        mContainer.addOnLayoutChangeListener(mHomeScreenLayoutChangeListener);

        mAppGridTaskViewPanel = findViewById(R.id.app_grid_panel);

        mRootTaskViewPanel = findViewById(R.id.application_panel);

        mStatusBarHeight = getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_height);

        mControlBarView = findViewById(R.id.control_bar_area);
        mControlBarView.addOnLayoutChangeListener(mControlBarOnLayoutChangeListener);

        // Setting as trusted overlay to let touches pass through.
        getWindow().addPrivateFlags(PRIVATE_FLAG_TRUSTED_OVERLAY);
        // To pass touches to the underneath task.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);

        mContainer.addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    boolean widthChanged = (right - left) != (oldRight - oldLeft);
                    boolean heightChanged = (bottom - top) != (oldBottom - oldTop);

                    if (widthChanged || heightChanged) {
                        onContainerDimensionsChanged();
                    }
                });

        // If we happen to be resurfaced into a multi display mode we skip launching content
        // in the activity view as we will get recreated anyway.
        if (isInMultiWindowMode() || isInPictureInPictureMode()) {
            return;
        }

        mCarUiPortraitServiceManager = new CarUiPortraitServiceManager(/* activity= */ this,
                new IncomingHandler());
        initializeCards();
        TaskStackChangeListeners.getInstance().registerTaskStackListener(mTaskStackListener);
        mCarUiPortraitDriveStateController = new CarUiPortraitDriveStateController(
                getApplicationContext());
        MediaIntentRouter.getInstance().registerMediaIntentHandler(mMediaIntentHandler);

        if (mTaskViewControllerWrapper == null) {
            mTaskViewControllerWrapper = new RemoteCarTaskViewControllerWrapperImpl(
                    /* activity= */ this,
                    this::onTaskViewControllerReady);
        }
    }

    private void onTaskViewControllerReady() {
        setUpRootTaskView();
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        mReceivedNewIntent = true;
        // This is done to handle the case where 'close' is tapped on ActivityBlockingActivity and
        // it navigates to the home app. It assumes that the currently display task will be
        // replaced with the home.
        // In this case, ABA is actually displayed inside launch-root-task. By closing the
        // root task view panel we make sure the app goes to the background.
        mRootTaskViewPanel.closePanel(createReason(ON_HOME_INTENT));
        // Close app grid to account for home key event
        mAppGridTaskViewPanel.closePanel(createReason(ON_HOME_INTENT));
    }

    private void registerUserEventReceiver() {
        UserEventReceiver.Callback callback = new UserEventReceiver.Callback() {
            @Override
            public void onUserSwitching() {
                logIfDebuggable("On user switching");
                if (!isFinishing()) {
                    finish();
                }
            }

            @Override
            public void onUserUnlock() {
                logIfDebuggable("On user unlock");
                initTaskViews();
            }
        };
        mUserEventReceiver.register(this, callback);
    }

    private void initializeCards() {
        Set<HomeCardModule> homeCardModules = new androidx.collection.ArraySet<>();
        for (String providerClassName : getResources().getStringArray(
                R.array.config_homeCardModuleClasses)) {
            try {
                long reflectionStartTime = System.currentTimeMillis();
                HomeCardModule cardModule = Class.forName(providerClassName).asSubclass(
                        HomeCardModule.class).getDeclaredConstructor().newInstance();

                cardModule.setViewModelProvider(new ViewModelProvider(/* owner= */ this));
                homeCardModules.add(cardModule);
                if (DBG) {
                    long reflectionTime = System.currentTimeMillis() - reflectionStartTime;
                    logIfDebuggable(
                            "Initialization of HomeCardModule class " + providerClassName
                                    + " took " + reflectionTime + " ms");
                }
            } catch (IllegalAccessException | InstantiationException
                     | ClassNotFoundException | InvocationTargetException
                     | NoSuchMethodException e) {
                Log.w(TAG, "Unable to create HomeCardProvider class " + providerClassName, e);
            }
        }
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        for (HomeCardModule cardModule : homeCardModules) {
            transaction.replace(cardModule.getCardResId(), cardModule.getCardView().getFragment());
        }
        transaction.commitNow();
    }

    private void collapseNotificationPanel() {
        if (mIsNotificationCenterOnTop) {
            mRootTaskViewPanel.closePanel(createReason(ON_COLLAPSE_MSG));
        }
    }

    private void collapseRecentsPanel() {
        if (mIsRecentsOnTop) {
            mRootTaskViewPanel.closePanel(createReason(ON_COLLAPSE_MSG));
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int diff = mConfiguration.updateFrom(newConfig);
        logIfDebuggable("onConfigurationChanged with diff =" + diff);
        if ((diff & CONFIG_UI_MODE) == 0) {
            return;
        }
        initializeCards();
        Drawable background =
                getResources().getDrawable(R.drawable.control_bar_background, getTheme());
        mControlBarView.setBackground(background);
        mRootTaskViewPanel.post(() -> mRootTaskViewPanel.refresh(getTheme()));
        mAppGridTaskViewPanel.post(() -> mAppGridTaskViewPanel.refresh(getTheme()));
        updateBackgroundTaskViewInsets();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mTaskCategoryManager.getCurrentBackgroundApp() == null) {
            return;
        }
        outState.putString(SAVED_BACKGROUND_APP_COMPONENT_NAME,
                mTaskCategoryManager.getCurrentBackgroundApp().flattenToString());
    }

    @Override
    protected void onDestroy() {
        mTaskViewControllerWrapper.onDestroy();
        mRootTaskViewPanel.onDestroy();
        mTaskCategoryManager.onDestroy();
        mUserEventReceiver.unregister();
        TaskStackChangeListeners.getInstance().unregisterTaskStackListener(mTaskStackListener);
        mCarUiPortraitServiceManager.onDestroy();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mTaskViewControllerWrapper.updateAllowListedActivities(BACKGROUND,
                mTaskCategoryManager.getBackgroundActivitiesList());
        mTaskViewControllerWrapper.updateAllowListedActivities(FULLSCREEN,
                mTaskCategoryManager.getFullScreenActivitiesList());

        mTaskViewControllerWrapper.showEmbeddedTasks(new int[]{BACKGROUND, FULLSCREEN});

        // the showEmbeddedTasks will make the task visible which will lead to opening of the panel
        // and that should be skipped for application panel  when the  home intent is sent. Because
        // that leads to CTS failures.
        if (mReceivedNewIntent) {
            mReceivedNewIntent = false;
        } else {
            mTaskViewControllerWrapper.showEmbeddedTasks(new int[]{APPLICATION});
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // This usually happens during user switch, SUW might starts before
        // SemiControlledCarTaskView get released. So manually clean the
        // SemiControlledCarTaskView allowlist to avoid null pointers.
        mTaskViewControllerWrapper.updateAllowListedActivities(BACKGROUND, List.of());
        mTaskViewControllerWrapper.updateAllowListedActivities(FULLSCREEN, List.of());
    }

    private boolean shouldTaskShowOnRootTaskView(TaskInfo taskInfo) {
        if (mIsSUWInProgress) {
            logIfDebuggable("Skip root task view state change during suw");
            return false;
        }
        if (taskInfo.baseIntent == null || taskInfo.baseIntent.getComponent() == null) {
            logIfDebuggable("Should not show on root task view since base intent is null");
            return false;
        }

        // fullscreen activities will open in a separate task view which will show on top most
        // z-layer, that should not change the state of the root task view.
        if (mTaskCategoryManager.isFullScreenActivity(taskInfo)) {
            logIfDebuggable("Should not show on root task view since task is full screen activity");
            return false;
        }

        if (mTaskCategoryManager.isAppGridActivity(taskInfo)) {
            logIfDebuggable("Don't open app grid activity on the root task view");
            return false;
        }

        if (mTaskCategoryManager.isBackgroundApp(taskInfo)) {
            logIfDebuggable("Should not show on root task view since task is background activity");
            // we don't want to change the state of the root task view when background
            // task are launched or brought to front.
            return false;
        }

        // Any task that does NOT meet all the below criteria should be ignored.
        // 1. displayAreaFeatureId should be FEATURE_DEFAULT_TASK_CONTAINER
        // 2. should be visible
        // 3. for the current user ONLY. System user launches some tasks on cluster that should
        //    not affect the state of the foreground DA
        // 4. any task that is manually defined to be ignored
        return taskInfo.displayAreaFeatureId == FEATURE_DEFAULT_TASK_CONTAINER
                && taskInfo.userId == ActivityManager.getCurrentUser()
                && !mTaskCategoryManager.shouldIgnoreOpeningForegroundDA(taskInfo);
    }

    private void cacheTask(ActivityManager.RunningTaskInfo taskInfo) {
        logIfDebuggable("Caching the task: " + taskInfo.taskId);
        if (mTaskInfoCache.cancelTask(taskInfo)) {
            boolean cached = mTaskInfoCache.cacheTask(taskInfo);
            logIfDebuggable("Task " + taskInfo.taskId + " is cached = " + cached);
        }
    }

    private void setControlBarSpacerVisibility(boolean isVisible) {
        if (mControlBarView == null) {
            return;
        }
        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) mControlBarView.getLayoutParams();
        params.setMargins(/* left= */ 0, /* top= */ 0, /* right= */ 0,
                isVisible ? mNavBarHeight : 0);
        mControlBarView.requestLayout();
    }

    private void setHomeScreenBottomPadding(int bottomPadding) {
        // Set padding instead of margin so the bottom area shows background of
        // car_ui_portrait_launcher during immersive mode without nav bar, and panel states are
        // calculated correctly.
        mContainer.setPadding(/* left= */ 0, /* top= */ 0, /* right= */ 0, bottomPadding);
    }

    private void adjustFullscreenSpacing(boolean isFullscreen) {
        if (mIsSUWInProgress) {
            logIfDebuggable("don't change spaceing during suw");
            return;
        }
        logIfDebuggable(isFullscreen
                ? "Adjusting screen spacing for fullscreen task view"
                : "Adjusting screen spacing for non-fullscreen task views");
        setControlBarSpacerVisibility(isFullscreen);
        setHomeScreenBottomPadding(isFullscreen ? 0 : mNavBarHeight);
        // Add offset to background app area to avoid background apps shift for full screen app.
        mBackgroundAppArea.setPadding(/* left= */ 0, /* top= */ 0, /* right= */ 0,
                isFullscreen ? mNavBarHeight : 0);
    }

    // TODO(b/275633095): Add test to verify the region is set correctly in each mode
    private void updateObscuredTouchRegion() {
        Rect controlBarRect = new Rect();
        mControlBarView.getBoundsOnScreen(controlBarRect);

        Rect appPanelGripBarRect = new Rect();
        mAppGridTaskViewPanel.getGripBarBounds(appPanelGripBarRect);

        Rect rootPanelGripBarRect = new Rect();
        mRootTaskViewPanel.getGripBarBounds(rootPanelGripBarRect);

        Rect navigationBarRect = new Rect(controlBarRect.left, controlBarRect.bottom,
                controlBarRect.right, mContainer.getMeasuredHeight());

        Rect statusBarRect = new Rect(controlBarRect.left, /* top= */ 0, controlBarRect.right,
                mIsSUWInProgress ? 0 : mStatusBarHeight);

        Region obscuredTouchRegion = new Region();
        if (!mRootTaskViewPanel.isFullScreen()) {
            obscuredTouchRegion.union(controlBarRect);
        }
        obscuredTouchRegion.union(navigationBarRect);
        logIfDebuggable("Update ObscuredTouchRegion for root and app grid" + obscuredTouchRegion);
        mTaskViewControllerWrapper.setObscuredTouchRegion(obscuredTouchRegion, APPLICATION);
        mTaskViewControllerWrapper.setObscuredTouchRegion(obscuredTouchRegion, APP_GRID);

        if (!mIsSUWInProgress) {
            obscuredTouchRegion.union(appPanelGripBarRect);
            obscuredTouchRegion.union(rootPanelGripBarRect);
            obscuredTouchRegion.union(statusBarRect);
        } else if (mRootTaskViewPanel.isVisible()) {
            obscuredTouchRegion.union(rootPanelGripBarRect);
            obscuredTouchRegion.union(appPanelGripBarRect);
        }
        logIfDebuggable(
                "Update ObscuredTouchRegion for background and fullscreen" + obscuredTouchRegion);
        mTaskViewControllerWrapper.setObscuredTouchRegion(obscuredTouchRegion, BACKGROUND);
        mTaskViewControllerWrapper.setObscuredTouchRegion(obscuredTouchRegion, FULLSCREEN);
    }

    private void updateBackgroundTaskViewInsets() {
        if (mBackgroundAppArea == null) {
            return;
        }

        int bottomOverlap = mControlBarView.getTop();
        if (mRootTaskViewPanel.isVisible()) {
            bottomOverlap = mRootTaskViewPanel.getTop();
        } else if (mAppGridTaskViewPanel.isVisible()) {
            bottomOverlap = mAppGridTaskViewPanel.getTop();
        }

        Rect appAreaBounds = new Rect();
        mBackgroundAppArea.getBoundsOnScreen(appAreaBounds);

        Rect bottomInsets = new Rect(appAreaBounds.left, bottomOverlap,
                appAreaBounds.right, appAreaBounds.bottom);
        Rect topInsets = new Rect(appAreaBounds.left, appAreaBounds.top, appAreaBounds.right,
                mStatusBarHeight);

        logIfDebuggable("Applying inset to backgroundTaskView bottom: " + bottomInsets + " top: "
                + topInsets);
        mTaskViewControllerWrapper.setSystemOverlayInsets(bottomInsets, topInsets, BACKGROUND);
    }

    private void updateFullScreenTaskViewInsets() {
        if (mFullScreenAppArea == null) {
            return;
        }

        Rect appAreaBounds = new Rect();
        mFullScreenAppArea.getBoundsOnScreen(appAreaBounds);

        Rect bottomInsets = new Rect(
                appAreaBounds.left,
                appAreaBounds.height() - mNavBarHeight,
                appAreaBounds.right,
                appAreaBounds.bottom);

        Rect topInsets = new Rect(appAreaBounds.left, appAreaBounds.top, appAreaBounds.right,
                mStatusBarHeight);

        logIfDebuggable("Applying inset to fullScreenTaskView bottom: " + bottomInsets + " top: "
                + topInsets);
        mTaskViewControllerWrapper.setSystemOverlayInsets(bottomInsets, topInsets, FULLSCREEN);
    }

    private void updateTaskViewInsets() {
        int bottom = mIsSUWInProgress ? 0 : mControlBarView.getHeight() + mNavBarHeight;
        Insets insets = Insets.of(/* left= */ 0, /* top= */ 0, /* right= */ 0, bottom);

        if (mRootTaskViewPanel != null) {
            mRootTaskViewPanel.setInsets(insets);
        }
        if (mAppGridTaskViewPanel != null) {
            mAppGridTaskViewPanel.setInsets(insets);
        }
        mTaskViewControllerWrapper.updateWindowBounds();
    }

    private void onContainerDimensionsChanged() {
        if (mRootTaskViewPanel != null) {
            mRootTaskViewPanel.onParentDimensionChanged();
        }

        if (mAppGridTaskViewPanel != null) {
            mAppGridTaskViewPanel.onParentDimensionChanged();
        }

        updateTaskViewInsets();
        mTaskViewControllerWrapper.updateWindowBounds();
    }

    private void setUpBackgroundTaskView() {
        mBackgroundAppArea = findViewById(R.id.background_app_area);

        TaskViewControllerWrapper.TaskViewCallback callback =
                new TaskViewControllerWrapper.TaskViewCallback() {
                    @Override
                    public void onTaskViewCreated(@NonNull SurfaceView taskView) {
                        logIfDebuggable("Background Task View is created");
                        taskView.setZOrderOnTop(false);
                        mBackgroundAppArea.addView(taskView);
                        mTaskViewControllerWrapper.setTaskView(taskView, BACKGROUND);
                    }

                    @Override
                    public void onTaskViewInitialized() {
                        logIfDebuggable("Background Task View is ready");
                        mIsBackgroundTaskViewReady = true;
                        startBackgroundActivities();
                        onTaskViewReadinessUpdated();
                        updateBackgroundTaskViewInsets();
                        registerOnBackgroundApplicationInstallUninstallListener();
                    }

                    @Override
                    public void onTaskViewReleased() {
                        logIfDebuggable("Background Task View is released");
                        mTaskViewControllerWrapper.setTaskView(/* taskView= */ null, BACKGROUND);
                        mIsBackgroundTaskViewReady = false;
                    }
                };

        mTaskViewControllerWrapper.createCarRootTaskView(callback, getDisplayId(),
                getMainExecutor(),
                mTaskCategoryManager.getBackgroundActivitiesList());
    }

    private void startBackgroundActivities() {
        logIfDebuggable("start background activities");
        Intent backgroundIntent = mTaskCategoryManager.getCurrentBackgroundApp() == null
                ? CarLauncherUtils.getMapsIntent(getApplicationContext())
                : (new Intent()).setComponent(mTaskCategoryManager.getCurrentBackgroundApp());

        Intent failureRecoveryIntent =
                BackgroundPanelBaseActivity.createIntent(getApplicationContext());

        Intent[] intents = {failureRecoveryIntent, backgroundIntent};

        startActivitiesInternal(intents);

        // Set the background app here to avoid recreating
        // CarUiPortraitHomeScreen in onTaskCreated
        mTaskCategoryManager.setCurrentBackgroundApp(backgroundIntent.getComponent());
    }

    /** Starts given {@code intents} in order. */
    private void startActivitiesInternal(Intent[] intents) {
        for (Intent intent : intents) {
            startActivityInternal(intent);
        }
    }

    /** Starts given {@code intent}. */
    private void startActivityInternal(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Failed to launch", e);
        }
    }

    private void registerOnBackgroundApplicationInstallUninstallListener() {
        mTaskCategoryManager.registerOnApplicationInstallUninstallListener(
                new TaskCategoryManager.OnApplicationInstallUninstallListener() {
                    @Override
                    public void onAppInstalled(String packageName) {
                        mTaskViewControllerWrapper.updateAllowListedActivities(BACKGROUND,
                                mTaskCategoryManager.getBackgroundActivitiesList());
                        mTaskViewControllerWrapper.updateAllowListedActivities(FULLSCREEN,
                                mTaskCategoryManager.getFullScreenActivitiesList());
                    }

                    @Override
                    public void onAppUninstall(String packageName) {
                        mTaskViewControllerWrapper.updateAllowListedActivities(BACKGROUND,
                                mTaskCategoryManager.getBackgroundActivitiesList());
                        mTaskViewControllerWrapper.updateAllowListedActivities(FULLSCREEN,
                                mTaskCategoryManager.getFullScreenActivitiesList());
                    }
                });
    }

    private void setControlBarVisibility(boolean isVisible, boolean animate) {
        float translationY = isVisible ? 0 : mContainer.getHeight() - mControlBarView.getTop();
        if (animate) {
            mControlBarView.animate().translationY(translationY).withEndAction(
                    () -> {
                        // TODO (b/316344351): Investigate why control bar does not re-appear
                        //                     without requestLayout()
                        mControlBarView.requestLayout();
                        updateObscuredTouchRegion();
                    });
        } else {
            mControlBarView.setTranslationY(translationY);
            updateObscuredTouchRegion();
        }
    }

    private void setUpAppGridTaskView() {
        mAppGridTaskViewPanel.setTag("AppGridPanel");

        TaskViewControllerWrapper.TaskViewCallback callback =
                new TaskViewControllerWrapper.TaskViewCallback() {
                    @Override
                    public void onTaskViewCreated(@NonNull SurfaceView taskView) {
                        logIfDebuggable("App grid Task View is created");
                        taskView.setZOrderOnTop(false);
                        mAppGridTaskViewPanel.setTaskView(taskView);
                        mTaskViewControllerWrapper.setTaskView(taskView, APP_GRID);
                        mTaskViewControllerWrapper.updateWindowBounds();
                    }

                    @Override
                    public void onTaskViewInitialized() {
                        logIfDebuggable("App grid Task View is ready");
                        mAppGridTaskViewPanel.setReady(/* isReady= */ true);
                        onTaskViewReadinessUpdated();
                        startActivity(CarLauncherUtils.getAppsGridIntent());
                    }

                    @Override
                    public void onTaskViewReleased() {
                        logIfDebuggable("AppGrid Task View is released");
                        mTaskViewControllerWrapper.setTaskView(/* taskView= */ null, APP_GRID);
                        mAppGridTaskViewPanel.setReady(/* isReady= */ false);
                    }
                };

        mTaskViewControllerWrapper.createCarRootTaskView(callback, getDisplayId(),
                getMainExecutor(),
                List.of(mTaskCategoryManager.getAppGridActivity()));

        mAppGridTaskViewPanel.setOnStateChangeListener(new TaskViewPanel.OnStateChangeListener() {
            @Override
            public void onStateChangeStart(TaskViewPanel.State oldState,
                    TaskViewPanel.State newState, boolean animated) {
                boolean isVisible = newState.isVisible();
                notifySystemUI(MSG_APP_GRID_VISIBILITY_CHANGE, boolToInt(isVisible));
                if (newState.isVisible() && newState != oldState) {
                    mTaskViewControllerWrapper.setWindowBounds(
                            mAppGridTaskViewPanel.getTaskViewBounds(newState), APP_GRID);
                }
            }

            @Override
            public void onStateChangeEnd(TaskViewPanel.State oldState,
                    TaskViewPanel.State newState, boolean animated) {
                updateObscuredTouchRegion();
                updateBackgroundTaskViewInsets();

                if (!newState.isVisible() && oldState != newState) {
                    mTaskViewControllerWrapper.setWindowBounds(
                            mAppGridTaskViewPanel.getTaskViewBounds(newState), APP_GRID);
                }
            }
        });
    }

    private boolean isAllTaskViewsReady() {
        return mRootTaskViewPanel.isReady() && mAppGridTaskViewPanel.isReady()
                && mIsBackgroundTaskViewReady && mIsFullScreenTaskViewReady;
    }

    /**
     * Initialize non-default taskviews. Proceed after both {@link TaskViewControllerWrapper} and
     * {@code
     * mRootTaskViewPanel} are ready.
     *
     * <p>Note: 1. After flashing device and FRX, {@link UserEventReceiver} doesn't receive
     * {@link  Intent.ACTION_USER_UNLOCKED}, but PackageManager already starts
     * resolving intent right after {@code mRootTaskViewPanel} is ready. So initialize
     * {@link RemoteCarTaskView}s directly. 2. For device boot later, PackageManager starts to
     * resolving intent after {@link Intent.ACTION_USER_UNLOCKED}, so wait
     * until {@link UserEventReceiver} notify {@link CarUiPortraitHomeScreen}.
     */
    private void initTaskViews() {
        if (!mTaskCategoryManager.isReady() || !mRootTaskViewPanel.isReady()
                || isAllTaskViewsReady()) {
            return;
        }

        // BackgroundTaskView and FullScreenTaskView are init with activities lists provided by
        // mTaskCategoryManager. mTaskCategoryManager needs refresh to get up-to-date activities
        // lists.
        mTaskCategoryManager.refresh();
        setUpBackgroundTaskView();
        setUpAppGridTaskView();
        setUpFullScreenTaskView();
    }

    private void onTaskViewReadinessUpdated() {
        if (!isAllTaskViewsReady()) {
            return;
        }
        logIfDebuggable("All task views are ready");
        updateObscuredTouchRegion();
        updateBackgroundTaskViewInsets();
        notifySystemUI(MSG_FG_TASK_VIEW_READY, boolToInt(true));
        Rect controlBarBounds = new Rect();
        mControlBarView.getBoundsOnScreen(controlBarBounds);
        boolean isControlBarVisible = mControlBarView.getVisibility() == View.VISIBLE;
        logIfDebuggable("Control bar:"
                + "( visible: " + isControlBarVisible
                + ", bounds:" + controlBarBounds
                + ")");

        mTaskInfoCache.startCachedTasks();
    }

    private void setUpRootTaskView() {
        mRootTaskViewPanel.setTag("RootPanel");
        mRootTaskViewPanel.setTaskViewBackgroundColor(Color.BLACK);
        mRootTaskViewPanel.setOnStateChangeListener(new TaskViewPanel.OnStateChangeListener() {
            @Override
            public void onStateChangeStart(TaskViewPanel.State oldState,
                    TaskViewPanel.State newState, boolean animated) {
                boolean isFullScreen = newState.isFullScreen();
                if (!mIsSUWInProgress) {
                    setControlBarVisibility(!isFullScreen, animated);
                    notifySystemUI(MSG_HIDE_SYSTEM_BAR_FOR_IMMERSIVE, isFullScreen
                            ? WindowInsets.Type.navigationBars()
                            : WindowInsets.Type.systemBars());
                }

                boolean isVisible = newState.isVisible();
                // Deselect the app grid button as soon as we know the root task view panel will
                // be shown on top.
                if (isVisible) {
                    notifySystemUI(MSG_APP_GRID_VISIBILITY_CHANGE, boolToInt(false));
                }

                if (mCurrentTaskInRootTaskView != null && isVisible) {
                    mTaskViewControllerWrapper.updateTaskVisibility(/* visibility= */ true,
                            APPLICATION);
                }

                // Update the notification button's selection state.
                if (mIsNotificationCenterOnTop && isVisible) {
                    notifySystemUI(MSG_NOTIFICATIONS_VISIBILITY_CHANGE, boolToInt(true));
                } else {
                    notifySystemUI(MSG_NOTIFICATIONS_VISIBILITY_CHANGE, boolToInt(false));
                }

                // Update the Recents button's selection state.
                if (mIsRecentsOnTop && isVisible) {
                    notifySystemUI(MSG_RECENTS_VISIBILITY_CHANGE, boolToInt(true));
                } else {
                    notifySystemUI(MSG_RECENTS_VISIBILITY_CHANGE, boolToInt(false));
                }

                if (newState.isVisible() && newState != oldState) {
                    mTaskViewControllerWrapper.setWindowBounds(
                            mRootTaskViewPanel.getTaskViewBounds(newState), APPLICATION);
                }
            }

            @Override
            public void onStateChangeEnd(TaskViewPanel.State oldState,
                    TaskViewPanel.State newState, boolean animated) {
                updateObscuredTouchRegion();
                // Hide the control bar after the animation if in full screen.
                if (newState.isFullScreen()) {
                    setControlBarVisibility(/* isVisible= */ false, animated);
                    updateObscuredTouchRegion();
                } else {
                    // Update the background task view insets to make sure their content is not
                    // covered with our panels. We only need to do this when we are not in
                    // fullscreen.
                    updateBackgroundTaskViewInsets();
                }

                // Hide the app grid task view behind the root task view.
                if (newState.isVisible()) {
                    mAppGridTaskViewPanel.closePanel(/* animated = */ false,
                            createReason(ON_PANEL_STATE_CHANGE_END));
                    mTaskViewControllerWrapper.moveToBack(APP_GRID);
                } else if (mCurrentTaskInRootTaskView != null && oldState.isVisible()) {
                    // hide the window of the task running in the root task view.
                    logIfDebuggable("hiding the window for task: " + mCurrentTaskInRootTaskView);
                    if (!mAppGridTaskViewPanel.isVisible()) {
                        mTaskViewControllerWrapper.moveToBack(APP_GRID);
                    }
                    mTaskViewControllerWrapper.moveToBack(APPLICATION);
                }
                if (!newState.isVisible() && oldState != newState) {
                    mTaskViewControllerWrapper.setWindowBounds(
                            mRootTaskViewPanel.getTaskViewBounds(newState), APPLICATION);
                }
            }
        });


        TaskViewControllerWrapper.TaskViewCallback callback =
                new TaskViewControllerWrapper.TaskViewCallback() {
                    @Override
                    public void onTaskViewCreated(@NonNull SurfaceView taskView) {
                        logIfDebuggable("Root Task View is created");
                        taskView.setZOrderMediaOverlay(true);
                        mRootTaskViewPanel.setTaskView(taskView);
                        mRootTaskViewPanel.setToolBarCallback(() -> sendVirtualBackPress());
                        mTaskViewControllerWrapper.setTaskView(taskView, APPLICATION);
                        mTaskViewControllerWrapper.updateWindowBounds();
                    }

                    @Override
                    public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
                        ActivityManager.RunningTaskInfo previousTaskInfoRecord =
                                mRunningTaskInfoRecords.get(
                                        taskInfo.taskId);
                        mRunningTaskInfoRecords.put(taskInfo.taskId, taskInfo);
                        logIfDebuggable("ON_TASK_INFO_CHANGED, taskInfo =" + taskInfo);
                        logIfDebuggable("ON_TASK_INFO_CHANGED, previousTaskInfoRecord ="
                                + previousTaskInfoRecord);

                        // Open the application panel only if the task info change from invisible
                        // to visible. This is only for opening CarSettings from QC panel, due to
                        // the launch flag, this action doesn't trigger onTaskMoveToFront. Normal
                        // apps should trigger the application panel open action w/
                        // onTaskMoveToFront, but not here.
                        if (taskInfo.isVisible() && previousTaskInfoRecord != null
                                && !previousTaskInfoRecord.isVisible()) {
                            mRootTaskViewPanel.openPanel(createReason(ON_TASK_INFO_CHANGED));
                        }
                    }

                    @Override
                    public void onTaskViewInitialized() {
                        logIfDebuggable("Root Task View is ready");
                        mRootTaskViewPanel.setReady(true);
                        onTaskViewReadinessUpdated();
                        initTaskViews();
                    }

                    @Override
                    public void onTaskViewReleased() {
                        logIfDebuggable("Root Task View is released");
                        mTaskViewControllerWrapper.setTaskView(/* taskView= */ null, APPLICATION);
                        mRootTaskViewPanel.setReady(/* isReady= */ false);
                    }
                };

        mTaskViewControllerWrapper.createCarDefaultRootTaskView(callback, getDisplayId(),
                getMainExecutor());
    }

    private void setUpFullScreenTaskView() {
        mFullScreenAppArea = findViewById(R.id.fullscreen_container);
        TaskViewControllerWrapper.TaskViewCallback callback =
                new TaskViewControllerWrapper.TaskViewCallback() {
                    @Override
                    public void onTaskViewCreated(@NonNull SurfaceView taskView) {
                        logIfDebuggable("FullScreen Task View is created");
                        taskView.setZOrderOnTop(true);
                        mFullScreenAppArea.addView(taskView);
                        mTaskViewControllerWrapper.setTaskView(taskView, FULLSCREEN);
                    }

                    @Override
                    public void onTaskViewInitialized() {
                        logIfDebuggable("FullScreen Task View is ready");
                        mIsFullScreenTaskViewReady = true;
                        onTaskViewReadinessUpdated();
                        updateFullScreenTaskViewInsets();
                    }

                    @Override
                    public void onTaskViewReleased() {
                        logIfDebuggable("FullScreen Task View is released");
                        mTaskViewControllerWrapper.setTaskView(/* taskView= */ null, FULLSCREEN);
                        mIsFullScreenTaskViewReady = false;
                    }
                };
        mTaskViewControllerWrapper.createCarRootTaskView(callback, getDisplayId(),
                getMainExecutor(),
                mTaskCategoryManager.getFullScreenActivitiesList().stream().toList());
    }

    private void onImmersiveModeRequested(boolean requested, ComponentName componentName) {
        logIfDebuggable("onImmersiveModeRequested = " + requested + " cmp=" + componentName);
        if (mIsSUWInProgress) {
            logIfDebuggable("skip immersive change during suw");
            return;
        }

        // Ignore the immersive mode request for app grid, since it's not in root task view panel.
        // Handle the app grid task in TaskStackListener.
        if (mTaskCategoryManager.isAppGridActivity(componentName)) {
            return;
        }

        if (mCarUiPortraitDriveStateController.isDrivingStateMoving()
                && mRootTaskViewPanel.isFullScreen()) {
            mRootTaskViewPanel.openPanel(createReason(ON_DRIVE_STATE_CHANGED, componentName));
            return;
        }

        // Only handles the immersive mode request here if requesting component has the same package
        // name as the current top task.
        if (!isPackageVisibleOnRootTask(componentName) || mIsAppGridOnTop) {
            // Save the component and timestamp of the latest immersive mode request, in case any
            // race condition with TaskStackListener.
            setUnhandledImmersiveModeRequest(componentName, System.currentTimeMillis(), requested);
            return;
        }

        if (requested) {
            mRootTaskViewPanel.openFullScreenPanel(/* animated= */ true, /* showToolBar= */ true,
                    mNavBarHeight, createReason(ON_IMMERSIVE_REQUEST, componentName));
        } else {
            mRootTaskViewPanel.openPanelWithIcon(createReason(ON_IMMERSIVE_REQUEST, componentName));
        }
    }

    private boolean isPackageVisibleOnRootTask(ComponentName componentName) {
        TaskInfo taskInfo = mCurrentTaskInRootTaskView;
        logIfDebuggable("Top task in launch root task is" + taskInfo);
        if (taskInfo == null) {
            return false;
        }

        ComponentName visibleComponentName = getVisibleActivity(taskInfo);

        return visibleComponentName != null
                && componentName.getPackageName().equals(visibleComponentName.getPackageName());
    }

    private ComponentName getVisibleActivity(TaskInfo taskInfo) {
        if (taskInfo.topActivity != null) {
            return taskInfo.topActivity;
        } else if (taskInfo.baseActivity != null) {
            return taskInfo.baseActivity;
        } else {
            return taskInfo.baseIntent.getComponent();
        }
    }

    private void setUnhandledImmersiveModeRequest(ComponentName componentName, long timestamp,
            boolean requested) {
        mUnhandledImmersiveModeRequestComponent = componentName;
        mUnhandledImmersiveModeRequestTimestamp = timestamp;
        mUnhandledImmersiveModeRequest = requested;
    }

    private ComponentName getComponentNameFromBundle(Bundle bundle) {
        String cmpString = bundle.getString(INTENT_EXTRA_IMMERSIVE_MODE_REQUESTED_SOURCE);
        return (cmpString == null)
                ? null
                : ComponentName.unflattenFromString(cmpString);
    }

    void notifySystemUI(int key, int value) {
        if (mCarUiPortraitServiceManager != null) {
            mCarUiPortraitServiceManager.notifySystemUI(key, value);
        }
    }

    /**
     * Handler of incoming messages from service.
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_IMMERSIVE_MODE_REQUESTED:
                    onImmersiveModeRequested(intToBool(msg.arg1),
                            getComponentNameFromBundle(msg.getData()));
                    break;
                case MSG_SUW_IN_PROGRESS:
                    mIsSUWInProgress = intToBool(msg.arg1);
                    logIfDebuggable("Get intent about the SUW is " + mIsSUWInProgress);
                    if (mIsSUWInProgress) {
                        mRootTaskViewPanel.openFullScreenPanel(/* animated= */false,
                                /* showToolBar= */ false, /* bottomAdjustment= */ 0,
                                createReason(ON_SUW_STATE_CHANGED));
                    } else {
                        mRootTaskViewPanel.closePanel(createReason(ON_SUW_STATE_CHANGED));
                    }
                    break;
                case MSG_IMMERSIVE_MODE_CHANGE:
                    boolean hideNavBar = intToBool(msg.arg1);
                    mRootTaskViewPanel.setToolBarViewVisibility(hideNavBar);
                    break;
                case MSG_COLLAPSE_NOTIFICATION:
                    collapseNotificationPanel();
                    break;
                case MSG_COLLAPSE_RECENTS:
                    collapseRecentsPanel();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }
}
