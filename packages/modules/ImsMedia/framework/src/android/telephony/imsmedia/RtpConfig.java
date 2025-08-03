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
import android.net.InetAddresses;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * Class to encapsulate RTP (Real Time Protocol) configurations
 *
 * @hide
 */
public abstract class RtpConfig implements Parcelable {
    public static final int TYPE_AUDIO = 0;
    public static final int TYPE_VIDEO = 1;
    public static final int TYPE_TEXT = 2;

    /** Device neither transmits nor receives any RTP */
    public static final int MEDIA_DIRECTION_NO_FLOW = 0;
    /**
     * Device transmits outgoing RTP but but doesn't receive incoming RTP.
     * Eg. Other party muted the call
     */
    public static final int MEDIA_DIRECTION_SEND_ONLY = 1;
    /**
     * Device receives the incoming RTP but doesn't transmit any outgoing RTP.
     * Eg. User muted the call
     */
    public static final int MEDIA_DIRECTION_RECEIVE_ONLY = 2;
    /** Device transmits and receives RTP in both the Directions */
    public static final int MEDIA_DIRECTION_SEND_RECEIVE = 3;
    /** No RTP flow however RTCP continues to flow. Eg. HOLD */
    public static final int MEDIA_DIRECTION_INACTIVE = 4;
    /* definition of uninitialized port number*/
    public static final int UNINITIALIZED_PORT = -1;

    /** @hide */
    @IntDef(
        value = {
           MEDIA_DIRECTION_NO_FLOW,
           MEDIA_DIRECTION_SEND_ONLY,
           MEDIA_DIRECTION_RECEIVE_ONLY,
           MEDIA_DIRECTION_SEND_RECEIVE,
           MEDIA_DIRECTION_INACTIVE,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface MediaDirection {}

    private final int mType;
    private @MediaDirection int mDirection;
    private int mAccessNetwork;
    @Nullable
    private InetSocketAddress mRemoteRtpAddress;
    @Nullable
    private RtcpConfig mRtcpConfig;
    private byte mDscp;
    private byte mRxPayloadTypeNumber;
    private byte mTxPayloadTypeNumber;
    private byte mSamplingRateKHz;
    /** Holds RTP parameters required to maintain RTP stream continuity */
    @Nullable
    private RtpContextParams mRtpContextParams;
    @Nullable
    private AnbrMode mAnbrMode;

    /** @hide */
    RtpConfig(int type, Parcel in) {
        mType = type;
        mDirection = in.readInt();
        mAccessNetwork = in.readInt();
        mRemoteRtpAddress = readSocketAddress(in);
        mRtcpConfig = in.readParcelable(RtcpConfig.class.getClassLoader(), RtcpConfig.class);
        mDscp = in.readByte();
        mRxPayloadTypeNumber = in.readByte();
        mTxPayloadTypeNumber = in.readByte();
        mSamplingRateKHz = in.readByte();
        mRtpContextParams = in.readParcelable(RtpContextParams.class.getClassLoader(),
                RtpContextParams.class);
        mAnbrMode = in.readParcelable(AnbrMode.class.getClassLoader(), AnbrMode.class);
    }

    /** @hide **/
    RtpConfig(int type, AbstractBuilder builder) {
        mType = type;
        mDirection = builder.mDirection;
        mAccessNetwork = builder.mAccessNetwork;
        mRemoteRtpAddress = builder.mRemoteRtpAddress;
        mRtcpConfig = builder.mRtcpConfig;
        mDscp = builder.mDscp;
        mRxPayloadTypeNumber = builder.mRxPayloadTypeNumber;
        mTxPayloadTypeNumber = builder.mTxPayloadTypeNumber;
        mSamplingRateKHz = builder.mSamplingRateKHz;
        mRtpContextParams = builder.mRtpContextParams;
        mAnbrMode = builder.mAnbrMode;
    }

    private @NonNull InetSocketAddress readSocketAddress(final Parcel in) {
        final String address = in.readString();
        final int port = in.readInt();
        if(address != null && port != UNINITIALIZED_PORT) {
            return new InetSocketAddress(
                InetAddresses.parseNumericAddress(address), port);
        }
        return null;
    }

    public int getMediaType() {
        return mType;
    }

    public int getMediaDirection() {
        return mDirection;
    }

    public void setMediaDirection(final @MediaDirection int mDirection) {
        this.mDirection = mDirection;
    }

    public int getAccessNetwork() {
        return mAccessNetwork;
    }

    public void setAccessNetwork(final int mAccessNetwork) {
        this.mAccessNetwork = mAccessNetwork;
    }

    public InetSocketAddress getRemoteRtpAddress() {
        return mRemoteRtpAddress;
    }

    public void setRemoteRtpAddress(final InetSocketAddress mRemoteRtpAddress) {
        this.mRemoteRtpAddress = mRemoteRtpAddress;
    }

    public RtcpConfig getRtcpConfig() {
        return mRtcpConfig;
    }

    public void setRtcpConfig(final RtcpConfig mRtcpConfig) {
        this.mRtcpConfig = mRtcpConfig;
    }

    public byte getDscp() {
        return mDscp;
    }

    public void setDscp(final byte mDscp) {
        this.mDscp = mDscp;
    }

    public byte getRxPayloadTypeNumber() {
        return mRxPayloadTypeNumber;
    }

    public void setRxPayloadTypeNumber(final byte mRxPayloadTypeNumber) {
        this.mRxPayloadTypeNumber = mRxPayloadTypeNumber;
    }

    public byte getTxPayloadTypeNumber() {
        return mTxPayloadTypeNumber;
    }

    public void setTxPayloadTypeNumber(final byte mTxPayloadTypeNumber) {
        this.mTxPayloadTypeNumber = mTxPayloadTypeNumber;
    }

    public byte getSamplingRateKHz() {
        return mSamplingRateKHz;
    }

    public void setSamplingRateKHz(final byte mSamplingRateKHz) {
        this.mSamplingRateKHz = mSamplingRateKHz;
    }

    public RtpContextParams getRtpContextParams() {
        return mRtpContextParams;
    }

    public void setRtpContextParams(final RtpContextParams mRtpContextParams) {
        this.mRtpContextParams = mRtpContextParams;
    }

    public AnbrMode getAnbrMode() {
        return mAnbrMode;
    }

    public void setAnbrMode(final AnbrMode mAnbrMode) {
        this.mAnbrMode = mAnbrMode;
    }

    @NonNull
    @Override
    public String toString() {
        return "RtpConfig: {mDirection=" + mDirection
            + ", mAccessNetwork=" + mAccessNetwork
            + ", mRemoteRtpAddress=" + mRemoteRtpAddress
            + ", mRtcpConfig=" + mRtcpConfig
            + ", mDscp=" + mDscp
            + ", mRxPayloadTypeNumber=" + mRxPayloadTypeNumber
            + ", mTxPayloadTypeNumber=" + mTxPayloadTypeNumber
            + ", mSamplingRateKHz=" + mSamplingRateKHz
            + ", mRtpContextParams=" + mRtpContextParams
            + ", mAnbrMode=" + mAnbrMode
            + " }";
    }

    @Override
    public int hashCode() {
        return Objects.hash(mDirection, mAccessNetwork, mRemoteRtpAddress, mRtcpConfig,
            mDscp, mRxPayloadTypeNumber, mTxPayloadTypeNumber, mSamplingRateKHz, mRtpContextParams,
            mAnbrMode);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (o == null || !(o instanceof RtpConfig) || hashCode() != o.hashCode()) {
            return false;
        }

        if (this == o) {
            return true;
        }

        RtpConfig s = (RtpConfig) o;

        return (mDirection == s.mDirection
                && mAccessNetwork == s.mAccessNetwork
                && Objects.equals(mRemoteRtpAddress, s.mRemoteRtpAddress)
                && Objects.equals(mRtcpConfig, s.mRtcpConfig)
                && mDscp == s.mDscp
                && mRxPayloadTypeNumber == s.mRxPayloadTypeNumber
                && mTxPayloadTypeNumber == s.mTxPayloadTypeNumber
                && mSamplingRateKHz == s.mSamplingRateKHz
                && Objects.equals(mRtpContextParams, s.mRtpContextParams)
                && Objects.equals(mAnbrMode, s.mAnbrMode));
    }

    /**
     * {@link Parcelable#describeContents}
     */
    public int describeContents() {
        return 0;
    }

    /**
     * Used by child classes for parceling.
     *
     * @hide
     */
    @CallSuper
    public void writeToParcel(Parcel dest, int type) {
        dest.writeInt(type);
        dest.writeInt(mDirection);
        dest.writeInt(mAccessNetwork);
        if (mRemoteRtpAddress == null) {
            dest.writeString(null);
            dest.writeInt(UNINITIALIZED_PORT);
        } else {
            dest.writeString(mRemoteRtpAddress.getAddress().getHostAddress());
            dest.writeInt(mRemoteRtpAddress.getPort());
        }
        dest.writeParcelable(mRtcpConfig, 0);
        dest.writeByte(mDscp);
        dest.writeByte(mRxPayloadTypeNumber);
        dest.writeByte(mTxPayloadTypeNumber);
        dest.writeByte(mSamplingRateKHz);
        dest.writeParcelable(mRtpContextParams, 0);
        dest.writeParcelable(mAnbrMode, 0);
    }

    public static final @NonNull Parcelable.Creator<RtpConfig>
            CREATOR = new Parcelable.Creator() {
                public RtpConfig createFromParcel(Parcel in) {
                    int type = in.readInt();
                    switch (type) {
                        case TYPE_AUDIO:
                            return new AudioConfig(in);
                        case TYPE_VIDEO:
                            return new VideoConfig(in);
                        case TYPE_TEXT:
                            return new TextConfig(in);
                        default:
                            throw new IllegalArgumentException("Bad Type Parcel");
                    }
                }

                public RtpConfig[] newArray(int size) {
                    return new RtpConfig[size];
                }
            };

    /**
     * Provides a convenient way to set the fields of a {@link RtpConfig}
     * when creating a new instance.
     */
    public static abstract class AbstractBuilder<T extends AbstractBuilder<T>> {
        private @MediaDirection int mDirection;
        private int mAccessNetwork;
        @Nullable
        private InetSocketAddress mRemoteRtpAddress;
        @Nullable
        private RtcpConfig mRtcpConfig;
        private byte mDscp;
        private byte mRxPayloadTypeNumber;
        private byte mTxPayloadTypeNumber;
        private byte mSamplingRateKHz;
        @Nullable
        private RtpContextParams mRtpContextParams;
        private AnbrMode mAnbrMode;

        AbstractBuilder() {}

        /** Returns {@code this} */
        abstract T self();

        /**
         * Sets media flow direction of {@link MediaDirection}
         * @param direction direction of media.
         */
        public T setMediaDirection(final @MediaDirection int direction) {
            this.mDirection = direction;
            return self();
        }

        /**
         * Sets radio access metwork type
         * @param accessNetwork network type
         */
        public T setAccessNetwork(final int accessNetwork) {
            this.mAccessNetwork = accessNetwork;
            return self();
        }

        /**
         * Sets Ip address and port number of the other party for RTP media.
         * @param remoteRtpAddress ip address and port form of InetSocketAddress
         */
        public T setRemoteRtpAddress(final InetSocketAddress remoteRtpAddress) {
            this.mRemoteRtpAddress = remoteRtpAddress;
            return self();
        }

        /**
         * Sets rtcp configuration
         * @param rtcpConfig configuration fields of a {@link RtcpConfig}
         */
        public T setRtcpConfig(final RtcpConfig rtcpConfig) {
            this.mRtcpConfig = rtcpConfig;
            return self();
        }

        /**
         * Sets a dscp: Differentiated Services Field Code Point value, see RFC 2474
         * @param dscp dscp value
         */
        public T setDscp(final byte dscp) {
            this.mDscp = dscp;
            return self();
        }

        /**
         * Sets static or dynamic payload type number negotiated through the SDP for
         * the incoming RTP packets. This value shall be matched with the PT value
         * of the incoming RTP header. Values 0 to 127, see RFC 3551 section 6.
         * @param rxPayloadTypeNumber payload type number.
         */
        public T setRxPayloadTypeNumber(final byte rxPayloadTypeNumber) {
            this.mRxPayloadTypeNumber = rxPayloadTypeNumber;
            return self();
        }

        /**
         * Sets static or dynamic payload type number negotiated through the SDP for
         * the outgoing RTP packets. This value shall be set to the PT value
         * of the outgoing RTP header. Values 0 to 127, see RFC 3551 section 6.
         * @param txPayloadTypeNumber payload type number.
         */
        public T setTxPayloadTypeNumber(final byte txPayloadTypeNumber) {
            this.mTxPayloadTypeNumber = txPayloadTypeNumber;
            return self();
        }

        /**
         * Sets media source sampling rate in kHz.
         * @param samplingRateKHz sampling rate.
         */
        public T setSamplingRateKHz(final byte samplingRateKHz) {
            this.mSamplingRateKHz = samplingRateKHz;
            return self();
        }

        /**
         * Sets RTP parameters required for RTP context transfer
         * @param rtpContextParams parameter fields of a {@link RtpContextParams}
         */
        public T setRtpContextParams(final RtpContextParams rtpContextParams) {
            this.mRtpContextParams = rtpContextParams;
            return self();
        }

        /**
         * Sets AnbrMode information
         * @param anbrMode The codec mode of the current negotiated codec (either Evs or Amr)
         */
        public T setAnbrMode(final AnbrMode anbrMode) {
            this.mAnbrMode = anbrMode;
            return self();
        }
    }
}
