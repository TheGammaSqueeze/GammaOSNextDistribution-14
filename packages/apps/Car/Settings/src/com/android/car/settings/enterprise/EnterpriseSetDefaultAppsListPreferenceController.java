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

import android.annotation.Nullable;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.UserHandle;

import androidx.preference.PreferenceGroup;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settingslib.applications.ApplicationFeatureProvider;
import com.android.car.settingslib.applications.UserAppInfo;
import com.android.car.settingslib.enterprise.EnterpriseDefaultApps;
import com.android.car.ui.preference.CarUiPreference;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
* Controller to show the list of default apps were set by the device owner.
*/
public final class EnterpriseSetDefaultAppsListPreferenceController
        extends BaseEnterprisePrivacyPreferenceController<PreferenceGroup> {

    @Nullable
    private EnumMap<EnterpriseDefaultApps, List<ApplicationInfo>> mAllApps;

    public EnterpriseSetDefaultAppsListPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        this(context, preferenceKey, fragmentController, uxRestrictions,
                /* applicationFeatureProvider= */ null);
    }

    @VisibleForTesting
    EnterpriseSetDefaultAppsListPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions,
            @Nullable ApplicationFeatureProvider applicationFeatureProvider) {
        super(context, preferenceKey, fragmentController, uxRestrictions,
                /* enterprisePrivacyFeatureProvider= */ null, applicationFeatureProvider);
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        if (mAllApps == null) {
            mAllApps = listEnterpriseSetDefaultApps();
            mLogger.d("All apps: " + mAllApps);
        }

        return mAllApps == null || mAllApps.isEmpty() ? DISABLED_FOR_PROFILE : AVAILABLE;
    }

    @Override
    protected void updateState(PreferenceGroup preferenceGroup) {
        if (mAllApps == null) {
            // Should never happen, but better log than crash...
            mLogger.wtf("updateState() called when mAllApps is null");
            return;
        }
        mLogger.d("Updating state with " + mAllApps.size() + " types of apps");

        createPreferences(getContext(), preferenceGroup, mAllApps);
    };

    // NOTE: methods below were copied from phone, mostly "as-is"

    @Nullable
    private EnumMap<EnterpriseDefaultApps, List<ApplicationInfo>> listEnterpriseSetDefaultApps() {
        // TODO(b/1703325190): to support work profiles, it should use and outer lopp:
        //   for (UserHandle user : mUserFeatureProvider.getUserProfiles()) {
        UserHandle user = getContext().getUser();
        boolean hasDefaultsForUser = false;
        EnumMap<EnterpriseDefaultApps, List<ApplicationInfo>> userMap = null;

        for (EnterpriseDefaultApps typeOfDefault : EnterpriseDefaultApps.values()) {
            List<UserAppInfo> apps = mApplicationFeatureProvider
                    .findPersistentPreferredActivities(user.getIdentifier(),
                            typeOfDefault.getIntents());
            if (apps.isEmpty()) {
                mLogger.v("no app of type " + typeOfDefault + " for user " + user);
                continue;
            }
            if (!hasDefaultsForUser) {
                hasDefaultsForUser = true;
                userMap = new EnumMap<>(EnterpriseDefaultApps.class);
            }
            ArrayList<ApplicationInfo> applicationInfos = new ArrayList<>();
            for (UserAppInfo userAppInfo : apps) {
                mLogger.d("adding app of type " + typeOfDefault + ":" + userAppInfo);
                applicationInfos.add(userAppInfo.appInfo);
            }
            userMap.put(typeOfDefault, applicationInfos);
        }
        return userMap;
    }

    private void createPreferences(Context prefContext, PreferenceGroup group,
            EnumMap<EnterpriseDefaultApps, List<ApplicationInfo>> apps) {
        group.removeAll();
        for (EnterpriseDefaultApps typeOfDefault : EnterpriseDefaultApps.values()) {
            List<ApplicationInfo> appsForCategory = apps.get(typeOfDefault);
            if (appsForCategory == null || appsForCategory.isEmpty()) {
                mLogger.d("No app for type " + typeOfDefault);
                continue;
            }
            CarUiPreference preference = new CarUiPreference(prefContext);
            preference.setTitle(getTitle(prefContext, typeOfDefault, appsForCategory.size()));
            preference.setSummary(buildSummaryString(prefContext, appsForCategory));
            preference.setOrder(typeOfDefault.ordinal());
            preference.setSelectable(false);
            group.addPreference(preference);
        }
    }

    private CharSequence buildSummaryString(Context context, List<ApplicationInfo> apps) {
        CharSequence[] appNames = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            appNames[i] = apps.get(i).loadLabel(mPm);
        }
        switch (apps.size()) {
            case 1:
                return appNames[0];
            case 2:
                return context.getString(R.string.app_names_concatenation_template_2, appNames[0],
                        appNames[1]);
            default:
                return context.getString(R.string.app_names_concatenation_template_3, appNames[0],
                        appNames[1], appNames[2]);
        }
    }

    private CharSequence getTitle(Context context, EnterpriseDefaultApps typeOfDefault,
            int appCount) {
        switch (typeOfDefault) {
            case BROWSER:
                return context.getString(R.string.default_browser_title);
            case CALENDAR:
                return context.getString(R.string.default_calendar_app_title);
            case CONTACTS:
                return context.getString(R.string.default_contacts_app_title);
            case PHONE:
                return context.getResources()
                        .getQuantityString(R.plurals.default_phone_app_title, appCount);
            case MAP:
                return context.getString(R.string.default_map_app_title);
            case EMAIL:
                return context.getResources()
                        .getQuantityString(R.plurals.default_email_app_title, appCount);
            case CAMERA:
                return context.getResources()
                        .getQuantityString(R.plurals.default_camera_app_title, appCount);
            default:
                throw new IllegalStateException("Unknown type of default " + typeOfDefault);
        }
    }
}
