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

package com.android.permissioncontroller.permission.ui.model;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;

import androidx.preference.Preference;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.model.livedatatypes.PermGroupPackagesUiInfo;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.Utils;

class PermissionGroupPreference extends Preference {
    PermissionGroupPreference(Context context, PermGroupPackagesUiInfo permissionGroupInfo) {
        super(context);

        final String name = permissionGroupInfo.getName();
        final CharSequence label = KotlinUtils.INSTANCE.getPermGroupLabel(context, name);
        final Drawable icon = KotlinUtils.INSTANCE.getPermGroupIcon(context, name);
        final Drawable tintedIcon =
                Utils.applyTint(context, icon, android.R.attr.colorControlNormal);
        final Intent managePgIntent = new Intent(Intent.ACTION_MANAGE_PERMISSION_APPS)
                .putExtra(Intent.EXTRA_PERMISSION_NAME, name);

        setKey(name);
        setPersistent(false);
        setTitle(label);
        setIcon(tintedIcon);
        setIntent(managePgIntent);
        updateSummary(permissionGroupInfo);
    }

    void updateSummary(PermGroupPackagesUiInfo info) {
        updateSummary(info.getNonSystemGranted(),
                info.getNonSystemTotal());
    }

    void updateSummary(int granted, int used) {
        if (used > 0) {
            final String summary = getContext().getString(R.string.app_permissions_group_summary,
                    granted, used);
            setSummary(summary);
        } else {
            setSummary(null);
        }
    }
}
