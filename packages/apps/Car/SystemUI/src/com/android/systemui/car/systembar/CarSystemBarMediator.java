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

package com.android.systemui.car.systembar;

import android.content.Context;
import android.content.om.OverlayManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.UserHandle;
import android.util.Log;

import com.android.systemui.CoreStartable;
import com.android.systemui.R;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.policy.ConfigurationController;

import javax.inject.Inject;

/**
 * TODO(): b/260206944, Can remove this after we have a fix for overlaid resources not applied.
 * <p>
 *     Currently because of Bug:b/260206944, RROs are not applied to the secondary user.
 *     This class acts as a Mediator, which toggles the Overlay state of the RRO package, which
 *     in turn triggers onConfigurationChange. Only after this change start the CarSystemBar
 *     with overlaid resources.
 * </p>
 */
@SysUISingleton
public class CarSystemBarMediator implements CoreStartable,
        ConfigurationController.ConfigurationListener {
    private static final boolean DEBUG = Build.IS_ENG || Build.IS_USERDEBUG;

    private final CarSystemBar mCarSystemBar;
    private final SystemBarConfigs mSystemBarConfigs;
    private final CarSystemBarController mCarSystemBarController;
    private final OverlayManager mOverlayManager;
    private final UserTracker mUserTracker;

    private static final String TAG = CarSystemBarMediator.class.getSimpleName();
    private boolean mCarSystemBarStarted = false;
    private final Context mContext;

    @Inject
    public CarSystemBarMediator(CarSystemBar carSystemBar, SystemBarConfigs systemBarConfigs,
            CarSystemBarController carSystemBarController, Context context,
            UserTracker userTracker) {
        mCarSystemBar = carSystemBar;
        mSystemBarConfigs = systemBarConfigs;
        mCarSystemBarController = carSystemBarController;
        mOverlayManager = context.getSystemService(OverlayManager.class);
        mUserTracker = userTracker;
        mContext = context;
    }

    @Override
    public void start() {
        String rroPackageName = mContext.getString(
                R.string.config_secondaryUserSystemUIRROPackageName);
        if (DEBUG) {
            Log.d(TAG, "start(), toggle RRO package:" + rroPackageName);
        }
        // The RRO must be applied to the user that SystemUI is running as.
        // MUPAND SystemUI runs as the system user, not the actual user.
        UserHandle userHandle = CarSystemUIUserUtil.isMUPANDSystemUI() ? UserHandle.SYSTEM
                : mUserTracker.getUserHandle();
        try {
            mOverlayManager.setEnabled(rroPackageName, false, userHandle);
            mOverlayManager.setEnabled(rroPackageName, true, userHandle);
        } catch (IllegalArgumentException ex) {
            Log.w(TAG, "Failed to set overlay package: " + ex);
            mCarSystemBar.start();
            mCarSystemBarStarted = true;
        }
    }

    @Override
    public void onConfigChanged(Configuration newConfig) {
        if (DEBUG) {
            Log.d(TAG, "onConfigurationChanged(), reset resources and start CarSystemBar");
        }
        // Do not start any components which depend on the overlaid resources before RROs gets
        // applied.
        if (mCarSystemBarStarted) {
            return;
        }
        mSystemBarConfigs.resetSystemBarConfigs();
        mCarSystemBarController.resetSystemBarConfigs();
        mCarSystemBar.start();
        mCarSystemBarStarted = true;
    }
}
