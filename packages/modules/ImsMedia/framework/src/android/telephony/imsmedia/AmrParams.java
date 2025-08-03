/**
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

package android.telephony.imsmedia;

import android.annotation.IntDef;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * The class represents AMR (Adaptive Multi-Rate) codec parameters.
 *
 * @hide
 */
public final class AmrParams implements Parcelable {
    /** 4.75 kbps for AMR / 6.6 kbps for AMR-WB */
    public static final int AMR_MODE_0 = 1 << 0;
    /** 5.15 kbps for AMR / 8.855 kbps for AMR-WB */
    public static final int AMR_MODE_1 = 1 << 1;
    /** 5.9 kbps for AMR / 12.65 kbps for AMR-WB */
    public static final int AMR_MODE_2 = 1 << 2;
    /** 6.7 kbps for AMR / 14.25 kbps for AMR-WB */
    public static final int AMR_MODE_3 = 1 << 3;
    /** 7.4 kbps for AMR / 15.85 kbps for AMR-WB */
    public static final int AMR_MODE_4 = 1 << 4;
    /** 7.95 kbps for AMR / 18.25 kbps for AMR-WB */
    public static final int AMR_MODE_5 = 1 << 5;
    /** 10.2 kbps for AMR / 19.85 kbps for AMR-WB */
    public static final int AMR_MODE_6 = 1 << 6;
    /** 12.2 kbps for AMR / 23.05 kbps for AMR-WB */
    public static final int AMR_MODE_7 = 1 << 7;
    /** Silence frame for AMR / 23.85 kbps for AMR-WB */
    public static final int AMR_MODE_8 = 1 << 8;

    /** @hide */
    @IntDef(
        value = {
           AMR_MODE_0,
           AMR_MODE_1,
           AMR_MODE_2,
           AMR_MODE_3,
           AMR_MODE_4,
           AMR_MODE_5,
           AMR_MODE_6,
           AMR_MODE_7,
           AMR_MODE_8,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AmrMode {}

    /** mode-set: AMR codec mode to represent the bit rate */
    private final @AmrMode int amrMode;
    /**
     * octet-align: If it's set to true then all fields in the AMR/AMR-WB header
     * shall be aligned to octet boundaries by adding padding bits.
     */
    private final boolean octetAligned;
    /**
     * max-red: It’s the maximum duration in milliseconds that elapses between the
     * primary (first) transmission of a frame and any redundant transmission that
     * the sender will use. This parameter allows a receiver to have a bounded delay
     * when redundancy is used. Allowed values are between 0 (no redundancy will be
     * used) and 65535. If the parameter is omitted, no limitation on the use of
     * redundancy is present. See RFC 4867
     */
    private final int maxRedundancyMillis;

    /** @hide **/
    public AmrParams(Parcel in) {
        amrMode = in.readInt();
        octetAligned = in.readBoolean();
        maxRedundancyMillis = in.readInt();
    }

    private AmrParams(@AmrMode int amrMode, boolean octetAligned, int maxRedundancyMillis) {
        this.amrMode = amrMode;
        this.octetAligned = octetAligned;
        this.maxRedundancyMillis = maxRedundancyMillis;
    }

    /** @hide **/
    public @AmrMode int getAmrMode() {
        return amrMode;
    }

    /** @hide **/
    public boolean getOctetAligned() {
        return octetAligned;
    }

    /** @hide **/
    public int getMaxRedundancyMillis() {
        return maxRedundancyMillis;
    }

    @NonNull
    @Override
    public String toString() {
        return "AmrParams: {amrMode=" + amrMode
                + ", octetAligned=" + octetAligned
                + ", maxRedundancyMillis=" + maxRedundancyMillis
                + " }";
    }

    @Override
    public int hashCode() {
        return Objects.hash(amrMode, octetAligned, maxRedundancyMillis);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (o == null || !(o instanceof AmrParams) || hashCode() != o.hashCode()) {
            return false;
        }

        if (this == o) {
            return true;
        }

        AmrParams s = (AmrParams) o;

        return (amrMode == s.amrMode
                && octetAligned == s.octetAligned
                && maxRedundancyMillis == s.maxRedundancyMillis);
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
        dest.writeInt(amrMode);
        dest.writeBoolean(octetAligned);
        dest.writeInt(maxRedundancyMillis);
    }

    public static final @NonNull Parcelable.Creator<AmrParams>
        CREATOR = new Parcelable.Creator() {
        public AmrParams createFromParcel(Parcel in) {
            // TODO use builder class so it will validate
            return new AmrParams(in);
        }

        public AmrParams[] newArray(int size) {
            return new AmrParams[size];
        }
    };

    /**
     * Provides a convenient way to set the fields of a {@link AmrParams}
     * when creating a new instance.
     */
    public static final class Builder {
        private @AmrMode int amrMode;
        private boolean octetAligned;
        private int maxRedundancyMillis;

        /**
         * Default constructor for Builder.
         */
        public Builder() {
        }

        /**
         * Set the AMR codec mode to represent the bit rate
         *
         * @param amrMode AMR codec mode
         * @return The same instance of the builder.
         */
        public @NonNull Builder setAmrMode(final @AmrMode int amrMode) {
            this.amrMode = amrMode;
            return this;
        }

        /**
         * Set whether octet aligned or not for AMR/AMR-WB headers
         *
         * @param octetAligned {@code true} means octets shall be aligned for AMR/AMR-WB header
         * @return The same instance of the builder.
         */
        public @NonNull Builder setOctetAligned(final boolean octetAligned) {
            this.octetAligned = octetAligned;
            return this;
        }

        /**
         * Set the maximum redundany in milliseconds.
         *
         * max-red: It’s the maximum duration in milliseconds that elapses between the
         * primary (first) transmission of a frame and any redundant transmission that
         * the sender will use. This parameter allows a receiver to have a bounded delay
         * when redundancy is used. Allowed values are between 0 (no redundancy will be
         * used) and 65535. If the parameter is omitted, no limitation on the use of
         * redundancy is present. See RFC 4867.
         *
         * @param maxRedundancyMillis the maximum duration in milliseconds.
         * @return The same instance of the builder.
         */
        public @NonNull Builder setMaxRedundancyMillis(final int maxRedundancyMillis) {
            this.maxRedundancyMillis = maxRedundancyMillis;
            return this;
        }

        /**
         * Build the AmrParams.
         *
         * @return the AmrParams object.
         */
        public @NonNull AmrParams build() {
            // TODO validation
            return new AmrParams(amrMode, octetAligned, maxRedundancyMillis);
        }
    }
}

