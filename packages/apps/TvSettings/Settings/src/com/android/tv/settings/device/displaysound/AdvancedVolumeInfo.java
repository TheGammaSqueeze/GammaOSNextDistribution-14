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

package com.android.tv.settings.device.displaysound;

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
 * {@link AdvancedVolumeFragment}.
 */
@Keep
public class AdvancedVolumeInfo {
    /** A class that hosts {@link InfoFragment} for auto option for surround sound setting
     * preference. */
    public static class AutoInfoFragment extends BaseInfoFragment {
        @Override
        protected int getSummaryResId() {
            return R.string.surround_sound_auto_summary;
        }
    }

    /** A class that hosts {@link InfoFragment} for manual option for surround sound setting
     * preference. */
    public static class ManualInfoFragment extends BaseInfoFragment {
        @Override
        protected int getSummaryResId() {
            return R.string.surround_sound_manual_summary;
        }
    }

    private abstract static class BaseInfoFragment extends InfoFragment {

        protected abstract int getSummaryResId();

        @Override
        public View onCreateView(
                LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            ((TextView) view.findViewById(R.id.info_summary)).setText(getSummaryResId());
            view.findViewById(R.id.info_summary).setVisibility(View.VISIBLE);
            return view;
        }
    }
}
