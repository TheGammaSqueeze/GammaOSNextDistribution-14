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

#include <gtest/gtest.h>
#include <ImsMediaNetworkUtil.h>
#include <TextConfig.h>
#include <MockTextManager.h>
#include <ImsMediaCondition.h>
#include <unordered_map>
#include <algorithm>

using namespace android::telephony::imsmedia;

using ::testing::Eq;
using ::testing::Pointee;
using ::testing::Return;

namespace
{
// RtpConfig
const int32_t kMediaDirection = RtpConfig::MEDIA_DIRECTION_SEND_RECEIVE;
const android::String8 kRemoteAddress("127.0.0.1");
const int32_t kRemotePort = 10000;
const int8_t kDscp = 0;
const int8_t kRxPayload = 100;
const int8_t kTxPayload = 100;
const int8_t kSamplingRate = 8;

// RtcpConfig
const android::String8 kCanonicalName("name");
const int32_t kTransmitPort = 10001;
const int32_t kIntervalSec = 3;
const int32_t kRtcpXrBlockTypes = 0;

// TextConfig
const int32_t kCodecType = TextConfig::TEXT_T140_RED;
const int32_t kBitrate = 100;
const int8_t kRedundantPayload = 102;
const int8_t kRedundantLevel = 3;
const bool kKeepRedundantLevel = true;

int32_t kSessionId = 0;
static ImsMediaCondition gCondition;

class TextManagerCallback
{
public:
    int32_t resSessionId;
    int32_t response;
    TextConfig resConfig;
    ImsMediaResult result;
    int32_t inactivityType;
    int32_t inactivityDuration;
    android::String8 receivedRtt;

    void resetRespond()
    {
        resSessionId = -1;
        response = -1;
        result = RESULT_NOT_READY;
    }

    void onCallback(const int id, const int event, const ImsMediaResult res)
    {
        resSessionId = id;
        response = event;
        result = res;
    }

    void onCallbackConfig(
            const int id, const int event, const ImsMediaResult res, const TextConfig& config)
    {
        resSessionId = id;
        response = event;
        resConfig = config;
        result = res;
    }

    void onCallbackInactivity(const int id, const int event, const int type, const int duration)
    {
        resSessionId = id;
        response = event;
        inactivityType = type;
        inactivityDuration = duration;
    }

    void onCallbackRttReceived(const int id, const int event, const android::String16& text)
    {
        resSessionId = id;
        response = event;
        receivedRtt = android::String8(text);
    }
};

static std::unordered_map<int, TextManagerCallback*> gMapCallback;

class TextManagerTest : public ::testing::Test
{
public:
    MockTextManager manager;
    TextConfig config;
    RtcpConfig rtcp;
    int socketRtpFd;
    int socketRtcpFd;
    TextManagerCallback callback;

    TextManagerTest()
    {
        socketRtpFd = -1;
        socketRtcpFd = -1;
        callback.resetRespond();
        gCondition.reset();
    }
    ~TextManagerTest() {}

protected:
    virtual void SetUp() override
    {
        rtcp.setCanonicalName(kCanonicalName);
        rtcp.setTransmitPort(kTransmitPort);
        rtcp.setIntervalSec(kIntervalSec);
        rtcp.setRtcpXrBlockTypes(kRtcpXrBlockTypes);

        config.setMediaDirection(kMediaDirection);
        config.setRemoteAddress(kRemoteAddress);
        config.setRemotePort(kRemotePort);
        config.setRtcpConfig(rtcp);
        config.setDscp(kDscp);
        config.setRxPayloadTypeNumber(kRxPayload);
        config.setTxPayloadTypeNumber(kTxPayload);
        config.setSamplingRateKHz(kSamplingRate);
        config.setCodecType(kCodecType);
        config.setBitrate(kBitrate);
        config.setRedundantPayload(kRedundantPayload);
        config.setRedundantLevel(kRedundantLevel);
        config.setKeepRedundantLevel(kKeepRedundantLevel);

        manager.setCallback(&textCallback);
        gMapCallback.insert(std::make_pair(kSessionId, &callback));
        const char testIp[] = "127.0.0.1";
        unsigned int testPortRtp = 50000;
        socketRtpFd = ImsMediaNetworkUtil::openSocket(testIp, testPortRtp, AF_INET);
        EXPECT_NE(socketRtpFd, -1);
        unsigned int testPortRtcp = 50001;
        socketRtcpFd = ImsMediaNetworkUtil::openSocket(testIp, testPortRtcp, AF_INET);
        EXPECT_NE(socketRtcpFd, -1);
        gCondition.reset();
    }

    virtual void TearDown() override
    {
        if (socketRtpFd != -1)
        {
            ImsMediaNetworkUtil::closeSocket(socketRtpFd);
        }

        if (socketRtcpFd != -1)
        {
            ImsMediaNetworkUtil::closeSocket(socketRtcpFd);
        }

        gMapCallback.erase(kSessionId);
    }

    void openSession(const int32_t sessionId)
    {
        callback.resetRespond();
        android::Parcel parcel;
        parcel.writeInt32(kTextOpenSession);
        parcel.writeInt32(socketRtpFd);
        parcel.writeInt32(socketRtcpFd);
        parcel.setDataPosition(0);
        gCondition.reset();
        manager.sendMessage(sessionId, parcel);
        EXPECT_TRUE(!gCondition.wait_timeout(1000));
        EXPECT_EQ(callback.resSessionId, sessionId);
        EXPECT_EQ(callback.response, kTextOpenSessionSuccess);
    }

    void closeSession(const int32_t sessionId)
    {
        callback.resetRespond();
        android::Parcel parcel;
        parcel.writeInt32(kTextCloseSession);
        parcel.setDataPosition(0);
        gCondition.reset();
        manager.sendMessage(sessionId, parcel);
        EXPECT_TRUE(!gCondition.wait_timeout(1000));
        EXPECT_EQ(callback.resSessionId, sessionId);
        EXPECT_EQ(callback.response, kTextSessionClosed);
    }

    void testEventResponse(const int32_t sessionId, const int32_t event, TextConfig* config,
            const int32_t response, const int32_t result)
    {
        callback.resetRespond();
        android::Parcel parcel;
        parcel.writeInt32(event);

        if (config != nullptr)
        {
            config->writeToParcel(&parcel);
        }

        parcel.setDataPosition(0);
        gCondition.reset();
        manager.sendMessage(sessionId, parcel);
        EXPECT_TRUE(!gCondition.wait_timeout(1000));
        EXPECT_EQ(callback.resSessionId, sessionId);
        EXPECT_EQ(callback.response, response);

        if (callback.response >= kTextOpenSessionFailure &&
                callback.response <= kTextModifySessionResponse)
        {
            EXPECT_EQ(result, result);

            if (config != nullptr && callback.response == kTextModifySessionResponse)
            {
                EXPECT_EQ(callback.resConfig, *config);
            }
        }
    }

    static int32_t textCallback(int sessionId, const android::Parcel& parcel)
    {
        parcel.setDataPosition(0);

        int response = parcel.readInt32();
        ImsMediaResult result = RESULT_INVALID_PARAM;

        auto callback = gMapCallback.find(sessionId);

        if (callback != gMapCallback.end())
        {
            if (response >= kTextOpenSessionFailure && response <= kTextModifySessionResponse)
            {
                result = static_cast<ImsMediaResult>(parcel.readInt32());
            }

            switch (response)
            {
                case kTextModifySessionResponse:
                {
                    TextConfig resConfig;
                    resConfig.readFromParcel(&parcel);
                    (callback->second)->onCallbackConfig(sessionId, response, result, resConfig);
                }
                break;
                case kTextMediaInactivityInd:
                    (callback->second)
                            ->onCallbackInactivity(
                                    sessionId, response, parcel.readInt32(), parcel.readInt32());
                    break;
                case kTextRttReceived:
                {
                    android::String16 text;
                    parcel.readString16(&text);
                    (callback->second)->onCallbackRttReceived(sessionId, response, text);
                }
                break;
                default:
                    (callback->second)->onCallback(sessionId, response, result);
                    break;
            }
        }

        gCondition.signal();
        return 0;
    }
};

TEST_F(TextManagerTest, testOpenCloseSession)
{
    EXPECT_EQ(manager.getState(kSessionId), kSessionStateClosed);
    openSession(kSessionId);
    closeSession(kSessionId);
}

TEST_F(TextManagerTest, testModifySession)
{
    testEventResponse(kSessionId, kTextModifySession, nullptr, kTextModifySessionResponse,
            RESULT_INVALID_PARAM);

    openSession(kSessionId);

    testEventResponse(kSessionId, kTextModifySession, nullptr, kTextModifySessionResponse,
            RESULT_INVALID_PARAM);

    testEventResponse(
            kSessionId, kTextModifySession, &config, kTextModifySessionResponse, RESULT_SUCCESS);

    closeSession(kSessionId);
}

TEST_F(TextManagerTest, testSendRtt)
{
    openSession(kSessionId);

    const android::String8 text = android::String8("hello");
    android::Parcel parcel;
    parcel.writeInt32(kTextSendRtt);
    android::String16 rttText(text);
    parcel.writeString16(rttText);
    parcel.setDataPosition(0);

    EXPECT_CALL(manager, sendRtt(kSessionId, Pointee(Eq(text))))
            .Times(1)
            .WillOnce(Return(RESULT_NOT_READY));
    manager.sendMessage(kSessionId, parcel);

    closeSession(kSessionId);
}

TEST_F(TextManagerTest, testSetMediaQualityThreshold)
{
    openSession(kSessionId);

    const std::vector<int32_t> kRtpInactivityTimerMillis = {10000, 20000};
    const int32_t kRtcpInactivityTimerMillis = 20000;
    const int32_t kRtpHysteresisTimeInMillis = 3000;
    const int32_t kRtpPacketLossDurationMillis = 5000;
    const std::vector<int32_t> kRtpPacketLossRate = {3, 5};
    const std::vector<int32_t> kRtpJitterMillis = {100, 200};
    const bool kNotifyCurrentStatus = false;

    MediaQualityThreshold threshold;
    threshold.setRtpInactivityTimerMillis(kRtpInactivityTimerMillis);
    threshold.setRtcpInactivityTimerMillis(kRtcpInactivityTimerMillis);
    threshold.setRtpHysteresisTimeInMillis(kRtpHysteresisTimeInMillis);
    threshold.setRtpPacketLossDurationMillis(kRtpPacketLossDurationMillis);
    threshold.setRtpPacketLossRate(kRtpPacketLossRate);
    threshold.setRtpJitterMillis(kRtpJitterMillis);
    threshold.setNotifyCurrentStatus(kNotifyCurrentStatus);

    android::Parcel parcel;
    parcel.writeInt32(kTextSetMediaQualityThreshold);
    threshold.writeToParcel(&parcel);
    parcel.setDataPosition(0);

    EXPECT_CALL(manager, setMediaQualityThreshold(kSessionId, Pointee(Eq(threshold))))
            .Times(1)
            .WillOnce(Return());

    manager.sendMessage(kSessionId, parcel);

    closeSession(kSessionId);
}

TEST_F(TextManagerTest, testMediaInactivityInd)
{
    const int32_t kInactivityType = kProtocolRtp;
    const int32_t kInactivityDuration = 10000;

    ImsMediaEventHandler::SendEvent("TEXT_RESPONSE_EVENT", kTextMediaInactivityInd, kSessionId,
            kInactivityType, kInactivityDuration);

    gCondition.wait_timeout(20);
    EXPECT_EQ(callback.resSessionId, kSessionId);
    EXPECT_EQ(callback.response, kTextMediaInactivityInd);
    EXPECT_EQ(callback.inactivityType, kInactivityType);
    EXPECT_EQ(callback.inactivityDuration, kInactivityDuration);
}

TEST_F(TextManagerTest, testRttReceivedInd)
{
    const android::String8 testText = android::String8("hello");
    android::String8* text = new android::String8(testText);

    ImsMediaEventHandler::SendEvent("TEXT_RESPONSE_EVENT", kTextRttReceived, kSessionId,
            reinterpret_cast<uint64_t>(text), 0);

    gCondition.wait_timeout(20);
    EXPECT_EQ(callback.resSessionId, kSessionId);
    EXPECT_EQ(callback.response, kTextRttReceived);
    EXPECT_EQ(callback.receivedRtt, testText);
}
}  // namespace