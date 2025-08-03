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

import static android.car.feature.Flags.FLAG_CAR_EVS_STREAM_MANAGEMENT;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.annotation.SystemApi;
import android.car.Car;
import android.car.annotation.RequiredFeature;
import android.car.evs.CarEvsManager;
import android.car.evs.CarEvsManager.CarEvsServiceType;
import android.car.feature.Flags;
import android.hardware.HardwareBuffer;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;

import java.util.Objects;

/**
 * Wraps around {@link android.hardware.HardwareBuffer} to embed additional metadata of the buffer
 * from the Extended View System service.
 *
 * @hide
 */
@SystemApi
@RequiredFeature(Car.CAR_EVS_SERVICE)
public final class CarEvsBufferDescriptor implements Parcelable, AutoCloseable {
    public static final @NonNull Parcelable.Creator<CarEvsBufferDescriptor> CREATOR =
            new Parcelable.Creator<CarEvsBufferDescriptor>() {
                @NonNull
                @Override
                public CarEvsBufferDescriptor createFromParcel(final Parcel in) {
                    return new CarEvsBufferDescriptor(in);
                }

                @NonNull
                @Override
                public CarEvsBufferDescriptor[] newArray(final int size) {
                    return new CarEvsBufferDescriptor[size];
                }
            };

    private final int mId;
    // This field shouldn't be accessed if CAR_EVS_STREAM_MANAGEMENT flag is not true.
    private final @CarEvsServiceType int mType;

    @NonNull
    private final HardwareBuffer mHardwareBuffer;

    /**
     * Creates a {@link CarEvsBufferDescriptor} given an unique identifier and
     * {@link android.hardware.HardwareBuffer}.
     *
     * @param id A 32-bit integer to uniquely identify associated hardware buffer.
     * @param buffer Hardware buffer that contains the imagery data from EVS service.
     */
    public CarEvsBufferDescriptor(int id, @NonNull final HardwareBuffer buffer) {
        this(id, Flags.carEvsStreamManagement() ?
                CarEvsManager.SERVICE_TYPE_UNKNOWN : CarEvsManager.SERVICE_TYPE_REARVIEW, buffer);
    }

    /**
     * Creates a {@link CarEvsBufferDescriptor} given an unique identifier and
     * {@link android.hardware.HardwareBuffer}.
     *
     * @param id A 32-bit integer to uniquely identify associated hardware buffer.
     * @param buffer Hardware buffer that contains the imagery data from EVS service.
     */
    @FlaggedApi(FLAG_CAR_EVS_STREAM_MANAGEMENT)
    public CarEvsBufferDescriptor(int id, @CarEvsServiceType int type,
            @NonNull HardwareBuffer buffer) {
        Objects.requireNonNull(buffer, "HardwardBuffer cannot be null.");

        mId = id;
        mHardwareBuffer = buffer;
        mType = type;
    }

    /** Parcelable methods */
    private CarEvsBufferDescriptor(final Parcel in) {
        mId = in.readInt();
        mType = in.readInt();
        mHardwareBuffer = Objects.requireNonNull(HardwareBuffer.CREATOR.createFromParcel(in));
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest, final int flags) {
        dest.writeInt(mId);
        dest.writeInt(mType);
        mHardwareBuffer.writeToParcel(dest, flags);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public String toString() {
        return "CarEvsBufferDescriptor: id = " + mId + ", buffer = " + mHardwareBuffer;
    }

    @Override
    @SuppressLint("GenericException")
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    @Override
    public void close() {
        if (!mHardwareBuffer.isClosed()) {
            mHardwareBuffer.close();
        }
    }

    /**
     * Returns an unique identifier of the hardware buffer described by this object.
     *
     * @return A 32-bit signed integer unique buffer identifier.
     */
    public int getId() {
        return mId;
    }

    /**
     * Returns a reference to {@link android.hardware.HardwareBuffer} registered
     * to this descriptor.
     *
     * @return the registered {@link android.hardware.HardwareBuffer}.
     */
    @NonNull
    public HardwareBuffer getHardwareBuffer() {
        return mHardwareBuffer;
    }

    /**
     * Returns a type of a camera this buffer is originated from.
     *
     * @return {@link CarEvsServiceType}.
     */
    @FlaggedApi(FLAG_CAR_EVS_STREAM_MANAGEMENT)
    public @CarEvsServiceType int getType() {
        return mType;
    }
}
