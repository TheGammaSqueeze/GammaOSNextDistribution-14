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

package androidx.core.uwb.backend.impl.internal;

import androidx.annotation.NonNull;

import com.google.common.collect.ImmutableList;

import java.util.List;

/** Ranging parameters that exposed through public API. */
public class RangingParameters {
    @Utils.UwbConfigId
    private final int mUwbConfigId;
    private final int mSessionId;
    private final int mSubSessionId;
    private final byte[] mSessionKeyInfo;
    private final byte[] mSubSessionKeyInfo;
    private final UwbComplexChannel mComplexChannel;
    private final ImmutableList<UwbAddress> mPeerAddresses;
    @Utils.RangingUpdateRate
    private final int mRangingUpdateRate;
    @NonNull
    private final UwbRangeDataNtfConfig mUwbRangeDataNtfConfig;
    @Utils.SlotDuration
    private final int mSlotDuration;
    private final boolean mIsAoaDisabled;

    public RangingParameters(
            @Utils.UwbConfigId int uwbConfigId,
            int sessionId,
            int subSessionId,
            byte[] sessionKeyInfo,
            byte[] subSessionKeyInfo,
            UwbComplexChannel complexChannel,
            List<UwbAddress> peerAddresses,
            @Utils.RangingUpdateRate int rangingUpdateRate,
            @NonNull UwbRangeDataNtfConfig uwbRangeDataNtfConfig,
            @Utils.SlotDuration int slotDuration,
            boolean isAoaDisabled) {
        mUwbConfigId = uwbConfigId;
        mSessionId = sessionId;
        mSubSessionId = subSessionId;
        mSessionKeyInfo = sessionKeyInfo;
        mSubSessionKeyInfo = subSessionKeyInfo;
        mComplexChannel = complexChannel;
        mPeerAddresses = ImmutableList.copyOf(peerAddresses);
        mRangingUpdateRate = rangingUpdateRate;
        mUwbRangeDataNtfConfig = uwbRangeDataNtfConfig;
        mSlotDuration = slotDuration;
        mIsAoaDisabled = isAoaDisabled;
    }

    public int getSessionId() {
        return mSessionId;
    }

    public int getSubSessionId() {
        return mSubSessionId;
    }

    @Utils.UwbConfigId
    public int getUwbConfigId() {
        return mUwbConfigId;
    }

    public byte[] getSessionKeyInfo() {
        return mSessionKeyInfo;
    }

    public byte[] getSubSessionKeyInfo() {
        return mSubSessionKeyInfo;
    }

    public UwbComplexChannel getComplexChannel() {
        return mComplexChannel;
    }

    public ImmutableList<UwbAddress> getPeerAddresses() {
        return mPeerAddresses;
    }

    public int getRangingUpdateRate() {
        return mRangingUpdateRate;
    }

    public UwbRangeDataNtfConfig getUwbRangeDataNtfConfig() {
        return mUwbRangeDataNtfConfig;
    }

    @Utils.SlotDuration
    public int getSlotDuration() {
        return mSlotDuration;
    }

    public boolean isAoaDisabled() {
        return mIsAoaDisabled;
    }
}
