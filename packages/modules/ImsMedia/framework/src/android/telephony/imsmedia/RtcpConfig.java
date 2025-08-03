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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * The class represents RTCP (Real Time Control Protocol) configurations.
 *
 * @hide
 */
public final class RtcpConfig implements Parcelable {
    /**
     * RTCP XR (extended report) types are not specified,
     * See RFC 3611 section 4
     */
    public static final int FLAG_RTCPXR_NONE = 0;
    /**
     * RTCP XR type Loss RLE Report Block as specified in
     * RFC 3611 section 4.1
     */
    public static final int FLAG_RTCPXR_LOSS_RLE_REPORT_BLOCK = 1 << 0;
    /**
     * RTCP XR type Duplicate RLE Report Block as specified in
     * RFC 3611 section 4.2
     */
    public static final int FLAG_RTCPXR_DUPLICATE_RLE_REPORT_BLOCK = 1 << 1;
    /**
     * RTCP XR type Packet Receipt Times Report Block as specified in
     * RFC 3611 section 4.3
     */
    public static final int FLAG_RTCPXR_PACKET_RECEIPT_TIMES_REPORT_BLOCK = 1 << 2;
    /**
     * RTCP XR type Receiver Reference Time Report Block as specified in
     * RFC 3611 section 4.4
     */
    public static final int FLAG_RTCPXR_RECEIVER_REFERENCE_TIME_REPORT_BLOCK = 1 << 3;
    /**
     * RTCP XR type DLRR Report Block as specified in
     * RFC 3611 section 4.5
     */
    public static final int FLAG_RTCPXR_DLRR_REPORT_BLOCK = 1 << 4;
    /**
     * RTCP XR type Statistics Summary Report Block as specified in
     * RFC 3611 section 4.6
     */
    public static final int FLAG_RTCPXR_STATISTICS_SUMMARY_REPORT_BLOCK = 1 << 5;
    /**
     * RTCP XR type VoIP Metrics Report Block as specified in
     * RFC 3611 section 4.7
     */
    public static final int FLAG_RTCPXR_VOIP_METRICS_REPORT_BLOCK = 1 << 6;

    /** Canonical name that will be sent to all session participants */
    private final String canonicalName;

    /** UDP port number for sending outgoing RTCP packets */
    private final int transmitPort;

    /**
     * RTCP transmit interval in seconds. The value 0 indicates that RTCP
     * reports shall not be sent to the other party.
     */
    private final int intervalSec;

    /** @hide */
    @IntDef(flag = true, prefix = { "FLAG_" }, value = {
        FLAG_RTCPXR_NONE,
        FLAG_RTCPXR_LOSS_RLE_REPORT_BLOCK,
        FLAG_RTCPXR_DUPLICATE_RLE_REPORT_BLOCK,
        FLAG_RTCPXR_PACKET_RECEIPT_TIMES_REPORT_BLOCK,
        FLAG_RTCPXR_RECEIVER_REFERENCE_TIME_REPORT_BLOCK,
        FLAG_RTCPXR_DLRR_REPORT_BLOCK,
        FLAG_RTCPXR_STATISTICS_SUMMARY_REPORT_BLOCK,
        FLAG_RTCPXR_VOIP_METRICS_REPORT_BLOCK,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface RtcpXrBlockType {}

    /** Bitmask of RTCP-XR blocks to be enabled */
    private final @RtcpXrBlockType int rtcpXrBlockTypes;

    /** @hide **/
    private RtcpConfig(Parcel in) {
        canonicalName = in.readString();
        transmitPort = in.readInt();
        intervalSec = in.readInt();
        rtcpXrBlockTypes = in.readInt();
    }

    /** @hide **/
    private RtcpConfig(final String canonicalName, final int transmitPort, final int intervalSec,
            final @RtcpXrBlockType int rtcpXrBlockTypes) {
        this.canonicalName = canonicalName;
        this.transmitPort = transmitPort;
        this.intervalSec = intervalSec;
        this.rtcpXrBlockTypes = rtcpXrBlockTypes;
    }

    /** @hide **/
    public String getCanonicalName() {
        return canonicalName;
    }

    /** @hide **/
    public int getTransmitPort() {
        return transmitPort;
    }

    /** @hide **/
    public int getIntervalSec() {
        return intervalSec;
    }

    /** @hide **/
    public @RtcpXrBlockType int getRtcpXrBlockTypes() {
        return rtcpXrBlockTypes;
    }

    @NonNull
    @Override
    public String toString() {
        return "RtcpConfig: {canonicalName=" + canonicalName
                + ", transmitPort=" + transmitPort
                + ", intervalSec=" + intervalSec
                + ", rtcpXrBlockTypes=" + rtcpXrBlockTypes
                + " }";
    }

    @Override
    public int hashCode() {
        return Objects.hash(canonicalName, transmitPort, intervalSec, rtcpXrBlockTypes);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (o == null || !(o instanceof RtcpConfig) || hashCode() != o.hashCode()) {
            return false;
        }

        if (this == o) {
            return true;
        }

        RtcpConfig s = (RtcpConfig) o;

        return (Objects.equals(canonicalName, s.canonicalName)
                && transmitPort == s.transmitPort
                && intervalSec == s.intervalSec
                && rtcpXrBlockTypes == s.rtcpXrBlockTypes);
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
        dest.writeString(canonicalName);
        dest.writeInt(transmitPort);
        dest.writeInt(intervalSec);
        dest.writeInt(rtcpXrBlockTypes);
    }

    public static final @NonNull Parcelable.Creator<RtcpConfig>
        CREATOR = new Parcelable.Creator() {
        public RtcpConfig createFromParcel(Parcel in) {
            // TODO use builder class so it will validate
            return new RtcpConfig(in);
        }

        public RtcpConfig[] newArray(int size) {
            return new RtcpConfig[size];
        }
    };

    /**
     * Provides a convenient way to set the fields of a {@link RtcpConfig}
     * when creating a new instance.
     */
    public static final class Builder {
        private String canonicalName;
        private int transmitPort;
        private int intervalSec;
        private @RtcpXrBlockType int rtcpXrBlockTypes;

        /**
         * Default constructor for Builder.
         */
        public Builder() {
        }

        /**
         * Set the canonical name which will be sent to all session participants.
         *
         * @param canonicalName The canonical name.
         * @return The same instance of the builder.
         */
        public @NonNull Builder setCanonicalName(final String canonicalName) {
            this.canonicalName = canonicalName;
            return this;
        }

        /**
         * Set the UDP port number for sending outgoing RTCP packets.
         *
         * @param transmitPort The UDP port number for outgoing RTCP packets.
         * @return The same instance of the builder.
         */
        public @NonNull Builder setTransmitPort(final int transmitPort) {
            this.transmitPort = transmitPort;
            return this;
        }

        /**
         * Set the RTCP transmit interval in seconds. The value 0 indicates that RTCP
         * reports shall not be sent to the other party.
         *
         * @param intervalSec RTCP transmit interval in seconds.
         * @return The same instance of the builder.
         */
        public @NonNull Builder setIntervalSec(final int intervalSec) {
            this.intervalSec = intervalSec;
            return this;
        }

        /**
         * Set the bitmask of RTCP-XR blocks to be enabled, See RFC 3611 section 4.
         *
         * @param rtcpXrBlockTypes RTCP-XR blocks to be enabled.
         * @return The same instance of the builder.
         */
        public @NonNull Builder setRtcpXrBlockTypes(final @RtcpXrBlockType int rtcpXrBlockTypes) {
            this.rtcpXrBlockTypes = rtcpXrBlockTypes;
            return this;
        }

        /**
         * Build the RtcpConfig.
         *
         * @return the RtcpConfig object.
         */
        public @NonNull RtcpConfig build() {
            // TODO validation
            return new RtcpConfig(canonicalName, transmitPort, intervalSec, rtcpXrBlockTypes);
        }
    }
}

