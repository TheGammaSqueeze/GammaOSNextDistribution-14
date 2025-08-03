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

/** \addtogroup  RTP_Stack
 *  @{
 */

#ifndef __RTP_PAYLOAD_INFO_H__
#define __RTP_PAYLOAD_INFO_H__

#include <RtpGlobal.h>

/**
 * @class    RtpPayloadInfo
 * @brief    It defines the RTP payload information.(ex:- payload type, sampling rate).
 */
class RtpPayloadInfo
{
private:
    // payload type
    RtpDt_UInt32 m_uiPayloadType[RTP_MAX_PAYLOAD_TYPE] = {0};
    RtpDt_UInt32 m_uiSamplingRate;

public:
    RtpPayloadInfo();
    RtpPayloadInfo(IN RtpDt_UInt32* uiPayloadType, IN RtpDt_UInt32 uiSamplingRate,
            IN RtpDt_UInt32 nNumOfPayloadParam);

    ~RtpPayloadInfo();

    RtpDt_UInt32 getPayloadType(IN RtpDt_UInt32 payloadIndex);

    RtpDt_UInt32 getSamplingRate();

    RtpDt_Void setRtpPayloadInfo(IN RtpPayloadInfo* pobjRlInfo);
};

#endif /* __RTP_PAYLOAD_INFO_H__ */

/** @}*/
