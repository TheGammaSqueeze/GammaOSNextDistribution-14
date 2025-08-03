/** \addtogroup  RTP_Stack
 *  @{
 */

/**
 * @class   RtpStackUtl
 * @brief   This class provides RTP utility functions
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

#ifndef __RTP_STACK_UTIL_H__
#define __RTP_STACK_UTIL_H__

#include <RtpGlobal.h>
#include <RtpBuffer.h>

class RtpStackUtil
{
public:
    // Constructor
    RtpStackUtil();
    // Destructor
    ~RtpStackUtil();

    /**
     * @brief Parse and retrieve seq number from a RTP packet
     * @param pucRtpHdrBuf RTP packet buffer from network
     * @return Retrieved sequence Number
     */
    static RtpDt_UInt16 getSequenceNumber(IN RtpDt_UChar* pucRtpHdrBuf);

    /**
     * @brief Parse and retrieve ssrc from a RTP packet
     * @param pucRecvdRtpPkt RTP packet buffer from network
     * @return Retrieved ssrc
     */
    static RtpDt_UInt32 getRtpSsrc(IN RtpDt_UChar* pucRecvdRtpPkt);

    /**
     * @brief Parse and retrieve ssrc from a RTCP packet
     * @param pucRecvdRtcpPkt RTCP packet from network
     * @return Retrieved ssrc
     */
    static RtpDt_UInt32 getRtcpSsrc(IN RtpDt_UChar* pucRecvdRtcpPkt);

    /**
     * @brief Utility to generate new ssrc
     * @param uiTermNum Terminal number
     * @return new generated ssrc
     */
    static RtpDt_UInt32 generateNewSsrc(IN RtpDt_UInt32 uiTermNum);

    /**
     * @brief It gets middle four octets from Ntp timestamp
     * @param pstNtpTs Ntp timestamp
     * @return Middle four octets of Ntp timestamp
     */
    static RtpDt_UInt32 getMidFourOctets(IN tRTP_NTP_TIME* pstNtpTs);

    /**
     * @brief Calculates RTP time stamp
     * @param uiPrevRtpTs Previous Rtp timestamp
     * @param pstCurNtpTs Current Ntp timestamp
     * @param pstPrevNtpTs Previous Ntp timestamp
     * @param uiSamplingRate Sampling Rate
     * @return Calculated RTP time stamp
     */
    static RtpDt_UInt32 calcRtpTimestamp(IN RtpDt_UInt32 uiPrevRtpTs, IN tRTP_NTP_TIME* pstCurNtpTs,
            IN tRTP_NTP_TIME* pstPrevNtpTs, IN RtpDt_UInt32 uiSamplingRate);
};

#endif  //__RTP_STACK_UTIL_H__
/** @}*/
