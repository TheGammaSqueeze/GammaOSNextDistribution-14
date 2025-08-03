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

package com.android.server.remoteauth.connectivity;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.TargetApi;
import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.util.List;

/** Wraps {@link android.companion.CompanionDeviceManager} for easier testing. */
// TODO(b/296625303): Change to VANILLA_ICE_CREAM when AssociationInfo is available in V.
@TargetApi(Build.VERSION_CODES.TIRAMISU)
public class CompanionDeviceManagerWrapper {
    private static final String TAG = "CompanionDeviceManagerWrapper";

    private Context mContext;
    private CompanionDeviceManager mCompanionDeviceManager;

    public CompanionDeviceManagerWrapper(@NonNull Context context) {
        mContext = context;
    }

    /**
     * Returns device profile string from the association info.
     *
     * @param associationInfo the association info.
     * @return String indicating device profile
     */
    @Nullable
    public String getDeviceProfile(@NonNull AssociationInfo associationInfo) {
        return associationInfo.getDeviceProfile();
    }

    /**
     * Returns all associations.
     *
     * @return associations or null if no associated devices present.
     */
    @Nullable
    public List<AssociationInfo> getAllAssociations() {
        if (mCompanionDeviceManager == null) {
            try {
                mCompanionDeviceManager = mContext.getSystemService(CompanionDeviceManager.class);
            } catch (NullPointerException e) {
                Log.e(TAG, "CompanionDeviceManager service does not exist: " + e);
                return null;
            }
        }

        try {
            return mCompanionDeviceManager.getAllAssociations();
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to get CompanionDeviceManager associations: " + e.getMessage());
        }
        return null;
    }
}
