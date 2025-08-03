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
package com.android.managedprovisioning.provisioning;

import android.annotation.Nullable;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.AccessibilityContextMenuMaker;
import com.android.managedprovisioning.common.CarSetupWizardLayoutHelper;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.ThemeHelper;
import com.android.managedprovisioning.common.ThemeHelper.DefaultNightModeChecker;
import com.android.managedprovisioning.common.ThemeHelper.DefaultSetupWizardBridge;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

/**
 * The first activity shown during provisioning.
 */
public final class CarLandingActivity extends LandingActivity {

    private final AccessibilityContextMenuMaker mContextMenuMaker;
    private ProvisioningParams mParams;
    private CarSetupWizardLayoutHelper mLayoutHelper;

    public CarLandingActivity() {
        this(new Utils(), /* contextMenuMaker= */ null, new SettingsFacade(),
                new ThemeHelper(new DefaultNightModeChecker(), new DefaultSetupWizardBridge()));
    }

    @VisibleForTesting
    CarLandingActivity(Utils utils, AccessibilityContextMenuMaker contextMenuMaker,
            SettingsFacade settingsFacade, ThemeHelper themeHelper) {
        super(utils, contextMenuMaker, settingsFacade, themeHelper);
        mContextMenuMaker = contextMenuMaker != null
                ? contextMenuMaker
                : new AccessibilityContextMenuMaker(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mParams = getIntent().getParcelableExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS);
        mLayoutHelper = new CarSetupWizardLayoutHelper(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected LandingActivityBridge createBridge() {
        return CarLandingActivityBridgeImpl.builder()
                .setBridgeCallbacks(createBridgeCallbacks())
                .setParams(mParams)
                .setInitializeLayoutParamsConsumer(CarLandingActivity.this::initializeLayoutParams)
                .setUtils(mUtils)
                .setLayoutHelper(mLayoutHelper)
                .setAccessibilityContextMenuMaker(mContextMenuMaker)
                .build();
    }

    private LandingActivityBridgeCallbacks createBridgeCallbacks() {
        return new LandingActivityBridgeCallbacks() {
            @Override
            public void onNextButtonClicked() {
                CarLandingActivity.this.onNextButtonClicked();
            }

            @Override
            public void onContactYourItAdminClicked(Intent webIntent) {
                getTransitionHelper().startActivityWithTransition(
                        CarLandingActivity.this, webIntent);
            }
        };
    }

    // For AAOS, admin integrated should be run by default. Therefore,
    // ADMIN_INTEGRATED_PREPARE screen will be skipped as compared to on mobile.
    private void onNextButtonClicked() {
        setResult(Activity.RESULT_OK);
        getTransitionHelper().finishActivity(this);
    }

    /**
     * Overrides {@code initializeLayoutParams} in SetupGlifLayoutActivity to set layout as
     * {@code CarSetupWizardLayout}
     */
    @Override
    protected void initializeLayoutParams(
            int layoutResourceId, @Nullable Integer headerResourceId) {
        CarSetupWizardLayoutHelper layoutHelper = mLayoutHelper;
        layoutHelper.setBaseLayout(
                /* mainLayoutId= */ layoutResourceId,
                /* subLayoutId= */ R.layout.empty_layout,
                /* isDoubleColumnAllowed= */ false);
    }
}
