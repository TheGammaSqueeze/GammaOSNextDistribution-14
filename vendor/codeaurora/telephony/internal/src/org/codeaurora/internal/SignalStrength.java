/*
 * Copyright (c) 2018, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.codeaurora.internal;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class SignalStrength implements Parcelable {

    private static final String TAG = "SignalStrength";

    public static final int INVALID = -32768;

    private static final int MAX_NR_RSRP = -44;
    private static final int MIN_NR_RSRP = -140;

    private static final int MAX_NR_SNR = -160;
    private static final int MIN_NR_SNR = -240;

    private int mNrRsrp;
    private int mNrSnr;

    public SignalStrength() {
        mNrRsrp = INVALID;
        mNrSnr = INVALID;
    }

    public SignalStrength(int rsrp, int snr) {
        mNrRsrp = rsrp;
        mNrSnr = snr;
    }

    public SignalStrength(Parcel in) {
        mNrRsrp = in.readInt();
        mNrSnr = in.readInt();
    }

    public int getRsrp() {
        return mNrRsrp;
    }

    public int getSnr() {
        return mNrSnr;
    }

    @Override
    public String toString() {
        return (TAG + ": Rsrp: " + getRsrp() + " Snr: " + getSnr());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mNrRsrp);
        out.writeInt(mNrSnr);
    }

    public static final Parcelable.Creator<SignalStrength> CREATOR = new Parcelable.Creator() {
        public SignalStrength createFromParcel(Parcel in) {
            return new SignalStrength(in);
        }

        public SignalStrength[] newArray(int size) {
            return new SignalStrength[size];
        }
    };

    public void readFromParcel(Parcel in) {
        mNrRsrp = in.readInt();
        mNrSnr = in.readInt();
    }
}
