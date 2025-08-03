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

package com.android.server.connectivity.wear;

import android.annotation.SuppressLint;
import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.content.Context;
import android.net.wear.ICompanionDeviceManagerProxy;
import android.os.Binder;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.net.module.util.PermissionUtils;

import java.util.List;

/**
 * A proxy for {@link CompanionDeviceManager}, for use by Tethering with NetworkStack permissions.
 */
public class CompanionDeviceManagerProxyService extends ICompanionDeviceManagerProxy.Stub {
    private final Context mContext;

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public CompanionDeviceManagerProxyService(Context context) {
        mContext = context;
    }

    // TODO(b/193460475): Android Lint handles change from SystemApi to public incorrectly.
    // CompanionDeviceManager#getAllAssociations() is made public in U,
    // but existed in T as an identical SystemApi.
    @SuppressLint("NewApi")
    @Override
    public List<AssociationInfo> getAllAssociations() {
        PermissionUtils.enforceNetworkStackPermission(mContext);
        final long token = Binder.clearCallingIdentity();
        try {
            final CompanionDeviceManager cdm = mContext.getSystemService(
                    CompanionDeviceManager.class);
            return cdm.getAllAssociations();
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }
}
