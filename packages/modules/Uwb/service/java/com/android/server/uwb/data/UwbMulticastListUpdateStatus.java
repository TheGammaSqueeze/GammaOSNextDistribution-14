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
package com.android.server.uwb.data;

import android.util.Log;
import android.uwb.UwbAddress;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.uwb.params.TlvUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class UwbMulticastListUpdateStatus {
    private static final String TAG = "UwbM*ListUpdateStatus";
    private long mSessionId;
    private int mRemainingSize;
    private int mNumOfControlees;
    private byte[] mControleeMacAddresses;
    private long[] mSubSessionId;
    private int[] mStatus;
    private UwbAddress[] mControleeUwbAddresses;

    public UwbMulticastListUpdateStatus(long sessionID, int remainingSize, int numOfControlees,
            byte[] controleeMacAddresses, long[] subSessionId, int[] status) {
        this.mSessionId = sessionID;
        this.mRemainingSize = remainingSize;
        this.mNumOfControlees = numOfControlees;
        this.mControleeMacAddresses = controleeMacAddresses;
        this.mSubSessionId = subSessionId;
        this.mStatus = status;

        Log.d(TAG, "Controlee count: " + numOfControlees + " mac addresses: "
                + Arrays.toString(controleeMacAddresses));
        if (controleeMacAddresses != null) {
            // Precache mac addresses in a more usable and universal form.
            mControleeUwbAddresses = getUwbAddresses(mControleeMacAddresses, mNumOfControlees,
                    mControleeMacAddresses.length / mNumOfControlees);
        }
    }

    // Convert controlee addresses in byte array to array of UwbAddress.
    public UwbAddress[] getUwbAddresses(byte[] macAddresses, int numOfAddresses,
            int addressLength) {
        UwbAddress[] uwbAddresses = new UwbAddress[numOfAddresses];
        ByteBuffer buffer = ByteBuffer.allocate(macAddresses.length);
        buffer.put(macAddresses);
        buffer.flip();
        for (int i = 0; i < numOfAddresses; i++) {
            if (buffer.remaining() >= addressLength) {
                byte[] macAddress = new byte[addressLength];
                buffer.get(macAddress, 0, macAddress.length);
                uwbAddresses[i] = getComputedMacAddress(macAddress);
            }
        }
        return uwbAddresses;
    }

    public long getSessionId() {
        return mSessionId;
    }

    public int getRemainingSize() {
        return mRemainingSize;
    }

    public int getNumOfControlee() {
        return mNumOfControlees;
    }

    // This should go obsolete as we shift to UwbAddresses.
    public byte[] getControleeMacAddresses() {
        return mControleeMacAddresses;
    }

    public long[] getSubSessionId() {
        return mSubSessionId;
    }

    public int[] getStatus() {
        return mStatus;
    }

    public UwbAddress[] getControleeUwbAddresses() {
        return mControleeUwbAddresses;
    }

    @Override
    public String toString() {
        return "UwbMulticastListUpdateEvent { "
                + " SessionID =" + mSessionId
                + ", RemainingSize =" + mRemainingSize
                + ", NumOfControlee =" + mNumOfControlees
                + ", MacAddress =" + Arrays.toString(mControleeMacAddresses)
                + ", SubSessionId =" + Arrays.toString(mSubSessionId)
                + ", Status =" + Arrays.toString(mStatus)
                + '}';
    }

    private static UwbAddress getComputedMacAddress(byte[] address) {
        if (!SdkLevel.isAtLeastU()) {
            return UwbAddress.fromBytes(TlvUtil.getReverseBytes(address));
        }
        return UwbAddress.fromBytes(address);
    }
}
