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

#ifndef VIDEO_JITTER_BUFFER_H_INCLUDEED
#define VIDEO_JITTER_BUFFER_H_INCLUDEED

#include <ImsMediaDefine.h>
#include <BaseJitterBuffer.h>
#include <ImsMediaVideoUtil.h>
#include <ImsMediaTimer.h>
#include <mutex>
#include <list>

class VideoJitterBuffer : public BaseJitterBuffer
{
public:
    VideoJitterBuffer();
    virtual ~VideoJitterBuffer();

    /**
     * @brief Set the Jitter Buffer Size
     *
     * @param nInit initial size of jitter buffer in milliseconds unit
     * @param nMin minimum size of jitter buffer in milliseconds unit
     * @param nMax maximum size of jitter buffer in milliseconds unit
     */
    virtual void SetJitterBufferSize(uint32_t nInit, uint32_t nMin, uint32_t nMax);
    virtual uint32_t GetCount();
    virtual void Reset();
    virtual void Delete();
    virtual void Add(ImsMediaSubType subtype, uint8_t* pbBuffer, uint32_t nBufferSize,
            uint32_t nTimeStamp, bool mark, uint32_t nSeqNum, ImsMediaSubType nDataType,
            uint32_t arrivalTime);
    virtual bool Get(ImsMediaSubType* psubtype, uint8_t** ppData, uint32_t* pnDataSize,
            uint32_t* ptimestamp, bool* pmark, uint32_t* pnSeqNum, uint32_t currentTime,
            ImsMediaSubType* pDataType = nullptr);

    /**
     * @brief Set the video codec type
     *
     * @param type The video codec type defined in ImsMediaDefine.h
     */
    void SetCodecType(uint32_t type);

    /**
     * @brief Set the video framerate
     */
    void SetFramerate(uint32_t framerate);

    /**
     * @brief Set the response wait time. A sender should ignore FIR messages that arrive within
     * Response Wait Time (RWT) duration after responding to a previous FIR message. Response Wait
     * Time (RWT) is defined as RTP-level round-trip time, estimated by RTCP or some other means,
     * plus twice the frame duration.
     *
     * @param time The response wait time in milliseconds unit
     */
    void SetResponseWaitTime(const uint32_t time);

    /**
     * @brief Start the packet loss monitoring timer to check the packet loss rate
     *
     * @param time The time duration of seconds unit to monitor the packet loss
     * @param rate The packet loss rate in the monitoring duration range
     */
    void StartTimer(uint32_t time, uint32_t rate);

    /**
     * @brief Stop the packet loss monitoring timer
     */
    void StopTimer();

private:
    bool CheckHeader(uint8_t* pbBuffer);
    void CheckValidIDR(DataEntry* pIDREntry);
    void InitLostPktList();
    void RemovePacketFromLostList(uint16_t seqNum, bool bRemOldPkt = false);
    void CheckPacketLoss(uint16_t seqNum, uint16_t nLastRecvPkt);
    bool UpdateLostPacketList(uint16_t mLossRateThreshold, uint16_t* countSecondNack,
            uint16_t* nPLIPkt, bool* bPLIPkt);
    bool UpdateNackStatus(LostPacket* pTempEntry, uint16_t mLossRateThreshold,
            uint16_t* countSecondNack, uint16_t* nPLIPkt, bool* bPLIPkt);
    void RequestSendNack(
            uint16_t nLossGap, uint16_t PID, uint16_t countSecondNack, bool bNACK = true);
    void RequestToSendPictureLost(uint32_t eType);
    void RequestToSendTmmbr(uint32_t bitrate);
    static void OnTimer(hTimerHandler hTimer, void* pUserData);
    void ProcessTimer();
    void CheckBitrateAdaptation(double lossRate);

private:
    bool mNewInputData;
    uint32_t mFramerate;
    uint32_t mFrameInterval;
    uint32_t mMaxSaveFrameNum;
    uint32_t mSavedFrameNum;
    uint32_t mMarkedFrameNum;
    uint32_t mLastPlayedTime;
    uint32_t mNumAddedPacket;
    uint32_t mNumLossPacket;
    uint64_t mAccumulatedPacketSize;
    uint32_t mLastAddedTimestamp;
    uint32_t mLastAddedSeqNum;
    uint32_t mResponseWaitTime;
    std::list<LostPacket*> mLostPktList;
    uint32_t mIDRCheckCnt;
    uint32_t mFirTimeStamp;
    uint32_t mMaxBitrate;
    uint32_t mRequestedBitrate;
    uint32_t mIncomingBitrate;
    uint32_t mLossDuration;
    uint32_t mLossRateThreshold;
    uint32_t mCountTimerExpired;
    hTimerHandler mTimer;
    std::mutex mMutexTimer;
};
#endif
