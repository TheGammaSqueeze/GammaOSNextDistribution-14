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

#ifndef __RTCP_APP_PACKET_H__
#define __RTCP_APP_PACKET_H__

#include <RtpGlobal.h>
#include <RtpBuffer.h>
#include <RtcpHeader.h>

/**
 * @class RtcpAppPacket
 * @brief It holds RTCP APP information
 */
class RtcpAppPacket
{
private:
    RtcpHeader m_objRtcpHdr;

    /**
     * A name chosen by the person defining the set of APP packets to be
     * unique with respect to other APP packets this application might
     * receive.
     */
    RtpDt_UInt32 m_uiName;

    RtpBuffer* m_pAppData;

public:
    RtcpAppPacket();

    ~RtcpAppPacket();

    /**
     *  Set method for m_objRtcpHdr
     */
    RtpDt_Void setRtcpHdrInfo(RtcpHeader& objHeader);

    /**
     *  Get method for m_objRtcpHdr
     */
    RtcpHeader* getRtcpHdrInfo();

    /**
     * get method for m_uiName
     */
    RtpDt_UInt32 getName();

    /**
     * set method for m_uiName
     */
    RtpDt_Void setName(IN RtpDt_UInt32 uiName);

    /**
     * get method for m_pAppData
     */
    RtpBuffer* getAppData();

    /**
     * set method for m_pAppData
     */
    RtpDt_Void setAppData(IN RtpBuffer* pobjAppData);

    /**
     * Decodes and stores the information of the RTCP APP packet
     * This function does not allocate memory required for decoding.
     * @param pucAppBuf received RTCP APP packet
     * @param usAppLen length of the APP packet
     */
    eRTP_STATUS_CODE decodeAppPacket(IN RtpDt_UChar* pucAppBuf, IN RtpDt_UInt16 usAppLen);

    /**
     * Performs the encoding of the RTCP APP packet.
     * This function does not allocate memory required for encoding.
     * @param pobjRtcpPktBuf Memory for the buffer is pre-allocated by caller
     */
    eRTP_STATUS_CODE formAppPacket(OUT RtpBuffer* pobjRtcpPktBuf);

};  // end of RtcpAppPacket

#endif  //__RTCP_APP_PACKET_H__

/** @}*/
