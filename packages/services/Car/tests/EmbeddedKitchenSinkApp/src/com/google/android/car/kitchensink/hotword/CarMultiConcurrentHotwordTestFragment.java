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

import static com.google.android.car.kitchensink.R.id.services_recycler_view;
import static com.google.android.car.kitchensink.R.layout;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public final class CarMultiConcurrentHotwordTestFragment extends Fragment {

    private static final String TAG = "HotwordTestFragment";

    public static final String HOTWORD_DETECTION_SERVICE_PACKAGE =
            "com.android.car.test.one.hotworddetectionservice";

    public static final String HOTWORD_DETECTION_SERVICE_ONE =
            "com.android.car.test.one.hotworddetectionservice.HotwordDetectionServiceOne";

    private RecyclerView mRecyclerView;
    private HotwordServiceAdapter mHotwordServiceAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        Log.i(TAG, "onCreateView");
        View view = inflater
                .inflate(layout.concurrent_hotword, container, /* attachToRoot= */ false);

        mHotwordServiceAdapter = new HotwordServiceAdapter(createServiceManagers());

        mRecyclerView = view.findViewById(services_recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mRecyclerView.scrollToPosition(0);
        mRecyclerView.setAdapter(mHotwordServiceAdapter);

        return view;
    }

    private HotwordServiceManager[] createServiceManagers() {
        Context context = getContext();
        AudioManager audioManager = context.getSystemService(AudioManager.class);
        PackageManager packageManager = context.getPackageManager();
        ComponentName componentNameOne = new ComponentName(HOTWORD_DETECTION_SERVICE_PACKAGE,
                HOTWORD_DETECTION_SERVICE_ONE);

        return new HotwordServiceManager[] {
                new HotwordServiceManager(componentNameOne, context, audioManager, packageManager)
        };
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.i(TAG, "onDestroyView");

        mHotwordServiceAdapter.release();
    }
}
