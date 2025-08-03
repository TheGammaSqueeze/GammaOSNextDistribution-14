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

#ifndef __RTP_HEADER_H__
#define __RTP_HEADER_H__

#include <RtpGlobal.h>
#include <RtpBuffer.h>
#include <list>

// It defines the RTP Header information.

/**
 * @class    RtpHeader
 * @brief    It defines the RTP packet header.
 * This class stores the fixed header values and csrc list of RTP packet.
 * It encodes and decodes the RTP header
 */

class RtpHeader
{
private:
    // m_ucVersion identifies the version of RTP
    RtpDt_UChar m_ucVersion;

    // m_ucPadding identifies the padding bit.
    /**
     * If the padding bit is set, the packet contains one or more
     * additional padding octets at the end which are not part of the
     * payload.
     */
    RtpDt_UChar m_ucPadding;

    /**
     * If the extension bit is set, the fixed header MUST be followed by
     * exactly one header extension. Refer RFC 3550 Section 5.1 for more details.
     */
    RtpDt_UChar m_ucExtension;

    /**
     * m_ucCsrcCount contains the number of CSRC identifiers that follow the fixed header.
     */
    RtpDt_UChar m_ucCsrcCount;

    /**
     * It contains CSRC list.
     */
    std::list<RtpDt_UInt32> m_uiCsrcList;

    /**
     * m_ucMarker contains marker bit.
     */
    RtpDt_UChar m_ucMarker;

    /**
     * m_ucPayloadType identifies the format of the Rtp payload.
     */
    RtpDt_UChar m_ucPayloadType;

    /**
     * Sequence number
     */
    RtpDt_UInt16 m_usSequenceNumber;

    /**
     * The m_uiTimestamp reflects the sampling instant of the first octet
     * in the RTP data packet.
     */
    RtpDt_UInt32 m_uiTimestamp;

    /**
     * Synchronization source.
     */
    RtpDt_UInt32 m_uiSsrc;

    /**
     * add element to m_uiCsrcList
     */
    RtpDt_Void addElementToCsrcList(IN RtpDt_UInt32 uiCsrc);

public:
    // Constructor
    RtpHeader();

    // Destructor
    ~RtpHeader();

    /**
     * set method for m_ucVersion
     */
    RtpDt_Void setVersion(IN RtpDt_UChar ucVersion);

    /**
     * get method for m_ucVersion
     */
    RtpDt_UChar getVersion();

    /**
     * set method for m_ucPadding
     */
    RtpDt_Void setPadding();

    /**
     * get method for m_ucPadding
     */
    RtpDt_UChar getPadding();

    /**
     * set method for m_ucExtension
     */
    RtpDt_Void setExtension(RtpDt_UChar ext);

    /**
     * get method for m_ucExtension
     */
    RtpDt_UChar getExtension();

    /**
     * set method for m_ucCsrcCount
     */
    RtpDt_Void setCsrcCount(IN RtpDt_UChar ucCsrcCnt);

    /**
     * get method for m_ucCsrcCount
     */
    RtpDt_UChar getCsrcCount();

    /**
     * get method for m_uiCsrcList
     */
    std::list<RtpDt_UInt32>& getCsrcList();

    /**
     * set method for m_ucMarker
     */
    RtpDt_Void setMarker();

    /**
     * get method for m_ucMarker
     */
    RtpDt_UChar getMarker();

    /**
     * set method for m_ucPayloadType
     */
    RtpDt_Void setPayloadType(IN RtpDt_UChar ucPldType);

    /**
     * get method for m_ucPayloadType
     */
    RtpDt_UChar getPayloadType();

    /**
     * set method for m_usSeqNum
     */
    RtpDt_Void setSequenceNumber(IN RtpDt_UInt16 usSeqNum);

    /**
     * get method for m_usSeqNum
     */
    RtpDt_UInt16 getSequenceNumber();

    /**
     * set method for m_uiTimestamp
     */
    RtpDt_Void setRtpTimestamp(IN RtpDt_UInt32 uiTimestamp);

    /**
     * get method for m_uiTimestamp
     */
    RtpDt_UInt32 getRtpTimestamp();

    /**
     * set method for m_uiSsrc
     */
    RtpDt_Void setRtpSsrc(IN RtpDt_UInt32 uiSsrc);

    /**
     * get method for m_uiSsrc
     */
    RtpDt_UInt32 getRtpSsrc();

    /**
     * Performs the fixed header encoding of the RTP packet.
     * This function does not allocate memory required for encoding.
     *
     * @param pobjRtpPktBuf Memory for the buffer is pre-allocated by caller
     */
    eRtp_Bool formHeader(IN RtpBuffer* pobjRtpPktBuf);

    /**
     * Decodes and stores the information of the fixed header in the RTP packet
     * This function does not allocate memory required for decoding.
     * @param pobjRtpPktBuf Memory for the buffer is pre-allocated by caller
     * @param uiBufPos returns current buffer position
     */
    eRtp_Bool decodeHeader(IN RtpBuffer* pobjRtpPktBuf, OUT RtpDt_UInt32& uiBufPos);
};

#endif /* __RTP_HEADER_H__ */
/** @}*/
