/*
 * Copyright (c) 2018, The Linux Foundation. All rights reserved.
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

package org.codeaurora.ims;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Parcelable object to handle Video Call DataUsage information
 * @hide
 */

public class MultiIdentityLineInfo implements Parcelable {

    public static final int LINE_TYPE_PRIMARY = 1;
    public static final int LINE_TYPE_SECONDARY = 2;

    public static final int LINE_STATUS_UNKNOWN = 0;
    public static final int LINE_STATUS_INACTIVE = 1;
    public static final int LINE_STATUS_ACTIVE = 2;

    public static final String LINE_TYPE = "lineType";
    public static final String ORIGINATING_NUMBER = "originatingNumber";
    public static final String TERMINATING_NUMBER = "terminatingNumber";
    public static final MultiIdentityLineInfo defaultLine  =
        new MultiIdentityLineInfo("", LINE_TYPE_PRIMARY);

    private String mMsisdn;
    private int mLineType;
    private int mLineStatus;

    public MultiIdentityLineInfo(String msisdn, int type) {
        this(msisdn, type, LINE_STATUS_UNKNOWN);
    }

    public MultiIdentityLineInfo(String msisdn, int type, int status) {
        mMsisdn = msisdn;
        mLineType = type;
        mLineStatus = status;
    }

    public MultiIdentityLineInfo(MultiIdentityLineInfo info) {
        mMsisdn = info.getMsisdn();
        mLineType = info.getLineType();
        mLineStatus = info.getLineStatus();
    }

    public MultiIdentityLineInfo(Parcel in) {
        readFromParcel(in);
    }


   /*
    * This method returns the default line
    */
    public static MultiIdentityLineInfo getDefaultLine() {
        return defaultLine;
    }

   /*
    * This method returns the msisdn of line
    */
    public String getMsisdn() {
        return mMsisdn;
    }

   /*
    * This method returns the type of line
    */
    public int getLineType() {
        return mLineType;
    }

   /*
    * This method returns the status of line
    */
    public int getLineStatus() {
        return mLineStatus;
    }

   /*
    * This method sets the msisdn of line
    */
    public void setMsisdn(String msisdn) {
        mMsisdn = msisdn;
    }

   /*
    * This method sets the type of line
    */
    public void setLineType(int type) {
        mLineType = type;
    }

   /*
    * This method sets the status of line
    */
    public void setLineStatus(int status) {
        mLineStatus = status;
    }

   /*
    * This method checks whether the line type
    * is secondary or not
    */
    public boolean isLineSecondary() {
        return mLineType == LINE_TYPE_SECONDARY;
    }

    @Override
    public void writeToParcel(Parcel dest, int flag) {
        dest.writeString(mMsisdn);
        dest.writeInt(mLineType);
        dest.writeInt(mLineStatus);
    }

    public void readFromParcel(Parcel in) {
        mMsisdn = in.readString();
        mLineType = in.readInt();
        mLineStatus = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<MultiIdentityLineInfo> CREATOR =
            new Creator<MultiIdentityLineInfo>() {
        @Override
        public  MultiIdentityLineInfo createFromParcel(Parcel in) {
            return new MultiIdentityLineInfo(in);
        }

        @Override
        public  MultiIdentityLineInfo[] newArray(int size) {
            return new MultiIdentityLineInfo[size];
        }
    };

    public String toString() {
        return ("{MultiIdentity Line Info : " + "msisdn = " +
                mMsisdn + " , line type = " + mLineType +
                " , line status = " + mLineStatus + "}");
    }
}
