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
import android.content.Context;
import android.content.Intent;
import android.icu.text.RelativeDateTimeFormatter;

import com.android.car.ui.preference.CarUiPreference;
import com.android.settingslib.applications.RecentAppOpsAccess;
import com.android.settingslib.utils.StringUtil;

/**
 * Utilities related to microphone recent access.
 */
public final class MicrophoneRecentAccessUtil {
    private MicrophoneRecentAccessUtil() {
    }

    /**
     * Create a {@link CarUiPreference} for an app with it's last access time and a link to its
     * microphone permission settings.
     */
    public static CarUiPreference createAppPreference(Context prefContext,
            RecentAppOpsAccess.Access access) {
        CarUiPreference pref = new CarUiPreference(prefContext);
        pref.setIcon(access.icon);
        pref.setTitle(access.label);
        pref.setSummary(StringUtil.formatRelativeTime(prefContext,
                System.currentTimeMillis() - access.accessFinishTime, false,
                RelativeDateTimeFormatter.Style.SHORT));
        pref.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(Intent.ACTION_MANAGE_APP_PERMISSION);
            intent.putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME,
                    Manifest.permission_group.MICROPHONE);
            intent.putExtra(Intent.EXTRA_PACKAGE_NAME, access.packageName);
            intent.putExtra(Intent.EXTRA_USER, access.userHandle);
            prefContext.startActivity(intent);
            return true;
        });
        return pref;
    }
}
