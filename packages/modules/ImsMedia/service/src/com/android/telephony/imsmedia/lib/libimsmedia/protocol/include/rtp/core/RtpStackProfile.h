/** \addtogroup  RTP_Stack
 *  @{
 */

/**
 * @class   RtpStackProfile
 * @brief   Class stores RTP profile information which includes MTU, RTCP Bandwidth, etc.
 */

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

#ifndef __RTP_STACK_PROFILE_H__
#define __RTP_STACK_PROFILE_H__

#include <RtpGlobal.h>

class RtpStackProfile
{
private:
    // Percentage of Bandwidth dedicated for RTCP packets. Defaults to RTP_DEF_RTCP_BW_SIZE
    RtpDt_UInt32 m_uiRtcpSessionBw;

    /** MTU size will be used for validation. If generated Packet is larger than the
     * MTU size, send will fail with RTP_MTU_EXCEEDED as return status
     */
    RtpDt_UInt32 m_uiMTUSize;
    // Terminal number. Used in SSRC generation to make it more unique
    RtpDt_UInt32 m_uiTermNum;

public:
    // Constructor
    RtpStackProfile();
    // Destructor
    ~RtpStackProfile();

    // set method for m_uiRtcpSessionBw
    RtpDt_Void setRtcpBandwidth(IN RtpDt_UInt32 uiRtcpBw);
    // get method for m_uiRtcpSessionBw
    RtpDt_UInt32 getRtcpBandwidth();

    // set method for m_uiMTUSize
    RtpDt_Void setMtuSize(IN RtpDt_UInt32 uiMtuSize);
    // get method for m_uiMTUSize
    RtpDt_UInt32 getMtuSize();

    // set method for m_uiTermNum
    RtpDt_Void setTermNumber(IN RtpDt_UInt32 uiTermNum);
    // get method for m_uiTermNum
    RtpDt_UInt32 getTermNumber();

};  // end of RtpStackProfile

#endif  //__RTP_STACK_PROFILE_H__

/** @}*/
