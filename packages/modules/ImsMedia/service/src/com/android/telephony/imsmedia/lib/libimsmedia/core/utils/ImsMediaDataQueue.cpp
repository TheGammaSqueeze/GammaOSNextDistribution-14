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

#include <ImsMediaDataQueue.h>
#include <string.h>

ImsMediaDataQueue::ImsMediaDataQueue() {}

ImsMediaDataQueue::~ImsMediaDataQueue()
{
    Clear();
}

void ImsMediaDataQueue::Add(DataEntry* pEntry)
{
    if (pEntry != nullptr)
    {
        std::lock_guard<std::mutex> guard(mMutex);
        DataEntry* pbData = new DataEntry(*pEntry);
        mList.push_back(pbData);
    }
}

void ImsMediaDataQueue::InsertAt(uint32_t index, DataEntry* pEntry)
{
    if (pEntry != nullptr)
    {
        std::lock_guard<std::mutex> guard(mMutex);
        DataEntry* pbData = new DataEntry(*pEntry);

        if (mList.empty() || index == 0)
        {
            mList.push_front(pbData);
        }
        else if (index >= mList.size())
        {
            mList.push_back(pbData);
        }
        else
        {
            std::list<DataEntry*>::iterator iter = mList.begin();
            advance(iter, index);
            mList.insert(iter, pbData);
        }
    }
}

void ImsMediaDataQueue::Delete()
{
    std::lock_guard<std::mutex> guard(mMutex);

    if (!mList.empty())
    {
        DataEntry* pbData = mList.front();
        pbData->deleteBuffer();
        delete pbData;
        mList.pop_front();
    }
}

void ImsMediaDataQueue::Clear()
{
    while (!mList.empty())
    {
        Delete();
    }
}

bool ImsMediaDataQueue::Get(DataEntry** ppEntry)
{
    if (ppEntry == nullptr)
    {
        return false;
    }

    std::lock_guard<std::mutex> guard(mMutex);

    if (!mList.empty())
    {
        // get first data in the queue
        *ppEntry = mList.front();
        return true;
    }
    else
    {
        *ppEntry = nullptr;
        return false;
    }
}

bool ImsMediaDataQueue::GetLast(DataEntry** ppEntry)
{
    if (ppEntry == nullptr)
    {
        return false;
    }

    std::lock_guard<std::mutex> guard(mMutex);
    // get last data in the queue
    if (!mList.empty())
    {
        *ppEntry = mList.back();
        return true;
    }
    else
    {
        *ppEntry = nullptr;
        return false;
    }
}

bool ImsMediaDataQueue::GetAt(uint32_t index, DataEntry** ppEntry)
{
    if (ppEntry == nullptr)
    {
        return false;
    }

    std::lock_guard<std::mutex> guard(mMutex);

    if (mList.size() > index)
    {
        std::list<DataEntry*>::iterator iter = mList.begin();
        advance(iter, index);
        *ppEntry = *(iter);
        return true;
    }
    else
    {
        *ppEntry = nullptr;
        return false;
    }
}

uint32_t ImsMediaDataQueue::GetCount()
{
    std::lock_guard<std::mutex> guard(mMutex);
    return mList.size();
}

void ImsMediaDataQueue::SetReadPosFirst()
{
    std::lock_guard<std::mutex> guard(mMutex);
    mListIter = mList.begin();
}

bool ImsMediaDataQueue::GetNext(DataEntry** ppEntry)
{
    if (ppEntry == nullptr)
    {
        return false;
    }

    std::lock_guard<std::mutex> guard(mMutex);

    if (mListIter != mList.end())
    {
        *ppEntry = *mListIter++;
        return true;
    }
    else
    {
        *ppEntry = nullptr;
        return false;
    }
}
