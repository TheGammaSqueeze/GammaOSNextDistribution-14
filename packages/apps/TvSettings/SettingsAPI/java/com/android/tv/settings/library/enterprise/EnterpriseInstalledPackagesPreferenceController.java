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

import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.enterprise.apps.ApplicationFeatureProvider;
import com.android.tv.settings.library.overlay.FlavorUtils;
import com.android.tv.settings.library.util.AbstractPreferenceController;
import com.android.tv.settings.library.util.ResourcesUtil;

/**
 * Forked from:
 * Settings/src/com/android/settings/enterprise/EnterpriseInstalledPackagesPreferenceController.java
 */
public class EnterpriseInstalledPackagesPreferenceController extends AbstractPreferenceController {
    private static final String KEY_NUMBER_ENTERPRISE_INSTALLED_PACKAGES =
            "number_enterprise_installed_packages";
    private final ApplicationFeatureProvider mFeatureProvider;
    private final boolean mAsync;

    public EnterpriseInstalledPackagesPreferenceController(
            Context context, UIUpdateCallback callback, int stateIdentifier,
            PreferenceCompatManager preferenceCompatManager, boolean async) {
        super(context, callback, stateIdentifier, preferenceCompatManager);
        mFeatureProvider =
                FlavorUtils.getFeatureFactory(context).getApplicationFeatureProvider(context);
        mAsync = async;
    }

    @Override
    public void update() {
        mFeatureProvider.calculateNumberOfPolicyInstalledApps(true /* async */, (num) -> {
            final boolean available;
            if (num == 0) {
                available = false;
            } else {
                available = true;
                mPreferenceCompat.setSummary(ResourcesUtil.getQuantityString(
                        mContext, "enterprise_privacy_number_packages_lower_bound", num, num));
            }
            mPreferenceCompat.setVisible(available);
        });
    }

    @Override
    public boolean isAvailable() {
        if (mAsync) {
            // When called on the main UI thread, we must not block. Since calculating the number of
            // enterprise-installed apps takes a bit of time, we always return true here and
            // determine the pref's actual visibility asynchronously in updateState().
            return true;
        }

        // When called by the search indexer, we are on a background thread that we can block. Also,
        // changes to the pref's visibility made in updateState() would not be seen by the indexer.
        // We block and return synchronously whether there are enterprise-installed apps or not.
        final Boolean[] haveEnterpriseInstalledPackages = {null};
        mFeatureProvider.calculateNumberOfPolicyInstalledApps(
                false /* async */, (num) -> haveEnterpriseInstalledPackages[0] = num > 0);
        return haveEnterpriseInstalledPackages[0];
    }

    @Override
    protected void init() {
        update();
    }

    @Override
    public String[] getPreferenceKey() {
        return new String[]{KEY_NUMBER_ENTERPRISE_INSTALLED_PACKAGES};
    }
}
