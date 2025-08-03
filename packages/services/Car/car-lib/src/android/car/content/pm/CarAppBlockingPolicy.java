/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.car.content.pm;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.SystemApi;
import android.car.builtin.os.ParcelHelper;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;

import java.util.Arrays;

/**
 * Contains application blocking policy
 * @hide
 */
@SystemApi
public final class CarAppBlockingPolicy implements Parcelable {
    private static final String TAG = CarAppBlockingPolicy.class.getSimpleName();

    public final AppBlockingPackageInfo[] whitelists;
    public final AppBlockingPackageInfo[] blacklists;

    public CarAppBlockingPolicy(AppBlockingPackageInfo[] whitelists,
            AppBlockingPackageInfo[] blacklists) {
        this.whitelists = whitelists;
        this.blacklists = blacklists;
    }

    public CarAppBlockingPolicy(Parcel in) {
        byte[] payload = ParcelHelper.readBlob(in);
        Parcel payloadParcel = Parcel.obtain();
        payloadParcel.unmarshall(payload, 0, payload.length);
        // reset to initial position to read
        payloadParcel.setDataPosition(0);
        whitelists = payloadParcel.createTypedArray(AppBlockingPackageInfo.CREATOR);
        blacklists = payloadParcel.createTypedArray(AppBlockingPackageInfo.CREATOR);
        payloadParcel.recycle();
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Parcel payloadParcel = Parcel.obtain();
        payloadParcel.writeTypedArray(whitelists, 0);
        payloadParcel.writeTypedArray(blacklists, 0);
        byte[] payload = payloadParcel.marshall();
        ParcelHelper.writeBlob(dest, payload);
        payloadParcel.recycle();
    }

    public static final Parcelable.Creator<CarAppBlockingPolicy> CREATOR =
            new Parcelable.Creator<CarAppBlockingPolicy>() {

                @Override
                public CarAppBlockingPolicy createFromParcel(Parcel in) {
                    return new CarAppBlockingPolicy(in);
                }

                @Override
                public CarAppBlockingPolicy[] newArray(int size) {
                    return new CarAppBlockingPolicy[size];
                }
            };

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(blacklists);
        result = prime * result + Arrays.hashCode(whitelists);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        CarAppBlockingPolicy other = (CarAppBlockingPolicy) obj;
        if (!Arrays.equals(blacklists, other.blacklists)) {
            return false;
        }
        if (!Arrays.equals(whitelists, other.whitelists)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "CarAppBlockingPolicy [whitelists=" + Arrays.toString(whitelists) + ", blacklists="
                + Arrays.toString(blacklists) + "]";
    }
}
