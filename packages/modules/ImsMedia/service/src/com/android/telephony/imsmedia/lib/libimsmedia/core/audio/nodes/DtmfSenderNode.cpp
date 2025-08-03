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

#include <ImsMediaDefine.h>
#include <ImsMediaTrace.h>
#include <DtmfSenderNode.h>
#include <ImsMediaTimer.h>
#include <AudioConfig.h>

DtmfSenderNode::DtmfSenderNode(BaseSessionCallback* callback) :
        BaseNode(callback)
{
    mNextTime = 0;
    mPrevTime = 0;
    mPtime = 20;
}

DtmfSenderNode::~DtmfSenderNode() {}

kBaseNodeId DtmfSenderNode::GetNodeId()
{
    return kNodeIdDtmfSender;
}

ImsMediaResult DtmfSenderNode::Start()
{
    IMLOGD0("[Start]");
    mNextTime = 0;
    mNodeState = kNodeStateRunning;
    return RESULT_SUCCESS;
}

void DtmfSenderNode::Stop()
{
    IMLOGD0("[Stop]");
    mNextTime = 0;
    ClearDataQueue();
    mNodeState = kNodeStateStopped;
}

bool DtmfSenderNode::IsRunTime()
{
    return false;
}

bool DtmfSenderNode::IsSourceNode()
{
    return false;
}

void DtmfSenderNode::SetConfig(void* config)
{
    AudioConfig* pConfig = reinterpret_cast<AudioConfig*>(config);

    if (pConfig != nullptr)
    {
        mPtime = pConfig->getPtimeMillis();
    }
}

bool DtmfSenderNode::IsSameConfig(void* config)
{
    if (config == nullptr)
    {
        return true;
    }

    AudioConfig* pConfig = reinterpret_cast<AudioConfig*>(config);
    return (mPtime == pConfig->getPtimeMillis());
}

void DtmfSenderNode::ProcessData()
{
    ImsMediaSubType subtype;
    uint8_t* data;
    uint32_t size;
    uint32_t timestamp;
    bool bMark;
    uint32_t currTime;

    if (!GetData(&subtype, &data, &size, &timestamp, &bMark, nullptr))
    {
        return;
    }

    currTime = ImsMediaTimer::GetTimeInMilliSeconds();

    if (mNextTime && !(currTime >= mNextTime || currTime < mPrevTime || mNextTime < mPrevTime))
    {
        mPrevTime = currTime;
        return;
    }

    if (subtype == MEDIASUBTYPE_DTMFSTART)
    {
        SendDataToRearNode(subtype, data, size, currTime, bMark, 0);
        DeleteData();
        mNextTime = currTime;

        // send the first dtmf packet
        if (GetData(&subtype, &data, &size, &timestamp, &bMark, nullptr, nullptr) &&
                subtype == MEDIASUBTYPE_DTMF_PAYLOAD)
        {
            SendDataToRearNode(subtype, data, size, currTime, bMark, 0);
            DeleteData();
            mNextTime += mPtime;
        }
    }
    else
    {
        SendDataToRearNode(subtype, data, size, currTime, bMark, 0);
        DeleteData();
        mNextTime += mPtime;
    }

    mPrevTime = currTime;
}
