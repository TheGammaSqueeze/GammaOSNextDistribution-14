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

import android.car.media.CarAudioManager;
import android.car.media.CarAudioZoneConfigInfo;
import android.util.SparseArray;
import android.view.View;

import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.car.kitchensink.R;
import com.google.android.material.tabs.TabLayout;

import java.util.List;

final class AudioConfigurationInformationTabs {

    private final SparseArray<ZoneConfigInfoFragment> mConfigIdToFragments;
    private final int mZoneId;
    private final CarAudioZoneTabAdapter mAudioPlayerZoneAdapter;

    AudioConfigurationInformationTabs(View view, CarAudioManager carAudioManager,
            FragmentManager fragmentManager, int zoneId) {
        mZoneId = zoneId;
        TabLayout playerTabLayout = view.findViewById(R.id.zone_configs_tabs);
        ViewPager viewPager = view.findViewById(R.id.zones_configs_view_pager);
        mAudioPlayerZoneAdapter = new CarAudioZoneTabAdapter(fragmentManager);
        viewPager.setAdapter(mAudioPlayerZoneAdapter);
        List<CarAudioZoneConfigInfo> configs = carAudioManager.getAudioZoneConfigInfos(zoneId);
        mConfigIdToFragments = new SparseArray<>(configs.size());
        for (CarAudioZoneConfigInfo info : configs) {
            ZoneConfigInfoFragment fragment = new ZoneConfigInfoFragment(info);
            mAudioPlayerZoneAdapter.addFragment(fragment, info.getName());
            mConfigIdToFragments.put(info.getConfigId(), fragment);
        }
        mAudioPlayerZoneAdapter.notifyDataSetChanged();
        playerTabLayout.setupWithViewPager(viewPager);
    }

    void updateConfigs(List<CarAudioZoneConfigInfo> infos) {
        boolean updated = false;
        for (CarAudioZoneConfigInfo info : infos) {
            if (info.getZoneId() != mZoneId) {
                continue;
            }
            ZoneConfigInfoFragment fragment = mConfigIdToFragments.get(info.getConfigId());
            if (fragment == null || !fragment.isSameConfig(info)) {
                continue;
            }
            fragment.updateConfigInfo(info);
            updated = true;
        }
        if (!updated) {
            return;
        }
        mAudioPlayerZoneAdapter.notifyDataSetChanged();
    }
}
