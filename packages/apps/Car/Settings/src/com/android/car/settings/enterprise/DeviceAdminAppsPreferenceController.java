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

package com.android.car.settings.enterprise;

import android.app.admin.DeviceAdminInfo;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.car.drivingstate.CarUxRestrictions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.ui.preference.CarUiPreference;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.List;

/**
 * Displays a list of device admin apps.
 * <p>Before changing the value of a permission, the user is directed to a confirmation
 * screen with more detailed information about the risks and potential effects.
 */
public abstract class DeviceAdminAppsPreferenceController
        extends BaseEnterprisePreferenceController<PreferenceGroup> {

    public DeviceAdminAppsPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    @Override
    protected void updateState(PreferenceGroup preferenceGroup) {
        preferenceGroup.removeAll();

        // NOTE: Only considering the current user for now. Later we may need to support profiles.
        List<ResolveInfo> receivers = mPm.queryBroadcastReceivers(
                new Intent(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED),
                PackageManager.GET_META_DATA | PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS);
        for (ResolveInfo resolveInfo : receivers) {
            DeviceAdminInfo deviceAdminInfo = createDeviceAdminInfo(resolveInfo.activityInfo);
            // Add only visible ones (note: active admins are added regardless of visibility)
            if (deviceAdminInfo != null && deviceAdminInfo.isVisible()) {
                if (!isIncluded(deviceAdminInfo.getComponent())
                        || !deviceAdminInfo.getActivityInfo().applicationInfo.isInternal()) {
                    continue;
                }
                preferenceGroup.addPreference(createPreference(deviceAdminInfo));
            }
        }

        if (preferenceGroup.getPreferenceCount() == 0) {
            preferenceGroup.addPreference(createEmptyListPreference());
        }
    }

    protected abstract boolean isIncluded(ComponentName componentName);

    protected boolean isActivated(ComponentName componentName) {
        return mDpm.isAdminActive(componentName);
    }

    private Preference createEmptyListPreference() {
        CarUiPreference preference = new CarUiPreference(getContext());
        preference.setTitle(R.string.device_admin_apps_list_empty);
        preference.setSelectable(false);

        return preference;
    }

    private Preference createPreference(DeviceAdminInfo deviceAdminInfo) {
        CarUiPreference preference = new CarUiPreference(getContext());
        preference.setTitle(deviceAdminInfo.loadLabel(mPm));
        preference.setIcon(deviceAdminInfo.loadIcon(mPm));
        CharSequence description = getDescription(deviceAdminInfo);
        if (!TextUtils.isEmpty(description)) {
            preference.setSummary(deviceAdminInfo.loadDescription(mPm));
        }
        preference.setKey(deviceAdminInfo.getPackageName());
        ComponentName componentName = deviceAdminInfo.getComponent();
        preference.setEnabled(isEnabled(componentName));
        preference.setOnPreferenceClickListener(p -> launchDetailScreen(componentName));

        return preference;
    }

    private boolean isEnabled(ComponentName componentName) {
        return !mDpm.isRemovingAdmin(componentName, getContext().getUserId());
    }

    private boolean launchDetailScreen(ComponentName componentName) {
        Intent intent = new Intent(getContext(), DeviceAdminAddActivity.class)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName);
        mLogger.d("Created intent " + intent + " for component: " + componentName);
        getContext().startActivity(intent);

        return true;
    }

    private @Nullable DeviceAdminInfo createDeviceAdminInfo(ActivityInfo ai) {
        try {
            return new DeviceAdminInfo(getContext(), ai);
        } catch (XmlPullParserException | IOException e) {
            mLogger.w("Skipping " + ai, e);
        }
        return null;
    }
}
