/*
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.qti.extphone;

import android.os.Parcel;
import android.os.Parcelable;

public class CiwlanConfig implements Parcelable {

    private static final String TAG = "CiwlanConfig";

    // On targets that support C_IWLAN modes, when there is a C_IWLAN status change from UI or UE
    // moves between home and roaming, there will be a brief moment when the mode will be INVALID.
    public static final int INVALID = -1;
    public static final int ONLY = 0;
    public static final int PREFERRED = 1;
    // On targets that do not support C_IWLAN modes, UNSUPPORTED will be returned.
    public static final int UNSUPPORTED = 2;

    private int mHomeMode = INVALID;
    private int mRoamMode = INVALID;

    public CiwlanConfig(int homeMode, int roamMode) {
        mHomeMode = homeMode;
        mRoamMode = roamMode;
    }

    public CiwlanConfig(Parcel in) {
        mHomeMode = in.readInt();
        mRoamMode = in.readInt();
    }

    public String getHomeCiwlanMode() {
        switch (mHomeMode) {
            case ONLY:
                return "ONLY";
            case PREFERRED:
                return "PREFERRED";
            case UNSUPPORTED:
                return "UNSUPPORTED";
            default:
                return "INVALID";
        }
    }

    public String getRoamCiwlanMode() {
        switch (mRoamMode) {
            case ONLY:
                return "ONLY";
            case PREFERRED:
                return "PREFERRED";
            case UNSUPPORTED:
                return "UNSUPPORTED";
            default:
                return "INVALID";
        }
    }

    public boolean isCiwlanOnlyInHome() {
        return mHomeMode == ONLY;
    }

    public boolean isCiwlanOnlyInRoam() {
        return mRoamMode == ONLY;
    }

    public boolean isCiwlanModeSupported() {
        return (mHomeMode != UNSUPPORTED && mRoamMode != UNSUPPORTED);
    }

    public boolean isValid() {
        return (mHomeMode != INVALID && mRoamMode != INVALID);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mHomeMode);
        out.writeInt(mRoamMode);
    }

    public static final Parcelable.Creator<CiwlanConfig> CREATOR = new Parcelable.Creator() {
        @Override
        public CiwlanConfig createFromParcel(Parcel in) {
            return new CiwlanConfig(in);
        }

        @Override
        public CiwlanConfig[] newArray(int size) {
            return new CiwlanConfig[size];
        }
    };

    @Override
    public String toString() {
        return TAG + " homeMode = " + getHomeCiwlanMode() + ", roamMode = " + getRoamCiwlanMode();
    }
}
