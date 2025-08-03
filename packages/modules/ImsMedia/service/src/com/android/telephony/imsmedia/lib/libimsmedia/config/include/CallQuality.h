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

#ifndef IMS_CALL_QULITY_H
#define IMS_CALL_QULITY_H

#include <binder/Parcel.h>
#include <binder/Parcelable.h>
#include <binder/Status.h>
#include <stdint.h>

namespace android
{

namespace telephony
{

namespace imsmedia
{

/**
 * @brief Implementation of CallQualty class in native
 *
 */
class CallQuality : public Parcelable
{
public:
    CallQuality();
    CallQuality(const CallQuality& quality);
    virtual ~CallQuality();
    enum
    {
        /** < 1% packet loss */
        kCallQualityExcellent = 0,
        /** <= 3% packet loss */
        kCallQualityGood = 1,
        /** <= 5% packet loss */
        kCallQualityFair = 2,
        /** <= 8% packet loss */
        kCallQualityPoor = 3,
        /** > 8% packet loss */
        kCallQualityBad = 4,
    };

    enum
    {
        /**
         * The codec type. This value corresponds to the AUDIO_QUALITY_* constants in
         * {@link ImsStreamMediaProfile}.
         */
        AUDIO_QUALITY_NONE = 0,
        AUDIO_QUALITY_AMR,
        AUDIO_QUALITY_AMR_WB,
        AUDIO_QUALITY_QCELP13K,
        AUDIO_QUALITY_EVRC,
        AUDIO_QUALITY_EVRC_B,
        AUDIO_QUALITY_EVRC_WB,
        AUDIO_QUALITY_EVRC_NW,
        AUDIO_QUALITY_GSM_EFR,
        AUDIO_QUALITY_GSM_FR,
        AUDIO_QUALITY_GSM_HR,
        AUDIO_QUALITY_G711U,
        AUDIO_QUALITY_G723,
        AUDIO_QUALITY_G711A,
        AUDIO_QUALITY_G722,
        AUDIO_QUALITY_G711AB,
        AUDIO_QUALITY_G729,
        AUDIO_QUALITY_EVS_NB,
        AUDIO_QUALITY_EVS_WB,
        AUDIO_QUALITY_EVS_SWB,
        AUDIO_QUALITY_EVS_FB,
    };

    CallQuality& operator=(const CallQuality& quality);
    bool operator==(const CallQuality& quality) const;
    bool operator!=(const CallQuality& quality) const;
    virtual status_t writeToParcel(Parcel* out) const;
    virtual status_t readFromParcel(const Parcel* in);

    int32_t getDownlinkCallQualityLevel();
    void setDownlinkCallQualityLevel(const int32_t level);
    int32_t getUplinkCallQualityLevel();
    void setUplinkCallQualityLevel(const int32_t level);
    int32_t getCallDuration();
    void setCallDuration(const int32_t duration);
    int32_t getNumRtpPacketsTransmitted();
    void setNumRtpPacketsTransmitted(const int32_t num);
    int32_t getNumRtpPacketsReceived();
    void setNumRtpPacketsReceived(const int32_t num);
    int32_t getNumRtpPacketsTransmittedLost();
    void setNumRtpPacketsTransmittedLost(const int32_t num);
    int32_t getNumRtpPacketsNotReceived();
    void setNumRtpPacketsNotReceived(const int32_t num);
    int32_t getAverageRelativeJitter();
    void setAverageRelativeJitter(const int32_t jitter);
    int32_t getMaxRelativeJitter();
    void setMaxRelativeJitter(const int32_t jitter);
    int32_t getAverageRoundTripTime();
    void setAverageRoundTripTime(const int32_t time);
    int32_t getCodecType();
    void setCodecType(const int32_t type);
    bool getRtpInactivityDetected();
    void setRtpInactivityDetected(const bool detected);
    bool getRxSilenceDetected();
    void setRxSilenceDetected(const bool detected);
    bool getTxSilenceDetected();
    void setTxSilenceDetected(const bool detected);
    int32_t getNumVoiceFrames();
    void setNumVoiceFrames(const int32_t num);
    int32_t getNumNoDataFrames();
    void setNumNoDataFrames(const int32_t num);
    int32_t getNumDroppedRtpPackets();
    void setNumDroppedRtpPackets(const int32_t num);
    int64_t getMinPlayoutDelayMillis();
    void setMinPlayoutDelayMillis(const int64_t delay);
    int64_t getMaxPlayoutDelayMillis();
    void setMaxPlayoutDelayMillis(const int64_t delay);
    int32_t getNumRtpSidPacketsReceived();
    void setNumRtpSidPacketsReceived(const int32_t num);
    int32_t getNumRtpDuplicatePackets();
    void setNumRtpDuplicatePackets(const int32_t num);

private:
    /** The Downlink call quality level measured in 5 sec monitoring*/
    int32_t mDownlinkCallQualityLevel;
    /** The Uplink call quality level */
    int32_t mUplinkCallQualityLevel;
    /** The call duration in milliseconds since the call session began. */
    int32_t mCallDuration;
    /** The number of RTP packets sent for an ongoing call. */
    int32_t mNumRtpPacketsTransmitted;
    /** The number of RTP packets received for ongoing calls. */
    int32_t mNumRtpPacketsReceived;
    /** The number of RTP packets which were lost in the network and never transmitted. */
    int32_t mNumRtpPacketsTransmittedLost;
    /** The number of RTP packets which were lost in the network and never received. */
    int32_t mNumRtpPacketsNotReceived;
    /** The average relative jitter in milliseconds. */
    int32_t mAverageRelativeJitter;
    /** The maximum relative jitter in milliseconds. */
    int32_t mMaxRelativeJitter;
    /** The average round trip delay in milliseconds. */
    int32_t mAverageRoundTripTime;
    /** The codec type used in the ongoing call. */
    int32_t mCodecType;
    /** To be true if no incoming RTP is received for a continuous duration of 4 seconds. */
    bool mRtpInactivityDetected;
    /** To be true if only silence RTP packets are received for 20 seconds immediately after the
     * call is connected. */
    bool mRxSilenceDetected;
    /** True if only silence RTP packets are sent for 20 seconds immediately after the call is
     * connected. The silence packet can be detected by observing that the RTP timestamp is not
     * contiguous with the end of the interval covered by the previous packet even though the
     * RTP sequence number has been incremented only by one. Check RFC 3389. */
    bool mTxSilenceDetected;
    /** The number of voice frames sent by jitter buffer to audio. */
    int32_t mNumVoiceFrames;
    /** The number of no-data frames sent by jitter buffer to audio. */
    int32_t mNumNoDataFrames;
    /** The number of RTP Voice packets dropped by jitter buffer. */
    int32_t mNumDroppedRtpPackets;
    /** The minimum playout delay in the reporting interval in milliseconds. */
    int64_t mMinPlayoutDelayMillis;
    /** The maximum Playout delay in the reporting interval in milliseconds. */
    int64_t mMaxPlayoutDelayMillis;
    /** The total number of RTP SID (Silence Insertion Descriptor) */
    int32_t mNumRtpSidPacketsReceived;
    /** The total number of RTP duplicate packets received by this device for an ongoing call. */
    int32_t mNumRtpDuplicatePackets;
};

}  // namespace imsmedia

}  // namespace telephony

}  // namespace android

#endif