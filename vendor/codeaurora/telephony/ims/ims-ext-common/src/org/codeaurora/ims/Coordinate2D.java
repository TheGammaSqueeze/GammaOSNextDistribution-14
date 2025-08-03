/*
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear.
 */

package org.codeaurora.ims;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Parcelable object to handle Coordinate2D info
 * @hide
 */

public class Coordinate2D implements Parcelable {

    private int mX;
    private int mY;

    public Coordinate2D(int x, int y) {
        mX = x;
        mY = y;
    }

    public Coordinate2D(Parcel in) {
        readFromParcel(in);
    }

    public int getX() {
        return mX;
    }

    public int getY() {
        return mY;
    }

    @Override
    public void writeToParcel(Parcel dest, int flag) {
        dest.writeInt(mX);
        dest.writeInt(mY);
    }

    public void readFromParcel(Parcel in) {
        mX = in.readInt();
        mY = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }


    public static final Creator<Coordinate2D> CREATOR =
            new Creator<Coordinate2D>() {
        @Override
        public Coordinate2D createFromParcel(Parcel in) {
            return new Coordinate2D(in);
        }

        @Override
        public Coordinate2D[] newArray(int size) {
            return new Coordinate2D[size];
        }
    };

    public String toString() {
        return ("{Coordinate2D: " + "x = " + mX + " , y = " + mY + "}");
    }
}
