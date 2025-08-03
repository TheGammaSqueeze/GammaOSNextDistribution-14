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

#ifndef AMRPARAMS_H
#define AMRPARAMS_H

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

/** Native representation of android.telephony.imsmedia.AmrParams */

/**
 * The class represents AMR (Adaptive Multi-Rate) codec parameters.
 */
class AmrParams : public Parcelable
{
public:
    enum AmrMode
    {
        /** 4.75 kbps for AMR / 6.6 kbps for AMR-WB */
        AMR_MODE_0 = 1 << 0,
        /** 5.15 kbps for AMR / 8.855 kbps for AMR-WB */
        AMR_MODE_1 = 1 << 1,
        /** 5.9 kbps for AMR / 12.65 kbps for AMR-WB */
        AMR_MODE_2 = 1 << 2,
        /** 6.7 kbps for AMR / 14.25 kbps for AMR-WB */
        AMR_MODE_3 = 1 << 3,
        /** 7.4 kbps for AMR / 15.85 kbps for AMR-WB */
        AMR_MODE_4 = 1 << 4,
        /** 7.95 kbps for AMR / 18.25 kbps for AMR-WB */
        AMR_MODE_5 = 1 << 5,
        /** 10.2 kbps for AMR / 19.85 kbps for AMR-WB */
        AMR_MODE_6 = 1 << 6,
        /** 12.2 kbps for AMR / 23.05 kbps for AMR-WB */
        AMR_MODE_7 = 1 << 7,
        /** Silence frame for AMR / 23.85 kbps for AMR-WB */
        AMR_MODE_8 = 1 << 8,
    };

    AmrParams();
    AmrParams(AmrParams& param);
    virtual ~AmrParams();
    AmrParams& operator=(const AmrParams& param);
    bool operator==(const AmrParams& param) const;
    bool operator!=(const AmrParams& param) const;
    virtual status_t writeToParcel(Parcel* parcel) const;
    virtual status_t readFromParcel(const Parcel* in);
    void setAmrMode(const int32_t mode);
    int32_t getAmrMode();
    void setOctetAligned(const bool enable);
    bool getOctetAligned();
    void setMaxRedundancyMillis(const int32_t value);
    int32_t getMaxRedundancyMillis();
    void setDefaultAmrParams();

private:
    /** mode-set: AMR codec mode to represent the bit rate */
    int32_t amrMode;
    /**
     * octet-align: If it's set to true then all fields in the AMR/AMR-WB header
     * shall be aligned to octet boundaries by adding padding bits.
     */
    bool octetAligned;
    /**
     * max-red: It’s the maximum duration in milliseconds that elapses between the
     * primary (first) transmission of a frame and any redundant transmission that
     * the sender will use. This parameter allows a receiver to have a bounded delay
     * when redundancy is used. Allowed values are between 0 (no redundancy will be
     * used) and 65535. If the parameter is omitted, no limitation on the use of
     * redundancy is present. See RFC 4867
     */
    int32_t maxRedundancyMillis;

    // Default AmrParams
    const int32_t kAmrMode = 0;
    const bool kOctetAligned = false;
    const int32_t kMaxRedundancyMillis = 0;
};

}  // namespace imsmedia

}  // namespace telephony

}  // namespace android

#endif