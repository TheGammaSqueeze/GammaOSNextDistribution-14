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

#ifndef AUDIOCONFIG_H
#define AUDIOCONFIG_H

#include <binder/Parcel.h>
#include <binder/Parcelable.h>
#include <binder/Status.h>
#include <RtpConfig.h>
#include <EvsParams.h>
#include <AmrParams.h>

namespace android
{

namespace telephony
{

namespace imsmedia
{

/** Native representation of android.telephony.imsmedia.AudioConfig */

/**
 * The class represents RTP (Real Time Control) configuration for audio stream.
 */
class AudioConfig : public RtpConfig
{
public:
    enum CodecType
    {
        /** Adaptive Multi-Rate */
        CODEC_AMR = 1 << 0,
        /** Adaptive Multi-Rate Wide Band */
        CODEC_AMR_WB = 1 << 1,
        /** Enhanced Voice Services */
        CODEC_EVS = 1 << 2,
        /** G.711 A-law i.e. Pulse Code Modulation using A-law */
        CODEC_PCMA = 1 << 3,
        /** G.711 μ-law i.e. Pulse Code Modulation using μ-law */
        CODEC_PCMU = 1 << 4,
    };

    AudioConfig();
    AudioConfig(AudioConfig* config);
    AudioConfig(const AudioConfig& config);
    virtual ~AudioConfig();
    AudioConfig& operator=(const AudioConfig& config);
    bool operator==(const AudioConfig& config) const;
    bool operator!=(const AudioConfig& config) const;
    virtual status_t writeToParcel(Parcel* parcel) const;
    virtual status_t readFromParcel(const Parcel* in);
    void setPtimeMillis(const int8_t ptime);
    int8_t getPtimeMillis();
    void setMaxPtimeMillis(const int32_t maxPtime);
    int32_t getMaxPtimeMillis();
    void setDtxEnabled(const bool enable);
    bool getDtxEnabled();
    void setCodecType(const int32_t type);
    int32_t getCodecType();
    void setTxDtmfPayloadTypeNumber(const int8_t num);
    void setRxDtmfPayloadTypeNumber(const int8_t num);
    int8_t getTxDtmfPayloadTypeNumber();
    int8_t getRxDtmfPayloadTypeNumber();
    void setDtmfsamplingRateKHz(const int8_t sampling);
    int8_t getDtmfsamplingRateKHz();
    void setAmrParams(const AmrParams& param);
    AmrParams getAmrParams();
    void setEvsParams(const EvsParams& param);
    EvsParams getEvsParams();

protected:
    /**
     * @brief Recommended length of time in milliseconds represented by the media
     * in each packet, see RFC 4566
     */
    int8_t pTimeMillis;
    /**
     * @brief Maximum amount of media that can be encapsulated in each packet
     * represented in milliseconds, see RFC 4566
     */
    int32_t maxPtimeMillis;
    /**
     * @brief Whether discontinuous transmission is enabled or not
     */
    bool dtxEnabled;
    /**
     * @brief Audio codec type
     */
    int32_t codecType;
    /**
     * @brief Dynamic payload type number to be used for DTMF RTP packets. The values is
     * in the range from 96 to 127 chosen during the session establishment. The PT
     * value of the RTP header of all DTMF packets shall be set with this value.
     */
    int8_t mDtmfTxPayloadTypeNumber;

    /**
     * @brief Dynamic payload type number to be used for DTMF RTP packets. The values is
     * in the range from 96 to 127 chosen during the session establishment. The PT
     * value of the RTP header of all DTMF packets shall be set with this value.
     */
    int8_t mDtmfRxPayloadTypeNumber;
    /**
     * @brief Sampling rate for DTMF tone in kHz
     */
    int8_t dtmfsamplingRateKHz;
    /**
     * @brief Negotiated AMR codec parameters
     */
    AmrParams amrParams;
    /**
     * @brief Negotiated EVS codec parameters
     */
    EvsParams evsParams;
};

}  // namespace imsmedia

}  // namespace telephony

}  // namespace android

#endif
