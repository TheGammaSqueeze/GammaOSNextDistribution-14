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

package com.android.tv.settings.enterprise;

import android.content.Context;

import com.android.internal.annotations.VisibleForTesting;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.tv.settings.PreferenceControllerFragment;
import com.android.tv.settings.overlay.FlavorUtils;

import java.util.List;

public class EnterprisePrivacySettingsFragment extends PreferenceControllerFragment {

    static final String TAG = "EnterprisePrivacySettings";

    @VisibleForTesting
    PrivacySettingsPreference mPrivacySettingsPreference;

    public static EnterprisePrivacySettingsFragment newInstance() {
        return new EnterprisePrivacySettingsFragment();
    }

    @Override
    protected int getPreferenceScreenResId() {
        return mPrivacySettingsPreference.getPreferenceScreenResId();
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        return mPrivacySettingsPreference.createPreferenceControllers(true /* async */);
    }

    protected int getPageId() {
        //TODO: get proper pageId
        return super.getPageId();
    }

    @Override
    public void onAttach(Context context) {
        mPrivacySettingsPreference = new PrivacySettingsEnterprisePreference(context);
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        mPrivacySettingsPreference = null;
        super.onDetach();
    }

    public static boolean isPageEnabled(Context context) {
        return FlavorUtils.getFeatureFactory(context).getEnterprisePrivacyFeatureProvider(
                context).hasDeviceOwner();
    }

}
