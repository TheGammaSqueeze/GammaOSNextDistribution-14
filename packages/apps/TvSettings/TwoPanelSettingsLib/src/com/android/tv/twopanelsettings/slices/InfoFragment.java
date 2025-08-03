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

package com.android.tv.twopanelsettings.slices;


import android.content.res.Resources;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.android.tv.twopanelsettings.R;

/**
 * Fragment to display informational image and description text for slice.
 */
public class InfoFragment extends Fragment {
    public static final String EXTRA_INFO_HAS_STATUS = "extra_info_has_status";

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.info_fragment, container, false);
        updateInfo(view);
        return view;
    }

    /** Update the infos in InfoFragment **/
    public void updateInfoFragment() {
        updateInfo(getView());
    }

    private void updateInfo(View view) {
        Icon image = getArguments().getParcelable(SlicesConstants.EXTRA_PREFERENCE_INFO_IMAGE);
        Icon titleIcon = getArguments().getParcelable(
                SlicesConstants.EXTRA_PREFERENCE_INFO_TITLE_ICON);
        String title = getArguments().getString(SlicesConstants.EXTRA_PREFERENCE_INFO_TEXT);
        String summary = getArguments().getString(SlicesConstants.EXTRA_PREFERENCE_INFO_SUMMARY);
        boolean hasStatus = getArguments().getBoolean(EXTRA_INFO_HAS_STATUS);
        boolean status = getArguments().getBoolean(SlicesConstants.EXTRA_PREFERENCE_INFO_STATUS);

        Resources res = getResources();
        ImageView infoImage = view.findViewById(R.id.info_image);
        ImageView infoIcon = view.findViewById(R.id.info_title_icon);
        TextView infoTitle = view.findViewById(R.id.info_title);
        TextView infoStatus = view.findViewById(R.id.info_status);
        TextView infoSummary = view.findViewById(R.id.info_summary);

        if (image != null) {
            infoImage.setImageDrawable(image.loadDrawable(getContext()));
        }
        if (titleIcon != null) {
            infoIcon.setImageDrawable(titleIcon.loadDrawable(getContext()));
            infoIcon.setVisibility(View.VISIBLE);
        } else {
            infoIcon.setVisibility(View.GONE);
        }
        if (title != null) {
            infoTitle.setText(title);
            infoTitle.setVisibility(View.VISIBLE);
            if (hasStatus) {
                infoStatus.setVisibility(View.VISIBLE);
                if (infoIcon.getVisibility() == View.VISIBLE) {
                    infoTitle.setMaxWidth(
                            res.getDimensionPixelSize(
                                    R.dimen.settings_info_fragment_title_text_max_width_short));
                } else {
                    infoTitle.setMaxWidth(
                            res.getDimensionPixelSize(
                                    R.dimen.settings_info_fragment_title_text_max_width_long));
                }
                if (status) {
                    infoStatus.setTextColor(getResources().getColor(R.color.info_status_on));
                    infoStatus.setText(getString(R.string.info_status_on));
                } else {
                    infoStatus.setTextColor(getResources().getColor(R.color.info_status_off));
                    infoStatus.setText(getString(R.string.info_status_off));
                }
            } else {
                infoStatus.setVisibility(View.GONE);
                // Reset maxWidth to allow the TextView to utilize the full width of its container.
                infoTitle.setMaxWidth(Integer.MAX_VALUE);
            }
        } else {
            infoTitle.setVisibility(View.GONE);
            infoStatus.setVisibility(View.GONE);
        }
        if (summary != null) {
            infoSummary.setText(summary);
        }
    }
}
