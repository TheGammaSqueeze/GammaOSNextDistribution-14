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

#ifndef IMSMEDIA_RTPCONTEXTPARAMS_H
#define IMSMEDIA_RTPCONTEXTPARAMS_H

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

/** Native representation of android.telephony.imsmedia.RtpContextParams */

class RtpContextParams : public Parcelable
{
private:
    int64_t ssrc;
    int64_t timestamp;
    int32_t sequenceNumber;

public:
    RtpContextParams();
    RtpContextParams(const int64_t ssrc, const int64_t timestamp, const int32_t sequenceNumber);
    RtpContextParams(const RtpContextParams& params);
    virtual ~RtpContextParams();
    RtpContextParams& operator=(const RtpContextParams& params);
    bool operator==(const RtpContextParams& params) const;
    bool operator!=(const RtpContextParams& params) const;
    status_t writeToParcel(Parcel* out) const;
    status_t readFromParcel(const Parcel* in);
    int64_t getSsrc();
    void setSsrc(int64_t ssrc);
    int64_t getTimestamp();
    void setTimestamp(int64_t timestamp);
    int32_t getSequenceNumber();
    void setSequenceNumber(int32_t sequenceNumber);
    void setDefaultConfig();
};

}  // namespace imsmedia

}  // namespace telephony

}  // namespace android

#endif  // IMSMEDIA_RTPCONTEXTPARAMS_H
