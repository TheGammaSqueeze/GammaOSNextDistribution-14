/*
 * Copyright 2023 The Android Open Source Project
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

package android.remoteauth;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Remote device that can be registered as remote authenticator.
 *
 * @hide
 */
// TODO(b/295407748) Change to use @DataClass
// TODO(b/290092977): Add back after M-2023-11 release - @SystemApi(client = MODULE_LIBRARIES)
public final class RemoteDevice implements Parcelable {
    /** The remote device is not registered as remote authenticator. */
    public static final int STATE_NOT_REGISTERED = 0;
    /** The remote device is registered as remote authenticator. */
    public static final int STATE_REGISTERED = 1;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({STATE_NOT_REGISTERED, STATE_REGISTERED})
    @interface RegistrationState {}

    @NonNull private final String mName;
    private final @RegistrationState int mRegistrationState;
    private final int mConnectionId;

    public static final @NonNull Creator<RemoteDevice> CREATOR =
            new Creator<>() {
                @Override
                public RemoteDevice createFromParcel(Parcel in) {
                    RemoteDevice.Builder builder = new RemoteDevice.Builder();
                    builder.setName(in.readString());
                    builder.setRegistrationState(in.readInt());
                    builder.setConnectionId(in.readInt());

                    return builder.build();
                }

                @Override
                public RemoteDevice[] newArray(int size) {
                    return new RemoteDevice[size];
                }
            };

    private RemoteDevice(
            @Nullable String name,
            @RegistrationState int registrationState,
            @NonNull int connectionId) {
        this.mName = name;
        this.mRegistrationState = registrationState;
        this.mConnectionId = connectionId;
    }

    /** Gets the name of the {@link RemoteDevice} device. */
    @Nullable
    public String getName() {
        return mName;
    }

    /** Returns registration state of the {@link RemoteDevice}. */
    public @RegistrationState int getRegistrationState() {
        return mRegistrationState;
    }

    /** Returns connection id of the {@link RemoteDevice}. */
    @NonNull
    public int getConnectionId() {
        return mConnectionId;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** Returns a string representation of {@link RemoteDevice}. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("RemoteDevice [");
        sb.append("name=").append(mName).append(", ");
        sb.append("registered=").append(mRegistrationState).append(", ");
        sb.append("connectionId=").append(mConnectionId);
        sb.append("]");
        return sb.toString();
    }

    /** Returns true if this {@link RemoteDevice} object is equals to other. */
    @Override
    public boolean equals(Object other) {
        if (other instanceof RemoteDevice) {
            RemoteDevice otherDevice = (RemoteDevice) other;
            return Objects.equals(this.mName, otherDevice.mName)
                    && this.getRegistrationState() == otherDevice.getRegistrationState()
                    && this.mConnectionId == otherDevice.mConnectionId;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mName, mRegistrationState, mConnectionId);
    }

    /**
     * Helper function for writing {@link RemoteDevice} to a Parcel.
     *
     * @param dest The Parcel in which the object should be written.
     * @param flags Additional flags about how the object should be written.
     */
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        String name = getName();
        dest.writeString(name);
        dest.writeInt(getRegistrationState());
        dest.writeInt(getConnectionId());
    }

    /** Builder for {@link RemoteDevice} objects. */
    public static final class Builder {
        @Nullable private String mName;
        // represents if device is already registered
        private @RegistrationState int mRegistrationState;
        private int mConnectionId;

        private Builder() {
        }

        public Builder(final int connectionId) {
            this.mConnectionId = connectionId;
        }

        /**
         * Sets the name of the {@link RemoteDevice} device.
         *
         * @param name of the {@link RemoteDevice}. Can be {@code null} if there is no name.
         */
        @NonNull
        public RemoteDevice.Builder setName(@Nullable String name) {
            this.mName = name;
            return this;
        }

        /**
         * Sets the registration state of the {@link RemoteDevice} device.
         *
         * @param registrationState of the {@link RemoteDevice}.
         */
        @NonNull
        public RemoteDevice.Builder setRegistrationState(@RegistrationState int registrationState) {
            this.mRegistrationState = registrationState;
            return this;
        }

        /**
         * Sets the connectionInfo of the {@link RemoteDevice} device.
         *
         * @param connectionId of the RemoteDevice.
         */
        @NonNull
        public RemoteDevice.Builder setConnectionId(int connectionId) {
            this.mConnectionId = connectionId;
            return this;
        }

        /**
         * Creates the {@link RemoteDevice} instance.
         *
         * @return the configured {@link RemoteDevice} instance.
         */
        @NonNull
        public RemoteDevice build() {
            return new RemoteDevice(mName, mRegistrationState, mConnectionId);
        }
    }
}
