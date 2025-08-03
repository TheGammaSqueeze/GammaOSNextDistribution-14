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

#ifndef IMS_MEDIA_DATA_QUEUE_H
#define IMS_MEDIA_DATA_QUEUE_H

#include <ImsMediaDefine.h>
#include <list>

using namespace std;

class DataEntry
{
public:
    DataEntry()
    {
        pbBuffer = nullptr;
        nBufferSize = 0;
        nTimestamp = 0;
        bMark = false;
        nSeqNum = 0;
        bHeader = false;
        bValid = false;
        arrivalTime = 0;
        eDataType = MEDIASUBTYPE_UNDEFINED;
        subtype = MEDIASUBTYPE_UNDEFINED;
    }

    DataEntry(const DataEntry& entry)
    {
        pbBuffer = nullptr;

        if (entry.nBufferSize > 0 && entry.pbBuffer != nullptr)
        {
            pbBuffer = new uint8_t[entry.nBufferSize];
            memcpy(pbBuffer, entry.pbBuffer, entry.nBufferSize);
        }

        nBufferSize = entry.nBufferSize;
        nTimestamp = entry.nTimestamp;
        bMark = entry.bMark;
        nSeqNum = entry.nSeqNum;
        bHeader = entry.bHeader;
        bValid = entry.bValid;
        arrivalTime = entry.arrivalTime;
        eDataType = entry.eDataType;
        subtype = entry.subtype;
    }

    ~DataEntry() {}

    void deleteBuffer()
    {
        if (pbBuffer != nullptr)
        {
            delete[] pbBuffer;
        }
    }

    uint8_t* pbBuffer;     // The data buffer
    uint32_t nBufferSize;  // The size of data
    /** The timestamp of data, it can be milliseconds unit or rtp timestamp unit */
    uint32_t nTimestamp;
    /** The flag when the data has marker bit set */
    bool bMark;
    /** The sequence number of data. it is 0 when there is no valid sequence number set */
    uint16_t nSeqNum;
    /** The flag when the data frame is header of the fragmented packet */
    bool bHeader;
    /** The flag when the data is fully integrated from fragmented packet */
    bool bValid;
    /** The arrival time of the packet */
    uint32_t arrivalTime;
    /** The additional data type for the video frames */
    ImsMediaSubType eDataType;
    /**The subtype of data stored in the queue. It can be various subtype according to the
     * characteristics of the given data */
    ImsMediaSubType subtype;
};

/*!
 *    @class ImsMediaDataQueue
 *    @brief
 */
class ImsMediaDataQueue
{
public:
    ImsMediaDataQueue();
    virtual ~ImsMediaDataQueue();

private:
    ImsMediaDataQueue(const ImsMediaDataQueue& obj);
    ImsMediaDataQueue& operator=(const ImsMediaDataQueue& obj);

public:
    void Add(DataEntry* pEntry);
    void InsertAt(uint32_t index, DataEntry* pEntry);
    void Delete();
    void Clear();
    bool Get(DataEntry** ppEntry);
    bool GetLast(DataEntry** ppEntry);
    bool GetAt(uint32_t index, DataEntry** ppEntry);
    uint32_t GetCount();
    void SetReadPosFirst();
    bool GetNext(DataEntry** ppEntry);

private:
    list<DataEntry*> mList;  // data list
    list<DataEntry*>::iterator mListIter;
    std::mutex mMutex;
};

#endif