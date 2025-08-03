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
import android.content.Intent;
import android.os.Bundle;

import com.android.car.setupwizardlib.util.CarWizardManagerHelper;
import com.android.managedprovisioning.ManagedProvisioningScreens;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.CarSetupWizardLayoutHelper;
import com.android.managedprovisioning.common.PolicyComplianceUtils;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.ThemeHelper;
import com.android.managedprovisioning.common.ThemeHelper.DefaultNightModeChecker;
import com.android.managedprovisioning.common.ThemeHelper.DefaultSetupWizardBridge;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.finalization.UserProvisioningStateHelper;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.server.utils.Slogf;

/**
 * TODO (b/183510678) A temporary implementation for AAOS to override
 * {@link ProvisioningActivity} in ManagedProvisioning using inheritance. The long term
 * solution will be to use composition.
 */
public final class CarProvisioningActivity extends ProvisioningActivity {

    private static final String TAG = CarProvisioningActivity.class.getSimpleName();
    private static final int RESET_REQUEST_CODE = 1;

    private UserProvisioningStateHelper mUserProvisioningStateHelper;
    private ProvisioningActivityBridge mBridge;

    public CarProvisioningActivity() {
        super(/* provisioningManager= */ null, // defined in getProvisioningManager()
                new Utils(),
                /* userProvisioningStateHelper= */ null, // defined in onCreate()
                new PolicyComplianceUtils(),
                new SettingsFacade(),
                new ThemeHelper(new DefaultNightModeChecker(), new DefaultSetupWizardBridge()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBridge = createBridge();
        mBridge.initiateUi(/* activity= */ this);

        // assign this Activity as the view store owner to access saved state and receive updates
        getProvisioningManager().setViewModelStoreOwner(this);

        if (mUserProvisioningStateHelper == null) {
            mUserProvisioningStateHelper = new UserProvisioningStateHelper(this);
        }

        if (mState == STATE_PROVISIONING_FINALIZED) {
            updateProvisioningFinalizedScreen();
        }
    }

    @Override
    protected void onAbortButtonClicked() {
        Intent intent = new Intent(this,
                getActivityForScreen(ManagedProvisioningScreens.RESET_AND_RETURN_DEVICE));
        CarWizardManagerHelper.copyWizardManagerExtras(getIntent(), intent);
        intent.putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, mParams);
        getTransitionHelper().startActivityForResultWithTransition(
                this, intent, RESET_REQUEST_CODE);
    }

    @Override
    protected ProvisioningActivityBridge createBridge() {
        return CarProvisioningActivityBridgeImpl.builder()
                .setParams(mParams)
                .setUtils(mUtils)
                .setProvisioningMode(getProvisioningMode())
                .setProvisioningManager(getProvisioningManager())
                .setTransitionAnimationCallback(this)
                .setInitializeLayoutParamsConsumer(
                        CarProvisioningActivity.this::initializeLayoutParams)
                .setShouldSkipEducationScreens(shouldSkipEducationScreens())
                .setProgressLabelResId(getProgressLabelResId())
                .setBridgeCallbacks(createCallbacks())
                .setStateManager(this)
                .build();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RESET_REQUEST_CODE
                && resultCode == RESULT_CANCELED) {
            Slogf.d(TAG, "Finishing Activity since RESET_AND_RETURN_DEVICE screen returned "
                    + "RESULT_CANCELED");
            setResult(resultCode);
            getTransitionHelper().finishActivity(CarProvisioningActivity.this);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * Overrides {@code initializeLayoutParams} in SetupGlifLayoutActivity to set layout as
     * {@code CarSetupWizardLayout}
     */
    @Override
    protected void initializeLayoutParams(
            int layoutResourceId, @Nullable Integer headerResourceId) {
        CarSetupWizardLayoutHelper layoutHelper = new CarSetupWizardLayoutHelper(this);
        layoutHelper.setBaseLayout(/* mainLayoutResId= */ layoutResourceId,
                /* subLayoutId= */ R.layout.provision_layout,
                /* isDoubleColumnAllowed= */ false);
    }
}
