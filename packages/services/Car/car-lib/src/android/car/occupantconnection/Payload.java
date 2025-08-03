/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.car.occupantconnection;


import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.os.IBinder;
import android.os.Parcel;

import com.android.car.internal.LargeParcelableBase;

import java.util.Arrays;
import java.util.Objects;

/**
 * A payload sent between client apps that have the same package name but run in different occupant
 * zones in the car.
 * <p>
 * The payload either contains a byte array, or a Binder object. In the former case, the payload
 * can be sent to any occupant zone in the car, no matter whether it runs on the same Android
 * instance or another Android instance. In the latter case, the payload can be only sent to another
 * occupant zone on the same Android instance; otherwise, the receiver app can still receive the
 * payload, but the Binder object of the payload will be {@code null}.
 * <p>
 * After establishing a connection to the receiver client, the sender client can send a payload via
 * {@link CarOccupantConnectionManager#sendPayload}. The receiver service in the receiver client
 * will receive the payload via {@link AbstractReceiverService#onPayloadReceived}, then dispatch it
 * to the proper receiver endpoint(s).
 * <p>
 * The sender client can put the receiver endpoint ID in the payload so that the receiver service
 * knows which receiver endpoint(s) to dispatch the payload to.
 *
 * @hide
 */
@SystemApi
public final class Payload extends LargeParcelableBase {

    @Nullable
    private byte[] mBytes;

    @Nullable
    private IBinder mBinder;

    public Payload(@NonNull byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes cannot be null");
        this.mBytes = bytes.clone();
    }

    private Payload(@NonNull Parcel in) {
        super(in);
    }

    /**
     * Creates a Payload that holds an IBinder object. This type of Payload
     * can only be sent between occupant zones running on the same Android instance.
     */
    public Payload(@NonNull IBinder binder) {
        this.mBinder = Objects.requireNonNull(binder, "binder cannot be null");
    }

    /** Returns a reference to the byte array of the payload. */
    @Nullable
    public byte[] getBytes() {
        return mBytes;
    }

    /** Returns a reference to the Binder object of the payload. */
    @Nullable
    public IBinder getBinder() {
        return mBinder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof Payload) {
            Payload other = (Payload) o;
            if (containsBinder()) {
                return mBinder == other.mBinder;
            } else {
                return Arrays.equals(mBytes, other.mBytes);
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        if (containsBinder()) {
            return Objects.hashCode(mBinder);
        } else {
            return Arrays.hashCode(mBytes);
        }
    }

    @NonNull
    public static final Creator<Payload> CREATOR = new Creator<>() {
        /**
         * {@inheritDoc}
         */
        @Override
        public Payload createFromParcel(Parcel in) {
            return new Payload(in);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Payload[] newArray(int size) {
            return new Payload[size];
        }
    };

    /**
     * {@inheritDoc}
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /** Writes this {@link Payload} into the given {@link Parcel}. */
    @Override
    public void serialize(@NonNull Parcel dest, int flags) {
        dest.writeBoolean(containsBinder());
        // writeByteArray() uses shared memory, so it cannot be called with writeStrongBinder()
        if (containsBinder()) {
            dest.writeStrongBinder(mBinder);
        } else {
            dest.writeByteArray(mBytes);
        }
    }

    /** Writes {@code null} {@link Payload} to the given {@link Parcel}. */
    @Override
    public void serializeNullPayload(@NonNull Parcel dest) {
        dest.writeBoolean(false);
        dest.writeByteArray(null);
    }

    /** Reads a {@link Payload} from the given {@link Parcel}. */
    @Override
    public void deserialize(@NonNull Parcel src) {
        if (src.readBoolean()) {
            mBinder = src.readStrongBinder();
            mBytes = null;
        } else {
            mBytes = src.createByteArray();
            mBinder = null;
        }
    }

    /**
     * This function returns {@code true} if the payload contains a Binder object, and
     * {@code false} if it contains a byte array.
     */
    private boolean containsBinder() {
        return mBinder != null;
    }

    // The method needs to be overridden for a CtsSystemApiAnnotationTestCases. Ideally test should
    // be fixed, but it is an edge case, so these methods are overridden. It is happening because
    // LargeParcelableBase is hidden class but it implements parcelable and closeable which are
    // public APIs. So the test is not able to find these methods in payload.java and complains.
    // More details in b/275738385
    @Override
    public void close() {
        super.close();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
    }
}
