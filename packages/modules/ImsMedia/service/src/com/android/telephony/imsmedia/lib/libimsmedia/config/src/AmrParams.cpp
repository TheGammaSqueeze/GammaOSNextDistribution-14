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

#include <AmrParams.h>

namespace android
{

namespace telephony
{

namespace imsmedia
{

/** Native representation of android.telephony.imsmedia.AmrParams */
AmrParams::AmrParams()
{
    amrMode = 0;
    octetAligned = false;
    maxRedundancyMillis = 0;
}

AmrParams::AmrParams(AmrParams& param)
{
    this->amrMode = param.amrMode;
    this->octetAligned = param.octetAligned;
    this->maxRedundancyMillis = param.maxRedundancyMillis;
}

AmrParams::~AmrParams() {}

AmrParams& AmrParams::operator=(const AmrParams& param)
{
    if (this != &param)
    {
        this->amrMode = param.amrMode;
        this->octetAligned = param.octetAligned;
        this->maxRedundancyMillis = param.maxRedundancyMillis;
    }
    return *this;
}

bool AmrParams::operator==(const AmrParams& param) const
{
    return (this->amrMode == param.amrMode && this->octetAligned == param.octetAligned &&
            this->maxRedundancyMillis == param.maxRedundancyMillis);
}

bool AmrParams::operator!=(const AmrParams& param) const
{
    return (this->amrMode != param.amrMode || this->octetAligned != param.octetAligned ||
            this->maxRedundancyMillis != param.maxRedundancyMillis);
}

status_t AmrParams::writeToParcel(Parcel* out) const
{
    status_t err;
    if (out == nullptr)
    {
        return BAD_VALUE;
    }

    err = out->writeInt32(amrMode);
    if (err != NO_ERROR)
    {
        return err;
    }

    int32_t value = 0;
    octetAligned ? value = 1 : value = 0;
    err = out->writeInt32(value);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(maxRedundancyMillis);
    if (err != NO_ERROR)
    {
        return err;
    }
    return NO_ERROR;
}

status_t AmrParams::readFromParcel(const Parcel* in)
{
    status_t err;
    if (in == nullptr)
    {
        return BAD_VALUE;
    }

    err = in->readInt32(&amrMode);
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

    value == 0 ? octetAligned = false : octetAligned = true;

    err = in->readInt32(&maxRedundancyMillis);
    if (err != NO_ERROR)
    {
        return err;
    }

    return NO_ERROR;
}

void AmrParams::setAmrMode(const int32_t mode)
{
    amrMode = mode;
}

int32_t AmrParams::getAmrMode()
{
    return amrMode;
}

void AmrParams::setOctetAligned(const bool enable)
{
    octetAligned = enable;
}

bool AmrParams::getOctetAligned()
{
    return octetAligned;
}

void AmrParams::setMaxRedundancyMillis(const int32_t value)
{
    maxRedundancyMillis = value;
}

int32_t AmrParams::getMaxRedundancyMillis()
{
    return maxRedundancyMillis;
}

void AmrParams::setDefaultAmrParams()
{
    amrMode = kAmrMode;
    octetAligned = kOctetAligned;
    maxRedundancyMillis = kMaxRedundancyMillis;
}

}  // namespace imsmedia

}  // namespace telephony

}  // namespace android
