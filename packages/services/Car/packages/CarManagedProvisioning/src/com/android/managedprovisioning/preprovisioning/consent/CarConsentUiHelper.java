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
package com.android.managedprovisioning.preprovisioning.consent;

import static java.util.Objects.requireNonNull;

import android.annotation.DrawableRes;
import android.app.Activity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.CarSetupWizardLayoutHelper;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.TouchTargetEnforcer;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.preprovisioning.PreProvisioningActivityBridgeCallbacks;
import com.android.managedprovisioning.preprovisioning.PreProvisioningActivityController.UiParams;
import com.android.server.utils.Slogf;

/**
 * Implements functionality for the consent screen.
 */
public final class CarConsentUiHelper implements ConsentUiHelper {
    private static final String TAG = CarConsentUiHelper.class.getSimpleName();
    private final Activity mActivity;
    private final TouchTargetEnforcer mTouchTargetEnforcer;
    private final ConsentUiHelperCallback mCallback;
    private final Utils mUtils;
    private final PreProvisioningActivityBridgeCallbacks mBridgeCallbacks;

    public CarConsentUiHelper(Activity activity, ConsentUiHelperCallback callback, Utils utils,
                PreProvisioningActivityBridgeCallbacks bridgeCallbacks) {
        Slogf.d(TAG, "created new instance..");
        mActivity = requireNonNull(activity);
        mCallback = requireNonNull(callback);
        mTouchTargetEnforcer =
                new TouchTargetEnforcer(activity.getResources().getDisplayMetrics().density);
        mUtils = requireNonNull(utils);
        mBridgeCallbacks = requireNonNull(bridgeCallbacks);
    }

    @Override
    public void onStart() {
        // Do nothing.
        Slogf.d(TAG, "onStart..");
    }

    @Override
    public void onStop() {
        // Do nothing.
        Slogf.d(TAG, "onStop..");
    }

    @Override
    public void initiateUi(UiParams uiParams) {
        int titleResId = 0;
        int headerResId = 0;
        int subLayoutId = R.layout.preprovision_layout;

        if (mUtils.isProfileOwnerAction(uiParams.provisioningAction)) {
            titleResId = R.string.setup_profile;
            headerResId = R.string.work_profile_provisioning_accept_header_post_suw;
        } else if (mUtils.isDeviceOwnerAction(uiParams.provisioningAction)) {
            titleResId = R.string.setup_device;
            headerResId = R.string.fully_managed_device_provisioning_accept_header;
        }
        mCallback.onInitiateUi(
                CarSetupWizardLayoutHelper.MAIN_LAYOUT_RES_ID,
                headerResId);
        mActivity.setTitle(titleResId);

        // Set the base layout
        CarSetupWizardLayoutHelper layoutHelper = new CarSetupWizardLayoutHelper(mActivity);
        layoutHelper.setBaseLayout(subLayoutId, /* isDoubleColumnAllowed= */ true);
        layoutHelper.setHeaderText(headerResId, R.string.view_terms);
        layoutHelper.setupPrimaryToolbarButton(R.string.accept_and_continue,
                v -> onTermsAccepted());

        // Set preprovisioning screen specific resources
        setupImageView(R.drawable.ic_car_cockpit);
        setupViewTermsButton();
    }

    private void setupImageView(@DrawableRes int imageResId) {
        ImageView illustration = mActivity.findViewById(R.id.illustration);
        illustration.setImageResource(imageResId);
    }

    private void onTermsAccepted() {
        ProvisionLogger.logi("Next button (next_button) is clicked.");
        mBridgeCallbacks.onTermsAccepted();
    }

    private void setupViewTermsButton() {
        TextView description = mActivity.findViewById(R.id.description);

        description.setTextColor(mUtils.getAccentColor(mActivity));
        description.setOnClickListener(v -> mBridgeCallbacks.onTermsButtonClicked());
        mTouchTargetEnforcer.enforce(description, (View) description.getParent());
    }
}
