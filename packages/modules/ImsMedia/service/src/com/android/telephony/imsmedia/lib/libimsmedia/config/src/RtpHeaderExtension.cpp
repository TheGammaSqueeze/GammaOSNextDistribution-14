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

#include <RtpHeaderExtension.h>

namespace android
{

namespace telephony
{

namespace imsmedia
{

RtpHeaderExtension::RtpHeaderExtension() :
        mLocalIdentifier(0),
        mExtensionData(nullptr),
        mExtensionDataSize(0)
{
}

RtpHeaderExtension::RtpHeaderExtension(const RtpHeaderExtension& extension) :
        mLocalIdentifier(extension.mLocalIdentifier),
        mExtensionData(nullptr),
        mExtensionDataSize(extension.mExtensionDataSize)
{
    if (mExtensionDataSize > 0)
    {
        mExtensionData = new uint8_t[mExtensionDataSize];
        memcpy(mExtensionData, extension.mExtensionData, mExtensionDataSize);
    }
}

RtpHeaderExtension::~RtpHeaderExtension()
{
    if (mExtensionData != nullptr)
    {
        delete[] mExtensionData;
        mExtensionData = nullptr;
    }
}

RtpHeaderExtension& RtpHeaderExtension::operator=(const RtpHeaderExtension& extension)
{
    if (this != &extension)
    {
        mLocalIdentifier = extension.mLocalIdentifier;
        this->setExtensionData(extension.mExtensionData, extension.mExtensionDataSize);
    }

    return *this;
}

bool RtpHeaderExtension::operator==(const RtpHeaderExtension& extension) const
{
    return (mLocalIdentifier == extension.mLocalIdentifier &&
            memcmp(mExtensionData, extension.mExtensionData, mExtensionDataSize) == 0);
}

bool RtpHeaderExtension::operator!=(const RtpHeaderExtension& extension) const
{
    return (mLocalIdentifier != extension.mLocalIdentifier ||
            memcmp(mExtensionData, extension.mExtensionData, mExtensionDataSize) != 0);
}

status_t RtpHeaderExtension::writeToParcel(Parcel* parcel) const
{
    status_t err;

    if (parcel == nullptr)
    {
        return BAD_VALUE;
    }

    err = parcel->writeInt32(mLocalIdentifier);

    if (err != NO_ERROR)
    {
        return err;
    }

    err = parcel->writeInt32(mExtensionDataSize);

    if (err != NO_ERROR)
    {
        return err;
    }

    void* dest = parcel->writeInplace(mExtensionDataSize);

    if (dest == nullptr)
    {
        return NO_MEMORY;
    }

    memcpy(dest, mExtensionData, mExtensionDataSize);
    return NO_ERROR;
}

status_t RtpHeaderExtension::readFromParcel(const Parcel* parcel)
{
    status_t err;

    if (parcel == nullptr)
    {
        return BAD_VALUE;
    }

    err = parcel->readInt32(&mLocalIdentifier);

    if (err != NO_ERROR)
    {
        return err;
    }

    err = parcel->readInt32(&mExtensionDataSize);

    if (err != NO_ERROR)
    {
        return err;
    }

    if (mExtensionDataSize != 0)
    {
        if (mExtensionData != nullptr)
        {
            delete[] mExtensionData;
            mExtensionData = nullptr;
        }

        mExtensionData = new uint8_t[mExtensionDataSize];
        const void* data = parcel->readInplace(mExtensionDataSize);

        if (data != nullptr)
        {
            memcpy(mExtensionData, data, mExtensionDataSize);
        }
    }

    return NO_ERROR;
}

int32_t RtpHeaderExtension::getLocalIdentifier()
{
    return mLocalIdentifier;
}

void RtpHeaderExtension::setLocalIdentifier(const int32_t id)
{
    mLocalIdentifier = id;
}

uint8_t* RtpHeaderExtension::getExtensionData() const
{
    return mExtensionData;
}

void RtpHeaderExtension::setExtensionData(const uint8_t* data, const int32_t size)
{
    if (mExtensionData != nullptr)
    {
        delete[] mExtensionData;
        mExtensionData = nullptr;
        mExtensionDataSize = 0;
    }

    if (data != nullptr)
    {
        mExtensionDataSize = size;
        mExtensionData = new uint8_t[mExtensionDataSize];
        memcpy(mExtensionData, data, mExtensionDataSize);
    }
}

int32_t RtpHeaderExtension::getExtensionDataSize()
{
    return mExtensionDataSize;
}
void RtpHeaderExtension::setExtensionDataSize(int32_t size)
{
    mExtensionDataSize = size;
}

}  // namespace imsmedia
}  // namespace telephony
}  // namespace android