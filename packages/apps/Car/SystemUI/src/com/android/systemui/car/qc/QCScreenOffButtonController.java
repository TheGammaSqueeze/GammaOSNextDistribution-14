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

package com.android.systemui.car.qc;

import android.car.hardware.power.CarPowerManager;
import android.content.Context;
import android.content.Intent;

import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.systembar.element.CarSystemBarElementController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStateController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.settings.UserTracker;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

/**
 * One of {@link QCFooterView} for quick control panels, which turns off the screen.
 */

public class QCScreenOffButtonController extends QCFooterViewController {
    private final Context mContext;
    private final UserTracker mUserTracker;
    private final CarServiceProvider mCarServiceProvider;
    private CarPowerManager mCarPowerManager;

    private final CarServiceProvider.CarServiceOnConnectedListener mCarServiceLifecycleListener =
            car -> {
                mCarPowerManager = car.getCarManager(CarPowerManager.class);
            };

    @AssistedInject
    protected QCScreenOffButtonController(@Assisted QCFooterView view,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController, Context context,
            UserTracker userTracker, CarServiceProvider carServiceProvider) {
        super(view, disableController, stateController, context, userTracker);
        mContext = context;
        mUserTracker = userTracker;
        mCarServiceProvider = carServiceProvider;
    }

    @AssistedFactory
    public interface Factory extends
            CarSystemBarElementController.Factory<QCFooterView, QCScreenOffButtonController> {}

    @Override
    protected void onInit() {
        super.onInit();
        mView.setOnClickListener(v -> turnScreenOff());
    }

    @Override
    protected void onViewAttached() {
        super.onViewAttached();
        mCarServiceProvider.addListener(mCarServiceLifecycleListener);
    }

    @Override
    protected void onViewDetached() {
        super.onViewDetached();
        mCarServiceProvider.removeListener(mCarServiceLifecycleListener);
    }

    private void turnScreenOff() {
        mContext.sendBroadcastAsUser(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS),
                mUserTracker.getUserHandle());
        if (mCarPowerManager != null) {
            mCarPowerManager.setDisplayPowerState(getContext().getDisplayId(), /* enable= */ false);
        }
    }
}
