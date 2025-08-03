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
package com.google.android.sample.testsliceapp;

import android.content.Context;
import android.os.PersistableBundle;
import android.service.carrier.CarrierIdentifier;
import android.service.carrier.CarrierService;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.util.Log;

/**
 * Carrier Service that sets the carrier config upon being bound by the system. Requires UICC
 * privileges.
 */
public class TestCarrierService extends CarrierService {
    @Override
    public void onCreate() {
        CarrierConfigManager cfgMgr =
                (CarrierConfigManager) getSystemService(Context.CARRIER_CONFIG_SERVICE);
        cfgMgr.notifyConfigChangedForSubId(SubscriptionManager.getDefaultSubscriptionId());
        Log.d("TestCarrierService", "onCreate + ");
    }

    @Override
    public PersistableBundle onLoadConfig(CarrierIdentifier carrierIdentifier) {
        PersistableBundle config = new PersistableBundle();
        Log.d("TestCarrierService", "onLoadConfig + ");
        return config;
    }
}
