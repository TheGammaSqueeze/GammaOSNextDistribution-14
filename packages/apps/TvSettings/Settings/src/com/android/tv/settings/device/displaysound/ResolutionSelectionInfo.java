/*
 * Copyright 2020 The Android Open Source Project
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

package com.android.tv.settings.device.displaysound;

import static android.view.Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION;
import static android.view.Display.HdrCapabilities.HDR_TYPE_HDR10;
import static android.view.Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS;
import static android.view.Display.HdrCapabilities.HDR_TYPE_HLG;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Keep;

import com.android.tv.twopanelsettings.R;
import com.android.tv.twopanelsettings.slices.InfoFragment;

/**
 * A class that hosts {@link InfoFragment}s for preferences in
 * {@link ResolutionSelectionFragment}.
 */
@Keep
public class ResolutionSelectionInfo {
    public static final String HDR_TYPES_ARRAY = "hdr_types_key";

    /** A class that hosts {@link InfoFragment} for mode specific hdr types description */
    public static class HDRInfoFragment extends ResolutionSelectionInfo.BaseInfoFragment {

        private String hdrTypesAsString(int[] hdrTypes) {
            StringBuilder supportedTypes = new StringBuilder(System.lineSeparator()).append(
                    getResources().getString(R.string.hdr_capability,
                            getResources().getString(R.string.hdr_format_sdr)));
            for (int supportedType : hdrTypes) {
                supportedTypes.append(System.lineSeparator());
                switch (supportedType) {
                    case HDR_TYPE_DOLBY_VISION:
                        supportedTypes
                                .append(getResources().getString(R.string.hdr_capability,
                                        getResources().getString(
                                                R.string.hdr_format_dolby_vision)));
                        break;
                    case HDR_TYPE_HDR10:
                        supportedTypes
                                .append(getResources().getString(R.string.hdr_capability,
                                        getResources().getString(R.string.hdr_format_hdr10)));
                        break;
                    case HDR_TYPE_HLG:
                        supportedTypes
                                .append(getResources().getString(R.string.hdr_capability,
                                        getResources().getString(R.string.hdr_format_hlg)));
                        break;
                    case HDR_TYPE_HDR10_PLUS:
                        supportedTypes
                                .append(getResources().getString(R.string.hdr_capability,
                                        getResources().getString(R.string.hdr_format_hdr10plus)));
                        break;
                }
            }
            return supportedTypes.toString();
        }

        @Override
        public String getSidePanelText() {
            return getResources().getString(R.string.resolution_hdr_description_info,
                    hdrTypesAsString(getArguments().getIntArray(HDR_TYPES_ARRAY)));
        }
    }

    private abstract static class BaseInfoFragment extends InfoFragment {

        protected abstract String getSidePanelText();

        @Override
        public View onCreateView(
                LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            ((TextView) view.findViewById(R.id.info_summary)).setText(getSidePanelText());
            view.findViewById(R.id.info_summary).setVisibility(View.VISIBLE);
            return view;
        }
    }
}
