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

#ifndef MEDIA_QUALITY_THRESHOLD_H
#define MEDIA_QUALITY_THRESHOLD_H

#include <binder/Parcel.h>
#include <binder/Parcelable.h>
#include <binder/Status.h>
#include <stdint.h>
#include <vector>

namespace android
{

namespace telephony
{

namespace imsmedia
{

/** Native representation of android.telephony.imsmedia.MediaQualityThreshold */
class MediaQualityThreshold : public Parcelable
{
public:
    MediaQualityThreshold();
    MediaQualityThreshold(const MediaQualityThreshold& threshold);
    virtual ~MediaQualityThreshold();
    MediaQualityThreshold& operator=(const MediaQualityThreshold& threshold);
    bool operator==(const MediaQualityThreshold& threshold) const;
    bool operator!=(const MediaQualityThreshold& threshold) const;
    virtual status_t writeToParcel(Parcel* parcel) const;
    virtual status_t readFromParcel(const Parcel* in);
    void setRtpInactivityTimerMillis(std::vector<int32_t> times);
    std::vector<int32_t> getRtpInactivityTimerMillis() const;
    void setRtcpInactivityTimerMillis(int32_t time);
    int32_t getRtcpInactivityTimerMillis() const;
    void setRtpHysteresisTimeInMillis(int32_t time);
    int32_t getRtpHysteresisTimeInMillis() const;
    void setRtpPacketLossDurationMillis(int32_t time);
    int32_t getRtpPacketLossDurationMillis() const;
    void setRtpPacketLossRate(std::vector<int32_t> rates);
    std::vector<int32_t> getRtpPacketLossRate() const;
    void setRtpJitterMillis(std::vector<int32_t> jitters);
    std::vector<int32_t> getRtpJitterMillis() const;
    void setNotifyCurrentStatus(bool status);
    bool getNotifyCurrentStatus() const;
    void setVideoBitrateBps(int32_t bitrate);
    int32_t getVideoBitrateBps() const;

private:
    /** The timer in milliseconds for monitoring RTP inactivity */
    std::vector<int32_t> mRtpInactivityTimerMillis;
    /** The timer in milliseconds for monitoring RTCP inactivity */
    int32_t mRtcpInactivityTimerMillis;
    /**
     * Set the threshold hysteresis time for packet loss and jitter. This has a goal to prevent
     * frequent ping-pong notification. So whenever a notifier needs to report the cross of
     * threshold in opposite direction, this hysteresis timer should be respected.
     */
    int32_t mRtpHysteresisTimeInMillis;
    /** Set the duration in milliseconds for monitoring the RTP packet loss rate */
    int32_t mRtpPacketLossDurationMillis;
    /**
     * Packet loss rate in percentage of (total number of packets lost) /
     * (total number of packets expected) during rtpPacketLossDurationMs
     */
    std::vector<int32_t> mRtpPacketLossRate;
    /** RTP jitter threshold in milliseconds */
    std::vector<int32_t> mRtpJitterMillis;
    /**
     * A flag indicating whether the client needs to be notify the current media quality status
     * right after threshold is being set. True means the media stack should notify the client
     * of the current status.
     */
    bool mNotifyCurrentStatus;

    /**
     * The receiving bitrate threshold in bps for video call. If it is not zero, bitrate
     * notification event is triggered when the receiving frame bitrate is less than the
     * threshold.
     */
    int mVideoBitrateBps;
};

}  // namespace imsmedia

}  // namespace telephony

}  // namespace android

#endif