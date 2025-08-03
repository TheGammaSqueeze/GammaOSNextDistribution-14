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

package com.android.car.audio;

import static android.car.media.CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED;

import static com.android.car.audio.GainBuilder.DEFAULT_GAIN;
import static com.android.car.audio.GainBuilder.MAX_GAIN;
import static com.android.car.audio.GainBuilder.STEP_SIZE;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.car.media.CarVolumeGroupInfo;
import android.media.AudioAttributes;
import android.util.ArrayMap;
import android.util.SparseArray;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Class to build mock volume group
 */
public final class VolumeGroupBuilder {

    private static final int TEST_MIN_VOLUME = 0;
    private static final int TEST_MAX_VOLUME = MAX_GAIN / STEP_SIZE;
    private static final int TEST_MIN_ACTIVATION_VOLUME = 0;
    private static final int TEST_MAX_ACTIVATION_VOLUME = MAX_GAIN / STEP_SIZE;
    private SparseArray<String> mDeviceAddresses = new SparseArray<>();
    private CarAudioDeviceInfo mCarAudioDeviceInfoMock;
    private ArrayMap<String, List<Integer>> mUsagesDeviceAddresses = new ArrayMap<>();
    private String mName;
    private boolean mIsMuted;
    private int mZoneId;
    private int mId;
    private boolean mIsActive = true;

    /**
     * Add name for volume group
     */
    public VolumeGroupBuilder setName(String name) {
        mName = name;
        return this;
    }

    /**
     * Add devices address for context
     */
    public VolumeGroupBuilder addDeviceAddressAndContexts(@CarAudioContext.AudioContext int context,
            String address) {
        mDeviceAddresses.put(context, address);
        return this;
    }

    /**
     * Set devices address with usage
     */
    public VolumeGroupBuilder addDeviceAddressAndUsages(int usage, String address) {
        if (!mUsagesDeviceAddresses.containsKey(address)) {
            mUsagesDeviceAddresses.put(address, new ArrayList<>());
        }
        mUsagesDeviceAddresses.get(address).add(usage);
        return this;
    }

    /**
     * Add mocked car audio device info
     */
    public VolumeGroupBuilder addCarAudioDeviceInfoMock(CarAudioDeviceInfo infoMock) {
        mCarAudioDeviceInfoMock = infoMock;
        return this;
    }

    /**
     * Sets volume group is muted
     */
    public VolumeGroupBuilder setIsMuted(boolean isMuted) {
        mIsMuted = isMuted;
        return this;
    }

    public VolumeGroupBuilder setZoneId(int zoneId) {
        mZoneId = zoneId;
        return this;
    }

    public VolumeGroupBuilder setGroupId(int groupId) {
        mId = groupId;
        return this;
    }

    /**
     * Sets is active for volume group
     */
    public VolumeGroupBuilder setIsActive(boolean isActive) {
        mIsActive = isActive;
        return this;
    }

    /**
     * Builds car volume group
     */
    public CarVolumeGroup build() {
        CarVolumeGroup carVolumeGroup = mock(CarVolumeGroup.class);
        Map<String, ArrayList<Integer>> addressToContexts = new ArrayMap<>();
        @CarAudioContext.AudioContext int[] contexts = new int[mDeviceAddresses.size()];

        for (int index = 0; index < mDeviceAddresses.size(); index++) {
            @CarAudioContext.AudioContext int context = mDeviceAddresses.keyAt(index);
            String address = mDeviceAddresses.get(context);
            when(carVolumeGroup.getAddressForContext(context)).thenReturn(address);
            if (!addressToContexts.containsKey(address)) {
                addressToContexts.put(address, new ArrayList<>());
            }
            addressToContexts.get(address).add(context);
            contexts[index] = context;
        }

        for (int index = 0; index < mUsagesDeviceAddresses.size(); index++) {
            String address = mUsagesDeviceAddresses.keyAt(index);
            List<Integer> usagesForAddress = mUsagesDeviceAddresses.get(address);
            when(carVolumeGroup.getAllSupportedUsagesForAddress(eq(address)))
                    .thenReturn(usagesForAddress);
        }

        when(carVolumeGroup.getContexts()).thenReturn(contexts);

        for (String address : addressToContexts.keySet()) {
            when(carVolumeGroup.getContextsForAddress(address))
                    .thenReturn(ImmutableList.copyOf(addressToContexts.get(address)));
        }
        when(carVolumeGroup.getAddresses())
                .thenReturn(ImmutableList.copyOf(addressToContexts.keySet()));

        when(carVolumeGroup.getCarAudioDeviceInfoForAddress(any()))
                .thenReturn(mCarAudioDeviceInfoMock);

        if (mName != null) {
            when(carVolumeGroup.getName()).thenReturn(mName);
        }
        when(carVolumeGroup.isMuted()).thenReturn(mIsMuted);

        when(carVolumeGroup.getId()).thenReturn(mId);

        when(carVolumeGroup.getCarVolumeGroupInfo()).thenReturn(new CarVolumeGroupInfo.Builder(
                "Name: " + mName, mZoneId, mId).setMinVolumeGainIndex(TEST_MIN_VOLUME)
                .setMaxVolumeGainIndex(TEST_MAX_VOLUME)
                .setVolumeGainIndex(DEFAULT_GAIN / STEP_SIZE)
                .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_VOLUME)
                .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_VOLUME).build());

        when(carVolumeGroup.calculateNewGainStageFromDeviceInfos())
                .thenReturn(EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED);

        when(carVolumeGroup.isActive()).thenReturn(mIsActive);

        when(carVolumeGroup.audioDevicesAdded(anyList())).thenReturn(!mIsActive);
        when(carVolumeGroup.audioDevicesRemoved(anyList())).thenReturn(!mIsActive);
        when(carVolumeGroup.validateDeviceTypes(any())).thenReturn(true);

        for (int index = 0; index < mUsagesDeviceAddresses.size(); index++) {
            List<Integer> usagesForAddress = mUsagesDeviceAddresses.valueAt(index);
            for (int usageId = 0; usageId < usagesForAddress.size(); usageId++) {
                AudioAttributes audioAttributes = CarAudioContext.getAudioAttributeFromUsage(
                        usagesForAddress.get(usageId));
                when(carVolumeGroup.hasAudioAttributes(audioAttributes)).thenReturn(true);
            }
        }

        return carVolumeGroup;
    }
}
