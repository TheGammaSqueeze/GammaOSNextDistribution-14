/*
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.qti.extphone;

import android.os.Parcel;
import android.os.Parcelable;

public class DualDataRecommendation implements Parcelable {

    private static final String TAG = "DualDataRecommendation";

    // Recommended SUB
    public static final int DDS = 1;
    public static final int NON_DDS = 2;

    // Action
    public static final int ACTION_DATA_NOT_ALLOW = 0;
    public static final int ACTION_DATA_ALLOW = 1;

    private int mSub;
    private int mAction;

    public DualDataRecommendation(int sub, int action) {
        mSub = sub;
        mAction = action;
    }

    public DualDataRecommendation(Parcel in) {
        mSub = in.readInt();
        mAction = in.readInt();
    }

    public int getRecommendedSub() {
        return mSub;
    }

    public int getAction() {
        return mAction;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mSub);
        out.writeInt(mAction);
    }

    public static final Parcelable.Creator<DualDataRecommendation> CREATOR
            = new Parcelable.Creator() {
        public DualDataRecommendation createFromParcel(Parcel in) {
            return new DualDataRecommendation(in);
        }

        public DualDataRecommendation[] newArray(int size) {
            return new DualDataRecommendation[size];
        }
    };

    @Override
    public String toString() {
        return TAG + ": getRecommendedSub(): " + getRecommendedSub()
                + " getAction: " + getAction();
    }
}
