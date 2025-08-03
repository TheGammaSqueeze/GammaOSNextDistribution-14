/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.car.media;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.internal.util.Preconditions;

/**
 * A class to encapsulate the handle for a system level audio patch. This is used
 * to provide a "safe" way for permitted applications to route automotive audio sources
 * outside of android.
 * @hide
 */
@SystemApi
public final class CarAudioPatchHandle implements Parcelable {

    // This is enough information to uniquely identify a patch to the system
    private final int mHandleId;
    private final String mSourceAddress;
    private final String mSinkAddress;

    /**
     * Construct a audio patch handle container given the system level handle
     * NOTE: Assumes (as it true today), that there is exactly one device port in the source
     * and sink arrays.
     *
     * @hide
     */
    public CarAudioPatchHandle(int patchId,
            @NonNull String sourceAddress,
            @NonNull String sinkAddress) {
        mSourceAddress = Preconditions.checkNotNull(sourceAddress,
                "Patch id %d Source's Address device can not be null", patchId);
        mSinkAddress = Preconditions.checkNotNull(sinkAddress,
                "Patch id %d Sink's Address device can not be null", patchId);
        mHandleId = patchId;
    }

    @Override
    public String toString() {
        return "Patch (mHandleId=" + mHandleId + "): "
                + mSourceAddress + " => " + mSinkAddress;
    }

    /**
     * Given a parcel, populate our data members
     */
    private CarAudioPatchHandle(Parcel in) {
        mHandleId = in.readInt();
        mSourceAddress = in.readString();
        mSinkAddress = in.readString();
    }

    /**
     * Serialize our internal data to a parcel
     */
    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mHandleId);
        out.writeString(mSourceAddress);
        out.writeString(mSinkAddress);
    }

    public static final Parcelable.Creator<CarAudioPatchHandle> CREATOR =
                new Parcelable.Creator<CarAudioPatchHandle>() {
            public CarAudioPatchHandle createFromParcel(Parcel in) {
                return new CarAudioPatchHandle(in);
            }

            public CarAudioPatchHandle[] newArray(int size) {
                return new CarAudioPatchHandle[size];
            }
        };

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    public int describeContents() {
        return 0;
    }

    /**
     * returns the source address
     *
     * @hide
     */
    public String getSourceAddress() {
        return mSourceAddress;
    }

    /**
     * returns the sink address
     *
     * @hide
     */
    public String getSinkAddress() {
        return mSinkAddress;
    }

    /**
     * returns the patch handle
     *
     * @hide
     */
    public int getHandleId() {
        return mHandleId;
    }
}
