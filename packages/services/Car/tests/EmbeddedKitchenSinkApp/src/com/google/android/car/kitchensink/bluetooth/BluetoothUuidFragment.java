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

package com.google.android.car.kitchensink.bluetooth;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.car.kitchensink.R;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.Arrays;
import java.util.List;

public class BluetoothUuidFragment extends Fragment {
    private static final String TAG = "CAR.BLUETOOTH.KS";

    private final List<FragmentTabEntry> mTabFragments = Arrays.asList(
            new FragmentTabEntry("BT device", BluetoothDeviceFragment.class),
            new FragmentTabEntry("Tx Custom Uuids EIR", CustomUuidEirFragment.class));

    TabFragmentStateAdapter mTabFragmentStateAdapter;
    ViewPager2 mViewPager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.bluetooth_uuid, container, false);

        mTabFragmentStateAdapter = new TabFragmentStateAdapter(this);
        mViewPager = v.findViewById(R.id.pager);
        mViewPager.setAdapter(mTabFragmentStateAdapter);

        // Link the {@link TabLayout} to the {@link ViewPager2}, and attach it.
        TabLayout tabLayout = v.findViewById(R.id.tab_layout);
        tabLayout.setTabMode(TabLayout.MODE_SCROLLABLE);
        new TabLayoutMediator(tabLayout, mViewPager,
                (tab, position) -> tab.setText(mTabFragments.get(position).getText())
        ).attach();

        return v;
    }

    private final class FragmentTabEntry<T extends Fragment> {
        private final String mTabText;
        final Class<T> mFragmentClass;
        private T mFragment = null;

        FragmentTabEntry(String text, Class<T> clazz) {
            mTabText = text;
            mFragmentClass = clazz;
        }

        String getText() {
            return mTabText;
        }

        T getFragment() {
            if (mFragment == null) {
                try {
                    mFragment = mFragmentClass.newInstance();
                } catch (java.lang.InstantiationException | java.lang.IllegalAccessException e) {
                    Log.e(TAG, "FragmentTabEntry unable to create fragment:", e);
                }
            }
            return mFragment;
        }
    }

    private final class TabFragmentStateAdapter extends FragmentStateAdapter {
        TabFragmentStateAdapter(Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            Fragment fragment = mTabFragments.get(position).getFragment();
            return fragment;
        }

        @Override
        public int getItemCount() {
            return mTabFragments.size();
        }
    }
}
