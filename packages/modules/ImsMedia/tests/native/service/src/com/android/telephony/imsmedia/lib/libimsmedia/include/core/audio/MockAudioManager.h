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

#ifndef MOCK_AUDIO_MANAGER_H
#define MOCK_AUDIO_MANAGER_H

#include <AudioManager.h>
#include <ImsMediaDefine.h>
#include <gmock/gmock.h>

class MockAudioManager : public AudioManager
{
public:
    MockAudioManager() { sManager = this; }
    virtual ~MockAudioManager() { sManager = nullptr; }
    MOCK_METHOD(ImsMediaResult, deleteConfig, (int sessionId, AudioConfig* config), (override));
    MOCK_METHOD(void, sendDtmf, (int sessionId, char dtmfDigit, int duration), (override));
    MOCK_METHOD(void, sendRtpHeaderExtension,
            (int sessionId, std::list<RtpHeaderExtension>* listExtension), (override));
    MOCK_METHOD(void, setMediaQualityThreshold, (int sessionId, MediaQualityThreshold* threshold),
            (override));
    MOCK_METHOD(void, SendInternalEvent,
            (uint32_t event, uint64_t sessionId, uint64_t paramA, uint64_t paramB), (override));
};

#endif