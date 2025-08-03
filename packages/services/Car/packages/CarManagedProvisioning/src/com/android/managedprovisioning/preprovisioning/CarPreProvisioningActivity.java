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

package com.android.managedprovisioning.preprovisioning;

import android.annotation.Nullable;

import androidx.annotation.VisibleForTesting;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.AccessibilityContextMenuMaker;
import com.android.managedprovisioning.common.CarSetupWizardLayoutHelper;
import com.android.managedprovisioning.common.RoleHolderProvider;
import com.android.managedprovisioning.common.RoleHolderUpdaterProvider;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.ThemeHelper;
import com.android.managedprovisioning.common.ThemeHelper.DefaultNightModeChecker;
import com.android.managedprovisioning.common.ThemeHelper.DefaultSetupWizardBridge;
import com.android.managedprovisioning.common.Utils;
import com.android.server.utils.Slogf;

/**
 * TODO (b/183510678) A temporary implementation for AAOS to override
 * {@link PreProvisioningActivity} in ManagedProvisioning using inheritance. The long term
 * solution will be to use composition.
 */
public final class CarPreProvisioningActivity extends PreProvisioningActivity {

    private static final String TAG = CarPreProvisioningActivity.class.getSimpleName();

    public CarPreProvisioningActivity() {
        this(activity ->
                new PreProvisioningActivityController(activity, activity),
                /* AccessibilityContextMenuMaker= */ null,
                new Utils(),
                new SettingsFacade(),
                new ThemeHelper(
                        new DefaultNightModeChecker(),
                        new DefaultSetupWizardBridge()));
    }

    @VisibleForTesting
    public CarPreProvisioningActivity(
            ControllerProvider controllerProvider,
            AccessibilityContextMenuMaker contextMenuMaker, Utils utils,
            SettingsFacade settingsFacade, ThemeHelper themeHelper) {
        super(controllerProvider, contextMenuMaker, utils, settingsFacade, themeHelper,
                RoleHolderProvider.DEFAULT, RoleHolderUpdaterProvider.DEFAULT);
    }

    @Override
    protected PreProvisioningActivityBridge createBridge() {
        Slogf.d(TAG, "createBridge from CarPreProvisioningActivityBridgeImpl.. ");
        return new CarPreProvisioningActivityBridgeImpl(
                /* activity= */ this,
                mUtils,
                CarPreProvisioningActivity.this::initializeLayoutParams,
                createBridgeCallbacks());
    }

    /**
     * Overrides {@code initializeLayoutParams} in SetupGlifLayoutActivity to set layout as
     * {@code CarSetupWizardLayout}.
     */
    @Override
    protected void initializeLayoutParams(
            int layoutResourceId, @Nullable Integer headerResourceId) {
        CarSetupWizardLayoutHelper layoutHelper = new CarSetupWizardLayoutHelper(this);
        layoutHelper.setBaseLayout(
                /* mainLayoutId= */ layoutResourceId,
                /* subLayoutId= */ R.layout.preprovision_layout,
                /* isDoubleColumnAllowed= */ true);
    }
}
