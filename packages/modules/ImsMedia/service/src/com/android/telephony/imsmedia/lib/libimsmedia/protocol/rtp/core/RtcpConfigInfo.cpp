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

#include <RtcpConfigInfo.h>
#include <string.h>

RtcpConfigInfo::RtcpConfigInfo() :
        m_uiSdesItemCnt(RTP_ZERO),
        m_uiByeReasonSize(RTP_ZERO),
        m_uiAppDepDataSize(RTP_ZERO),
        m_bEnaRtcpAppPktSend(eRTP_FALSE)
{
    for (RtpDt_UInt32 uiCount = RTP_ZERO; uiCount < RTP_MAX_SDES_TYPE; uiCount++)
    {
        m_arrSdesInfo[uiCount].pValue = nullptr;
        m_arrSdesInfo[uiCount].ucType = RTP_ZERO;
        m_arrSdesInfo[uiCount].ucLength = RTP_ZERO;
    }
}  // RtcpConfigInfo

RtcpConfigInfo::~RtcpConfigInfo()
{
    for (RtpDt_UInt32 uiCount = RTP_ZERO; uiCount < RTP_MAX_SDES_TYPE; uiCount++)
    {
        if (m_arrSdesInfo[uiCount].pValue != nullptr)
        {
            delete[] m_arrSdesInfo[uiCount].pValue;
            m_arrSdesInfo[uiCount].pValue = nullptr;
            m_arrSdesInfo[uiCount].ucLength = 0;
        }
    }
}

RtpDt_Void RtcpConfigInfo::setByeReasonSize(IN RtpDt_UInt32 uiByeReason)
{
    m_uiByeReasonSize = uiByeReason;
}

RtpDt_UInt32 RtcpConfigInfo::getByeReasonSize()
{
    return m_uiByeReasonSize;
}

RtpDt_Void RtcpConfigInfo::setAppDepDataSize(IN RtpDt_UInt32 uiAppDepSize)
{
    m_uiAppDepDataSize = uiAppDepSize;
}

RtpDt_UInt32 RtcpConfigInfo::getAppDepDataSize()
{
    return m_uiAppDepDataSize;
}

RtpDt_UInt32 RtcpConfigInfo::estimateSdesPktSize()
{
    RtpDt_UInt32 uiSdesPktSize = RTP_WORD_SIZE;
    for (RtpDt_UInt32 uiCount = RTP_ZERO; uiCount < RTP_MAX_SDES_TYPE; uiCount++)
    {
        if (m_arrSdesInfo[uiCount].pValue != nullptr)
        {
            uiSdesPktSize += m_arrSdesInfo[uiCount].ucLength;
            uiSdesPktSize += RTP_TWO;
        }
    }
    RtpDt_UInt32 uiTmpSize = uiSdesPktSize % RTP_WORD_SIZE;
    if (uiTmpSize != RTP_ZERO)
    {
        uiTmpSize = RTP_WORD_SIZE - uiTmpSize;
    }
    uiSdesPktSize = uiSdesPktSize + uiTmpSize;
    return uiSdesPktSize;
}  // estimateSdesPktSize

eRtp_Bool RtcpConfigInfo::addRtcpSdesItem(IN tRTCP_SDES_ITEM* pstSdesItem, IN RtpDt_UInt32 uiIndex)
{
    if (pstSdesItem == nullptr)
    {
        return eRTP_FAILURE;
    }
    m_arrSdesInfo[uiIndex].ucType = pstSdesItem->ucType;
    m_arrSdesInfo[uiIndex].ucLength = pstSdesItem->ucLength;
    if (pstSdesItem->ucLength > RTP_ZERO)
    {
        RtpDt_UChar* pcBuffer = new RtpDt_UChar[pstSdesItem->ucLength];
        if (pcBuffer == nullptr)
        {
            return eRTP_FALSE;
        }
        memcpy(pcBuffer, pstSdesItem->pValue, pstSdesItem->ucLength);
        if (m_arrSdesInfo[uiIndex].pValue != nullptr)
        {
            delete[] m_arrSdesInfo[uiIndex].pValue;
        }

        m_arrSdesInfo[uiIndex].pValue = pcBuffer;
    }
    else
    {
        return eRTP_FALSE;
    }
    m_arrSdesInfo[uiIndex].uiFreq = pstSdesItem->uiFreq;

    m_uiSdesItemCnt++;
    return eRTP_SUCCESS;
}

RtpDt_Void RtcpConfigInfo::enableRtcpAppPktSend()
{
    m_bEnaRtcpAppPktSend = eRTP_SUCCESS;
}

eRtp_Bool RtcpConfigInfo::isRtcpAppPktSendEnable()
{
    return m_bEnaRtcpAppPktSend;
}

RtpDt_UInt32 RtcpConfigInfo::getSdesItemCount()
{
    return m_uiSdesItemCnt;
}

RtpDt_Void RtcpConfigInfo::setSdesItemCount(IN RtpDt_UInt32 uiSdesItemCnt)
{
    m_uiSdesItemCnt = uiSdesItemCnt;
}

tRTCP_SDES_ITEM* RtcpConfigInfo::getRtcpSdesItem(IN RtpDt_UInt32 uiIndex)
{
    if (uiIndex >= RTP_MAX_SDES_TYPE)
        return nullptr;

    return &m_arrSdesInfo[uiIndex];
}
