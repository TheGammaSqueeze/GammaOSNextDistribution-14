#include <RtcpConfig.h>

namespace android
{

namespace telephony
{

namespace imsmedia
{

/** Native representation of android.telephony.imsmedia.RtcpConfig */
RtcpConfig::RtcpConfig() :
        canonicalName(""),
        transmitPort(0),
        intervalSec(0),
        rtcpXrBlockTypes(0)
{
}

RtcpConfig::RtcpConfig(const RtcpConfig& config)
{
    this->canonicalName = String8(config.canonicalName.c_str());
    this->transmitPort = config.transmitPort;
    this->intervalSec = config.intervalSec;
    this->rtcpXrBlockTypes = config.rtcpXrBlockTypes;
}

RtcpConfig::~RtcpConfig() {}

RtcpConfig& RtcpConfig::operator=(const RtcpConfig& config)
{
    if (this != &config)
    {
        this->canonicalName = String8(config.canonicalName.c_str());
        this->transmitPort = config.transmitPort;
        this->intervalSec = config.intervalSec;
        this->rtcpXrBlockTypes = config.rtcpXrBlockTypes;
    }
    return *this;
}

bool RtcpConfig::operator==(const RtcpConfig& config) const
{
    return (this->canonicalName == config.canonicalName &&
            this->transmitPort == config.transmitPort && this->intervalSec == config.intervalSec &&
            this->rtcpXrBlockTypes == config.rtcpXrBlockTypes);
}

bool RtcpConfig::operator!=(const RtcpConfig& config) const
{
    return (this->canonicalName != config.canonicalName ||
            this->transmitPort != config.transmitPort || this->intervalSec != config.intervalSec ||
            this->rtcpXrBlockTypes != config.rtcpXrBlockTypes);
}

status_t RtcpConfig::writeToParcel(Parcel* out) const
{
    status_t err;
    if (out == nullptr)
    {
        return BAD_VALUE;
    }

    String16 name(canonicalName);
    err = out->writeString16(name);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(transmitPort);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(intervalSec);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(rtcpXrBlockTypes);
    if (err != NO_ERROR)
    {
        return err;
    }

    return NO_ERROR;
}

status_t RtcpConfig::readFromParcel(const Parcel* in)
{
    status_t err;
    if (in == nullptr)
    {
        return BAD_VALUE;
    }

    String16 name;
    err = in->readString16(&name);
    if (err != NO_ERROR)
    {
        return err;
    }

    canonicalName = String8(name.c_str());

    err = in->readInt32(&transmitPort);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&intervalSec);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&rtcpXrBlockTypes);
    if (err != NO_ERROR)
    {
        return err;
    }

    return NO_ERROR;
}

void RtcpConfig::setCanonicalName(const String8& name)
{
    canonicalName = name;
}

String8 RtcpConfig::getCanonicalName()
{
    return canonicalName;
}

void RtcpConfig::setTransmitPort(const int32_t port)
{
    transmitPort = port;
}

int32_t RtcpConfig::getTransmitPort()
{
    return transmitPort;
}

void RtcpConfig::setIntervalSec(const int32_t interval)
{
    intervalSec = interval;
}

int32_t RtcpConfig::getIntervalSec()
{
    return intervalSec;
}

void RtcpConfig::setRtcpXrBlockTypes(const int32_t type)
{
    rtcpXrBlockTypes = type;
}

int32_t RtcpConfig::getRtcpXrBlockTypes()
{
    return rtcpXrBlockTypes;
}

void RtcpConfig::setDefaultRtcpConfig()
{
    canonicalName = android::String8("");
    transmitPort = kTransmitPort;
    intervalSec = kIntervalSec;
    rtcpXrBlockTypes = kRtcpXrBlockTypes;
}

}  // namespace imsmedia

}  // namespace telephony

}  // namespace android