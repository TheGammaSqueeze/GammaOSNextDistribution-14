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

#include <BaseNode.h>
#include <ImsMediaTrace.h>
#include <stdlib.h>

using NODE_ID_PAIR = std::pair<kBaseNodeId, const char*>;
static std::vector<NODE_ID_PAIR> vectorNodeId{
        std::make_pair(kNodeIdUnknown, "NodeUnknown"),
        std::make_pair(kNodeIdSocketWriter, "SocketWriter"),
        std::make_pair(kNodeIdSocketReader, "SocketReader"),
        std::make_pair(kNodeIdRtpEncoder, "RtpEncoder"),
        std::make_pair(kNodeIdRtpDecoder, "RtpDecoder"),
        std::make_pair(kNodeIdRtcpEncoder, "RtcpEncoder"),
        std::make_pair(kNodeIdRtcpDecoder, "RtcpDecoder"),
        std::make_pair(kNodeIdAudioSource, "AudioSource"),
        std::make_pair(kNodeIdAudioPlayer, "AudioPlayer"),
        std::make_pair(kNodeIdDtmfEncoder, "DtmfEncoder"),
        std::make_pair(kNodeIdDtmfSender, "DtmfSender"),
        std::make_pair(kNodeIdAudioPayloadEncoder, "AudioPayloadEncoder"),
        std::make_pair(kNodeIdAudioPayloadDecoder, "AudioPayloadDecoder"),
        std::make_pair(kNodeIdVideoSource, "VideoSource"),
        std::make_pair(kNodeIdVideoRenderer, "VideoRenderer"),
        std::make_pair(kNodeIdVideoPayloadEncoder, "VideoPayloadEncoder"),
        std::make_pair(kNodeIdVideoPayloadDecoder, "VideoPayloadDecoder"),
        std::make_pair(kNodeIdTextSource, "TextSource"),
        std::make_pair(kNodeIdTextRenderer, "TextRenderer"),
        std::make_pair(kNodeIdTextPayloadEncoder, "TextPayloadEncoder"),
        std::make_pair(kNodeIdTextPayloadDecoder, "TextPayloadDecoder"),
};

BaseNode::BaseNode(BaseSessionCallback* callback)
{
    mScheduler = nullptr;
    mCallback = callback;
    mNodeState = kNodeStateStopped;
    mMediaType = IMS_MEDIA_AUDIO;
    mListFrontNodes.clear();
    mListRearNodes.clear();
}

BaseNode::~BaseNode()
{
    ClearDataQueue();
    mNodeState = kNodeStateStopped;
}

void BaseNode::SetSessionCallback(BaseSessionCallback* callback)
{
    mCallback = callback;
}

void BaseNode::SetSchedulerCallback(const std::shared_ptr<StreamSchedulerCallback>& callback)
{
    mScheduler = callback;
}

void BaseNode::ConnectRearNode(BaseNode* pRearNode)
{
    if (pRearNode == nullptr)
    {
        return;
    }

    IMLOGD3("[ConnectRearNode] type[%d] connect [%s] to [%s]", mMediaType, GetNodeName(),
            pRearNode->GetNodeName());
    mListRearNodes.push_back(pRearNode);
    pRearNode->mListFrontNodes.push_back(this);
}

void BaseNode::DisconnectNodes()
{
    while (!mListFrontNodes.empty())
    {
        DisconnectFrontNode(mListFrontNodes.back());
    }

    while (!mListRearNodes.empty())
    {
        DisconnectRearNode(mListRearNodes.back());
    }
}

void BaseNode::ClearDataQueue()
{
    IMLOGD1("[ClearDataQueue] queue size[%d]", mDataQueue.GetCount());
    mDataQueue.Clear();
}

kBaseNodeId BaseNode::GetNodeId()
{
    return kNodeIdUnknown;
}

bool BaseNode::Prepare()
{
    return RESULT_SUCCESS;
}

ImsMediaResult BaseNode::Start()
{
    if (!IsRunTimeStart())
    {
        return RESULT_SUCCESS;
    }
    else
    {
        IMLOGW0("[Start] Error - base method");
        return RESULT_NOT_SUPPORTED;
    }
}

ImsMediaResult BaseNode::ProcessStart()
{
    IMLOGW0("[ProcessStart] Error - base method");
    return RESULT_NOT_SUPPORTED;
}

bool BaseNode::IsRunTime()
{
    return true;
}

bool BaseNode::IsRunTimeStart()
{
    return true;
}

void BaseNode::SetConfig(void* config)
{
    (void)config;
    IMLOGW0("[SetConfig] Error - base method");
}

bool BaseNode::IsSameConfig(void* config)
{
    (void)config;
    IMLOGW0("[IsSameConfig] Error - base method");
    return true;
}

ImsMediaResult BaseNode::UpdateConfig(void* config)
{
    // check config items updates
    bool isUpdateNode = false;

    if (IsSameConfig(config))
    {
        IMLOGD0("[UpdateConfig] no update");
        return RESULT_SUCCESS;
    }
    else
    {
        isUpdateNode = true;
    }

    kBaseNodeState prevState = mNodeState;

    if (isUpdateNode && mNodeState == kNodeStateRunning)
    {
        Stop();
    }

    // reset the parameters
    SetConfig(config);

    if (isUpdateNode && prevState == kNodeStateRunning)
    {
        return Start();
    }

    return RESULT_SUCCESS;
}

void BaseNode::ProcessData()
{
    IMLOGE0("ProcessData] Error - base method");
}

const char* BaseNode::GetNodeName()
{
    typedef typename std::vector<std::pair<kBaseNodeId, const char*>>::iterator iterator;

    for (iterator it = vectorNodeId.begin(); it != vectorNodeId.end(); ++it)
    {
        if (it->first == GetNodeId())
        {
            return it->second;
        }
    }

    return "NodeUnknown";
}

void BaseNode::SetMediaType(ImsMediaType eType)
{
    mMediaType = eType;
}

ImsMediaType BaseNode::GetMediaType()
{
    return mMediaType;
}

// Graph Interface
kBaseNodeState BaseNode::GetState()
{
    return mNodeState;
}

void BaseNode::SetState(kBaseNodeState state)
{
    mNodeState = state;
}

uint32_t BaseNode::GetDataCount()
{
    return mDataQueue.GetCount();
}

bool BaseNode::GetData(ImsMediaSubType* psubtype, uint8_t** ppData, uint32_t* pnDataSize,
        uint32_t* pnTimestamp, bool* pbMark, uint32_t* pnSeqNum, ImsMediaSubType* peDataType,
        uint32_t* arrivalTime)
{
    DataEntry* pEntry;

    if (mDataQueue.Get(&pEntry))
    {
        if (psubtype)
            *psubtype = pEntry->subtype;
        if (ppData)
            *ppData = pEntry->pbBuffer;
        if (pnDataSize)
            *pnDataSize = pEntry->nBufferSize;
        if (pnTimestamp)
            *pnTimestamp = pEntry->nTimestamp;
        if (pbMark)
            *pbMark = pEntry->bMark;
        if (pnSeqNum)
            *pnSeqNum = pEntry->nSeqNum;
        if (peDataType)
            *peDataType = pEntry->eDataType;
        if (arrivalTime)
            *arrivalTime = pEntry->arrivalTime;
        return true;
    }
    else
    {
        if (psubtype)
            *psubtype = MEDIASUBTYPE_UNDEFINED;
        if (ppData)
            *ppData = nullptr;
        if (pnDataSize)
            *pnDataSize = 0;
        if (pnTimestamp)
            *pnTimestamp = 0;
        if (pbMark)
            *pbMark = false;
        if (pnSeqNum)
            *pnSeqNum = 0;
        if (peDataType)
            *peDataType = MEDIASUBTYPE_UNDEFINED;
        if (arrivalTime)
            *arrivalTime = 0;
        return false;
    }
}

void BaseNode::AddData(uint8_t* data, uint32_t size, uint32_t timestamp, bool mark, uint32_t seq,
        ImsMediaSubType subtype, ImsMediaSubType dataType, uint32_t arrivalTime, int32_t index)
{
    DataEntry entry = DataEntry();
    entry.pbBuffer = data;
    entry.nBufferSize = size;
    entry.nTimestamp = timestamp;
    entry.bMark = mark;
    entry.nSeqNum = seq;
    entry.eDataType = dataType;
    entry.subtype = subtype;
    entry.arrivalTime = arrivalTime;
    index == -1 ? mDataQueue.Add(&entry) : mDataQueue.InsertAt(index, &entry);
}

void BaseNode::DeleteData()
{
    mDataQueue.Delete();
}

void BaseNode::SendDataToRearNode(ImsMediaSubType subtype, uint8_t* pData, uint32_t nDataSize,
        uint32_t nTimestamp, bool bMark, uint32_t nSeqNum, ImsMediaSubType nDataType,
        uint32_t arrivalTime)
{
    bool needRunCount = false;

    for (auto& node : mListRearNodes)
    {
        if (node != nullptr && node->GetState() == kNodeStateRunning)
        {
            node->OnDataFromFrontNode(
                    subtype, pData, nDataSize, nTimestamp, bMark, nSeqNum, nDataType, arrivalTime);

            if (!node->IsRunTime())
            {
                needRunCount = true;
            }
        }
    }

    if (needRunCount && mScheduler != nullptr)
    {
        mScheduler->onAwakeScheduler();
    }
}

void BaseNode::OnDataFromFrontNode(ImsMediaSubType subtype, uint8_t* pData, uint32_t nDataSize,
        uint32_t nTimestamp, bool bMark, uint32_t nSeqNum, ImsMediaSubType nDataType,
        uint32_t arrivalTime)
{
    DataEntry entry = DataEntry();
    entry.pbBuffer = pData;
    entry.nBufferSize = nDataSize;
    entry.nTimestamp = nTimestamp;
    entry.bMark = bMark;
    entry.nSeqNum = nSeqNum;
    entry.eDataType = nDataType;
    entry.subtype = subtype;
    entry.arrivalTime = arrivalTime;
    mDataQueue.Add(&entry);
}

void BaseNode::DisconnectRearNode(BaseNode* pRearNode)
{
    if (pRearNode == nullptr)
    {
        mListRearNodes.pop_back();
        return;
    }

    IMLOGD3("[DisconnectRearNode] type[%d] disconnect [%s] from [%s]", mMediaType, GetNodeName(),
            pRearNode->GetNodeName());

    mListRearNodes.remove(pRearNode);
    pRearNode->mListFrontNodes.remove(this);
}

void BaseNode::DisconnectFrontNode(BaseNode* pFrontNode)
{
    if (pFrontNode == nullptr)
    {
        mListFrontNodes.pop_back();
        return;
    }

    IMLOGD3("[DisconnectFrontNode] type[%d] disconnect [%s] from [%s]", mMediaType,
            pFrontNode->GetNodeName(), GetNodeName());

    mListFrontNodes.remove(pFrontNode);
    pFrontNode->mListRearNodes.remove(this);
}