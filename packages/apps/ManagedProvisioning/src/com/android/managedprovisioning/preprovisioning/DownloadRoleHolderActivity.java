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

package com.android.managedprovisioning.preprovisioning;

import static com.android.managedprovisioning.model.ProvisioningParams.EXTRA_PROVISIONING_PARAMS;

import android.os.Bundle;

import androidx.lifecycle.ViewModelProvider;

import com.android.managedprovisioning.ManagedProvisioningBaseApplication;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.ErrorDialogUtils;
import com.android.managedprovisioning.common.ErrorWrapper;
import com.android.managedprovisioning.common.Flags;
import com.android.managedprovisioning.common.RoleHolderProvider;
import com.android.managedprovisioning.common.SetupGlifLayoutActivity;
import com.android.managedprovisioning.contracts.DownloadRoleHolderContract;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.preprovisioning.DownloadRoleHolderViewModel.DownloadRoleHolderViewModelFactory;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Spinner which takes care of network connectivity if needed, and downloading of the role holder.
 *
 * <p>If successfully connected to network and downloaded the role holder, {@link #RESULT_OK} is
 * returned. Otherwise the result is {@link #RESULT_CANCELED}.
 *
 * <p>If the result is {@link #RESULT_CANCELED}, it may be accompanied by
 * {@link ErrorDialogUtils#EXTRA_DIALOG_TITLE_ID}, {@link
 * ErrorDialogUtils#EXTRA_ERROR_MESSAGE_RES_ID} and {@link
 * ErrorDialogUtils#EXTRA_FACTORY_RESET_REQUIRED} which can be used to display in a user-visible
 * dialog.
 */
@AndroidEntryPoint(SetupGlifLayoutActivity.class)
public class DownloadRoleHolderActivity extends Hilt_DownloadRoleHolderActivity {
    private DownloadRoleHolderViewModel mViewModel;

    @Inject
    protected Flags mFlags;

    @Inject
    protected DownloadRoleHolderContract mContract;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (mFlags.isCosmicRayEnabled()) {
            mContract.attach(this, getIntent());
        }

        ProvisioningParams params = getIntent().getParcelableExtra(EXTRA_PROVISIONING_PARAMS);
        if (params.roleHolderDownloadInfo == null) {
            setResult(RESULT_CANCELED);
            getTransitionHelper().finishActivity(this);
            return;
        }

        mViewModel = new ViewModelProvider(
                this,
                new DownloadRoleHolderViewModelFactory(
                        (ManagedProvisioningBaseApplication) getApplication(),
                        params,
                        mUtils,
                        mSettingsFacade,
                        RoleHolderProvider.DEFAULT.getPackageName(this)))
                .get(DownloadRoleHolderViewModel.class);
        mViewModel.observeState().observe(this, this::onStateChanged);
        mViewModel.connectToNetworkAndDownloadRoleHolder(getApplicationContext());
        initializeUi();
    }

    private void onStateChanged(Integer state) {
        switch (state) {
            case DownloadRoleHolderViewModel.STATE_IDLE:
                break;
            case DownloadRoleHolderViewModel.STATE_DOWNLOADING:
                break;
            case DownloadRoleHolderViewModel.STATE_DOWNLOADED:
                setResult(RESULT_OK);
                getTransitionHelper().finishActivity(this);
                break;
            case DownloadRoleHolderViewModel.STATE_ERROR:
                ErrorWrapper error = mViewModel.getError();
                setResult(RESULT_CANCELED, ErrorDialogUtils.createResultIntent(error));
                getTransitionHelper().finishActivity(this);
                break;
        }
    }

    private void initializeUi() {
        final int headerResId = R.string.setting_up;
        final int titleResId = R.string.setting_up;
        initializeLayoutParams(R.layout.empty_loading_layout, headerResId);
        setTitle(titleResId);
    }

    @Override
    protected boolean isWaitingScreen() {
        return true;
    }
}
