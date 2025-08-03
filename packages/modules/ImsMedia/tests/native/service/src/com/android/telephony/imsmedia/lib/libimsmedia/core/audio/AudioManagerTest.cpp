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
#include <AudioConfig.h>
#include <MockAudioManager.h>
#include <ImsMediaCondition.h>
#include <unordered_map>
#include <algorithm>

using namespace android::telephony::imsmedia;

using ::testing::_;
using ::testing::Eq;
using ::testing::Pointee;
using ::testing::Ref;
using ::testing::Return;

namespace
{
// RtpConfig
const int32_t kMediaDirection = RtpConfig::MEDIA_DIRECTION_SEND_RECEIVE;
const android::String8 kRemoteAddress("127.0.0.1");
const int32_t kRemotePort = 10000;
const int8_t kDscp = 0;
const int8_t kRxPayload = 96;
const int8_t kTxPayload = 96;
const int8_t kSamplingRate = 16;

// RtcpConfig
const android::String8 kCanonicalName("name");
const int32_t kTransmitPort = 1001;
const int32_t kIntervalSec = 5;
const int32_t kRtcpXrBlockTypes = RtcpConfig::FLAG_RTCPXR_STATISTICS_SUMMARY_REPORT_BLOCK |
        RtcpConfig::FLAG_RTCPXR_VOIP_METRICS_REPORT_BLOCK;

// AudioConfig
const int8_t kPTimeMillis = 20;
const int32_t kMaxPtimeMillis = 100;
const bool kDtxEnabled = true;
const int32_t kCodecType = AudioConfig::CODEC_AMR_WB;
const int8_t kDtmfTxPayloadTypeNumber = 100;
const int8_t kDtmfRxPayloadTypeNumber = 101;
const int8_t kDtmfsamplingRateKHz = 16;

// AmrParam
const int32_t kAmrMode = 8;
const bool kOctetAligned = false;
const int32_t kMaxRedundancyMillis = 240;

// EvsParam
const int32_t kEvsBandwidth = EvsParams::EVS_BAND_NONE;
const int32_t kEvsMode = 8;
const int8_t kChannelAwareMode = 3;
const bool kUseHeaderFullOnly = false;
const int8_t kcodecModeRequest = 15;

int32_t kSessionId = 0;
static ImsMediaCondition gCondition;

class AudioManagerCallback
{
public:
    int32_t resSessionId;
    int32_t response;
    AudioConfig resConfig;
    ImsMediaResult result;
    std::list<RtpHeaderExtension> extensions;
    MediaQualityStatus mediaQualityStatus;
    char receivedDtmfDigit;
    int32_t receivedDtmfDuration;
    CallQuality callQuality;

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
            const int id, const int event, const ImsMediaResult res, const AudioConfig& config)
    {
        resSessionId = id;
        response = event;
        resConfig = config;
        result = res;
    }

    void onCallbackHeaderExtension(
            const int id, const int event, const std::list<RtpHeaderExtension>& list)
    {
        extensions.clear();
        resSessionId = id;
        response = event;
        std::copy(list.begin(), list.end(), std::back_inserter(extensions));
    }

    void onCallbackMediaQualityStatus(
            const int id, const int event, const MediaQualityStatus& status)
    {
        resSessionId = id;
        response = event;
        mediaQualityStatus = status;
    }

    void onCallbackDtmfReceived(const int id, const int event, char digit, int32_t duration)
    {
        resSessionId = id;
        response = event;
        receivedDtmfDigit = digit;
        receivedDtmfDuration = duration;
    }

    void onCallbackCallQuality(const int id, const int event, const CallQuality& status)
    {
        resSessionId = id;
        response = event;
        callQuality = status;
    }
};

static std::unordered_map<int, AudioManagerCallback*> gMapCallback;

class AudioManagerTest : public ::testing::Test
{
public:
    MockAudioManager manager;
    AudioConfig config;
    RtcpConfig rtcp;
    AmrParams amr;
    EvsParams evs;
    int socketRtpFd;
    int socketRtcpFd;
    AudioManagerCallback callback;

    AudioManagerTest()
    {
        socketRtpFd = -1;
        socketRtcpFd = -1;
        callback.resetRespond();
        gCondition.reset();
    }
    ~AudioManagerTest() {}

protected:
    virtual void SetUp() override
    {
        rtcp.setCanonicalName(kCanonicalName);
        rtcp.setTransmitPort(kTransmitPort);
        rtcp.setIntervalSec(kIntervalSec);
        rtcp.setRtcpXrBlockTypes(kRtcpXrBlockTypes);

        amr.setAmrMode(kAmrMode);
        amr.setOctetAligned(kOctetAligned);
        amr.setMaxRedundancyMillis(kMaxRedundancyMillis);

        evs.setEvsBandwidth(kEvsBandwidth);
        evs.setEvsMode(kEvsMode);
        evs.setChannelAwareMode(kChannelAwareMode);
        evs.setUseHeaderFullOnly(kUseHeaderFullOnly);
        evs.setCodecModeRequest(kcodecModeRequest);

        config.setMediaDirection(kMediaDirection);
        config.setRemoteAddress(kRemoteAddress);
        config.setRemotePort(kRemotePort);
        config.setRtcpConfig(rtcp);
        config.setDscp(kDscp);
        config.setRxPayloadTypeNumber(kRxPayload);
        config.setTxPayloadTypeNumber(kTxPayload);
        config.setSamplingRateKHz(kSamplingRate);
        config.setPtimeMillis(kPTimeMillis);
        config.setMaxPtimeMillis(kMaxPtimeMillis);
        config.setDtxEnabled(kDtxEnabled);
        config.setCodecType(kCodecType);
        config.setTxDtmfPayloadTypeNumber(kDtmfTxPayloadTypeNumber);
        config.setRxDtmfPayloadTypeNumber(kDtmfRxPayloadTypeNumber);
        config.setDtmfsamplingRateKHz(kDtmfsamplingRateKHz);
        config.setAmrParams(amr);
        config.setEvsParams(evs);

        manager.setCallback(&audioCallback);
        gMapCallback.insert(std::make_pair(kSessionId, &callback));
        const char testIp[] = "127.0.0.1";
        unsigned int testPortRtp = 30000;
        socketRtpFd = ImsMediaNetworkUtil::openSocket(testIp, testPortRtp, AF_INET);
        EXPECT_NE(socketRtpFd, -1);
        unsigned int testPortRtcp = 30001;
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
        parcel.writeInt32(kAudioOpenSession);
        parcel.writeInt32(socketRtpFd);
        parcel.writeInt32(socketRtcpFd);
        parcel.setDataPosition(0);
        gCondition.reset();
        manager.sendMessage(sessionId, parcel);
        EXPECT_TRUE(!gCondition.wait_timeout(1000));
        EXPECT_EQ(callback.resSessionId, sessionId);
        EXPECT_EQ(callback.response, kAudioOpenSessionSuccess);
    }

    void closeSession(const int32_t sessionId)
    {
        callback.resetRespond();
        android::Parcel parcel;
        parcel.writeInt32(kAudioCloseSession);
        parcel.setDataPosition(0);
        gCondition.reset();
        manager.sendMessage(sessionId, parcel);
        EXPECT_TRUE(!gCondition.wait_timeout(1000));
        EXPECT_EQ(callback.resSessionId, sessionId);
        EXPECT_EQ(callback.response, kAudioSessionClosed);
    }

    void testEventResponse(const int32_t sessionId, const int32_t event, AudioConfig* config,
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

        if (callback.response >= kAudioOpenSessionFailure &&
                callback.response <= kAudioConfirmConfigResponse)
        {
            EXPECT_EQ(result, result);

            if (config != nullptr && callback.response >= kAudioModifySessionResponse &&
                    callback.response <= kAudioConfirmConfigResponse)
            {
                EXPECT_EQ(callback.resConfig, *config);
            }
        }
    }

    static int32_t audioCallback(int sessionId, const android::Parcel& parcel)
    {
        parcel.setDataPosition(0);

        int response = parcel.readInt32();
        ImsMediaResult result = RESULT_INVALID_PARAM;

        auto callback = gMapCallback.find(sessionId);

        if (callback != gMapCallback.end())
        {
            if (response >= kAudioOpenSessionFailure && response <= kAudioConfirmConfigResponse)
            {
                result = static_cast<ImsMediaResult>(parcel.readInt32());
            }

            switch (response)
            {
                case kAudioModifySessionResponse:
                case kAudioAddConfigResponse:
                case kAudioConfirmConfigResponse:
                {
                    AudioConfig resConfig;
                    resConfig.readFromParcel(&parcel);
                    (callback->second)->onCallbackConfig(sessionId, response, result, resConfig);
                }
                break;
                case kAudioFirstMediaPacketInd:
                {
                    AudioConfig resConfig;
                    resConfig.readFromParcel(&parcel);
                    (callback->second)
                            ->onCallbackConfig(sessionId, response, RESULT_SUCCESS, resConfig);
                }
                break;
                case kAudioRtpHeaderExtensionInd:
                {
                    std::list<RtpHeaderExtension> listExtension;
                    int32_t listSize = parcel.readInt32();

                    for (int32_t i = 0; i < listSize; i++)
                    {
                        RtpHeaderExtension extension;
                        extension.readFromParcel(&parcel);
                        listExtension.push_back(extension);
                    }

                    (callback->second)
                            ->onCallbackHeaderExtension(sessionId, response, listExtension);
                }
                break;
                case kAudioMediaQualityStatusInd:
                {
                    MediaQualityStatus status;
                    status.readFromParcel(&parcel);
                    (callback->second)->onCallbackMediaQualityStatus(sessionId, response, status);
                }
                break;
                case kAudioDtmfReceivedInd:
                    (callback->second)
                            ->onCallbackDtmfReceived(
                                    sessionId, response, parcel.readByte(), parcel.readInt32());
                    break;
                case kAudioCallQualityChangedInd:
                {
                    CallQuality quality;
                    quality.readFromParcel(&parcel);
                    (callback->second)->onCallbackCallQuality(sessionId, response, quality);
                }
                break;
                default:
                    (callback->second)->onCallback(sessionId, response, result);
                    break;
            }
        }

        if (response != kAudioCallQualityChangedInd)
        {
            gCondition.signal();
        }

        return 0;
    }
};

TEST_F(AudioManagerTest, testOpenCloseSession)
{
    EXPECT_EQ(manager.getState(kSessionId), kSessionStateClosed);
    openSession(kSessionId);
    closeSession(kSessionId);
}

TEST_F(AudioManagerTest, testModifySession)
{
    testEventResponse(kSessionId, kAudioModifySession, nullptr, kAudioModifySessionResponse,
            RESULT_INVALID_PARAM);

    openSession(kSessionId);

    testEventResponse(kSessionId, kAudioModifySession, nullptr, kAudioModifySessionResponse,
            RESULT_INVALID_PARAM);

    testEventResponse(
            kSessionId, kAudioModifySession, &config, kAudioModifySessionResponse, RESULT_SUCCESS);

    closeSession(kSessionId);
}

TEST_F(AudioManagerTest, testAddConfig)
{
    testEventResponse(
            kSessionId, kAudioAddConfig, nullptr, kAudioAddConfigResponse, RESULT_INVALID_PARAM);

    openSession(kSessionId);

    testEventResponse(
            kSessionId, kAudioAddConfig, nullptr, kAudioAddConfigResponse, RESULT_INVALID_PARAM);

    testEventResponse(
            kSessionId, kAudioAddConfig, &config, kAudioAddConfigResponse, RESULT_SUCCESS);

    closeSession(kSessionId);
}

TEST_F(AudioManagerTest, testConfirmConfig)
{
    testEventResponse(kSessionId, kAudioConfirmConfig, nullptr, kAudioConfirmConfigResponse,
            RESULT_INVALID_PARAM);

    openSession(kSessionId);

    testEventResponse(kSessionId, kAudioConfirmConfig, nullptr, kAudioConfirmConfigResponse,
            RESULT_INVALID_PARAM);

    testEventResponse(
            kSessionId, kAudioConfirmConfig, &config, kAudioConfirmConfigResponse, RESULT_SUCCESS);

    closeSession(kSessionId);
}

TEST_F(AudioManagerTest, testDeleteConfig)
{
    openSession(kSessionId);

    android::Parcel parcel;
    parcel.writeInt32(kAudioDeleteConfig);

    if (config != nullptr)
    {
        config.writeToParcel(&parcel);
    }

    EXPECT_CALL(manager, deleteConfig(kSessionId, Pointee(Eq(config))))
            .Times(1)
            .WillOnce(Return(RESULT_INVALID_PARAM));

    parcel.setDataPosition(0);
    manager.sendMessage(kSessionId, parcel);

    gCondition.wait_timeout(20);
    closeSession(kSessionId);
}

TEST_F(AudioManagerTest, testSendDtmf)
{
    openSession(kSessionId);

    const char kDigit = '1';
    const int32_t kDuration = 100;

    android::Parcel parcel;
    parcel.writeInt32(kAudioSendDtmf);
    parcel.writeByte(kDigit);
    parcel.writeInt32(kDuration);
    parcel.setDataPosition(0);

    EXPECT_CALL(manager, sendDtmf(kSessionId, kDigit, kDuration)).Times(1).WillOnce(Return());

    manager.sendMessage(kSessionId, parcel);

    gCondition.wait_timeout(20);
    closeSession(kSessionId);
}

TEST_F(AudioManagerTest, testSendHeaderExtension)
{
    openSession(kSessionId);

    std::list<RtpHeaderExtension> extensions;
    RtpHeaderExtension extension;
    const uint8_t kExtensionData[] = {0x01, 0x02};
    const int32_t kExtensionDataSize = 2;
    extension.setLocalIdentifier(15);
    extension.setExtensionData(kExtensionData, kExtensionDataSize);
    extensions.push_back(extension);

    android::Parcel parcel;
    parcel.writeInt32(kAudioSendRtpHeaderExtension);
    parcel.writeInt32(extensions.size());

    for (auto& item : extensions)
    {
        item.writeToParcel(&parcel);
    }

    parcel.setDataPosition(0);

    EXPECT_CALL(manager, sendRtpHeaderExtension(kSessionId, Pointee(Eq(extensions))))
            .Times(1)
            .WillOnce(Return());

    manager.sendMessage(kSessionId, parcel);

    gCondition.wait_timeout(20);
    closeSession(kSessionId);
}

TEST_F(AudioManagerTest, testSetMediaQualityThreshold)
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
    parcel.writeInt32(kAudioSetMediaQualityThreshold);
    threshold.writeToParcel(&parcel);
    parcel.setDataPosition(0);

    EXPECT_CALL(manager, setMediaQualityThreshold(kSessionId, Pointee(Eq(threshold))))
            .Times(1)
            .WillOnce(Return());

    manager.sendMessage(kSessionId, parcel);

    gCondition.wait_timeout(20);
    closeSession(kSessionId);
}

TEST_F(AudioManagerTest, testSendInternalEventCmr)
{
    openSession(kSessionId);

    const int32_t kCmrCode = 1;
    const int32_t kCmrDefine = 7;

    EXPECT_CALL(manager, SendInternalEvent(kRequestAudioCmr, kSessionId, kCmrCode, kCmrDefine))
            .Times(1)
            .WillOnce(Return());

    ImsMediaEventHandler::SendEvent(
            "AUDIO_REQUEST_EVENT", kRequestAudioCmr, kSessionId, kCmrCode, kCmrDefine);

    gCondition.wait_timeout(20);
    closeSession(kSessionId);
}

TEST_F(AudioManagerTest, testSendInternalEventRtcpXr)
{
    openSession(kSessionId);

    const int32_t param1 = 10;
    const int32_t param2 = 20;

    EXPECT_CALL(manager, SendInternalEvent(kRequestSendRtcpXrReport, kSessionId, param1, param2))
            .Times(1)
            .WillOnce(Return());

    ImsMediaEventHandler::SendEvent(
            "AUDIO_REQUEST_EVENT", kRequestSendRtcpXrReport, kSessionId, param1, param2);

    gCondition.wait_timeout(20);
    closeSession(kSessionId);
}

TEST_F(AudioManagerTest, testFirstMediaPacketInd)
{
    AudioConfig* param = new AudioConfig(config);

    ImsMediaEventHandler::SendEvent("AUDIO_RESPONSE_EVENT", kAudioFirstMediaPacketInd, kSessionId,
            reinterpret_cast<uint64_t>(param), 0);

    gCondition.wait_timeout(20);
    EXPECT_EQ(callback.resSessionId, kSessionId);
    EXPECT_EQ(callback.response, kAudioFirstMediaPacketInd);
    EXPECT_EQ(callback.resConfig, config);
}

TEST_F(AudioManagerTest, testRtpHeaderExtensionInd)
{
    std::list<RtpHeaderExtension> extensions;
    RtpHeaderExtension extension;
    const uint8_t kExtensionData[] = {0x01, 0x02};
    const int32_t kExtensionDataSize = 2;
    extension.setLocalIdentifier(15);
    extension.setExtensionData(kExtensionData, kExtensionDataSize);
    extensions.push_back(extension);

    std::list<RtpHeaderExtension>* param = new std::list<RtpHeaderExtension>();
    std::copy(extensions.begin(), extensions.end(), std::back_inserter(*param));

    android::Parcel parcel;
    parcel.writeInt32(param->size());

    for (auto& item : *param)
    {
        item.writeToParcel(&parcel);
    }

    ImsMediaEventHandler::SendEvent("AUDIO_RESPONSE_EVENT", kAudioRtpHeaderExtensionInd, kSessionId,
            reinterpret_cast<uint64_t>(param), 0);

    gCondition.wait_timeout(20);
    EXPECT_EQ(callback.resSessionId, kSessionId);
    EXPECT_EQ(callback.response, kAudioRtpHeaderExtensionInd);
    EXPECT_EQ(callback.extensions, extensions);
}

TEST_F(AudioManagerTest, testMediaQualityStatusInd)
{
    MediaQualityStatus status;
    status.setRtpInactivityTimeMillis(10000);
    status.setRtcpInactivityTimeMillis(10000);
    status.setRtpPacketLossRate(1);
    status.setRtpJitterMillis(100);

    MediaQualityStatus* param = new MediaQualityStatus(status);

    ImsMediaEventHandler::SendEvent("AUDIO_RESPONSE_EVENT", kAudioMediaQualityStatusInd, kSessionId,
            reinterpret_cast<uint64_t>(param), 0);

    gCondition.wait_timeout(20);
    EXPECT_EQ(callback.resSessionId, kSessionId);
    EXPECT_EQ(callback.response, kAudioMediaQualityStatusInd);
    EXPECT_EQ(callback.mediaQualityStatus, status);
}

TEST_F(AudioManagerTest, testDtmfReceivedInd)
{
    const char digit = 1;
    const int32_t duration = 100;

    ImsMediaEventHandler::SendEvent(
            "AUDIO_RESPONSE_EVENT", kAudioDtmfReceivedInd, kSessionId, digit, duration);

    gCondition.wait_timeout(20);
    EXPECT_EQ(callback.resSessionId, kSessionId);
    EXPECT_EQ(callback.response, kAudioDtmfReceivedInd);
    EXPECT_EQ(callback.receivedDtmfDigit, digit);
    EXPECT_EQ(callback.receivedDtmfDuration, duration);
}

TEST_F(AudioManagerTest, testCallQualityInd)
{
    CallQuality quality;
    quality.setDownlinkCallQualityLevel(0);
    quality.setUplinkCallQualityLevel(0);
    quality.setCallDuration(30000);
    quality.setNumRtpPacketsTransmitted(1500);
    quality.setNumRtpPacketsReceived(1500);
    quality.setNumRtpPacketsTransmittedLost(1);
    quality.setNumRtpPacketsNotReceived(2);
    quality.setAverageRelativeJitter(50);
    quality.setMaxRelativeJitter(150);
    quality.setAverageRoundTripTime(60);
    quality.setCodecType(AudioConfig::CODEC_AMR_WB);
    quality.setRtpInactivityDetected(false);
    quality.setRxSilenceDetected(false);
    quality.setTxSilenceDetected(false);
    quality.setNumVoiceFrames(1400);
    quality.setNumNoDataFrames(0);
    quality.setNumDroppedRtpPackets(0);
    quality.setMinPlayoutDelayMillis(100);
    quality.setMaxPlayoutDelayMillis(180);
    quality.setNumRtpSidPacketsReceived(100);
    quality.setNumRtpDuplicatePackets(1);

    CallQuality* param = new CallQuality(quality);

    ImsMediaEventHandler::SendEvent("AUDIO_RESPONSE_EVENT", kAudioCallQualityChangedInd, kSessionId,
            reinterpret_cast<uint64_t>(param), 0);

    gCondition.wait_timeout(20);
    EXPECT_EQ(callback.resSessionId, kSessionId);
    EXPECT_EQ(callback.response, kAudioCallQualityChangedInd);
    EXPECT_EQ(callback.callQuality, quality);
}
}  // namespace