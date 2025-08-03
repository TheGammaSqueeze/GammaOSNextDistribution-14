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

package com.android.tv.settings.library.device.apps;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.text.TextUtils;

import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.util.ResourcesUtil;

import java.util.List;

/** Preference controller to handle app storage preference. */
public class AppStoragePreferenceController extends AppActionPreferenceController {
    private static final String KEY_APP_STORAGE = "appStorage";
    private final PackageManager mPackageManager;
    private final StorageManager mStorageManager;

    public AppStoragePreferenceController(Context context,
            UIUpdateCallback callback, int stateIdentifier,
            ApplicationsState.AppEntry appEntry, PreferenceCompatManager preferenceCompatManager) {
        super(context, callback, stateIdentifier, appEntry, preferenceCompatManager);
        mPackageManager = context.getPackageManager();
        mStorageManager = context.getSystemService(StorageManager.class);
    }

    @Override
    public void update() {
        if (mAppEntry == null) {
            return;
        }
        final ApplicationInfo applicationInfo = mAppEntry.info;
        final VolumeInfo volumeInfo = mPackageManager.getPackageCurrentVolume(applicationInfo);
        final List<VolumeInfo> candidates =
                mPackageManager.getPackageCandidateVolumes(applicationInfo);

        mPreferenceCompat.setTitle(ResourcesUtil.getString(mContext,
                "device_apps_app_management_storage_used"));

        final String volumeDesc = mStorageManager.getBestVolumeDescription(volumeInfo);
        final String size = mAppEntry.sizeStr;
        if (TextUtils.isEmpty(size)) {
            mPreferenceCompat.setSummary(ResourcesUtil.getString(mContext,
                    "storage_calculating_size"));
        } else {
            mPreferenceCompat.setSummary(ResourcesUtil.getString(
                    mContext,
                    "device_apps_app_management_storage_used_desc",
                    mAppEntry.sizeStr, volumeDesc));
        }
        super.update();
    }

    @Override
    public boolean useAdminDisabledSummary() {
        return false;
    }

    @Override
    public String getAttrUserRestriction() {
        return null;
    }

    @Override
    public String[] getPreferenceKey() {
        return new String[]{KEY_APP_STORAGE};
    }
}
