/*
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear.nd
 */

package org.codeaurora.ims;

import android.os.Parcel;
import android.os.Parcelable;

import org.codeaurora.ims.Coordinate2D;

/**
 * Parcelable object to handle VosTouchInfo info
 * @hide
 */

public class VosTouchInfo implements Parcelable {

    private Coordinate2D mTouch;
    // Milliseconds
    private int mTouchDuration;

    public VosTouchInfo(Coordinate2D touch, int touchDuration) {
        mTouch = touch;
        mTouchDuration = touchDuration;
    }

    public VosTouchInfo(Parcel in) {
        readFromParcel(in);
    }

    public Coordinate2D getTouch() {
        return mTouch;
    }

    public int getTouchDuration() {
        return mTouchDuration;
    }

    @Override
    public void writeToParcel(Parcel dest, int flag) {
        dest.writeParcelable(mTouch, flag);
        dest.writeInt(mTouchDuration);
    }

    public void readFromParcel(Parcel in) {
        mTouch = in.readParcelable(Coordinate2D.class.getClassLoader());
        mTouchDuration = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }


    public static final Creator<VosTouchInfo> CREATOR =
            new Creator<VosTouchInfo>() {
        @Override
        public VosTouchInfo createFromParcel(Parcel in) {
            return new VosTouchInfo(in);
        }

        @Override
        public VosTouchInfo[] newArray(int size) {
            return new VosTouchInfo[size];
        }
    };

    public String toString() {
        return ("{VosTouchInfo: " + "touch = " +
                mTouch + " , touchDuration = " + mTouchDuration + "}");
    }
}
