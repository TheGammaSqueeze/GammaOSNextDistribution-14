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

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PermissionGroupInfo;
import android.graphics.drawable.Drawable;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;
import com.android.car.ui.preference.CarUiPreference;

/** Business logic for controlling the privacy center mic setting. */
public class MicPreferenceController
        extends PreferenceController<CarUiPreference> {
    private static final Logger LOG = new Logger(MicPreferenceController.class);
    public static final String PERMISSION_GROUP_MICROPHONE = "android.permission-group.MICROPHONE";

    private PackageManager mPm;

    public MicPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mPm = context.getPackageManager();
    }

    @Override
    protected Class<CarUiPreference> getPreferenceType() {
        return CarUiPreference.class;
    }

    @Override
    protected void onCreateInternal() {
        try {
            PermissionGroupInfo groupInfo = mPm.getPermissionGroupInfo(
                    PERMISSION_GROUP_MICROPHONE,
                    /* flags= */ 0);
            Drawable icon = groupInfo.loadIcon(mPm);
            icon.mutate().setTintList(getContext().getColorStateList(R.color.icon_color_default));
            getPreference().setIcon(icon);
        } catch (PackageManager.NameNotFoundException e) {
            // fall back to using the CarSettings icon specified in the resource file
            LOG.e("Unable to load permission icon from PackageManager", e);
        }
    }
}
