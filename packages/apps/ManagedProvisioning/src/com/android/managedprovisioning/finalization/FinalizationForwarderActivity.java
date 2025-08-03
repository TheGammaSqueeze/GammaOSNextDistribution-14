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

package com.android.managedprovisioning.finalization;

import static com.android.managedprovisioning.ManagedProvisioningScreens.RETRY_LAUNCH;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.android.managedprovisioning.ManagedProvisioningBaseApplication;
import com.android.managedprovisioning.ManagedProvisioningScreens;
import com.android.managedprovisioning.common.DefaultFeatureFlagChecker;
import com.android.managedprovisioning.common.DefaultPackageInstallChecker;
import com.android.managedprovisioning.common.DeviceManagementRoleHolderHelper;
import com.android.managedprovisioning.common.DeviceManagementRoleHolderHelper.DefaultResolveIntentChecker;
import com.android.managedprovisioning.common.DeviceManagementRoleHolderHelper.DefaultRoleHolderStubChecker;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.RetryLaunchActivity;
import com.android.managedprovisioning.common.RoleHolderProvider;
import com.android.managedprovisioning.common.SharedPreferences;
import com.android.managedprovisioning.common.TransitionHelper;
import com.android.managedprovisioning.common.Utils;

/**
 * A UX-less {@link Activity} which is meant to delegate provisioning finalization to either
 * the platform-provided finalization or the device management role holder finalization.
 */
public class FinalizationForwarderActivity extends Activity implements
        FinalizationForwarderController.Ui {
    private static final int START_PLATFORM_PROVIDED_PROVISIONING_FINALIZATION_REQUEST_CODE = 1;
    private static final int START_DEVICE_MANAGEMENT_ROLE_HOLDER_FINALIZATION_REQUEST_CODE = 2;

    private final TransitionHelper mTransitionHelper;
    private FinalizationForwarderController mFinalizationController;

    public FinalizationForwarderActivity() {
        mTransitionHelper = new TransitionHelper();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mTransitionHelper.applyContentScreenTransitions(this);
        super.onCreate(savedInstanceState);
        mFinalizationController = createFinalizationController();
        if (savedInstanceState == null) {
            mFinalizationController.forwardFinalization(this);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case START_PLATFORM_PROVIDED_PROVISIONING_FINALIZATION_REQUEST_CODE:
            case START_DEVICE_MANAGEMENT_ROLE_HOLDER_FINALIZATION_REQUEST_CODE:
                setResult(resultCode);
                mTransitionHelper.finishActivity(this);
                break;
        }
    }

    @Override
    public void startPlatformProvidedProvisioningFinalization() {
        mTransitionHelper.startActivityForResultWithTransition(
                this,
                mFinalizationController.createPlatformProvidedProvisioningFinalizationIntent(
                        this, getIntent()),
                START_PLATFORM_PROVIDED_PROVISIONING_FINALIZATION_REQUEST_CODE);
    }

    @Override
    public void startRoleHolderFinalization() {
        Intent intent = new Intent(this, getActivityForScreen(RETRY_LAUNCH));
        intent.putExtra(
                RetryLaunchActivity.EXTRA_INTENT_TO_LAUNCH,
                mFinalizationController.createRoleHolderFinalizationIntent(this, getIntent()));
        mTransitionHelper.startActivityForResultWithTransition(
                this,
                intent,
                START_DEVICE_MANAGEMENT_ROLE_HOLDER_FINALIZATION_REQUEST_CODE);
    }

    protected Class<? extends Activity> getActivityForScreen(ManagedProvisioningScreens screen) {
        return getBaseApplication().getActivityClassForScreen(screen);
    }

    private ManagedProvisioningBaseApplication getBaseApplication() {
        return ((ManagedProvisioningBaseApplication) getApplication());
    }


    private FinalizationForwarderController createFinalizationController() {
        DeviceManagementRoleHolderHelper roleHolderHelper = new DeviceManagementRoleHolderHelper(
                RoleHolderProvider.DEFAULT.getPackageName(this),
                new DefaultPackageInstallChecker(getPackageManager(), new Utils()),
                new DefaultResolveIntentChecker(),
                new DefaultRoleHolderStubChecker(),
                new DefaultFeatureFlagChecker(getContentResolver()));
        SharedPreferences sharedPreferences =
                new ManagedProvisioningSharedPreferences(getApplicationContext());
        return new FinalizationForwarderController(
                roleHolderHelper,
                /* ui= */ this,
                sharedPreferences,
                ((ManagedProvisioningBaseApplication) getApplication()).getScreenManager());
    }
}
