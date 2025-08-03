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

#ifndef __RTCP_CONFIGINFO_H__
#define __RTCP_CONFIGINFO_H__

#include <RtpGlobal.h>

/**
 * @class    RtcpConfigInfo
 * @brief    It stores RTCP config information. RtcpConfigInfo information will be used in
 * RTCP packet.
 */
class RtcpConfigInfo
{
private:
    RtpDt_UInt32 m_uiSdesItemCnt;
    RtpDt_UInt32 m_uiByeReasonSize;
    RtpDt_UInt32 m_uiAppDepDataSize;

    /**
     * Each array element contains the information about one SDES item.
     * The array is indexed as per eRTCP_SDES_TYPE
     */
    tRTCP_SDES_ITEM m_arrSdesInfo[RTP_MAX_SDES_TYPE];

    /**
     * enable RTCP packet transmission support.
     */
    eRtp_Bool m_bEnaRtcpAppPktSend;

public:
    RtcpConfigInfo();
    ~RtcpConfigInfo();

    /**
     * set method for uiByeReasonSize
     */
    RtpDt_Void setByeReasonSize(IN RtpDt_UInt32 uiByeReason);

    /**
     * get method for uiByeReasonSize
     */
    RtpDt_UInt32 getByeReasonSize();

    /**
     * set method for uiAppDepDataSize
     */
    RtpDt_Void setAppDepDataSize(IN RtpDt_UInt32 uiAppDepSize);

    /**
     * get method for uiAppDepDataSize
     */
    RtpDt_UInt32 getAppDepDataSize();

    /**
     * get method for m_uiSdesItemCnt
     */
    RtpDt_UInt32 getSdesItemCount();

    /**
     * set method for m_uiSdesItemCnt
     */
    RtpDt_Void setSdesItemCount(IN RtpDt_UInt32 uiSdesItemCnt);

    /**
     * It adds tRTCP_SDES_ITEM  to m_arrSdesInfo
     */
    eRtp_Bool addRtcpSdesItem(IN tRTCP_SDES_ITEM* pstSdesItem, IN RtpDt_UInt32 uiIndex);

    /**
     * It gets tRTCP_SDES_ITEM item using index
     */
    tRTCP_SDES_ITEM* getRtcpSdesItem(IN RtpDt_UInt32 uiIndex);

    /**
     * set method for m_bEnaRtcpAppPktSend
     */
    RtpDt_Void enableRtcpAppPktSend();

    /**
     * It returns true if m_bEnaRtcpAppPktSend is enabled.
     */
    eRtp_Bool isRtcpAppPktSendEnable();

    /**
     * It estimates SDES Packet Size
     */
    RtpDt_UInt32 estimateSdesPktSize();
};  // RtcpConfigInfo

#endif  //__RTCP_CONFIGINFO_H__

/** @}*/
