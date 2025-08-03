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

package com.google.android.car.kitchensink.radio;

import android.hardware.radio.RadioManager;
import android.os.Handler;
import android.util.SparseArray;
import android.view.View;

import com.google.android.car.kitchensink.R;

public final class DabTunerFragment extends RadioTunerFragment {

    private final SparseArray<String> mDabFrequencyToLabelMap;

    DabTunerFragment(RadioManager radioManager, SparseArray<String> dabFrequencyTable,
                     int moduleId, Handler handler, RadioTestFragment.TunerListener tunerListener) {
        super(radioManager, moduleId, handler, tunerListener);
        mDabFrequencyToLabelMap = dabFrequencyTable;
    }

    @Override
    void setupTunerView(View view) {
        mProgramInfoAdapter = new DabProgramInfoAdapter(getContext(), R.layout.program_info_item,
                new RadioManager.ProgramInfo[]{}, this, mDabFrequencyToLabelMap);
    }

    @Override
    CharSequence getChannelName(RadioManager.ProgramInfo info) {
        return RadioTestFragmentUtils.getDabChannelName(info, mDabFrequencyToLabelMap);
    }
}
