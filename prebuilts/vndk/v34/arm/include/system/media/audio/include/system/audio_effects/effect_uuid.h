/*
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

#ifndef ANDROID_EFFECT_UUID_H_
#define ANDROID_EFFECT_UUID_H_

#include <aidl/android/hardware/audio/effect/Descriptor.h>
#include <aidl/android/media/audio/common/AudioUuid.h>
#include <android-base/no_destructor.h>

namespace aidl::android::hardware::audio::effect {

using ::aidl::android::media::audio::common::AudioUuid;

inline AudioUuid stringToUuid(const char* str) {
    AudioUuid uuid{};
    uint32_t tmp[10];
    if (!str || sscanf(str, "%08x-%04x-%04x-%04x-%02x%02x%02x%02x%02x%02x", tmp,
                       tmp + 1, tmp + 2, tmp + 3, tmp + 4, tmp + 5, tmp + 6,
                       tmp + 7, tmp + 8, tmp + 9) < 10) {
      return uuid;
    }

    uuid.timeLow = (uint32_t)tmp[0];
    uuid.timeMid = (uint16_t)tmp[1];
    uuid.timeHiAndVersion = (uint16_t)tmp[2];
    uuid.clockSeq = (uint16_t)tmp[3];
    uuid.node.insert(uuid.node.end(), {(uint8_t)tmp[4], (uint8_t)tmp[5], (uint8_t)tmp[6],
                                       (uint8_t)tmp[7], (uint8_t)tmp[8], (uint8_t)tmp[9]});
    return uuid;
}

inline const char* const& kEffectTypeUuidAcousticEchoCanceler =
    Descriptor::EFFECT_TYPE_UUID_AEC;
inline const char* const& kEffectTypeUuidAutomaticGainControlV1 =
    Descriptor::EFFECT_TYPE_UUID_AGC1;
inline const char* const& kEffectTypeUuidAutomaticGainControlV2 =
    Descriptor::EFFECT_TYPE_UUID_AGC2;
inline const char* const& kEffectTypeUuidBassBoost =
    Descriptor::EFFECT_TYPE_UUID_BASS_BOOST;
inline const char* const& kEffectTypeUuidDownmix =
    Descriptor::EFFECT_TYPE_UUID_DOWNMIX;
inline const char* const& kEffectTypeUuidDynamicsProcessing =
    Descriptor::EFFECT_TYPE_UUID_DYNAMICS_PROCESSING;
inline const char* const& kEffectTypeUuidEnvReverb =
    Descriptor::EFFECT_TYPE_UUID_ENV_REVERB;
inline const char* const& kEffectTypeUuidEqualizer =
    Descriptor::EFFECT_TYPE_UUID_EQUALIZER;
inline const char* const& kEffectTypeUuidHapticGenerator =
    Descriptor::EFFECT_TYPE_UUID_HAPTIC_GENERATOR;
inline const char* const& kEffectTypeUuidLoudnessEnhancer =
    Descriptor::EFFECT_TYPE_UUID_LOUDNESS_ENHANCER;
inline const char* const& kEffectTypeUuidNoiseSuppression =
    Descriptor::EFFECT_TYPE_UUID_NS;
inline const char* const& kEffectTypeUuidPresetReverb =
    Descriptor::EFFECT_TYPE_UUID_PRESET_REVERB;
inline const char* const& kEffectTypeUuidSpatializer =
    Descriptor::EFFECT_TYPE_UUID_SPATIALIZER;
inline const char* const& kEffectTypeUuidVirtualizer =
    Descriptor::EFFECT_TYPE_UUID_VIRTUALIZER;
inline const char* const& kEffectTypeUuidVisualizer =
    Descriptor::EFFECT_TYPE_UUID_VISUALIZER;
inline const char* const& kEffectTypeUuidVolume =
    Descriptor::EFFECT_TYPE_UUID_VOLUME;

constexpr char kEffectImplUuidAcousticEchoCancelerSw[] = "bb392ec0-8d4d-11e0-a896-0002a5d5c51b";
constexpr char kEffectImplUuidAutomaticGainControlV1Sw[] = "aa8130e0-66fc-11e0-bad0-0002a5d5c51b";
constexpr char kEffectImplUuidAutomaticGainControlV2Sw[] = "89f38e65-d4d2-4d64-ad0e-2b3e799ea886";
constexpr char kEffectImplUuidBassBoostSw[] = "fa8181f2-588b-11ed-9b6a-0242ac120002";
constexpr char kEffectImplUuidBassBoostBundle[] = "8631f300-72e2-11df-b57e-0002a5d5c51b";
constexpr char kEffectImplUuidBassBoostProxy[] = "14804144-a5ee-4d24-aa88-0002a5d5c51b";
constexpr char kEffectImplUuidDownmixSw[] = "fa8187ba-588b-11ed-9b6a-0242ac120002";
constexpr char kEffectImplUuidDownmix[] = "93f04452-e4fe-41cc-91f9-e475b6d1d69f";
constexpr char kEffectImplUuidDynamicsProcessingSw[] = "fa818d78-588b-11ed-9b6a-0242ac120002";
constexpr char kEffectImplUuidDynamicsProcessing[] = "e0e6539b-1781-7261-676f-6d7573696340";
constexpr char kEffectImplUuidEqualizerSw[] = "0bed4300-847d-11df-bb17-0002a5d5c51b";
constexpr char kEffectImplUuidEqualizerBundle[] = "ce772f20-847d-11df-bb17-0002a5d5c51b";
constexpr char kEffectImplUuidEqualizerProxy[] = "c8e70ecd-48ca-456e-8a4f-0002a5d5c51b";
constexpr char kEffectImplUuidHapticGeneratorSw[] = "fa819110-588b-11ed-9b6a-0242ac120002";
constexpr char kEffectImplUuidHapticGenerator[] = "97c4acd1-8b82-4f2f-832e-c2fe5d7a9931";
constexpr char kEffectImplUuidLoudnessEnhancerSw[] = "fa819610-588b-11ed-9b6a-0242ac120002";
constexpr char kEffectImplUuidLoudnessEnhancer[] = "fa415329-2034-4bea-b5dc-5b381c8d1e2c";
constexpr char kEffectImplUuidEnvReverbSw[] = "fa819886-588b-11ed-9b6a-0242ac120002";
constexpr char kEffectImplUuidAuxEnvReverb[] = "4a387fc0-8ab3-11df-8bad-0002a5d5c51b";
constexpr char kEffectImplUuidInsertEnvReverb[] = "c7a511a0-a3bb-11df-860e-0002a5d5c51b";
constexpr char kEffectImplUuidNoiseSuppressionSw[] = "c06c8400-8e06-11e0-9cb6-0002a5d5c51b";
constexpr char kEffectImplUuidPresetReverbSw[] = "fa8199c6-588b-11ed-9b6a-0242ac120002";
constexpr char kEffectImplUuidAuxPresetReverb[] = "f29a1400-a3bb-11df-8ddc-0002a5d5c51b";
constexpr char kEffectImplUuidInsertPresetReverb[] = "172cdf00-a3bc-11df-a72f-0002a5d5c51b";
constexpr char kEffectImplUuidVirtualizerSw[] = "fa819d86-588b-11ed-9b6a-0242ac120002";
constexpr char kEffectImplUuidVirtualizerBundle[] = "1d4033c0-8557-11df-9f2d-0002a5d5c51b";
constexpr char kEffectImplUuidVirtualizerProxy[] = "d3467faa-acc7-4d34-acaf-0002a5d5c51b";
constexpr char kEffectImplUuidVisualizerSw[] = "fa81a0f6-588b-11ed-9b6a-0242ac120002";
constexpr char kEffectImplUuidVisualizer[] = "d069d9e0-8329-11df-9168-0002a5d5c51b";
constexpr char kEffectImplUuidVisualizerProxy[] = "1d0a1a53-7d5d-48f2-8e71-27fbd10d842c";
constexpr char kEffectImplUuidVolumeSw[] = "fa81a718-588b-11ed-9b6a-0242ac120002";
constexpr char kEffectImplUuidVolumeBundle[] = "119341a0-8469-11df-81f9-0002a5d5c51b";

constexpr char kEffectUuidNull[] = "ec7178ec-e5e1-4432-a3f4-4657e6795210";
constexpr char kEffectUuidZero[] = "00000000-0000-0000-0000-000000000000";
constexpr char kEffectUuidExtensionType[] = "fa81dbde-588b-11ed-9b6a-0242ac120002";
constexpr char kEffectUuidExtensionImpl[] = "fa81dd00-588b-11ed-9b6a-0242ac120002";

#define EFFECT_TYPE_UUID_LIST_DEF(V)  \
    V(TypeUuidAcousticEchoCanceler)   \
    V(TypeUuidAutomaticGainControlV1) \
    V(TypeUuidAutomaticGainControlV2) \
    V(TypeUuidBassBoost)              \
    V(TypeUuidDownmix)                \
    V(TypeUuidDynamicsProcessing)     \
    V(TypeUuidEqualizer)              \
    V(TypeUuidHapticGenerator)        \
    V(TypeUuidLoudnessEnhancer)       \
    V(TypeUuidEnvReverb)              \
    V(TypeUuidPresetReverb)           \
    V(TypeUuidNoiseSuppression)       \
    V(TypeUuidSpatializer)            \
    V(TypeUuidVirtualizer)            \
    V(TypeUuidVisualizer)             \
    V(TypeUuidVolume)

#define EFFECT_IMPL_UUID_LIST_DEF(V)    \
    V(ImplUuidAcousticEchoCancelerSw)   \
    V(ImplUuidAutomaticGainControlV1Sw) \
    V(ImplUuidAutomaticGainControlV2Sw) \
    V(ImplUuidBassBoostSw)              \
    V(ImplUuidBassBoostBundle)          \
    V(ImplUuidBassBoostProxy)           \
    V(ImplUuidDownmixSw)                \
    V(ImplUuidDownmix)                  \
    V(ImplUuidDynamicsProcessingSw)     \
    V(ImplUuidDynamicsProcessing)       \
    V(ImplUuidEqualizerSw)              \
    V(ImplUuidEqualizerBundle)          \
    V(ImplUuidEqualizerProxy)           \
    V(ImplUuidHapticGeneratorSw)        \
    V(ImplUuidHapticGenerator)          \
    V(ImplUuidLoudnessEnhancerSw)       \
    V(ImplUuidLoudnessEnhancer)         \
    V(ImplUuidEnvReverbSw)              \
    V(ImplUuidAuxEnvReverb)             \
    V(ImplUuidInsertEnvReverb)          \
    V(ImplUuidNoiseSuppressionSw)       \
    V(ImplUuidPresetReverbSw)           \
    V(ImplUuidAuxPresetReverb)          \
    V(ImplUuidInsertPresetReverb)       \
    V(ImplUuidVirtualizerSw)            \
    V(ImplUuidVirtualizerBundle)        \
    V(ImplUuidVirtualizerProxy)         \
    V(ImplUuidVisualizerSw)             \
    V(ImplUuidVisualizer)               \
    V(ImplUuidVisualizerProxy)          \
    V(ImplUuidVolumeSw)                 \
    V(ImplUuidVolumeBundle)

#define EFFECT_OTHER_UUID_LIST_DEF(V) \
    V(UuidNull)                           \
    V(UuidZero)                           \
    V(UuidExtensionType)                  \
    V(UuidExtensionImpl)

#define GENERATE_UUID_GETTER_V(symbol)                            \
    inline const AudioUuid& getEffect##symbol() {                 \
      static const ::android::base::NoDestructor<AudioUuid> uuid( \
          stringToUuid(kEffect##symbol));                         \
      return *uuid;                                               \
    }

// Generate all the UUID getter functions:
EFFECT_TYPE_UUID_LIST_DEF(GENERATE_UUID_GETTER_V)

EFFECT_IMPL_UUID_LIST_DEF(GENERATE_UUID_GETTER_V)

EFFECT_OTHER_UUID_LIST_DEF(GENERATE_UUID_GETTER_V)

}  // namespace aidl::android::hardware::audio::effect

#endif  // ANDROID_EFFECT_UUID_H_