/*
 * Copyright (C) 2020 The Android Open Source Project
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

#pragma once
#include <memory>
#include <tinyalsa/asoundlib.h>

namespace android {
namespace hardware {
namespace audio {
namespace CPP_VERSION {
namespace implementation {
namespace talsa {

constexpr unsigned int kPcmDevice = 0;
constexpr unsigned int kPcmCard = 0;

struct PcmPeriodSettings {
    unsigned periodCount;
    unsigned periodSizeMultiplier;
};

void init();
PcmPeriodSettings pcmGetPcmPeriodSettings();
unsigned pcmGetHostLatencyMs();

typedef struct pcm pcm_t;
struct PcmDeleter { void operator()(pcm_t *x) const; };
typedef std::unique_ptr<pcm_t, PcmDeleter> PcmPtr;
PcmPtr pcmOpen(unsigned int dev, unsigned int card, unsigned int nChannels,
               size_t sampleRateHz, size_t frameCount, bool isOut);
int pcmRead(pcm_t *pcm, void *data, int szBytes, unsigned int frameSize);
int pcmWrite(pcm_t *pcm, const void *data, int szBytes, unsigned int frameSize);

class Mixer {
public:
    Mixer(unsigned card);
    ~Mixer();

    operator bool() const { return mMixer != nullptr; }

    Mixer(const Mixer &) = delete;
    Mixer &operator=(const Mixer &) = delete;
    Mixer(Mixer &&) = delete;
    Mixer &operator=(Mixer &&) = delete;

private:
    struct mixer *mMixer;
};

}  // namespace talsa
}  // namespace implementation
}  // namespace CPP_VERSION
}  // namespace audio
}  // namespace hardware
}  // namespace android
