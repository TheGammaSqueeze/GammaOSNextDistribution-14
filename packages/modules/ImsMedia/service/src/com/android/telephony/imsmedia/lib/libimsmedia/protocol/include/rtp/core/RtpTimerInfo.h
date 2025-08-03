/** \addtogroup  RTP_Stack
 *  @{
 */

/**
 * @class RtpTimerInfo
 *
 * @brief It stores timer info for RTCP transmission timer interval calculation.
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

#ifndef __RTP_TIMER_INFO_H__
#define __RTP_TIMER_INFO_H__

#include <RtpGlobal.h>

class RtpTimerInfo
{
    // the last time an RTCP packet was transmitted;
    RtpDt_UInt32 m_uiTp;
    // the current time; TBD..replace with system time
    RtpDt_UInt32 m_uiTc;
    // the next scheduled transmission time of an RTCP packet
    RtpDt_UInt32 m_uiTn;
    /** the estimated number of session members at the time tn
    was last recomputed
    */
    RtpDt_UInt32 m_uiPmembers;
    /**the most current estimate for the number of session
    members
    */
    RtpDt_UInt32 m_uiMembers;
    /**the most current estimate for the number of senders in
    the session;*/
    RtpDt_UInt32 m_uiSenders;
    /**The target RTCP bandwidth, i.e., the total bandwidth
    that will be used for RTCP packets by all members of this session,
    in octets per second.  This will be a specified fraction of the
    "session bandwidth" parameter supplied to the application at
    startup*/
    RtpDt_UInt32 m_uiRtcpBw;
    /**Flag that is true if the application has sent data
    since the 2nd previous RTCP report was transmitted
    */
    RtpDt_UInt32 m_uiWeSent;
    /** The average compound RTCP packet size, in octets,
    over all RTCP packets sent and received by this participant.  The
    size includes lower-layer transport and network protocol headers
    (e.g., UDP and IP) as explained in Section 6.2.
    */
    RtpDt_Int32 m_ulAvgRtcpSize;
    /** Flag that is true if the application has not yet sent
    an RTCP packet.*/
    eRtp_Bool m_bInitial;

    // increment sender count by uiIncrVal
    RtpDt_Void incrSndrCount(IN RtpDt_UInt32 uiIncrVal);

public:
    /**
     * All member vars are inited to zero
     */
    RtpTimerInfo();

    // Destructor
    ~RtpTimerInfo();

    // get method for m_uiTp
    RtpDt_UInt32 getTp();
    // set method for m_uiTp
    RtpDt_Void setTp(IN RtpDt_UInt32 uiTp);

    // get method for m_uiTc
    RtpDt_UInt32 getTc();

    // get method for m_uiTn
    RtpDt_UInt32 getTn();
    // set method for m_uiTn
    RtpDt_Void setTn(IN RtpDt_UInt32 uiTn);

    // get method for m_uiPmembers
    RtpDt_UInt32 getPmembers();
    // set method for m_uiPmembers
    RtpDt_Void setPmembers(IN RtpDt_UInt32 uiPmembers);

    // get method for m_uiRtcpBw
    RtpDt_UInt32 getRtcpBw();
    // set method for m_uiRtcpBw
    RtpDt_Void setRtcpBw(IN RtpDt_UInt32 uiRtcpBw);

    // get method for m_uiWeSent
    RtpDt_UInt32 getWeSent();
    // set method for m_uiWeSent
    RtpDt_Void setWeSent(IN RtpDt_UInt32 uiWeSent);

    // get method for m_ulAvgRtcpSize
    RtpDt_Int32 getAvgRtcpSize();
    // set method for m_ulAvgRtcpSize
    RtpDt_Void setAvgRtcpSize(IN RtpDt_Int32 uiAvgRtcpSize);

    // get method for m_uiInitial
    eRtp_Bool isInitial();
    // set method for m_uiInitial
    RtpDt_Void setInitial(IN eRtp_Bool bSetInitial);

    // It updates AVG RTCP SIZE
    RtpDt_Void updateAvgRtcpSize(IN RtpDt_UInt32 uiRcvdPktSize);

    // It updates Tn and Tp after receiving BYE packet
    eRtp_Bool updateByePktInfo(IN RtpDt_UInt32 uiMemSize);

    // It makes all members with default values
    RtpDt_Void cleanUp();
};

#endif  //__RTP_TIMER_INFO_H__

/** @}*/
