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

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RadioTunerTabAdapter extends FragmentStateAdapter {

    private static final String TAG = RadioTunerTabAdapter.class.getSimpleName();
    private static final int INDEX_TITLE_NOT_FOUND = -1;

    private final List<Fragment> mFragmentList = new ArrayList<>();
    private final List<String> mFragmentTitleList = new ArrayList<>();
    RadioTunerTabAdapter(Fragment fm) {
        super(fm);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        Fragment fragment = mFragmentList.get(position);
        return fragment;
    }

    @Override
    public int getItemCount() {
        return mFragmentList.size();
    }

    void addFragment(Fragment fragment, String title) {
        if (findTitleIdx(title) != INDEX_TITLE_NOT_FOUND) {
            Log.e(TAG, "Tuner title " + title + " already exists");
            return;
        }
        mFragmentList.add(fragment);
        mFragmentTitleList.add(title);
    }

    void removeFragment(String title) {
        int titleIdx = findTitleIdx(title);
        if (titleIdx == INDEX_TITLE_NOT_FOUND) {
            Log.e(TAG, "Tuner title " + title + " does not exist");
            return;
        }
        mFragmentList.remove(titleIdx);
        mFragmentTitleList.remove(titleIdx);
    }

    CharSequence getPageTitle(int position) {
        return mFragmentTitleList.get(position);
    }

    private int findTitleIdx(String title) {
        for (int i = 0; i < mFragmentTitleList.size(); i++) {
            if (Objects.equals(mFragmentTitleList.get(i), title)) {
                return i;
            }
        }
        return INDEX_TITLE_NOT_FOUND;
    }
}
