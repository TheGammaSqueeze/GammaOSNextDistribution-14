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

import android.view.View;

import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.car.kitchensink.R;
import com.google.android.material.tabs.TabLayout;

public final class AudioPlayersTabControllers {

    private AudioPlayersTabControllers() {
        throw new UnsupportedOperationException();
    }

    static void setUpAudioPlayersTab(View view, FragmentManager fragmentManager) {
        TabLayout playerTabLayout = view.findViewById(R.id.audio_player_tabs);
        ViewPager viewPager = view.findViewById(R.id.zones_player_view_pager);
        CarAudioZoneTabAdapter audioPlayerZoneAdapter = new CarAudioZoneTabAdapter(fragmentManager);
        viewPager.setAdapter(audioPlayerZoneAdapter);
        audioPlayerZoneAdapter.addFragment(new AudioSystemPlayerFragment(), "System Players");
        audioPlayerZoneAdapter.addFragment(new AudioTransientPlayersFragment(), "Sound Players");
        audioPlayerZoneAdapter.notifyDataSetChanged();
        playerTabLayout.setupWithViewPager(viewPager);
    }
}
