/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License") {
}

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

#include <VideoConfig.h>

namespace android
{

namespace telephony
{

namespace imsmedia
{

VideoConfig::VideoConfig() :
        RtpConfig(RtpConfig::TYPE_VIDEO)
{
    videoMode = CODEC_PROFILE_NONE;
    codecType = CODEC_AVC;
    framerate = DEFAULT_FRAMERATE;
    bitrate = DEFAULT_BITRATE;
    maxMtuBytes = 1500;
    codecProfile = CODEC_PROFILE_NONE;
    codecLevel = CODEC_LEVEL_NONE;
    intraFrameIntervalSec = 1;
    packetizationMode = 1;
    cameraId = 0;
    cameraZoom = 0;
    resolutionWidth = DEFAULT_RESOLUTION_WIDTH;
    resolutionHeight = DEFAULT_RESOLUTION_HEIGHT;
    pauseImagePath = String8("");
    deviceOrientationDegree = 0;
    cvoValue = CVO_DEFINE_NONE;
    rtcpFbTypes = RTP_FB_NONE;
}

VideoConfig::VideoConfig(VideoConfig* config) :
        RtpConfig(config)
{
    if (config == nullptr)
        return;
    videoMode = config->videoMode;
    codecType = config->codecType;
    framerate = config->framerate;
    bitrate = config->bitrate;
    maxMtuBytes = config->maxMtuBytes;
    codecProfile = config->codecProfile;
    codecLevel = config->codecLevel;
    intraFrameIntervalSec = config->intraFrameIntervalSec;
    packetizationMode = config->packetizationMode;
    cameraId = config->cameraId;
    cameraZoom = config->cameraZoom;
    resolutionWidth = config->resolutionWidth;
    resolutionHeight = config->resolutionHeight;
    pauseImagePath = config->pauseImagePath;
    deviceOrientationDegree = config->deviceOrientationDegree;
    cvoValue = config->cvoValue;
    rtcpFbTypes = config->rtcpFbTypes;
}

VideoConfig::VideoConfig(const VideoConfig& config) :
        RtpConfig(config)
{
    videoMode = config.videoMode;
    codecType = config.codecType;
    framerate = config.framerate;
    bitrate = config.bitrate;
    maxMtuBytes = config.maxMtuBytes;
    codecProfile = config.codecProfile;
    codecLevel = config.codecLevel;
    intraFrameIntervalSec = config.intraFrameIntervalSec;
    packetizationMode = config.packetizationMode;
    cameraId = config.cameraId;
    cameraZoom = config.cameraZoom;
    resolutionWidth = config.resolutionWidth;
    resolutionHeight = config.resolutionHeight;
    pauseImagePath = config.pauseImagePath;
    deviceOrientationDegree = config.deviceOrientationDegree;
    cvoValue = config.cvoValue;
    rtcpFbTypes = config.rtcpFbTypes;
}

VideoConfig::~VideoConfig() {}

VideoConfig& VideoConfig::operator=(const VideoConfig& config)
{
    if (this != &config)
    {
        RtpConfig::operator=(config);
        videoMode = config.videoMode;
        codecType = config.codecType;
        framerate = config.framerate;
        bitrate = config.bitrate;
        maxMtuBytes = config.maxMtuBytes;
        codecProfile = config.codecProfile;
        codecLevel = config.codecLevel;
        intraFrameIntervalSec = config.intraFrameIntervalSec;
        packetizationMode = config.packetizationMode;
        cameraId = config.cameraId;
        cameraZoom = config.cameraZoom;
        resolutionWidth = config.resolutionWidth;
        resolutionHeight = config.resolutionHeight;
        pauseImagePath = config.pauseImagePath;
        deviceOrientationDegree = config.deviceOrientationDegree;
        cvoValue = config.cvoValue;
        rtcpFbTypes = config.rtcpFbTypes;
    }
    return *this;
}

bool VideoConfig::operator==(const VideoConfig& config) const
{
    return (RtpConfig::operator==(config) && this->videoMode == config.videoMode &&
            this->codecType == config.codecType && this->framerate == config.framerate &&
            this->bitrate == config.bitrate && this->maxMtuBytes == config.maxMtuBytes &&
            this->codecProfile == config.codecProfile && this->codecLevel == config.codecLevel &&
            this->intraFrameIntervalSec == config.intraFrameIntervalSec &&
            this->packetizationMode == config.packetizationMode &&
            this->cameraId == config.cameraId && this->cameraZoom == config.cameraZoom &&
            this->resolutionWidth == config.resolutionWidth &&
            this->resolutionHeight == config.resolutionHeight &&
            this->pauseImagePath == config.pauseImagePath &&
            this->deviceOrientationDegree == config.deviceOrientationDegree &&
            this->cvoValue == config.cvoValue && this->rtcpFbTypes == config.rtcpFbTypes);
}

bool VideoConfig::operator!=(const VideoConfig& config) const
{
    return (RtpConfig::operator!=(config) || this->videoMode != config.videoMode ||
            this->codecType != config.codecType || this->framerate != config.framerate ||
            this->bitrate != config.bitrate || this->maxMtuBytes != config.maxMtuBytes ||
            this->codecProfile != config.codecProfile || this->codecLevel != config.codecLevel ||
            this->intraFrameIntervalSec != config.intraFrameIntervalSec ||
            this->packetizationMode != config.packetizationMode ||
            this->cameraId != config.cameraId || this->cameraZoom != config.cameraZoom ||
            this->resolutionWidth != config.resolutionWidth ||
            this->resolutionHeight != config.resolutionHeight ||
            this->pauseImagePath != config.pauseImagePath ||
            this->deviceOrientationDegree != config.deviceOrientationDegree ||
            this->cvoValue != config.cvoValue || this->rtcpFbTypes != config.rtcpFbTypes);
}

status_t VideoConfig::writeToParcel(Parcel* out) const
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

    err = out->writeInt32(videoMode);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(codecType);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(framerate);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(bitrate);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(maxMtuBytes);
    if (err != NO_ERROR)
    {
        return err;
    }
    err = out->writeInt32(codecProfile);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(codecLevel);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(intraFrameIntervalSec);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(packetizationMode);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(cameraId);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(cameraZoom);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(resolutionWidth);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(resolutionHeight);
    if (err != NO_ERROR)
    {
        return err;
    }

    String16 name(pauseImagePath);
    err = out->writeString16(name);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(deviceOrientationDegree);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(cvoValue);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = out->writeInt32(rtcpFbTypes);
    if (err != NO_ERROR)
    {
        return err;
    }

    return NO_ERROR;
}

status_t VideoConfig::readFromParcel(const Parcel* in)
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

    err = in->readInt32(&videoMode);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&codecType);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&framerate);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&bitrate);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&maxMtuBytes);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&codecProfile);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&codecLevel);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&intraFrameIntervalSec);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&packetizationMode);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&cameraId);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&cameraZoom);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&resolutionWidth);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&resolutionHeight);
    if (err != NO_ERROR)
    {
        return err;
    }

    String16 path;
    err = in->readString16(&path);
    if (err != NO_ERROR)
    {
        return err;
    }

    pauseImagePath = String8(path.c_str());

    err = in->readInt32(&deviceOrientationDegree);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&cvoValue);
    if (err != NO_ERROR)
    {
        return err;
    }

    err = in->readInt32(&rtcpFbTypes);
    if (err != NO_ERROR)
    {
        return err;
    }

    return NO_ERROR;
}

void VideoConfig::setVideoMode(const int32_t mode)
{
    videoMode = mode;
}

int32_t VideoConfig::getVideoMode()
{
    return videoMode;
}

void VideoConfig::setCodecType(const int32_t type)
{
    codecType = type;
}

int32_t VideoConfig::getCodecType()
{
    return codecType;
}

void VideoConfig::setFramerate(const int32_t framerate)
{
    this->framerate = framerate;
}

int32_t VideoConfig::getFramerate()
{
    return framerate;
}

void VideoConfig::setBitrate(const int32_t bitrate)
{
    this->bitrate = bitrate;
}

int32_t VideoConfig::getBitrate()
{
    return bitrate;
}

void VideoConfig::setMaxMtuBytes(const int32_t mtuBytes)
{
    maxMtuBytes = mtuBytes;
}

int32_t VideoConfig::getMaxMtuBytes()
{
    return maxMtuBytes;
}

void VideoConfig::setCodecProfile(const int32_t profile)
{
    codecProfile = profile;
}

int32_t VideoConfig::getCodecProfile()
{
    return codecProfile;
}

void VideoConfig::setCodecLevel(const int32_t level)
{
    codecLevel = level;
}

int32_t VideoConfig::getCodecLevel()
{
    return codecLevel;
}

void VideoConfig::setIntraFrameInterval(const int32_t interval)
{
    intraFrameIntervalSec = interval;
}

int32_t VideoConfig::getIntraFrameInterval()
{
    return intraFrameIntervalSec;
}

void VideoConfig::setPacketizationMode(const int32_t mode)
{
    packetizationMode = mode;
}

int32_t VideoConfig::getPacketizationMode()
{
    return packetizationMode;
}

void VideoConfig::setCameraId(const int32_t id)
{
    cameraId = id;
}

int32_t VideoConfig::getCameraId()
{
    return cameraId;
}

void VideoConfig::setCameraZoom(const int32_t zoom)
{
    cameraZoom = zoom;
}

int32_t VideoConfig::getCameraZoom()
{
    return cameraZoom;
}

void VideoConfig::setResolutionWidth(const int32_t width)
{
    resolutionWidth = width;
}

int32_t VideoConfig::getResolutionWidth()
{
    return resolutionWidth;
}

void VideoConfig::setResolutionHeight(const int32_t height)
{
    resolutionHeight = height;
}

int32_t VideoConfig::getResolutionHeight()
{
    return resolutionHeight;
}

void VideoConfig::setPauseImagePath(const android::String8& path)
{
    pauseImagePath = path;
}

android::String8 VideoConfig::getPauseImagePath()
{
    return pauseImagePath;
}

void VideoConfig::setDeviceOrientationDegree(const int32_t degree)
{
    deviceOrientationDegree = degree;
}

int32_t VideoConfig::getDeviceOrientationDegree()
{
    return deviceOrientationDegree;
}

void VideoConfig::setCvoValue(const int32_t value)
{
    cvoValue = value;
}

int32_t VideoConfig::getCvoValue()
{
    return cvoValue;
}

void VideoConfig::setRtcpFbType(const int32_t types)
{
    rtcpFbTypes = types;
}

int32_t VideoConfig::getRtcpFbType()
{
    return rtcpFbTypes;
}

}  // namespace imsmedia

}  // namespace telephony

}  // namespace android
