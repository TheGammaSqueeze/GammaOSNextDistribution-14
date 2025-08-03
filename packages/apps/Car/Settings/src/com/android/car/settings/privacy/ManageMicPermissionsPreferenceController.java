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

package com.android.car.settings.privacy;

import android.Manifest;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.SensorPrivacyManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.permission.PermissionControllerManager;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;
import com.android.internal.annotations.VisibleForTesting;
import com.android.settingslib.utils.StringUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This controller displays the number of non-system apps that have access to microphone.
 */
public class ManageMicPermissionsPreferenceController extends
        PreferenceController<Preference> {
    private static final Logger LOG = new Logger(
            ManageMicPermissionsPreferenceController.class);

    private final AtomicInteger mLoadingInProgress = new AtomicInteger(0);
    private int mNumTotal = 0;
    private int mNumHasAccess = 0;

    private final SensorPrivacyManager mSensorPrivacyManager;
    private final UserManager mUserManager;
    private final SensorPrivacyManager.OnSensorPrivacyChangedListener mListener =
            (sensor, enabled) -> refreshUi();

    public ManageMicPermissionsPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        this(context, preferenceKey, fragmentController, uxRestrictions,
                SensorPrivacyManager.getInstance(context),
                context.getSystemService(UserManager.class));
    }

    @VisibleForTesting
    ManageMicPermissionsPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions,
            SensorPrivacyManager sensorPrivacyManager, UserManager userManager) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mSensorPrivacyManager = sensorPrivacyManager;
        mUserManager = userManager;
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected void onStartInternal() {
        mSensorPrivacyManager.addSensorPrivacyListener(
                SensorPrivacyManager.Sensors.MICROPHONE, mListener);
    }

    @Override
    protected void onStopInternal() {
        mSensorPrivacyManager.removeSensorPrivacyListener(SensorPrivacyManager.Sensors.MICROPHONE,
                mListener);
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        if (mSensorPrivacyManager.isSensorPrivacyEnabled(
                SensorPrivacyManager.Sensors.MICROPHONE)) {
            getPreference().setSummary(getContext().getString(
                    R.string.microphone_app_permission_summary_microphone_off));
            return;
        }
        // Bail out if there's another loading request in progress.
        if (mLoadingInProgress.get() != 0) {
            return;
        }
        getPreference().setSummary(
                getContext().getString(R.string.microphone_settings_loading_app_permission_stats));

        mNumTotal = 0;
        mNumHasAccess = 0;
        // Retrieve a list of users inside the current user profile group.
        List<UserHandle> users = mUserManager.getUserProfiles();
        // need to asynchronously load 2 callbacks for each profile
        mLoadingInProgress.set(2 * users.size());
        for (UserHandle user : users) {
            Context userContext = createPackageContextAsUser(getContext(), user);
            if (userContext == null) {
                // remove the two callbacks that we would normally expect for this user
                if (mLoadingInProgress.addAndGet(-2) == 0) {
                    setAppCounts(mNumTotal, mNumHasAccess);
                }
                continue;
            }
            PermissionControllerManager permController =
                    userContext.getSystemService(PermissionControllerManager.class);
            permController.countPermissionApps(
                    Collections.singletonList(Manifest.permission.RECORD_AUDIO), /* flags= */ 0,
                    (numApps) -> {
                        mNumTotal += numApps;
                        if (mLoadingInProgress.decrementAndGet() == 0) {
                            setAppCounts(mNumTotal, mNumHasAccess);
                        }
                    }, /* handler= */ null);
            permController.countPermissionApps(
                    Collections.singletonList(Manifest.permission.RECORD_AUDIO),
                    PermissionControllerManager.COUNT_ONLY_WHEN_GRANTED,
                    (numApps) -> {
                        mNumHasAccess += numApps;
                        if (mLoadingInProgress.decrementAndGet() == 0) {
                            setAppCounts(mNumTotal, mNumHasAccess);
                        }
                    }, /* handler= */  null);
        }

    }

    private void setAppCounts(int numTotal, int numHasAccess) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("count", numHasAccess);
        arguments.put("total_count", numTotal);
        getPreference().setSummary(StringUtil.getIcuPluralsString(getContext(), arguments,
                R.string.microphone_app_permission_summary_microphone_on));
    }


    /**
     * Returns a context created from the given context for the given user, or null if it fails.
     */
    private Context createPackageContextAsUser(Context context, UserHandle userHandle) {
        try {
            return context.createPackageContextAsUser(
                    context.getPackageName(), /* flags= */ 0, userHandle);
        } catch (PackageManager.NameNotFoundException e) {
            LOG.e("Failed to create user context", e);
        }
        return null;
    }
}

