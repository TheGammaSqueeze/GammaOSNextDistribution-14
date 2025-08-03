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

#ifndef VIDEOCONFIG_H
#define VIDEOCONFIG_H

#include <stdint.h>
#include <binder/Parcel.h>
#include <binder/Parcelable.h>
#include <binder/Status.h>
#include <RtpConfig.h>

namespace android
{

namespace telephony
{

namespace imsmedia
{

using namespace android;

#define CVO_DEFINE_NONE           (-1)
#define DEFAULT_FRAMERATE         (15)
#define DEFAULT_BITRATE           (384)
#define DEFAULT_RESOLUTION_WIDTH  (480)
#define DEFAULT_RESOLUTION_HEIGHT (640)

/** Native representation of android.telephony.imsmedia.VideoConfig */

/**
 * The class represents RTP (Real Time Protocol) configuration for video stream.
 */
class VideoConfig : public RtpConfig
{
public:
    enum CodecType
    {
        CODEC_AVC = 1 << 5,
        CODEC_HEVC = 1 << 6,
    };

    enum VideoMode
    {
        VIDEO_MODE_PREVIEW = 0,
        VIDEO_MODE_RECORDING,
        VIDEO_MODE_PAUSE_IMAGE,
    };

    enum VideoCodecProfile
    {
        /* Codec profile is not specified */
        CODEC_PROFILE_NONE = 0,
        /* AVC Codec Baseline profile */
        AVC_PROFILE_BASELINE = 1,
        /* AVC Codec Constrained Baseline profile */
        AVC_PROFILE_CONSTRAINED_BASELINE = 0x00010000,
        /* AVC Codec Constrained High profile */
        AVC_PROFILE_CONSTRAINED_HIGH = 0x00080000,
        /* AVC Codec High profile */
        AVC_PROFILE_HIGH = 0x00000008,
        /* AVC Codec Main profile */
        AVC_PROFILE_MAIN = 0x00000002,
        /* HEVC Codec Main profile */
        HEVC_PROFILE_MAIN = 0x00000001,
        /* HEVC Codec Main 10 profile */
        HEVC_PROFILE_MAIN10 = 0x00000002,
    };

    enum VideoCodecLevel
    {
        /* Video codec level is not specified */
        CODEC_LEVEL_NONE = 0,
        /* AVC Codec level 1 : 176x144, 64kbps, 15.0fps for QCIF */
        AVC_LEVEL_1 = 1,
        /* AVC Codec level 1b : 176x144, 128kbps, 15.0fps for QCIF */
        AVC_LEVEL_1B = 0x00000002,
        /* AVC Codec level 1.1 : 352x288, 192kbps, 10.0fps for QVGA, 7.5fps for CIF */
        AVC_LEVEL_11 = 0x00000004,
        /* AVC Codec level 1.2 : 352x288, 384kbps, 20.0fps for QVGA, 15.1fps for CIF */
        AVC_LEVEL_12 = 0x00000008,
        /* AVC Codec level 1.3 : 352x288, 768kbps, 39.6fps for QVGA, 30.0fps for CIF */
        AVC_LEVEL_13 = 0x00000010,
        /* AVC Codec level 2.0 : 352x288, 2Mbps */
        AVC_LEVEL_2 = 0x00000020,
        /* AVC Codec level 2.1 : 704x288, 352x576, 4Mbps */
        AVC_LEVEL_21 = 0x00000040,
        /* AVC Codec level 2.2 : 720x576, 4Mbps */
        AVC_LEVEL_22 = 0x00000080,
        /* AVC Codec level 3.0 : 720x576, 10Mbps */
        AVC_LEVEL_3 = 0x00000100,
        /* AVC Codec level 3.1 : 1280x720, 14Mbps */
        AVC_LEVEL_31 = 0x00000200,
        /* HEVC Codec high tier level 1 */
        HEVC_HIGHTIER_LEVEL_1 = 0x00000002,
        /* HEVC Codec high tier level 2 */
        HEVC_HIGHTIER_LEVEL_2 = 0x00000008,
        /* HEVC Codec high tier level 2.1 */
        HEVC_HIGHTIER_LEVEL_21 = 0x00000020,
        /* HEVC Codec high tier level 3 */
        HEVC_HIGHTIER_LEVEL_3 = 0x00000080,
        /* HEVC Codec high tier level 3.1 */
        HEVC_HIGHTIER_LEVEL_31 = 0x00000200,
        /* HEVC Codec high tier level 4 */
        HEVC_HIGHTIER_LEVEL_4 = 0x00000800,
        /* HEVC Codec high tier level 4.1 */
        HEVC_HIGHTIER_LEVEL_41 = 0x00002000,
        /* HEVC Codec main tier level 1 */
        HEVC_MAINTIER_LEVEL_1 = 0x00000001,
        /* HEVC Codec main tier level 2 */
        HEVC_MAINTIER_LEVEL_2 = 0x00000004,
        /* HEVC Codec main tier level 2.1 */
        HEVC_MAINTIER_LEVEL_21 = 0x00000010,
        /* HEVC Codec main tier level 3 */
        HEVC_MAINTIER_LEVEL_3 = 0x00000040,
        /* HEVC Codec main tier level 3.1 */
        HEVC_MAINTIER_LEVEL_31 = 0x00000100,
        /* HEVC Codec main tier level 4 */
        HEVC_MAINTIER_LEVEL_4 = 0x00000400,
        /* HEVC Codec main tier level 4.1 */
        HEVC_MAINTIER_LEVEL_41 = 0x00001000,
    };

    enum VideoPacketizationMode
    {
        MODE_SINGLE_NAL_UNIT = 0,
        MODE_NON_INTERLEAVED,
        MODE_INTERLEAVED,
    };

    enum RtcpFbType
    {
        /* Rtcp fb type is not set*/
        RTP_FB_NONE = 0,
        /**
         * The Generic NACK(Negative Acknowledgement) message identified by RTCP packet type
         * value PT=RTPFB and FMT=1. RFC 4585.
         */
        RTP_FB_NACK = 1 << 0,
        /**
         * The Temporary Maximum Media Stream Bit Rate Request is identified by
         * RTCP packet type value PT=RTPFB and FMT=3. RFC 5104.
         */
        RTP_FB_TMMBR = 1 << 1,
        /**
         * The Temporary Maximum Media Stream Bit Rate Notification is identified
         * by RTCP packet type value PT=RTPFB and FMT=4. RFC 5104.
         */
        RTP_FB_TMMBN = 1 << 2,
        /**
         * Picture Loss Indication. The PLI FB message is identified
         * by RTCP packet type value PT=PSFB and FMT=1. RFC 4585.
         */
        PSFB_PLI = 1 << 3,
        /**
         * Full Intra Request. The FIR message is identified by RTCP packet type
         * value PT=PSFB and FMT=4. RFC 5104.
         */
        PSFB_FIR = 1 << 4,
    };

    VideoConfig();
    VideoConfig(VideoConfig* config);
    VideoConfig(const VideoConfig& config);
    virtual ~VideoConfig();
    VideoConfig& operator=(const VideoConfig& config);
    bool operator==(const VideoConfig& config) const;
    bool operator!=(const VideoConfig& config) const;
    virtual status_t writeToParcel(Parcel* out) const;
    virtual status_t readFromParcel(const Parcel* in);
    void setVideoMode(const int32_t mode);
    int32_t getVideoMode();
    void setCodecType(const int32_t type);
    int32_t getCodecType();
    void setFramerate(const int32_t framerate);
    int32_t getFramerate();
    void setBitrate(const int32_t bitrate);
    int32_t getBitrate();
    void setCodecProfile(const int32_t profile);
    int32_t getCodecProfile();
    void setCodecLevel(const int32_t level);
    int32_t getCodecLevel();
    void setIntraFrameInterval(const int32_t interval);
    int32_t getIntraFrameInterval();
    void setPacketizationMode(const int32_t mode);
    int32_t getPacketizationMode();
    int32_t getMaxMtuBytes();
    void setMaxMtuBytes(const int32_t mtuBytes);
    void setCameraId(const int32_t id);
    int32_t getCameraId();
    void setCameraZoom(const int32_t zoom);
    int32_t getCameraZoom();
    void setResolutionWidth(const int32_t width);
    int32_t getResolutionWidth();
    void setResolutionHeight(const int32_t height);
    int32_t getResolutionHeight();
    void setPauseImagePath(const String8& path);
    String8 getPauseImagePath();
    void setDeviceOrientationDegree(const int32_t degree);
    int32_t getDeviceOrientationDegree();
    void setCvoValue(const int32_t value);
    int32_t getCvoValue();
    void setRtcpFbType(const int32_t types);
    int32_t getRtcpFbType();

protected:
    /* Sets video mode. */
    int32_t videoMode;
    /* Video Codec type. It can be H.264, HEVC codec. */
    int32_t codecType;
    /* Video frame rate in encoding streaming */
    int32_t framerate;
    /* Video bitrate of encoding streaming */
    int32_t bitrate;
    /* MaxMtuBytes of RTP packet will be defined here */
    int32_t maxMtuBytes;
    /* Video codec encoder profile */
    int32_t codecProfile;
    /* Video codec encoder level */
    int32_t codecLevel;
    /* Video codec encoder interval of intra-frames in seconds */
    int32_t intraFrameIntervalSec;
    /* Video Rtp packetization mode. The 0 means Single NAL unit mode, 1 means non-interleaved
     * mode. And Interleaved mode is not supported. Check RFC 6184.
     */
    int32_t packetizationMode;
    /* An identification of camera device to use */
    int32_t cameraId;
    /* A level of zoom of camera device. It can be 0 to 10. */
    int32_t cameraZoom;
    /* The width of resolution in transmit streaming */
    int32_t resolutionWidth;
    /* The height of resolution in transmit streaming */
    int32_t resolutionHeight;
    /* The path of jpg image for video mode VIDEO_MODE_PAUSE_IMAGE. */
    String8 pauseImagePath;
    /* The device orientation from sensor captured as degree unit */
    int32_t deviceOrientationDegree;
    /* The value to identify CVO RTP header extension features is enabled by the SDP negotiation.
     * When the flag is set, MediaStack sends CVO RTP extension byte in the RTP header when the
     * Video IDR frame is sent. if this value is -1, CVO is disabled, and non zero means CVO enabled
     * with specified offset. Check RFC 5285 */
    int32_t cvoValue;
    /* The RTPFB, PSFB configuration with RTCP Protocol */
    int32_t rtcpFbTypes;
};

}  // namespace imsmedia

}  // namespace telephony

}  // namespace android

#endif
