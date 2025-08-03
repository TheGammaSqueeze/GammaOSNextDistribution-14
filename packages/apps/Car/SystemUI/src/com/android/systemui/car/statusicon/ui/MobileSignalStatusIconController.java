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

import androidx.annotation.VisibleForTesting;

import com.android.settingslib.graph.SignalDrawable;
import com.android.systemui.R;
import com.android.systemui.car.statusicon.StatusIconView;
import com.android.systemui.car.statusicon.StatusIconViewController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStateController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.statusbar.connectivity.MobileDataIndicators;
import com.android.systemui.statusbar.connectivity.NetworkController;
import com.android.systemui.statusbar.connectivity.SignalCallback;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

/**
 * A controller for the read-only status icon about mobile data.
 */
public class MobileSignalStatusIconController extends StatusIconViewController implements
        SignalCallback{
    private final NetworkController mNetworkController;

    private final SignalDrawable mMobileSignalIconDrawable;
    private final String mMobileSignalContentDescription;

    @AssistedInject
    protected MobileSignalStatusIconController(@Assisted StatusIconView view,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController,
            Context context, NetworkController networkController) {
        super(view, disableController, stateController);
        mNetworkController = networkController;

        mMobileSignalIconDrawable = new SignalDrawable(context);
        mMobileSignalContentDescription = context.getString(R.string.status_icon_signal_mobile);
    }

    @AssistedFactory
    public interface Factory extends
            StatusIconViewController.Factory<MobileSignalStatusIconController> {
    }

    @Override
    protected void onViewAttached() {
        super.onViewAttached();
        mNetworkController.addCallback(this);
        updateStatus();
    }

    @Override
    protected void onViewDetached() {
        super.onViewDetached();
        mNetworkController.removeCallback(this);
    }

    @Override
    protected void updateStatus() {
        setIconDrawableToDisplay(mMobileSignalIconDrawable);
        setIconContentDescription(mMobileSignalContentDescription);
        onStatusUpdated();
    }

    @Override
    public void setMobileDataIndicators(MobileDataIndicators mobileDataIndicators) {
        mMobileSignalIconDrawable.setLevel(mobileDataIndicators.statusIcon.icon);
        updateStatus();
    }

    @VisibleForTesting
    SignalDrawable getMobileSignalIconDrawable() {
        return mMobileSignalIconDrawable;
    }
}
