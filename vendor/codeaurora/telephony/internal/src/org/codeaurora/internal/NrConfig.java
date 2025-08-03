/*
 * Copyright (c) 2020, The Linux Foundation. All rights reserved.
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

import android.os.Parcel;
import android.os.Parcelable;

public class NrConfig implements Parcelable{
    private static final String TAG = "NrConfig";

    public static final int NR_CONFIG_INVALID = -1;
    public static final int NR_CONFIG_COMBINED_SA_NSA = 0;
    public static final int NR_CONFIG_NSA =  1;
    public static final int NR_CONFIG_SA =  2;

    private int mValue;

    public NrConfig(int val) {
        mValue = val;
    }

    public NrConfig(Parcel in) {
        mValue = in.readInt();
    }

    public int get() {
        return mValue;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mValue);
    }

    public static final Parcelable.Creator<NrConfig> CREATOR = new Parcelable.Creator() {
        public NrConfig createFromParcel(Parcel in) {
            return new NrConfig(in);
        }

        public NrConfig[] newArray(int size) {
            return new NrConfig[size];
        }
    };

    public void readFromParcel(Parcel in) {
        mValue = in.readInt();
    }

    @Override
    public String toString() {
        return TAG + ": " + get();
    }
}
