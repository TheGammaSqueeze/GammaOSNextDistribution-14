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

#ifndef _RTP_BUFFER_H_
#define _RTP_BUFFER_H_

#include <RtpGlobal.h>

/**
 * @class    RtpBuffer
 * @brief    It contains buffer with length
 */
class RtpBuffer
{
private:
    // It holds the length of the buffer
    RtpDt_UInt32 m_uiLength;
    // It holds the actual data
    RtpDt_UChar* m_pBuffer;

public:
    // Constructor
    RtpBuffer();

    /**
     * Constructor
     *
     * @param uiLength Length of the buffer
     *
     * @param pBuffer value of the buffer.
     */
    RtpBuffer(IN RtpDt_UInt32 uiLength, IN RtpDt_UChar* pBuffer);

    // Destructor
    ~RtpBuffer();

    /**
     * It sets length to the RtpBuffer
     * @param uiLen     Length of the buffer
     */
    RtpDt_Void setLength(IN RtpDt_UInt32 uiLen);

    /**
     * It gets length from the RtpBuffer
     *
     * @return It returns the length
     */
    RtpDt_UInt32 getLength();

    /**
     * It creates the buffer and cp input data to it
     *
     * @param   pBuff   Input buffer pointer.
     */
    RtpDt_Void setBuffer(IN RtpDt_UChar* pBuff);

    /**
     * It gets the value from the RtpBuffer
     *
     * @return  It returns the buffer value pointer.
     */
    RtpDt_UChar* getBuffer();

    /**
     * Utility function to set buffer information.
     *
     * @param uiLength  Length of the buffer
     *
     * @param pBuffer   Buffer pointer.
     */
    RtpDt_Void setBufferInfo(IN RtpDt_UInt32 uiLength, IN RtpDt_UChar* pBuffer);
};

#endif /* _RTP_BUFFER_H_ */
/** @}*/
