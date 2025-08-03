/**
 * Copyright (C) 2024 The Android Open Source Project
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

package android.telephony.imsmedia;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * The class represents ANBR (Access Network Bitrate Recommendation) parameters.
 *
 * @hide
 */
public final class AnbrMode implements Parcelable {

    private int mAnbrUplinkMode;
    private int mAnbrDownlinkMode;

    /** @hide **/
    public AnbrMode(Parcel in) {
        mAnbrUplinkMode = in.readInt();
        mAnbrDownlinkMode = in.readInt();
    }

    /** @hide */
    AnbrMode(Builder builder) {
        this.mAnbrUplinkMode = builder.mAnbrUplinkMode;
        this.mAnbrDownlinkMode = builder.mAnbrDownlinkMode;
    }

    /** @hide **/
    public int getAnbrUplinkCodecMode() {
        return mAnbrUplinkMode;
    }

    /** @hide **/
    public void setAnbrUplinkCodecMode(final int anbrUplinkMode) {
        this.mAnbrUplinkMode = anbrUplinkMode;
    }

    /** @hide **/
    public int getAnbrDownlinkCodecMode() {
        return mAnbrDownlinkMode;
    }

    /** @hide **/
    public void setAnbrDownlinkCodecMode(final int anbrDownlinkMode) {
        this.mAnbrDownlinkMode = anbrDownlinkMode;
    }

    @NonNull
    @Override
    public String toString() {
        return "AnbrMode: {mAnbrUplinkMode =" + mAnbrUplinkMode
                + ", mAnbrDownlinkMode =" + mAnbrDownlinkMode
                + " }";
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAnbrUplinkMode, mAnbrDownlinkMode);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (o == null || !(o instanceof AnbrMode) || hashCode() != o.hashCode()) {
            return false;
        }

        if (this == o) {
            return true;
        }

        AnbrMode s = (AnbrMode) o;

        return ((mAnbrUplinkMode == s.mAnbrUplinkMode)
                && (mAnbrDownlinkMode == s.mAnbrDownlinkMode));
    }

    /**
     * {@link Parcelable#describeContents}
     */
    public int describeContents() {
        return 0;
    }

    /**
     * {@link Parcelable#writeToParcel}
     */
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mAnbrUplinkMode);
        dest.writeInt(mAnbrDownlinkMode);
    }

    public static final @NonNull Parcelable.Creator<AnbrMode>
            CREATOR = new Parcelable.Creator() {
                public AnbrMode createFromParcel(Parcel in) {
                    // TODO use builder class so it will validate
                    return new AnbrMode(in);
                }

                public AnbrMode[] newArray(int size) {
                    return new AnbrMode[size];
                }
            };

    /**
     * Provides a convenient way to set the fields of the {@link AmrParams}
     * when creating a new instance.
     */
    public static final class Builder {
        private int mAnbrUplinkMode;
        private int mAnbrDownlinkMode;

        /**
         * Default constructor for Builder.
         */
        public Builder() {
        }

        /**
         * Set the AMR codec mode to represent the bit rate
         *
         * @param anbrUplinkMode AMR codec mode
         * @return The same instance of the builder.
         */
        public @NonNull Builder setAnbrUplinkCodecMode(final int anbrUplinkMode) {
            this.mAnbrUplinkMode = anbrUplinkMode;
            return this;
        }

        /**
         * Set the AMR codec mode to represent the bit rate
         *
         * @param anbrDownlinkMode AMR codec mode
         * @return The same instance of the builder.
         */
        public @NonNull Builder setAnbrDownlinkCodecMode(final int anbrDownlinkMode) {
            this.mAnbrDownlinkMode = anbrDownlinkMode;
            return this;
        }

        /**
         * Build the AnbrMode.
         *
         * @return the AnbrMode object.
         */
        public @NonNull AnbrMode build() {
            // TODO validation
            return new AnbrMode(this);
        }
    }
}
