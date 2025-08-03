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

#include <ImsMediaAudioPlayer.h>

ImsMediaAudioPlayer::ImsMediaAudioPlayer() {}

ImsMediaAudioPlayer::~ImsMediaAudioPlayer() {}

void ImsMediaAudioPlayer::SetCodec(int32_t /*type*/) {}

void ImsMediaAudioPlayer::SetEvsBitRate(int32_t /*bitRate*/) {}

void ImsMediaAudioPlayer::SetSamplingRate(int32_t /*samplingRate*/) {}

void ImsMediaAudioPlayer::SetCodecMode(uint32_t /*mode*/) {}

void ImsMediaAudioPlayer::SetEvsChAwOffset(int32_t /*offset*/) {}

void ImsMediaAudioPlayer::SetEvsBandwidth(int32_t /*evsBandwidth*/) {}

void ImsMediaAudioPlayer::SetEvsPayloadHeaderMode(int32_t /*EvsPayloadHeaderMode*/) {}

void ImsMediaAudioPlayer::SetDtxEnabled(bool /*isDtxEnabled*/) {}

void ImsMediaAudioPlayer::SetOctetAligned(bool /*isOctetAligned*/) {}

void ImsMediaAudioPlayer::ProcessCmr(const uint32_t /*cmr*/) {}

bool ImsMediaAudioPlayer::Start()
{
    return true;
}

void ImsMediaAudioPlayer::Stop() {}

bool ImsMediaAudioPlayer::onDataFrame(uint8_t* /*buffer*/, uint32_t /*size*/, bool /*isSid*/)
{
    return true;
}
