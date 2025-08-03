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

package com.android.tv.settings.library.device.apps.specialaccess;

import android.annotation.NonNull;
import android.content.Context;
import android.content.Intent;

import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceControllerState;
import com.android.tv.settings.library.device.apps.ApplicationsState;
import com.android.tv.settings.library.util.AbstractPreferenceController;
import com.android.tv.settings.library.util.ResourcesUtil;

import java.util.List;

/** {@link State} to handle high power screen. */
public class HighPowerState extends PreferenceControllerState
        implements ManageApplicationsController.Callback {
    private PowerAllowlistBackend mPowerAllowlistBackend;
    private ManageApplicationsController mManageApplicationsController;
    private final ApplicationsState.AppFilter mFilter =
            new ApplicationsState.CompoundFilter(
                    new ApplicationsState.CompoundFilter(
                            ApplicationsState.FILTER_WITHOUT_DISABLED_UNTIL_USED,
                            ApplicationsState.FILTER_ALL_ENABLED),
                    new ApplicationsState.AppFilter() {
                        @Override
                        public void init() {
                        }

                        @Override
                        public boolean filterApp(ApplicationsState.AppEntry info) {
                            info.extraInfo =
                                    mPowerAllowlistBackend.isAllowlisted(info.info.packageName);
                            return !ManageAppOpState.shouldIgnorePackage(mContext,
                                    info.info.packageName, 0);
                        }
                    });

    @Override
    public void onAttach() {
        super.onAttach();
        mPowerAllowlistBackend = PowerAllowlistBackend.getInstance(mContext);
        mManageApplicationsController = new ManageApplicationsController(mContext,
                getStateIdentifier(),
                getLifecycle(), mFilter, ApplicationsState.ALPHA_COMPARATOR, this,
                mUIUpdateCallback);
    }

    @Override
    public void onResume() {
        super.onResume();
        mManageApplicationsController.updateAppList();
    }

    @Override
    public boolean onPreferenceChange(String[] key, Object value) {
        if (!(value instanceof Boolean)) {
            return false;
        }
        if ((Boolean) value) {
            mPowerAllowlistBackend.removeApp(key[0]);
        } else {
            mPowerAllowlistBackend.addApp(key[0]);
        }
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // no-op
    }

    @Override
    public int getStateIdentifier() {
        return ManagerUtil.STATE_HIGH_POWER;
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        return null;
    }

    public HighPowerState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
    }

    @NonNull
    @Override
    public PreferenceCompat createAppPreference(ApplicationsState.AppEntry entry) {
        final PreferenceCompat pref = mPreferenceCompatManager.getOrCreatePrefCompat(
                entry.info.packageName);
        pref.setTitle(entry.label);
        pref.setIcon(entry.icon);
        if (mPowerAllowlistBackend.isSysAllowlisted(entry.info.packageName)) {
            pref.setChecked(false);
            pref.setEnabled(false);
        } else {
            pref.setEnabled(true);
            pref.setChecked(!(Boolean) entry.extraInfo);
        }
        pref.setType(PreferenceCompat.TYPE_SWITCH);
        pref.setHasOnPreferenceChangeListener(true);
        updateSummary(pref);
        return pref;
    }

    private void updateSummary(PreferenceCompat preference) {
        if (preference.getKey().length != 1) {
            return;
        }
        final String pkg = preference.getKey()[0];
        if (mPowerAllowlistBackend.isSysAllowlisted(pkg)) {
            preference.setSummary(ResourcesUtil.getString(mContext, "high_power_system"));
        } else if (mPowerAllowlistBackend.isAllowlisted(pkg)) {
            preference.setSummary(ResourcesUtil.getString(mContext, "string.high_power_on"));
        } else {
            preference.setSummary(ResourcesUtil.getString(mContext, "high_power_off"));
        }
    }

    @NonNull
    @Override
    public PreferenceCompat getEmptyPreference() {
        return null;
    }
}
