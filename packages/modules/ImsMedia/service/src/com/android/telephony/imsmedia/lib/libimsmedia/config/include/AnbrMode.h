/**
 * Copyright (C) 2024 The Android Open Source Project
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

#ifndef ANBRMODE_H
#define ANBRMODE_H

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

/** Native representation of android.telephony.imsmedia.AnbrMode */

/**
 * The class represents ANBR parameters.
 */
class AnbrMode : public Parcelable
{
public:
    AnbrMode();
    AnbrMode(AnbrMode& param);
    virtual ~AnbrMode();
    AnbrMode& operator=(const AnbrMode& param);
    bool operator==(const AnbrMode& param) const;
    bool operator!=(const AnbrMode& param) const;
    virtual status_t writeToParcel(Parcel* parcel) const;
    virtual status_t readFromParcel(const Parcel* in);
    void setAnbrUplinkCodecMode(const int32_t uplinkMode);
    int32_t getAnbrUplinkCodecMode();
    void setAnbrDownlinkCodecMode(const int32_t downlinkMode);
    int32_t getAnbrDownlinkCodecMode();
    void setDefaultAnbrMode();

private:
    /** The codec mode of the current activated code in EvsParams and AmrParams */
    int32_t anbrUplinkMode;

    /** The codec mode of the current activated code in EvsParams and AmrParams */
    int32_t anbrDownlinkMode;

    // Default AnbrMode
    const int32_t kAnbrUplinkMode = 0;
    const int32_t kAnbrDownlinkMode = 0;
};

}  // namespace imsmedia

}  // namespace telephony

}  // namespace android

#endif
