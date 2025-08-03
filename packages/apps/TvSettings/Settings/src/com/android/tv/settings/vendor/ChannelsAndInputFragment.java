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

package com.android.tv.settings.vendor;

import android.os.Bundle;

import androidx.annotation.Keep;

import com.android.tv.settings.R;
import com.android.tv.settings.SettingsPreferenceFragment;
import com.android.tv.settings.customization.CustomizationConstants;
import com.android.tv.settings.customization.Partner;
import com.android.tv.settings.customization.PartnerPreferencesMerger;

/** A vendor sample of Channels And Inputs settings. */
@Keep
public class ChannelsAndInputFragment  extends SettingsPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        setPreferencesFromResource(R.xml.channels_and_inputs_vendor, null);
        if (Partner.getInstance(getContext()).isCustomizationPackageProvided()) {
            PartnerPreferencesMerger.mergePreferences(
                    getContext(),
                    getPreferenceScreen(),
                    CustomizationConstants.CHANNELS_AND_INPUTS_SCREEN
            );
        }
    }
}
