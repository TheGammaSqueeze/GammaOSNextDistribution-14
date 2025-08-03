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

#include <RtpTimerInfo.h>
#include <RtpStackUtil.h>

/*********************************************************
 * Function name        : RtpTimerInfo
 * Description          : Constructor
 * Return type          : None
 * Argument             : None
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
RtpTimerInfo::RtpTimerInfo() :
        m_uiTp(RTP_ZERO),
        m_uiTc(RTP_ZERO),
        m_uiTn(RTP_ZERO),
        m_uiPmembers(RTP_ONE),
        m_uiMembers(RTP_ONE),
        m_uiSenders(RTP_ZERO),
        m_uiRtcpBw(RTP_ZERO),
        m_uiWeSent(RTP_ZERO),
        m_ulAvgRtcpSize(RTP_ZERO),
        m_bInitial(eRTP_TRUE)
{
}

/*********************************************************
 * Function name        : ~RtpTimerInfo
 * Description          : Destructor
 * Return type          : None
 * Argument             : None
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
RtpTimerInfo::~RtpTimerInfo() {}

/*********************************************************
 * Function name        : cleanUp
 * Description          : It makes all members with default values
 * Return type          : None
 * Argument             : None
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
RtpDt_Void RtpTimerInfo::cleanUp()
{
    m_uiTp = RTP_ZERO;
    m_uiTc = RTP_ZERO;
    m_uiTn = RTP_ZERO;
    m_uiPmembers = RTP_ONE;
    m_uiMembers = RTP_ONE;
    m_uiSenders = RTP_ZERO;
    m_uiRtcpBw = RTP_ZERO;
    m_uiWeSent = RTP_ZERO;
    m_ulAvgRtcpSize = RTP_ZERO;
    m_bInitial = eRTP_TRUE;
}

/*********************************************************
 * Function name        : incrSndrCount
 * Description          : It increments the m_uiSenders variable by uiIncrVal
 * Return type          : RtpDt_Void
 * Argument             : RtpDt_UInt32 : In
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
RtpDt_Void RtpTimerInfo::incrSndrCount(IN RtpDt_UInt32 uiIncrVal)
{
    m_uiSenders = m_uiSenders + uiIncrVal;
}

/*********************************************************
 * Function name        : updateAvgRtcpSize
 * Description          : It updates AVG_RTCP_SIZE parameter
 * Return type          : RtpDt_Void
 * Argument             : RtpDt_UInt32 : In
 *                          Received RTCP packet size
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
RtpDt_Void RtpTimerInfo::updateAvgRtcpSize(IN RtpDt_UInt32 uiRcvdPktSize)
{
    // avg_rtcp_size = (1/16) * packet_size + (15/16) * avg_rtcp_size
    m_ulAvgRtcpSize = (1.0 / 16) * uiRcvdPktSize + (15.0 / 16) * m_ulAvgRtcpSize;
}

/*********************************************************
 * Function name        : updateByePktInfo
 * Description          : It updates the timer information
 * Return type          : eRtp_Bool
 * Argument             : RtpDt_UInt32 : In
 *                          size of the Receiver list.
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
eRtp_Bool RtpTimerInfo::updateByePktInfo(IN RtpDt_UInt32 uiMemSize)
{
    m_uiMembers = uiMemSize;
    /*
        if (*members < *pmembers) {
            tn = tc +
                    (((RtpDt_Double) *members)/(*pmembers))*(tn - tc);
            *tp = tc -
                        (((RtpDt_Double) *members)/(*pmembers))*(tc - *tp);
            *pmembers = *members;
        }
    */

    // Reference: RFC 3550, section A.7, page 93
    if (m_uiMembers < m_uiPmembers)
    {
        m_uiTn = getTc() + (m_uiMembers / m_uiPmembers) * (m_uiTn - getTc());
        m_uiTp = getTc() - (m_uiMembers / m_uiPmembers) * (getTc() - m_uiTp);
        m_uiPmembers = m_uiMembers;
        return eRTP_TRUE;
    }

    return eRTP_FALSE;
}  // updateByePktInfo

/*********************************************************
 * Function name        : getTp
 * Description          : get method for m_uiTp
 * Return type          : RtpDt_UInt32
 * Argument             : None
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
RtpDt_UInt32 RtpTimerInfo::getTp()
{
    return m_uiTp;
}

/*********************************************************
 * Function name        : setTp
 * Description          : set method for m_uiTp
 * Return type          : RtpDt_Void
 * Argument             : RtpDt_UInt32 : In
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
RtpDt_Void RtpTimerInfo::setTp(IN RtpDt_UInt32 uiTp)
{
    m_uiTp = uiTp;
}

/*********************************************************
 * Function name        : getTc
 * Description          : get method for m_uiTc
 * Return type          : RtpDt_UInt32
 *                          return value in Milliseconds
 * Argument             : None
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
RtpDt_UInt32 RtpTimerInfo::getTc()
{
    tRTP_NTP_TIME stCurNtpRtcpTs = {RTP_ZERO, RTP_ZERO};
    RtpOsUtil::GetNtpTime(stCurNtpRtcpTs);
    RtpDt_UInt32 uiMidOctets = RtpStackUtil::getMidFourOctets(&stCurNtpRtcpTs);
    RtpDt_UInt32 uiHigh = uiMidOctets >> RTP_BYTE2_BIT_SIZE;
    uiHigh = uiHigh * RTP_SEC_TO_MILLISEC;
    RtpDt_Double uiLow = uiMidOctets & RTP_HEX_16_BIT_MAX;
    uiLow = uiLow / RTP_MILLISEC_MICRO;
    uiMidOctets = uiHigh + (RtpDt_UInt32)uiLow;  // it is in milliseconds
    return uiMidOctets;
}

/*********************************************************
 * Function name        : getTn
 * Description          : get method for m_uiTn
 * Return type          : RtpDt_UInt32
 * Argument             : None
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
RtpDt_UInt32 RtpTimerInfo::getTn()
{
    return m_uiTn;
}

/*********************************************************
 * Function name        : setTn
 * Description          : set method for m_uiTn
 * Return type          : RtpDt_Void
 * Argument             : RtpDt_UInt32 : In
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
RtpDt_Void RtpTimerInfo::setTn(IN RtpDt_UInt32 uiTn)
{
    m_uiTn = uiTn;
}

/*********************************************************
 * Function name        : getPmembers
 * Description          : get method for m_uiPmembers
 * Return type          : RtpDt_UInt32
 * Argument             : None
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
RtpDt_UInt32 RtpTimerInfo::getPmembers()
{
    return m_uiPmembers;
}

/*********************************************************
 * Function name        : setPmembers
 * Description          : set method for m_uiPmembers
 * Return type          : RtpDt_Void
 * Argument             : RtpDt_UInt32 : In
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
RtpDt_Void RtpTimerInfo::setPmembers(IN RtpDt_UInt32 uiPmembers)
{
    m_uiPmembers = uiPmembers;
}

/*********************************************************
 * Function name        : getRtcpBw
 * Description          : get method for m_uiRtcpBw
 * Return type          : RtpDt_UInt32
 * Argument             : None
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
RtpDt_UInt32 RtpTimerInfo::getRtcpBw()
{
    return m_uiRtcpBw;
}

/*********************************************************
 * Function name        : setRtcpBw
 * Description          : set method for m_uiRtcpBw
 * Return type          : RtpDt_Void
 * Argument             : RtpDt_UInt32 : In
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
RtpDt_Void RtpTimerInfo::setRtcpBw(IN RtpDt_UInt32 uiRtcpBw)
{
    m_uiRtcpBw = uiRtcpBw;
}

/*********************************************************
 * Function name        : getWeSent
 * Description          : get method for m_uiWeSent
 * Return type          : RtpDt_UInt32
 * Argument             : None
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
RtpDt_UInt32 RtpTimerInfo::getWeSent()
{
    return m_uiWeSent;
}

/*********************************************************
 * Function name        : setWeSent
 * Description          : set method for m_uiWeSent
 * Return type          : RtpDt_Void
 * Argument             : RtpDt_UInt32 : In
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
RtpDt_Void RtpTimerInfo::setWeSent(IN RtpDt_UInt32 uiWeSent)
{
    m_uiWeSent = uiWeSent;
}

/*********************************************************
 * Function name        : getAvgRtcpSize
 * Description          : get method for m_ulAvgRtcpSize
 * Return type          : RtpDt_UInt32
 * Argument             : None
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
RtpDt_Int32 RtpTimerInfo::getAvgRtcpSize()
{
    return m_ulAvgRtcpSize;
}

/*********************************************************
 * Function name        : setAvgRtcpSize
 * Description          : set method for m_ulAvgRtcpSize
 * Return type          : RtpDt_Void
 * Argument             : RtpDt_Int32 : In
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
RtpDt_Void RtpTimerInfo::setAvgRtcpSize(IN RtpDt_Int32 uiAvgRtcpSize)
{
    m_ulAvgRtcpSize = uiAvgRtcpSize;
}

/*********************************************************
 * Function name        : isInitial
 * Description          : get method for m_bInitial
 * Return type          : eRtp_Bool
 * Argument             : None
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
eRtp_Bool RtpTimerInfo::isInitial()
{
    return m_bInitial;
}

/*********************************************************
 * Function name        : setInitial
 * Description          : set method for m_uiInitial
 * Return type          : RtpDt_Void
 * Argument             : eRtp_Bool : In
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
RtpDt_Void RtpTimerInfo::setInitial(IN eRtp_Bool bSetInitial)
{
    m_bInitial = bSetInitial;
}
