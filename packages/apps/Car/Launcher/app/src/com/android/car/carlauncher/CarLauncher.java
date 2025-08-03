/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.carlauncher;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.car.settings.CarSettings.Secure.KEY_USER_TOS_ACCEPTED;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY;

import static com.android.car.carlauncher.CarLauncherViewModel.CarLauncherViewModelFactory;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.TaskStackListener;
import android.car.Car;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.collection.ArraySet;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.android.car.carlauncher.homescreen.HomeCardModule;
import com.android.car.carlauncher.homescreen.audio.IntentHandler;
import com.android.car.carlauncher.homescreen.audio.media.MediaIntentRouter;
import com.android.car.carlauncher.taskstack.TaskStackChangeListeners;
import com.android.car.internal.common.UserHelperLite;
import com.android.wm.shell.taskview.TaskView;

import com.google.common.annotations.VisibleForTesting;

import java.util.Set;

/**
 * Basic Launcher for Android Automotive which demonstrates the use of {@link TaskView} to host
 * maps content and uses a Model-View-Presenter structure to display content in cards.
 *
 * <p>Implementations of the Launcher that use the given layout of the main activity
 * (car_launcher.xml) can customize the home screen cards by providing their own
 * {@link HomeCardModule} for R.id.top_card or R.id.bottom_card. Otherwise, implementations that
 * use their own layout should define their own activity rather than using this one.
 *
 * <p>Note: On some devices, the TaskView may render with a width, height, and/or aspect
 * ratio that does not meet Android compatibility definitions. Developers should work with content
 * owners to ensure content renders correctly when extending or emulating this class.
 */
public class CarLauncher extends FragmentActivity {
    public static final String TAG = "CarLauncher";
    public static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private ActivityManager mActivityManager;
    private Car mCar;
    private int mCarLauncherTaskId = INVALID_TASK_ID;
    private Set<HomeCardModule> mHomeCardModules;

    /** Set to {@code true} once we've logged that the Activity is fully drawn. */
    private boolean mIsReadyLogged;
    private boolean mUseSmallCanvasOptimizedMap;
    private ViewGroup mMapsCard;
    private CarLauncherViewModel mCarLauncherViewModel;

    @VisibleForTesting
    ContentObserver mTosContentObserver;

    private final TaskStackListener mTaskStackListener = new TaskStackListener() {
        @Override
        public void onTaskFocusChanged(int taskId, boolean focused) {
        }

        @Override
        public void onActivityRestartAttempt(ActivityManager.RunningTaskInfo task,
                boolean homeTaskVisible, boolean clearedTask, boolean wasVisible) {
            if (DEBUG) {
                Log.d(TAG, "onActivityRestartAttempt: taskId=" + task.taskId
                        + ", homeTaskVisible=" + homeTaskVisible + ", wasVisible=" + wasVisible);
            }
            if (!mUseSmallCanvasOptimizedMap
                    && !homeTaskVisible
                    && getTaskViewTaskId() == task.taskId) {
                // The embedded map component received an intent, therefore forcibly bringing the
                // launcher to the foreground.
                bringToForeground();
            }
        }
    };

    private final IntentHandler mMediaIntentHandler = new IntentHandler() {
        @Override
        public void handleIntent(Intent intent) {
            if (intent != null) {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLaunchDisplayId(getDisplay().getDisplayId());
                startActivity(intent, options.toBundle());
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (DEBUG) {
            Log.d(TAG, "onCreate(" + getUserId() + ") displayId=" + getDisplayId());
        }
        // Since MUMD is introduced, CarLauncher can be called in the main display of visible users.
        // In ideal shape, CarLauncher should handle both driver and passengers together.
        // But, in the mean time, we have separate launchers for driver and passengers, so
        // CarLauncher needs to reroute the request to Passenger launcher if it is invoked from
        // the main display of passengers (not driver).
        // For MUPAND, PassengerLauncher should be the default launcher.
        // For non-main displays, ATM will invoke SECONDARY_HOME Intent, so the secondary launcher
        // should handle them.
        UserManager um = getSystemService(UserManager.class);
        boolean isPassengerDisplay = getDisplayId() != Display.DEFAULT_DISPLAY
                || um.isVisibleBackgroundUsersOnDefaultDisplaySupported();
        if (isPassengerDisplay) {
            String passengerLauncherName = getString(R.string.config_passengerLauncherComponent);
            Intent passengerHomeIntent;
            if (!passengerLauncherName.isEmpty()) {
                ComponentName component = ComponentName.unflattenFromString(passengerLauncherName);
                if (component == null) {
                    throw new IllegalStateException(
                            "Invalid passengerLauncher name=" + passengerLauncherName);
                }
                passengerHomeIntent = new Intent(Intent.ACTION_MAIN)
                        // passenger launcher should be launched in home task in order to
                        // fix TaskView layering issue
                        .addCategory(Intent.CATEGORY_HOME)
                        .setComponent(component);
            } else {
                // No passenger launcher is specified, then use AppsGrid as a fallback.
                passengerHomeIntent = CarLauncherUtils.getAppsGridIntent();
            }
            ActivityOptions options = ActivityOptions
                    // No animation for the trampoline.
                    .makeCustomAnimation(this, /* enterResId=*/ 0, /* exitResId= */ 0)
                    .setLaunchDisplayId(getDisplayId());
            startActivity(passengerHomeIntent, options.toBundle());
            finish();
            return;
        }

        mUseSmallCanvasOptimizedMap =
                CarLauncherUtils.isSmallCanvasOptimizedMapIntentConfigured(this);

        mActivityManager = getSystemService(ActivityManager.class);
        mCarLauncherTaskId = getTaskId();
        TaskStackChangeListeners.getInstance().registerTaskStackListener(mTaskStackListener);

        // Setting as trusted overlay to let touches pass through.
        getWindow().addPrivateFlags(PRIVATE_FLAG_TRUSTED_OVERLAY);
        // To pass touches to the underneath task.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);

        // Don't show the maps panel in multi window mode.
        // NOTE: CTS tests for split screen are not compatible with activity views on the default
        // activity of the launcher
        if (isInMultiWindowMode() || isInPictureInPictureMode()) {
            setContentView(R.layout.car_launcher_multiwindow);
        } else {
            setContentView(R.layout.car_launcher);
            // We don't want to show Map card unnecessarily for the headless user 0.
            if (!UserHelperLite.isHeadlessSystemUser(getUserId())) {
                mMapsCard = findViewById(R.id.maps_card);
                if (mMapsCard != null) {
                    setupRemoteCarTaskView(mMapsCard);
                }
            }
        }
        MediaIntentRouter.getInstance().registerMediaIntentHandler(mMediaIntentHandler);
        initializeCards();
        setupContentObserversForTos();
    }

    private void setupRemoteCarTaskView(ViewGroup parent) {
        mCarLauncherViewModel = new ViewModelProvider(this,
                new CarLauncherViewModelFactory(this, getMapsIntent()))
                .get(CarLauncherViewModel.class);

        getLifecycle().addObserver(mCarLauncherViewModel);

        mCarLauncherViewModel.getRemoteCarTaskView().observe(this, taskView -> {
            if (taskView != null && taskView.getParent() == null) {
                parent.addView(taskView);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        maybeLogReady();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        TaskStackChangeListeners.getInstance().unregisterTaskStackListener(mTaskStackListener);
        if (mTosContentObserver != null) {
            Log.i(TAG, "Unregister content observer for tos state");
            getContentResolver().unregisterContentObserver(mTosContentObserver);
            mTosContentObserver = null;
        }
        release();
    }

    private int getTaskViewTaskId() {
        if (mCarLauncherViewModel != null) {
            return mCarLauncherViewModel.getRemoteCarTaskViewTaskId();
        }
        return INVALID_TASK_ID;
    }

    private void release() {
        // When using a ViewModel for the RemoteCarTaskViews, the task view can still be attached
        // to the mMapsCard due to which the CarLauncher activity does not get garbage collected
        // during activity recreation.
        mMapsCard = null;
        if (mCar != null) {
            mCar.disconnect();
            mCar = null;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        initializeCards();
    }

    private void initializeCards() {
        if (mHomeCardModules == null) {
            mHomeCardModules = new ArraySet<>();
            for (String providerClassName : getResources().getStringArray(
                    R.array.config_homeCardModuleClasses)) {
                try {
                    long reflectionStartTime = System.currentTimeMillis();
                    HomeCardModule cardModule = (HomeCardModule)
                            Class.forName(providerClassName).newInstance();
                    cardModule.setViewModelProvider(new ViewModelProvider(/* owner= */this));
                    mHomeCardModules.add(cardModule);
                    if (DEBUG) {
                        long reflectionTime = System.currentTimeMillis() - reflectionStartTime;
                        Log.d(TAG, "Initialization of HomeCardModule class " + providerClassName
                                + " took " + reflectionTime + " ms");
                    }
                } catch (IllegalAccessException | InstantiationException
                         | ClassNotFoundException e) {
                    Log.w(TAG, "Unable to create HomeCardProvider class " + providerClassName, e);
                }
            }
        }
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        for (HomeCardModule cardModule : mHomeCardModules) {
            transaction.replace(cardModule.getCardResId(), cardModule.getCardView().getFragment());
        }
        transaction.commitNow();
    }

    /** Logs that the Activity is ready. Used for startup time diagnostics. */
    private void maybeLogReady() {
        boolean isResumed = isResumed();
        if (isResumed) {
            // We should report every time - the Android framework will take care of logging just
            // when it's effectively drawn for the first time, but....
            reportFullyDrawn();
            if (!mIsReadyLogged) {
                // ... we want to manually check that the Log.i below (which is useful to show
                // the user id) is only logged once (otherwise it would be logged every time the
                // user taps Home)
                Log.i(TAG, "Launcher for user " + getUserId() + " is ready");
                mIsReadyLogged = true;
            }
        }
    }

    /** Brings the Car Launcher to the foreground. */
    private void bringToForeground() {
        if (mCarLauncherTaskId != INVALID_TASK_ID) {
            mActivityManager.moveTaskToFront(mCarLauncherTaskId,  /* flags= */ 0);
        }
    }

    @VisibleForTesting
    protected Intent getMapsIntent() {
        Intent mapIntent = mUseSmallCanvasOptimizedMap
                ? CarLauncherUtils.getSmallCanvasOptimizedMapIntent(this)
                : CarLauncherUtils.getMapsIntent(this);

        String packageName = mapIntent.getComponent() != null
                ? mapIntent.getComponent().getPackageName()
                : null;
        Set<String> tosDisabledPackages = AppLauncherUtils.getTosDisabledPackages(this);

        // Launch tos map intent when the user has not accepted tos and when the
        // default maps package is not available to package manager, or it's disabled by tos
        if (!AppLauncherUtils.tosAccepted(this)
                && (packageName == null || tosDisabledPackages.contains(packageName))) {
            mapIntent = CarLauncherUtils.getTosMapIntent(this);
            Log.i(TAG, "Launching tos activity in task view");
        }
        // Don't want to show this Activity in Recents.
        mapIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        return mapIntent;
    }

    private void setupContentObserversForTos() {
        if (AppLauncherUtils.tosStatusUninitialized(/* context = */ this)
                || !AppLauncherUtils.tosAccepted(/* context = */ this)) {
            Log.i(TAG, "TOS not accepted, setting up content observers for TOS state");
        } else {
            Log.i(TAG, "TOS accepted, state will remain accepted, "
                    + "don't need to observe this value");
            return;
        }
        mTosContentObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                // TODO (b/280077391): Release the remote task view and recreate the map activity
                Log.i(TAG, "TOS state updated:" + AppLauncherUtils.tosAccepted(getBaseContext()));
                recreate();
            }
        };
        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(KEY_USER_TOS_ACCEPTED),
                /* notifyForDescendants*/ false,
                mTosContentObserver);
    }
}
