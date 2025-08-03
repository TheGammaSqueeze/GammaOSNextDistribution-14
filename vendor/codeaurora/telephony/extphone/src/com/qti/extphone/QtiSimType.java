/*
 * Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.qti.extphone;

import android.os.Parcel;
import android.os.Parcelable;

public class QtiSimType implements Parcelable {
    private static final String TAG = "QtiSimType";

    /**
     * For completeness added eSIM related constants below but, currently they are not used.
     * At present, PHYSICAL type is used for both Physical and eSIM Type cards.
     */
    public static final int SIM_TYPE_INVALID = -1;
    public static final int SIM_TYPE_PHYSICAL = 0;
    public static final int SIM_TYPE_ESIM = 1;
    public static final int SIM_TYPE_IUICC = 2;
    public static final int SIM_TYPE_PHYSICAL_ESIM = 3;
    public static final int SIM_TYPE_PHYSICAL_IUICC = 4;
    public static final int SIM_TYPE_ESIM_IUICC = 5;
    public static final int SIM_TYPE_PHYSICAL_ESIM_IUICC = 6;

    private int mType;

    public QtiSimType(int val) {
        mType = val;
    }

    public QtiSimType(Parcel in) {
        mType = in.readInt();
    }

    public int get() {
        return mType;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mType);
    }

    public static final Parcelable.Creator<QtiSimType> CREATOR = new Parcelable.Creator() {
        public QtiSimType createFromParcel(Parcel in) {
            return new QtiSimType(in);
        }

        public QtiSimType[] newArray(int size) {
            return new QtiSimType[size];
        }
    };

    @Override
    public String toString() {
        return TAG + ": " + get();
    }
}
