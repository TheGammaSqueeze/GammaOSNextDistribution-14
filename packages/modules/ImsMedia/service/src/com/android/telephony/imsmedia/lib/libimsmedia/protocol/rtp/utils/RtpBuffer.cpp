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

#include <RtpBuffer.h>
#include <string.h>

RtpBuffer::RtpBuffer() :
        m_uiLength(RTP_ZERO),
        m_pBuffer(nullptr)
{
}

RtpBuffer::RtpBuffer(IN RtpDt_UInt32 uiLength, IN RtpDt_UChar* pBuffer)
{
    m_uiLength = 0;
    m_pBuffer = nullptr;

    if (uiLength > RTP_ZERO)
    {
        m_uiLength = uiLength;
        m_pBuffer = new RtpDt_UChar[m_uiLength];

        if (pBuffer != nullptr)
        {
            memcpy(m_pBuffer, pBuffer, m_uiLength);
        }
        else
        {
            memset(m_pBuffer, RTP_ZERO, m_uiLength);
        }
    }
}

RtpBuffer::~RtpBuffer()
{
    if (m_pBuffer != nullptr)
    {
        delete[] m_pBuffer;
    }
}

RtpDt_Void RtpBuffer::setLength(IN RtpDt_UInt32 uiLen)
{
    m_uiLength = uiLen;
}

RtpDt_UInt32 RtpBuffer::getLength()
{
    return m_uiLength;
}

RtpDt_Void RtpBuffer::setBuffer(IN RtpDt_UChar* pBuff)
{
    m_pBuffer = pBuff;
}

RtpDt_UChar* RtpBuffer::getBuffer()
{
    return m_pBuffer;
}

RtpDt_Void RtpBuffer::setBufferInfo(IN RtpDt_UInt32 uiLength, IN RtpDt_UChar* pBuffer)
{
    m_uiLength = uiLength;
    m_pBuffer = pBuffer;
}
