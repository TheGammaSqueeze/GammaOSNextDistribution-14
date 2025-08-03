/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tv.settings.privacy;

import android.content.Context;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.android.tv.settings.R;
import com.android.tv.twopanelsettings.slices.InfoFragment;


/**
 * A {@link InfoFragment} that hosts the preview pane of the physical privacy enabled info box when
 * it is focused.
 */
@Keep
public class PhysicalPrivacyUnblockInfoFragment extends InfoFragment {

    private static final String TAG = "PhysicalPrivacyUnblockInfoFragment";
    public static final String TOGGLE_EXTRA = "toggle";

    private PrivacyToggle mToggle;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mToggle = (PrivacyToggle) getArguments().get(TOGGLE_EXTRA);
        if (mToggle == null) {
            Log.e(TAG, "toggle not set as an extra");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = super.onCreateView(inflater, container, savedInstanceState);

        ImageView infoImage = view.findViewById(R.id.info_image);
        infoImage.setImageResource(mToggle.physicalPrivacyEnabledInfoPanelImage);

        ImageView icon = view.findViewById(R.id.info_title_icon);
        icon.setImageResource(R.drawable.ic_info_outline_base);
        icon.setVisibility(View.VISIBLE);

        TextView titleView = view.findViewById(R.id.info_title);
        titleView.setVisibility(View.VISIBLE);
        titleView.setText(mToggle.physicalPrivacyEnabledInfoTitle);

        TextView infoSummary = view.findViewById(R.id.info_summary);
        infoSummary.setText(mToggle.physicalPrivacyEnabledInfoPanelText);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        ImageView infoImage = getView().findViewById(R.id.info_image);
        if (infoImage != null) {
            Drawable image = infoImage.getDrawable();
            if (image instanceof Animatable) {
                ((Animatable) image).start();
            }
        }
    }
}
