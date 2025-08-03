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

#include <AnbrMode.h>

namespace android
{

namespace telephony
{

namespace imsmedia
{

/** Native representation of android.telephony.imsmedia.AnbrMode */
AnbrMode::AnbrMode()
{
    anbrUplinkMode = 0;
    anbrDownlinkMode = 0;
}

AnbrMode::AnbrMode(AnbrMode& param)
{
    this->anbrUplinkMode = param.anbrUplinkMode;
    this->anbrDownlinkMode = param.anbrDownlinkMode;
}

AnbrMode::~AnbrMode() {}

AnbrMode& AnbrMode::operator=(const AnbrMode& param)
{
    if (this != &param)
    {
        this->anbrUplinkMode = param.anbrUplinkMode;
        this->anbrDownlinkMode = param.anbrDownlinkMode;
    }
    return *this;
}

bool AnbrMode::operator==(const AnbrMode& param) const
{
    return (this->anbrUplinkMode == param.anbrUplinkMode &&
            this->anbrDownlinkMode == param.anbrDownlinkMode);
}

bool AnbrMode::operator!=(const AnbrMode& param) const
{
    return (this->anbrUplinkMode != param.anbrUplinkMode ||
            this->anbrDownlinkMode != param.anbrDownlinkMode);
}

status_t AnbrMode::writeToParcel(Parcel* out) const
{
    status_t err;
    if (out == nullptr)
    {
        return BAD_VALUE;
    }

    err = out->writeInt32(anbrUplinkMode);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(anbrDownlinkMode);
    if (err != NO_ERROR)
    {
        return err;
    }
    return NO_ERROR;
}

status_t AnbrMode::readFromParcel(const Parcel* in)
{
    status_t err;
    if (in == nullptr)
    {
        return BAD_VALUE;
    }

    err = in->readInt32(&anbrUplinkMode);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&anbrDownlinkMode);
    if (err != NO_ERROR)
    {
        return err;
    }

    return NO_ERROR;
}

void AnbrMode::setAnbrUplinkCodecMode(const int32_t uplinkMode)
{
    anbrUplinkMode = uplinkMode;
}

int32_t AnbrMode::getAnbrUplinkCodecMode()
{
    return anbrUplinkMode;
}

void AnbrMode::setAnbrDownlinkCodecMode(const int32_t downlinkMode)
{
    anbrDownlinkMode = downlinkMode;
}

int32_t AnbrMode::getAnbrDownlinkCodecMode()
{
    return anbrDownlinkMode;
}

void AnbrMode::setDefaultAnbrMode()
{
    anbrUplinkMode = kAnbrUplinkMode;
    anbrDownlinkMode = kAnbrDownlinkMode;
}

}  // namespace imsmedia

}  // namespace telephony

}  // namespace android
