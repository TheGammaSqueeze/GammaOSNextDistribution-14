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

#include <RtpConfig.h>

namespace android
{

namespace telephony
{

namespace imsmedia
{

const android::String8 kClassNameRtcpConfig("android.telephony.imsmedia.RtcpConfig");
const android::String8 kClassNameRtpContextParams("android.telephony.imsmedia.RtpContextParams");
const android::String8 kClassNameAnbrMode("android.telephony.imsmedia.AnbrMode");

/** Native representation of android.telephony.imsmedia.RtpConfig */
RtpConfig::RtpConfig(int32_t mediaType) :
        type(mediaType),
        direction(0),
        accessNetwork(0),
        remoteAddress(""),
        remotePort(UNINITIALIZED_PORT),
        dscp(0),
        rxPayloadTypeNumber(0),
        txPayloadTypeNumber(0),
        samplingRateKHz(0)
{
}

RtpConfig::~RtpConfig() {}

RtpConfig::RtpConfig(RtpConfig* config)
{
    if (config == nullptr)
    {
        return;
    }
    type = config->type;
    direction = config->direction;
    accessNetwork = config->accessNetwork;
    remoteAddress = String8(config->remoteAddress.c_str());
    remotePort = config->remotePort;
    rtcpConfig = config->rtcpConfig;
    dscp = config->dscp;
    rxPayloadTypeNumber = config->rxPayloadTypeNumber;
    txPayloadTypeNumber = config->txPayloadTypeNumber;
    samplingRateKHz = config->samplingRateKHz;
    rtpContextParams = config->rtpContextParams;
    anbrMode = config->anbrMode;
}

RtpConfig::RtpConfig(const RtpConfig& config)
{
    type = config.type;
    direction = config.direction;
    accessNetwork = config.accessNetwork;
    remoteAddress = String8(config.remoteAddress.c_str());
    remotePort = config.remotePort;
    rtcpConfig = config.rtcpConfig;
    dscp = config.dscp;
    rxPayloadTypeNumber = config.rxPayloadTypeNumber;
    txPayloadTypeNumber = config.txPayloadTypeNumber;
    samplingRateKHz = config.samplingRateKHz;
    rtpContextParams = config.rtpContextParams;
    anbrMode = config.anbrMode;
}

RtpConfig& RtpConfig::operator=(const RtpConfig& config)
{
    if (this != &config)
    {
        type = config.type;
        direction = config.direction;
        accessNetwork = config.accessNetwork;
        remoteAddress = String8(config.remoteAddress.c_str());
        remotePort = config.remotePort;
        rtcpConfig = config.rtcpConfig;
        dscp = config.dscp;
        rxPayloadTypeNumber = config.rxPayloadTypeNumber;
        txPayloadTypeNumber = config.txPayloadTypeNumber;
        samplingRateKHz = config.samplingRateKHz;
        rtpContextParams = config.rtpContextParams;
        anbrMode = config.anbrMode;
    }
    return *this;
}

bool RtpConfig::operator==(const RtpConfig& config) const
{
    return (this->type == config.type && this->direction == config.direction &&
            this->accessNetwork == config.accessNetwork &&
            this->remoteAddress == config.remoteAddress && this->remotePort == config.remotePort &&
            this->rtcpConfig == config.rtcpConfig && this->dscp == config.dscp &&
            this->rxPayloadTypeNumber == config.rxPayloadTypeNumber &&
            this->txPayloadTypeNumber == config.txPayloadTypeNumber &&
            this->samplingRateKHz == config.samplingRateKHz &&
            this->rtpContextParams == config.rtpContextParams && this->anbrMode == config.anbrMode);
}

bool RtpConfig::operator!=(const RtpConfig& config) const
{
    return (this->type != config.type || this->direction != config.direction ||
            this->accessNetwork != config.accessNetwork ||
            this->remoteAddress != config.remoteAddress || this->remotePort != config.remotePort ||
            this->rtcpConfig != config.rtcpConfig || this->dscp != config.dscp ||
            this->rxPayloadTypeNumber != config.rxPayloadTypeNumber ||
            this->txPayloadTypeNumber != config.txPayloadTypeNumber ||
            this->samplingRateKHz != config.samplingRateKHz ||
            this->rtpContextParams != config.rtpContextParams || this->anbrMode != config.anbrMode);
}

status_t RtpConfig::writeToParcel(Parcel* out) const
{
    status_t err;
    if (out == nullptr)
    {
        return BAD_VALUE;
    }

    err = out->writeInt32(type);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(direction);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(accessNetwork);
    if (err != NO_ERROR)
    {
        return err;
    }

    String16 address(remoteAddress);
    err = out->writeString16(address);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(remotePort);
    if (err != NO_ERROR)
    {
        return err;
    }

    String16 className(kClassNameRtcpConfig);
    err = out->writeString16(className);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = rtcpConfig.writeToParcel(out);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeByte(dscp);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeByte(rxPayloadTypeNumber);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeByte(txPayloadTypeNumber);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeByte(samplingRateKHz);
    if (err != NO_ERROR)
    {
        return err;
    }

    String16 classNameRtpContextParams(kClassNameRtpContextParams);
    err = out->writeString16(classNameRtpContextParams);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = rtpContextParams.writeToParcel(out);
    if (err != NO_ERROR)
    {
        return err;
    }

    String16 classNameAnbr(kClassNameAnbrMode);
    err = out->writeString16(classNameAnbr);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = anbrMode.writeToParcel(out);
    if (err != NO_ERROR)
    {
        return err;
    }

    return NO_ERROR;
}

status_t RtpConfig::readFromParcel(const Parcel* in)
{
    status_t err;
    if (in == nullptr)
    {
        return BAD_VALUE;
    }

    err = in->readInt32(&type);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&direction);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&accessNetwork);
    if (err != NO_ERROR)
    {
        return err;
    }

    String16 address;
    err = in->readString16(&address);
    if (err == UNEXPECTED_NULL)
    {
        remoteAddress = String8("");
    }
    else if (err == NO_ERROR)
    {
        remoteAddress = String8(address.c_str());
    }
    else
    {
        return err;
    }

    err = in->readInt32(&remotePort);
    if (err != NO_ERROR)
    {
        return err;
    }

    String16 className;
    err = in->readString16(&className);
    if (err == NO_ERROR)
    {
        // read RtcpConfig
        err = rtcpConfig.readFromParcel(in);
        if (err != NO_ERROR)
        {
            return err;
        }
    }
    else if (err == UNEXPECTED_NULL)
    {
        rtcpConfig.setDefaultRtcpConfig();
    }
    else
    {
        return err;
    }

    err = in->readByte(&dscp);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readByte(&rxPayloadTypeNumber);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readByte(&txPayloadTypeNumber);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readByte(&samplingRateKHz);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readString16(&className);
    if (err == NO_ERROR)
    {
        err = rtpContextParams.readFromParcel(in);
    }
    else if (err == UNEXPECTED_NULL)
    {
        rtpContextParams.setDefaultConfig();
    }
    else
    {
        return err;
    }

    String16 classNameAnbr;
    err = in->readString16(&classNameAnbr);
    if (err == NO_ERROR)
    {
        // read AnbrMode
        err = anbrMode.readFromParcel(in);
        if (err != NO_ERROR)
        {
            return err;
        }
    }
    else if (err == UNEXPECTED_NULL)
    {
        anbrMode.setDefaultAnbrMode();
    }
    else
    {
        return err;
    }

    return NO_ERROR;
}

void RtpConfig::setMediaDirection(const int32_t direction)
{
    this->direction = direction;
}

int32_t RtpConfig::getMediaDirection()
{
    return direction;
}

void RtpConfig::setAccessNetwork(const int32_t network)
{
    accessNetwork = network;
}

int32_t RtpConfig::getAccessNetwork()
{
    return accessNetwork;
}

void RtpConfig::setRemoteAddress(const String8& address)
{
    this->remoteAddress = address;
}

String8 RtpConfig::getRemoteAddress()
{
    return remoteAddress;
}

void RtpConfig::setRemotePort(const int32_t port)
{
    this->remotePort = port;
}

int32_t RtpConfig::getRemotePort()
{
    return remotePort;
}

void RtpConfig::setRtcpConfig(const RtcpConfig& config)
{
    this->rtcpConfig = config;
}

RtcpConfig RtpConfig::getRtcpConfig()
{
    return rtcpConfig;
}

void RtpConfig::setDscp(const int8_t dscp)
{
    this->dscp = dscp;
}

int8_t RtpConfig::getDscp()
{
    return dscp;
}

void RtpConfig::setRxPayloadTypeNumber(const int8_t num)
{
    this->rxPayloadTypeNumber = num;
}

int8_t RtpConfig::getRxPayloadTypeNumber()
{
    return rxPayloadTypeNumber;
}

void RtpConfig::setTxPayloadTypeNumber(const int8_t num)
{
    this->txPayloadTypeNumber = num;
}

int8_t RtpConfig::getTxPayloadTypeNumber()
{
    return txPayloadTypeNumber;
}

void RtpConfig::setSamplingRateKHz(const int8_t sample)
{
    this->samplingRateKHz = sample;
}

int8_t RtpConfig::getSamplingRateKHz()
{
    return samplingRateKHz;
}

RtpContextParams RtpConfig::getRtpContextParams()
{
    return rtpContextParams;
}

void RtpConfig::setRtpContextParams(RtpContextParams& rtpContextParams)
{
    this->rtpContextParams = rtpContextParams;
}

void RtpConfig::setAnbrMode(const AnbrMode& anbrMode)
{
    this->anbrMode = anbrMode;
}

AnbrMode RtpConfig::getAnbrMode()
{
    return anbrMode;
}

}  // namespace imsmedia

}  // namespace telephony

}  // namespace android
