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

#ifndef __RTCP_HEADER_H__
#define __RTCP_HEADER_H__

#include <RtpGlobal.h>
#include <RtpBuffer.h>

#define MAX_RTP_VERSION            3
#define MAX_RECEPTION_REPORT_COUNT 31

/**
 * @class   RtcpHeader
 * @brief   This class provides RTCP packet encode and decode functionality.
 *          RTCP header fields can be accessed via get/set APIs.
 */
class RtcpHeader
{
private:
    /**
     * m_ucVersion identifies the version of RTCP
     */
    RtpDt_UChar m_ucVersion;

    /**
     * If the padding bit is set, RTCP packet contains some additional padding octets at the end.
     */
    eRtp_Bool m_ucIsPadding;

    /**
     * The number of reception report blocks contained in this packet.
     */
    RtpDt_UChar m_ucReceptionReportCount;

    /**
     * It Identifies the RTCP packet type.
     */
    RtpDt_UChar m_ucPacketType;

    /**
     * The length of the RTCP packet in 32-bit words minus one,
     * including the header and any padding
     */
    RtpDt_UInt32 m_usLength;

    // Synchronization source.
    RtpDt_UInt32 m_uiSsrc;

public:
    // Constructor
    RtcpHeader();

    // Destructor
    ~RtcpHeader();

    /**
     *  Set RTCP protocol version.
     *  It's a 2-bit field and value should not be greater than 3.
     *  Version defined by the specification is 2.
     *
     *  @param ucVersion Version number to be set.
     *  @return false if ucVersion is greater than 3.
     */
    eRtp_Bool setVersion(IN RtpDt_UChar ucVersion);

    /**
     * Get RTCP protocol version.
     *
     * @return RTP version number.
     */
    RtpDt_UChar getVersion();

    /**
     * Set method for padding bit.
     *
     * Padding bit indicates if RTCP packet contains additional padding octets at the end to align
     * with block size required by encryption algorithms.
     */
    RtpDt_Void setPadding(eRtp_Bool padding = eRTP_TRUE);

    /**
     * Get method for padding bit.
     *
     * @return true if padding bit is set and false otherwise.
     */
    eRtp_Bool getPadding();

    /**
     * Set method for Reception report count.
     * It's a 5-bits field. Max value is
     *
     * @param ucReceptionReport The number of reception report blocks contained in this packet.
     */
    eRtp_Bool setReceptionReportCount(IN RtpDt_UChar ucReceptionReport);

    /**
     * Get method for Reception report count.
     */
    RtpDt_UChar getReceptionReportCount();

    /**
     * Sets Packet type to identify RTCP packet type. It's a 8-bits field.
     *
     * @param ucPacketType Possible values are:
     * 200 = SR Sender Report packet.
     * 201 = RR Receiver Report packet.
     * 202 = SDES Source Description packet.
     * 203 = BYE Goodbye packet.
     * 204 = APP Application-defined packet.
     */
    RtpDt_Void setPacketType(IN RtpDt_UChar ucPacketType);

    /**
     * Get method for RTCP packet type.
     */
    RtpDt_UChar getPacketType();

    /**
     * Set method for length of RTCP packet. It's a 16-bit field.
     *
     * @param usLength  length in 32-bit words minus one, including the header and any padding.
     */
    RtpDt_Void setLength(IN RtpDt_UInt32 usLength);

    /**
     * Get method for RTCP packet length.
     */
    RtpDt_UInt32 getLength();

    /**
     * Get method for SSRC (synchronization source identifier)
     *
     * @param uiSsrc SSRC of the originator of this RTCP packet.
     */
    RtpDt_Void setSsrc(IN RtpDt_UInt32 uiSsrc);

    /**
     * Get method for SSRC (synchronization source identifier)
     */
    RtpDt_UInt32 getSsrc();

    /**
     * Equality operator overloaded
     */
    bool operator==(const RtcpHeader& objRtcpHeader) const;

    /**
     * Decodes and stores the information of the RTCP Header.
     *
     * @param[in] pobjRtcpPktBuf RTCP pcaket buffer.
     * @return eRTP_SUCCESS on successful decoding
     */
    eRtp_Bool decodeRtcpHeader(IN RtpDt_UChar* pRtcpBuffer, RtpDt_Int32 length);

    /**
     * Performs the encoding of the RTCP header using the data filled by set methods.
     *
     * @param[out] pobjRtcpPktBuf RTCP header is encoded into this buffer. Buffer should be
     * allocated by the caller.
     * @return eRTP_SUCCESS on successful encoding
     */
    eRtp_Bool formRtcpHeader(OUT RtpBuffer* pobjRtcpPktBuf);

    /**
     * Performs the encoding of the first 4 octets of the RTCP Header.
     *
     * @param[out] pobjRtcpPktBuf First 4 octets of the RTCP header is encoded into this buffer.
     * Buffer should be allocated by the caller.
     * @return eRTP_SUCCESS on successful encoding
     */
    eRtp_Bool formPartialRtcpHeader(OUT RtpBuffer* pobjRtcpPktBuf);

    /**
     * It populates RTCP header information
     */
    RtpDt_Void populateRtcpHeader(IN RtpDt_UChar ucReceptionReportCount,
            IN RtpDt_UChar ucPacketType, IN RtpDt_UInt32 uiSsrc);

};  // end of RtcpHeader

#endif  //__RTCP_HEADER_H__

/** @}*/
