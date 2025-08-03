/*
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear.
 */

package org.codeaurora.ims;

import android.os.Parcel;
import android.os.Parcelable;

import org.codeaurora.ims.Coordinate2D;

/**
 * Parcelable object to handle VosMoveInfo info
 * @hide
 */

public class VosMoveInfo implements Parcelable {

    private Coordinate2D mStart;
    private Coordinate2D mEnd;

    public VosMoveInfo(Coordinate2D start, Coordinate2D end) {
        mStart = start;
        mEnd = end;
    }

    public VosMoveInfo(Parcel in) {
        readFromParcel(in);
    }

    public Coordinate2D getStart() {
        return mStart;
    }

    public Coordinate2D getEnd() {
        return mEnd;
    }

    @Override
    public void writeToParcel(Parcel dest, int flag) {
        dest.writeParcelable(mStart, flag);
        dest.writeParcelable(mEnd, flag);
    }

    public void readFromParcel(Parcel in) {
        mStart = in.readParcelable(Coordinate2D.class.getClassLoader());
        mEnd = in.readParcelable(Coordinate2D.class.getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }


    public static final Creator<VosMoveInfo> CREATOR =
            new Creator<VosMoveInfo>() {
        @Override
        public VosMoveInfo createFromParcel(Parcel in) {
            return new VosMoveInfo(in);
        }

        @Override
        public VosMoveInfo[] newArray(int size) {
            return new VosMoveInfo[size];
        }
    };

    public String toString() {
        return ("{VosMoveInfo: " + "start = " + mStart + " , end = " + mEnd + "}");
    }
}
