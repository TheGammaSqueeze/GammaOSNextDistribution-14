/*
 * Copyright (c) 2020, The Linux Foundation. All rights reserved.
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
 *
 * Changes from Qualcomm Innovation Center are provided under the following license:
 *
 * Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted (subject to the limitations in the
 * disclaimer below) provided that the following conditions are met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *
 *   * Neither the name of Qualcomm Innovation Center, Inc. nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE
 * GRANTED BY THIS LICENSE. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT
 * HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.codeaurora.ims;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Parcelable object to handle CallComposer info
 * @hide
 */

public class CallComposerInfo implements Parcelable {

    public static final class Location implements Parcelable {
        public static final float LOCATION_NOT_SET = -1;
        public static final float DEFAULT_RADIUS = 0;
        public static final Location UNKNOWN = new Location(LOCATION_NOT_SET, 0, 0);

        private float mRadius;
        private double mLatitude;
        private double mLongitude;

        public Location(double latitude, double longitude) {
            this(DEFAULT_RADIUS, latitude, longitude);
        }

        public Location(float radius, double latitude, double longitude) {
            mRadius = radius;
            mLatitude = latitude;
            mLongitude = longitude;
        }

        public Location(Location in) {
            mRadius = in.getRadius();
            mLatitude = in.getLatitude();
            mLongitude = in.getLongitude();
        }

        public Location(Parcel in) {
            readFromParcel(in);
        }

        public float getRadius() {
            return mRadius;
        }

        public double getLatitude() {
            return mLatitude;
        }

        public double getLongitude() {
            return mLongitude;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeFloat(mRadius);
            dest.writeDouble(mLatitude);
            dest.writeDouble(mLongitude);
        }

        public void readFromParcel(Parcel in) {
            mRadius = in.readFloat();
            mLatitude = in.readDouble();
            mLongitude = in.readDouble();
        }

        public static final Creator<Location> CREATOR = new Creator<Location>() {
            public Location createFromParcel(Parcel in) {
                return new Location(in);
            }

            public Location[] newArray(int size) {
                return new Location[size];
            }
        };

        public String toString() {
            return ("{radius = " +
                    mRadius + " , latitude = " + mLatitude +
                    " , longitude = " + mLongitude + "}");
        }
    }

    public static final int PRIORITY_URGENT = 0;
    public static final int PRIORITY_NORMAL = 1;
    public static final int INVALID_CALLID = -1;
    public static final int INVALID_TOKEN = -1;
    public static final String INVALID_ORGANIZATION = "";

    private int mPriority;
    private String mSubject;
    private Uri mImageUrl;
    private Location mLocation;
    private String mOrganization;

    public CallComposerInfo(int priority, String subject, Uri imageUrl,
            Location location, String organization) {
        mPriority = priority;
        mSubject = subject;
        mImageUrl = imageUrl;
        mLocation = location;
        mOrganization = organization;
    }

    public CallComposerInfo(int priority, String subject, Uri imageUrl,
            Location location) {
        this(priority, subject, imageUrl, location, INVALID_ORGANIZATION);
    }

    public CallComposerInfo(int priority, String subject, Uri imageUrl) {
        this(priority, subject, imageUrl, Location.UNKNOWN);
    }

    public CallComposerInfo(CallComposerInfo info) {
        mPriority = info.getPriority();
        mSubject = info.getSubject();
        mImageUrl = info.getImageUrl();
        mLocation = info.getLocation();
        mOrganization = info.getOrganization();
    }

    public CallComposerInfo(Parcel in) {
        readFromParcel(in);
    }

   /*
    * This method returns the priority of the call
    * Int value: NORMAL, URGENT
    */
    public int getPriority() {
        return mPriority;
    }

   /*
    * This method returns the subject of the call
    */
    public String getSubject() {
        return mSubject;
    }

   /*
    * This method returns the Url of the image
    */
    public Uri getImageUrl() {
        return mImageUrl;
    }

   /*
    * This method returns the location of the call
    */
    public Location getLocation() {
        return mLocation;
    }

    /*
    * This method returns the organization of the call
    */
    public String getOrganization() {
        return mOrganization;
    }

    @Override
    public void writeToParcel(Parcel dest, int flag) {
        dest.writeInt(mPriority);
        dest.writeString(mSubject);
        dest.writeParcelable(mImageUrl, flag);
        dest.writeParcelable(mLocation, flag);
        dest.writeString(mOrganization);
    }

    public void readFromParcel(Parcel in) {
        mPriority = in.readInt();
        mSubject = in.readString();
        mImageUrl = in.readParcelable(Uri.class.getClassLoader());
        mLocation = in.readParcelable(Location.class.getClassLoader());
        mOrganization = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<CallComposerInfo> CREATOR =
            new Creator<CallComposerInfo>() {
        @Override
        public CallComposerInfo createFromParcel(Parcel in) {
            return new CallComposerInfo(in);
        }

        @Override
        public CallComposerInfo[] newArray(int size) {
            return new CallComposerInfo[size];
        }
    };

    public String toString() {
        return ("{CallComposerInfo: " + "priority = " +
                mPriority + " , subject = " + mSubject +
                " , image url = " + mImageUrl + ", location = " + mLocation +
                " , organization = " + mOrganization + "}");
    }
}
