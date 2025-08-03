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
 * The class represents RTP (Real Time Control) configuration for audio stream.
 *
 * @hide
 */
public final class AudioConfig extends RtpConfig {
    /** Adaptive Multi-Rate */
    public static final int CODEC_AMR = android.hardware.radio.ims.media.CodecType.AMR;
    /** Adaptive Multi-Rate Wide Band */
    public static final int CODEC_AMR_WB = android.hardware.radio.ims.media.CodecType.AMR_WB;
    /** Enhanced Voice Services */
    public static final int CODEC_EVS = android.hardware.radio.ims.media.CodecType.EVS;
    /** G.711 A-law i.e. Pulse Code Modulation using A-law */
    public static final int CODEC_PCMA = android.hardware.radio.ims.media.CodecType.PCMA;
    /** G.711 μ-law i.e. Pulse Code Modulation using μ-law */
    public static final int CODEC_PCMU = android.hardware.radio.ims.media.CodecType.PCMU;

    /** @hide */
    @IntDef(
        flag = true,
        value = {
           CODEC_AMR,
           CODEC_AMR_WB,
           CODEC_EVS,
           CODEC_PCMA,
           CODEC_PCMU,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface CodecType {}

    private byte pTimeMillis;
    private int maxPtimeMillis;
    private boolean dtxEnabled;
    private @CodecType int codecType;
    private byte mDtmfTxPayloadTypeNumber;
    private byte mDtmfRxPayloadTypeNumber;
    private byte dtmfSamplingRateKHz;
    @Nullable
    private AmrParams amrParams;
    @Nullable
    private EvsParams evsParams;

    /** @hide */
    AudioConfig(Parcel in) {
        super(RtpConfig.TYPE_AUDIO, in);
        pTimeMillis = in.readByte();
        maxPtimeMillis = in.readInt();
        dtxEnabled = in.readBoolean();
        codecType = in.readInt();
        mDtmfTxPayloadTypeNumber = in.readByte();
        mDtmfRxPayloadTypeNumber = in.readByte();
        dtmfSamplingRateKHz = in.readByte();
        amrParams = in.readParcelable(AmrParams.class.getClassLoader(), AmrParams.class);
        evsParams = in.readParcelable(EvsParams.class.getClassLoader(), EvsParams.class);
    }

    /** @hide */
    AudioConfig(Builder builder) {
        super(RtpConfig.TYPE_AUDIO, builder);
        this.pTimeMillis = builder.pTimeMillis;
        this.maxPtimeMillis = builder.maxPtimeMillis;
        this.dtxEnabled = builder.dtxEnabled;
        this.codecType = builder.codecType;
        this.mDtmfTxPayloadTypeNumber = builder.mDtmfTxPayloadTypeNumber;
        this.mDtmfRxPayloadTypeNumber = builder.mDtmfRxPayloadTypeNumber;
        this.dtmfSamplingRateKHz = builder.dtmfSamplingRateKHz;
        this.amrParams = builder.amrParams;
        this.evsParams = builder.evsParams;
    }

    /** @hide **/
    public byte getPtimeMillis() {
        return pTimeMillis;
    }

    /** @hide **/
    public void setPtimeMillis(byte pTimeMillis) {
        this.pTimeMillis = pTimeMillis;
    }

    /** @hide **/
    public int getMaxPtimeMillis() {
        return maxPtimeMillis;
    }

    /** @hide **/
    public void setMaxPtimeMillis(int maxPtimeMillis) {
        this.maxPtimeMillis = maxPtimeMillis;
    }

    /** @hide **/
    public boolean getDtxEnabled() {
        return dtxEnabled;
    }

    /** @hide **/
    public void setDtxEnabled(boolean dtxEnabled) {
        this.dtxEnabled = dtxEnabled;
    }

    /** @hide **/
    public int getCodecType() {
        return codecType;
    }

    /** @hide **/
    public void setCodecType(int codecType) {
        this.codecType = codecType;
    }

    /** @hide **/
    public byte getTxDtmfPayloadTypeNumber() {
        return mDtmfTxPayloadTypeNumber;
    }

    /** @hide **/
    public byte getRxDtmfPayloadTypeNumber() {
        return mDtmfRxPayloadTypeNumber;
    }

    /** @hide **/
    public void setTxDtmfPayloadTypeNumber(byte dtmfTxPayloadTypeNumber) {
        this.mDtmfTxPayloadTypeNumber = dtmfTxPayloadTypeNumber;
    }

    /** @hide **/
    public void setRxDtmfPayloadTypeNumber(byte dtmfRxPayloadTypeNumber) {
        this.mDtmfRxPayloadTypeNumber = dtmfRxPayloadTypeNumber;
    }

    /** @hide **/
    public byte getDtmfSamplingRateKHz() {
        return dtmfSamplingRateKHz;
    }

    /** @hide **/
    public void setDtmfSamplingRateKHz(byte dtmfSamplingRateKHz) {
        this.dtmfSamplingRateKHz = dtmfSamplingRateKHz;
    }

    /** @hide **/
    public AmrParams getAmrParams() {
        return amrParams;
    }

    /** @hide **/
    public EvsParams getEvsParams() {
        return evsParams;
    }

    @NonNull
    @Override
    public String toString() {
        return super.toString() + " AudioConfig: {pTimeMillis=" + pTimeMillis
                + ", maxPtimeMillis=" + maxPtimeMillis
                + ", dtxEnabled=" + dtxEnabled
                + ", codecType=" + codecType
                + ", mDtmfTxPayloadTypeNumber=" + mDtmfTxPayloadTypeNumber
                + ", mDtmfRxPayloadTypeNumber=" + mDtmfRxPayloadTypeNumber
                + ", dtmfSamplingRateKHz=" + dtmfSamplingRateKHz
                + ", amrParams=" + amrParams
                + ", evsParams=" + evsParams
                + " }";
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), pTimeMillis, maxPtimeMillis,
                dtxEnabled, codecType, mDtmfTxPayloadTypeNumber,
                mDtmfRxPayloadTypeNumber, dtmfSamplingRateKHz, amrParams, evsParams);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (o == null || !(o instanceof AudioConfig) || hashCode() != o.hashCode()) {
            return false;
        }

        if (this == o) {
            return true;
        }

        AudioConfig s = (AudioConfig) o;

        if (!super.equals(s)) {
            return false;
        }

        return (pTimeMillis == s.pTimeMillis
                && maxPtimeMillis == s.maxPtimeMillis
                && dtxEnabled == s.dtxEnabled
                && codecType == s.codecType
                && mDtmfTxPayloadTypeNumber == s.mDtmfTxPayloadTypeNumber
                && mDtmfRxPayloadTypeNumber == s.mDtmfRxPayloadTypeNumber
                && dtmfSamplingRateKHz == s.dtmfSamplingRateKHz
                && Objects.equals(amrParams, s.amrParams)
                && Objects.equals(evsParams, s.evsParams));
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
        super.writeToParcel(dest, RtpConfig.TYPE_AUDIO);
        dest.writeByte(pTimeMillis);
        dest.writeInt(maxPtimeMillis);
        dest.writeBoolean(dtxEnabled);
        dest.writeInt(codecType);
        dest.writeByte(mDtmfTxPayloadTypeNumber);
        dest.writeByte(mDtmfRxPayloadTypeNumber);
        dest.writeByte(dtmfSamplingRateKHz);
        dest.writeParcelable(amrParams, 0);
        dest.writeParcelable(evsParams, 0);
    }

    public static final @NonNull Parcelable.Creator<AudioConfig>
            CREATOR = new Parcelable.Creator() {
                public AudioConfig createFromParcel(Parcel in) {
                    in.readInt();   //skip
                    return new AudioConfig(in);
                }

                public AudioConfig[] newArray(int size) {
                    return new AudioConfig[size];
                }
            };

    /**
     * Provides a convenient way to set the fields of a {@link AudioConfig}
     * when creating a new instance.
     */
    public static final class Builder extends RtpConfig.AbstractBuilder<Builder> {
        private byte pTimeMillis;
        private int maxPtimeMillis;
        private boolean dtxEnabled;
        private @CodecType int codecType;
        private byte mDtmfTxPayloadTypeNumber;
        private byte mDtmfRxPayloadTypeNumber;
        private byte dtmfSamplingRateKHz;
        @Nullable
        private AmrParams amrParams;
        @Nullable
        private EvsParams evsParams;

        /**
         * Default constructor for Builder.
         */
        public Builder() {
        }

        @Override
        Builder self() {
            return this;
        }

        /**
         * Set the packet time i.e. recommended length of time in milliseconds
         * represented by the media in each packet, see RFC 4566
         *
         * @param pTimeMillis packet time
         * @return The same instance of the builder
         */
        public Builder setPtimeMillis(final byte pTimeMillis) {
            this.pTimeMillis = pTimeMillis;
            return this;
        }

        /**
         * Set the maximum amount of media that can be encapsulated in each packet
         * represented in milliseconds, see RFC 4566
         *
         * @param maxPtimeMillis maximum packet time
         * @return The same instance of the builder
         */
        public Builder setMaxPtimeMillis(final int maxPtimeMillis) {
            this.maxPtimeMillis = maxPtimeMillis;
            return this;
        }

        /**
         * Set whether discontinuous transmission (DTX) is enabled or not
         *
         * @param dtxEnabled {@code true} if DTX enabled
         * @return The same instance of the builder
         */
        public Builder setDtxEnabled(final boolean dtxEnabled) {
            this.dtxEnabled = dtxEnabled;
            return this;
        }

        /**
         * Set the negotiated audio codec
         *
         * @param codecType codec type
         * @return The same instance of the builder
         */
        public Builder setCodecType(final @CodecType int codecType) {
            this.codecType = codecType;
            return this;
        }

        /**
         * Set the dynamic Tx payload type number to be used to transmit DTMF RTP packets.
         * The values is in the range from 96 to 127 chosen during the session establishment.
         * The PT value of the RTP header of all DTMF packets shall be set with this value.
         *
         * @param dtmfTxPayloadTypeNumber Payload type number for the Tx DTMF packets
         * @return The same instance of the builder
         */
        public Builder setTxDtmfPayloadTypeNumber(final byte dtmfTxPayloadTypeNumber) {
            this.mDtmfTxPayloadTypeNumber = dtmfTxPayloadTypeNumber;
            return this;
        }

        /**
         * Set the dynamic Rx payload type number to be used for DTMF received RTP packets.
         * The values is in the range from 96 to 127 chosen during the session establishment.
         * The PT value of the RTP header of all DTMF packets shall be set with this value.
         *
         * @param dtmfRxPayloadTypeNumber Payload type number for the Rx DTMF packets
         * @return The same instance of the builder
         */
        public Builder setRxDtmfPayloadTypeNumber(final byte dtmfRxPayloadTypeNumber) {
            this.mDtmfRxPayloadTypeNumber = dtmfRxPayloadTypeNumber;
            return this;
        }

        /**
         * Set the DTMF sampling rate on this media stream
         *
         * @param dtmfSamplingRateKHz DTMF sampling rate
         * @return The same instance of the builder
         */
        public Builder setDtmfSamplingRateKHz(final byte dtmfSamplingRateKHz) {
            this.dtmfSamplingRateKHz = dtmfSamplingRateKHz;
            return this;
        }

        /**
         * Set the AMR codec parameters, see {@link AmrParams}
         *
         * @param amrParams AMR parameters
         * @return The same instance of the builder
         */
        public Builder setAmrParams(final AmrParams amrParams) {
            this.amrParams = amrParams;
            return this;
        }

        /**
         * Set the EVS codec parameters, see {@link EvsParams}
         *
         * @param evsParams EVS parameters.
         * @return The same instance of the builder
         */
        public Builder setEvsParams(final EvsParams evsParams) {
            this.evsParams = evsParams;
            return this;
        }

        /**
         * Build the AudioConfig.
         *
         * @return the AudioConfig object.
         */
        public @NonNull AudioConfig build() {
            // TODO validation
            return new AudioConfig(this);
        }
    }
}

