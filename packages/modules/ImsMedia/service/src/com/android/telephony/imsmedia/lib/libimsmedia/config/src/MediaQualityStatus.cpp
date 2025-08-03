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

#include <MediaQualityStatus.h>

namespace android
{

namespace telephony
{

namespace imsmedia
{

MediaQualityStatus::MediaQualityStatus()
{
    mRtpInactivityTimeMillis = 0;
    mRtcpInactivityTimeMillis = 0;
    mRtpPacketLossRate = 0;
    mRtpJitterMillis = 0;
}

MediaQualityStatus::MediaQualityStatus(const MediaQualityStatus& status)
{
    mRtpInactivityTimeMillis = status.mRtpInactivityTimeMillis;
    mRtcpInactivityTimeMillis = status.mRtcpInactivityTimeMillis;
    mRtpPacketLossRate = status.mRtpPacketLossRate;
    mRtpJitterMillis = status.mRtpJitterMillis;
}

MediaQualityStatus::~MediaQualityStatus() {}

MediaQualityStatus& MediaQualityStatus::operator=(const MediaQualityStatus& status)
{
    if (this != &status)
    {
        mRtpInactivityTimeMillis = status.mRtpInactivityTimeMillis;
        mRtcpInactivityTimeMillis = status.mRtcpInactivityTimeMillis;
        mRtpPacketLossRate = status.mRtpPacketLossRate;
        mRtpJitterMillis = status.mRtpJitterMillis;
    }

    return *this;
}

bool MediaQualityStatus::operator==(const MediaQualityStatus& status) const
{
    return (mRtpInactivityTimeMillis == status.mRtpInactivityTimeMillis &&
            mRtcpInactivityTimeMillis == status.mRtcpInactivityTimeMillis &&
            mRtpPacketLossRate == status.mRtpPacketLossRate &&
            mRtpJitterMillis == status.mRtpJitterMillis);
}

bool MediaQualityStatus::operator!=(const MediaQualityStatus& status) const
{
    return (mRtpInactivityTimeMillis != status.mRtpInactivityTimeMillis ||
            mRtcpInactivityTimeMillis != status.mRtcpInactivityTimeMillis ||
            mRtpPacketLossRate != status.mRtpPacketLossRate ||
            mRtpJitterMillis != status.mRtpJitterMillis);
}

status_t MediaQualityStatus::writeToParcel(Parcel* out) const
{
    out->writeInt32(mRtpInactivityTimeMillis);
    out->writeInt32(mRtcpInactivityTimeMillis);
    out->writeInt32(mRtpPacketLossRate);
    out->writeInt32(mRtpJitterMillis);
    return NO_ERROR;
}

status_t MediaQualityStatus::readFromParcel(const Parcel* in)
{
    in->readInt32(&mRtpInactivityTimeMillis);
    in->readInt32(&mRtcpInactivityTimeMillis);
    in->readInt32(&mRtpPacketLossRate);
    in->readInt32(&mRtpJitterMillis);
    return NO_ERROR;
}

void MediaQualityStatus::setRtpInactivityTimeMillis(int32_t time)
{
    mRtpInactivityTimeMillis = time;
}

int32_t MediaQualityStatus::getRtpInactivityTimeMillis()
{
    return mRtpInactivityTimeMillis;
}

void MediaQualityStatus::setRtcpInactivityTimeMillis(int32_t time)
{
    mRtcpInactivityTimeMillis = time;
}

int32_t MediaQualityStatus::getRtcpInactivityTimeMillis()
{
    return mRtcpInactivityTimeMillis;
}

void MediaQualityStatus::setRtpPacketLossRate(int32_t rate)
{
    mRtpPacketLossRate = rate;
}

int32_t MediaQualityStatus::getRtpPacketLossRate()
{
    return mRtpPacketLossRate;
}

void MediaQualityStatus::setRtpJitterMillis(int32_t jitter)
{
    mRtpJitterMillis = jitter;
}

int32_t MediaQualityStatus::getRtpJitterMillis()
{
    return mRtpJitterMillis;
}

}  // namespace imsmedia

}  // namespace telephony

}  // namespace android