/*
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear.
 */

package org.codeaurora.ims;

import android.os.Parcel;
import android.os.Parcelable;

import org.codeaurora.ims.VosMoveInfo;
import org.codeaurora.ims.VosTouchInfo;

/**
 * Parcelable object to handle VosActionInfo info
 * @hide
 */

public class VosActionInfo implements Parcelable {
    public static final VosMoveInfo INVALID_MOVEINFO = null;
    public static final VosTouchInfo INVALID_TOUCHINFO = null;

    private VosMoveInfo mVosMoveInfo;
    private VosTouchInfo mVosTouchInfo;

    public VosActionInfo(VosMoveInfo vosMoveInfo, VosTouchInfo vosTouchInfo) {
        mVosMoveInfo = vosMoveInfo;
        mVosTouchInfo = vosTouchInfo;
    }

    public VosActionInfo(VosTouchInfo vosTouchInfo) {
        this(INVALID_MOVEINFO, vosTouchInfo);
    }

    public VosActionInfo(VosMoveInfo vosMoveInfo) {
        this(vosMoveInfo, INVALID_TOUCHINFO);
    }

    public VosActionInfo(Parcel in) {
        readFromParcel(in);
    }

    public VosMoveInfo getVosMoveInfo() {
        return mVosMoveInfo;
    }

    public VosTouchInfo getVosTouchInfo() {
        return mVosTouchInfo;
    }

    @Override
    public void writeToParcel(Parcel dest, int flag) {
        dest.writeParcelable(mVosMoveInfo, flag);
        dest.writeParcelable(mVosTouchInfo, flag);
    }

    public void readFromParcel(Parcel in) {
        mVosMoveInfo = in.readParcelable(VosMoveInfo.class.getClassLoader());
        mVosTouchInfo = in.readParcelable(VosTouchInfo.class.getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }


    public static final Creator<VosActionInfo> CREATOR =
            new Creator<VosActionInfo>() {
        @Override
        public VosActionInfo createFromParcel(Parcel in) {
            return new VosActionInfo(in);
        }

        @Override
        public VosActionInfo[] newArray(int size) {
            return new VosActionInfo[size];
        }
    };

    public String toString() {
        return ("{VosActionInfo: " + "vosMoveInfo = " +
                mVosMoveInfo + " , vosTouchInfo = " + mVosTouchInfo + "}");
    }
}
