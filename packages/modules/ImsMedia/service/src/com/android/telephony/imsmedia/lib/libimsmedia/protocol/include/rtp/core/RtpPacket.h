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

#ifndef __RTP_PACKET_H__
#define __RTP_PACKET_H__

#include <RtpGlobal.h>
#include <RtpHeader.h>

/**
 * @class    RtpPacket
 * @brief    It contains Rtp packet information.
 */
class RtpPacket
{
private:
    // Rtp Header
    RtpHeader m_objRtpHeader;

    /**
     * Extension header buffer. This is encoded and given by app.
     * After decoding, ExtractExtHeaders, will update this with the extension
     * header buffer
     */
    RtpBuffer* m_pobjExt;

    // RTP payload
    RtpBuffer* m_pobjRtpPayload;

#ifdef ENABLE_PADDING
    // Length of Pad Bytes.
    RtpDt_UChar m_ucPadLen;
#endif

public:
    // Constructor
    RtpPacket();
    // Destructor
    ~RtpPacket();

    // get method for m_objRtpHeader
    RtpHeader* getRtpHeader();

    // set method for m_pobjRtpPayload
    RtpDt_Void setRtpPayload(IN RtpBuffer* pobjRtpPld);

    // get method for m_pobjRtpPayload
    RtpBuffer* getRtpPayload();

    // get method for RTP Header extension buffer
    RtpBuffer* getExtHeader();

    /**
     * pobjExt will stored and freed by stack.
     *
     * @param[in] pobjExt Header extension to be added to the packet
     */
    RtpDt_Void setExtHeader(IN RtpBuffer* pobjExt);

    /**
     * Performs the encoding of the RTP packet.
     * This function does not allocate memory required for encoding.
     *
     * @param[out] pobjRtpPktBuf Memory for the buffer is pre-allocated by caller
     */
    eRtp_Bool formPacket(OUT RtpBuffer* pobjRtpPktBuf);

    /**
     * Decodes and stores the information of the RTP packet
     * This function does not allocate memory required for decoding.
     *
     * @param[in] pobjRtpPktBuf Memory for the buffer is pre-allocated by caller
     * @return eRTP_SUCCESS on successful decoding
     */
    eRtp_Bool decodePacket(IN RtpBuffer* pobjRtpPktBuf);
};
#endif  //__RTP_PACKET_H__

/** @}*/
