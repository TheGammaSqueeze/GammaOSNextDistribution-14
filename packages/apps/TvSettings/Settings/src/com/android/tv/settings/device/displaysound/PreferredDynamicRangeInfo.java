/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tv.settings.R;
import com.android.tv.twopanelsettings.slices.InfoFragment;

/**
 * A class that hosts {@link InfoFragment}s for preferences in
 * {@link PreferredDynamicRangeFragment}.
 */
@Keep
public class PreferredDynamicRangeInfo {
    /** A class that hosts {@link InfoFragment} for Auto hdr selection preference */
    public static class MatchContentDynamicRangeInfoFragment
            extends PreferredDynamicRangeInfo.BaseInfoFragment {
        @Override
        protected int getSummaryResId() {
            return R.string.match_content_dynamic_range_summary;
        }
    }

    /** A class that hosts {@link InfoFragment} for system hdr selection preference */
    public static class PreferredDynamicRangeSystemInfoFragment
            extends PreferredDynamicRangeInfo.BaseInfoFragment {
        @Override
        protected int getSummaryResId() {
            return R.string.preferred_dynamic_range_selection_system_summary;
        }
    }

    /** A class that hosts {@link InfoFragment} for force DV selection preference */
    public static class ForceDVInfoFragment extends PreferredDynamicRangeInfo.ForceInfoFragment {
        @Override
        protected int getSummaryResId() {
            return R.string.dynamic_range_selection_force_dv_summary;
        }

        @Override
        protected int getTitleResId() {
            return R.string.dynamic_range_selection_force_dv_title;
        }
    }

    /** A class that hosts {@link InfoFragment} for force HDR10 selection preference */
    public static class ForceHdrInfoFragment extends PreferredDynamicRangeInfo.ForceInfoFragment {
        @Override
        protected int getSummaryResId() {
            return R.string.dynamic_range_selection_force_hdr10_summary;
        }

        @Override
        protected int getTitleResId() {
            return R.string.dynamic_range_selection_force_hdr10_title;
        }
    }

    /** A class that hosts {@link InfoFragment} for force HLG selection preference */
    public static class ForceHlgInfoFragment extends PreferredDynamicRangeInfo.ForceInfoFragment {
        @Override
        protected int getSummaryResId() {
            return R.string.dynamic_range_selection_force_hlg_summary;
        }

        @Override
        protected int getTitleResId() {
            return R.string.dynamic_range_selection_force_hlg_title;
        }
    }

    /** A class that hosts {@link InfoFragment} for force HDR10+ selection preference */
    public static class ForceHdr10PlusInfoFragment
            extends PreferredDynamicRangeInfo.ForceInfoFragment {
        @Override
        protected int getSummaryResId() {
            return R.string.dynamic_range_selection_force_hdr10plus_summary;
        }

        @Override
        protected int getTitleResId() {
            return R.string.dynamic_range_selection_force_hdr10plus_title;
        }
    }

    /** A class that hosts {@link InfoFragment} for force SDR selection preference */
    public static class ForceSdrInfoFragment extends PreferredDynamicRangeInfo.BaseInfoFragment {
        @Override
        protected int getSummaryResId() {
            return R.string.dynamic_range_selection_force_sdr_summary;
        }

        @Override
        public View onCreateView(
                LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            ((TextView) view.findViewById(R.id.info_title)).setText(
                    R.string.dynamic_range_selection_force_sdr_title);
            view.findViewById(R.id.info_title).setVisibility(View.VISIBLE);
            ((TextView) view.findViewById(R.id.info_summary)).setText(getSummaryResId());
            view.findViewById(R.id.info_summary).setVisibility(View.VISIBLE);
            return view;
        }
    }

    /** A class that hosts {@link InfoFragment} for force hdr selection preference */
    public abstract static class ForceInfoFragment extends BaseInfoFragment {
        protected abstract int getSummaryResId();

        protected abstract int getTitleResId();

        @Override
        public View onCreateView(
                LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            ((TextView) view.findViewById(R.id.info_title)).setText(getTitleResId());
            view.findViewById(R.id.info_title).setVisibility(View.VISIBLE);
            ((TextView) view.findViewById(R.id.info_summary)).setText(getSummaryResId());
            view.findViewById(R.id.info_summary).setVisibility(View.VISIBLE);
            return view;
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
