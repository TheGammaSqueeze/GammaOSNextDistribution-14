/**
 * Copyright (C) 2024 The Android Open Source Project
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

#include <RtpReceptionStats.h>

namespace android
{

namespace telephony
{

namespace imsmedia
{

RtpReceptionStats::RtpReceptionStats()
{
    setDefaultConfig();
}

RtpReceptionStats::RtpReceptionStats(const int32_t rtpTimestamp, const int32_t rtcpSrTimestamp,
        const int64_t ntp, const int32_t jitterBuffer, const int32_t roundTripTime)
{
    mRtpTimestamp = rtpTimestamp;
    mRtcpSrTimestamp = rtcpSrTimestamp;
    mRtcpSrNtpTimestamp = ntp;
    mJitterBufferMs = jitterBuffer;
    mRoundTripTimeMs = roundTripTime;
}

RtpReceptionStats::RtpReceptionStats(const RtpReceptionStats& stats)
{
    mRtpTimestamp = stats.mRtpTimestamp;
    mRtcpSrTimestamp = stats.mRtcpSrTimestamp;
    mRtcpSrNtpTimestamp = stats.mRtcpSrNtpTimestamp;
    mJitterBufferMs = stats.mJitterBufferMs;
    mRoundTripTimeMs = stats.mRoundTripTimeMs;
}

RtpReceptionStats::~RtpReceptionStats() {}

RtpReceptionStats& RtpReceptionStats::operator=(const RtpReceptionStats& stats)
{
    if (this != &stats)
    {
        mRtpTimestamp = stats.mRtpTimestamp;
        mRtcpSrTimestamp = stats.mRtcpSrTimestamp;
        mRtcpSrNtpTimestamp = stats.mRtcpSrNtpTimestamp;
        mJitterBufferMs = stats.mJitterBufferMs;
        mRoundTripTimeMs = stats.mRoundTripTimeMs;
    }
    return *this;
}

bool RtpReceptionStats::operator==(const RtpReceptionStats& stats) const
{
    return (mRtpTimestamp == stats.mRtpTimestamp && mRtcpSrTimestamp == stats.mRtcpSrTimestamp &&
            mRtcpSrNtpTimestamp == stats.mRtcpSrNtpTimestamp &&
            mJitterBufferMs == stats.mJitterBufferMs && mRoundTripTimeMs == stats.mRoundTripTimeMs);
}

bool RtpReceptionStats::operator!=(const RtpReceptionStats& stats) const
{
    return (mRtpTimestamp != stats.mRtpTimestamp || mRtcpSrTimestamp != stats.mRtcpSrTimestamp ||
            mRtcpSrNtpTimestamp != stats.mRtcpSrNtpTimestamp ||
            mJitterBufferMs != stats.mJitterBufferMs || mRoundTripTimeMs != stats.mRoundTripTimeMs);
}

status_t RtpReceptionStats::writeToParcel(Parcel* out) const
{
    if (out == nullptr)
    {
        return BAD_VALUE;
    }

    out->writeInt32(mRtpTimestamp);
    out->writeInt32(mRtcpSrTimestamp);
    out->writeInt64(mRtcpSrNtpTimestamp);
    out->writeInt32(mJitterBufferMs);
    out->writeInt32(mRoundTripTimeMs);
    return NO_ERROR;
}

status_t RtpReceptionStats::readFromParcel(const Parcel* in)
{
    if (in == nullptr)
    {
        return BAD_VALUE;
    }

    in->readInt32(&mRtpTimestamp);
    in->readInt32(&mRtcpSrTimestamp);
    in->readInt64(&mRtcpSrNtpTimestamp);
    in->readInt32(&mJitterBufferMs);
    in->readInt32(&mRoundTripTimeMs);

    return NO_ERROR;
}

int32_t RtpReceptionStats::getRtpTimestamp()
{
    return mRtpTimestamp;
}

void RtpReceptionStats::setRtpTimestamp(const int32_t timestamp)
{
    mRtpTimestamp = timestamp;
}

int32_t RtpReceptionStats::getRtcpSrTimestamp()
{
    return mRtcpSrTimestamp;
}

void RtpReceptionStats::setRtcpSrTimestamp(const int32_t timestamp)
{
    mRtcpSrTimestamp = timestamp;
}

int64_t RtpReceptionStats::getRtcpSrNtpTimestamp()
{
    return mRtcpSrNtpTimestamp;
}

void RtpReceptionStats::setRtcpSrNtpTimestamp(const int64_t ntp)
{
    mRtcpSrNtpTimestamp = ntp;
}

int32_t RtpReceptionStats::getJitterBufferMs()
{
    return mJitterBufferMs;
}

void RtpReceptionStats::setJitterBufferMs(const int32_t jitterBufferMs)
{
    mJitterBufferMs = jitterBufferMs;
}

int32_t RtpReceptionStats::getRoundTripTimeMs()
{
    return mRoundTripTimeMs;
}

void RtpReceptionStats::setRoundTripTimeMs(const int32_t roundTripTimeMs)
{
    mRoundTripTimeMs = roundTripTimeMs;
}

void RtpReceptionStats::setDefaultConfig()
{
    mRtpTimestamp = 0;
    mRtcpSrTimestamp = 0;
    mRtcpSrNtpTimestamp = 0;
    mJitterBufferMs = 0;
    mRoundTripTimeMs = 0;
}

}  // namespace imsmedia

}  // namespace telephony

}  // namespace android