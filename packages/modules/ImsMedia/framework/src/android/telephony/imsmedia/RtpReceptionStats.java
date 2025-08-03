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
 * Class to encapsulate RTP (Real-time Transport Protocol) reception parameters required to transfer
 * the reception statistics for the delay adjustment
 *
 * @hide
 */

public final class RtpReceptionStats implements Parcelable {
    /* The timestamp reflects the sampling instant of the latest RTP packet received */
    private int mRtpTimestamp;
    /* The timestamp reflects the sampling instant of the latest RTCP-SR packet received */
    private int mRtcpSrTimestamp;
    /* The NTP timestamp of latest RTCP-SR packet received */
    private long mRtcpSrNtpTimestamp;
    /**
     * The mean jitter buffer delay of a media stream from received to playback, measured in
     * milliseconds, within the reporting interval
     */
    private int mJitterBufferMs;
    /* The round trip time delay in millisecond when latest RTP packet received */
    private int mRoundTripTimeMs;
    /* Maximum 16bit value */
    private static final int MAX_VALUE_16BIT = Short.MAX_VALUE;

    /** @hide **/
    public RtpReceptionStats(Parcel in) {
        mRtpTimestamp = in.readInt();
        mRtcpSrTimestamp = in.readInt();
        mRtcpSrNtpTimestamp = in.readLong();
        mJitterBufferMs = in.readInt();
        mRoundTripTimeMs = in.readInt();
    }

    private RtpReceptionStats(int rtpTs, int rtcpTs, long ntp, int delay, int roundtrip) {
        this.mRtpTimestamp = rtpTs;
        this.mRtcpSrTimestamp = rtcpTs;
        this.mRtcpSrNtpTimestamp = ntp;
        this.mJitterBufferMs = delay;
        this.mRoundTripTimeMs = roundtrip;
    }

    /** @hide **/
    public int getRtpTimestamp() {
        return mRtpTimestamp;
    }

    /** @hide **/
    public int getRtcpSrTimestamp() {
        return mRtcpSrTimestamp;
    }

    /** @hide **/
    public long getRtcpSrNtpTimestamp() {
        return mRtcpSrNtpTimestamp;
    }

    /** @hide **/
    public int getJitterBufferMs() {
        return mJitterBufferMs;
    }

    /** @hide **/
    public int getRoundTripTimeMs() {
        return mRoundTripTimeMs;
    }


    @NonNull
    @Override
    public String toString() {
        return "RtpReceptionStats: {"
                + "mRtpTimestamp=" + mRtpTimestamp
                + ", mRtcpSrTimestamp=" + mRtcpSrTimestamp
                + ", mRtcpSrNtpTimestamp=" + mRtcpSrNtpTimestamp
                + ", mJitterBufferMs=" + mJitterBufferMs
                + ", mRoundTripTimeMs=" + mRoundTripTimeMs
            + " }";
    }

    @Override
    public int hashCode() {
        return Objects.hash(mRtpTimestamp, mRtcpSrTimestamp, mRtcpSrNtpTimestamp,
            mJitterBufferMs, mRoundTripTimeMs);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (o == null || !(o instanceof RtpReceptionStats) || hashCode() != o.hashCode()) {
            return false;
        }

        if (this == o) {
            return true;
        }

        RtpReceptionStats s = (RtpReceptionStats) o;

        return (mRtpTimestamp == s.mRtpTimestamp
                && mRtcpSrTimestamp == s.mRtcpSrTimestamp
                && mRtcpSrNtpTimestamp == s.mRtcpSrNtpTimestamp
                && mJitterBufferMs == s.mJitterBufferMs
                && mRoundTripTimeMs == s.mRoundTripTimeMs);
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
        dest.writeInt(mRtpTimestamp);
        dest.writeInt(mRtcpSrTimestamp);
        dest.writeLong(mRtcpSrNtpTimestamp);
        dest.writeInt(mJitterBufferMs);
        dest.writeInt(mRoundTripTimeMs);
    }

    public static final @NonNull Parcelable.Creator<RtpReceptionStats>
            CREATOR = new Parcelable.Creator() {
                public RtpReceptionStats createFromParcel(Parcel in) {
                    // TODO use builder class so it will validate
                    return new RtpReceptionStats(in);
                }

                public RtpReceptionStats[] newArray(int size) {
                    return new RtpReceptionStats[size];
                }
            };

    /**
     * Provides a convenient way to set the fields of a {@link RtpReceptionStats} when creating a
     * new instance.
     */
    public static final class Builder {
        private int mRtpTimestamp;
        private int mRtcpSrTimestamp;
        private long mRtcpSrNtpTimestamp;
        private int mJitterBufferMs;
        private int mRoundTripTimeMs;

        /**
         * Default constructor for Builder.
         */
        public Builder() {
        }

        /**
         * Set the timestamp.
         *
         * @param timestamp The timestamp of last RTP packet sent
         * @return The same instance of the builder.
         */
        public @NonNull Builder setRtpTimestamp(final int timestamp) {
            this.mRtpTimestamp = timestamp;
            return this;
        }

        /**
         * Set the timestamp reflects the sampling instant of the latest RTCP-SR packet received
         *
         * @param timestamp The timestamp of the latest RTCP-SR packet received
         * @return The same instance of the builder.
         */
        public @NonNull Builder setRtcpSrTimestamp(final int timestamp) {
            this.mRtcpSrTimestamp = timestamp;
            return this;
        }

        /**
         * Set the NTP timestamp of latest RTCP-SR packet received
         *
         * @param ntp The NTP timestamp of the latest RTCP-SR packet received
         * @return The same instance of the builder.
         */
        public @NonNull Builder setRtcpSrNtpTimestamp(final long ntp) {
            this.mRtcpSrNtpTimestamp = ntp;
            return this;
        }

        /**
         * Set the mean jitter buffering time from received to play out in millisecond measured in
         * the reporting interval
         *
         * @param time The mean jitter buffering time in millisecond
         * @return The same instance of the builder.
         */
        public @NonNull Builder setJitterBufferMs(final int time) {
            this.mJitterBufferMs = time;
            return this;
        }

        /**
         * Set round trip time delay in millisecond when latest RTP packet received
         *
         * @param time The round trip time in millisecond
         * @return The same instance of the builder.
         */
        public @NonNull Builder setRoundTripTimeMs(final int time) {
            this.mRoundTripTimeMs = time;
            return this;
        }

        /**
         * Build the RtpReceptionStats.
         *
         * @return the RtpReceptionStats object.
         */
        public @NonNull RtpReceptionStats build() {
            return new RtpReceptionStats(mRtpTimestamp, mRtcpSrTimestamp, mRtcpSrNtpTimestamp,
                    mJitterBufferMs, mRoundTripTimeMs);
        }
    }
}
