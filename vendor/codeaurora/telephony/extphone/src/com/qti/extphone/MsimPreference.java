/*
 * Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.qti.extphone;

import android.os.Parcel;
import android.os.Parcelable;

public class MsimPreference implements Parcelable {

    private static final String TAG = "MsimPreference";

    public static final int DSDA = 0;
    public static final int DSDS = 1;

    private int mMsimPreference;

    public MsimPreference(int mode) {
        mMsimPreference = mode;
    }

    public MsimPreference(Parcel in) {
        mMsimPreference = in.readInt();
    }

    public int get() {
        return mMsimPreference;
    }

    public static boolean isValid(int mode) {
        switch (mode) {
            case DSDA:
            case DSDS:
                return true;
            default:
                return false;
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mMsimPreference);
    }

    public static final Parcelable.Creator<MsimPreference> CREATOR = new Parcelable.Creator() {
        public MsimPreference createFromParcel(Parcel in) {
            return new MsimPreference(in);
        }

        public MsimPreference[] newArray(int size) {
            return new MsimPreference[size];
        }
    };

    @Override
    public String toString() {
        return TAG + ": " + get();
    }
}
