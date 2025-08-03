/**
 * Copyright (C) 2024 The Android Open Source Project
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

#include <gtest/gtest.h>
#include <MockBaseNode.h>
#include <StreamScheduler.h>
#include <ImsMediaCondition.h>

using namespace android::telephony::imsmedia;
using namespace android;

namespace
{
class NonRuntimeTestNode : public BaseNode
{
public:
    virtual ~NonRuntimeTestNode() {}
    virtual const char* GetNodeName() { return "TestNode"; }
    virtual bool IsSourceNode() { return false; }
    virtual bool IsRunTime() { return false; }
    virtual bool IsRunTimeStart() { return false; }
    virtual ImsMediaResult Start()
    {
        SetState(kNodeStateRunning);
        return RESULT_SUCCESS;
    }
    virtual ImsMediaResult ProcessStart()
    {
        SetState(kNodeStateRunning);
        return RESULT_SUCCESS;
    }
    virtual void Stop() { SetState(kNodeStateStopped); }
    virtual void ProcessData()
    {
        uint8_t* data;
        uint32_t size;
        uint32_t timestamp;
        uint32_t seq;
        bool mark;
        ImsMediaSubType subtype;
        ImsMediaSubType dataType;

        if (GetData(&subtype, &data, &size, &timestamp, &mark, &seq, &dataType))
        {
            SendDataToRearNode(subtype, data, size, timestamp, mark, seq, dataType, 0);
            DeleteData();
        }
    }
};

class FakeSourceTestNode : public NonRuntimeTestNode
{
public:
    virtual ~FakeSourceTestNode() {}
    virtual bool IsSourceNode() { return true; }
    virtual bool IsRunTimeStart() { return true; }
};

class FakeNormalTestNode : public FakeSourceTestNode
{
public:
    virtual ~FakeNormalTestNode() {}
    virtual bool IsSourceNode() { return false; }
};

class FakeBaseTestNode : public BaseNode
{
public:
    virtual ~FakeBaseTestNode() {}
    virtual bool IsRunTime() { return true; }
    virtual bool IsSourceNode() { return false; }
    virtual ImsMediaResult Start()
    {
        SetState(kNodeStateRunning);
        return RESULT_SUCCESS;
    }
    virtual void Stop() { SetState(kNodeStateStopped); }
};

class StreamSchedulerTest : public ::testing::Test
{
public:
    StreamSchedulerTest() {}
    virtual ~StreamSchedulerTest() {}

protected:
    std::shared_ptr<StreamScheduler> mScheduler;
    ImsMediaCondition mCondition;

    virtual void SetUp() override { mScheduler = std::make_shared<StreamScheduler>(); }

    virtual void TearDown() override {}
};

TEST_F(StreamSchedulerTest, testStartWithEmptyNode)
{
    mScheduler->Start();
    mCondition.wait_timeout(10);
    EXPECT_FALSE(mScheduler->IsThreadRunning());
    mScheduler->Stop();
}

TEST_F(StreamSchedulerTest, testAsyncStartNode)
{
    NonRuntimeTestNode node;
    mScheduler->RegisterNode(&node);
    EXPECT_EQ(mScheduler->GetNumRegisteredNodes(), 1);
    mScheduler->Start();
    mCondition.wait_timeout(10);
    EXPECT_FALSE(mScheduler->IsThreadRunning());
    EXPECT_EQ(node.GetState(), kNodeStateRunning);
    mScheduler->Stop();
    mScheduler->DeRegisterNode(&node);
    EXPECT_EQ(mScheduler->GetNumRegisteredNodes(), 0);
}

TEST_F(StreamSchedulerTest, testSourceNode)
{
    FakeSourceTestNode node;
    mScheduler->RegisterNode(&node);
    EXPECT_EQ(mScheduler->GetNumRegisteredNodes(), 1);
    node.Start();
    mScheduler->Start();
    mCondition.wait_timeout(10);
    EXPECT_TRUE(mScheduler->IsThreadRunning());
    EXPECT_EQ(node.GetState(), kNodeStateRunning);
    mScheduler->Stop();
    mScheduler->DeRegisterNode(&node);
    EXPECT_EQ(mScheduler->GetNumRegisteredNodes(), 0);
}

TEST_F(StreamSchedulerTest, testNormalNode)
{
    FakeBaseTestNode node1;
    FakeNormalTestNode node2;
    node1.ConnectRearNode(&node2);
    node1.SetSchedulerCallback(std::static_pointer_cast<StreamSchedulerCallback>(mScheduler));
    node2.SetSchedulerCallback(std::static_pointer_cast<StreamSchedulerCallback>(mScheduler));

    mScheduler->RegisterNode(&node2);
    EXPECT_EQ(mScheduler->GetNumRegisteredNodes(), 1);

    node1.Start();
    node2.Start();

    mScheduler->Start();
    mCondition.wait_timeout(10);
    EXPECT_FALSE(mScheduler->IsThreadRunning());
    EXPECT_EQ(node2.GetState(), kNodeStateRunning);

    mScheduler->Stop();

    char buffer[10] = {"\x1\x2\x3\x4\x5\x6\x7\x0"};

    for (int32_t i = 0; i < 3; i++)
    {
        node1.SendDataToRearNode(MEDIASUBTYPE_UNDEFINED, reinterpret_cast<uint8_t*>(buffer),
                sizeof(buffer), 0, false, 0, MEDIASUBTYPE_UNDEFINED, 0);
    }

    EXPECT_TRUE(node2.GetDataCount() > 0);

    mScheduler->Start();
    EXPECT_TRUE(mScheduler->IsThreadRunning());
    node1.SendDataToRearNode(MEDIASUBTYPE_UNDEFINED, reinterpret_cast<uint8_t*>(buffer),
            sizeof(buffer), 0, false, 0, MEDIASUBTYPE_UNDEFINED, 0);

    mCondition.wait_timeout(20);
    EXPECT_TRUE(node2.GetDataCount() == 0);
    EXPECT_FALSE(mScheduler->IsThreadRunning());

    mScheduler->Stop();
    mScheduler->DeRegisterNode(&node2);
    EXPECT_EQ(mScheduler->GetNumRegisteredNodes(), 0);
}

}  // namespace