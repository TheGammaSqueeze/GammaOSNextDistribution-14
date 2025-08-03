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

#include <TextConfig.h>

namespace android
{

namespace telephony
{

namespace imsmedia
{

TextConfig::TextConfig() :
        RtpConfig(RtpConfig::TYPE_TEXT)
{
    this->mCodecType = 0;
    this->mBitrate = 0;
    this->mRedundantPayload = 0;
    this->mRedundantLevel = 0;
    this->mKeepRedundantLevel = false;
}

TextConfig::TextConfig(TextConfig* config) :
        RtpConfig(config)
{
    if (config != nullptr)
    {
        this->mCodecType = config->mCodecType;
        this->mBitrate = config->mBitrate;
        this->mRedundantPayload = config->mRedundantPayload;
        this->mRedundantLevel = config->mRedundantLevel;
        this->mKeepRedundantLevel = config->mKeepRedundantLevel;
    }
}

TextConfig::TextConfig(TextConfig& config) :
        RtpConfig(config)
{
    this->mCodecType = config.mCodecType;
    this->mBitrate = config.mBitrate;
    this->mRedundantPayload = config.mRedundantPayload;
    this->mRedundantLevel = config.mRedundantLevel;
    this->mKeepRedundantLevel = config.mKeepRedundantLevel;
}

TextConfig::~TextConfig() {}

TextConfig& TextConfig::operator=(const TextConfig& config)
{
    if (this != &config)
    {
        RtpConfig::operator=(config);
        this->mCodecType = config.mCodecType;
        this->mBitrate = config.mBitrate;
        this->mRedundantPayload = config.mRedundantPayload;
        this->mRedundantLevel = config.mRedundantLevel;
        this->mKeepRedundantLevel = config.mKeepRedundantLevel;
    }
    return *this;
}

bool TextConfig::operator==(const TextConfig& config) const
{
    return (RtpConfig::operator==(config) && mCodecType == config.mCodecType &&
            mBitrate == config.mBitrate && mRedundantPayload == config.mRedundantPayload &&
            mRedundantLevel == config.mRedundantLevel &&
            mKeepRedundantLevel == config.mKeepRedundantLevel);
}

bool TextConfig::operator!=(const TextConfig& config) const
{
    return (RtpConfig::operator!=(config) || mCodecType == config.mCodecType ||
            mBitrate == config.mBitrate || mRedundantPayload == config.mRedundantPayload ||
            mRedundantLevel == config.mRedundantLevel ||
            mKeepRedundantLevel == config.mKeepRedundantLevel);
}

status_t TextConfig::writeToParcel(Parcel* out) const
{
    status_t err;
    if (out == nullptr)
    {
        return BAD_VALUE;
    }

    err = RtpConfig::writeToParcel(out);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(mCodecType);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(mBitrate);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeByte(mRedundantPayload);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeByte(mRedundantLevel);
    if (err != NO_ERROR)
    {
        return err;
    }

    int32_t value = 0;
    mKeepRedundantLevel ? value = 1 : value = 0;
    err = out->writeInt32(value);
    if (err != NO_ERROR)
    {
        return err;
    }

    return NO_ERROR;
}

status_t TextConfig::readFromParcel(const Parcel* in)
{
    status_t err;
    if (in == nullptr)
    {
        return BAD_VALUE;
    }

    err = RtpConfig::readFromParcel(in);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&mCodecType);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&mBitrate);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readByte(&mRedundantPayload);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readByte(&mRedundantLevel);
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

    value == 0 ? mKeepRedundantLevel = false : mKeepRedundantLevel = true;

    return NO_ERROR;
}

void TextConfig::setCodecType(const int32_t codec)
{
    mCodecType = codec;
}

int32_t TextConfig::getCodecType()
{
    return mCodecType;
}

void TextConfig::setBitrate(const int32_t bitrate)
{
    mBitrate = bitrate;
}

int32_t TextConfig::getBitrate()
{
    return mBitrate;
}

void TextConfig::setRedundantPayload(const int8_t payload)
{
    mRedundantPayload = payload;
}

int8_t TextConfig::getRedundantPayload()
{
    return mRedundantPayload;
}

void TextConfig::setRedundantLevel(const int8_t level)
{
    mRedundantLevel = level;
}

int8_t TextConfig::getRedundantLevel()
{
    return mRedundantLevel;
}

void TextConfig::setKeepRedundantLevel(const bool enable)
{
    mKeepRedundantLevel = enable;
}

bool TextConfig::getKeepRedundantLevel()
{
    return mKeepRedundantLevel;
}

}  // namespace imsmedia

}  // namespace telephony

}  // namespace android