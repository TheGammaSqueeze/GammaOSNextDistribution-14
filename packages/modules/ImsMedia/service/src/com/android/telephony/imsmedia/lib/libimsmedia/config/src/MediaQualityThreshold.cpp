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

#include <MediaQualityThreshold.h>

namespace android
{

namespace telephony
{

namespace imsmedia
{

MediaQualityThreshold::MediaQualityThreshold()
{
    mRtpInactivityTimerMillis.clear();
    mRtcpInactivityTimerMillis = 0;
    mRtpHysteresisTimeInMillis = 0;
    mRtpPacketLossDurationMillis = 0;
    mRtpPacketLossRate.clear();
    mRtpJitterMillis.clear();
    mNotifyCurrentStatus = false;
    mVideoBitrateBps = 0;
}

MediaQualityThreshold::MediaQualityThreshold(const MediaQualityThreshold& threshold)
{
    mRtpInactivityTimerMillis = threshold.mRtpInactivityTimerMillis;
    mRtcpInactivityTimerMillis = threshold.mRtcpInactivityTimerMillis;
    mRtpHysteresisTimeInMillis = threshold.mRtpHysteresisTimeInMillis;
    mRtpPacketLossDurationMillis = threshold.mRtpPacketLossDurationMillis;
    mRtpPacketLossRate = threshold.mRtpPacketLossRate;
    mRtpJitterMillis = threshold.mRtpJitterMillis;
    mNotifyCurrentStatus = threshold.mNotifyCurrentStatus;
    mVideoBitrateBps = threshold.mVideoBitrateBps;
}

MediaQualityThreshold::~MediaQualityThreshold() {}

MediaQualityThreshold& MediaQualityThreshold::operator=(const MediaQualityThreshold& threshold)
{
    if (this != &threshold)
    {
        mRtpInactivityTimerMillis = threshold.mRtpInactivityTimerMillis;
        mRtcpInactivityTimerMillis = threshold.mRtcpInactivityTimerMillis;
        mRtpHysteresisTimeInMillis = threshold.mRtpHysteresisTimeInMillis;
        mRtpPacketLossDurationMillis = threshold.mRtpPacketLossDurationMillis;
        mRtpPacketLossRate = threshold.mRtpPacketLossRate;
        mRtpJitterMillis = threshold.mRtpJitterMillis;
        mNotifyCurrentStatus = threshold.mNotifyCurrentStatus;
        mVideoBitrateBps = threshold.mVideoBitrateBps;
    }
    return *this;
}

bool MediaQualityThreshold::operator==(const MediaQualityThreshold& threshold) const
{
    return (mRtpInactivityTimerMillis == threshold.mRtpInactivityTimerMillis &&
            mRtcpInactivityTimerMillis == threshold.mRtcpInactivityTimerMillis &&
            mRtpHysteresisTimeInMillis == threshold.mRtpHysteresisTimeInMillis &&
            mRtpPacketLossDurationMillis == threshold.mRtpPacketLossDurationMillis &&
            mRtpPacketLossRate == threshold.mRtpPacketLossRate &&
            mRtpJitterMillis == threshold.mRtpJitterMillis &&
            mNotifyCurrentStatus == threshold.mNotifyCurrentStatus &&
            mVideoBitrateBps == threshold.mVideoBitrateBps);
}

bool MediaQualityThreshold::operator!=(const MediaQualityThreshold& threshold) const
{
    return (mRtpInactivityTimerMillis != threshold.mRtpInactivityTimerMillis ||
            mRtcpInactivityTimerMillis != threshold.mRtcpInactivityTimerMillis ||
            mRtpHysteresisTimeInMillis != threshold.mRtpHysteresisTimeInMillis ||
            mRtpPacketLossDurationMillis != threshold.mRtpPacketLossDurationMillis ||
            mRtpPacketLossRate != threshold.mRtpPacketLossRate ||
            mRtpJitterMillis != threshold.mRtpJitterMillis ||
            mNotifyCurrentStatus != threshold.mNotifyCurrentStatus ||
            mVideoBitrateBps != threshold.mVideoBitrateBps);
}

status_t MediaQualityThreshold::writeToParcel(Parcel* out) const
{
    out->writeInt32(mRtpInactivityTimerMillis.size());

    for (auto& i : mRtpInactivityTimerMillis)
    {
        out->writeInt32(i);
    }

    out->writeInt32(mRtcpInactivityTimerMillis);
    out->writeInt32(mRtpHysteresisTimeInMillis);
    out->writeInt32(mRtpPacketLossDurationMillis);
    out->writeInt32(mRtpPacketLossRate.size());

    for (auto& i : mRtpPacketLossRate)
    {
        out->writeInt32(i);
    }

    out->writeInt32(mRtpJitterMillis.size());

    for (auto& i : mRtpJitterMillis)
    {
        out->writeInt32(i);
    }

    out->writeInt32(mNotifyCurrentStatus ? 1 : 0);
    out->writeInt32(mVideoBitrateBps);
    return NO_ERROR;
}

status_t MediaQualityThreshold::readFromParcel(const Parcel* in)
{
    int32_t arrayLength = 0;
    in->readInt32(&arrayLength);
    mRtpInactivityTimerMillis.resize(arrayLength);

    for (int32_t i = 0; i < arrayLength; i++)
    {
        in->readInt32(&mRtpInactivityTimerMillis[i]);
    }

    in->readInt32(&mRtcpInactivityTimerMillis);
    in->readInt32(&mRtpHysteresisTimeInMillis);
    in->readInt32(&mRtpPacketLossDurationMillis);
    in->readInt32(&arrayLength);
    mRtpPacketLossRate.resize(arrayLength);

    for (int32_t i = 0; i < arrayLength; i++)
    {
        in->readInt32(&mRtpPacketLossRate[i]);
    }

    in->readInt32(&arrayLength);
    mRtpJitterMillis.resize(arrayLength);

    for (int32_t i = 0; i < arrayLength; i++)
    {
        in->readInt32(&mRtpJitterMillis[i]);
    }

    int32_t value;
    in->readInt32(&value);
    value == 1 ? mNotifyCurrentStatus = true : mNotifyCurrentStatus = false;
    in->readInt32(&mVideoBitrateBps);
    return NO_ERROR;
}

void MediaQualityThreshold::setRtpInactivityTimerMillis(std::vector<int32_t> time)
{
    mRtpInactivityTimerMillis = time;
}

std::vector<int32_t> MediaQualityThreshold::getRtpInactivityTimerMillis() const
{
    return mRtpInactivityTimerMillis;
}

void MediaQualityThreshold::setRtcpInactivityTimerMillis(int32_t time)
{
    mRtcpInactivityTimerMillis = time;
}

int32_t MediaQualityThreshold::getRtcpInactivityTimerMillis() const
{
    return mRtcpInactivityTimerMillis;
}

void MediaQualityThreshold::setRtpHysteresisTimeInMillis(int32_t time)
{
    mRtpHysteresisTimeInMillis = time;
}

int32_t MediaQualityThreshold::getRtpHysteresisTimeInMillis() const
{
    return mRtpHysteresisTimeInMillis;
}

void MediaQualityThreshold::setRtpPacketLossDurationMillis(int32_t time)
{
    mRtpPacketLossDurationMillis = time;
}

int32_t MediaQualityThreshold::getRtpPacketLossDurationMillis() const
{
    return mRtpPacketLossDurationMillis;
}

void MediaQualityThreshold::setRtpPacketLossRate(std::vector<int32_t> rate)
{
    mRtpPacketLossRate = rate;
}

std::vector<int32_t> MediaQualityThreshold::getRtpPacketLossRate() const
{
    return mRtpPacketLossRate;
}

void MediaQualityThreshold::setRtpJitterMillis(std::vector<int32_t> jitter)
{
    mRtpJitterMillis = jitter;
}

std::vector<int32_t> MediaQualityThreshold::getRtpJitterMillis() const
{
    return mRtpJitterMillis;
}

void MediaQualityThreshold::setNotifyCurrentStatus(bool status)
{
    mNotifyCurrentStatus = status;
}

bool MediaQualityThreshold::getNotifyCurrentStatus() const
{
    return mNotifyCurrentStatus;
}

void MediaQualityThreshold::setVideoBitrateBps(int32_t bitrate)
{
    mVideoBitrateBps = bitrate;
}

int32_t MediaQualityThreshold::getVideoBitrateBps() const
{
    return mVideoBitrateBps;
}

}  // namespace imsmedia

}  // namespace telephony

}  // namespace android