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

#include "OMXAL/OpenMAXAL.h"
#include "OMXAL/OpenMAXAL_Android.h"

#include "berberis/base/struct_check.h"
#include "berberis/guest_abi/function_wrappers.h"
#include "berberis/guest_abi/guest_params.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/proxy_loader/proxy_library_builder.h"

namespace berberis {

namespace {

// TODO(b/312279687): Maybe share this with SLES translation.
#define REGISTER_TRAMPOLINE(itf_name, func_name) \
  WrapHostFunction((*itf)->func_name, #itf_name "::" #func_name)

#define REGISTER_CUSTOM_TRAMPOLINE(itf_name, func_name)             \
  WrapHostFunctionImpl(reinterpret_cast<void*>((*itf)->func_name),  \
                       DoCustomTrampoline_##itf_name##_##func_name, \
                       #itf_name "::" #func_name)

// Interfaces are just structures listing function pointers, thus are layout-compatible.
typedef XAObjectItf Guest_XAObjectItf;
typedef XAEngineItf Guest_XAEngineItf;
typedef XAPlayItf Guest_XAPlayItf;
typedef XAAndroidBufferQueueItf Guest_XAAndroidBufferQueueItf;
typedef XAStreamInformationItf Guest_XAStreamInformationItf;
typedef XAVolumeItf Guest_XAVolumeItf;

CHECK_STRUCT_LAYOUT(XAEngineOption, 64, 32);
CHECK_FIELD_LAYOUT(XAEngineOption, feature, 0, 32);
CHECK_FIELD_LAYOUT(XAEngineOption, data, 32, 32);
typedef XAEngineOption Guest_XAEngineOption;

// Note, that this is not an integer, but is a pointer to structure!
typedef std::remove_reference_t<decltype(*std::declval<XAInterfaceID>())> XAInterfaceID_deref;
CHECK_STRUCT_LAYOUT(XAInterfaceID_deref, 128, 32);
CHECK_FIELD_LAYOUT(XAInterfaceID_deref, time_low, 0, 32);
CHECK_FIELD_LAYOUT(XAInterfaceID_deref, time_mid, 32, 16);
CHECK_FIELD_LAYOUT(XAInterfaceID_deref, time_hi_and_version, 48, 16);
CHECK_FIELD_LAYOUT(XAInterfaceID_deref, clock_seq, 64, 16);
CHECK_FIELD_LAYOUT(XAInterfaceID_deref, node, 80, 48);
typedef XAInterfaceID Guest_XAInterfaceID;

#if defined(NATIVE_BRIDGE_GUEST_ARCH_ARM)

CHECK_STRUCT_LAYOUT(XADataSource, 64, 32);
CHECK_FIELD_LAYOUT(XADataSource, pLocator, 0, 32);
CHECK_FIELD_LAYOUT(XADataSource, pFormat, 32, 32);

CHECK_STRUCT_LAYOUT(XADataSink, 64, 32);
CHECK_FIELD_LAYOUT(XADataSink, pLocator, 0, 32);
CHECK_FIELD_LAYOUT(XADataSink, pFormat, 32, 32);

#elif defined(NATIVE_BRIDGE_GUEST_ARCH_ARM64)

CHECK_STRUCT_LAYOUT(XADataSource, 128, 64);
CHECK_FIELD_LAYOUT(XADataSource, pLocator, 0, 64);
CHECK_FIELD_LAYOUT(XADataSource, pFormat, 64, 64);

CHECK_STRUCT_LAYOUT(XADataSink, 128, 64);
CHECK_FIELD_LAYOUT(XADataSink, pLocator, 0, 64);
CHECK_FIELD_LAYOUT(XADataSink, pFormat, 64, 64);

#elif defined(NATIVE_BRIDGE_GUEST_ARCH_RISCV64)

CHECK_STRUCT_LAYOUT(XADataSource, 128, 64);
CHECK_FIELD_LAYOUT(XADataSource, pLocator, 0, 64);
CHECK_FIELD_LAYOUT(XADataSource, pFormat, 64, 64);

CHECK_STRUCT_LAYOUT(XADataSink, 128, 64);
CHECK_FIELD_LAYOUT(XADataSink, pLocator, 0, 64);
CHECK_FIELD_LAYOUT(XADataSink, pFormat, 64, 64);

#else

#error "Unknown guest arch"

#endif

CHECK_STRUCT_LAYOUT(XALEDDescriptor, 64, 32);
CHECK_FIELD_LAYOUT(XALEDDescriptor, ledCount, 0, 8);
CHECK_FIELD_LAYOUT(XALEDDescriptor, primaryLED, 8, 8);
CHECK_FIELD_LAYOUT(XALEDDescriptor, colorMask, 32, 32);

CHECK_STRUCT_LAYOUT(XAVibraDescriptor, 128, 32);
CHECK_FIELD_LAYOUT(XAVibraDescriptor, supportsFrequency, 0, 32);
CHECK_FIELD_LAYOUT(XAVibraDescriptor, supportsIntensity, 32, 32);
CHECK_FIELD_LAYOUT(XAVibraDescriptor, minFrequency, 64, 32);
CHECK_FIELD_LAYOUT(XAVibraDescriptor, maxFrequency, 96, 32);

void RegisterXAEngineItfMethods(Guest_XAEngineItf itf) {
  REGISTER_TRAMPOLINE(XAEngine, CreateOutputMix);
  REGISTER_TRAMPOLINE(XAEngine, CreateMediaPlayer);
  REGISTER_TRAMPOLINE(XAEngine, CreateMediaRecorder);
  REGISTER_TRAMPOLINE(XAEngine, CreateCameraDevice);
  REGISTER_TRAMPOLINE(XAEngine, CreateRadioDevice);
  REGISTER_TRAMPOLINE(XAEngine, CreateLEDDevice);
  REGISTER_TRAMPOLINE(XAEngine, CreateVibraDevice);
  REGISTER_TRAMPOLINE(XAEngine, CreateMetadataExtractor);
  REGISTER_TRAMPOLINE(XAEngine, CreateExtensionObject);
  REGISTER_TRAMPOLINE(XAEngine, GetImplementationInfo);
  REGISTER_TRAMPOLINE(XAEngine, QuerySupportedProfiles);
  REGISTER_TRAMPOLINE(XAEngine, QueryNumSupportedInterfaces);
  REGISTER_TRAMPOLINE(XAEngine, QuerySupportedInterfaces);
  REGISTER_TRAMPOLINE(XAEngine, QueryNumSupportedExtensions);
  REGISTER_TRAMPOLINE(XAEngine, QuerySupportedExtension);
  REGISTER_TRAMPOLINE(XAEngine, IsExtensionSupported);
  REGISTER_TRAMPOLINE(XAEngine, QueryLEDCapabilities);
  REGISTER_TRAMPOLINE(XAEngine, QueryVibraCapabilities);
}

// XAresult (*RegisterCallback) (XAPlayItf self, xaPlayCallback callback, void * pContext);
void DoCustomTrampoline_XAPlay_RegisterCallback(HostCode /*callee*/, ProcessState* state) {
  using PFN_callee = decltype(std::declval<XAPlayItf_>().RegisterCallback);
  auto [self, guest_callback, callback_context] = GuestParamsValues<PFN_callee>(state);

  // typedef void (XAAPIENTRY * xaPlayCallback) (XAPlayItf caller, void * pContext, XAuint32 event);
  auto host_callback = WrapGuestFunction(guest_callback, "XAPlay_RegisterCallback-callback");

  auto&& [ret] = GuestReturnReference<PFN_callee>(state);
  ret = (*self)->RegisterCallback(self, host_callback, callback_context);
}

void RegisterXAPlayItfMethods(Guest_XAPlayItf itf) {
  REGISTER_TRAMPOLINE(XAPlay, SetPlayState);
  REGISTER_TRAMPOLINE(XAPlay, GetPlayState);
  REGISTER_TRAMPOLINE(XAPlay, GetDuration);
  REGISTER_TRAMPOLINE(XAPlay, GetPosition);
  REGISTER_TRAMPOLINE(XAPlay, SetMarkerPosition);
  REGISTER_TRAMPOLINE(XAPlay, ClearMarkerPosition);
  REGISTER_TRAMPOLINE(XAPlay, GetMarkerPosition);
  REGISTER_TRAMPOLINE(XAPlay, SetPositionUpdatePeriod);
  REGISTER_TRAMPOLINE(XAPlay, GetPositionUpdatePeriod);
  REGISTER_TRAMPOLINE(XAPlay, SetCallbackEventsMask);
  REGISTER_TRAMPOLINE(XAPlay, GetCallbackEventsMask);
  REGISTER_CUSTOM_TRAMPOLINE(XAPlay, RegisterCallback);
}

// XAresult (*RegisterCallback) (XAAndroidBufferQueueItf self);
//                               xaAndroidBufferQueueCallback callback,
//                               void* pCallbackContext);
void DoCustomTrampoline_XABufferQueue_RegisterCallback(HostCode /*callee*/, ProcessState* state) {
  using PFN_callee = decltype(std::declval<XAAndroidBufferQueueItf_>().RegisterCallback);
  auto [self, guest_callback, callback_context] = GuestParamsValues<PFN_callee>(state);

  // typedef XAresult (XAAPIENTRY *xaAndroidBufferQueueCallback)(
  //   XAAndroidBufferQueueItf caller,/* input */
  //   void *pCallbackContext,        /* input */
  //   void *pBufferContext,          /* input */
  //   void *pBufferData,             /* input */
  //   XAuint32 dataSize,             /* input */
  //   XAuint32 dataUsed,             /* input */
  //   const XAAndroidBufferItem *pItems,/* input */
  //   XAuint32 itemsLength           /* input */
  // );
  auto host_callback = WrapGuestFunction(guest_callback, "XABufferQueue_RegisterCallback-callback");

  auto&& [ret] = GuestReturnReference<PFN_callee>(state);
  ret = (*self)->RegisterCallback(self, host_callback, callback_context);
}

CHECK_STRUCT_LAYOUT(XAAndroidBufferQueueState, 64, 32);
CHECK_FIELD_LAYOUT(XAAndroidBufferQueueState, count, 0, 32);
CHECK_FIELD_LAYOUT(XAAndroidBufferQueueState, index, 32, 32);

void RegisterXAAndroidBufferQueueItfMethods(Guest_XAAndroidBufferQueueItf itf) {
  REGISTER_CUSTOM_TRAMPOLINE(XABufferQueue, RegisterCallback);
  REGISTER_TRAMPOLINE(XABufferQueue, SetCallbackEventsMask);
  REGISTER_TRAMPOLINE(XABufferQueue, Enqueue);
  REGISTER_TRAMPOLINE(XABufferQueue, Clear);
  REGISTER_TRAMPOLINE(XABufferQueue, GetState);
  REGISTER_TRAMPOLINE(XABufferQueue, GetCallbackEventsMask);
}

// XAresult (*RegisterStreamChangeCallback) (XAStreamInformationItf self,
//                                           xaStreamEventChangeCallback callback,
//                                           void * pContext);
void DoCustomTrampoline_XAStreamInformation_RegisterStreamChangeCallback(HostCode /*callee*/,
                                                                         ProcessState* state) {
  using PFN_callee = decltype(std::declval<XAStreamInformationItf_>().RegisterStreamChangeCallback);
  auto [self, guest_callback, callback_context] = GuestParamsValues<PFN_callee>(state);

  // typedef void (XAAPIENTRY * xaStreamEventChangeCallback) (
  //     XAStreamInformationItf caller, XAuint32 eventId,
  //     XAuint32 streamIndex, void * pEventData, void * pContext);
  auto host_callback = WrapGuestFunction(
      guest_callback, "XAStreamInformation_RegisterStreamChangeCallback-callback");

  auto&& [ret] = GuestReturnReference<PFN_callee>(state);
  ret = (*self)->RegisterStreamChangeCallback(self, host_callback, callback_context);
}

CHECK_STRUCT_LAYOUT(XAMediaContainerInformation, 96, 32);
CHECK_FIELD_LAYOUT(XAMediaContainerInformation, containerType, 0, 32);
CHECK_FIELD_LAYOUT(XAMediaContainerInformation, mediaDuration, 32, 32);
CHECK_FIELD_LAYOUT(XAMediaContainerInformation, numStreams, 64, 32);

void RegisterXAStreamInformationItfMethods(Guest_XAStreamInformationItf itf) {
  REGISTER_CUSTOM_TRAMPOLINE(XAStreamInformation, RegisterStreamChangeCallback);
  REGISTER_TRAMPOLINE(XAStreamInformation, QueryMediaContainerInformation);
  REGISTER_TRAMPOLINE(XAStreamInformation, QueryStreamType);
  REGISTER_TRAMPOLINE(XAStreamInformation, QueryStreamInformation);
  REGISTER_TRAMPOLINE(XAStreamInformation, QueryStreamName);
  REGISTER_TRAMPOLINE(XAStreamInformation, QueryActiveStreams);
  REGISTER_TRAMPOLINE(XAStreamInformation, SetActiveStream);
}

void RegisterXAVideoDecoderCapabilitiesItfMethods(XAVideoDecoderCapabilitiesItf itf) {
  REGISTER_TRAMPOLINE(XAVideoDecoderCapabilities, GetVideoDecoders);
  REGISTER_TRAMPOLINE(XAVideoDecoderCapabilities, GetVideoDecoderCapabilities);
}

void RegisterXAVolumeItfMethods(Guest_XAVolumeItf itf) {
  REGISTER_TRAMPOLINE(XAVolume, SetVolumeLevel);
  REGISTER_TRAMPOLINE(XAVolume, GetVolumeLevel);
  REGISTER_TRAMPOLINE(XAVolume, GetMaxVolumeLevel);
  REGISTER_TRAMPOLINE(XAVolume, SetMute);
  REGISTER_TRAMPOLINE(XAVolume, GetMute);
  REGISTER_TRAMPOLINE(XAVolume, EnableStereoPosition);
  REGISTER_TRAMPOLINE(XAVolume, IsEnabledStereoPosition);
  REGISTER_TRAMPOLINE(XAVolume, SetStereoPosition);
  REGISTER_TRAMPOLINE(XAVolume, GetStereoPosition);
}

//  XAresult (*GetInterface) (XAObjectItf self, const XAInterfaceID iid, void * pInterface);
void DoCustomTrampoline_XAObject_GetInterface(HostCode /*callee*/, ProcessState* state) {
  using PFN_callee = decltype(std::declval<XAObjectItf_>().GetInterface);
  auto [self, iid, interface] = GuestParamsValues<PFN_callee>(state);

  auto&& [ret] = GuestReturnReference<PFN_callee>(state);
  ret = (*self)->GetInterface(self, iid, interface);

  if (ret != XA_RESULT_SUCCESS) {
    return;
  }

  // Note, that iid is not an integer (see comment to Guest_XAInterfaceID).
  if (iid == XA_IID_ANDROIDBUFFERQUEUESOURCE) {
    RegisterXAAndroidBufferQueueItfMethods(*static_cast<Guest_XAAndroidBufferQueueItf*>(interface));
  } else if (iid == XA_IID_AUDIODECODERCAPABILITIES) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_AUDIODECODERCAPABILITIES");
  } else if (iid == XA_IID_AUDIOENCODER) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_AUDIOENCODER");
  } else if (iid == XA_IID_AUDIOENCODERCAPABILITIES) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_AUDIOENCODERCAPABILITIES");
  } else if (iid == XA_IID_AUDIOIODEVICECAPABILITIES) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_AUDIOIODEVICECAPABILITIES");
  } else if (iid == XA_IID_CAMERA) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_CAMERA");
  } else if (iid == XA_IID_CAMERACAPABILITIES) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_CAMERACAPABILITIES");
  } else if (iid == XA_IID_CONFIGEXTENSION) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_CONFIGEXTENSION");
  } else if (iid == XA_IID_DEVICEVOLUME) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_DEVICEVOLUME");
  } else if (iid == XA_IID_DYNAMICINTERFACEMANAGEMENT) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_DYNAMICINTERFACEMANAGEMENT");
  } else if (iid == XA_IID_DYNAMICSOURCE) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_DYNAMICSOURCE");
  } else if (iid == XA_IID_ENGINE) {
    RegisterXAEngineItfMethods(*static_cast<Guest_XAEngineItf*>(interface));
  } else if (iid == XA_IID_EQUALIZER) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_EQUALIZER");
  } else if (iid == XA_IID_IMAGECONTROLS) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_IMAGECONTROLS");
  } else if (iid == XA_IID_IMAGEDECODERCAPABILITIES) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_IMAGEDECODERCAPABILITIES");
  } else if (iid == XA_IID_IMAGEEFFECTS) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_IMAGEEFFECTS");
  } else if (iid == XA_IID_IMAGEENCODER) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_IMAGEENCODER");
  } else if (iid == XA_IID_IMAGEENCODERCAPABILITIES) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_IMAGEENCODERCAPABILITIES");
  } else if (iid == XA_IID_LED) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_LED");
  } else if (iid == XA_IID_METADATAEXTRACTION) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_METADATAEXTRACTION");
  } else if (iid == XA_IID_METADATAINSERTION) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_METADATAINSERTION");
  } else if (iid == XA_IID_METADATATRAVERSAL) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_METADATATRAVERSAL");
  } else if (iid == XA_IID_NULL) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_NULL");
  } else if (iid == XA_IID_OBJECT) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_OBJECT");
  } else if (iid == XA_IID_OUTPUTMIX) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_OUTPUTMIX");
  } else if (iid == XA_IID_PLAY) {
    RegisterXAPlayItfMethods(*static_cast<Guest_XAPlayItf*>(interface));
  } else if (iid == XA_IID_PLAYBACKRATE) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_PLAYBACKRATE");
  } else if (iid == XA_IID_PREFETCHSTATUS) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_PREFETCHSTATUS");
  } else if (iid == XA_IID_RADIO) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_RADIO");
  } else if (iid == XA_IID_RDS) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_RDS");
  } else if (iid == XA_IID_RECORD) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_RECORD");
  } else if (iid == XA_IID_SEEK) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_SEEK");
  } else if (iid == XA_IID_SNAPSHOT) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_SNAPSHOT");
  } else if (iid == XA_IID_STREAMINFORMATION) {
    RegisterXAStreamInformationItfMethods(*static_cast<Guest_XAStreamInformationItf*>(interface));
  } else if (iid == XA_IID_THREADSYNC) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_THREADSYNC");
  } else if (iid == XA_IID_VIBRA) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_VIBRA");
  } else if (iid == XA_IID_VIDEODECODERCAPABILITIES) {
    RegisterXAVideoDecoderCapabilitiesItfMethods(
        *static_cast<XAVideoDecoderCapabilitiesItf*>(interface));
  } else if (iid == XA_IID_VIDEOENCODER) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_VIDEOENCODER");
  } else if (iid == XA_IID_VIDEOENCODERCAPABILITIES) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_VIDEOENCODERCAPABILITIES");
  } else if (iid == XA_IID_VIDEOPOSTPROCESSING) {
    LOG_ALWAYS_FATAL("Unknown XA_IID_VIDEOPOSTPROCESSING");
  } else if (iid == XA_IID_VOLUME) {
    RegisterXAVolumeItfMethods(*static_cast<Guest_XAVolumeItf*>(interface));
  } else {
    LOG_ALWAYS_FATAL("Unknown XAInterfaceID");
  }
}

// XAresult (*RegisterCallback) (XAObjectItf self, xaObjectCallback callback, void * pContext);
void DoCustomTrampoline_XAObject_RegisterCallback(HostCode /*callee*/, ProcessState* state) {
  using PFN_callee = decltype(std::declval<XAObjectItf_>().RegisterCallback);
  auto [self, guest_callback, callback_context] = GuestParamsValues<PFN_callee>(state);

  // typedef void (XAAPIENTRY * xaObjectCallback) (
  //    XAObjectItf caller,
  //    const void * pContext,
  //    XAuint32 event,
  //    XAresult result,
  //    XAuint32 param,
  //    void * pInterface);
  auto host_callback = WrapGuestFunction(guest_callback, "XAObject_RegisterCallback-callback");

  auto&& [ret] = GuestReturnReference<PFN_callee>(state);
  ret = (*self)->RegisterCallback(self, host_callback, callback_context);
}

void RegisterXAObjectItfMethods(Guest_XAObjectItf itf) {
  REGISTER_TRAMPOLINE(XAObject, Realize);
  REGISTER_CUSTOM_TRAMPOLINE(XAObject, GetInterface);
  REGISTER_TRAMPOLINE(XAObject, Destroy);
  REGISTER_TRAMPOLINE(XAObject, Resume);
  REGISTER_TRAMPOLINE(XAObject, GetState);
  REGISTER_TRAMPOLINE(XAObject, AbortAsyncOperation);
  REGISTER_TRAMPOLINE(XAObject, SetPriority);
  REGISTER_TRAMPOLINE(XAObject, GetPriority);
  REGISTER_TRAMPOLINE(XAObject, SetLossOfControlInterfaces);
  REGISTER_CUSTOM_TRAMPOLINE(XAObject, RegisterCallback);
};

uint32_t DoThunk_xaCreateEngine(Guest_XAObjectItf* engine,
                                uint32_t num_options,
                                const Guest_XAEngineOption* engine_options,
                                uint32_t num_interfaces,
                                const Guest_XAInterfaceID* interface_ids,
                                uint32_t* interface_required) {
  auto res = xaCreateEngine(
      engine, num_options, engine_options, num_interfaces, interface_ids, interface_required);
  RegisterXAObjectItfMethods(*engine);
  return res;
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

DEFINE_INIT_PROXY_LIBRARY("libOpenMAXAL.so")

}  // namespace

}  // namespace berberis
