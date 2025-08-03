/*
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

#ifndef MEDIA_QUALITY_STATUS_H
#define MEDIA_QUALITY_STATUS_H

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

/** Native representation of android.telephony.imsmedia.MediaQualityStatus */
class MediaQualityStatus : public Parcelable
{
public:
    MediaQualityStatus();
    MediaQualityStatus(const MediaQualityStatus& status);
    virtual ~MediaQualityStatus();
    MediaQualityStatus& operator=(const MediaQualityStatus& status);
    bool operator==(const MediaQualityStatus& status) const;
    bool operator!=(const MediaQualityStatus& status) const;
    virtual status_t writeToParcel(Parcel* out) const;
    virtual status_t readFromParcel(const Parcel* in);
    void setRtpInactivityTimeMillis(int32_t time);
    int32_t getRtpInactivityTimeMillis();
    void setRtcpInactivityTimeMillis(int32_t time);
    int32_t getRtcpInactivityTimeMillis();
    void setRtpPacketLossRate(int32_t rate);
    int32_t getRtpPacketLossRate();
    void setRtpJitterMillis(int32_t jitter);
    int32_t getRtpJitterMillis();

private:
    /**
     * The rtp inactivity observed as per thresholds set by the MediaQualityThreshold API
     */
    int32_t mRtpInactivityTimeMillis;
    /**
     * The rtcp inactivity observed as per thresholds set by the MediaQualityThreshold API
     */
    int32_t mRtcpInactivityTimeMillis;
    /**
     * The rtp packet loss rate observed as per thresholds set by the MediaQualityThreshold API
     */
    int32_t mRtpPacketLossRate;
    /**
     * The rtp jitter observed as per thresholds set by MediaQualityThreshold API
     */
    int32_t mRtpJitterMillis;
};

}  // namespace imsmedia

}  // namespace telephony

}  // namespace android

#endif