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

#include <RtpStackProfile.h>

RtpStackProfile::RtpStackProfile() :
        m_uiRtcpSessionBw(RTP_DEF_RTCP_BW_SIZE),
        m_uiMTUSize(RTP_CONF_MTU_SIZE),
        m_uiTermNum(RTP_ZERO)
{
}

RtpStackProfile::~RtpStackProfile() {}

RtpDt_Void RtpStackProfile::setRtcpBandwidth(IN RtpDt_UInt32 uiRtcpBw)
{
    m_uiRtcpSessionBw = uiRtcpBw;
}

RtpDt_UInt32 RtpStackProfile::getRtcpBandwidth()
{
    return m_uiRtcpSessionBw;
}

RtpDt_Void RtpStackProfile::setMtuSize(IN RtpDt_UInt32 uiMtuSize)
{
    m_uiMTUSize = uiMtuSize;
}

RtpDt_UInt32 RtpStackProfile::getMtuSize()
{
    return m_uiMTUSize;
}

RtpDt_Void RtpStackProfile::setTermNumber(IN RtpDt_UInt32 uiTermNum)
{
    m_uiTermNum = uiTermNum;
}

RtpDt_UInt32 RtpStackProfile::getTermNumber()
{
    return m_uiTermNum;
}
