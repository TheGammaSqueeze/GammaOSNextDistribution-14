/*
 * Copyright (c) 2021, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   * Neither the name of The Linux Foundation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
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
 */

package com.qti.extphone;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

public class QtiImeiInfo implements Parcelable {
    private static final String TAG = "QtiImeiInfo";

    public static final int IMEI_TYPE_INVALID = 0;
    public static final int IMEI_TYPE_PRIMARY = 1;
    public static final int IMEI_TYPE_SECONDARY = 2;

    private int mSlotId;
    private String mImei;
    private int mImeiType;

    public QtiImeiInfo(int slotId, String imei, int imeiType) {
        mSlotId = slotId;
        mImei = imei;
        mImeiType = imeiType;
    }

    public QtiImeiInfo(Parcel in) {
        mSlotId = in.readInt();
        mImei = in.readString();
        mImeiType = in.readInt();
    }

    public int getSlotId() {
        return mSlotId;
    }

    public String getImei() {
        return mImei;
    }

    public int getImeiType() {
        return mImeiType;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mSlotId);
        out.writeString(mImei);
        out.writeInt(mImeiType);
    }

    public static final Parcelable.Creator<QtiImeiInfo> CREATOR = new Parcelable.Creator() {
        public QtiImeiInfo createFromParcel(Parcel in) {
            return new QtiImeiInfo(in);
        }

        public QtiImeiInfo[] newArray(int size) {
            return new QtiImeiInfo[size];
        }
    };

    public void readFromParcel(Parcel in) {
        mSlotId = in.readInt();
        mImei = in.readString();
        mImeiType = in.readInt();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QtiImeiInfo imeiInfo = (QtiImeiInfo) o;

        return this.mSlotId == imeiInfo.mSlotId &&
                this.mImeiType == imeiInfo.mImeiType &&
                Objects.equals(this.mImei, imeiInfo.mImei);
    }

    @Override
    public String toString() {
        return "QtiImeiInfo{" + " slotId=" + mSlotId + " mImeiType=" + getImeiType() +  "}";
    }
}
