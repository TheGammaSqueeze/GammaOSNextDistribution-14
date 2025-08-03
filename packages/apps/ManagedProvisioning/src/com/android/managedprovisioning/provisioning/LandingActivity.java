/*
 * Copyright 2019, The Android Open Source Project
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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.ManagedProvisioningScreens;
import com.android.managedprovisioning.common.AccessibilityContextMenuMaker;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.SetupGlifLayoutActivity;
import com.android.managedprovisioning.common.ThemeHelper;
import com.android.managedprovisioning.common.ThemeHelper.DefaultNightModeChecker;
import com.android.managedprovisioning.common.ThemeHelper.DefaultSetupWizardBridge;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

import com.google.android.setupcompat.util.WizardManagerHelper;

/**
 * The first activity shown during provisioning.
 */
public class LandingActivity extends SetupGlifLayoutActivity {
    protected static final int ADMIN_INTEGRATED_FLOW_PREPARE_REQUEST_CODE = 1;
    private final AccessibilityContextMenuMaker mContextMenuMaker;
    private LandingActivityBridge mBridge;
    private ProvisioningParams mParams;

    public LandingActivity() {
        this(new Utils(), /* contextMenuMaker */ null, new SettingsFacade(),
                new ThemeHelper(new DefaultNightModeChecker(), new DefaultSetupWizardBridge()));
    }

    @VisibleForTesting
    LandingActivity(Utils utils, AccessibilityContextMenuMaker contextMenuMaker,
            SettingsFacade settingsFacade, ThemeHelper themeHelper) {
        super(utils, settingsFacade, themeHelper);
        mContextMenuMaker = contextMenuMaker != null
                ? contextMenuMaker
                : new AccessibilityContextMenuMaker(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mParams = getIntent().getParcelableExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS);
        mBridge = createBridge();
        mBridge.initiateUi(this);
    }

    protected LandingActivityBridge createBridge() {
        return LandingActivityBridgeImpl.builder()
                .setBridgeCallbacks(createBridgeCallbacks())
                .setParams(mParams)
                .setInitializeLayoutParamsConsumer(LandingActivity.this::initializeLayoutParams)
                .setUtils(mUtils)
                .setAccessibilityContextMenuMaker(mContextMenuMaker)
                .build();
    }

    private LandingActivityBridgeCallbacks createBridgeCallbacks() {
        return new LandingActivityBridgeCallbacks() {
            @Override
            public void onNextButtonClicked() {
                LandingActivity.this.onNextButtonClicked();
            }

            @Override
            public void onContactYourItAdminClicked(Intent webIntent) {
                getTransitionHelper().startActivityWithTransition(
                        LandingActivity.this, webIntent);
            }
        };
    }

    private void onNextButtonClicked() {
        if (AdminIntegratedFlowPrepareActivity
                .shouldRunPrepareActivity(mUtils, this, mParams)) {
            Intent intent = new Intent(this,
                    getActivityForScreen(ManagedProvisioningScreens.ADMIN_INTEGRATED_PREPARE));
            WizardManagerHelper.copyWizardManagerExtras(getIntent(), intent);
            intent.putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, mParams);
            getTransitionHelper().startActivityForResultWithTransition(
                    this, intent, ADMIN_INTEGRATED_FLOW_PREPARE_REQUEST_CODE);
        } else {
            setResult(Activity.RESULT_OK);
            getTransitionHelper().finishActivity(this);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ADMIN_INTEGRATED_FLOW_PREPARE_REQUEST_CODE) {
            setResult(resultCode);
            getTransitionHelper().finishActivity(this);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}
