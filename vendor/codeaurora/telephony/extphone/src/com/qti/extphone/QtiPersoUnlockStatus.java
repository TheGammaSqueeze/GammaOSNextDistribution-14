/*
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.qti.extphone;

import android.os.Parcel;
import android.os.Parcelable;

public class QtiPersoUnlockStatus implements Parcelable {
    private static final String TAG = "QtiPersoUnlockStatus";

    public static final int UNKNOWN = 0;
    public static final int TEMPORARY_UNLOCKED  = 1;
    public static final int PERMANENT_UNLOCKED  = 2;

    private int mPersoUnlockStatus;

    public QtiPersoUnlockStatus(int status) {
        mPersoUnlockStatus = status;
    }

    public QtiPersoUnlockStatus(Parcel in) {
        mPersoUnlockStatus = in.readInt();
    }

    public int get() {
        return mPersoUnlockStatus;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mPersoUnlockStatus);
    }

    public static final Parcelable.Creator<QtiPersoUnlockStatus> CREATOR =
            new Parcelable.Creator() {
        public QtiPersoUnlockStatus createFromParcel(Parcel in) {
            return new QtiPersoUnlockStatus(in);
        }

        public QtiPersoUnlockStatus[] newArray(int size) {
            return new QtiPersoUnlockStatus[size];
        }
    };

    @Override
    public String toString() {
        return TAG + ": " + get();
    }
}
