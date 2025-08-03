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

#include <RtcpChunk.h>
#include <RtpTrace.h>

RtcpChunk::RtcpChunk() :
        m_uiSsrc(RTP_ZERO),
        m_stSdesItemList(std::list<tRTCP_SDES_ITEM*>())
{
}

RtcpChunk::~RtcpChunk()
{
    // delete all tRTCP_SDES_ITEM objects from SdesItemList
    for (const auto& pstSdesItem : m_stSdesItemList)
    {
        if (pstSdesItem->pValue != nullptr)
        {
            delete[] pstSdesItem->pValue;
        }
        delete pstSdesItem;
    }
    m_stSdesItemList.clear();
}

RtpDt_Void RtcpChunk::setSsrc(IN RtpDt_UInt32 uiSsrc)
{
    m_uiSsrc = uiSsrc;
}

RtpDt_UInt32 RtcpChunk::getSsrc()
{
    return m_uiSsrc;
}

std::list<tRTCP_SDES_ITEM*>& RtcpChunk::getSdesItemList()
{
    return m_stSdesItemList;
}

eRTP_STATUS_CODE RtcpChunk::decodeRtcpChunk(IN RtpDt_UChar* pucChunkBuf,
        IN RtpDt_UInt16& usChunkLen, IN RtcpConfigInfo* pobjRtcpCfgInfo)
{
    // SDES items
    RtpDt_UInt32 uiSdesItemCnt = pobjRtcpCfgInfo->getSdesItemCount();
    eRtp_Bool bCName = eRTP_FALSE;

    while (uiSdesItemCnt > RTP_ZERO)
    {
        tRTCP_SDES_ITEM* pstSdesItem = new tRTCP_SDES_ITEM();
        if (pstSdesItem == nullptr)
        {
            RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
            return RTP_MEMORY_FAIL;
        }
        memset(pstSdesItem, RTP_ZERO, sizeof(tRTCP_SDES_ITEM));

        // type
        pstSdesItem->ucType = *(reinterpret_cast<RtpDt_UChar*>(pucChunkBuf));
        pucChunkBuf = pucChunkBuf + RTP_ONE;
        usChunkLen = usChunkLen + RTP_ONE;

        if (pstSdesItem->ucType == RTP_ONE)
        {
            bCName = eRTP_TRUE;
        }
        // length
        pstSdesItem->ucLength = *(reinterpret_cast<RtpDt_UChar*>(pucChunkBuf));
        pucChunkBuf = pucChunkBuf + RTP_ONE;
        usChunkLen = usChunkLen + RTP_ONE;

        RTP_TRACE_MESSAGE("decodeRtcpChunk , [Sdes item type =%d], [Sdes item length = %d]",
                pstSdesItem->ucType, pstSdesItem->ucLength);

        // value
        RtpDt_UChar* pcSdesBuf = new RtpDt_UChar[pstSdesItem->ucLength];
        if (pcSdesBuf == nullptr)
        {
            RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
            delete pstSdesItem;
            return RTP_MEMORY_FAIL;
        }
        memcpy(pcSdesBuf, pucChunkBuf, pstSdesItem->ucLength);

        pucChunkBuf = pucChunkBuf + pstSdesItem->ucLength;
        usChunkLen = usChunkLen + pstSdesItem->ucLength;
        pstSdesItem->pValue = pcSdesBuf;

        m_stSdesItemList.push_back(pstSdesItem);

        // decrement uiSdesItemCnt by 1
        uiSdesItemCnt = uiSdesItemCnt - RTP_ONE;
    }  // while

    if (bCName == eRTP_FALSE)
    {
        return RTP_DECODE_ERROR;
    }

    return RTP_SUCCESS;
}  // decodeRtcpChunk

eRTP_STATUS_CODE RtcpChunk::formRtcpChunk(OUT RtpBuffer* pobjRtcpPktBuf)
{
    RtpDt_UInt32 uiCurPos = pobjRtcpPktBuf->getLength();
    RtpDt_UChar* pucBuffer = pobjRtcpPktBuf->getBuffer();

    pucBuffer = pucBuffer + uiCurPos;

    // m_uiSsrc
    *(reinterpret_cast<RtpDt_UInt32*>(pucBuffer)) = RtpOsUtil::Ntohl(m_uiSsrc);
    pucBuffer = pucBuffer + RTP_WORD_SIZE;
    uiCurPos = uiCurPos + RTP_WORD_SIZE;

    eRtp_Bool bCName = eRTP_FALSE;

    for (auto& pstSdesItem : m_stSdesItemList)
    {
        // ucType
        *(reinterpret_cast<RtpDt_UChar*>(pucBuffer)) = pstSdesItem->ucType;
        pucBuffer = pucBuffer + RTP_ONE;
        uiCurPos = uiCurPos + RTP_ONE;

        if (pstSdesItem->ucType == RTP_ONE)
        {
            bCName = eRTP_TRUE;
        }

        // ucLength
        *(reinterpret_cast<RtpDt_UChar*>(pucBuffer)) = pstSdesItem->ucLength;
        pucBuffer = pucBuffer + RTP_ONE;
        uiCurPos = uiCurPos + RTP_ONE;

        // pValue
        memcpy(pucBuffer, pstSdesItem->pValue, pstSdesItem->ucLength);
        pucBuffer = pucBuffer + pstSdesItem->ucLength;
        uiCurPos = uiCurPos + pstSdesItem->ucLength;

        // to add type(0)
        uiCurPos = uiCurPos + RTP_ONE;
        pucBuffer[0] = (RtpDt_UChar)RTP_ZERO;
        pucBuffer = pucBuffer + RTP_ONE;

        // to align the memory
        RtpDt_UInt32 uiPadLen = uiCurPos % RTP_WORD_SIZE;
        if (uiPadLen > RTP_ZERO)
        {
            uiPadLen = RTP_WORD_SIZE - uiPadLen;
            uiCurPos = uiCurPos + uiPadLen;
            memset(pucBuffer, RTP_ZERO, uiPadLen);
            pucBuffer = pucBuffer + uiPadLen;
        }
    }  // for

    pobjRtcpPktBuf->setLength(uiCurPos);

    if (bCName == eRTP_FALSE)
    {
        return RTP_ENCODE_ERROR;
    }

    return RTP_SUCCESS;
}  // formRtcpChunk
