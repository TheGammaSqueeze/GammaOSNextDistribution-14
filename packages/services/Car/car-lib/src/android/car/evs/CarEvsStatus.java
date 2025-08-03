/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.car.evs;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.car.Car;
import android.car.annotation.RequiredFeature;
import android.car.evs.CarEvsManager.CarEvsServiceState;
import android.car.evs.CarEvsManager.CarEvsServiceType;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;

/**
 * Describes current status of CarEvsService with its current state and service type.
 *
 * @hide
 */
@SystemApi
@RequiredFeature(Car.CAR_EVS_SERVICE)
public final class CarEvsStatus implements Parcelable {
    public static final @NonNull Parcelable.Creator<CarEvsStatus> CREATOR =
            new Parcelable.Creator<CarEvsStatus>() {
                @NonNull
                @Override
                public CarEvsStatus createFromParcel(final Parcel in) {
                    return new CarEvsStatus(in);
                }

                @NonNull
                @Override
                public CarEvsStatus[] newArray(final int size) {
                    return new CarEvsStatus[size];
                }
            };

    private final @CarEvsServiceType int mServiceType;
    private final @CarEvsServiceState int mState;

    /**
     * Creates a {@link CarEvsStatus} with a current state and type of CarEvsService.
     *
     * @param type {@link android.car.evs.CarEvsManager.CarEvsServiceType}
     * @param state {@link android.car.evs.CarEvsManager.CarEvsServiceState}
     */
    public CarEvsStatus(@CarEvsServiceType int type, @CarEvsServiceState int state) {
        mServiceType = type;
        mState = state;
    }

    /** Parcelable methods */
    private CarEvsStatus(final Parcel in) {
        mServiceType = in.readInt();
        mState = in.readInt();
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest, final int flags) {
        dest.writeInt(mServiceType);
        dest.writeInt(mState);
    }

    @Override
    public String toString() {
        return "CarEvsStatus: mServiceType = " + mServiceType + " + mState + " + mState;
    }

    /**
     * Returns a current state of CarEvsService
     *
     * @return {@link android.car.evs.CarEvsManager.CarEvsServiceState}
     */
    public @CarEvsServiceState int getState() {
        return mState;
    }

    /**
     * Returns a type of what CarEvsService currently provides
     *
     * @return {@link android.car.evs.CarEvsManager.CarEvsServiceType}
     */
    public @CarEvsServiceType int getServiceType() {
        return mServiceType;
    }
}
