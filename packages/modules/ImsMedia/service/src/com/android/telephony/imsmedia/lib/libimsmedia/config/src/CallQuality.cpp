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

#include <CallQuality.h>

namespace android
{

namespace telephony
{

namespace imsmedia
{

#define DEFAULT_PARAM (-1)

CallQuality::CallQuality()
{
    mDownlinkCallQualityLevel = 0;
    mUplinkCallQualityLevel = 0;
    mCallDuration = 0;
    mNumRtpPacketsTransmitted = 0;
    mNumRtpPacketsReceived = 0;
    mNumRtpPacketsTransmittedLost = 0;
    mNumRtpPacketsNotReceived = 0;
    mAverageRelativeJitter = 0;
    mMaxRelativeJitter = 0;
    mAverageRoundTripTime = 0;
    mCodecType = DEFAULT_PARAM;
    mRtpInactivityDetected = false;
    mRxSilenceDetected = false;
    mTxSilenceDetected = false;
    mNumVoiceFrames = 0;
    mNumNoDataFrames = 0;
    mNumDroppedRtpPackets = 0;
    mMinPlayoutDelayMillis = 0;
    mMaxPlayoutDelayMillis = 0;
    mNumRtpSidPacketsReceived = 0;
    mNumRtpDuplicatePackets = 0;
}

CallQuality::CallQuality(const CallQuality& quality)
{
    mDownlinkCallQualityLevel = quality.mDownlinkCallQualityLevel;
    mUplinkCallQualityLevel = quality.mUplinkCallQualityLevel;
    mCallDuration = quality.mCallDuration;
    mNumRtpPacketsTransmitted = quality.mNumRtpPacketsTransmitted;
    mNumRtpPacketsReceived = quality.mNumRtpPacketsReceived;
    mNumRtpPacketsTransmittedLost = quality.mNumRtpPacketsTransmittedLost;
    mNumRtpPacketsNotReceived = quality.mNumRtpPacketsNotReceived;
    mAverageRelativeJitter = quality.mAverageRelativeJitter;
    mMaxRelativeJitter = quality.mMaxRelativeJitter;
    mAverageRoundTripTime = quality.mAverageRoundTripTime;
    mCodecType = quality.mCodecType;
    mRtpInactivityDetected = quality.mRtpInactivityDetected;
    mRxSilenceDetected = quality.mRxSilenceDetected;
    mTxSilenceDetected = quality.mTxSilenceDetected;
    mNumVoiceFrames = quality.mNumVoiceFrames;
    mNumNoDataFrames = quality.mNumNoDataFrames;
    mNumDroppedRtpPackets = quality.mNumDroppedRtpPackets;
    mMinPlayoutDelayMillis = quality.mMinPlayoutDelayMillis;
    mMaxPlayoutDelayMillis = quality.mMaxPlayoutDelayMillis;
    mNumRtpSidPacketsReceived = quality.mNumRtpSidPacketsReceived;
    mNumRtpDuplicatePackets = quality.mNumRtpDuplicatePackets;
}

CallQuality::~CallQuality() {}

CallQuality& CallQuality::operator=(const CallQuality& quality)
{
    if (this != &quality)
    {
        mDownlinkCallQualityLevel = quality.mDownlinkCallQualityLevel;
        mUplinkCallQualityLevel = quality.mUplinkCallQualityLevel;
        mCallDuration = quality.mCallDuration;
        mNumRtpPacketsTransmitted = quality.mNumRtpPacketsTransmitted;
        mNumRtpPacketsReceived = quality.mNumRtpPacketsReceived;
        mNumRtpPacketsTransmittedLost = quality.mNumRtpPacketsTransmittedLost;
        mNumRtpPacketsNotReceived = quality.mNumRtpPacketsNotReceived;
        mAverageRelativeJitter = quality.mAverageRelativeJitter;
        mMaxRelativeJitter = quality.mMaxRelativeJitter;
        mAverageRoundTripTime = quality.mAverageRoundTripTime;
        mCodecType = quality.mCodecType;
        mRtpInactivityDetected = quality.mRtpInactivityDetected;
        mRxSilenceDetected = quality.mRxSilenceDetected;
        mTxSilenceDetected = quality.mTxSilenceDetected;
        mNumVoiceFrames = quality.mNumVoiceFrames;
        mNumNoDataFrames = quality.mNumNoDataFrames;
        mNumDroppedRtpPackets = quality.mNumDroppedRtpPackets;
        mMinPlayoutDelayMillis = quality.mMinPlayoutDelayMillis;
        mMaxPlayoutDelayMillis = quality.mMaxPlayoutDelayMillis;
        mNumRtpSidPacketsReceived = quality.mNumRtpSidPacketsReceived;
        mNumRtpDuplicatePackets = quality.mNumRtpDuplicatePackets;
    }
    return *this;
}

bool CallQuality::operator==(const CallQuality& quality) const
{
    return (mDownlinkCallQualityLevel == quality.mDownlinkCallQualityLevel &&
            mUplinkCallQualityLevel == quality.mUplinkCallQualityLevel &&
            mCallDuration == quality.mCallDuration &&
            mNumRtpPacketsTransmitted == quality.mNumRtpPacketsTransmitted &&
            mNumRtpPacketsReceived == quality.mNumRtpPacketsReceived &&
            mNumRtpPacketsTransmittedLost == quality.mNumRtpPacketsTransmittedLost &&
            mNumRtpPacketsNotReceived == quality.mNumRtpPacketsNotReceived &&
            mAverageRelativeJitter == quality.mAverageRelativeJitter &&
            mMaxRelativeJitter == quality.mMaxRelativeJitter &&
            mAverageRoundTripTime == quality.mAverageRoundTripTime &&
            mCodecType == quality.mCodecType &&
            mRtpInactivityDetected == quality.mRtpInactivityDetected &&
            mRxSilenceDetected == quality.mRxSilenceDetected &&
            mTxSilenceDetected == quality.mTxSilenceDetected &&
            mNumVoiceFrames == quality.mNumVoiceFrames &&
            mNumNoDataFrames == quality.mNumNoDataFrames &&
            mNumDroppedRtpPackets == quality.mNumDroppedRtpPackets &&
            mMinPlayoutDelayMillis == quality.mMinPlayoutDelayMillis &&
            mMaxPlayoutDelayMillis == quality.mMaxPlayoutDelayMillis &&
            mNumRtpSidPacketsReceived == quality.mNumRtpSidPacketsReceived &&
            mNumRtpDuplicatePackets == quality.mNumRtpDuplicatePackets);
}

bool CallQuality::operator!=(const CallQuality& quality) const
{
    return (mDownlinkCallQualityLevel != quality.mDownlinkCallQualityLevel ||
            mUplinkCallQualityLevel != quality.mUplinkCallQualityLevel ||
            mCallDuration != quality.mCallDuration ||
            mNumRtpPacketsTransmitted != quality.mNumRtpPacketsTransmitted ||
            mNumRtpPacketsReceived != quality.mNumRtpPacketsReceived ||
            mNumRtpPacketsTransmittedLost != quality.mNumRtpPacketsTransmittedLost ||
            mNumRtpPacketsNotReceived != quality.mNumRtpPacketsNotReceived ||
            mAverageRelativeJitter != quality.mAverageRelativeJitter ||
            mMaxRelativeJitter != quality.mMaxRelativeJitter ||
            mAverageRoundTripTime != quality.mAverageRoundTripTime ||
            mCodecType != quality.mCodecType ||
            mRtpInactivityDetected != quality.mRtpInactivityDetected ||
            mRxSilenceDetected != quality.mRxSilenceDetected ||
            mTxSilenceDetected != quality.mTxSilenceDetected ||
            mNumVoiceFrames != quality.mNumVoiceFrames ||
            mNumNoDataFrames != quality.mNumNoDataFrames ||
            mNumDroppedRtpPackets != quality.mNumDroppedRtpPackets ||
            mMinPlayoutDelayMillis != quality.mMinPlayoutDelayMillis ||
            mMaxPlayoutDelayMillis != quality.mMaxPlayoutDelayMillis ||
            mNumRtpSidPacketsReceived != quality.mNumRtpSidPacketsReceived ||
            mNumRtpDuplicatePackets != quality.mNumRtpDuplicatePackets);
}

status_t CallQuality::writeToParcel(Parcel* out) const
{
    status_t err;
    err = out->writeInt32(mDownlinkCallQualityLevel);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(mUplinkCallQualityLevel);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(mCallDuration);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(mNumRtpPacketsTransmitted);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(mNumRtpPacketsReceived);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(mNumRtpPacketsTransmittedLost);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(mNumRtpPacketsNotReceived);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(mAverageRelativeJitter);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(mMaxRelativeJitter);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(mAverageRoundTripTime);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(mCodecType);
    if (err != NO_ERROR)
    {
        return err;
    }

    int32_t value = 0;
    mRtpInactivityDetected ? value = 1 : value = 0;
    err = out->writeInt32(value);
    if (err != NO_ERROR)
    {
        return err;
    }

    mRxSilenceDetected ? value = 1 : value = 0;
    err = out->writeInt32(value);
    if (err != NO_ERROR)
    {
        return err;
    }

    mTxSilenceDetected ? value = 1 : value = 0;
    err = out->writeInt32(value);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(mNumVoiceFrames);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(mNumNoDataFrames);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(mNumDroppedRtpPackets);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt64(mMinPlayoutDelayMillis);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt64(mMaxPlayoutDelayMillis);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(mNumRtpSidPacketsReceived);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(mNumRtpDuplicatePackets);
    if (err != NO_ERROR)
    {
        return err;
    }

    return NO_ERROR;
}

status_t CallQuality::readFromParcel(const Parcel* in)
{
    status_t err;
    err = in->readInt32(&mDownlinkCallQualityLevel);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&mUplinkCallQualityLevel);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&mCallDuration);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&mNumRtpPacketsTransmitted);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&mNumRtpPacketsReceived);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&mNumRtpPacketsTransmittedLost);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&mNumRtpPacketsNotReceived);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&mAverageRelativeJitter);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&mMaxRelativeJitter);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&mAverageRoundTripTime);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&mCodecType);
    if (err != NO_ERROR)
    {
        return err;
    }

    int32_t value = 0;
    err = in->readInt32(&value);
    if (err != NO_ERROR)
    {
        return err;
    }

    value == 0 ? mRtpInactivityDetected = false : mRtpInactivityDetected = true;

    err = in->readInt32(&value);
    if (err != NO_ERROR)
    {
        return err;
    }

    value == 0 ? mRxSilenceDetected = false : mRxSilenceDetected = true;

    err = in->readInt32(&value);
    if (err != NO_ERROR)
    {
        return err;
    }

    value == 0 ? mTxSilenceDetected = false : mTxSilenceDetected = true;

    err = in->readInt32(&mNumVoiceFrames);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&mNumNoDataFrames);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&mNumDroppedRtpPackets);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt64(&mMinPlayoutDelayMillis);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt64(&mMaxPlayoutDelayMillis);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&mNumRtpSidPacketsReceived);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&mNumRtpDuplicatePackets);
    if (err != NO_ERROR)
    {
        return err;
    }

    return NO_ERROR;
}

int CallQuality::getDownlinkCallQualityLevel()
{
    return mDownlinkCallQualityLevel;
}

void CallQuality::setDownlinkCallQualityLevel(const int level)
{
    mDownlinkCallQualityLevel = level;
}

int CallQuality::getUplinkCallQualityLevel()
{
    return mUplinkCallQualityLevel;
}

void CallQuality::setUplinkCallQualityLevel(const int level)
{
    mUplinkCallQualityLevel = level;
}

int CallQuality::getCallDuration()
{
    return mCallDuration;
}

void CallQuality::setCallDuration(const int duration)
{
    mCallDuration = duration;
}

int CallQuality::getNumRtpPacketsTransmitted()
{
    return mNumRtpPacketsTransmitted;
}

void CallQuality::setNumRtpPacketsTransmitted(const int num)
{
    mNumRtpPacketsTransmitted = num;
}

int CallQuality::getNumRtpPacketsReceived()
{
    return mNumRtpPacketsReceived;
}

void CallQuality::setNumRtpPacketsReceived(const int num)
{
    mNumRtpPacketsReceived = num;
}

int CallQuality::getNumRtpPacketsTransmittedLost()
{
    return mNumRtpPacketsTransmittedLost;
}

void CallQuality::setNumRtpPacketsTransmittedLost(const int num)
{
    mNumRtpPacketsTransmittedLost = num;
}

int CallQuality::getNumRtpPacketsNotReceived()
{
    return mNumRtpPacketsNotReceived;
}

void CallQuality::setNumRtpPacketsNotReceived(const int num)
{
    mNumRtpPacketsNotReceived = num;
}

int CallQuality::getAverageRelativeJitter()
{
    return mAverageRelativeJitter;
}

void CallQuality::setAverageRelativeJitter(const int jitter)
{
    mAverageRelativeJitter = jitter;
}

int CallQuality::getMaxRelativeJitter()
{
    return mMaxRelativeJitter;
}

void CallQuality::setMaxRelativeJitter(const int jitter)
{
    mMaxRelativeJitter = jitter;
}

int CallQuality::getAverageRoundTripTime()
{
    return mAverageRoundTripTime;
}

void CallQuality::setAverageRoundTripTime(const int time)
{
    mAverageRoundTripTime = time;
}

int CallQuality::getCodecType()
{
    return mCodecType;
}

void CallQuality::setCodecType(const int type)
{
    mCodecType = type;
}

bool CallQuality::getRtpInactivityDetected()
{
    return mRtpInactivityDetected;
}

void CallQuality::setRtpInactivityDetected(const bool detected)
{
    mRtpInactivityDetected = detected;
}

bool CallQuality::getRxSilenceDetected()
{
    return mRxSilenceDetected;
}

void CallQuality::setRxSilenceDetected(const bool detected)
{
    mRxSilenceDetected = detected;
}

bool CallQuality::getTxSilenceDetected()
{
    return mTxSilenceDetected;
}

void CallQuality::setTxSilenceDetected(const bool detected)
{
    mTxSilenceDetected = detected;
}

int CallQuality::getNumVoiceFrames()
{
    return mNumVoiceFrames;
}

void CallQuality::setNumVoiceFrames(const int num)
{
    mNumVoiceFrames = num;
}

int CallQuality::getNumNoDataFrames()
{
    return mNumNoDataFrames;
}

void CallQuality::setNumNoDataFrames(const int num)
{
    mNumNoDataFrames = num;
}

int CallQuality::getNumDroppedRtpPackets()
{
    return mNumDroppedRtpPackets;
}

void CallQuality::setNumDroppedRtpPackets(const int num)
{
    mNumDroppedRtpPackets = num;
}

int64_t CallQuality::getMinPlayoutDelayMillis()
{
    return mMinPlayoutDelayMillis;
}

void CallQuality::setMinPlayoutDelayMillis(const int64_t delay)
{
    mMinPlayoutDelayMillis = delay;
}

int64_t CallQuality::getMaxPlayoutDelayMillis()
{
    return mMaxPlayoutDelayMillis;
}

void CallQuality::setMaxPlayoutDelayMillis(const int64_t delay)
{
    mMaxPlayoutDelayMillis = delay;
}

int CallQuality::getNumRtpSidPacketsReceived()
{
    return mNumRtpSidPacketsReceived;
}

void CallQuality::setNumRtpSidPacketsReceived(const int num)
{
    mNumRtpSidPacketsReceived = num;
}

int CallQuality::getNumRtpDuplicatePackets()
{
    return mNumRtpDuplicatePackets;
}

void CallQuality::setNumRtpDuplicatePackets(const int num)
{
    mNumRtpDuplicatePackets = num;
}

}  // namespace imsmedia

}  // namespace telephony

}  // namespace android