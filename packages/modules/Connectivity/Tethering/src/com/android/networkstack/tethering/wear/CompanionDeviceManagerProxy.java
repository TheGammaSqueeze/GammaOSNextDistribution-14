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

package com.android.networkstack.tethering.wear;

import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.content.Context;
import android.net.connectivity.ConnectivityInternalApiUtil;
import android.net.wear.ICompanionDeviceManagerProxy;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import java.util.List;

/**
 * A proxy for {@link android.companion.CompanionDeviceManager}, allowing Tethering to call it with
 * a different set of permissions.
 * @hide
 */
public class CompanionDeviceManagerProxy {
    private final ICompanionDeviceManagerProxy mService;

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public CompanionDeviceManagerProxy(Context context) {
        mService = ICompanionDeviceManagerProxy.Stub.asInterface(
                ConnectivityInternalApiUtil.getCompanionDeviceManagerProxyService(context));
    }

    /**
     * @see CompanionDeviceManager#getAllAssociations()
     */
    public List<AssociationInfo> getAllAssociations() {
        try {
            return mService.getAllAssociations();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
