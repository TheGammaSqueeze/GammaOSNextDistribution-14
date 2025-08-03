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

package com.android.car.portraitlauncher.common;

import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;

import android.car.Car;
import android.car.user.CarUserManager;
import android.car.user.UserLifecycleEventFilter;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Centralized receiver to listen and trigger callbacks for certain user events. Currently listens
 * for:
 * - User switching events
 * - User unlocked events
 */
public final class UserEventReceiver {
    public static final String TAG = UserEventReceiver.class.getSimpleName();
    private int mUserId;
    private Car mCar;
    private CarUserManager mCarUserManager;
    private Callback mCallback;
    private CarUserManager.UserLifecycleListener mUserListener =
            new CarUserManager.UserLifecycleListener() {
                @Override
                public void onEvent(@NonNull CarUserManager.UserLifecycleEvent event) {
                    if (mCallback == null) return;
                    if (event.getEventType() == USER_LIFECYCLE_EVENT_TYPE_SWITCHING
                            && event.getPreviousUserId() == mUserId) {
                        mCallback.onUserSwitching();
                    } else if (event.getEventType() == USER_LIFECYCLE_EVENT_TYPE_UNLOCKED
                            && event.getUserId() == mUserId) {
                        mCallback.onUserUnlock();
                    }
                }
            };

    /** Registers an observer to receive user switch events. */
    public void register(Context context, Callback callback) {
        if (mCar != null) {
            Log.w(TAG, "already registered");
            return;
        }
        mUserId = context.getUserId();
        mCar = Car.createCar(context);
        if (mCar == null) {
            throw new IllegalStateException("Unable to connect to car service");
        }
        mCarUserManager = mCar.getCarManager(CarUserManager.class);
        mCallback = callback;
        UserLifecycleEventFilter filter =
                new UserLifecycleEventFilter.Builder()
                        .addEventType(USER_LIFECYCLE_EVENT_TYPE_UNLOCKED)
                        .addEventType(USER_LIFECYCLE_EVENT_TYPE_SWITCHING)
                        .build();
        mCarUserManager.addListener(context.getMainExecutor(), filter, mUserListener);
    }

    /** Unregisters this observer. */
    public void unregister() {
        if (mCar == null || mCarUserManager == null) {
            return;
        }
        mCarUserManager.removeListener(mUserListener);
        mCar.disconnect();
        mCarUserManager = null;
        mCar = null;
        mCallback = null;
    }

    /** Callback interface for {@link UserEventReceiver}. */
    public interface Callback {

        /** Callback triggered when user switch is received. */
        void onUserSwitching();

        /** Callback triggered when user unlock is received. */
        void onUserUnlock();
    }
}
