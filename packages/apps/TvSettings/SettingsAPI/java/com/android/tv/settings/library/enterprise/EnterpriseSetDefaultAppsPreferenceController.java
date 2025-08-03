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
import android.os.UserHandle;
import android.os.UserManager;

import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.enterprise.apps.ApplicationFeatureProvider;
import com.android.tv.settings.library.enterprise.apps.EnterpriseDefaultApps;
import com.android.tv.settings.library.overlay.FlavorUtils;
import com.android.tv.settings.library.util.AbstractPreferenceController;
import com.android.tv.settings.library.util.ResourcesUtil;

/**
 * Forked from:
 * Settings/src/com/android/settings/enterprise/EnterpriseSetDefaultAppsPreferenceController.java
 */
public class EnterpriseSetDefaultAppsPreferenceController extends AbstractPreferenceController {
    private static final String KEY_DEFAULT_APPS = "number_enterprise_set_default_apps";
    private final ApplicationFeatureProvider mApplicationFeatureProvider;
    private final UserManager mUserManager;

    public EnterpriseSetDefaultAppsPreferenceController(
            Context context, UIUpdateCallback callback, int stateIdentifier,
            PreferenceCompatManager preferenceCompatManager) {
        super(context, callback, stateIdentifier, preferenceCompatManager);
        mApplicationFeatureProvider =
                FlavorUtils.getFeatureFactory(context).getApplicationFeatureProvider(context);
        mUserManager = context.getSystemService(UserManager.class);
    }

    @Override
    public void update() {
        final int num = getNumberOfEnterpriseSetDefaultApps();
        mPreferenceCompat.setSummary(ResourcesUtil.getQuantityString(
                mContext, "enterprise_privacy_number_packages", num, num));
    }

    @Override
    public boolean isAvailable() {
        return getNumberOfEnterpriseSetDefaultApps() > 0;
    }

    @Override
    protected void init() {
        update();
    }

    @Override
    public String[] getPreferenceKey() {
        return new String[]{KEY_DEFAULT_APPS};
    }

    private int getNumberOfEnterpriseSetDefaultApps() {
        int num = 0;

        for (UserHandle user : mUserManager.getUserProfiles()) {
            for (EnterpriseDefaultApps app : EnterpriseDefaultApps.values()) {
                num += mApplicationFeatureProvider
                        .findPersistentPreferredActivities(
                                user.getIdentifier(), app.getIntents())
                        .size();
            }
        }
        return num;
    }
}
