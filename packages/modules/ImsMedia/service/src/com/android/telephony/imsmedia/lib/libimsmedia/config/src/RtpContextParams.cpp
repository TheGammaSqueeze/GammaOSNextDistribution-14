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

#include <RtpContextParams.h>

namespace android
{

namespace telephony
{

namespace imsmedia
{

RtpContextParams::RtpContextParams() :
        ssrc(0),
        timestamp(0),
        sequenceNumber(0)
{
}

RtpContextParams::RtpContextParams(
        const int64_t ssrc, const int64_t timestamp, const int32_t sequenceNumber)
{
    this->ssrc = ssrc;
    this->timestamp = timestamp;
    this->sequenceNumber = sequenceNumber;
}

RtpContextParams::RtpContextParams(const RtpContextParams& params)
{
    this->ssrc = params.ssrc;
    this->timestamp = params.timestamp;
    this->sequenceNumber = params.sequenceNumber;
}

RtpContextParams::~RtpContextParams() {}

RtpContextParams& RtpContextParams::operator=(const RtpContextParams& params)
{
    if (this != &params)
    {
        this->ssrc = params.ssrc;
        this->timestamp = params.timestamp;
        this->sequenceNumber = params.sequenceNumber;
    }
    return *this;
}

bool RtpContextParams::operator==(const RtpContextParams& params) const
{
    return (this->ssrc == params.ssrc && this->timestamp == params.timestamp &&
            this->sequenceNumber == params.sequenceNumber);
}

bool RtpContextParams::operator!=(const RtpContextParams& params) const
{
    return (this->ssrc != params.ssrc || this->timestamp != params.timestamp ||
            this->sequenceNumber != params.sequenceNumber);
}

status_t RtpContextParams::writeToParcel(Parcel* out) const
{
    status_t err;

    if (out == nullptr)
    {
        return BAD_VALUE;
    }

    err = out->writeInt64(ssrc);

    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt64(timestamp);

    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(sequenceNumber);

    if (err != NO_ERROR)
    {
        return err;
    }

    return NO_ERROR;
}

status_t RtpContextParams::readFromParcel(const Parcel* in)
{
    status_t err;

    if (in == nullptr)
    {
        return BAD_VALUE;
    }

    err = in->readInt64(&ssrc);

    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt64(&timestamp);

    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&sequenceNumber);

    if (err != NO_ERROR)
    {
        return err;
    }

    return NO_ERROR;
}

int64_t RtpContextParams::getSsrc()
{
    return ssrc;
}

void RtpContextParams::setSsrc(int64_t ssrc)
{
    this->ssrc = ssrc;
}

int64_t RtpContextParams::getTimestamp()
{
    return timestamp;
}

void RtpContextParams::setTimestamp(int64_t timestamp)
{
    this->timestamp = timestamp;
}

int32_t RtpContextParams::getSequenceNumber()
{
    return sequenceNumber;
}

void RtpContextParams::setSequenceNumber(int32_t sequenceNumber)
{
    this->sequenceNumber = sequenceNumber;
}

void RtpContextParams::setDefaultConfig()
{
    ssrc = 0;
    timestamp = 0;
    sequenceNumber = 0;
}

}  // namespace imsmedia

}  // namespace telephony

}  // namespace android