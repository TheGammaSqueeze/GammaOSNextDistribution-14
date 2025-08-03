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

package com.android.managedprovisioning;


import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import com.android.managedprovisioning.preprovisioning.CarPreProvisioningActivity;
import com.android.managedprovisioning.preprovisioning.terms.CarTermsActivity;
import com.android.managedprovisioning.provisioning.CarLandingActivity;
import com.android.managedprovisioning.provisioning.CarProvisioningActivity;
import com.android.managedprovisioning.provisioning.CarResetAndReturnDeviceActivity;

/**
 * <p> {@code ManagedProvisioningBaseApplication} for AAOS. Key differences are
 * <ol>
 * <li>AAOS specific activities for {@code ManagedProvisioningScreens}
 * <li>TODO (b/14409262) Car SUW layout integration, driving safety, rotaory support
 * </ol>
 */
public final class CarManagedProvisioningApplication extends ManagedProvisioningBaseApplication {

    private static final String TAG = CarManagedProvisioningApplication.class.getSimpleName();

    private final ActivityLifecycleCallbacks mCallbacks = new ActivityLifecycleCallbacks() {

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            WindowInsetsController insetsController = activity.getWindow().getDecorView()
                    .getWindowInsetsController();
            if (insetsController == null) {
                Log.w(TAG, "No insets controller");
                return;
            }
            Log.d(TAG, "Hiding the system UI bars on " + activity);
            insetsController.hide(WindowInsets.Type.navigationBars());
        }

        @Override
        public void onActivityStarted(Activity activity) {
        }

        @Override
        public void onActivityResumed(Activity activity) {
            // TODO(b/202784955): fix it on themes instead
            Window window = activity.getWindow();
            window.setStatusBarColor(activity.getColor(R.color.status_bar_color));
            window.setNavigationBarColor(activity.getColor(R.color.navigation_bar_color));
        }

        @Override
        public void onActivityPaused(Activity activity) {
        }

        @Override
        public void onActivityStopped(Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        registerActivityLifecycleCallbacks(mCallbacks);

        setOverrideActivity(ManagedProvisioningScreens.PRE_PROVISIONING,
                CarPreProvisioningActivity.class);
        setOverrideActivity(ManagedProvisioningScreens.TERMS,
                CarTermsActivity.class);
        setOverrideActivity(ManagedProvisioningScreens.LANDING,
                CarLandingActivity.class);
        setOverrideActivity(ManagedProvisioningScreens.PROVISIONING,
                CarProvisioningActivity.class);
        setOverrideActivity(ManagedProvisioningScreens.RESET_AND_RETURN_DEVICE,
                CarResetAndReturnDeviceActivity.class);
    }

    @Override
    public void onTerminate() {
        unregisterActivityLifecycleCallbacks(mCallbacks);

        super.onTerminate();
    }
}
