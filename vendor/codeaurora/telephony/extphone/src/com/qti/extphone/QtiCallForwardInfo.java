/*
 * Copyright (c) 2021, The Linux Foundation. All rights reserved.
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

public class QtiCallForwardInfo implements Parcelable {
    private static final String TAG = "QtiCallForwardInfo";

    public int             status;      /*1 = active, 0 = not active */
    public int             reason;      /* from TS 27.007 7.11 "reason" */
    public int             serviceClass; /* Saum of CommandsInterface.SERVICE_CLASS */
    public int             toa;         /* "type" from TS 27.007 7.11 */
    public String          number;      /* "number" from TS 27.007 7.11 */
    public int             timeSeconds; /* for CF no reply only */

    public QtiCallForwardInfo() {
    }

    public QtiCallForwardInfo(Parcel in) {
        status = in.readInt();
        reason = in.readInt();
        serviceClass = in.readInt();
        toa = in.readInt();
        number = in.readString();
        timeSeconds = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(status);
        out.writeInt(reason);
        out.writeInt(serviceClass);
        out.writeInt(toa);
        out.writeString(number);
        out.writeInt(timeSeconds);
    }

    public static final Parcelable.Creator<QtiCallForwardInfo> CREATOR = new Parcelable.Creator() {
        public QtiCallForwardInfo createFromParcel(Parcel in) {
            return new QtiCallForwardInfo(in);
        }

        public QtiCallForwardInfo[] newArray(int size) {
            return new QtiCallForwardInfo[size];
        }
    };

    public void readFromParcel(Parcel in) {
        status = in.readInt();
        reason = in.readInt();
        serviceClass = in.readInt();
        toa = in.readInt();
        number = in.readString();
        timeSeconds = in.readInt();
    }

    @Override
    public String toString() {
        return "[QtiCallForwardInfo: status=" + (status == 0 ? " not active " : " active ")
                + ", reason= " + reason + ", serviceClass= " + serviceClass
                + ", timeSec= " + timeSeconds + " seconds" + "]";
    }
}