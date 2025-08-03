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

package com.android.tv.settings.library.enterprise;

import android.content.Context;

import androidx.annotation.VisibleForTesting;

import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;

public class CaCertsManagedProfilePreferenceController extends CaCertsPreferenceControllerBase {
    @VisibleForTesting
    static final String CA_CERTS_MANAGED_PROFILE = "ca_certs_managed_profile";

    public CaCertsManagedProfilePreferenceController(
            Context context, UIUpdateCallback callback, int stateIdentifier,
            PreferenceCompatManager preferenceCompatManager) {
        super(context, callback, stateIdentifier, preferenceCompatManager);
    }

    @Override
    public String[] getPreferenceKey() {
        return new String[]{CA_CERTS_MANAGED_PROFILE};
    }

    @Override
    protected int getNumberOfCaCerts() {
        return mFeatureProvider.getNumberOfOwnerInstalledCaCertsForManagedProfile();
    }
}
