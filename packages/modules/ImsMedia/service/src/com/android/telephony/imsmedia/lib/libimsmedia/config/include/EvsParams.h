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

#ifndef EVSPARAMS_H
#define EVSPARAMS_H

#include <binder/Parcel.h>
#include <binder/Parcelable.h>
#include <binder/Status.h>
#include <stdint.h>

namespace android
{

namespace telephony
{

namespace imsmedia
{

/** Native representation of android.telephony.imsmedia.EvsParams */

/**
 * The class represents EVS (Enhanced Voice Services) codec parameters.
 */
class EvsParams : public Parcelable
{
public:
    enum EvsMode
    {
        /** 6.6 kbps for EVS AMR-WB IO */
        EVS_MODE_0 = 1 << 0,
        /** 8.855 kbps for AMR-WB IO */
        EVS_MODE_1 = 1 << 1,
        /** 12.65 kbps for AMR-WB IO */
        EVS_MODE_2 = 1 << 2,
        /** 14.25 kbps for AMR-WB IO */
        EVS_MODE_3 = 1 << 3,
        /** 15.85 kbps for AMR-WB IO */
        EVS_MODE_4 = 1 << 4,
        /** 18.25 kbps for AMR-WB IO */
        EVS_MODE_5 = 1 << 5,
        /** 19.85 kbps for AMR-WB IO */
        EVS_MODE_6 = 1 << 6,
        /** 23.05 kbps for AMR-WB IO */
        EVS_MODE_7 = 1 << 7,
        /** 23.85 kbps for AMR-WB IO */
        EVS_MODE_8 = 1 << 8,
        /** 5.9 kbps for EVS primary */
        EVS_MODE_9 = 1 << 9,
        /** 7.2 kbps for EVS primary */
        EVS_MODE_10 = 1 << 10,
        /** 8.0 kbps for EVS primary */
        EVS_MODE_11 = 1 << 11,
        /** 9.6 kbps for EVS primary */
        EVS_MODE_12 = 1 << 12,
        /** 13.2 kbps for EVS primary */
        EVS_MODE_13 = 1 << 13,
        /** 16.4 kbps for EVS primary */
        EVS_MODE_14 = 1 << 14,
        /** 24.4 kbps for EVS primary */
        EVS_MODE_15 = 1 << 15,
        /** 32.0 kbps for EVS primary */
        EVS_MODE_16 = 1 << 16,
        /** 48.0 kbps for EVS primary */
        EVS_MODE_17 = 1 << 17,
        /** 64.0 kbps for EVS primary */
        EVS_MODE_18 = 1 << 18,
        /** 96.0 kbps for EVS primary */
        EVS_MODE_19 = 1 << 19,
        /** 128.0 kbps for EVS primary */
        EVS_MODE_20 = 1 << 20,
    };

    enum EvsBandwidth
    {
        EVS_BAND_NONE = 0,
        EVS_NARROW_BAND = 1 << 0,
        EVS_WIDE_BAND = 1 << 1,
        EVS_SUPER_WIDE_BAND = 1 << 2,
        EVS_FULL_BAND = 1 << 3,
    };

    EvsParams();
    EvsParams(EvsParams& params);
    virtual ~EvsParams();
    EvsParams& operator=(const EvsParams& param);
    bool operator==(const EvsParams& param) const;
    bool operator!=(const EvsParams& param) const;
    virtual status_t writeToParcel(Parcel* parcel) const;
    virtual status_t readFromParcel(const Parcel* in);
    void setEvsBandwidth(const int32_t EvsBandwidth);
    int32_t getEvsBandwidth();
    void setEvsMode(const int32_t EvsMode);
    int32_t getEvsMode();
    void setChannelAwareMode(int8_t channelAwMode);
    int8_t getChannelAwareMode();
    void setUseHeaderFullOnly(const bool enable);
    bool getUseHeaderFullOnly();
    void setCodecModeRequest(int8_t cmr);
    int8_t getCodecModeRequest();
    void setDefaultEvsParams();

private:
    /** bw: EVS codec bandwidth range */
    int32_t evsBandwidth;
    /** mode-set: EVS codec mode to represent the bit rate */
    int32_t evsMode;
    /**
     * ch-aw-recv: Channel aware mode for the receive direction. Permissible values
     * are -1, 0, 2, 3, 5, and 7. If -1, channel-aware mode is disabled in the
     * session for the receive direction. If 0 or not present, partial redundancy
     * (channel-aware mode) is not used at the start of the session for the receive
     * direction. If positive (2, 3, 5, or 7), partial redundancy (channel-aware
     * mode) is used at the start of the session for the receive direction using the
     * value as the offset, See 3GPP TS 26.445 section 4.4.5
     */
    int8_t channelAwareMode;
    /**
     * hf-only: Header full only is used for the outgoing/incoming packets. If it's true then
     * the session shall support header full format only else the session could
     * support both header full format and compact format.
     */
    bool useHeaderFullOnly;
    /**
     * cmr: Codec mode request is used to request the speech codec encoder of the
     * other party to set the frame type index of speech mode via RTP header, See
     * 3GPP TS 26.445 section A.3. Allowed values are -1, 0 and 1.
     */
    int8_t codecModeRequest;

    // Default EvsParams
    const int32_t kBandwidth = EvsParams::EVS_BAND_NONE;
    const int32_t kEvsMode = 0;
    const int8_t kChannelAwareMode = 0;
    const bool kUseHeaderFullOnly = false;
    const int8_t kcodecModeRequest = 0;
};

}  // namespace imsmedia

}  // namespace telephony

}  // namespace android

#endif
