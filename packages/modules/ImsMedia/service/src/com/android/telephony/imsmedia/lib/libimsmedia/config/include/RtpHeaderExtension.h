/**
 * Copyright (C) 2023 The Android Open Source Project
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

#ifndef RTPEXTENSION_H
#define RTPEXTENSION_H

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

/** Native representation of android.telephony.imsmedia.RtpHeaderExtension */

/**
 * The class to encapsulate RTP header extension.
 * Per RFC8285, an RTP header extension consists of both a local identifier in the range 1-14, an
 * 8-bit length indicator and a number of extension data bytes equivalent to the stated length.
 */
class RtpHeaderExtension : public Parcelable
{
public:
    RtpHeaderExtension();
    RtpHeaderExtension(const RtpHeaderExtension& extension);
    virtual ~RtpHeaderExtension();
    RtpHeaderExtension& operator=(const RtpHeaderExtension& extension);
    bool operator==(const RtpHeaderExtension& extension) const;
    bool operator!=(const RtpHeaderExtension& extension) const;
    virtual status_t writeToParcel(Parcel* parcel) const;
    virtual status_t readFromParcel(const Parcel* in);
    int32_t getLocalIdentifier();
    void setLocalIdentifier(int32_t id);
    uint8_t* getExtensionData() const;
    void setExtensionData(const uint8_t* data, const int32_t size);
    int32_t getExtensionDataSize();
    void setExtensionDataSize(int32_t size);

protected:
    // The local identifier for this RTP header extension.
    int32_t mLocalIdentifier;
    // The data for this RTP header extension.
    uint8_t* mExtensionData;
    // The length of the mExtensionData
    int32_t mExtensionDataSize;
};
}  // namespace imsmedia
}  // namespace telephony
}  // namespace android

#endif