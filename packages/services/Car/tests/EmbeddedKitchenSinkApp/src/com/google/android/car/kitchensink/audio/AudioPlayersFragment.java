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

import static com.google.android.car.kitchensink.R.layout.audio_player_tab;
import static com.google.android.car.kitchensink.R.raw.one2six;

import static java.util.Collections.EMPTY_LIST;

import android.media.AudioAttributes;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

import com.android.internal.widget.LinearLayoutManager;
import com.android.internal.widget.RecyclerView;

import com.google.android.car.kitchensink.R;

import java.util.ArrayList;
import java.util.List;

public class AudioPlayersFragment extends Fragment {
    private RecyclerView mRecyclerView;
    private PlayerAdapter mSystemPlayerAdapter;
    private final List<AudioPlayer> mPlayers = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(audio_player_tab, container, /* attachToRoot= */ false);
        initPlayers();

        mRecyclerView = view.findViewById(R.id.players_view);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        mSystemPlayerAdapter = new PlayerAdapter(mPlayers);
        mRecyclerView.setAdapter(mSystemPlayerAdapter);
        mRecyclerView.scrollToPosition(0);

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        for (int index = 0; index < mPlayers.size(); index++) {
            mPlayers.get(index).stop();
        }
        mPlayers.clear();
    }

    private void initPlayers() {
        List<Integer> usages = getUsages();
        for (int index = 0; index < usages.size(); index++) {
            int usage = usages.get(index);
            mPlayers.add(getCarSoundsPlayer(usage));
        }
    }

    private AudioPlayer getCarSoundsPlayer(int usage) {
        AudioAttributes.Builder builder = new AudioAttributes.Builder();
        if (AudioAttributes.isSystemUsage(usage)) {
            builder.setSystemUsage(usage);
        } else {
            builder.setUsage(usage);
        }

        return new AudioPlayer(getContext(), getPlayerResource(usage), builder.build());
    }

    int getPlayerResource(int usage) {
        return one2six;
    }

    List<Integer> getUsages() {
        return EMPTY_LIST;
    }
}
