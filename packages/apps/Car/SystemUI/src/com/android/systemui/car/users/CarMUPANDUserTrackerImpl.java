/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.car.users;

import static android.car.CarOccupantZoneManager.INVALID_USER_ID;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_INVISIBLE;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STARTING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_VISIBLE;

import android.annotation.UserIdInt;
import android.app.IActivityManager;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.user.CarUserManager;
import android.car.user.UserLifecycleEventFilter;
import android.content.Context;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.view.Display;

import com.android.systemui.InitController;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.flags.FeatureFlagsClassic;
import com.android.systemui.settings.UserTrackerImpl;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Provider;

import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.CoroutineScope;

/**
 * Custom user tracking class extended from {@link UserTrackerImpl} specifically for
 * the system user (user 0) running in a MUPAND configuration. This tracker will behave
 * as if the passenger user running on the default display is the foreground user and
 * handle switching accordingly.
 */
public class CarMUPANDUserTrackerImpl extends UserTrackerImpl {
    private static final String TAG = CarMUPANDUserTrackerImpl.class.getName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final CarServiceProvider mCarServiceProvider;
    private final Executor mCarUserManagerCallbackExecutor;
    private CarOccupantZoneManager mCarOccupantZoneManager;
    private boolean mIsPostInit;

    private final UserLifecycleEventFilter mFilter = new UserLifecycleEventFilter.Builder()
            .addEventType(USER_LIFECYCLE_EVENT_TYPE_STARTING)
            .addEventType(USER_LIFECYCLE_EVENT_TYPE_VISIBLE)
            .addEventType(USER_LIFECYCLE_EVENT_TYPE_INVISIBLE)
            .build();

    private final CarUserManager.UserLifecycleListener mUserLifecycleListener = event -> {
        int eventType = event.getEventType();
        if (DEBUG) {
            Log.d(TAG, "UserLifeCycleEvent eventType=" + eventType);
        }
        int userId = event.getUserId();
        if (eventType == USER_LIFECYCLE_EVENT_TYPE_INVISIBLE) {
            if (userId == getUserId() && userId != UserHandle.USER_SYSTEM) {
                // User has logged out - switch back to the system user
                if (DEBUG) {
                    Log.d(TAG, String.format("User %d removed from default display"
                                    + " - switching to system user", userId));
                }
                performUserSwitch(UserHandle.USER_SYSTEM);
            }
            return;
        }

        if (getDisplayIdForUser(userId) == Display.DEFAULT_DISPLAY) {
            if (DEBUG) {
                Log.d(TAG, String.format("Assigning user %d to default display SysUI", userId));
            }
            // Non-foreground user running on default display will be assigned to system user SysUI
            performUserSwitch(userId);
        }
    };

    public CarMUPANDUserTrackerImpl(Context context,
            Provider<FeatureFlagsClassic> featureFlagsClassic, UserManager userManager,
            IActivityManager iActivityManager, DumpManager dumpManager,
            CoroutineScope appScope, CoroutineDispatcher backgroundContext,
            Handler backgroundHandler, CarServiceProvider carServiceProvider,
            InitController initController) {
        super(context, featureFlagsClassic, userManager, iActivityManager, dumpManager, appScope,
                backgroundContext, backgroundHandler);
        mCarUserManagerCallbackExecutor = Executors.newSingleThreadExecutor();
        mCarServiceProvider = carServiceProvider;

        initController.addPostInitTask(() -> {
            maybePerformInitialUserSwitch();
            mIsPostInit = true;
        });
    }

    @Override
    public void initialize(int startingUser) {
        if (getInitialized()) {
            return;
        }
        super.initialize(startingUser);
        mCarServiceProvider.addListener(mCarServiceOnConnectedListener);
    }

    private int getDisplayIdForUser(@UserIdInt int userId) {
        if (mCarOccupantZoneManager == null) {
            return Display.INVALID_DISPLAY;
        }

        List<CarOccupantZoneManager.OccupantZoneInfo> occupantZoneInfos =
                mCarOccupantZoneManager.getAllOccupantZones();
        for (int i = 0; i < occupantZoneInfos.size(); i++) {
            CarOccupantZoneManager.OccupantZoneInfo zoneInfo = occupantZoneInfos.get(i);
            int zoneUserId = mCarOccupantZoneManager.getUserForOccupant(zoneInfo);
            if (zoneUserId == userId) {
                Display d = mCarOccupantZoneManager.getDisplayForOccupant(zoneInfo,
                        CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
                return d != null ? d.getDisplayId() : Display.INVALID_DISPLAY;
            }
        }
        return Display.INVALID_DISPLAY;
    }

    private void performUserSwitch(int userId) {
        if (userId == getUserId()) {
            if (DEBUG) {
                Log.d(TAG, String.format("User %d already assigned to display", userId));
            }
            return;
        }
        handleBeforeUserSwitching(userId);
        handleUserSwitching(userId);
        handleUserSwitchComplete(userId);
    }

    private void maybePerformInitialUserSwitch() {
        if (mCarOccupantZoneManager == null) {
            return;
        }
        int userId = mCarOccupantZoneManager.getUserForDisplayId(
                Display.DEFAULT_DISPLAY);
        if (userId != INVALID_USER_ID) {
            mCarUserManagerCallbackExecutor.execute(() -> performUserSwitch(userId));
        }
    }

    private final CarServiceProvider.CarServiceOnConnectedListener mCarServiceOnConnectedListener =
            new CarServiceProvider.CarServiceOnConnectedListener() {
                @Override
                public void onConnected(Car car) {
                    mCarOccupantZoneManager = car.getCarManager(CarOccupantZoneManager.class);
                    if (mIsPostInit) {
                        // Car connected after SystemUI initialization completed - check to see
                        // if an initial user switch is necessary.
                        maybePerformInitialUserSwitch();
                    }

                    CarUserManager carUserManager = car.getCarManager(CarUserManager.class);
                    if (carUserManager != null) {
                        carUserManager.addListener(mCarUserManagerCallbackExecutor, mFilter,
                                mUserLifecycleListener);
                    }
                }
            };
}
