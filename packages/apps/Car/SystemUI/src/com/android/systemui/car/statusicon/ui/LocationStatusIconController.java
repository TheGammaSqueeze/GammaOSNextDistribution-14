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

package com.android.systemui.car.statusicon.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.location.LocationManager;

import androidx.annotation.NonNull;

import com.android.systemui.R;
import com.android.systemui.car.statusicon.StatusIconView;
import com.android.systemui.car.statusicon.StatusIconViewController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStateController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.settings.UserTracker;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

/**
 * A controller for the read-only icon that shows location active status.
 */
public class LocationStatusIconController extends StatusIconViewController {

    private static final IntentFilter INTENT_FILTER_LOCATION_MODE_CHANGED = new IntentFilter(
            LocationManager.MODE_CHANGED_ACTION);

    private final Context mContext;
    private final UserTracker mUserTracker;
    private final LocationManager mLocationManager;
    private boolean mIsLocationActive;

    private final BroadcastReceiver mLocationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateIconVisibilityForCurrentUser();
        }
    };

    private final UserTracker.Callback mUserChangedCallback = new UserTracker.Callback() {
        @Override
        public void onUserChanged(int newUser, @NonNull Context userContext) {
            updateIconVisibilityForCurrentUser();
        }
    };

    @AssistedInject
    protected LocationStatusIconController(@Assisted StatusIconView view,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController,
            Context context, @Main Resources resources, UserTracker userTracker) {
        super(view, disableController, stateController);
        mContext = context;
        mUserTracker = userTracker;
        mLocationManager = context.getSystemService(LocationManager.class);
        setIconDrawableToDisplay(resources.getDrawable(R.drawable.ic_location, context.getTheme()));
    }

    @AssistedFactory
    public interface Factory extends
            StatusIconViewController.Factory<LocationStatusIconController> {
    }

    @Override
    protected void onViewAttached() {
        super.onViewAttached();
        mContext.registerReceiverForAllUsers(mLocationReceiver, INTENT_FILTER_LOCATION_MODE_CHANGED,
                /* broadcastPermission= */ null, /* scheduler= */ null);
        mUserTracker.addCallback(mUserChangedCallback, mContext.getMainExecutor());
        updateIconVisibilityForCurrentUser();
    }

    @Override
    protected void onViewDetached() {
        super.onViewDetached();
        mContext.unregisterReceiver(mLocationReceiver);
        mUserTracker.removeCallback(mUserChangedCallback);
    }

    @Override
    protected void updateStatus() {
        setIconVisibility(mIsLocationActive);
        onStatusUpdated();
    }

    private void updateIconVisibilityForCurrentUser() {
        mIsLocationActive = mLocationManager.isLocationEnabledForUser(mUserTracker.getUserHandle());
        updateStatus();
    }
}
