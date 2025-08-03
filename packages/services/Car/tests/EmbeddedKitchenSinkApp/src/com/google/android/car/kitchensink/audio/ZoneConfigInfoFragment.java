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

package com.google.android.car.kitchensink.audio;

import static com.google.android.car.kitchensink.R.layout.zone_config_item;
import static com.google.android.car.kitchensink.R.string.config_name;
import static com.google.android.car.kitchensink.R.string.generic_name_and_id;

import android.car.feature.Flags;
import android.car.media.CarAudioZoneConfigInfo;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.R;

import javax.annotation.concurrent.GuardedBy;

public final class ZoneConfigInfoFragment extends Fragment {
    private TextView mDefaultTextView;
    private TextView mActiveTextView;
    private TextView mSelectedTextView;
    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private CarAudioZoneConfigInfo mInfo;

    public ZoneConfigInfoFragment(CarAudioZoneConfigInfo info) {
        mInfo = info;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Context context = getContext();
        CarAudioZoneConfigInfo info;
        synchronized (mLock) {
            info = mInfo;
        }

        View view = inflater.inflate(zone_config_item, container, /* attachToRoot= */ false);
        TextView nameView = view.findViewById(R.id.config_name);
        TextView zoneView = view.findViewById(R.id.zone_id);
        TextView configView = view.findViewById(R.id.config_id);
        mDefaultTextView = view.findViewById(R.id.config_default_status);
        mActiveTextView = view.findViewById(R.id.config_active_status);
        mSelectedTextView = view.findViewById(R.id.config_selected_status);
        nameView.setText(context.getString(config_name, info.getName()));
        zoneView.setText(context.getString(generic_name_and_id, "Zone id", info.getZoneId()));
        configView.setText(context.getString(generic_name_and_id, "Config id", info.getConfigId()));
        updateConfigInfoInternal(info, context);

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    boolean isSameConfig(CarAudioZoneConfigInfo info) {
        synchronized (mLock) {
            return Flags.carAudioDynamicDevices()
                    ? mInfo.hasSameConfigInfo(info) : mInfo.equals(info);
        }
    }
    void updateConfigInfo(CarAudioZoneConfigInfo info) {
        synchronized (mLock) {
            mInfo = info;
        }
        Context context = getContext();
        // Not yet visible
        if (context == null) {
            return;
        }
        updateConfigInfoInternal(info, context);
    }

    private void updateConfigInfoInternal(CarAudioZoneConfigInfo info, Context context) {
        if (Flags.carAudioDynamicDevices()) {
            mDefaultTextView.setText(context.getString(R.string.generic_status_name_and_status,
                    "Default", info.isDefault()));
        } else {
            mDefaultTextView.setVisibility(View.GONE);
        }
        mSelectedTextView.setText(context.getString(R.string.generic_status_name_and_status,
                "Selected", info.isSelected()));
        mActiveTextView.setText(context.getString(R.string.generic_status_name_and_status,
                "Active", info.isActive()));
    }
}
