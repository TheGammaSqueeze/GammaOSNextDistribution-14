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

import static android.car.media.CarAudioManager.AUDIO_FEATURE_DYNAMIC_ROUTING;
import static android.car.media.CarAudioManager.AUDIO_FEATURE_VOLUME_GROUP_EVENTS;

import android.car.Car;
import android.car.Car.CarServiceLifecycleListener;
import android.car.media.CarAudioManager;
import android.car.media.CarAudioManager.CarVolumeCallback;
import android.car.media.CarVolumeGroupEvent;
import android.car.media.CarVolumeGroupEventCallback;
import android.car.media.CarVolumeGroupInfo;
import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.google.android.car.kitchensink.R;
import com.google.android.material.tabs.TabLayout;

import java.util.List;

import javax.annotation.concurrent.GuardedBy;

public final class VolumeTestFragment extends Fragment {
    private static final String TAG = "CarVolumeTest";
    private static final boolean DEBUG = true;

    private AudioManager mAudioManager;
    private AudioZoneVolumeTabAdapter mAudioZoneAdapter;
    @GuardedBy("mLock")
    private final SparseArray<CarAudioZoneVolumeFragment> mZoneVolumeFragments =
            new SparseArray<>();

    private CarAudioManager mCarAudioManager;
    private Car mCar;

    private SeekBar mFader;
    private SeekBar mBalance;

    private TabLayout mZonesTabLayout;
    private final Object mLock = new Object();

    public static final class CarAudioZoneVolumeInfo {
        public int groupId;
        public String id;
        public int maxGain;
        public int minGain;
        public String currentGain;
        public boolean hasAudioFocus;
        public boolean isMuted;
        public boolean isBlocked;
        public boolean isAttenuated;
        public boolean isSystemMuted;
    }

    private final class CarVolumeChangeListener extends CarVolumeCallback {
        @Override
        public void onGroupVolumeChanged(int zoneId, int groupId, int flags) {
            if (DEBUG) {
                Log.d(TAG, "onGroupVolumeChanged volume changed for zone "
                        + zoneId);
            }
            sendFragmentChangedMessage(zoneId, groupId, flags);
        }

        @Override
        public void onGroupMuteChanged(int zoneId, int groupId, int flags) {
            if (DEBUG) {
                Log.d(TAG, "onGroupMuteChanged mute changed for zone " + zoneId);
            }
            sendFragmentChangedMessage(zoneId, groupId, flags);
        }

        @Override
        public void onMasterMuteChanged(int zoneId, int flags) {
            if (DEBUG) {
                Log.d(TAG, "onMasterMuteChanged master mute "
                        + mAudioManager.isMasterMute());
            }
        }

        private void sendFragmentChangedMessage(int zoneId, int groupId, int flags) {
            CarAudioZoneVolumeFragment fragment;

            synchronized (mLock) {
                fragment = mZoneVolumeFragments.get(zoneId);
            }

            if (fragment != null) {
                fragment.sendVolumeChangedMessage(groupId, flags);
            }
        }
    }

    private  CarVolumeGroupEventCallback mEventCallback = (volumeGroupEvents) -> {
        if (DEBUG) {
            Log.d(TAG, "onVolumeGroupEvent received events: " + volumeGroupEvents);
        }
        sendFragmentChangedMessageForEvents(volumeGroupEvents);
    };

    private final CarVolumeCallback mCarVolumeCallback = new CarVolumeChangeListener();

    private CarServiceLifecycleListener mCarServiceLifecycleListener = (car, ready) -> {
        if (!ready) {
            if (DEBUG) {
                Log.d(TAG, "Disconnect from Car Service");
            }
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "Connected to Car Service");
        }
        mCarAudioManager = (CarAudioManager) car.getCarManager(Car.AUDIO_SERVICE);
        initVolumeInfo();
        if (mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_VOLUME_GROUP_EVENTS)
                && mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING)) {
            mCarAudioManager.registerCarVolumeGroupEventCallback(
                    ContextCompat.getMainExecutor(getActivity().getApplicationContext()),
                    mEventCallback);
        }
        mCarAudioManager.registerCarVolumeCallback(mCarVolumeCallback);
    };

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);

        View v = inflater.inflate(R.layout.volume_test, container, false);

        mZonesTabLayout = v.findViewById(R.id.zones_tab);
        ViewPager viewPager = (ViewPager) v.findViewById(R.id.zone_view_pager);

        mAudioZoneAdapter = new AudioZoneVolumeTabAdapter(getChildFragmentManager());
        viewPager.setAdapter(mAudioZoneAdapter);
        mZonesTabLayout.setupWithViewPager(viewPager);

        SeekBar.OnSeekBarChangeListener seekListener =
                new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                final float percent = (progress - 100) / 100.0f;
                if (seekBar.getId() == R.id.fade_bar) {
                    mCarAudioManager.setFadeTowardFront(percent);
                } else {
                    mCarAudioManager.setBalanceTowardRight(percent);
                }
            }

            public void onStartTrackingTouch(SeekBar seekBar) {}

            public void onStopTrackingTouch(SeekBar seekBar) {}
        };

        mFader = v.findViewById(R.id.fade_bar);
        mFader.setOnSeekBarChangeListener(seekListener);

        mBalance = v.findViewById(R.id.balance_bar);
        mBalance.setOnSeekBarChangeListener(seekListener);

        mCar = Car.createCar(getActivity(), /* handler= */ null,
                Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER, mCarServiceLifecycleListener);
        return v;
    }

    @Override
    public void onDestroyView() {
        if (mCar != null && mCar.isConnected()) {
            mCar.disconnect();
            mCar = null;
        }
        super.onDestroyView();
    }

    private void initVolumeInfo() {
        synchronized (mLock) {
            List<Integer> audioZoneIds = mCarAudioManager.getAudioZoneIds();
            for (int index = 0; index < audioZoneIds.size(); index++) {
                int zoneId = audioZoneIds.get(index);
                CarAudioZoneVolumeFragment fragment =
                        new CarAudioZoneVolumeFragment(zoneId, mCarAudioManager, mAudioManager);
                mZonesTabLayout.addTab(mZonesTabLayout.newTab().setText("Audio Zone " + zoneId));
                mAudioZoneAdapter.addFragment(fragment, "Audio Zone " + zoneId);
                if (DEBUG) {
                    Log.d(TAG, "Adding audio volume for zone " + zoneId);
                }
                mZoneVolumeFragments.put(zoneId, fragment);
            }
        }
    }

    private void sendFragmentChangedMessageForEvents(List<CarVolumeGroupEvent> volumeGroupEvents) {
        CarAudioZoneVolumeFragment fragment;
        for (int index = 0; index < volumeGroupEvents.size(); index++) {
            CarVolumeGroupEvent event = volumeGroupEvents.get(index);
            List<CarVolumeGroupInfo> groupInfos = event.getCarVolumeGroupInfos();

            synchronized (mLock) {
                fragment = mZoneVolumeFragments.get(groupInfos.get(0).getZoneId());
            }

            if (fragment != null) {
                fragment.sendEventReceivedMessage(event);
            }
        }
    }
}
