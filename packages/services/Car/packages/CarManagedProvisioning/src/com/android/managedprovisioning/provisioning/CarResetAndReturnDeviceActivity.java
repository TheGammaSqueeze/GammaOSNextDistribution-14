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
import android.os.Bundle;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.CarSetupWizardLayoutHelper;
import com.android.managedprovisioning.model.ProvisioningParams;

/**
 * An activity for telling the user they can abort set-up, reset the device and return
 * it to their IT admin.
 */
public class CarResetAndReturnDeviceActivity extends ResetAndReturnDeviceActivity {
    private ProvisioningParams mParams;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mParams = getIntent().getParcelableExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected ResetAndReturnDeviceActivityBridge createBridge() {
        return CarResetAndReturnDeviceActivityBridgeImpl.builder()
                .setBridgeCallback(createBridgeCallback())
                .setParams(mParams)
                .setInitializeLayoutParamsConsumer(
                        CarResetAndReturnDeviceActivity.this::initializeLayoutParams)
                .setUtils(mUtils)
                .build();
    }

    @Override
    public void onBackPressed() {
        setResult(Activity.RESULT_OK);
        getTransitionHelper().finishActivity(this);
    }

    private ResetAndReturnDeviceActivityBridgeCallback createBridgeCallback() {
        return () -> {
            getUtils().factoryReset(this, "User chose to abort setup.");
            setResult(Activity.RESULT_CANCELED);
            getTransitionHelper().finishActivity(this);
        };
    }

    /**
     * Overrides {@code initializeLayoutParams} in SetupGlifLayoutActivity to set layout as
     * {@code CarSetupWizardLayout}
     */
    @Override
    protected void initializeLayoutParams(
            int layoutResourceId, @Nullable Integer headerResourceId) {
        CarSetupWizardLayoutHelper layoutHelper = new CarSetupWizardLayoutHelper(this);
        layoutHelper.setBaseLayout(
                /* mainLayoutResId= */ CarSetupWizardLayoutHelper.MAIN_LAYOUT_RES_ID,
                /* columnLayoutId= */ R.layout.single_column_left_layout,
                /* subLayoutId= */ R.layout.empty_layout);
    }
}
