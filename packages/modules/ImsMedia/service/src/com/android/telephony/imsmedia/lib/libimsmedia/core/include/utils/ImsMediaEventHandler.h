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

#ifndef IMS_MEDIA_EVENTHANDLER_H
#define IMS_MEDIA_EVENTHANDLER_H

#include <stdint.h>
#include <list>
#include <mutex>
#include <IImsMediaThread.h>
#include <ImsMediaCondition.h>

/**
 * @class ImsMediaEventHandler
 * @brief Thread based event handler
 * - Call SendEvent() method to send an evnet
 * - Child class should implement processEvent() method.
 * - processEvent() method will be called when an event is received.
 */
class ImsMediaEventHandler : public IImsMediaThread
{
private:
    std::list<uint32_t> mListevent;
    std::list<uint64_t> mListParamA;
    std::list<uint64_t> mListParamB;
    std::list<uint64_t> mListParamC;
    static std::list<ImsMediaEventHandler*> gListEventHandler;
    static std::mutex mMutex;
    ImsMediaCondition mCondition;
    ImsMediaCondition mConditionExit;
    bool mbTerminate;
    char mName[MAX_EVENTHANDLER_NAME];
    std::mutex mMutexEvent;

public:
    ImsMediaEventHandler();
    virtual ~ImsMediaEventHandler();
    void Init(const char* strName);
    void Deinit();
    static void SendEvent(const char* strEventHandlerName, uint32_t event, uint64_t paramA,
            uint64_t paramB = 0, uint64_t paramC = 0);
    char* getName();

private:
    void AddEvent(uint32_t event, uint64_t paramA, uint64_t paramB, uint64_t paramC);
    virtual void processEvent(
            uint32_t event, uint64_t paramA, uint64_t paramB, uint64_t paramC) = 0;
    virtual void* run();  // thread method
};

#endif