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

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * Class to set the threshold for media quality status notifications
 *
 * @hide
 */
public final class MediaQualityThreshold implements Parcelable {
    private final int[] mRtpInactivityTimerMillis;
    private final int mRtcpInactivityTimerMillis;
    private final int mRtpHysteresisTimeInMillis;
    private final int mRtpPacketLossDurationMillis;
    private final int[] mRtpPacketLossRate;
    private final int[] mRtpJitterMillis;
    private final boolean mNotifyCurrentStatus;
    private final int mVideoBitrateBps;

    /** @hide **/
    public MediaQualityThreshold(Parcel in) {
        int arrayLength = in.readInt();
        mRtpInactivityTimerMillis = new int[arrayLength];
        for (int i = 0; i < arrayLength; i++) {
            mRtpInactivityTimerMillis[i] = in.readInt();
        }
        mRtcpInactivityTimerMillis = in.readInt();
        mRtpHysteresisTimeInMillis = in.readInt();
        mRtpPacketLossDurationMillis = in.readInt();
        arrayLength = in.readInt();
        mRtpPacketLossRate = new int[arrayLength];
        for (int i = 0; i < arrayLength; i++) {
            mRtpPacketLossRate[i] = in.readInt();
        }
        arrayLength = in.readInt();
        mRtpJitterMillis = new int[arrayLength];
        for (int i = 0; i < arrayLength; i++) {
            mRtpJitterMillis[i] = in.readInt();
        }
        mNotifyCurrentStatus = in.readBoolean();
        mVideoBitrateBps = in.readInt();
    }

    /** @hide **/
    public MediaQualityThreshold(Builder builder) {
        mRtpInactivityTimerMillis = Arrays.copyOf(builder.mRtpInactivityTimerMillis,
            builder.mRtpInactivityTimerMillis.length);
        mRtcpInactivityTimerMillis = builder.mRtcpInactivityTimerMillis;
        mRtpHysteresisTimeInMillis = builder.mRtpHysteresisTimeInMillis;
        mRtpPacketLossDurationMillis = builder.mRtpPacketLossDurationMillis;
        mRtpPacketLossRate = Arrays.copyOf(builder.mRtpPacketLossRate,
            builder.mRtpPacketLossRate.length);
        mRtpJitterMillis = Arrays.copyOf(builder.mRtpJitterMillis,
            builder.mRtpJitterMillis.length);
        mNotifyCurrentStatus = builder.mNotifyCurrentStatus;
        mVideoBitrateBps = builder.mVideoBitrateBps;
    }

    /** @hide **/
    public int[] getRtpInactivityTimerMillis() {
        return mRtpInactivityTimerMillis;
    }

    /** @hide **/
    public int getRtcpInactivityTimerMillis() {
        return mRtcpInactivityTimerMillis;
    }

    /** @hide **/
    public int getRtpHysteresisTimeInMillis() {
        return mRtpHysteresisTimeInMillis;
    }

    /** @hide **/
    public int getRtpPacketLossDurationMillis() {
        return mRtpPacketLossDurationMillis;
    }

    /** @hide **/
    public int[] getRtpPacketLossRate() {
        return mRtpPacketLossRate;
    }

    /** @hide **/
    public int[] getRtpJitterMillis() {
        return mRtpJitterMillis;
    }

    /** @hide **/
    public boolean getNotifyCurrentStatus() {
        return mNotifyCurrentStatus;
    }

    public int getVideoBitrateBps() {
        return mVideoBitrateBps;
    }

    @NonNull
    @Override
    public String toString() {
        return "MediaQualityThreshold: {mRtpInactivityTimerMillis="
            + Arrays.toString(mRtpInactivityTimerMillis)
            + ", mRtcpInactivityTimerMillis=" + mRtcpInactivityTimerMillis
            + ", mRtpHysteresisTimeInMillis =" + mRtpHysteresisTimeInMillis
            + ", mRtpPacketLossDurationMillis=" + mRtpPacketLossDurationMillis
            + ", mRtpPacketLossRate=" + Arrays.toString(mRtpPacketLossRate)
            + ", mRtpJitterMillis=" + Arrays.toString(mRtpJitterMillis)
            + ", mNotifyCurrentStatus=" + mNotifyCurrentStatus
            + ", mVideoBitrateBps=" + mVideoBitrateBps
            + " }";
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(mRtpInactivityTimerMillis), mRtcpInactivityTimerMillis,
            mRtpHysteresisTimeInMillis, mRtpPacketLossDurationMillis,
            Arrays.hashCode(mRtpPacketLossRate), Arrays.hashCode(mRtpJitterMillis),
            mNotifyCurrentStatus, mVideoBitrateBps);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (o == null || !(o instanceof MediaQualityThreshold)) {
            return false;
        }

        if (this == o) {
            return true;
        }

        MediaQualityThreshold s = (MediaQualityThreshold) o;

        return (Arrays.equals(mRtpInactivityTimerMillis, s.mRtpInactivityTimerMillis)
            && mRtcpInactivityTimerMillis == s.mRtcpInactivityTimerMillis
            && mRtpHysteresisTimeInMillis == s.mRtpHysteresisTimeInMillis
            && mRtpPacketLossDurationMillis == s.mRtpPacketLossDurationMillis
            && Arrays.equals(mRtpPacketLossRate, s.mRtpPacketLossRate)
            && Arrays.equals(mRtpJitterMillis, s.mRtpJitterMillis)
            && mNotifyCurrentStatus == s.mNotifyCurrentStatus
            && mVideoBitrateBps == s.mVideoBitrateBps);
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
        dest.writeIntArray(mRtpInactivityTimerMillis);
        dest.writeInt(mRtcpInactivityTimerMillis);
        dest.writeInt(mRtpHysteresisTimeInMillis);
        dest.writeInt(mRtpPacketLossDurationMillis);
        dest.writeIntArray(mRtpPacketLossRate);
        dest.writeIntArray(mRtpJitterMillis);
        dest.writeBoolean(mNotifyCurrentStatus);
        dest.writeInt(mVideoBitrateBps);
    }

    public static final @NonNull Parcelable.Creator<MediaQualityThreshold>
        CREATOR = new Parcelable.Creator() {
        public MediaQualityThreshold createFromParcel(Parcel in) {
            // TODO use builder class so it will validate
            return new MediaQualityThreshold(in);
        }

        public MediaQualityThreshold[] newArray(int size) {
            return new MediaQualityThreshold[size];
        }
    };

    /**
     * Provides a convenient way to set the fields of a {@link MediaQualityThreshold}
     * when creating a new instance.
     */
    public static final class Builder {
        private int[] mRtpInactivityTimerMillis;
        private int mRtcpInactivityTimerMillis;
        private int mRtpHysteresisTimeInMillis;
        private int mRtpPacketLossDurationMillis;
        private int[] mRtpPacketLossRate;
        private int[] mRtpJitterMillis;
        private boolean mNotifyCurrentStatus;
        private int mVideoBitrateBps;

        /**
         * Default constructor for Builder.
         */
        public Builder() {
            mRtpInactivityTimerMillis = new int[0];
            mRtcpInactivityTimerMillis = 0;
            mRtpHysteresisTimeInMillis = 0;
            mRtpPacketLossDurationMillis = 0;
            mRtpPacketLossRate = new int[0];
            mRtpJitterMillis = new int[0];
            mVideoBitrateBps = 0;
        }

        /**
         * Set the timer in milliseconds for monitoring RTP inactivity
         *
         * @param timer The array of inacitivity timer values in milliseconds
         *
         * @return The same instance of the builder
         */
        public @NonNull Builder setRtpInactivityTimerMillis(@NonNull final int[] timer) {
            this.mRtpInactivityTimerMillis = Arrays.copyOf(timer, timer.length);
            return this;
        }

        /**
         * Set the timer in milliseconds for monitoring RTCP inactivity
         *
         * @param timer The timer value in milliseconds
         *
         * @return The same instance of the builder
         */
        public @NonNull Builder setRtcpInactivityTimerMillis(final int timer) {
            this.mRtcpInactivityTimerMillis = timer;
            return this;
        }

        /**
         * Set the threshold hysteresis time for packet loss and jitter. This has a goal to prevent
         * frequent ping-pong notification. So whenever a notifier needs to report the cross of
         * threshold in opposite direction, this hysteresis timer should be respected.
         *
         * @param time The hysteresis time in milliseconds
         *
         * @return The same instance of the builder
         */
        public @NonNull Builder setRtpHysteresisTimeInMillis(final int time) {
            this.mRtpHysteresisTimeInMillis = time;
            return this;
        }

        /**
         * Set the duration in milliseconds for monitoring the RTP packet loss rate
         *
         * @param duration The duration in milliseconds
         *
         * @return The same instance of the builder
         */
        public @NonNull Builder setRtpPacketLossDurationMillis(final int duration) {
            this.mRtpPacketLossDurationMillis = duration;
            return this;
        }

        /**
         * Set the RTP packet loss rate threshold in percentage
         *
         * Packet loss rate = (Number of packets lost / number of packets expected) * 100
         *
         * @param packetLossRate The array of packet loss rates
         *
         * @return The same instance of the builder
         */
        public @NonNull Builder setRtpPacketLossRate(@NonNull final int[] packetLossRate) {
            this.mRtpPacketLossRate = Arrays.copyOf(packetLossRate, packetLossRate.length);
            return this;
        }

        /**
         * Set the RTP jitter threshold in milliseconds
         *
         * @param jitter The array of jitter thresholds
         *
         * @return The same instance of the builder
         */
        public @NonNull Builder setRtpJitterMillis(@NonNull final int[] jitter) {
            this.mRtpJitterMillis = Arrays.copyOf(jitter, jitter.length);
            return this;
        }

        /**
         * A flag indicating whether the client needs to be notify the current media quality status
         * right after threshold is being set. True means the media stack should notify the client
         * of the current status.
         *
         * @param notify The boolean state if it requires the prompt notification of
         * MediaQualityStatus
         *
         * @return The same instance of the builder
         */
        public @NonNull Builder setNotifyCurrentStatus(final boolean notify) {
            this.mNotifyCurrentStatus = notify;
            return this;
        }

        /**
         * The receiving bitrate threshold in bps for video call. If it is not zero, bitrate
         * notification event is triggered when the receiving frame bitrate is less than the
         * threshold.
         */
        public @NonNull Builder setVideoBitrateBps(final int bitrate) {
            this.mVideoBitrateBps = bitrate;
            return this;
        }

        /**
         * Build the MediaQualityThreshold.
         *
         * @return the MediaQualityThreshold object.
         */
        public @NonNull MediaQualityThreshold build() {
            // TODO validation
            return new MediaQualityThreshold(this);
        }
    }
}

