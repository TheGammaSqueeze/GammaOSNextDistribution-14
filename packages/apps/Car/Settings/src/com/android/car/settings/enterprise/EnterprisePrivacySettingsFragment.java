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

import android.app.Activity;
import android.content.Context;

import androidx.annotation.VisibleForTesting;

import com.android.car.settings.R;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.SettingsFragment;

/** Displays privacy information about the device owner.*/
public final class EnterprisePrivacySettingsFragment extends SettingsFragment {

    private final Logger mLogger = new Logger(getClass());

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.enterprise_privacy_settings_fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        // Split in 2 as it would be hard to mock requireActivity();
        onAttach(context, requireActivity());
    }

    @VisibleForTesting
    void onAttach(Context context, Activity activity) {
        if (!EnterpriseUtils.hasDeviceOwner(context)) {
            mLogger.d("finishing " + activity + " because there is no device owner");
            activity.finish();
            return;
        }
    }
}
