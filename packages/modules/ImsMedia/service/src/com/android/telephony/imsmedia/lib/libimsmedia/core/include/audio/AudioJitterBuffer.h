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

#ifndef AUDIO_JITTER_BUFFER_INCLUDED
#define AUDIO_JITTER_BUFFER_INCLUDED

#include <BaseJitterBuffer.h>
#include <JitterNetworkAnalyser.h>

class AudioJitterBuffer : public BaseJitterBuffer
{
public:
    AudioJitterBuffer();
    virtual ~AudioJitterBuffer();
    virtual void Reset();
    virtual void ClearBuffer();
    virtual void SetJitterBufferSize(uint32_t nInit, uint32_t nMin, uint32_t nMax);
    virtual void Add(ImsMediaSubType subtype, uint8_t* pbBuffer, uint32_t nBufferSize,
            uint32_t nTimestamp, bool bMark, uint32_t nSeqNum,
            ImsMediaSubType nDataType = ImsMediaSubType::MEDIASUBTYPE_UNDEFINED,
            uint32_t arrivalTime = 0);
    virtual bool Get(ImsMediaSubType* psubtype, uint8_t** ppData, uint32_t* pnDataSize,
            uint32_t* pnTimestamp, bool* pbMark, uint32_t* pnSeqNum, uint32_t currentTime,
            ImsMediaSubType* pDataType = nullptr);

    /**
     * @brief Set the jitter network analyzer option parameters
     *
     * @param incThreshold The threshold of time difference to increase the jitter buffer size
     * @param decThreshold The threshold of time difference to decrease the jitter buffer size
     * @param stepSize The size how many steps to decrease the jitter buffer size
     * @param weight The weight to calculate margin to the jitter buffer size
     */
    void SetJitterOptions(
            uint32_t incThreshold, uint32_t decThreshold, uint32_t stepSize, double weight);

    /**
     * @brief Set the offset of the sequence number for extracting the redundant frame for EVS
     *
     * @param offset The offset value of sequence number to find redundant frame
     */
    void SetEvsRedundantFrameOffset(const int32_t offset);
    /* set the start time in ms unit */
    void SetStartTime(uint32_t time) { mTimeStarted = time; }
    uint32_t GetCurrentSize() { return mCurrJitterBufferSize; }
    double GetMeanBufferSize();
    /**
     * @brief For EVS, get the redundant frame for lost packet
     *
     * @param lostSeq The sequence number of lost Rtp packet
     * @param ppData full payload of redundant frame if found
     * @param pnDataSize payload size in bytes of redundant frame if found
     * @param hasNextFrame set to true if next to lost frame is found only if
              redundant frame is found
     * @param nextFrameFirstByte first byte of next to lost frame only if
              redundant frame is found
     * @return true when redundant frame is found
     * @return false when redundant frame is not found
     */
    bool GetRedundantFrame(uint32_t lostSeq, uint8_t** ppData, uint32_t* pnDataSize,
            bool* hasNextFrame, uint8_t* nextFrameFirstByte);

    /**
     * @brief Adjust buffering delay by adding the additional delay to synchronize the audio with
     *        the video frames
     *
     * @param delayMs The delay to add
     */
    void SetAdditionalDelay(const int32_t delayMs);

private:
    void Resync(uint32_t spareFrames);
    void CountLostFrames(int32_t currentSeq, int32_t lastSeq);
    uint32_t GetDropVoiceRateInDuration(uint32_t duration, uint32_t currentTime);
    void CollectRxRtpStatus(int32_t seq, kRtpPacketStatus status);
    void CollectJitterBufferStatus(int32_t currSize, int32_t maxSize);
    bool GetPartialRedundancyFrame(const uint32_t currentSeq, uint32_t currentTimestamp,
            const uint32_t offset, DataEntry** entry);
    bool GetNextFrameFirstByte(uint32_t nextSeq, uint8_t* nextFrameFirstByte);

    JitterNetworkAnalyser mJitterAnalyzer;
    bool mDtxPlayed;
    bool mDtxReceived;
    bool mWaiting;
    int32_t mUpdatedDelay;
    uint32_t mCurrPlayingTS;
    uint32_t mCheckUpdateJitterPacketCnt;
    uint32_t mCurrJitterBufferSize;
    uint32_t mNextJitterBufferSize;
    uint32_t mTimeStarted;
    std::list<uint32_t> mListJitterBufferSize;
    std::list<uint32_t> mListVoiceFrames;
    std::list<uint32_t> mListDropVoiceFrames;
    DataEntry* mPreservedDtx;
    int32_t mEvsRedundantFrameOffset;
    uint32_t mPrevGetTime;
    uint32_t mAdditionalDelay;
};

#endif