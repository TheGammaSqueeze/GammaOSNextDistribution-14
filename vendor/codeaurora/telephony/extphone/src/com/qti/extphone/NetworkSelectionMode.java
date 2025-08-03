/*
 * Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.qti.extphone;

import android.os.Parcel;
import android.os.Parcelable;

public class NetworkSelectionMode implements Parcelable{
    private static final String TAG = "NetworkSelectionMode";

    /** Invalid access mode */
    public static final int ACCESS_MODE_INVALID = 0;

    /**
     * Describes the access mode
     */
    private int mAccessMode = ACCESS_MODE_INVALID;

    /** Defines the CAG ID of CAG Cell. */
    private boolean mIsManual = false;

    public NetworkSelectionMode(int accessMode, boolean isManual) {
        mAccessMode = accessMode;
        mIsManual = isManual;
    }

    public NetworkSelectionMode(Parcel in) {
        mAccessMode = in.readInt();
        mIsManual = in.readBoolean();
    }

    public int getAccessMode() {
        return mAccessMode;
    }

    public boolean getIsManual() {
        return mIsManual;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mAccessMode);
        dest.writeBoolean(mIsManual);
    }

    public static final Parcelable.Creator<NetworkSelectionMode> CREATOR =
            new Parcelable.Creator() {
        public NetworkSelectionMode createFromParcel(Parcel in) {
            return new NetworkSelectionMode(in);
        }

        public NetworkSelectionMode[] newArray(int size) {
            return new NetworkSelectionMode[size];
        }
    };

    public void readFromParcel(Parcel in) {
        mAccessMode = in.readInt();
        mIsManual = in.readBoolean();
    }

    @Override
    public String toString() {
        return TAG + mAccessMode
        + "/" + mIsManual;
    }
}
