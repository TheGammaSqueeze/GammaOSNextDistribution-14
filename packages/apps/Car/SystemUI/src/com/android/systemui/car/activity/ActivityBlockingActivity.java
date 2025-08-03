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
package com.android.systemui.car.activity;

import android.app.Activity;
import android.app.ActivityManager;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.app.CarActivityManager;
import android.car.content.pm.CarPackageManager;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Slog;
import android.view.DisplayInfo;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.car.activity.blurredbackground.BlurredSurfaceRenderer;

import java.util.List;

/**
 * Default activity that will be launched when the current foreground activity is not allowed.
 * Additional information on blocked Activity should be passed as intent extras.
 */
public class ActivityBlockingActivity extends Activity {
    private static final int ACTIVITY_MONITORING_DELAY_MS = 1000;
    private static final String TAG = "BlockingActivity";
    private static final int EGL_CONTEXT_VERSION = 2;
    private static final int EGL_CONFIG_SIZE = 8;
    private static final int INVALID_TASK_ID = -1;
    private final Object mLock = new Object();

    private GLSurfaceView mGLSurfaceView;
    private BlurredSurfaceRenderer mSurfaceRenderer;
    private boolean mIsGLSurfaceSetup = false;

    private Car mCar;
    private CarUxRestrictionsManager mUxRManager;
    private CarPackageManager mCarPackageManager;
    private CarActivityManager mCarActivityManager;
    private CarOccupantZoneManager mCarOccupantZoneManager;

    private Button mExitButton;
    private Button mToggleDebug;

    private int mBlockedTaskId;
    private final Handler mHandler = new Handler();

    private final View.OnClickListener mOnExitButtonClickedListener =
            v -> {
                if (isExitOptionCloseApplication()) {
                    handleCloseApplication();
                } else {
                    handleRestartingTask();
                }
            };

    private final ViewTreeObserver.OnGlobalLayoutListener mOnGlobalLayoutListener =
            new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mToggleDebug.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    updateButtonWidths();
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocking);

        mExitButton = findViewById(R.id.exit_button);

        // Listen to the CarUxRestrictions so this blocking activity can be dismissed when the
        // restrictions are lifted.
        // This Activity should be launched only after car service is initialized. Currently this
        // Activity is only launched from CPMS. So this is safe to do.
        mCar = Car.createCar(this, /* handler= */ null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                (car, ready) -> {
                    if (!ready) {
                        return;
                    }
                    mCarPackageManager = (CarPackageManager) car.getCarManager(
                            Car.PACKAGE_SERVICE);
                    mCarActivityManager = (CarActivityManager) car.getCarManager(
                            Car.CAR_ACTIVITY_SERVICE);
                    mUxRManager = (CarUxRestrictionsManager) car.getCarManager(
                            Car.CAR_UX_RESTRICTION_SERVICE);
                    mCarOccupantZoneManager = car.getCarManager(CarOccupantZoneManager.class);
                    // This activity would have been launched only in a restricted state.
                    // But ensuring when the service connection is established, that we are still
                    // in a restricted state.
                    handleUxRChange(mUxRManager.getCurrentCarUxRestrictions());
                    mUxRManager.registerListener(ActivityBlockingActivity.this::handleUxRChange);
                });

        setupGLSurface();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mIsGLSurfaceSetup) {
            mGLSurfaceView.onResume();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Display info about the current blocked activity, and optionally show an exit button
        // to restart the blocked task (stack of activities) if its root activity is DO.
        mBlockedTaskId = getIntent().getIntExtra(
                CarPackageManager.BLOCKING_INTENT_EXTRA_BLOCKED_TASK_ID,
                INVALID_TASK_ID);

        // blockedActivity is expected to be always passed in as the topmost activity of task.
        String blockedActivity = getIntent().getStringExtra(
                CarPackageManager.BLOCKING_INTENT_EXTRA_BLOCKED_ACTIVITY_NAME);
        if (!TextUtils.isEmpty(blockedActivity)) {
            boolean finished = finishIfActivitiesAreDistractionOptimised();
            if (finished) {
                return;
            }

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, "Blocking activity " + blockedActivity);
            }
        }

        displayExitButton();

        // Show more debug info for non-user build.
        if (Build.IS_ENG || Build.IS_USERDEBUG) {
            displayDebugInfo();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mIsGLSurfaceSetup) {
            // We queue this event so that it runs on the Rendering thread
            mGLSurfaceView.queueEvent(() -> mSurfaceRenderer.onPause());

            mGLSurfaceView.onPause();
        }

        // Finish when blocking activity goes invisible to avoid it accidentally re-surfaces with
        // stale string regarding blocked activity.
        finish();
    }

    private boolean finishIfActivitiesAreDistractionOptimised() {
        if (areAllVisibleActivitiesDistractionOptimised()) {
            Slog.i(TAG, "All visible activities are already DO, so finishing");
            finish();
            return true;
        }
        mHandler.postDelayed(() -> finishIfActivitiesAreDistractionOptimised(),
                ACTIVITY_MONITORING_DELAY_MS);
        return false;
    }

    private void setupGLSurface() {
        DisplayManager displayManager = (DisplayManager) getApplicationContext().getSystemService(
                Context.DISPLAY_SERVICE);
        DisplayInfo displayInfo = new DisplayInfo();

        int displayId = getDisplayId();
        displayManager.getDisplay(displayId).getDisplayInfo(displayInfo);

        Rect windowRect = getAppWindowRect();

        mSurfaceRenderer = new BlurredSurfaceRenderer(this, windowRect, getDisplayId());

        mGLSurfaceView = findViewById(R.id.blurred_surface_view);
        mGLSurfaceView.setEGLContextClientVersion(EGL_CONTEXT_VERSION);

        // Sets up the surface so that we can make it translucent if needed
        mGLSurfaceView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        mGLSurfaceView.setEGLConfigChooser(EGL_CONFIG_SIZE, EGL_CONFIG_SIZE, EGL_CONFIG_SIZE,
                EGL_CONFIG_SIZE, EGL_CONFIG_SIZE, EGL_CONFIG_SIZE);

        mGLSurfaceView.setRenderer(mSurfaceRenderer);

        // We only want to render the screen once
        mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        mIsGLSurfaceSetup = true;
    }

    /**
     * Computes a Rect that represents the portion of the screen that contains the activity that is
     * being blocked.
     *
     * @return Rect that represents the application window
     */
    private Rect getAppWindowRect() {
        Insets systemBarInsets = getWindowManager()
                .getCurrentWindowMetrics()
                .getWindowInsets()
                .getInsets(WindowInsets.Type.systemBars());

        Rect displayBounds = getWindowManager().getCurrentWindowMetrics().getBounds();

        int leftX = systemBarInsets.left;
        int rightX = displayBounds.width() - systemBarInsets.right;
        int topY = systemBarInsets.top;
        int bottomY = displayBounds.height() - systemBarInsets.bottom;

        return new Rect(leftX, topY, rightX, bottomY);
    }

    private void displayExitButton() {
        String exitButtonText = getExitButtonText();

        mExitButton.setText(exitButtonText);
        mExitButton.setOnClickListener(mOnExitButtonClickedListener);
    }

    // If the root activity is DO, the user will have the option to go back to that activity,
    // otherwise, the user will have the option to close the blocked application
    private boolean isExitOptionCloseApplication() {
        boolean isRootDO = getIntent().getBooleanExtra(
                CarPackageManager.BLOCKING_INTENT_EXTRA_IS_ROOT_ACTIVITY_DO, false);
        return mBlockedTaskId == INVALID_TASK_ID || !isRootDO;
    }

    private String getExitButtonText() {
        return isExitOptionCloseApplication() ? getString(R.string.exit_button_close_application)
                : getString(R.string.exit_button_go_back);
    }

    /**
     * It is possible that the stack info has changed between when the intent to launch this
     * activity was initiated and when this activity is started. Check whether all the visible
     * activities are distraction optimized.
     */
    private boolean areAllVisibleActivitiesDistractionOptimised() {
        List<ActivityManager.RunningTaskInfo> visibleTasks;
        visibleTasks = mCarActivityManager.getVisibleTasks();
        for (int i = visibleTasks.size() - 1; i >= 0; i--) {
            ActivityManager.RunningTaskInfo taskInfo = visibleTasks.get(i);
            if (taskInfo.displayId != getDisplayId()) {
                // ignore stacks on other displays
                continue;
            }

            if (getComponentName().equals(taskInfo.topActivity)) {
                // skip the ActivityBlockingActivity itself
                continue;
            }

            if (taskInfo.topActivity != null) {
                boolean isDo = mCarPackageManager.isActivityDistractionOptimized(
                        taskInfo.topActivity.getPackageName(),
                        taskInfo.topActivity.getClassName());
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Slog.d(TAG,
                            String.format("Activity (%s) is DO: %s", taskInfo.topActivity, isDo));
                }
                if (!isDo) {
                    return false;
                }
            }
        }

        // No visible non-DO activity found.
        return true;
    }

    private void displayDebugInfo() {
        String blockedActivity = getIntent().getStringExtra(
                CarPackageManager.BLOCKING_INTENT_EXTRA_BLOCKED_ACTIVITY_NAME);
        String rootActivity = getIntent().getStringExtra(
                CarPackageManager.BLOCKING_INTENT_EXTRA_ROOT_ACTIVITY_NAME);

        TextView debugInfo = findViewById(R.id.debug_info);
        debugInfo.setText(getDebugInfo(blockedActivity, rootActivity));

        // We still want to ensure driving safety for non-user build;
        // toggle visibility of debug info with this button.
        mToggleDebug = findViewById(R.id.toggle_debug_info);
        mToggleDebug.setVisibility(View.VISIBLE);
        mToggleDebug.setOnClickListener(v -> {
            boolean isDebugVisible = debugInfo.getVisibility() == View.VISIBLE;
            debugInfo.setVisibility(isDebugVisible ? View.GONE : View.VISIBLE);
        });

        mToggleDebug.getViewTreeObserver().addOnGlobalLayoutListener(mOnGlobalLayoutListener);
    }

    // When the Debug button is visible, we set both of the visible buttons to have the width
    // of whichever button is wider
    private void updateButtonWidths() {
        Button debugButton = findViewById(R.id.toggle_debug_info);

        int exitButtonWidth = mExitButton.getWidth();
        int debugButtonWidth = debugButton.getWidth();

        if (exitButtonWidth > debugButtonWidth) {
            debugButton.setWidth(exitButtonWidth);
        } else {
            mExitButton.setWidth(debugButtonWidth);
        }
    }

    private String getDebugInfo(String blockedActivity, String rootActivity) {
        StringBuilder debug = new StringBuilder();

        ComponentName blocked = ComponentName.unflattenFromString(blockedActivity);
        debug.append("Blocked activity is ")
                .append(blocked.getShortClassName())
                .append("\nBlocked activity package is ")
                .append(blocked.getPackageName());

        if (rootActivity != null) {
            ComponentName root = ComponentName.unflattenFromString(rootActivity);
            // Optionally show root activity info if it differs from the blocked activity.
            if (!root.equals(blocked)) {
                debug.append("\n\nRoot activity is ").append(root.getShortClassName());
            }
            if (!root.getPackageName().equals(blocked.getPackageName())) {
                debug.append("\nRoot activity package is ").append(root.getPackageName());
            }
        }
        return debug.toString();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCar.disconnect();
        mUxRManager.unregisterListener();
        if (mToggleDebug != null) {
            mToggleDebug.getViewTreeObserver().removeOnGlobalLayoutListener(
                    mOnGlobalLayoutListener);
        }
        mHandler.removeCallbacksAndMessages(null);
        mCar.disconnect();
    }

    // If no distraction optimization is required in the new restrictions, then dismiss the
    // blocking activity (self).
    private void handleUxRChange(CarUxRestrictions restrictions) {
        if (restrictions == null) {
            return;
        }
        if (!restrictions.isRequiresDistractionOptimization()) {
            finish();
        }
    }

    private void handleCloseApplication() {
        if (isFinishing()) {
            return;
        }

        int displayId = getDisplayId();
        int userOnDisplay = mCarOccupantZoneManager.getUserForDisplayId(displayId);
        if (userOnDisplay == CarOccupantZoneManager.INVALID_USER_ID) {
            Slog.e(TAG, "can not find user on display " + displayId
                    + " to start Home");
            finish();
        }

        Intent startMain = new Intent(Intent.ACTION_MAIN);

        int driverDisplayId = mCarOccupantZoneManager.getDisplayIdForDriver(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, String.format("display id: %d, driver display id: %d",
                    displayId, driverDisplayId));
        }
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityAsUser(startMain, UserHandle.of(userOnDisplay));
        finish();
    }

    private void handleRestartingTask() {
        // Lock on self to avoid restarting the same task twice.
        synchronized (mLock) {
            if (isFinishing()) {
                return;
            }

            if (Log.isLoggable(TAG, Log.INFO)) {
                Slog.i(TAG, "Restarting task " + mBlockedTaskId);
            }
            mCarPackageManager.restartTask(mBlockedTaskId);
            finish();
        }
    }
}
