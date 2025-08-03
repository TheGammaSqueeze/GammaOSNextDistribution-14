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

#include <TextJitterBuffer.h>
#include <ImsMediaTrace.h>

TextJitterBuffer::TextJitterBuffer() :
        BaseJitterBuffer()
{
}

TextJitterBuffer::~TextJitterBuffer() {}

void TextJitterBuffer::Add(ImsMediaSubType subtype, uint8_t* buffer, uint32_t size,
        uint32_t timestamp, bool mark, uint32_t seqNum, ImsMediaSubType /*dataType*/,
        uint32_t arrivalTime)
{
    if (subtype == MEDIASUBTYPE_REFRESHED)
    {
        mSsrc = size;
        IMLOGI1("[Add] ssrc[%u]", mSsrc);
        Reset();
        return;
    }

    IMLOGD_PACKET6(IM_PACKET_LOG_JITTER,
            "[Add] seq[%u], mark[%u], TS[%u], size[%u], lastPlayedSeq[%u], arrivalTime[%u]", seqNum,
            mark, timestamp, size, mLastPlayedSeqNum, arrivalTime);

    std::lock_guard<std::mutex> guard(mMutex);

    if (mFirstFrameReceived && USHORT_SEQ_ROUND_COMPARE(mLastPlayedSeqNum, seqNum))
    {
        IMLOGD_PACKET2(IM_PACKET_LOG_JITTER, "[Add] receive old frame, seq[%u], LastPlayedSeq[%u]",
                seqNum, mLastPlayedSeqNum);
        return;
    }

    DataEntry currEntry = DataEntry();
    currEntry.subtype = subtype;
    currEntry.pbBuffer = buffer;
    currEntry.nBufferSize = size;
    currEntry.nTimestamp = timestamp;
    currEntry.bMark = mark;
    currEntry.nSeqNum = seqNum;
    currEntry.bHeader = true;
    currEntry.bValid = true;
    currEntry.arrivalTime = arrivalTime;

    if (mDataQueue.GetCount() == 0)  // jitter buffer is empty
    {
        mDataQueue.Add(&currEntry);
    }
    else
    {
        DataEntry* pEntry = nullptr;
        mDataQueue.GetLast(&pEntry);

        if (pEntry == nullptr)
        {
            return;
        }

        if (USHORT_SEQ_ROUND_COMPARE(pEntry->nSeqNum, seqNum))  // a >= b
        {
            uint32_t i = 0;
            mDataQueue.SetReadPosFirst();

            while (mDataQueue.GetNext(&pEntry))
            {
                if (seqNum == pEntry->nSeqNum)
                {
                    IMLOGD_PACKET1(IM_PACKET_LOG_JITTER, "[Add] Redundant seq[%u]", seqNum);
                    break;
                }
                else if (!USHORT_SEQ_ROUND_COMPARE(seqNum, pEntry->nSeqNum))
                {
                    IMLOGD_PACKET2(IM_PACKET_LOG_JITTER, "[Add] InsertAt[%u] seq[%u]", i, seqNum);
                    mDataQueue.InsertAt(i, &currEntry);
                    break;
                }
                i++;
            }
        }
        else  // a < b
        {
            IMLOGD_PACKET1(
                    IM_PACKET_LOG_JITTER, "[Add] current data is the latest seq[%u]", seqNum);
            mDataQueue.Add(&currEntry);
        }
    }
}

bool TextJitterBuffer::Get(ImsMediaSubType* subtype, uint8_t** data, uint32_t* dataSize,
        uint32_t* timestamp, bool* mark, uint32_t* seqNum, uint32_t /*currentTime*/,
        ImsMediaSubType* /*pDataType*/)
{
    std::lock_guard<std::mutex> guard(mMutex);
    DataEntry* pEntry;

    if (mDataQueue.Get(&pEntry) == true && pEntry != nullptr)
    {
        if (subtype)
            *subtype = pEntry->subtype;
        if (data)
            *data = pEntry->pbBuffer;
        if (dataSize)
            *dataSize = pEntry->nBufferSize;
        if (timestamp)
            *timestamp = pEntry->nTimestamp;
        if (mark)
            *mark = pEntry->bMark;
        if (seqNum)
            *seqNum = pEntry->nSeqNum;

        IMLOGD_PACKET5(IM_PACKET_LOG_JITTER,
                "[Get] OK - seq[%u], mark[%u], TS[%u], size[%u], queue[%u]", pEntry->nSeqNum,
                pEntry->bMark, pEntry->nTimestamp, pEntry->nBufferSize, mDataQueue.GetCount());

        return true;
    }
    else
    {
        if (subtype)
            *subtype = MEDIASUBTYPE_UNDEFINED;
        if (data)
            *data = nullptr;
        if (dataSize)
            *dataSize = 0;
        if (timestamp)
            *timestamp = 0;
        if (mark)
            *mark = false;
        if (seqNum)
            *seqNum = 0;

        IMLOGD_PACKET0(IM_PACKET_LOG_JITTER, "[Get] fail");

        return false;
    }
}

void TextJitterBuffer::Delete()
{
    DataEntry* pEntry;
    std::lock_guard<std::mutex> guard(mMutex);
    mDataQueue.Get(&pEntry);

    if (pEntry == nullptr)
    {
        return;
    }

    if (!mFirstFrameReceived)
    {
        mFirstFrameReceived = true;
    }

    mLastPlayedSeqNum = pEntry->nSeqNum;
    mLastPlayedTimestamp = pEntry->nTimestamp;
    mDataQueue.Delete();
}