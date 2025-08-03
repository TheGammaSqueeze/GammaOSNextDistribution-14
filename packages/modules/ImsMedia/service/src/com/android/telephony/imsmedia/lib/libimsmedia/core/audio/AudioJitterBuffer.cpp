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

#include <AudioJitterBuffer.h>
#include <ImsMediaAudioUtil.h>
#include <ImsMediaDataQueue.h>
#include <ImsMediaTimer.h>
#include <ImsMediaTrace.h>
#include <numeric>

#define AUDIO_JITTER_BUFFER_MIN_SIZE    (3)
#define AUDIO_JITTER_BUFFER_MAX_SIZE    (9)
#define AUDIO_JITTER_BUFFER_START_SIZE  (4)
#define GET_SEQ_GAP(a, b)               ((uint16_t)(a) - (uint16_t)(b))
#define JITTER_BUFFER_UPDATE_INTERVAL   (100)   // ms unit
#define FRAME_INTERVAL                  (20)    // ms unit
#define ALLOWABLE_ERROR                 (10)    // ms unit
#define RESET_THRESHOLD_IN_DTX_ENABLED  (80)    // percentage
#define RESET_THRESHOLD_IN_DTX_DISABLED (35)    // percentage
#define TS_ROUND_QUARD                  (3000)  // ms unit
#define SEQ_OUTLIER_THRESHOLD           (3000)
#define USHORT_TS_ROUND_COMPARE(a, b)                                                   \
    ((((a) >= (b)) && (((b) >= TS_ROUND_QUARD) || ((a) <= 0xffff - TS_ROUND_QUARD))) || \
            (((a) <= TS_ROUND_QUARD) && ((b) >= 0xffff - TS_ROUND_QUARD)))

#define MAX_STORED_BUFFER_SIZE (50 * 60 * 60)  // 1 hour in frame interval unit
#define MAX_QUEUE_SIZE         (150)           // 3 sec
#define DROP_WINDOW            (5000)          // 5 ec

AudioJitterBuffer::AudioJitterBuffer()
{
    mInitJitterBufferSize = AUDIO_JITTER_BUFFER_START_SIZE;
    mMinJitterBufferSize = AUDIO_JITTER_BUFFER_MIN_SIZE;
    mMaxJitterBufferSize = AUDIO_JITTER_BUFFER_MAX_SIZE;
    mJitterAnalyzer.Reset();
    mJitterAnalyzer.SetMinMaxJitterBufferSize(mMinJitterBufferSize, mMaxJitterBufferSize);
    mListJitterBufferSize.clear();
    mEvsRedundantFrameOffset = -1;
    AudioJitterBuffer::Reset();
}

AudioJitterBuffer::~AudioJitterBuffer()
{
    AudioJitterBuffer::ClearBuffer();
}

void AudioJitterBuffer::Reset()
{
    IMLOGD0("[Reset]");
    mLastPlayedSeqNum = 0;
    mLastPlayedTimestamp = 0;
    mFirstFrameReceived = false;
    mDtxPlayed = false;
    mDtxReceived = false;
    mWaiting = true;
    mUpdatedDelay = 0;
    mCheckUpdateJitterPacketCnt = 0;
    mPreservedDtx = nullptr;
    mPrevGetTime = 0;
    mListVoiceFrames.clear();
    mListDropVoiceFrames.clear();
    mAdditionalDelay = 0;
}

void AudioJitterBuffer::ClearBuffer()
{
    IMLOGD0("[ClearBuffer]");
    std::lock_guard<std::mutex> guard(mMutex);
    DataEntry* entry = nullptr;

    while (mDataQueue.Get(&entry))
    {
        if (entry->eDataType != MEDIASUBTYPE_AUDIO_SID)
        {
            CollectRxRtpStatus(entry->nSeqNum, kRtpStatusDiscarded);
        }

        mDataQueue.Delete();
    }
}

void AudioJitterBuffer::SetJitterBufferSize(uint32_t nInit, uint32_t nMin, uint32_t nMax)
{
    IMLOGD3("[SetJitterBufferSize] %d, %d, %d", nInit, nMin, nMax);

    if (nMin > 0)
    {
        mMinJitterBufferSize = nMin;
    }

    if (nMax > 0)
    {
        mMaxJitterBufferSize = nMax;
    }

    if (nInit > 0)
    {
        if (nInit < mMinJitterBufferSize)
        {
            nInit = mMinJitterBufferSize;
        }

        if (nInit > mMaxJitterBufferSize)
        {
            nInit = mMaxJitterBufferSize;
        }

        mInitJitterBufferSize = nInit;
        mCurrJitterBufferSize = mInitJitterBufferSize;
        mNextJitterBufferSize = mInitJitterBufferSize;
    }

    mJitterAnalyzer.SetMinMaxJitterBufferSize(mMinJitterBufferSize, mMaxJitterBufferSize);
}

void AudioJitterBuffer::SetJitterOptions(
        uint32_t incThreshold, uint32_t decThreshold, uint32_t stepSize, double zValue)
{
    mJitterAnalyzer.SetJitterOptions(incThreshold, decThreshold, stepSize, zValue);
}

void AudioJitterBuffer::SetEvsRedundantFrameOffset(const int32_t offset)
{
    IMLOGD1("[SetEvsRedundantFrameOffset] offset=%d", offset);
    mEvsRedundantFrameOffset = offset;
}

void AudioJitterBuffer::Add(ImsMediaSubType subtype, uint8_t* pbBuffer, uint32_t nBufferSize,
        uint32_t nTimestamp, bool bMark, uint32_t nSeqNum, ImsMediaSubType nDataType,
        uint32_t arrivalTime)
{
    DataEntry currEntry = DataEntry();
    currEntry.subtype = subtype;
    currEntry.pbBuffer = pbBuffer;
    currEntry.nBufferSize = nBufferSize;
    currEntry.nTimestamp = nTimestamp;
    currEntry.bMark = bMark;
    currEntry.nSeqNum = nSeqNum;
    currEntry.bHeader = true;
    currEntry.bValid = true;
    currEntry.arrivalTime = arrivalTime;
    currEntry.eDataType = nDataType;

    if (subtype == MEDIASUBTYPE_REFRESHED)
    {
        std::lock_guard<std::mutex> guard(mMutex);
        mSsrc = nBufferSize;
        mTimeStarted = arrivalTime;
        mJitterAnalyzer.Reset();
        mCurrJitterBufferSize = mInitJitterBufferSize;
        mNextJitterBufferSize = mCurrJitterBufferSize;
        mDataQueue.Add(&currEntry);

        IMLOGI2("[Add] ssrc=%u, startTime=%d", mSsrc, mTimeStarted);
        return;
    }

    if (currEntry.eDataType == MEDIASUBTYPE_AUDIO_SID)
    {
        mDtxReceived = true;
    }

    int32_t jitter = mJitterAnalyzer.CalculateTransitTimeDifference(nTimestamp, arrivalTime);

    RtpPacket* packet = new RtpPacket();

    switch (currEntry.eDataType)
    {
        case MEDIASUBTYPE_AUDIO_SID:
            packet->rtpDataType = kRtpDataTypeSid;
            break;
        default:
        case MEDIASUBTYPE_AUDIO_NODATA:
            packet->rtpDataType = kRtpDataTypeNoData;
            break;
        case MEDIASUBTYPE_AUDIO_NORMAL:
            packet->rtpDataType = kRtpDataTypeNormal;
            break;
    }

    packet->ssrc = mSsrc;
    packet->seqNum = nSeqNum;
    packet->jitter = jitter;
    packet->arrival = arrivalTime;
    packet->timestamp = nTimestamp;
    mCallback->SendEvent(kCollectPacketInfo, kStreamRtpRx, reinterpret_cast<uint64_t>(packet));

    std::lock_guard<std::mutex> guard(mMutex);

    IMLOGD_PACKET8(IM_PACKET_LOG_JITTER,
            "[Add] seq=%d, mark=%d, TS=%d, size=%d, jitter=%d, queue=%d, playingDiff=%d, "
            "arrival=%d",
            nSeqNum, bMark, nTimestamp, nBufferSize, jitter, mDataQueue.GetCount() + 1,
            mCurrPlayingTS - nTimestamp, arrivalTime);

    if (mDataQueue.GetCount() == 0)
    {  // jitter buffer is empty
        mDataQueue.Add(&currEntry);
    }
    else
    {
        DataEntry* pEntry;
        mDataQueue.GetLast(&pEntry);

        if (pEntry == nullptr)
        {
            return;
        }

        // current data is the latest data
        if (USHORT_SEQ_ROUND_COMPARE(nSeqNum, pEntry->nSeqNum))
        {
            mDataQueue.Add(&currEntry);
        }
        else
        {
            // find the position of current data and insert current data to the correct position
            mDataQueue.SetReadPosFirst();

            for (int32_t i = 0; mDataQueue.GetNext(&pEntry); i++)
            {
                // late arrival packet
                if (!USHORT_SEQ_ROUND_COMPARE(nSeqNum, pEntry->nSeqNum))
                {
                    mDataQueue.InsertAt(i, &currEntry);
                    break;
                }
            }
        }
    }

    if (currEntry.eDataType != MEDIASUBTYPE_AUDIO_SID)
    {
        mListVoiceFrames.push_back(arrivalTime);

        // keep the list 3 times of the drop window
        if (mListVoiceFrames.size() > DROP_WINDOW * 3 / FRAME_INTERVAL)
        {
            mListVoiceFrames.pop_front();
        }
    }

    // update jitter buffer size
    if (!mWaiting && mUpdatedDelay == 0)
    {
        uint32_t nextJitterBufferSize =
                mJitterAnalyzer.GetNextJitterBufferSize(mCurrJitterBufferSize, arrivalTime);
        mCheckUpdateJitterPacketCnt = 0;
        mUpdatedDelay = nextJitterBufferSize - mCurrJitterBufferSize;
    }
}

bool AudioJitterBuffer::Get(ImsMediaSubType* psubtype, uint8_t** ppData, uint32_t* pnDataSize,
        uint32_t* pnTimestamp, bool* pbMark, uint32_t* pnSeqNum, uint32_t currentTime,
        ImsMediaSubType* pDataType)
{
    std::lock_guard<std::mutex> guard(mMutex);

    IMLOGD_PACKET1(IM_PACKET_LOG_JITTER, "[Get] time diff=%d", currentTime - mPrevGetTime);
    mPrevGetTime = currentTime;

    DataEntry* pEntry = nullptr;
    bool bForceToPlay = false;
    mCheckUpdateJitterPacketCnt++;

    if (mDataQueue.Get(&pEntry) && pEntry->subtype == MEDIASUBTYPE_REFRESHED)  // ssrc changed
    {
        Reset();
        mDataQueue.Delete();  // delete indication frame of ssrc

        if (!mWaiting && mDataQueue.Get(&pEntry))  // get next frame
        {
            mCurrPlayingTS = pEntry->nTimestamp;  // play directly
            mWaiting = false;
        }
    }

    // increase delay
    if (!mWaiting && mDtxPlayed && mUpdatedDelay > 0)
    {
        IMLOGD2("[Get] increase delay=%d, curTS=%d", mUpdatedDelay, mCurrPlayingTS);
        mUpdatedDelay--;
        mCurrJitterBufferSize++;
        return false;
    }

    // decrease delay
    if (!mWaiting && mDataQueue.Get(&pEntry) && pEntry->eDataType == MEDIASUBTYPE_AUDIO_SID &&
            mUpdatedDelay < 0)
    {
        IMLOGD3("[Get] decrease delay=%d, curTS=%u, queue=%u", mUpdatedDelay, mCurrPlayingTS,
                mDataQueue.GetCount());
        mUpdatedDelay++;
        mCurrJitterBufferSize--;
        mCurrPlayingTS += FRAME_INTERVAL;
    }

    int32_t dropRate = GetDropVoiceRateInDuration(DROP_WINDOW, currentTime);

    // resync the jitter buffer
    if ((dropRate > RESET_THRESHOLD_IN_DTX_ENABLED && mDtxReceived) ||
            (dropRate > RESET_THRESHOLD_IN_DTX_DISABLED && !mDtxReceived))
    {
        if (mCurrJitterBufferSize == mMaxJitterBufferSize)
        {
            IMLOGD1("[Get] resync, drop rate=%u", dropRate);
            mWaiting = true;
            mTimeStarted = currentTime;
        }
        else
        {
            IMLOGD1("[Get] increase delay by drop rate=%u", dropRate);
            mCurrPlayingTS -= FRAME_INTERVAL;
            mCurrJitterBufferSize++;
        }

        mListDropVoiceFrames.clear();
    }

    if (mDataQueue.GetCount() == 0)
    {
        IMLOGD_PACKET1(IM_PACKET_LOG_JITTER, "[Get] fail - empty, curTS=%u", mCurrPlayingTS);

        if (!mWaiting)
        {
            mCurrPlayingTS += FRAME_INTERVAL;
        }

        return false;
    }
    else if (mDataQueue.Get(&pEntry) && mWaiting)
    {
        if (currentTime - mTimeStarted + ALLOWABLE_ERROR < mCurrJitterBufferSize * FRAME_INTERVAL)
        {
            if (psubtype)
                *psubtype = MEDIASUBTYPE_UNDEFINED;
            if (ppData)
                *ppData = nullptr;
            if (pnDataSize)
                *pnDataSize = 0;
            if (pnTimestamp)
                *pnTimestamp = 0;
            if (pbMark)
                *pbMark = false;
            if (pnSeqNum)
                *pnSeqNum = 0;
            if (pDataType)
                *pDataType = MEDIASUBTYPE_UNDEFINED;

            IMLOGD_PACKET5(IM_PACKET_LOG_JITTER,
                    "[Get] Wait - timeStarted=%d, seq=%u, CurrJBSize=%u, delay=%u, "
                    "QueueCount=%u",
                    mTimeStarted, pEntry->nSeqNum, mCurrJitterBufferSize,
                    currentTime - pEntry->arrivalTime, GetCount());
            return false;
        }
        else
        {
            // resync when the audio frame stacked over the current jitter buffer size
            Resync(mCurrJitterBufferSize + 1);
            mWaiting = false;
        }
    }

    mListJitterBufferSize.push_back(mCurrJitterBufferSize);

    if (mListJitterBufferSize.size() > MAX_STORED_BUFFER_SIZE)  // 1hour
    {
        mListJitterBufferSize.pop_front();
    }

    // report duplicated packet
    while (mDataQueue.Get(&pEntry) && mFirstFrameReceived && pEntry->nSeqNum == mLastPlayedSeqNum &&
            pEntry->nTimestamp == mLastPlayedTimestamp)
    {
        IMLOGD6("[Get] duplicate - curTS=%u, seq=%d, mark=%d, TS=%u, size=%d, queue=%d",
                mCurrPlayingTS, pEntry->nSeqNum, pEntry->bMark, pEntry->nTimestamp,
                pEntry->nBufferSize, mDataQueue.GetCount());
        CollectRxRtpStatus(pEntry->nSeqNum, kRtpStatusDuplicated);
        mDataQueue.Delete();
    }

    if (currentTime - mTimeStarted < 3000)
    {
        // resync when the audio frame stacked over the max jitter buffer size
        Resync(mMaxJitterBufferSize);
    }
    else
    {
        Resync(MAX_QUEUE_SIZE);
    }

    // adjust the playing timestamp
    if (mDataQueue.Get(&pEntry) && pEntry->nTimestamp != mCurrPlayingTS &&
            ((mCurrPlayingTS - ALLOWABLE_ERROR) <= pEntry->nTimestamp) &&
            (pEntry->nTimestamp <= (mCurrPlayingTS + ALLOWABLE_ERROR)))
    {
        IMLOGD3("[Get] sync playing curTS=%u, TS=%u, seq=%d", mCurrPlayingTS, pEntry->nTimestamp,
                pEntry->nSeqNum);
        mCurrPlayingTS = pEntry->nTimestamp;
    }

    // delete late arrival
    while (mDataQueue.Get(&pEntry) && !USHORT_TS_ROUND_COMPARE(pEntry->nTimestamp, mCurrPlayingTS))
    {
        mDtxPlayed = (pEntry->eDataType == MEDIASUBTYPE_AUDIO_SID);

        // discard case that latest packet is about to cut by the jitter then update the
        // sequence number to avoid incorrect lost counting
        if (pEntry->nSeqNum >= mLastPlayedSeqNum)
        {
            CountLostFrames(pEntry->nSeqNum, mLastPlayedSeqNum);
            mLastPlayedSeqNum = pEntry->nSeqNum;
            mLastPlayedTimestamp = pEntry->nTimestamp;
        }

        IMLOGD_PACKET4(IM_PACKET_LOG_JITTER,
                "[Get] delete late arrival, dtx=%d, seq=%d, curTS=%u, TS=%u", mDtxPlayed,
                pEntry->nSeqNum, mCurrPlayingTS, pEntry->nTimestamp);

        if (mPreservedDtx != nullptr)
        {
            delete mPreservedDtx;
        }

        mPreservedDtx = nullptr;

        if (mDtxPlayed)
        {
            mPreservedDtx = new DataEntry(*pEntry);
        }
        else
        {
            CollectRxRtpStatus(pEntry->nSeqNum, kRtpStatusLate);
            mListDropVoiceFrames.push_back(currentTime);

            // keep the list 3 times of the drop window
            if (mListDropVoiceFrames.size() > DROP_WINDOW * 3 / FRAME_INTERVAL)
            {
                mListDropVoiceFrames.pop_front();
            }
        }

        mJitterAnalyzer.SetLateArrivals(currentTime);
        mDataQueue.Delete();
    }

    // add condition in case of changing Seq# & TS
    if (mDataQueue.Get(&pEntry) && (pEntry->nTimestamp - mCurrPlayingTS) > TS_ROUND_QUARD)
    {
        IMLOGD4("[Get] TS changing case, enforce play [ %d / %u / %u / %d ]", pEntry->nSeqNum,
                pEntry->nTimestamp, mCurrPlayingTS, mDataQueue.GetCount());
        bForceToPlay = true;
    }

    if (mDataQueue.Get(&pEntry) &&
            (pEntry->nTimestamp == mCurrPlayingTS || bForceToPlay ||
                    (pEntry->nTimestamp < TS_ROUND_QUARD && mCurrPlayingTS > 0xFFFF)))
    {
        if (psubtype)
            *psubtype = pEntry->subtype;
        if (ppData)
            *ppData = pEntry->pbBuffer;
        if (pnDataSize)
            *pnDataSize = pEntry->nBufferSize;
        if (pnTimestamp)
            *pnTimestamp = pEntry->nTimestamp;
        if (pbMark)
            *pbMark = pEntry->bMark;
        if (pnSeqNum)
            *pnSeqNum = pEntry->nSeqNum;
        if (pDataType)
            *pDataType = pEntry->eDataType;

        mDtxPlayed = (pEntry->eDataType == MEDIASUBTYPE_AUDIO_SID);

        if (mFirstFrameReceived)
        {
            CountLostFrames(pEntry->nSeqNum, mLastPlayedSeqNum);
        }

        IMLOGD_PACKET7(IM_PACKET_LOG_JITTER,
                "[Get] OK, dtx=%d, curTS=%u, seq=%u, TS=%u, size=%u, delay=%u, curSize=%u",
                mDtxPlayed, mCurrPlayingTS, pEntry->nSeqNum, pEntry->nTimestamp,
                pEntry->nBufferSize, currentTime - pEntry->arrivalTime, mCurrJitterBufferSize);

        mCurrPlayingTS = pEntry->nTimestamp + FRAME_INTERVAL;
        mFirstFrameReceived = true;
        mLastPlayedSeqNum = pEntry->nSeqNum;
        mLastPlayedTimestamp = pEntry->nTimestamp;
        CollectRxRtpStatus(pEntry->nSeqNum, kRtpStatusNormal);
        CollectJitterBufferStatus(
                mCurrJitterBufferSize * FRAME_INTERVAL, mMaxJitterBufferSize * FRAME_INTERVAL);

        if (mPreservedDtx != nullptr)
        {
            delete mPreservedDtx;
            mPreservedDtx = nullptr;
        }

        return true;
    }
    else
    {
        // use the preserved dtx when it is discarded as late arrival
        if (mPreservedDtx != nullptr)
        {
            // push front the preserved dtx to the queue
            mDataQueue.InsertAt(0, mPreservedDtx);
            delete mPreservedDtx;
            mPreservedDtx = nullptr;

            mDataQueue.Get(&pEntry);

            if (psubtype)
                *psubtype = pEntry->subtype;
            if (ppData)
                *ppData = pEntry->pbBuffer;
            if (pnDataSize)
                *pnDataSize = pEntry->nBufferSize;
            if (pnTimestamp)
                *pnTimestamp = mCurrPlayingTS;
            if (pbMark)
                *pbMark = pEntry->bMark;
            if (pnSeqNum)
                *pnSeqNum = pEntry->nSeqNum;
            if (pDataType)
                *pDataType = pEntry->eDataType;

            IMLOGD_PACKET3(IM_PACKET_LOG_JITTER,
                    "[Get] OK, preserved frame, dtx=%d, curTS=%u, current=%u", mDtxPlayed,
                    mCurrPlayingTS, currentTime);

            mLastPlayedSeqNum = pEntry->nSeqNum;
            mLastPlayedTimestamp = pEntry->nTimestamp;
            mCurrPlayingTS += FRAME_INTERVAL;
            return true;
        }
        if (psubtype)
            *psubtype = MEDIASUBTYPE_UNDEFINED;
        if (ppData)
            *ppData = nullptr;
        if (pnDataSize)
            *pnDataSize = 0;
        if (pnTimestamp)
            *pnTimestamp = 0;
        if (pbMark)
            *pbMark = false;
        if (pnSeqNum)
            *pnSeqNum = 0;
        if (pDataType)
            *pDataType = MEDIASUBTYPE_UNDEFINED;

        IMLOGD_PACKET3(IM_PACKET_LOG_JITTER, "[Get] fail - dtx=%d, curTS=%u, current=%u",
                mDtxPlayed, mCurrPlayingTS, currentTime);

        mCurrPlayingTS += FRAME_INTERVAL;
        return false;
    }

    return false;
}

double AudioJitterBuffer::GetMeanBufferSize()
{
    return std::accumulate(mListJitterBufferSize.begin(), mListJitterBufferSize.end(), 0.0f) /
            mListJitterBufferSize.size();
}

void AudioJitterBuffer::Resync(uint32_t spareFrames)
{
    bool isDeleted = false;
    DataEntry* entry = nullptr;

    while (mDataQueue.Get(&entry) && GetCount() > spareFrames)
    {
        IMLOGD6("[Resync] state=%d, seq=%d, TS=%d, dtx=%d, queue=%d, spareFrames=%d", mWaiting,
                entry->nSeqNum, entry->nTimestamp, entry->eDataType == MEDIASUBTYPE_AUDIO_SID,
                GetCount(), spareFrames);

        if (entry->eDataType != MEDIASUBTYPE_AUDIO_SID)
        {
            CollectRxRtpStatus(entry->nSeqNum, kRtpStatusDiscarded);
        }

        if (!mWaiting)
        {
            mLastPlayedSeqNum = entry->nSeqNum;
            mLastPlayedTimestamp = entry->nTimestamp;
        }

        mDataQueue.Delete();
        isDeleted = true;
    }

    if ((mWaiting || isDeleted) && mDataQueue.Get(&entry))
    {
        mCurrPlayingTS = entry->nTimestamp;
    }
}

void AudioJitterBuffer::CountLostFrames(int32_t currentSeq, int32_t lastSeq)
{
    /** Report the loss gap if the loss gap is over 0 */
    uint16_t lostGap = GET_SEQ_GAP(currentSeq, lastSeq);

    if (lostGap > 1 && lostGap < SEQ_OUTLIER_THRESHOLD)
    {
        uint16_t lostSeq = lastSeq + 1;
        IMLOGD_PACKET2(IM_PACKET_LOG_JITTER, "[CountLostFrames] lost seq=%u, num=%u", lostSeq,
                lostGap - 1);

        SessionCallbackParameter* param =
                new SessionCallbackParameter(kReportPacketLossGap, lostSeq, lostGap - 1);
        mCallback->SendEvent(kCollectOptionalInfo, reinterpret_cast<uint64_t>(param), 0);
    }
}

uint32_t AudioJitterBuffer::GetDropVoiceRateInDuration(uint32_t duration, uint32_t currentTime)
{
    uint32_t numVoice = std::count_if(mListVoiceFrames.begin(), mListVoiceFrames.end(),
            [duration, currentTime, this](uint32_t frameTime)
            {
                return ((currentTime - frameTime + mCurrJitterBufferSize * FRAME_INTERVAL) <=
                        duration);
            });

    uint32_t numDrop = std::count_if(mListDropVoiceFrames.begin(), mListDropVoiceFrames.end(),
            [duration, currentTime](uint32_t frameTime)
            {
                return (currentTime - frameTime <= duration);
            });

    if (numVoice <= 5)
    {
        return 0;
    }

    return numDrop * 100 / numVoice;
}

void AudioJitterBuffer::CollectRxRtpStatus(int32_t seq, kRtpPacketStatus status)
{
    if (mCallback != nullptr)
    {
        SessionCallbackParameter* param =
                new SessionCallbackParameter(seq, status, ImsMediaTimer::GetTimeInMilliSeconds());
        mCallback->SendEvent(kCollectRxRtpStatus, reinterpret_cast<uint64_t>(param));
    }
}

void AudioJitterBuffer::CollectJitterBufferStatus(int32_t currSize, int32_t maxSize)
{
    if (mCallback != nullptr)
    {
        mCallback->SendEvent(kCollectJitterBufferSize, currSize, maxSize);
    }
}

bool AudioJitterBuffer::GetPartialRedundancyFrame(
        uint32_t lostSeq, uint32_t currentTimestamp, uint32_t offset, DataEntry** entry)
{
    bool foundPartialFrame = false;
    DataEntry* tempEntry = nullptr;
    uint32_t partialSeq = lostSeq + offset;

    // find redundancy frame from the queue
    for (int32_t i = 0; i < mDataQueue.GetCount(); i++)
    {
        if (mDataQueue.GetAt(i, &tempEntry) && tempEntry->nSeqNum == partialSeq)
        {
            foundPartialFrame = true;
            break;
        }

        if (tempEntry->nSeqNum > partialSeq)
        {
            break;
        }
    }

    if (!foundPartialFrame)
    {
        *entry = nullptr;
        IMLOGD_PACKET1(IM_PACKET_LOG_JITTER,
                "[GetPartialRedundancyFrame] lostSeq=%d Redundant Frame not found", lostSeq);
        return false;
    }

    if (tempEntry->eDataType == MEDIASUBTYPE_AUDIO_SID)
    {
        *entry = nullptr;
        IMLOGD_PACKET1(IM_PACKET_LOG_JITTER,
                "[GetPartialRedundancyFrame] lostSeq=%d Redundant Frame is SID", lostSeq);
        return false;
    }

    // If the timestamp of RF is greater than the (currentframe_timestamp + offset*20msec) then it
    // cannot be used for concealment.
    if (tempEntry->nTimestamp > (currentTimestamp + offset * 20))
    {
        *entry = nullptr;
        IMLOGD_PACKET2(IM_PACKET_LOG_JITTER,
                "[GetPartialRedundancyFrame] RF not in offset timeframe. \
                RF_timestamp=%u LostFrame_timestamp=%u",
                tempEntry->nTimestamp, currentTimestamp);
        return false;
    }

    if (tempEntry->nBufferSize == 33 || tempEntry->nBufferSize == 34)
    {
        *entry = tempEntry;
        IMLOGD_PACKET4(IM_PACKET_LOG_JITTER,
                "[GetPartialRedundancyFrame] lostSeq=%d RFSeq=%d, size=%d , curTS=%u", lostSeq,
                tempEntry->nSeqNum, tempEntry->nBufferSize, mCurrPlayingTS);
        return true;
    }

    *entry = nullptr;
    IMLOGD_PACKET1(IM_PACKET_LOG_JITTER,
            "[GetPartialRedundancyFrame] lostSeq=%d Redundant Frame not found", lostSeq);
    return false;
}

bool AudioJitterBuffer::GetNextFrameFirstByte(uint32_t nextSeq, uint8_t* nextFrameFirstByte)
{
    DataEntry* pEntry = nullptr;
    if (mDataQueue.Get(&pEntry) &&
            (pEntry->eDataType != MEDIASUBTYPE_AUDIO_NODATA && pEntry->nSeqNum == nextSeq))
    {
        *nextFrameFirstByte =
                pEntry->pbBuffer[ImsMediaAudioUtil::CheckEVSPrimaryHeaderFullModeFromSize(
                                         pEntry->nBufferSize)
                                ? 1
                                : 0];
        IMLOGD_PACKET2(IM_PACKET_LOG_JITTER,
                "[GetNextFrameFirstByte] nextSeq=%d nextFrameFirstByte[%02X]", pEntry->nSeqNum,
                nextFrameFirstByte[0]);
        return true;
    }
    IMLOGD_PACKET0(IM_PACKET_LOG_JITTER, "[GetNextFrameFirstByte] Next Frame not found");
    return false;
}

bool AudioJitterBuffer::GetRedundantFrame(uint32_t lostSeq, uint8_t** ppData, uint32_t* pnDataSize,
        bool* hasNextFrame, uint8_t* nextFrameFirstByte)
{
    std::lock_guard<std::mutex> guard(mMutex);
    DataEntry* pEntry = nullptr;

    if (!mDtxPlayed &&
            GetPartialRedundancyFrame(lostSeq, mCurrPlayingTS, mEvsRedundantFrameOffset, &pEntry))
    {
        if (ppData)
            *ppData = pEntry->pbBuffer;
        if (pnDataSize)
            *pnDataSize = pEntry->nBufferSize;
        *hasNextFrame = GetNextFrameFirstByte((lostSeq + 1), nextFrameFirstByte);

        return true;
    }

    return false;
}

void AudioJitterBuffer::SetAdditionalDelay(const int32_t delayMs)
{
    std::lock_guard<std::mutex> guard(mMutex);

    if (!mWaiting && mFirstFrameReceived)
    {
        int32_t delayDiff = delayMs - mAdditionalDelay;
        mAdditionalDelay = delayMs;

        int32_t newDelay = delayDiff / FRAME_INTERVAL;
        mCurrPlayingTS -= newDelay * FRAME_INTERVAL;
        mCurrJitterBufferSize += newDelay;
        IMLOGD3("[SetAdditionalDelay] delay=%d, delayDiff=%d, curSize=%d", delayMs, delayDiff,
                mCurrJitterBufferSize);
    }
}