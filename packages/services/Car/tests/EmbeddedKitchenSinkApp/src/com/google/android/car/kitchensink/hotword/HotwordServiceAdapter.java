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

package com.google.android.car.kitchensink.hotword;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.car.kitchensink.R;

import java.util.Objects;

final class HotwordServiceAdapter extends RecyclerView.Adapter<HotwordServiceViewHolder> implements
        HotwordServiceViewChangeCallback {

    private static final String TAG = "HotwordTestAdapter";
    private final HotwordServiceManager[] mHotwordServiceManagers;

    HotwordServiceAdapter(HotwordServiceManager[] hotwordServiceManagers) {
        mHotwordServiceManagers = Objects.requireNonNull(hotwordServiceManagers,
                "HotwordServiceManagers can not be null");
    }

    @Override
    public HotwordServiceViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.concurrent_hotword_item, viewGroup, false);

        return new HotwordServiceViewHolder(view, this);
    }

    @Override
    public void onBindViewHolder(HotwordServiceViewHolder hotwordServiceViewHolder, int position) {
        mHotwordServiceManagers[position].setServiceUpdatedCallback(hotwordServiceViewHolder);
    }

    @Override
    public int getItemCount() {
        return mHotwordServiceManagers.length;
    }

    public void release() {
        for (HotwordServiceManager manager : mHotwordServiceManagers) {
            manager.release();
        }
    }

    @Override
    public void onStartService(int index) {
        Log.i(TAG, "onStartService " + index);
        if (index >= mHotwordServiceManagers.length) {
            return;
        }
        mHotwordServiceManagers[index].startService();
    }

    @Override
    public void onsStopService(int index) {
        Log.i(TAG, "onsStopService " + index);
        if (index >= mHotwordServiceManagers.length) {
            return;
        }
        mHotwordServiceManagers[index].stopService();
    }

    @Override
    public void onStartRecording(int index) {
        Log.i(TAG, "onStartRecording " + index);
        if (index >= mHotwordServiceManagers.length) {
            return;
        }
        mHotwordServiceManagers[index].startRecording();
    }

    @Override
    public void onStopRecording(int index) {
        Log.i(TAG, "onStopRecording " + index);
        if (index >= mHotwordServiceManagers.length) {
            return;
        }
        mHotwordServiceManagers[index].stopRecording();
    }
}
