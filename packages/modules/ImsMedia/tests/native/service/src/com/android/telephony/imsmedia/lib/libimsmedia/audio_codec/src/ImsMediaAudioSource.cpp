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

#include <ImsMediaAudioSource.h>

ImsMediaAudioSource::ImsMediaAudioSource() {}

ImsMediaAudioSource::~ImsMediaAudioSource() {}

void ImsMediaAudioSource::SetUplinkCallback(IFrameCallback* /*callback*/) {}

void ImsMediaAudioSource::SetCodec(int32_t /* type*/) {}

void ImsMediaAudioSource::SetCodecMode(uint32_t /* mode*/) {}

void ImsMediaAudioSource::SetEvsBitRate(uint32_t /* mode*/) {}

void ImsMediaAudioSource::SetPtime(uint32_t /* time*/) {}

void ImsMediaAudioSource::SetEvsBandwidth(int32_t /* evsBandwidth*/) {}

void ImsMediaAudioSource::SetSamplingRate(int32_t /* samplingRate*/) {}

void ImsMediaAudioSource::SetEvsChAwOffset(int32_t /* offset*/) {}

void ImsMediaAudioSource::SetMediaDirection(int32_t /* direction*/) {}

void ImsMediaAudioSource::SetDtxEnabled(bool /*isDtxEnabled*/) {}

void ImsMediaAudioSource::SetOctetAligned(bool /*isOctetAligned*/) {}

bool ImsMediaAudioSource::Start()
{
    return true;
}
void ImsMediaAudioSource::Stop() {}

void ImsMediaAudioSource::ProcessCmr(const uint32_t /* cmr*/) {}