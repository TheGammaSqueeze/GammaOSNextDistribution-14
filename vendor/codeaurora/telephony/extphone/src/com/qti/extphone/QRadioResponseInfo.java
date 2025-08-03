/*
 * Copyright (c) 2020-2021, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   * Neither the name of The Linux Foundation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.qti.extphone;

import android.os.Parcel;
import android.os.Parcelable;

public class QRadioResponseInfo implements Parcelable {

    private static final String TAG = "QRadioResponseInfo";

    // RadioResponseType
    public static final int SOLICITED = 0;
    public static final int SOLICITED_ACK = 1;
    public static final int SOLICITED_ACK_EXP = 2;

    // RadioError
    public static final int NONE = 0;
    public static final int RADIO_NOT_AVAILABLE = 1;
    public static final int GENERIC_FAILURE = 2;

    private int mResponseType;
    private int mSerial;
    private int mError;

    public QRadioResponseInfo(int responseType, int serial, int error) {
        mResponseType = responseType;
        mSerial = serial;
        mError = error;
    }

    public QRadioResponseInfo(Parcel in) {
        mResponseType = in.readInt();
        mSerial = in.readInt();
        mError = in.readInt();
    }

    public int getResponseType() {
        return mResponseType;
    }

    public int getSerial() {
        return mSerial;
    }

    public int getError() {
        return mError;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mResponseType);
        out.writeInt(mSerial);
        out.writeInt(mError);
    }

    public static final Parcelable.Creator<QRadioResponseInfo> CREATOR = new Parcelable.Creator() {
        public QRadioResponseInfo createFromParcel(Parcel in) {
            return new QRadioResponseInfo(in);
        }

        public QRadioResponseInfo[] newArray(int size) {
            return new QRadioResponseInfo[size];
        }
    };

    public void readFromParcel(Parcel in) {
        mResponseType = in.readInt();
        mSerial = in.readInt();
        mError = in.readInt();
    }

    @Override
    public String toString() {
        return TAG + ": ResponseType: " + getResponseType() + " Serial: " + getSerial() +
                " Error: " + getError();
    }

}

