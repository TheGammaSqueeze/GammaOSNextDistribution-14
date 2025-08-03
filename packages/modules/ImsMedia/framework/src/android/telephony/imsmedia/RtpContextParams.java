/**
 * Copyright (C) 2023 The Android Open Source Project
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
 * Class to encapsulate RTP (Real-time Transport Protocol) parameters
 * required for RTP context transfer to maintain RTP stream continuity
 *
 * @hide
 */

public final class RtpContextParams implements Parcelable {
    /** Synchronization source (SSRC) identifier of RTP stream **/
    private final long mSsrc;
    /** The timestamp reflects the sampling instant of the last RTP packet sent **/
    private final long mTimestamp;
    /** The sequence number of last RTP packet sent **/
    private final int mSequenceNumber;
    /** Maximum 32bit value **/
    private static final long MAX_VALUE_32BIT = 4294967295L;
    /** Maximum 16bit value **/
    private static final int MAX_VALUE_16BIT = 65535;

    /** @hide **/
    public RtpContextParams(Parcel in) {
        mSsrc = in.readLong();
        mTimestamp = in.readLong();
        mSequenceNumber = in.readInt();
    }

    private RtpContextParams(long ssrc, long timestamp, int sequenceNumber) {
        this.mSsrc = ssrc;
        this.mTimestamp  = timestamp;
        this.mSequenceNumber = sequenceNumber;
    }

    /** @hide **/
    public long getSsrc() {
        return mSsrc;
    }

    /** @hide **/
    public long getTimestamp() {
        return mTimestamp;
    }

    /** @hide **/
    public int getSequenceNumber() {
        return mSequenceNumber;
    }

    @NonNull
    @Override
    public String toString() {
        return "RtpContextParams: {mSsrc=" + Long.toHexString(mSsrc)
            + ", mTimestamp=" + Long.toHexString(mTimestamp)
            + ", mSequenceNumber=" + Integer.toHexString(mSequenceNumber)
            + " }";
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSsrc, mTimestamp, mSequenceNumber);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (o == null || !(o instanceof RtpContextParams) || hashCode() != o.hashCode()) {
            return false;
        }

        if (this == o) {
            return true;
        }

        RtpContextParams s = (RtpContextParams) o;

        return (mSsrc == s.mSsrc
            && mTimestamp == s.mTimestamp
            && mSequenceNumber == s.mSequenceNumber);
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
        dest.writeLong(mSsrc);
        dest.writeLong(mTimestamp);
        dest.writeInt(mSequenceNumber);
    }

    public static final @NonNull Parcelable.Creator<RtpContextParams>
            CREATOR = new Parcelable.Creator() {
                public RtpContextParams createFromParcel(Parcel in) {
                    // TODO use builder class so it will validate
                    return new RtpContextParams(in);
                }

                public RtpContextParams[] newArray(int size) {
                    return new RtpContextParams[size];
                }
            };

    /**
     * Provides a convenient way to set the fields of a {@link RtpContextParams}
     * when creating a new instance.
     */
    public static final class Builder {
        private long mSsrc;
        private long mTimestamp;
        private int mSequenceNumber;

        /**
         * Default constructor for Builder.
         */
        public Builder() {
        }

        /**
         * Set the Synchronization source (SSRC) identifier of RTP stream
         * Throws IllegalArgumentException if SSRC is set greater than maximum 32bit value
         *
         * @param ssrc ssrc of RTP stream
         * @return The same instance of the builder.
         */
        public @NonNull Builder setSsrc(final long ssrc) {
            if (Long.compareUnsigned(ssrc, MAX_VALUE_32BIT) > 0) {
                throw new IllegalArgumentException("Invalid ssrc value: " + ssrc);
            } else {
                this.mSsrc = ssrc;
            }
            return this;
        }

        /**
         * Set the timestamp
         * Throws IllegalArgumentException if timestamp is set greater than maximum 32bit value
         *
         * @param timestamp The timestamp of last RTP packet sent
         * @return The same instance of the builder.
         */
        public @NonNull Builder setTimestamp(final long timestamp) {
            if (Long.compareUnsigned(timestamp, MAX_VALUE_32BIT) > 0) {
                throw new IllegalArgumentException("Invalid timestamp value: " + timestamp);
            } else {
                this.mTimestamp = timestamp;
            }
            return this;
        }

        /**
         * Set the Sequence Number
         * Throws IllegalArgumentException if sequenceNumber is set greater than maximum 16bit value
         *
         * @param sequenceNumber The sequence number of last RTP packet sent
         * @return The same instance of the builder.
         */
        public @NonNull Builder setSequenceNumber(final int sequenceNumber) {
            if (Integer.compareUnsigned(sequenceNumber, MAX_VALUE_16BIT) > 0) {
                throw new IllegalArgumentException(
                        "Invalid sequenceNumber value: " + sequenceNumber);
            } else {
                this.mSequenceNumber = sequenceNumber;
            }
            return this;
        }

        /**
         * Build the RtpContextParams.
         *
         * @return the RtpContextParams object.
         */
        public @NonNull RtpContextParams build() {
            // TODO validation
            return new RtpContextParams(mSsrc, mTimestamp, mSequenceNumber);
        }
    }
}
