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

package com.android.managedprovisioning.networkconnection;

import android.annotation.Nullable;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Bundle;

import androidx.lifecycle.ViewModelProvider;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.ErrorDialogUtils;
import com.android.managedprovisioning.common.ErrorWrapper;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.SetupGlifLayoutActivity;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.networkconnection.EstablishNetworkConnectionViewModel.EstablishNetworkConnectionViewModelFactory;

import com.google.android.setupcompat.util.WizardManagerHelper;

/**
 * Spinner which takes care of network connectivity.
 *
 * <p>For a list of possible extras see {@link
 * DevicePolicyManager#ACTION_ESTABLISH_NETWORK_CONNECTION}. If relevant extras are supplied,
 * it will try to connect to wifi or mobile data.
 *
 * <p>If no extras are supplied, then a network picker is shown to the end-user.
 *
 * <p>If successfully connected to network, {@link #RESULT_OK} is returned. Otherwise the result is
 * {@link #RESULT_CANCELED}.
 *
 * <p>If the result is {@link #RESULT_CANCELED}, it may be accompanied by
 * {@link ErrorDialogUtils#EXTRA_DIALOG_TITLE_ID}, {@link
 * ErrorDialogUtils#EXTRA_ERROR_MESSAGE_RES_ID} and {@link
 * ErrorDialogUtils#EXTRA_FACTORY_RESET_REQUIRED} which can be used to display in a user-visible
 * dialog.
 */
public final class EstablishNetworkConnectionActivity extends SetupGlifLayoutActivity {

    private static final int WIFI_REQUEST_CODE = 1;
    private EstablishNetworkConnectionViewModel mViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mViewModel = new ViewModelProvider(this,
                new EstablishNetworkConnectionViewModelFactory(
                        new Utils(),
                        new SettingsFacade(),
                        new ManagedProvisioningSharedPreferences(this)))
                .get(EstablishNetworkConnectionViewModel.class);
        mViewModel.observeState().observe(this, this::onStateChanged);
    }

    private void onStateChanged(int state) {
        switch(state) {
            case EstablishNetworkConnectionViewModel.STATE_IDLE:
                ProvisioningParams params =
                        mViewModel.parseExtras(getApplicationContext(), getIntent());
                mViewModel.connectToNetwork(getApplicationContext(), params);
                initializeUi();
                break;
            case EstablishNetworkConnectionViewModel.STATE_CONNECTING:
                break;
            case EstablishNetworkConnectionViewModel.STATE_CONNECTED:
                setResult(RESULT_OK);
                getTransitionHelper().finishActivity(this);
                break;
            case EstablishNetworkConnectionViewModel.STATE_ERROR:
                ErrorWrapper error = mViewModel.getError();
                setResult(RESULT_CANCELED, ErrorDialogUtils.createResultIntent(error));
                getTransitionHelper().finishActivity(this);
                break;
            case EstablishNetworkConnectionViewModel.STATE_SHOW_NETWORK_PICKER:
                Intent wifiPickIntent = mUtils.getWifiPickIntent();
                if (canLaunchWifiPicker(wifiPickIntent)) {
                    launchWifiPicker(wifiPickIntent);
                } else {
                    setResult(RESULT_CANCELED);
                    getTransitionHelper().finishActivity(this);
                }
                break;
        }
    }

    private void launchWifiPicker(Intent wifiPickIntent) {
        WizardManagerHelper.copyWizardManagerExtras(getIntent(), wifiPickIntent);
        getTransitionHelper()
                .startActivityForResultWithTransition(
                        this, wifiPickIntent, WIFI_REQUEST_CODE);
    }

    private boolean canLaunchWifiPicker(Intent wifiPickIntent) {
        return getPackageManager().resolveActivity(wifiPickIntent, 0) != null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == WIFI_REQUEST_CODE) {
            setResult(resultCode);
            getTransitionHelper().finishActivity(this);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void initializeUi() {
        final int headerResId = R.string.just_a_sec;
        final int titleResId = R.string.just_a_sec;
        initializeLayoutParams(R.layout.empty_loading_layout, headerResId);
        setTitle(titleResId);
    }

    @Override
    protected boolean isWaitingScreen() {
        return true;
    }
}
