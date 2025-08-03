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

#include <RtpPayloadInfo.h>

RtpPayloadInfo::RtpPayloadInfo() :
        m_uiSamplingRate(RTP_ZERO)
{
    for (RtpDt_UInt32 i = 0; i < RTP_MAX_PAYLOAD_TYPE; i++)
        m_uiPayloadType[i] = RTP_ZERO;
}

RtpPayloadInfo::RtpPayloadInfo(IN RtpDt_UInt32* uiPayloadType, IN RtpDt_UInt32 uiSamplingRate,
        IN RtpDt_UInt32 nNumOfPayloadParam) :
        m_uiSamplingRate(uiSamplingRate)
{
    for (RtpDt_UInt32 i = 0; i < nNumOfPayloadParam; i++)
        m_uiPayloadType[i] = uiPayloadType[i];
}

RtpPayloadInfo::~RtpPayloadInfo() {}

RtpDt_Void RtpPayloadInfo::setRtpPayloadInfo(IN RtpPayloadInfo* pobjRlInfo)
{
    for (RtpDt_UInt32 i = 0; i < RTP_MAX_PAYLOAD_TYPE; i++)
        m_uiPayloadType[i] = pobjRlInfo->getPayloadType(i);

    m_uiSamplingRate = pobjRlInfo->getSamplingRate();
}

RtpDt_UInt32 RtpPayloadInfo::getPayloadType(IN RtpDt_UInt32 payloadIndex)
{
    return m_uiPayloadType[payloadIndex];
}

RtpDt_UInt32 RtpPayloadInfo::getSamplingRate()
{
    return m_uiSamplingRate;
}
