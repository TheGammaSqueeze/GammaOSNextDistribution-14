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

#ifndef BASE_JITTER_BUFFER_H_INCLUDEED
#define BASE_JITTER_BUFFER_H_INCLUDEED

#include <ImsMediaDataQueue.h>
#include <BaseSessionCallback.h>
#include <mutex>

/*!
 *    @class        BaseJitterBuffer
 */
class BaseJitterBuffer
{
public:
    BaseJitterBuffer();
    virtual ~BaseJitterBuffer();
    virtual void SetSessionCallback(BaseSessionCallback* callback);

    /**
     * @brief Set the codec type
     */
    virtual void SetCodecType(uint32_t type);
    virtual void SetJitterBufferSize(uint32_t nInit, uint32_t nMin, uint32_t nMax);

    /**
     * @brief Get the size of the queue
     */
    virtual uint32_t GetCount();

    /**
     * @brief Reset the parameters for playing
     */
    virtual void Reset();

    /**
     * @brief Delete the first data in the queue
     */
    virtual void Delete();

    /**
     * @brief Delete all the data frames in the queue
     */
    virtual void ClearBuffer();

    /**
     * @brief Add data frame to jitter buffer for dejittering
     *
     * @param subtype The subtype of data stored in the queue. It can be various subtype according
     * to the characteristics of the given data
     * @param data The data buffer
     * @param dataSize The size of data
     * @param timestamp The timestamp of data, it can be milliseconds unit or rtp timestamp unit
     * @param mark It is true when the data has marker bit set
     * @param seq The sequence number of data. it is 0 when there is no valid sequence number set
     * @param dataType The additional data type for the video frames
     * @param arrivalTime The arrival time of the packet in milliseconds unit
     */
    virtual void Add(ImsMediaSubType subtype, uint8_t* data, uint32_t dataSize, uint32_t timestamp,
            bool mark, uint32_t seq,
            /** TODO: remove deprecated argument dataType */
            ImsMediaSubType dataType = ImsMediaSubType::MEDIASUBTYPE_UNDEFINED,
            uint32_t arrivalTime = 0) = 0;

    /**
     * @brief Get data frame from the jitter buffer
     *
     * @param subtype The subtype of data stored in the queue. It can be various subtype according
     * to the characteristics of the given data
     * @param data The data buffer
     * @param dataSize The size of data
     * @param timestamp The timestamp of data, it can be milliseconds unit or rtp timestamp unit
     * @param mark It is true when the data has marker bit set
     * @param seq The sequence number of data. it is 0 when there is no valid sequence number set
     * @param currentTime The current timestamp of this method invoked with milliseconds unit
     */
    virtual bool Get(ImsMediaSubType* psubtype, uint8_t** ppData, uint32_t* pnDataSize,
            uint32_t* ptimestamp, bool* pmark, uint32_t* pnSeqNum, uint32_t currentTime,
            ImsMediaSubType* pDataType = nullptr) = 0;
    virtual bool GetRedundantFrame(uint32_t lostSeq, uint8_t** ppData = nullptr,
            uint32_t* pnDataSize = nullptr, bool* hasNextFrame = nullptr,
            uint8_t* nextFrameFirstByte = nullptr);

protected:
    BaseSessionCallback* mCallback;
    bool mFirstFrameReceived;
    uint32_t mSsrc;
    uint32_t mCodecType;
    ImsMediaDataQueue mDataQueue;
    std::mutex mMutex;
    uint32_t mInitJitterBufferSize;
    uint32_t mMinJitterBufferSize;
    uint32_t mMaxJitterBufferSize;
    bool mNewInputData;
    uint16_t mLastPlayedSeqNum;
    uint32_t mLastPlayedTimestamp;
    uint32_t mMaxSaveFrameNum;
};

#endif