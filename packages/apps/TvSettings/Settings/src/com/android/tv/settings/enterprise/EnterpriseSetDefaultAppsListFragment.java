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

import androidx.annotation.Keep;

import com.android.settingslib.core.AbstractPreferenceController;
import com.android.tv.settings.PreferenceControllerFragment;
import com.android.tv.settings.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment for displaying a list of default applications set by profile or device admin.
 * Forked from:
 * Settings/src/com/android/settings/enterprise/EnterpriseSetDefaultAppsListFragment.java
 */
@Keep
public class EnterpriseSetDefaultAppsListFragment extends PreferenceControllerFragment {
    static final String TAG = "EnterprisePrivacySettings";

    public static EnterpriseSetDefaultAppsListFragment newInstance() {
        return new EnterpriseSetDefaultAppsListFragment();
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.enterprise_set_default_apps_settings;
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();
        final EnterpriseSetDefaultAppsListPreferenceController controller =
                new EnterpriseSetDefaultAppsListPreferenceController(
                        context, this, context.getPackageManager());
        controllers.add(controller);
        return controllers;
    }

    @Override
    protected int getPageId() {
        //TODO: create proper TvSettingsEnums pageId
        return super.getPageId();
    }
}
