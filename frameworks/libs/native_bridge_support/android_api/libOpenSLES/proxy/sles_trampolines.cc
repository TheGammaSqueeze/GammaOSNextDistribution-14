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

#include <dlfcn.h>
#include <string.h>

#include "SLES/OpenSLES.h"
#include "SLES/OpenSLES_Android.h"

#include "berberis/base/logging.h"
#include "berberis/base/macros.h"
#include "berberis/guest_abi/function_wrappers.h"
#include "berberis/guest_abi/guest_params.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/guest_state/guest_state.h"
#include "berberis/proxy_loader/proxy_library_builder.h"

#define LOG_SLES(...)

namespace berberis {

namespace {

#define REGISTER_TRAMPOLINE(itf_name, func_name) \
  WrapHostFunction((*itf)->func_name, #itf_name "::" #func_name)

#define REGISTER_CUSTOM_TRAMPOLINE(itf_name, func_name)                                \
  do {                                                                                 \
    void* func = reinterpret_cast<void*>((*itf)->func_name);                           \
    WrapHostFunctionImpl(                                                              \
        func, DoCustomTrampoline_##itf_name##_##func_name, #itf_name "::" #func_name); \
  } while (0)

void RegisterSL3DCommitItfMethods(SL3DCommitItf itf) {
  REGISTER_TRAMPOLINE(SL3DCommit, Commit);
  REGISTER_TRAMPOLINE(SL3DCommit, SetDeferred);
}

void RegisterSL3DDopplerItfMethods(SL3DDopplerItf itf) {
  REGISTER_TRAMPOLINE(SL3DDoppler, SetVelocityCartesian);
  REGISTER_TRAMPOLINE(SL3DDoppler, SetVelocitySpherical);
  REGISTER_TRAMPOLINE(SL3DDoppler, GetVelocityCartesian);
  REGISTER_TRAMPOLINE(SL3DDoppler, SetDopplerFactor);
  REGISTER_TRAMPOLINE(SL3DDoppler, GetDopplerFactor);
}

void RegisterSL3DGroupingItfMethods(SL3DGroupingItf itf) {
  REGISTER_TRAMPOLINE(SL3DGrouping, Set3DGroup);
  REGISTER_TRAMPOLINE(SL3DGrouping, Get3DGroup);
}

void RegisterSL3DLocationItfMethods(SL3DLocationItf itf) {
  REGISTER_TRAMPOLINE(SL3DLocation, SetLocationCartesian);
  REGISTER_TRAMPOLINE(SL3DLocation, SetLocationSpherical);
  REGISTER_TRAMPOLINE(SL3DLocation, Move);
  REGISTER_TRAMPOLINE(SL3DLocation, GetLocationCartesian);
  REGISTER_TRAMPOLINE(SL3DLocation, SetOrientationVectors);
  REGISTER_TRAMPOLINE(SL3DLocation, SetOrientationAngles);
  REGISTER_TRAMPOLINE(SL3DLocation, Rotate);
  REGISTER_TRAMPOLINE(SL3DLocation, GetOrientationVectors);
}

void RegisterSL3DMacroscopicItfMethods(SL3DMacroscopicItf itf) {
  REGISTER_TRAMPOLINE(SL3DMacroscopic, SetSize);
  REGISTER_TRAMPOLINE(SL3DMacroscopic, GetSize);
  REGISTER_TRAMPOLINE(SL3DMacroscopic, SetOrientationAngles);
  REGISTER_TRAMPOLINE(SL3DMacroscopic, SetOrientationVectors);
  REGISTER_TRAMPOLINE(SL3DMacroscopic, Rotate);
  REGISTER_TRAMPOLINE(SL3DMacroscopic, GetOrientationVectors);
}

void RegisterSL3DSourceItfMethods(SL3DSourceItf itf) {
  REGISTER_TRAMPOLINE(SL3DSource, SetHeadRelative);
  REGISTER_TRAMPOLINE(SL3DSource, GetHeadRelative);
  REGISTER_TRAMPOLINE(SL3DSource, SetRolloffDistances);
  REGISTER_TRAMPOLINE(SL3DSource, GetRolloffDistances);
  REGISTER_TRAMPOLINE(SL3DSource, SetRolloffMaxDistanceMute);
  REGISTER_TRAMPOLINE(SL3DSource, GetRolloffMaxDistanceMute);
  REGISTER_TRAMPOLINE(SL3DSource, SetRolloffFactor);
  REGISTER_TRAMPOLINE(SL3DSource, GetRolloffFactor);
  REGISTER_TRAMPOLINE(SL3DSource, SetRoomRolloffFactor);
  REGISTER_TRAMPOLINE(SL3DSource, GetRoomRolloffFactor);
  REGISTER_TRAMPOLINE(SL3DSource, SetRolloffModel);
  REGISTER_TRAMPOLINE(SL3DSource, GetRolloffModel);
  REGISTER_TRAMPOLINE(SL3DSource, SetCone);
  REGISTER_TRAMPOLINE(SL3DSource, GetCone);
}

void RegisterSLAndroidAcousticEchoCancellationItfMethods(SLAndroidAcousticEchoCancellationItf itf) {
  REGISTER_TRAMPOLINE(SLAndroidAcousticEchoCancellation, SetEnabled);
  REGISTER_TRAMPOLINE(SLAndroidAcousticEchoCancellation, IsEnabled);
}

void RegisterSLAndroidAutomaticGainControlItfMethods(SLAndroidAutomaticGainControlItf itf) {
  REGISTER_TRAMPOLINE(SLAndroidAutomaticGainControl, SetEnabled);
  REGISTER_TRAMPOLINE(SLAndroidAutomaticGainControl, IsEnabled);
}

void DoCustomTrampoline_SLAndroidBufferQueueItf_RegisterCallback(HostCode /* callee */,
                                                                 ProcessState* /* state */) {
  LOG_ALWAYS_FATAL("not implemented: SLAndroidBufferQueueItf::RegisterCallback");
}

void RegisterSLAndroidBufferQueueItfMethods(SLAndroidBufferQueueItf itf) {
  REGISTER_CUSTOM_TRAMPOLINE(SLAndroidBufferQueueItf, RegisterCallback);
  REGISTER_TRAMPOLINE(SLAndroidBufferQueue, Clear);
  REGISTER_TRAMPOLINE(SLAndroidBufferQueue, Enqueue);
  REGISTER_TRAMPOLINE(SLAndroidBufferQueue, GetState);
  REGISTER_TRAMPOLINE(SLAndroidBufferQueue, SetCallbackEventsMask);
  REGISTER_TRAMPOLINE(SLAndroidBufferQueue, GetCallbackEventsMask);
}

void RegisterSLAndroidConfigurationItfMethods(SLAndroidConfigurationItf itf) {
  REGISTER_TRAMPOLINE(SLAndroidConfiguration, SetConfiguration);
  REGISTER_TRAMPOLINE(SLAndroidConfiguration, GetConfiguration);
  REGISTER_TRAMPOLINE(SLAndroidConfiguration, AcquireJavaProxy);
  REGISTER_TRAMPOLINE(SLAndroidConfiguration, ReleaseJavaProxy);
}

void RegisterSLAndroidEffectItfMethods(SLAndroidEffectItf itf) {
  REGISTER_TRAMPOLINE(SLAndroidEffect, CreateEffect);
  REGISTER_TRAMPOLINE(SLAndroidEffect, ReleaseEffect);
  REGISTER_TRAMPOLINE(SLAndroidEffect, SetEnabled);
  REGISTER_TRAMPOLINE(SLAndroidEffect, IsEnabled);
  REGISTER_TRAMPOLINE(SLAndroidEffect, SendCommand);
}

void RegisterSLAndroidEffectCapabilitiesItfMethods(SLAndroidEffectCapabilitiesItf itf) {
  REGISTER_TRAMPOLINE(SLAndroidEffectCapabilities, QueryNumEffects);
  REGISTER_TRAMPOLINE(SLAndroidEffectCapabilities, QueryEffect);
}

void RegisterSLAndroidEffectSendItfMethods(SLAndroidEffectSendItf itf) {
  REGISTER_TRAMPOLINE(SLAndroidEffectSend, EnableEffectSend);
  REGISTER_TRAMPOLINE(SLAndroidEffectSend, IsEnabled);
  REGISTER_TRAMPOLINE(SLAndroidEffectSend, SetDirectLevel);
  REGISTER_TRAMPOLINE(SLAndroidEffectSend, GetDirectLevel);
  REGISTER_TRAMPOLINE(SLAndroidEffectSend, SetSendLevel);
  REGISTER_TRAMPOLINE(SLAndroidEffectSend, GetSendLevel);
}

void RegisterSLAndroidNoiseSuppressionItfMethods(SLAndroidNoiseSuppressionItf itf) {
  REGISTER_TRAMPOLINE(SLAndroidNoiseSuppression, SetEnabled);
  REGISTER_TRAMPOLINE(SLAndroidNoiseSuppression, IsEnabled);
}

void DoCustomTrampoline_SLAndroidSimpleBufferQueueItf_RegisterCallback(HostCode callee,
                                                                       ProcessState* state) {
  using PFN_callee = decltype(std::declval<SLAndroidSimpleBufferQueueItf_>().RegisterCallback);
  PFN_callee callee_function = AsFuncPtr(callee);
  auto [buffer_queue, guest_callback, context] = GuestParamsValues<PFN_callee>(state);
  slAndroidSimpleBufferQueueCallback host_callback =
      WrapGuestFunction(guest_callback, "SLAndroidSimpleBufferQueueItf_RegisterCallback-callback");
  auto&& [ret] = GuestReturnReference<PFN_callee>(state);
  ret = callee_function(buffer_queue, host_callback, context);
}

void RegisterSLAndroidSimpleBufferQueueItfMethods(SLAndroidSimpleBufferQueueItf itf) {
  REGISTER_TRAMPOLINE(SLAndroidSimpleBufferQueue, Enqueue);
  REGISTER_TRAMPOLINE(SLAndroidSimpleBufferQueue, Clear);
  REGISTER_TRAMPOLINE(SLAndroidSimpleBufferQueue, GetState);
  REGISTER_CUSTOM_TRAMPOLINE(SLAndroidSimpleBufferQueueItf, RegisterCallback);
}

void RegisterSLAudioDecoderCapabilitiesItfMethods(SLAudioDecoderCapabilitiesItf itf) {
  REGISTER_TRAMPOLINE(SLAudioDecoderCapabilities, GetAudioDecoders);
  REGISTER_TRAMPOLINE(SLAudioDecoderCapabilities, GetAudioDecoderCapabilities);
}

void RegisterSLAudioEncoderItfMethods(SLAudioEncoderItf itf) {
  REGISTER_TRAMPOLINE(SLAudioEncoder, SetEncoderSettings);
  REGISTER_TRAMPOLINE(SLAudioEncoder, GetEncoderSettings);
}

void RegisterSLAudioEncoderCapabilitiesItfMethods(SLAudioEncoderCapabilitiesItf itf) {
  REGISTER_TRAMPOLINE(SLAudioEncoderCapabilities, GetAudioEncoders);
  REGISTER_TRAMPOLINE(SLAudioEncoderCapabilities, GetAudioEncoderCapabilities);
}

void DoCustomTrampoline_SLAudioIODeviceCapabilitiesItf_RegisterAvailableAudioInputsChangedCallback(
    HostCode /* callee */,
    ProcessState* /* state */) {
  LOG_ALWAYS_FATAL(
      "not implemented: "
      "SLAudioIODeviceCapabilitiesItf::RegisterAvailableAudioInputsChangedCallback");
}

void DoCustomTrampoline_SLAudioIODeviceCapabilitiesItf_RegisterAvailableAudioOutputsChangedCallback(
    HostCode /* callee */,
    ProcessState* /* state */) {
  LOG_ALWAYS_FATAL(
      "not implemented: "
      "SLAudioIODeviceCapabilitiesItf::RegisterAvailableAudioOutputsChangedCallback");
}

void DoCustomTrampoline_SLAudioIODeviceCapabilitiesItf_RegisterDefaultDeviceIDMapChangedCallback(
    HostCode /* callee */,
    ProcessState* /* state */) {
  LOG_ALWAYS_FATAL(
      "not implemented: SLAudioIODeviceCapabilitiesItf::RegisterDefaultDeviceIDMapChangedCallback");
}

void RegisterSLAudioIODeviceCapabilitiesItfMethods(SLAudioIODeviceCapabilitiesItf itf) {
  REGISTER_TRAMPOLINE(SLAudioIODeviceCapabilities, GetAvailableAudioInputs);
  REGISTER_TRAMPOLINE(SLAudioIODeviceCapabilities, QueryAudioInputCapabilities);
  REGISTER_CUSTOM_TRAMPOLINE(SLAudioIODeviceCapabilitiesItf,
                             RegisterAvailableAudioInputsChangedCallback);
  REGISTER_TRAMPOLINE(SLAudioIODeviceCapabilities, GetAvailableAudioOutputs);
  REGISTER_TRAMPOLINE(SLAudioIODeviceCapabilities, QueryAudioOutputCapabilities);
  REGISTER_CUSTOM_TRAMPOLINE(SLAudioIODeviceCapabilitiesItf,
                             RegisterAvailableAudioOutputsChangedCallback);
  REGISTER_CUSTOM_TRAMPOLINE(SLAudioIODeviceCapabilitiesItf,
                             RegisterDefaultDeviceIDMapChangedCallback);
  REGISTER_TRAMPOLINE(SLAudioIODeviceCapabilities, GetAssociatedAudioInputs);
  REGISTER_TRAMPOLINE(SLAudioIODeviceCapabilities, GetAssociatedAudioOutputs);
  REGISTER_TRAMPOLINE(SLAudioIODeviceCapabilities, GetDefaultAudioDevices);
  REGISTER_TRAMPOLINE(SLAudioIODeviceCapabilities, QuerySampleFormatsSupported);
}

void RegisterSLBassBoostItfMethods(SLBassBoostItf itf) {
  REGISTER_TRAMPOLINE(SLBassBoost, SetEnabled);
  REGISTER_TRAMPOLINE(SLBassBoost, IsEnabled);
  REGISTER_TRAMPOLINE(SLBassBoost, SetStrength);
  REGISTER_TRAMPOLINE(SLBassBoost, GetRoundedStrength);
  REGISTER_TRAMPOLINE(SLBassBoost, IsStrengthSupported);
}

void DoCustomTrampoline_SLBufferQueueItf_RegisterCallback(HostCode callee, ProcessState* state) {
  using PFN_callee = decltype(std::declval<SLBufferQueueItf_>().RegisterCallback);
  PFN_callee callee_function = AsFuncPtr(callee);
  auto [buffer_queue, guest_callback, context] = GuestParamsValues<PFN_callee>(state);
  slBufferQueueCallback host_callback =
      WrapGuestFunction(guest_callback, "SLBufferQueueItf_RegisterCallback-callback");
  auto&& [ret] = GuestReturnReference<PFN_callee>(state);
  ret = callee_function(buffer_queue, host_callback, context);
}

void RegisterSLBufferQueueItfMethods(SLBufferQueueItf itf) {
  REGISTER_TRAMPOLINE(SLBufferQueue, Enqueue);
  REGISTER_TRAMPOLINE(SLBufferQueue, Clear);
  REGISTER_TRAMPOLINE(SLBufferQueue, GetState);
  REGISTER_CUSTOM_TRAMPOLINE(SLBufferQueueItf, RegisterCallback);
}

void RegisterSLDeviceVolumeItfMethods(SLDeviceVolumeItf itf) {
  REGISTER_TRAMPOLINE(SLDeviceVolume, GetVolumeScale);
  REGISTER_TRAMPOLINE(SLDeviceVolume, SetVolume);
  REGISTER_TRAMPOLINE(SLDeviceVolume, GetVolume);
}

void DoCustomTrampoline_SLDynamicInterfaceManagementItf_RegisterCallback(
    HostCode /* callee */,
    ProcessState* /* state */) {
  LOG_ALWAYS_FATAL("not implemented: SLDynamicInterfaceManagementItf::RegisterCallback");
}

void RegisterSLDynamicInterfaceManagementItfMethods(SLDynamicInterfaceManagementItf itf) {
  REGISTER_TRAMPOLINE(SLDynamicInterfaceManagement, AddInterface);
  REGISTER_TRAMPOLINE(SLDynamicInterfaceManagement, RemoveInterface);
  REGISTER_TRAMPOLINE(SLDynamicInterfaceManagement, ResumeInterface);
  REGISTER_CUSTOM_TRAMPOLINE(SLDynamicInterfaceManagementItf, RegisterCallback);
}

void RegisterSLDynamicSourceItfMethods(SLDynamicSourceItf itf) {
  REGISTER_TRAMPOLINE(SLDynamicSource, SetSource);
}

void RegisterSLEffectSendItfMethods(SLEffectSendItf itf) {
  REGISTER_TRAMPOLINE(SLEffectSend, EnableEffectSend);
  REGISTER_TRAMPOLINE(SLEffectSend, IsEnabled);
  REGISTER_TRAMPOLINE(SLEffectSend, SetDirectLevel);
  REGISTER_TRAMPOLINE(SLEffectSend, GetDirectLevel);
  REGISTER_TRAMPOLINE(SLEffectSend, SetSendLevel);
  REGISTER_TRAMPOLINE(SLEffectSend, GetSendLevel);
}

void RegisterSLEngineItfMethods(SLEngineItf itf) {
  REGISTER_TRAMPOLINE(SLEngine, CreateLEDDevice);
  REGISTER_TRAMPOLINE(SLEngine, CreateVibraDevice);
  REGISTER_TRAMPOLINE(SLEngine, CreateAudioPlayer);
  REGISTER_TRAMPOLINE(SLEngine, CreateAudioRecorder);
  REGISTER_TRAMPOLINE(SLEngine, CreateMidiPlayer);
  REGISTER_TRAMPOLINE(SLEngine, CreateListener);
  REGISTER_TRAMPOLINE(SLEngine, Create3DGroup);
  REGISTER_TRAMPOLINE(SLEngine, CreateOutputMix);
  REGISTER_TRAMPOLINE(SLEngine, CreateMetadataExtractor);
  REGISTER_TRAMPOLINE(SLEngine, CreateExtensionObject);
  REGISTER_TRAMPOLINE(SLEngine, QueryNumSupportedInterfaces);
  REGISTER_TRAMPOLINE(SLEngine, QuerySupportedInterfaces);
  REGISTER_TRAMPOLINE(SLEngine, QueryNumSupportedExtensions);
  REGISTER_TRAMPOLINE(SLEngine, QuerySupportedExtension);
  REGISTER_TRAMPOLINE(SLEngine, IsExtensionSupported);
}

void RegisterSLEngineCapabilitiesItfMethods(SLEngineCapabilitiesItf itf) {
  REGISTER_TRAMPOLINE(SLEngineCapabilities, QuerySupportedProfiles);
  REGISTER_TRAMPOLINE(SLEngineCapabilities, QueryAvailableVoices);
  REGISTER_TRAMPOLINE(SLEngineCapabilities, QueryNumberOfMIDISynthesizers);
  REGISTER_TRAMPOLINE(SLEngineCapabilities, QueryAPIVersion);
  REGISTER_TRAMPOLINE(SLEngineCapabilities, QueryLEDCapabilities);
  REGISTER_TRAMPOLINE(SLEngineCapabilities, QueryVibraCapabilities);
  REGISTER_TRAMPOLINE(SLEngineCapabilities, IsThreadSafe);
}

void RegisterSLEnvironmentalReverbItfMethods(SLEnvironmentalReverbItf itf) {
  REGISTER_TRAMPOLINE(SLEnvironmentalReverb, SetRoomLevel);
  REGISTER_TRAMPOLINE(SLEnvironmentalReverb, GetRoomLevel);
  REGISTER_TRAMPOLINE(SLEnvironmentalReverb, SetRoomHFLevel);
  REGISTER_TRAMPOLINE(SLEnvironmentalReverb, GetRoomHFLevel);
  REGISTER_TRAMPOLINE(SLEnvironmentalReverb, SetDecayTime);
  REGISTER_TRAMPOLINE(SLEnvironmentalReverb, GetDecayTime);
  REGISTER_TRAMPOLINE(SLEnvironmentalReverb, SetDecayHFRatio);
  REGISTER_TRAMPOLINE(SLEnvironmentalReverb, GetDecayHFRatio);
  REGISTER_TRAMPOLINE(SLEnvironmentalReverb, SetReflectionsLevel);
  REGISTER_TRAMPOLINE(SLEnvironmentalReverb, GetReflectionsLevel);
  REGISTER_TRAMPOLINE(SLEnvironmentalReverb, SetReflectionsDelay);
  REGISTER_TRAMPOLINE(SLEnvironmentalReverb, GetReflectionsDelay);
  REGISTER_TRAMPOLINE(SLEnvironmentalReverb, SetReverbLevel);
  REGISTER_TRAMPOLINE(SLEnvironmentalReverb, GetReverbLevel);
  REGISTER_TRAMPOLINE(SLEnvironmentalReverb, SetReverbDelay);
  REGISTER_TRAMPOLINE(SLEnvironmentalReverb, GetReverbDelay);
  REGISTER_TRAMPOLINE(SLEnvironmentalReverb, SetDiffusion);
  REGISTER_TRAMPOLINE(SLEnvironmentalReverb, GetDiffusion);
  REGISTER_TRAMPOLINE(SLEnvironmentalReverb, SetDensity);
  REGISTER_TRAMPOLINE(SLEnvironmentalReverb, GetDensity);
  REGISTER_TRAMPOLINE(SLEnvironmentalReverb, SetEnvironmentalReverbProperties);
  REGISTER_TRAMPOLINE(SLEnvironmentalReverb, GetEnvironmentalReverbProperties);
}

void RegisterSLEqualizerItfMethods(SLEqualizerItf itf) {
  REGISTER_TRAMPOLINE(SLEqualizer, SetEnabled);
  REGISTER_TRAMPOLINE(SLEqualizer, IsEnabled);
  REGISTER_TRAMPOLINE(SLEqualizer, GetNumberOfBands);
  REGISTER_TRAMPOLINE(SLEqualizer, GetBandLevelRange);
  REGISTER_TRAMPOLINE(SLEqualizer, SetBandLevel);
  REGISTER_TRAMPOLINE(SLEqualizer, GetBandLevel);
  REGISTER_TRAMPOLINE(SLEqualizer, GetCenterFreq);
  REGISTER_TRAMPOLINE(SLEqualizer, GetBandFreqRange);
  REGISTER_TRAMPOLINE(SLEqualizer, GetBand);
  REGISTER_TRAMPOLINE(SLEqualizer, GetCurrentPreset);
  REGISTER_TRAMPOLINE(SLEqualizer, UsePreset);
  REGISTER_TRAMPOLINE(SLEqualizer, GetNumberOfPresets);
  REGISTER_TRAMPOLINE(SLEqualizer, GetPresetName);
}

void RegisterSLLEDArrayItfMethods(SLLEDArrayItf itf) {
  REGISTER_TRAMPOLINE(SLLEDArray, ActivateLEDArray);
  REGISTER_TRAMPOLINE(SLLEDArray, IsLEDArrayActivated);
  REGISTER_TRAMPOLINE(SLLEDArray, SetColor);
  REGISTER_TRAMPOLINE(SLLEDArray, GetColor);
}

void DoCustomTrampoline_SLMIDIMessageItf_RegisterMetaEventCallback(HostCode /* callee */,
                                                                   ProcessState* /* state */) {
  LOG_ALWAYS_FATAL("not implemented: SLMIDIMessageItf::RegisterMetaEventCallback");
}

void DoCustomTrampoline_SLMIDIMessageItf_RegisterMIDIMessageCallback(HostCode /* callee */,
                                                                     ProcessState* /* state */) {
  LOG_ALWAYS_FATAL("not implemented: SLMIDIMessageItf::RegisterMIDIMessageCallback");
}

void RegisterSLMIDIMessageItfMethods(SLMIDIMessageItf itf) {
  REGISTER_TRAMPOLINE(SLMIDIMessage, SendMessage);
  REGISTER_CUSTOM_TRAMPOLINE(SLMIDIMessageItf, RegisterMetaEventCallback);
  REGISTER_CUSTOM_TRAMPOLINE(SLMIDIMessageItf, RegisterMIDIMessageCallback);
  REGISTER_TRAMPOLINE(SLMIDIMessage, AddMIDIMessageCallbackFilter);
  REGISTER_TRAMPOLINE(SLMIDIMessage, ClearMIDIMessageCallbackFilter);
}

void RegisterSLMIDIMuteSoloItfMethods(SLMIDIMuteSoloItf itf) {
  REGISTER_TRAMPOLINE(SLMIDIMuteSolo, SetChannelMute);
  REGISTER_TRAMPOLINE(SLMIDIMuteSolo, GetChannelMute);
  REGISTER_TRAMPOLINE(SLMIDIMuteSolo, SetChannelSolo);
  REGISTER_TRAMPOLINE(SLMIDIMuteSolo, GetChannelSolo);
  REGISTER_TRAMPOLINE(SLMIDIMuteSolo, GetTrackCount);
  REGISTER_TRAMPOLINE(SLMIDIMuteSolo, SetTrackMute);
  REGISTER_TRAMPOLINE(SLMIDIMuteSolo, GetTrackMute);
  REGISTER_TRAMPOLINE(SLMIDIMuteSolo, SetTrackSolo);
  REGISTER_TRAMPOLINE(SLMIDIMuteSolo, GetTrackSolo);
}

void RegisterSLMIDITempoItfMethods(SLMIDITempoItf itf) {
  REGISTER_TRAMPOLINE(SLMIDITempo, SetTicksPerQuarterNote);
  REGISTER_TRAMPOLINE(SLMIDITempo, GetTicksPerQuarterNote);
  REGISTER_TRAMPOLINE(SLMIDITempo, SetMicrosecondsPerQuarterNote);
  REGISTER_TRAMPOLINE(SLMIDITempo, GetMicrosecondsPerQuarterNote);
}

void RegisterSLMIDITimeItfMethods(SLMIDITimeItf itf) {
  REGISTER_TRAMPOLINE(SLMIDITime, GetDuration);
  REGISTER_TRAMPOLINE(SLMIDITime, SetPosition);
  REGISTER_TRAMPOLINE(SLMIDITime, GetPosition);
  REGISTER_TRAMPOLINE(SLMIDITime, SetLoopPoints);
  REGISTER_TRAMPOLINE(SLMIDITime, GetLoopPoints);
}

void RegisterSLMetadataExtractionItfMethods(SLMetadataExtractionItf itf) {
  REGISTER_TRAMPOLINE(SLMetadataExtraction, GetItemCount);
  REGISTER_TRAMPOLINE(SLMetadataExtraction, GetKeySize);
  REGISTER_TRAMPOLINE(SLMetadataExtraction, GetKey);
  REGISTER_TRAMPOLINE(SLMetadataExtraction, GetValueSize);
  REGISTER_TRAMPOLINE(SLMetadataExtraction, GetValue);
  REGISTER_TRAMPOLINE(SLMetadataExtraction, AddKeyFilter);
  REGISTER_TRAMPOLINE(SLMetadataExtraction, ClearKeyFilter);
}

void RegisterSLMetadataTraversalItfMethods(SLMetadataTraversalItf itf) {
  REGISTER_TRAMPOLINE(SLMetadataTraversal, SetMode);
  REGISTER_TRAMPOLINE(SLMetadataTraversal, GetChildCount);
  REGISTER_TRAMPOLINE(SLMetadataTraversal, GetChildMIMETypeSize);
  REGISTER_TRAMPOLINE(SLMetadataTraversal, GetChildInfo);
  REGISTER_TRAMPOLINE(SLMetadataTraversal, SetActiveNode);
}

void RegisterSLMuteSoloItfMethods(SLMuteSoloItf itf) {
  REGISTER_TRAMPOLINE(SLMuteSolo, SetChannelMute);
  REGISTER_TRAMPOLINE(SLMuteSolo, GetChannelMute);
  REGISTER_TRAMPOLINE(SLMuteSolo, SetChannelSolo);
  REGISTER_TRAMPOLINE(SLMuteSolo, GetChannelSolo);
  REGISTER_TRAMPOLINE(SLMuteSolo, GetNumChannels);
}

void RegisterMethodsById(SLInterfaceID id, void* itf);

void DoCustomTrampoline_SLObjectItf_GetInterface(HostCode callee, ProcessState* state) {
  using PFN_callee = decltype(std::declval<SLObjectItf_>().GetInterface);
  PFN_callee callee_function = AsFuncPtr(callee);
  auto [itf, id, pInterface] = GuestParamsValues<PFN_callee>(state);
  auto&& [ret] = GuestReturnReference<PFN_callee>(state);
  ret = callee_function(itf, id, pInterface);
  if (ret != SL_RESULT_SUCCESS) {
    return;
  }
  RegisterMethodsById(id, pInterface);
}

void DoCustomTrampoline_SLObjectItf_RegisterCallback(HostCode callee, ProcessState* state) {
  using PFN_callee = decltype(std::declval<SLObjectItf_>().RegisterCallback);
  PFN_callee callee_function = AsFuncPtr(callee);
  auto [play, guest_callback, context] = GuestParamsValues<PFN_callee>(state);
  slObjectCallback host_callback =
      WrapGuestFunction(guest_callback, "SLObjectItf_RegisterCallback-callback");
  auto&& [ret] = GuestReturnReference<PFN_callee>(state);
  ret = callee_function(play, host_callback, context);
}

void RegisterSLObjectItfMethods(SLObjectItf itf) {
  REGISTER_TRAMPOLINE(SLObject, Realize);
  REGISTER_TRAMPOLINE(SLObject, Resume);
  REGISTER_TRAMPOLINE(SLObject, GetState);
  REGISTER_CUSTOM_TRAMPOLINE(SLObjectItf, GetInterface);
  REGISTER_CUSTOM_TRAMPOLINE(SLObjectItf, RegisterCallback);
  REGISTER_TRAMPOLINE(SLObject, AbortAsyncOperation);
  REGISTER_TRAMPOLINE(SLObject, Destroy);
  REGISTER_TRAMPOLINE(SLObject, SetPriority);
  REGISTER_TRAMPOLINE(SLObject, GetPriority);
  REGISTER_TRAMPOLINE(SLObject, SetLossOfControlInterfaces);
}

void DoCustomTrampoline_SLOutputMixItf_RegisterDeviceChangeCallback(HostCode /* callee */,
                                                                    ProcessState* /* state */) {
  LOG_ALWAYS_FATAL("not implemented: SLOutputMixItf::RegisterDeviceChangeCallback");
}

void RegisterSLOutputMixItfMethods(SLOutputMixItf itf) {
  REGISTER_TRAMPOLINE(SLOutputMix, GetDestinationOutputDeviceIDs);
  REGISTER_CUSTOM_TRAMPOLINE(SLOutputMixItf, RegisterDeviceChangeCallback);
  REGISTER_TRAMPOLINE(SLOutputMix, ReRoute);
}

void RegisterSLPitchItfMethods(SLPitchItf itf) {
  REGISTER_TRAMPOLINE(SLPitch, SetPitch);
  REGISTER_TRAMPOLINE(SLPitch, GetPitch);
  REGISTER_TRAMPOLINE(SLPitch, GetPitchCapabilities);
}

void DoCustomTrampoline_SLPlayItf_RegisterCallback(HostCode callee, ProcessState* state) {
  using PFN_callee = decltype(std::declval<SLPlayItf_>().RegisterCallback);
  PFN_callee callee_function = AsFuncPtr(callee);
  auto [play, guest_callback, context] = GuestParamsValues<PFN_callee>(state);
  slPlayCallback host_callback =
      WrapGuestFunction(guest_callback, "SLPlayItf_RegisterCallback-callback");
  auto&& [ret] = GuestReturnReference<PFN_callee>(state);
  ret = callee_function(play, host_callback, context);
}

void RegisterSLPlayItfMethods(SLPlayItf itf) {
  REGISTER_TRAMPOLINE(SLPlay, SetPlayState);
  REGISTER_TRAMPOLINE(SLPlay, GetPlayState);
  REGISTER_TRAMPOLINE(SLPlay, GetDuration);
  REGISTER_TRAMPOLINE(SLPlay, GetPosition);
  REGISTER_CUSTOM_TRAMPOLINE(SLPlayItf, RegisterCallback);
  REGISTER_TRAMPOLINE(SLPlay, SetCallbackEventsMask);
  REGISTER_TRAMPOLINE(SLPlay, GetCallbackEventsMask);
  REGISTER_TRAMPOLINE(SLPlay, SetMarkerPosition);
  REGISTER_TRAMPOLINE(SLPlay, ClearMarkerPosition);
  REGISTER_TRAMPOLINE(SLPlay, GetMarkerPosition);
  REGISTER_TRAMPOLINE(SLPlay, SetPositionUpdatePeriod);
  REGISTER_TRAMPOLINE(SLPlay, GetPositionUpdatePeriod);
}

void RegisterSLPlaybackRateItfMethods(SLPlaybackRateItf itf) {
  REGISTER_TRAMPOLINE(SLPlaybackRate, SetRate);
  REGISTER_TRAMPOLINE(SLPlaybackRate, GetRate);
  REGISTER_TRAMPOLINE(SLPlaybackRate, SetPropertyConstraints);
  REGISTER_TRAMPOLINE(SLPlaybackRate, GetProperties);
  REGISTER_TRAMPOLINE(SLPlaybackRate, GetCapabilitiesOfRate);
  REGISTER_TRAMPOLINE(SLPlaybackRate, GetRateRange);
}

void DoCustomTrampoline_SLPrefetchStatusItf_RegisterCallback(HostCode callee, ProcessState* state) {
  using PFN_callee = decltype(std::declval<SLPrefetchStatusItf_>().RegisterCallback);
  PFN_callee callee_function = AsFuncPtr(callee);
  auto [self, guest_callback, context] = GuestParamsValues<PFN_callee>(state);
  slPrefetchCallback host_callback =
      WrapGuestFunction(guest_callback, "SLPrefetchStatusItf_RegisterCallback-callback");
  auto&& [ret] = GuestReturnReference<PFN_callee>(state);
  ret = callee_function(self, host_callback, context);
}

void RegisterSLPrefetchStatusItfMethods(SLPrefetchStatusItf itf) {
  REGISTER_TRAMPOLINE(SLPrefetchStatus, GetPrefetchStatus);
  REGISTER_TRAMPOLINE(SLPrefetchStatus, GetFillLevel);
  REGISTER_CUSTOM_TRAMPOLINE(SLPrefetchStatusItf, RegisterCallback);
  REGISTER_TRAMPOLINE(SLPrefetchStatus, SetCallbackEventsMask);
  REGISTER_TRAMPOLINE(SLPrefetchStatus, GetCallbackEventsMask);
  REGISTER_TRAMPOLINE(SLPrefetchStatus, SetFillUpdatePeriod);
  REGISTER_TRAMPOLINE(SLPrefetchStatus, GetFillUpdatePeriod);
}

void RegisterSLPresetReverbItfMethods(SLPresetReverbItf itf) {
  REGISTER_TRAMPOLINE(SLPresetReverb, SetPreset);
  REGISTER_TRAMPOLINE(SLPresetReverb, GetPreset);
}

void RegisterSLRatePitchItfMethods(SLRatePitchItf itf) {
  REGISTER_TRAMPOLINE(SLRatePitch, SetRate);
  REGISTER_TRAMPOLINE(SLRatePitch, GetRate);
  REGISTER_TRAMPOLINE(SLRatePitch, GetRatePitchCapabilities);
}

void DoCustomTrampoline_SLRecordItf_RegisterCallback(HostCode callee, ProcessState* state) {
  using PFN_callee = decltype(std::declval<SLRecordItf_>().RegisterCallback);
  PFN_callee callee_function = AsFuncPtr(callee);
  auto [record, guest_callback, context] = GuestParamsValues<PFN_callee>(state);
  slRecordCallback host_callback =
      WrapGuestFunction(guest_callback, "SLRecordItf_RegisterCallback-callback");
  auto&& [ret] = GuestReturnReference<PFN_callee>(state);
  ret = callee_function(record, host_callback, context);
}

void RegisterSLRecordItfMethods(SLRecordItf itf) {
  REGISTER_TRAMPOLINE(SLRecord, SetRecordState);
  REGISTER_TRAMPOLINE(SLRecord, GetRecordState);
  REGISTER_TRAMPOLINE(SLRecord, SetDurationLimit);
  REGISTER_TRAMPOLINE(SLRecord, GetPosition);
  REGISTER_CUSTOM_TRAMPOLINE(SLRecordItf, RegisterCallback);
  REGISTER_TRAMPOLINE(SLRecord, SetCallbackEventsMask);
  REGISTER_TRAMPOLINE(SLRecord, GetCallbackEventsMask);
  REGISTER_TRAMPOLINE(SLRecord, SetMarkerPosition);
  REGISTER_TRAMPOLINE(SLRecord, ClearMarkerPosition);
  REGISTER_TRAMPOLINE(SLRecord, GetMarkerPosition);
  REGISTER_TRAMPOLINE(SLRecord, SetPositionUpdatePeriod);
  REGISTER_TRAMPOLINE(SLRecord, GetPositionUpdatePeriod);
}

void RegisterSLSeekItfMethods(SLSeekItf itf) {
  REGISTER_TRAMPOLINE(SLSeek, SetPosition);
  REGISTER_TRAMPOLINE(SLSeek, SetLoop);
  REGISTER_TRAMPOLINE(SLSeek, GetLoop);
}

void RegisterSLThreadSyncItfMethods(SLThreadSyncItf itf) {
  REGISTER_TRAMPOLINE(SLThreadSync, EnterCriticalSection);
  REGISTER_TRAMPOLINE(SLThreadSync, ExitCriticalSection);
}

void RegisterSLVibraItfMethods(SLVibraItf itf) {
  REGISTER_TRAMPOLINE(SLVibra, Vibrate);
  REGISTER_TRAMPOLINE(SLVibra, IsVibrating);
  REGISTER_TRAMPOLINE(SLVibra, SetFrequency);
  REGISTER_TRAMPOLINE(SLVibra, GetFrequency);
  REGISTER_TRAMPOLINE(SLVibra, SetIntensity);
  REGISTER_TRAMPOLINE(SLVibra, GetIntensity);
}

void RegisterSLVirtualizerItfMethods(SLVirtualizerItf itf) {
  REGISTER_TRAMPOLINE(SLVirtualizer, SetEnabled);
  REGISTER_TRAMPOLINE(SLVirtualizer, IsEnabled);
  REGISTER_TRAMPOLINE(SLVirtualizer, SetStrength);
  REGISTER_TRAMPOLINE(SLVirtualizer, GetRoundedStrength);
  REGISTER_TRAMPOLINE(SLVirtualizer, IsStrengthSupported);
}

void DoCustomTrampoline_SLVisualizationItf_RegisterVisualizationCallback(
    HostCode /* callee */,
    ProcessState* /* state */) {
  LOG_ALWAYS_FATAL("not implemented: SLVisualizationItf::RegisterVisualizationCallback");
}

void RegisterSLVisualizationItfMethods(SLVisualizationItf itf) {
  REGISTER_CUSTOM_TRAMPOLINE(SLVisualizationItf, RegisterVisualizationCallback);
  REGISTER_TRAMPOLINE(SLVisualization, GetMaxRate);
}

void RegisterSLVolumeItfMethods(SLVolumeItf itf) {
  REGISTER_TRAMPOLINE(SLVolume, SetVolumeLevel);
  REGISTER_TRAMPOLINE(SLVolume, GetVolumeLevel);
  REGISTER_TRAMPOLINE(SLVolume, GetMaxVolumeLevel);
  REGISTER_TRAMPOLINE(SLVolume, SetMute);
  REGISTER_TRAMPOLINE(SLVolume, GetMute);
  REGISTER_TRAMPOLINE(SLVolume, EnableStereoPosition);
  REGISTER_TRAMPOLINE(SLVolume, IsEnabledStereoPosition);
  REGISTER_TRAMPOLINE(SLVolume, SetStereoPosition);
  REGISTER_TRAMPOLINE(SLVolume, GetStereoPosition);
}

#undef REGISTER_CUSTOM_TRAMPOLINE

bool isSLID(SLInterfaceID id, SLInterfaceID id2) {
  return memcmp(id, id2, sizeof(*id)) == 0;
}

const char* GetIDName(const SLInterfaceID& id);

void RegisterMethodsById(SLInterfaceID id, void* itf) {
  if (isSLID(id, SL_IID_3DCOMMIT)) {
    LOG_SLES("SL_IID_3DCOMMIT");
    RegisterSL3DCommitItfMethods(*static_cast<SL3DCommitItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_3DDOPPLER)) {
    LOG_SLES("SL_IID_3DDOPPLER");
    RegisterSL3DDopplerItfMethods(*static_cast<SL3DDopplerItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_3DGROUPING)) {
    LOG_SLES("SL_IID_3DGROUPING");
    RegisterSL3DGroupingItfMethods(*static_cast<SL3DGroupingItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_3DLOCATION)) {
    LOG_SLES("SL_IID_3DLOCATION");
    RegisterSL3DLocationItfMethods(*static_cast<SL3DLocationItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_3DMACROSCOPIC)) {
    LOG_SLES("SL_IID_3DMACROSCOPIC");
    RegisterSL3DMacroscopicItfMethods(*static_cast<SL3DMacroscopicItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_3DSOURCE)) {
    LOG_SLES("SL_IID_3DSOURCE");
    RegisterSL3DSourceItfMethods(*static_cast<SL3DSourceItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_ANDROIDACOUSTICECHOCANCELLATION)) {
    LOG_SLES("SL_IID_ANDROIDACOUSTICECHOCANCELLATION");
    RegisterSLAndroidAcousticEchoCancellationItfMethods(
        *static_cast<SLAndroidAcousticEchoCancellationItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_ANDROIDAUTOMATICGAINCONTROL)) {
    LOG_SLES("SL_IID_ANDROIDAUTOMATICGAINCONTROL");
    RegisterSLAndroidAutomaticGainControlItfMethods(
        *static_cast<SLAndroidAutomaticGainControlItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_ANDROIDBUFFERQUEUESOURCE)) {
    LOG_SLES("SL_IID_ANDROIDBUFFERQUEUESOURCE");
    RegisterSLAndroidBufferQueueItfMethods(*static_cast<SLAndroidBufferQueueItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_ANDROIDCONFIGURATION)) {
    LOG_SLES("SL_IID_ANDROIDCONFIGURATION");
    RegisterSLAndroidConfigurationItfMethods(*static_cast<SLAndroidConfigurationItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_ANDROIDEFFECT)) {
    LOG_SLES("SL_IID_ANDROIDEFFECT");
    RegisterSLAndroidEffectItfMethods(*static_cast<SLAndroidEffectItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_ANDROIDEFFECTCAPABILITIES)) {
    LOG_SLES("SL_IID_ANDROIDEFFECTCAPABILITIES");
    RegisterSLAndroidEffectCapabilitiesItfMethods(
        *static_cast<SLAndroidEffectCapabilitiesItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_ANDROIDEFFECTSEND)) {
    LOG_SLES("SL_IID_ANDROIDEFFECTSEND");
    RegisterSLAndroidEffectSendItfMethods(*static_cast<SLAndroidEffectSendItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_ANDROIDNOISESUPPRESSION)) {
    LOG_SLES("SL_IID_ANDROIDNOISESUPPRESSION");
    RegisterSLAndroidNoiseSuppressionItfMethods(*static_cast<SLAndroidNoiseSuppressionItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_ANDROIDSIMPLEBUFFERQUEUE)) {
    LOG_SLES("SL_IID_ANDROIDSIMPLEBUFFERQUEUE");
    RegisterSLAndroidSimpleBufferQueueItfMethods(*static_cast<SLAndroidSimpleBufferQueueItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_AUDIODECODERCAPABILITIES)) {
    LOG_SLES("SL_IID_AUDIODECODERCAPABILITIES");
    RegisterSLAudioDecoderCapabilitiesItfMethods(*static_cast<SLAudioDecoderCapabilitiesItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_AUDIOENCODER)) {
    LOG_SLES("SL_IID_AUDIOENCODER");
    RegisterSLAudioEncoderItfMethods(*static_cast<SLAudioEncoderItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_AUDIOENCODERCAPABILITIES)) {
    LOG_SLES("SL_IID_AUDIOENCODERCAPABILITIES");
    RegisterSLAudioEncoderCapabilitiesItfMethods(*static_cast<SLAudioEncoderCapabilitiesItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_AUDIOIODEVICECAPABILITIES)) {
    LOG_SLES("SL_IID_AUDIOIODEVICECAPABILITIES");
    RegisterSLAudioIODeviceCapabilitiesItfMethods(
        *static_cast<SLAudioIODeviceCapabilitiesItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_BASSBOOST)) {
    LOG_SLES("SL_IID_BASSBOOST");
    RegisterSLBassBoostItfMethods(*static_cast<SLBassBoostItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_BUFFERQUEUE)) {
    LOG_SLES("SL_IID_BUFFERQUEUE");
    RegisterSLBufferQueueItfMethods(*static_cast<SLBufferQueueItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_DEVICEVOLUME)) {
    LOG_SLES("SL_IID_DEVICEVOLUME");
    RegisterSLDeviceVolumeItfMethods(*static_cast<SLDeviceVolumeItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_DYNAMICINTERFACEMANAGEMENT)) {
    LOG_SLES("SL_IID_DYNAMICINTERFACEMANAGEMENT");
    RegisterSLDynamicInterfaceManagementItfMethods(
        *static_cast<SLDynamicInterfaceManagementItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_DYNAMICSOURCE)) {
    LOG_SLES("SL_IID_DYNAMICSOURCE");
    RegisterSLDynamicSourceItfMethods(*static_cast<SLDynamicSourceItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_EFFECTSEND)) {
    LOG_SLES("SL_IID_EFFECTSEND");
    RegisterSLEffectSendItfMethods(*static_cast<SLEffectSendItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_ENGINE)) {
    LOG_SLES("SL_IID_ENGINE");
    RegisterSLEngineItfMethods(*static_cast<SLEngineItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_ENGINECAPABILITIES)) {
    LOG_SLES("SL_IID_ENGINECAPABILITIES");
    RegisterSLEngineCapabilitiesItfMethods(*static_cast<SLEngineCapabilitiesItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_ENVIRONMENTALREVERB)) {
    LOG_SLES("SL_IID_ENVIRONMENTALREVERB");
    RegisterSLEnvironmentalReverbItfMethods(*static_cast<SLEnvironmentalReverbItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_EQUALIZER)) {
    LOG_SLES("SL_IID_EQUALIZER");
    RegisterSLEqualizerItfMethods(*static_cast<SLEqualizerItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_LED)) {
    LOG_SLES("SL_IID_LED");
    RegisterSLLEDArrayItfMethods(*static_cast<SLLEDArrayItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_METADATAEXTRACTION)) {
    LOG_SLES("SL_IID_METADATAEXTRACTION");
    RegisterSLMetadataExtractionItfMethods(*static_cast<SLMetadataExtractionItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_METADATATRAVERSAL)) {
    LOG_SLES("SL_IID_METADATATRAVERSAL");
    RegisterSLMetadataTraversalItfMethods(*static_cast<SLMetadataTraversalItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_MIDIMESSAGE)) {
    LOG_SLES("SL_IID_MIDIMESSAGE");
    RegisterSLMIDIMessageItfMethods(*static_cast<SLMIDIMessageItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_MIDIMUTESOLO)) {
    LOG_SLES("SL_IID_MIDIMUTESOLO");
    RegisterSLMIDIMuteSoloItfMethods(*static_cast<SLMIDIMuteSoloItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_MIDITEMPO)) {
    LOG_SLES("SL_IID_MIDITEMPO");
    RegisterSLMIDITempoItfMethods(*static_cast<SLMIDITempoItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_MIDITIME)) {
    LOG_SLES("SL_IID_MIDITIME");
    RegisterSLMIDITimeItfMethods(*static_cast<SLMIDITimeItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_MUTESOLO)) {
    LOG_SLES("SL_IID_MUTESOLO");
    RegisterSLMuteSoloItfMethods(*static_cast<SLMuteSoloItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_OBJECT)) {
    LOG_SLES("SL_IID_OBJECT");
    RegisterSLObjectItfMethods(*static_cast<SLObjectItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_OUTPUTMIX)) {
    LOG_SLES("SL_IID_OUTPUTMIX");
    RegisterSLOutputMixItfMethods(*static_cast<SLOutputMixItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_PITCH)) {
    LOG_SLES("SL_IID_PITCH");
    RegisterSLPitchItfMethods(*static_cast<SLPitchItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_PLAY)) {
    LOG_SLES("SL_IID_PLAY");
    RegisterSLPlayItfMethods(*static_cast<SLPlayItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_PLAYBACKRATE)) {
    LOG_SLES("SL_IID_PLAYBACKRATE");
    RegisterSLPlaybackRateItfMethods(*static_cast<SLPlaybackRateItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_PREFETCHSTATUS)) {
    LOG_SLES("SL_IID_PREFETCHSTATUS");
    RegisterSLPrefetchStatusItfMethods(*static_cast<SLPrefetchStatusItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_PRESETREVERB)) {
    LOG_SLES("SL_IID_PRESETREVERB");
    RegisterSLPresetReverbItfMethods(*static_cast<SLPresetReverbItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_RATEPITCH)) {
    LOG_SLES("SL_IID_RATEPITCH");
    RegisterSLRatePitchItfMethods(*static_cast<SLRatePitchItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_RECORD)) {
    LOG_SLES("SL_IID_RECORD");
    RegisterSLRecordItfMethods(*static_cast<SLRecordItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_SEEK)) {
    LOG_SLES("SL_IID_SEEK");
    RegisterSLSeekItfMethods(*static_cast<SLSeekItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_THREADSYNC)) {
    LOG_SLES("SL_IID_THREADSYNC");
    RegisterSLThreadSyncItfMethods(*static_cast<SLThreadSyncItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_VIBRA)) {
    LOG_SLES("SL_IID_VIBRA");
    RegisterSLVibraItfMethods(*static_cast<SLVibraItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_VIRTUALIZER)) {
    LOG_SLES("SL_IID_VIRTUALIZER");
    RegisterSLVirtualizerItfMethods(*static_cast<SLVirtualizerItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_VISUALIZATION)) {
    LOG_SLES("SL_IID_VISUALIZATION");
    RegisterSLVisualizationItfMethods(*static_cast<SLVisualizationItf*>(itf));
    return;
  }
  if (isSLID(id, SL_IID_VOLUME)) {
    LOG_SLES("SL_IID_VOLUME");
    RegisterSLVolumeItfMethods(*static_cast<SLVolumeItf*>(itf));
    return;
  }
  LOG_ALWAYS_FATAL("Unknown id=%s", GetIDName(id));
}

void DoCustomTrampoline_slCreateEngine(HostCode /* callee */, ProcessState* state) {
  auto [pEngine, numOptions, pEngineOptions, numInterfaces, pInterfaceIds, pInterfaceRequired] =
      GuestParamsValues<decltype(slCreateEngine)>(state);
  // Attention: we cannot write directly to ret here since we use pEngine param below.
  auto&& [ret] = GuestReturnReference<decltype(slCreateEngine)>(state);
  ret = slCreateEngine(
      pEngine, numOptions, pEngineOptions, numInterfaces, pInterfaceIds, pInterfaceRequired);
  if (ret == SL_RESULT_SUCCESS) {
    RegisterSLObjectItfMethods(*pEngine);
  }
}

#if defined(NATIVE_BRIDGE_GUEST_ARCH_ARM) && defined(__i386__)

#include "trampolines_arm_to_x86-inl.h"  // generated file NOLINT [build/include]

#elif defined(NATIVE_BRIDGE_GUEST_ARCH_ARM64) && defined(__x86_64__)

#include "trampolines_arm64_to_x86_64-inl.h"  // generated file NOLINT [build/include]

#elif defined(NATIVE_BRIDGE_GUEST_ARCH_RISCV64) && defined(__x86_64__)

#include "trampolines_riscv64_to_x86_64-inl.h"  // generated file NOLINT [build/include]

#else

#error "Unknown guest/host arch combination"

#endif

// This function is used to log interfaces which we haven't wrapped yet.
// Interface names are prefixed with SL_IID_.
const char* GetIDName(const SLInterfaceID& id) {
  for (size_t i = 0; i < std::size(kKnownVariables); i++) {
    const char* name = kKnownVariables[i].name;
    if (strncmp(name, "SL_IID_", 7) == 0) {
      SLInterfaceID interface_id = *reinterpret_cast<SLInterfaceID*>(dlsym(RTLD_DEFAULT, name));
      if (isSLID(id, interface_id)) {
        return name;
      }
    }
  }
  return "Unknown";
}

DEFINE_INIT_PROXY_LIBRARY("libOpenSLES.so")

}  // namespace

}  // namespace berberis
