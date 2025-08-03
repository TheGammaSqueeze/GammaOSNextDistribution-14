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

#ifndef MOCK_BASE_NODE_H
#define MOCK_BASE_NODE_H

#include <BaseNode.h>
#include <gmock/gmock.h>

class MockBaseNode : public BaseNode
{
public:
    MockBaseNode(BaseSessionCallback* callback = nullptr) :
            BaseNode(callback)
    {
    }
    virtual ~MockBaseNode() {}
    MOCK_METHOD(kBaseNodeId, GetNodeId, (), (override));
    MOCK_METHOD(ImsMediaResult, Start, (), (override));
    MOCK_METHOD(void, Stop, (), (override));
    MOCK_METHOD(bool, IsRunTime, (), (override));
    MOCK_METHOD(bool, IsSourceNode, (), (override));
    MOCK_METHOD(void, SetConfig, (void* config), (override));
    MOCK_METHOD(bool, IsSameConfig, (void* config), (override));
    MOCK_METHOD(ImsMediaResult, UpdateConfig, (void* config), (override));
    MOCK_METHOD(void, ProcessData, (), (override));
    MOCK_METHOD(const char*, GetNodeName, (), (override));
    MOCK_METHOD(void, SetMediaType, (ImsMediaType eType), (override));
    MOCK_METHOD(ImsMediaType, GetMediaType, (), (override));
    MOCK_METHOD(kBaseNodeState, GetState, (), (override));
    MOCK_METHOD(void, SetState, (kBaseNodeState state), (override));
    MOCK_METHOD(uint32_t, GetDataCount, (), (override));
    MOCK_METHOD(bool, GetData,
            (ImsMediaSubType * subtype, uint8_t** data, uint32_t* size, uint32_t* timestamp,
                    bool* mark, uint32_t* seq, ImsMediaSubType* dataType, uint32_t* arrivalTime),
            (override));
    MOCK_METHOD(void, DeleteData, (), (override));
    MOCK_METHOD(void, SendDataToRearNode,
            (ImsMediaSubType subtype, uint8_t* data, uint32_t size, uint32_t timestamp, bool mark,
                    uint32_t seq, ImsMediaSubType dataType, uint32_t arrivalTime),
            (override));
    MOCK_METHOD(void, OnDataFromFrontNode,
            (ImsMediaSubType subtype, uint8_t* pData, uint32_t size, uint32_t timestamp, bool mark,
                    uint32_t seq, ImsMediaSubType dataType, uint32_t arrivalTime),
            (override));

    void DelegateToFake()
    {
        ON_CALL(*this, OnDataFromFrontNode)
                .WillByDefault(
                        [this](ImsMediaSubType subtype, uint8_t* data, uint32_t size,
                                uint32_t timestamp, bool mark, uint32_t seq,
                                ImsMediaSubType dataType, uint32_t arrivalTime)
                        {
                            if (mFake != nullptr)
                            {
                                mFake->OnDataFromFrontNode(subtype, data, size, timestamp, mark,
                                        seq, dataType, arrivalTime);
                            }
                        });
    }

    void SetDelegate(BaseNode* fake) { mFake = fake; }

private:
    BaseNode* mFake;
};

#endif