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

package com.android.systemui.car.statusicon.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import androidx.annotation.VisibleForTesting;

import com.android.systemui.R;
import com.android.systemui.car.statusicon.StatusIconView;
import com.android.systemui.car.statusicon.StatusIconViewController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStateController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.statusbar.connectivity.NetworkController;
import com.android.systemui.statusbar.connectivity.SignalCallback;
import com.android.systemui.statusbar.connectivity.WifiIndicators;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

/**
 * A controller for the read-only status icon about Wi-Fi.
 */
public class WifiSignalStatusIconController extends StatusIconViewController implements
        SignalCallback {

    private final Context mContext;
    private final Resources mResources;
    private final NetworkController mNetworkController;

    private Drawable mWifiSignalIconDrawable;
    private String mWifiConnectedContentDescription;

    @AssistedInject
    protected WifiSignalStatusIconController(@Assisted StatusIconView view,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController,
            @Main Resources resources, Context context, NetworkController networkController) {
        super(view, disableController, stateController);
        mResources = resources;
        mContext = context;
        mNetworkController = networkController;

        mWifiConnectedContentDescription = context.getString(R.string.status_icon_signal_wifi);
    }

    @AssistedFactory
    public interface Factory extends
            StatusIconViewController.Factory<WifiSignalStatusIconController> {
    }

    @Override
    protected void onViewAttached() {
        super.onViewAttached();
        mNetworkController.addCallback(this);
    }

    @Override
    protected void onViewDetached() {
        super.onViewDetached();
        mNetworkController.removeCallback(this);
    }

    @Override
    protected void updateStatus() {
        setIconDrawableToDisplay(mWifiSignalIconDrawable);
        setIconContentDescription(mWifiConnectedContentDescription);
        onStatusUpdated();
    }

    @Override
    public void setWifiIndicators(WifiIndicators indicators) {
        if (indicators.enabled) {
            mWifiSignalIconDrawable = mResources.getDrawable(indicators.statusIcon.icon,
                    mContext.getTheme());
        } else {
            // Base implementation of Wi-Fi icons does not include a disabled state (uses same icon
            // as disconnected state). For clarity, use a specific icon for disabled Wi-Fi state.
            mWifiSignalIconDrawable = mResources.getDrawable(R.drawable.ic_status_wifi_disabled,
                    mContext.getTheme());
        }
        updateStatus();
    }

    @VisibleForTesting
    Drawable getWifiSignalIconDrawable() {
        return mWifiSignalIconDrawable;
    }
}
