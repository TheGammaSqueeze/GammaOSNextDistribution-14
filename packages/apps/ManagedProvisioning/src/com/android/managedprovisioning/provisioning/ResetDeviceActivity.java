/*
 * Copyright (C) 2023 The Android Open Source Project
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

/**
 * An activity for telling the user they can abort set-up, reset the device.
 * This activity is aimed to be repurposed for all the facrtory-resets in MP, in general
 * but not for all provisioning failures as not every failure should imply factory-reset
 */
public class ResetDeviceActivity extends SetupGlifLayoutActivity {

    public ResetDeviceActivity() {
        super();
    }

    @VisibleForTesting
    public ResetDeviceActivity(
            Utils utils, SettingsFacade settingsFacade, ThemeHelper themeHelper) {
        super(utils, settingsFacade, themeHelper);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createBridge().initiateUi(this);
    }

    protected ResetDeviceActivityBridge createBridge() {
        return ResetDeviceActivityBridgeImpl.builder()
                .setBridgeCallback(createBridgeCallback())
                .setInitializeLayoutParamsConsumer(
                        ResetDeviceActivity.this::initializeLayoutParams)
                .build();
    }

    private ResetDeviceActivityBridgeCallback createBridgeCallback() {
        return () -> {
            getUtils().factoryReset(ResetDeviceActivity.this,
                    "User chose to abort setup.");
            getTransitionHelper().finishActivity(ResetDeviceActivity.this);
        };
    }
}
