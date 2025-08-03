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

package com.android.systemui.car.users;

import android.app.IActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.UserManager;

import com.android.systemui.dump.DumpManager;
import com.android.systemui.flags.FeatureFlagsClassic;
import com.android.systemui.settings.UserTrackerImpl;

import javax.inject.Provider;

import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.CoroutineScope;

/**
 * Custom user tracking class extended from {@link UserTrackerImpl} which defines custom behavior
 * when CarSystemUI is running as a secondary user on a multi-display device.
 *
 */
public class CarUserTrackerImpl extends UserTrackerImpl {
    // Indicates whether or not a UserTracker instance should ignore user switch events. This is
    // typically used for background users who should not be influenced by foreground user switches.
    private final boolean mShouldIgnoreUserSwitch;

    public CarUserTrackerImpl(Context context, Provider<FeatureFlagsClassic> featureFlagsClassic,
            UserManager userManager, IActivityManager iActivityManager, DumpManager dumpManager,
            CoroutineScope appScope, CoroutineDispatcher backgroundContext,
            Handler backgroundHandler, boolean ignoreUserSwitch) {
        super(context, featureFlagsClassic, userManager, iActivityManager, dumpManager, appScope,
                backgroundContext, backgroundHandler);
        mShouldIgnoreUserSwitch = ignoreUserSwitch;
    }

    @Override
    public void handleBeforeUserSwitching(int newUserId) {
        if (mShouldIgnoreUserSwitch) {
            // Secondary user SystemUI instances are not running on foreground users, so they should
            // not be impacted by foreground user switches.
            return;
        }
        super.handleBeforeUserSwitching(newUserId);
    }

    @Override
    public void handleUserSwitching(int newUserId) {
        if (mShouldIgnoreUserSwitch) {
            // Secondary user SystemUI instances are not running on foreground users, so they should
            // not be impacted by foreground user switches.
            return;
        }
        super.handleUserSwitching(newUserId);
    }

    @Override
    public void handleUserSwitchComplete(int newUserId) {
        if (mShouldIgnoreUserSwitch) {
            // Secondary user SystemUI instances are not running on foreground users, so they should
            // not be impacted by foreground user switches.
            return;
        }
        super.handleUserSwitchComplete(newUserId);
    }

    @Override
    protected void handleProfilesChanged() {
        if (mShouldIgnoreUserSwitch) {
            // Profile changes are only sent for the primary user, so they should be ignored by
            // secondary users.
            return;
        }
        super.handleProfilesChanged();
    }
}
