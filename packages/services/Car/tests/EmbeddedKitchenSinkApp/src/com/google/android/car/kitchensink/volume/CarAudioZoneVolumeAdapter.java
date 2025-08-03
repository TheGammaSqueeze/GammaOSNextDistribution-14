/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.google.android.car.kitchensink.volume;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.google.android.car.kitchensink.R;
import com.google.android.car.kitchensink.volume.VolumeTestFragment.CarAudioZoneVolumeInfo;

public final class CarAudioZoneVolumeAdapter extends ArrayAdapter<CarAudioZoneVolumeInfo> {

    private final Context mContext;
    private final boolean mGroupMuteEnabled;
    private CarAudioZoneVolumeInfo[] mVolumeList;
    private final int mLayoutResourceId;
    private CarAudioZoneVolumeFragment mFragment;

    public CarAudioZoneVolumeAdapter(Context context,
            int layoutResourceId, CarAudioZoneVolumeInfo[] volumeList,
            CarAudioZoneVolumeFragment fragment, boolean groupMuteEnabled) {
        super(context, layoutResourceId, volumeList);
        mFragment = fragment;
        mContext = context;
        mLayoutResourceId = layoutResourceId;
        mVolumeList = volumeList;
        mGroupMuteEnabled = groupMuteEnabled;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder vh = new ViewHolder();
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            convertView = inflater.inflate(mLayoutResourceId, parent, false);
            vh.id = convertView.findViewById(R.id.stream_id);
            vh.maxVolume = convertView.findViewById(R.id.volume_limit);
            vh.currentVolume = convertView.findViewById(R.id.current_volume);
            vh.isMuted = convertView.findViewById(R.id.is_muted);
            vh.isBlocked = convertView.findViewById(R.id.is_blocked);
            vh.isAttenuated = convertView.findViewById(R.id.is_attenuated);
            vh.isSystemMuted = convertView.findViewById(R.id.is_hal_muted);
            vh.muteButton = convertView.findViewById(R.id.volume_mute);
            vh.upButton = convertView.findViewById(R.id.volume_up);
            vh.downButton = convertView.findViewById(R.id.volume_down);
            vh.requestButton = convertView.findViewById(R.id.request);
            convertView.setTag(vh);
        } else {
            vh = (ViewHolder) convertView.getTag();
        }
        if (mVolumeList[position] != null) {
            vh.id.setText(mVolumeList[position].id);
            vh.currentVolume.setText(String.valueOf(mVolumeList[position].currentGain));
            int color = mVolumeList[position].hasAudioFocus ? Color.GREEN : Color.GRAY;
            vh.requestButton.setBackgroundColor(color);
            if (position == 0) {
                vh.maxVolume.setText("Max");
                vh.isMuted.setText(String.valueOf("Muted?"));
                vh.isAttenuated.setText(String.valueOf("Attenuated?"));
                vh.isBlocked.setText(String.valueOf("Blocked?"));
                vh.isSystemMuted.setText(String.valueOf("System Muted?"));
                vh.upButton.setVisibility(View.INVISIBLE);
                vh.downButton.setVisibility(View.INVISIBLE);
                vh.requestButton.setVisibility(View.INVISIBLE);
                vh.muteButton.setVisibility(View.INVISIBLE);
            } else {
                vh.maxVolume.setText(String.valueOf(mVolumeList[position].maxGain));
                vh.isMuted.setText(String.valueOf(mVolumeList[position].isMuted));
                vh.isAttenuated.setText(String.valueOf(mVolumeList[position].isAttenuated));
                vh.isBlocked.setText(String.valueOf(mVolumeList[position].isBlocked));
                vh.isSystemMuted.setText(String.valueOf(mVolumeList[position].isSystemMuted));
                vh.upButton.setVisibility(View.VISIBLE);
                vh.downButton.setVisibility(View.VISIBLE);
                vh.requestButton.setVisibility(View.VISIBLE);
                vh.muteButton.setVisibility(mGroupMuteEnabled ? View.VISIBLE : View.INVISIBLE);
                vh.upButton.setOnClickListener((view) -> {
                    mFragment.adjustVolumeUp(mVolumeList[position].groupId);
                });
                vh.downButton.setOnClickListener((view) -> {
                    mFragment.adjustVolumeDown(mVolumeList[position].groupId);
                });
                vh.muteButton.setChecked(mVolumeList[position].isMuted);
                vh.muteButton.setOnClickListener((view) -> {
                    mFragment.toggleMute(mVolumeList[position].groupId);
                });

                vh.requestButton.setOnClickListener((view) -> {
                    mFragment.requestFocus(mVolumeList[position].groupId);
                });
            }
        }
        return convertView;
    }

    @Override
    public int getCount() {
        return mVolumeList.length;
    }

    public void refreshVolumes(CarAudioZoneVolumeInfo[] volumes) {
        mVolumeList = volumes;
        notifyDataSetChanged();
    }

    private static final class ViewHolder {
        public TextView id;
        public TextView maxVolume;
        public TextView currentVolume;
        public TextView isMuted;
        public TextView isBlocked;
        public TextView isAttenuated;
        public TextView isSystemMuted;
        public ToggleButton muteButton;
        public Button upButton;
        public Button downButton;
        public Button requestButton;
    }
}
