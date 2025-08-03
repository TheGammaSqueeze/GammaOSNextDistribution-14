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

import android.os.Bundle;

import androidx.annotation.VisibleForTesting;

import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.SetupGlifLayoutActivity;
import com.android.managedprovisioning.common.ThemeHelper;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

import com.google.android.setupcompat.template.FooterButton;

/**
 * An activity for telling the user they can abort set-up, reset the device and return
 * it to their IT admin.
 */
public class ResetAndReturnDeviceActivity extends SetupGlifLayoutActivity {

    public ResetAndReturnDeviceActivity() {
        super();
    }

    @VisibleForTesting
    public ResetAndReturnDeviceActivity(
            Utils utils, SettingsFacade settingsFacade, ThemeHelper themeHelper) {
        super(utils, settingsFacade, themeHelper);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createBridge().initiateUi(this);
    }

    protected ResetAndReturnDeviceActivityBridge createBridge() {
        return ResetAndReturnDeviceActivityBridgeImpl.builder()
                .setBridgeCallback(createBridgeCallback())
                .setInitializeLayoutParamsConsumer(
                        ResetAndReturnDeviceActivity.this::initializeLayoutParams)
                .build();
    }

    private ResetAndReturnDeviceActivityBridgeCallback createBridgeCallback() {
        return () -> {
            getUtils().factoryReset(ResetAndReturnDeviceActivity.this,
                    "User chose to abort setup.");
            getTransitionHelper().finishActivity(ResetAndReturnDeviceActivity.this);
        };
    }
}
