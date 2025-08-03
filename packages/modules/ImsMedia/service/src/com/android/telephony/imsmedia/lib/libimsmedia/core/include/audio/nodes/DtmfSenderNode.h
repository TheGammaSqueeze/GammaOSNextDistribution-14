/**
 * Copyright (C) 2023 The Android Open Source Project
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

#ifndef DTMFSENDERNODE_H_INCLUDED
#define DTMFSENDERNODE_H_INCLUDED

#include <ImsMediaDefine.h>
#include <BaseNode.h>

class DtmfSenderNode : public BaseNode
{
public:
    DtmfSenderNode(BaseSessionCallback* callback = nullptr);
    virtual ~DtmfSenderNode();
    virtual kBaseNodeId GetNodeId();
    virtual ImsMediaResult Start();
    virtual void Stop();
    virtual bool IsRunTime();
    virtual bool IsSourceNode();
    virtual void SetConfig(void* config);
    virtual bool IsSameConfig(void* config);
    virtual void ProcessData();

private:
    uint32_t mNextTime;
    uint32_t mPrevTime;
    int8_t mPtime;  // msec unit, interval between dtmf packets
};

#endif  // DTMFSENDERNODE_H_INCLUDED
