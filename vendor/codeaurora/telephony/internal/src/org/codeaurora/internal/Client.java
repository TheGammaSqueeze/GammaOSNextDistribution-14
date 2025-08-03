/*
 * Copyright (c) 2018, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
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
 *
 */

package org.codeaurora.internal;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class Client implements Parcelable {

    private static final String TAG = "Client";

    private int mId;
    private int mUid;
    private String mPackageName;
    private INetworkCallback mCallback;

    public Client(int id, int uid, String packageName, INetworkCallback callback) {
        mId = id;
        mUid = uid;
        mPackageName = packageName;
        mCallback = callback;
    }

    public Client(Parcel in) {
        mId = in.readInt();
        mUid = in.readInt();
        mPackageName = in.readString();
        mCallback = INetworkCallback.Stub.asInterface(in.readStrongBinder());
    }

    public int getId() {
        return mId;
    }

    public int getUid() {
        return mUid;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public INetworkCallback getCallback() {
        return mCallback;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mId);
        out.writeInt(mUid);
        out.writeString(mPackageName);
        out.writeStrongInterface(mCallback);
    }

    public static final Parcelable.Creator<Client> CREATOR = new Parcelable.Creator() {
        public Client createFromParcel(Parcel in) {
            return new Client(in);
        }

        public Client[] newArray(int size) {
            return new Client[size];
        }
    };

    public void readFromParcel(Parcel in) {
        mId = in.readInt();
        mUid = in.readInt();
        mPackageName = in.readString();
        mCallback = INetworkCallback.Stub.asInterface(in.readStrongBinder());
    }

    @Override
    public String toString() {
        return "Client{" + "mId=" + getId() + ", mUid=" + getUid() + ", mPackageName='" +
                getPackageName() + '\'' + ", mCallback=" + getCallback() + '}';
    }
}
