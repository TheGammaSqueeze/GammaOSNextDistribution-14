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

package com.android.tv.settings.privacy;

import static android.hardware.SensorPrivacyManager.TOGGLE_TYPE_HARDWARE;
import static android.hardware.SensorPrivacyManager.TOGGLE_TYPE_SOFTWARE;

import android.content.Context;
import android.hardware.SensorPrivacyManager;
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
 * A {@link InfoFragment} that hosts the preview pane of the sensor fragment when the toggle is
 * focused.
 */
@Keep
public class SensorToggleInfoFragment extends InfoFragment {

    private static final String TAG = "SensorToggleInfoFragment";
    public static final String TOGGLE_EXTRA = "toggle";

    private TextView mTitleView;
    private SensorPrivacyManager mSensorPrivacyManager;

    private PrivacyToggle mToggle;

    private final SensorPrivacyManager.OnSensorPrivacyChangedListener mPrivacyChangedListener =
            (sensor, enabled) -> updateTitle();

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
        mSensorPrivacyManager = (SensorPrivacyManager)
                getContext().getSystemService(Context.SENSOR_PRIVACY_SERVICE);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = super.onCreateView(inflater, container, savedInstanceState);
        ImageView icon = view.findViewById(R.id.info_title_icon);
        icon.setImageResource(R.drawable.ic_info_outline_base);
        icon.setVisibility(View.VISIBLE);

        ((TextView) view.findViewById(R.id.info_summary)).setText(mToggle.toggleInfoText);

        mTitleView = view.findViewById(R.id.info_title);
        mTitleView.setVisibility(View.VISIBLE);
        updateTitle();
        mSensorPrivacyManager.addSensorPrivacyListener(mToggle.sensor,
                mPrivacyChangedListener);
        return view;
    }

    private void updateTitle() {
        if (mTitleView == null) {
            return;
        }

        boolean softwarePrivacyEnabled = mSensorPrivacyManager.isSensorPrivacyEnabled(
                TOGGLE_TYPE_SOFTWARE, mToggle.sensor);
        boolean physicalPrivacyEnabled = mSensorPrivacyManager.isSensorPrivacyEnabled(
                TOGGLE_TYPE_HARDWARE, mToggle.sensor);
        boolean accessAllowed = !softwarePrivacyEnabled && !physicalPrivacyEnabled;
        String toggleState = getString(
                accessAllowed ? R.string.sensor_toggle_info_on : R.string.sensor_toggle_info_off);
        mTitleView.setText(getString(mToggle.toggleInfoTitle, toggleState));
    }

    @Override
    public void onDestroyView() {
        mSensorPrivacyManager.removeSensorPrivacyListener(mToggle.sensor, mPrivacyChangedListener);
        mTitleView = null;
        super.onDestroyView();
    }
}
