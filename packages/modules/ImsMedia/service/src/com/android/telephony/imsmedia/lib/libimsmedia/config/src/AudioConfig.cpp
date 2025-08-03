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

#include <AudioConfig.h>

namespace android
{

namespace telephony
{

namespace imsmedia
{

const android::String8 kClassNameAmrParams("android.telephony.imsmedia.AmrParams");
const android::String8 kClassNameEvsParams("android.telephony.imsmedia.EvsParams");

AudioConfig::AudioConfig() :
        RtpConfig(RtpConfig::TYPE_AUDIO)
{
    pTimeMillis = 0;
    maxPtimeMillis = 0;
    dtxEnabled = false;
    codecType = 0;
    mDtmfTxPayloadTypeNumber = 0;
    mDtmfRxPayloadTypeNumber = 0;
    dtmfsamplingRateKHz = 0;
}

AudioConfig::AudioConfig(AudioConfig* config) :
        RtpConfig(config)
{
    if (config != nullptr)
    {
        pTimeMillis = config->pTimeMillis;
        maxPtimeMillis = config->maxPtimeMillis;
        dtxEnabled = config->dtxEnabled;
        codecType = config->codecType;
        mDtmfTxPayloadTypeNumber = config->mDtmfTxPayloadTypeNumber;
        mDtmfRxPayloadTypeNumber = config->mDtmfRxPayloadTypeNumber;
        dtmfsamplingRateKHz = config->dtmfsamplingRateKHz;
        amrParams = config->amrParams;
        evsParams = config->evsParams;
    }
}

AudioConfig::AudioConfig(const AudioConfig& config) :
        RtpConfig(config)
{
    pTimeMillis = config.pTimeMillis;
    maxPtimeMillis = config.maxPtimeMillis;
    dtxEnabled = config.dtxEnabled;
    codecType = config.codecType;
    mDtmfTxPayloadTypeNumber = config.mDtmfTxPayloadTypeNumber;
    mDtmfRxPayloadTypeNumber = config.mDtmfRxPayloadTypeNumber;
    dtmfsamplingRateKHz = config.dtmfsamplingRateKHz;
    amrParams = config.amrParams;
    evsParams = config.evsParams;
}

AudioConfig::~AudioConfig() {}

AudioConfig& AudioConfig::operator=(const AudioConfig& config)
{
    if (this != &config)
    {
        RtpConfig::operator=(config);
        pTimeMillis = config.pTimeMillis;
        maxPtimeMillis = config.maxPtimeMillis;
        dtxEnabled = config.dtxEnabled;
        codecType = config.codecType;
        mDtmfTxPayloadTypeNumber = config.mDtmfTxPayloadTypeNumber;
        mDtmfRxPayloadTypeNumber = config.mDtmfRxPayloadTypeNumber;
        dtmfsamplingRateKHz = config.dtmfsamplingRateKHz;
        amrParams = config.amrParams;
        evsParams = config.evsParams;
    }
    return *this;
}

bool AudioConfig::operator==(const AudioConfig& config) const
{
    return (RtpConfig::operator==(config) && this->pTimeMillis == config.pTimeMillis &&
            this->maxPtimeMillis == config.maxPtimeMillis &&
            this->dtxEnabled == config.dtxEnabled && this->codecType == config.codecType &&
            this->mDtmfTxPayloadTypeNumber == config.mDtmfTxPayloadTypeNumber &&
            this->mDtmfRxPayloadTypeNumber == config.mDtmfRxPayloadTypeNumber &&
            this->dtmfsamplingRateKHz == config.dtmfsamplingRateKHz &&
            this->amrParams == config.amrParams && this->evsParams == config.evsParams);
}

bool AudioConfig::operator!=(const AudioConfig& config) const
{
    return (RtpConfig::operator!=(config) || this->pTimeMillis != config.pTimeMillis ||
            this->maxPtimeMillis != config.maxPtimeMillis ||
            this->dtxEnabled != config.dtxEnabled || this->codecType != config.codecType ||
            this->mDtmfTxPayloadTypeNumber != config.mDtmfTxPayloadTypeNumber ||
            this->mDtmfRxPayloadTypeNumber != config.mDtmfRxPayloadTypeNumber ||
            this->dtmfsamplingRateKHz != config.dtmfsamplingRateKHz ||
            this->amrParams != config.amrParams || this->evsParams != config.evsParams);
}

status_t AudioConfig::writeToParcel(Parcel* out) const
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

    err = out->writeByte(pTimeMillis);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(maxPtimeMillis);
    if (err != NO_ERROR)
    {
        return err;
    }

    int32_t value = 0;
    dtxEnabled ? value = 1 : value = 0;
    err = out->writeInt32(value);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(codecType);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeByte(mDtmfTxPayloadTypeNumber);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeByte(mDtmfRxPayloadTypeNumber);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeByte(dtmfsamplingRateKHz);
    if (err != NO_ERROR)
    {
        return err;
    }

    String16 classNameAmr(kClassNameAmrParams);
    err = out->writeString16(classNameAmr);
    if (err != NO_ERROR)
    {
        return err;
    }

    // err = out->writeParcelable(amrParams);
    err = amrParams.writeToParcel(out);
    if (err != NO_ERROR)
    {
        return err;
    }

    String16 classNameEvs(kClassNameEvsParams);
    err = out->writeString16(classNameEvs);
    if (err != NO_ERROR)
    {
        return err;
    }

    // err = out->writeParcelable(evsParams);
    err = evsParams.writeToParcel(out);
    if (err != NO_ERROR)
    {
        return err;
    }

    return NO_ERROR;
}

status_t AudioConfig::readFromParcel(const Parcel* in)
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

    err = in->readByte(&pTimeMillis);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&maxPtimeMillis);
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

    value == 0 ? dtxEnabled = false : dtxEnabled = true;

    err = in->readInt32(&codecType);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readByte(&mDtmfTxPayloadTypeNumber);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readByte(&mDtmfRxPayloadTypeNumber);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readByte(&dtmfsamplingRateKHz);
    if (err != NO_ERROR)
    {
        return err;
    }

    String16 className;
    err = in->readString16(&className);

    if (err == NO_ERROR)
    {
        // read AmrParams
        err = amrParams.readFromParcel(in);
        if ((codecType == CODEC_AMR || codecType == CODEC_AMR_WB) && err != NO_ERROR)
        {
            return err;
        }
    }
    else if (err == UNEXPECTED_NULL)
    {
        amrParams.setDefaultAmrParams();
    }
    else
    {
        return err;
    }

    err = in->readString16(&className);
    if (err == NO_ERROR)
    {
        // read EvsParams
        err = evsParams.readFromParcel(in);
        if (codecType == CODEC_EVS && err != NO_ERROR)
        {
            return err;
        }
    }
    else if (err == UNEXPECTED_NULL)
    {
        evsParams.setDefaultEvsParams();
    }
    else
    {
        return err;
    }

    return NO_ERROR;
}

void AudioConfig::setPtimeMillis(const int8_t ptime)
{
    pTimeMillis = ptime;
}

int8_t AudioConfig::getPtimeMillis()
{
    return pTimeMillis;
}

void AudioConfig::setMaxPtimeMillis(const int32_t maxPtime)
{
    maxPtimeMillis = maxPtime;
}

int32_t AudioConfig::getMaxPtimeMillis()
{
    return maxPtimeMillis;
}

void AudioConfig::setDtxEnabled(const bool enable)
{
    dtxEnabled = enable;
}

bool AudioConfig::getDtxEnabled()
{
    return dtxEnabled;
}

void AudioConfig::setCodecType(const int32_t type)
{
    codecType = type;
}

int32_t AudioConfig::getCodecType()
{
    return codecType;
}

void AudioConfig::setTxDtmfPayloadTypeNumber(const int8_t num)
{
    mDtmfTxPayloadTypeNumber = num;
}

void AudioConfig::setRxDtmfPayloadTypeNumber(const int8_t num)
{
    mDtmfRxPayloadTypeNumber = num;
}

int8_t AudioConfig::getTxDtmfPayloadTypeNumber()
{
    return mDtmfTxPayloadTypeNumber;
}

int8_t AudioConfig::getRxDtmfPayloadTypeNumber()
{
    return mDtmfRxPayloadTypeNumber;
}

void AudioConfig::setDtmfsamplingRateKHz(const int8_t sampling)
{
    dtmfsamplingRateKHz = sampling;
}

int8_t AudioConfig::getDtmfsamplingRateKHz()
{
    return dtmfsamplingRateKHz;
}

void AudioConfig::setAmrParams(const AmrParams& param)
{
    amrParams = param;
}

AmrParams AudioConfig::getAmrParams()
{
    return amrParams;
}

void AudioConfig::setEvsParams(const EvsParams& param)
{
    evsParams = param;
}

EvsParams AudioConfig::getEvsParams()
{
    return evsParams;
}

}  // namespace imsmedia

}  // namespace telephony

}  // namespace android
