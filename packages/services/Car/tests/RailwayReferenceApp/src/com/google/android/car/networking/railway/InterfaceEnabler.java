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

package com.google.android.car.networking.railway;

import android.content.Context;
import android.net.EthernetManager;
import android.net.EthernetNetworkManagementException;
import android.os.OutcomeReceiver;

public final class InterfaceEnabler {
    private final Context mApplicationContext;
    private final EthernetManager mEthernetManager;
    private final OutcomeReceiver<String, EthernetNetworkManagementException> mOutcomeReceiver;

    public InterfaceEnabler(Context applicationContext,
            OutcomeReceiver<String, EthernetNetworkManagementException> outcomeReceiver) {
        mApplicationContext = applicationContext;
        mEthernetManager = applicationContext.getSystemService(EthernetManager.class);
        mOutcomeReceiver = outcomeReceiver;
    }

    public void enableInterface(String ifaceName) {
        mEthernetManager.enableInterface(ifaceName,
                mApplicationContext.getMainExecutor(),
                mOutcomeReceiver);
    }

    public void disableInterface(String ifaceName) {
        mEthernetManager.disableInterface(ifaceName,
                mApplicationContext.getMainExecutor(),
                mOutcomeReceiver);
    }
}
