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

#include <ImsMediaEventHandler.h>
#include <ImsMediaTrace.h>
#include <string.h>
#include <string>

std::list<ImsMediaEventHandler*> ImsMediaEventHandler::gListEventHandler;
std::mutex ImsMediaEventHandler::mMutex;

ImsMediaEventHandler::ImsMediaEventHandler() {}

ImsMediaEventHandler::~ImsMediaEventHandler() {}

void ImsMediaEventHandler::Init(const char* strName)
{
    strncpy(mName, strName, MAX_EVENTHANDLER_NAME);
    mbTerminate = false;
    gListEventHandler.push_back(this);
    IMLOGD1("[Init] %s", mName);
    StartThread("ImsMediaEventHandler");
}

void ImsMediaEventHandler::Deinit()
{
    IMLOGD2("[Deinit] %s, queue size[%d]", mName, mListevent.size());
    std::lock_guard<std::mutex> guard(mMutexEvent);
    StopThread();
    gListEventHandler.remove(this);
    mListevent.clear();
    mListParamA.clear();
    mListParamB.clear();
    mListParamC.clear();
    mCondition.signal();
    mConditionExit.wait();
}

void ImsMediaEventHandler::SendEvent(const char* strEventHandlerName, uint32_t event,
        uint64_t paramA, uint64_t paramB, uint64_t paramC)
{
    if (strEventHandlerName == nullptr)
    {
        IMLOGE0("[SendEvent] strEventHandlerName is nullptr");
        return;
    }

    IMLOGD5("[SendEvent] Name[%s], event[%d], paramA[%p], paramB[%p], paramC[%p]",
            strEventHandlerName, event, paramA, paramB, paramC);

    for (auto& i : gListEventHandler)
    {
        if (i != nullptr && strcmp(i->getName(), strEventHandlerName) == 0)
        {
            i->AddEvent(event, paramA, paramB, paramC);
        }
    }
}

char* ImsMediaEventHandler::getName()
{
    return mName;
}

void ImsMediaEventHandler::AddEvent(
        uint32_t event, uint64_t paramA, uint64_t paramB, uint64_t paramC)
{
    std::lock_guard<std::mutex> guard(mMutexEvent);
    IMLOGD3("[AddEvent] %s, event[%d], size[%d]", mName, event, mListevent.size());
    mListevent.push_back(event);
    mListParamA.push_back(paramA);
    mListParamB.push_back(paramB);
    mListParamC.push_back(paramC);
    mCondition.signal();
}

void* ImsMediaEventHandler::run()
{
    IMLOGD2("[run] %s enter, %p", mName, this);

    for (;;)
    {
        IMLOGD1("[run] %s wait", mName);
        mCondition.wait();

        for (;;)
        {
            if (IsThreadStopped())
            {
                break;
            }

            mMutexEvent.lock();

            if (mListevent.size() == 0)
            {
                mMutexEvent.unlock();
                break;
            }

            uint32_t event = mListevent.front();
            uint64_t paramA = mListParamA.front();
            uint64_t paramB = mListParamB.front();
            uint64_t paramC = mListParamC.front();

            mListevent.pop_front();
            mListParamA.pop_front();
            mListParamB.pop_front();
            mListParamC.pop_front();
            mMutexEvent.unlock();

            processEvent(event, paramA, paramB, paramC);

            if (IsThreadStopped())
            {
                break;
            }
        }

        if (IsThreadStopped())
        {
            break;
        }
    }

    IMLOGD2("[run] %s exit, %p", mName, this);
    mConditionExit.signal();
    return nullptr;
}
