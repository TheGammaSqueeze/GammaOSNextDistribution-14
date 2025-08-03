/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tv.settings.oemlink;

import static com.android.tv.settings.overlay.FlavorUtils.X_EXPERIENCE_FLAVORS_MASK;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;

import androidx.fragment.app.Fragment;

import com.android.tv.settings.TvSettingsActivity;
import com.android.tv.settings.accessibility.AccessibilityServiceFragment;

import java.util.List;

/** An OEM hook for starting a specific accessibility service settings directly. */
public class AccessibilityServiceActivity extends TvSettingsActivity {

    private static final String TAG = "A11yServiceOemLink";
    private static final String A11Y_SERVICE_INFO_EXTRA = "accessibilityServiceInfo";

    @Override
    protected Fragment createSettingsFragment() {
        if (getIntent() == null || getIntent().getExtras() == null
                || getIntent().getExtras().getParcelable(A11Y_SERVICE_INFO_EXTRA) == null) {
            Log.e(TAG, "No accessibility info extras, returning null");
            return null;
        }
        AccessibilityServiceInfo a11yServiceInfo =
                getIntent().getExtras().getParcelable(A11Y_SERVICE_INFO_EXTRA);
        final List<AccessibilityServiceInfo> installedA11yServiceInfos =
                getSystemService(AccessibilityManager.class)
                        .getInstalledAccessibilityServiceList();
        if (installedA11yServiceInfos == null || installedA11yServiceInfos.isEmpty()
                || !installedA11yServiceInfos.contains(a11yServiceInfo)) {
            Log.e(TAG, "Input accessibility service info is absent on device, returning null");
            return null;
        }
        Bundle args = new Bundle();
        AccessibilityServiceFragment.prepareArgs(
                args,
                a11yServiceInfo.getResolveInfo().serviceInfo.packageName,
                a11yServiceInfo.getResolveInfo().serviceInfo.name,
                a11yServiceInfo.getSettingsActivityName(),
                a11yServiceInfo.getResolveInfo().loadLabel(this.getPackageManager()).toString());
        return com.android.tv.settings.overlay.FlavorUtils.getFeatureFactory(
                this).getSettingsFragmentProvider()
                .newSettingsFragment(AccessibilityServiceFragment.class.getName(), args);
    }

    @Override
    protected int getAvailableFlavors() {
        return X_EXPERIENCE_FLAVORS_MASK;
    }
}
