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

#ifndef STREAM_SCHEDULER_H
#define STREAM_SCHEDULER_H

#include <BaseNode.h>
#include <IImsMediaThread.h>
#include <StreamSchedulerCallback.h>
#include <ImsMediaCondition.h>
#include <list>
#include <atomic>

class StreamScheduler : public IImsMediaThread, public StreamSchedulerCallback
{
public:
    StreamScheduler();
    virtual ~StreamScheduler();
    void RegisterNode(BaseNode* pNode);
    void DeRegisterNode(BaseNode* pNode);
    void Start();
    void Stop();
    void Awake();
    virtual void onAwakeScheduler() { this->Awake(); }
    virtual void* run();
    bool IsThreadRunning() { return mIsRunning; }
    uint32_t GetNumRegisteredNodes() { return mListRegisteredNode.size(); }

private:
    bool RunRegisteredNode();
    std::list<BaseNode*> mListRegisteredNode;
    ImsMediaCondition mConditionMain;
    ImsMediaCondition mConditionExit;
    std::mutex mMutex;
    std::atomic<bool> mIsRunning;
};

#endif