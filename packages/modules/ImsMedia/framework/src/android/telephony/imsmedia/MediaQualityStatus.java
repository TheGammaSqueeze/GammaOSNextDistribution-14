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
 * Class to set the media quality status for notifications
 *
 * @hide
 */

public final class MediaQualityStatus implements Parcelable {
    private final int mRtpInactivityTimeMillis;
    private final int mRtcpInactivityTimeMillis;
    private final int mRtpPacketLossRate;
    private final int mRtpJitterMillis;

    /** @hide **/
    public MediaQualityStatus(Parcel in) {
        mRtpInactivityTimeMillis = in.readInt();
        mRtcpInactivityTimeMillis = in.readInt();
        mRtpPacketLossRate = in.readInt();
        mRtpJitterMillis = in.readInt();
    }

    /** @hide **/
    public MediaQualityStatus(Builder builder) {
        mRtpInactivityTimeMillis = builder.mRtpInactivityTimeMillis;
        mRtcpInactivityTimeMillis = builder.mRtcpInactivityTimeMillis;
        mRtpPacketLossRate = builder.mRtpPacketLossRate;
        mRtpJitterMillis = builder.mRtpJitterMillis;
    }

    /** @hide **/
    public int getRtpInactivityTimeMillis() {
        return mRtpInactivityTimeMillis;
    }

    /** @hide **/
    public int getRtcpInactivityTimeMillis() {
        return mRtcpInactivityTimeMillis;
    }

    /** @hide **/
    public int getRtpPacketLossRate() {
        return mRtpPacketLossRate;
    }

    /** @hide **/
    public int getRtpJitterMillis() {
        return mRtpJitterMillis;
    }

    @NonNull
    @Override
    public String toString() {
        return "MediaQualityStatus: {mRtpInactivityTimeMillis=" + mRtpInactivityTimeMillis
                + ", mRtcpInactivityTimeMillis=" + mRtcpInactivityTimeMillis
                + ", mRtpPacketLossRate=" + mRtpPacketLossRate
                + ", mRtpJitterMillis=" + mRtpJitterMillis
                + " }";
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mRtpInactivityTimeMillis, mRtcpInactivityTimeMillis, mRtpPacketLossRate,
                mRtpJitterMillis);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (o == null || !(o instanceof MediaQualityStatus) || hashCode() != o.hashCode()) {
            return false;
        }

        if (this == o) {
            return true;
        }

        MediaQualityStatus s = (MediaQualityStatus) o;

        return (mRtpInactivityTimeMillis == s.mRtpInactivityTimeMillis
                && mRtcpInactivityTimeMillis == s.mRtcpInactivityTimeMillis
                && mRtpPacketLossRate == s.mRtpPacketLossRate
                && mRtpJitterMillis == s.mRtpJitterMillis);
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
        dest.writeInt(mRtpInactivityTimeMillis);
        dest.writeInt(mRtcpInactivityTimeMillis);
        dest.writeInt(mRtpPacketLossRate);
        dest.writeInt(mRtpJitterMillis);
    }

    public static final @NonNull Parcelable.Creator<MediaQualityStatus>
                CREATOR = new Parcelable.Creator() {
                    public MediaQualityStatus createFromParcel(Parcel in) {
                            // TODO use builder class so it will validate
                            return new MediaQualityStatus(in);
                    }

                    public MediaQualityStatus[] newArray(int size) {
                            return new MediaQualityStatus[size];
                    }
                };

    /**
     * Provides a convenient way to set the fields of a {@link MediaQualityStatus}
     * when creating a new instance.
     */
    public static final class Builder {
        private int mRtpInactivityTimeMillis;
        private int mRtcpInactivityTimeMillis;
        private int mRtpPacketLossRate;
        private int mRtpJitterMillis;

        /**
         * Set the rtp inactivity observed as per thresholds set by the MediaQualityThreshold API
         * @param value The receiving rtp inacitivity time observed in milliseconds unit
         */
        public @NonNull Builder setRtpInactivityTimeMillis(int value) {
            this.mRtpInactivityTimeMillis = value;
            return this;
        }

        /**
         * Set the rtcp inactivity observed as per thresholds set by the MediaQualityThreshold API
         * @param value The receiving rtcp inacitivity time observed in milliseconds unit
         */
        public @NonNull Builder setRtcpInactivityTimeMillis(int value) {
            this.mRtcpInactivityTimeMillis = value;
            return this;
        }

        /**
         * Set the rtp packet loss rate observed as per thresholds set by the MediaQualityThreshold
         * API
         * @param value The receiving rtp packet loss rate calculated in percentage unit
         */
        public @NonNull Builder setRtpPacketLossRate(int value) {
            this.mRtpPacketLossRate = value;
            return this;
        }

        /**
         * Set the rtp jitter observed as per thresholds set by MediaQualityThreshold API
         * @param value The receiving rtp jitter calculated in milliseconds unit
         */
        public @NonNull Builder setRtpJitterMillis(int value) {
            this.mRtpJitterMillis = value;
            return this;
        }

        /**
         * Build the MediaQualityStatus.
         *
         * @return the MediaQualityStatus object.
         */
        public @NonNull MediaQualityStatus build() {
            // TODO validation
            return new MediaQualityStatus(this);
        }
    }
}
