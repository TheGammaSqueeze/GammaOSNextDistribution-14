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
package com.android.car.settings.admin;

import android.car.Car;
import android.car.admin.CarDevicePolicyManager;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;

import androidx.fragment.app.FragmentActivity;

import com.android.car.admin.ui.ManagedDeviceTextView;
import com.android.car.settings.R;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.Logger;
import com.android.internal.annotations.VisibleForTesting;
import com.android.settingslib.core.lifecycle.HideNonSystemOverlayMixin;

/**
 * Shows a disclaimer dialog when a new user is added in a device that is managed by a device owner.
 *
 * <p>The dialog text will contain the message from
 * {@code ManagedDeviceTextView.getManagedDeviceText}.
 *
 * <p>The dialog contains two buttons: one to acknowlege the disclaimer; the other to launch
 * {@code Settings.ACTION_ENTERPRISE_PRIVACY_SETTINGS} for more details. Note: when
 * {@code Settings.ACTION_ENTERPRISE_PRIVACY_SETTINGS} is closed, the same dialog will be shown.
 *
 * <p>Clicking anywhere outside the dialog will dimiss the dialog.
 */
public final class NewUserDisclaimerActivity extends FragmentActivity {
    @VisibleForTesting
    static final Logger LOG = new Logger(NewUserDisclaimerActivity.class);
    @VisibleForTesting
    static final String DIALOG_TAG = "NewUserDisclaimerActivity.ConfirmationDialogFragment";
    private static final int LEARN_MORE_RESULT_CODE = 1;

    private Car mCar;
    private CarDevicePolicyManager mCarDevicePolicyManager;
    private Button mAcceptButton;
    private ConfirmationDialogFragment mConfirmationDialog;
    private boolean mLearnMoreLaunched;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getLifecycle().addObserver(new HideNonSystemOverlayMixin(this));
        getCarDevicePolicyManager();
        setupConfirmationDialog();
    }

    @Override
    protected void onResume() {
        super.onResume();
        showConfirmationDialog();
        getCarDevicePolicyManager().setUserDisclaimerShown(getUser());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != LEARN_MORE_RESULT_CODE) {
            LOG.w("onActivityResult(), invalid request code: " + requestCode);
            return;
        }
        mLearnMoreLaunched = false;
    }

    private ConfirmationDialogFragment setupConfirmationDialog() {
        String managedByOrganizationText = ManagedDeviceTextView.getManagedDeviceText(this)
                .toString();
        String managedProfileText = getResources().getString(
                R.string.new_user_managed_device_text);

        mConfirmationDialog = new ConfirmationDialogFragment.Builder(getApplicationContext())
                .setTitle(R.string.new_user_managed_device_title)
                .setMessage(managedByOrganizationText + System.lineSeparator()
                        + System.lineSeparator() + managedProfileText)
                .setPositiveButton(R.string.new_user_managed_device_acceptance,
                        arguments -> onAccept())
                .setNeutralButton(R.string.new_user_managed_device_learn_more,
                        arguments -> onLearnMoreClicked())
                .setDismissListener((arguments, positiveResult) -> onDialogDimissed())
                .build();

        return mConfirmationDialog;
    }

    private void showConfirmationDialog() {
        if (mConfirmationDialog == null) {
            setupConfirmationDialog();
        }
        mConfirmationDialog.show(getSupportFragmentManager(), DIALOG_TAG);
    }

    private void onAccept() {
        LOG.d("user accepted");
        getCarDevicePolicyManager().setUserDisclaimerAcknowledged(getUser());
        setResult(RESULT_OK);
        finish();
    }

    private void onLearnMoreClicked() {
        mLearnMoreLaunched = true;
        startActivityForResult(new Intent(Settings.ACTION_ENTERPRISE_PRIVACY_SETTINGS),
                LEARN_MORE_RESULT_CODE);
    }

    private void onDialogDimissed() {
        if (mLearnMoreLaunched) {
            return;
        }
        finish();
    }

    private CarDevicePolicyManager getCarDevicePolicyManager() {
        LOG.d("getCarDevicePolicyManager for user: " + getUser());
        if (mCarDevicePolicyManager != null) {
            return mCarDevicePolicyManager;
        }
        if (mCar == null) {
            mCar = Car.createCar(this);
        }
        mCarDevicePolicyManager = (CarDevicePolicyManager) mCar.getCarManager(
                Car.CAR_DEVICE_POLICY_SERVICE);
        return mCarDevicePolicyManager;
    }
}
