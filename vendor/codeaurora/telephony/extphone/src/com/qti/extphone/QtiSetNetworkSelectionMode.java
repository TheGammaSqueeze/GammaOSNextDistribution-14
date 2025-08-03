/*
 * Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.qti.extphone;

import android.os.Parcel;
import android.os.Parcelable;

public class QtiSetNetworkSelectionMode implements Parcelable{
    private static final String TAG = "QtiSetNetworkSelectionMode";

    /** Invalid access mode */
    public static final int ACCESS_MODE_INVALID = 0;

    /** Invalid CAG Id */
    public static final long CAG_ID_INVALID = -1;

    /** Access network unknown */
    public static final int ACCESS_NETWORK_UNKNOWN = 0;

    private String mOperatorNumeric;

    private int mRan = ACCESS_NETWORK_UNKNOWN;

    /**
     * Describes the access mode
     */
    private int mAccessMode = ACCESS_MODE_INVALID;

    /** Defines the CAG ID of CAG Cell. */
    private long mCagId = CAG_ID_INVALID;

    /** Defines the SNPN network ID.
     * This field has six bytes:
     * mNid[0] is the most significant byte
     * mNid[5] is the least significant byte
     */
    private byte[] mNid;

    public QtiSetNetworkSelectionMode(String operatorNumeric, int ran, int accessMode,
            long cagId, byte[] nid) {
        mOperatorNumeric = operatorNumeric;
        mRan = ran;
        mAccessMode = accessMode;
        mCagId = cagId;
        mNid = nid;
    }

    public QtiSetNetworkSelectionMode(Parcel in) {
        mOperatorNumeric = in.readString();
        mRan = in.readInt();
        mAccessMode = in.readInt();
        mCagId = in.readLong();
        int arrayLength = in.readInt();
        if (arrayLength > 0) {
            mNid = new byte[arrayLength];
            in.readByteArray(mNid);
        } else {
            mNid = null;
        }
    }

    public String getOperatorNumeric() {
        return mOperatorNumeric;
    }

    public int getRan() {
        return mRan;
    }

    public int getAccessMode() {
        return mAccessMode;
    }

    public long getCagId() {
        return mCagId;
    }

    public byte[] getNid() {
        return mNid;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mOperatorNumeric);
        dest.writeInt(mRan);
        dest.writeInt(mAccessMode);
        dest.writeLong(mCagId);
        if (mNid != null && mNid.length > 0) {
             dest.writeInt(mNid.length);
             dest.writeByteArray(mNid);
        } else {
             dest.writeInt(0);
        }
    }

    public static final Parcelable.Creator<QtiSetNetworkSelectionMode> CREATOR =
            new Parcelable.Creator() {
        public QtiSetNetworkSelectionMode createFromParcel(Parcel in) {
            return new QtiSetNetworkSelectionMode(in);
        }

        public QtiSetNetworkSelectionMode[] newArray(int size) {
            return new QtiSetNetworkSelectionMode[size];
        }
    };

    public void readFromParcel(Parcel in) {
        mOperatorNumeric = in.readString();
        mRan = in.readInt();
        mAccessMode = in.readInt();
        mCagId = in.readLong();
        int arrayLength = in.readInt();
        if (arrayLength > 0) {
            mNid = new byte[arrayLength];
            in.readByteArray(mNid);
        } else {
            mNid = null;
        }
    }

    @Override
    public String toString() {
        return TAG + mOperatorNumeric
        + "/" + mRan
        + "/" + mAccessMode
        + "/" + mCagId
        + "/" + mNid;
    }
}
