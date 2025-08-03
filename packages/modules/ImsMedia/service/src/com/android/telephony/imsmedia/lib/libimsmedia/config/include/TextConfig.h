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

#ifndef TEXTCONFIG_H
#define TEXTCONFIG_H

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

/** Native representation of android.telephony.imsmedia.TextConfig */

/**
 * The class represents RTP (Real Time Control) configuration for text stream.
 */
class TextConfig : public RtpConfig
{
public:
    enum CodecType
    {
        /** codec is not defined */
        TEXT_CODEC_NONE = 0,
        /** T.140 enabled */
        TEXT_T140,
        /** T.140 and redundant codec enabled */
        TEXT_T140_RED,
    };

    TextConfig();
    TextConfig(TextConfig* config);
    TextConfig(TextConfig& config);
    virtual ~TextConfig();
    TextConfig& operator=(const TextConfig& config);
    bool operator==(const TextConfig& config) const;
    bool operator!=(const TextConfig& config) const;
    virtual status_t writeToParcel(Parcel* out) const;
    virtual status_t readFromParcel(const Parcel* in);
    void setCodecType(const int32_t codec);
    int32_t getCodecType();
    void setBitrate(const int32_t bitrate);
    int32_t getBitrate();
    void setRedundantPayload(const int8_t payload);
    int8_t getRedundantPayload();
    void setRedundantLevel(const int8_t level);
    int8_t getRedundantLevel();
    void setKeepRedundantLevel(const bool enable);
    bool getKeepRedundantLevel();

private:
    /** Codec type to set, RTT is using T.140 and redundant of T.140 with in payload number with
     * original T.140. The codec type can be choose to use only T.140 or use the redundant payload
     * together. */
    int32_t mCodecType;

    /** Bitrate for the encoding streaming in kbps unit*/
    int32_t mBitrate;

    /** The negotiated text redundancy payload number for RED payload */
    int8_t mRedundantPayload;

    /* The text redundancy level which is how many redundant payload of the T.140 payload is sent
     * every time packet sent */
    int8_t mRedundantLevel;

    /** The option for sending empty redundant payload when the codec type is sending T.140 and RED
     * payload */
    bool mKeepRedundantLevel;
};

}  // namespace imsmedia

}  // namespace telephony

}  // namespace android

#endif
