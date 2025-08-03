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

#include "berberis/native_activity/native_activity_wrapper.h"

#include <stddef.h>
#include <string.h>

#include "android/input.h"
#include "android/native_activity.h"
#include "android/native_window_jni.h"
#include "android/rect.h"
#include "berberis/guest_abi/function_wrappers.h"
#include "berberis/guest_abi/guest_arguments.h"
#include "berberis/guest_abi/guest_call.h"
#include "berberis/jni/jni_trampolines.h"
#include "berberis/native_activity/native_activity.h"

namespace berberis {

namespace {

Guest_ANativeActivity* ToGuestNativeActivity(ANativeActivity* activity) {
  return reinterpret_cast<Guest_ANativeActivity*>(activity->instance);
}

Guest_ANativeActivityCallbacks* GetGuestCallbacks(Guest_ANativeActivity* activity) {
  return ToHostAddr<Guest_ANativeActivityCallbacks>(activity->callbacks);
}

Guest_ANativeActivity* AllocGuestNativeActivity() {
  Guest_ANativeActivity* guest_activity = new Guest_ANativeActivity;
  Guest_ANativeActivityCallbacks* guest_callbacks = new Guest_ANativeActivityCallbacks;
  memset(guest_callbacks, 0, sizeof(*guest_callbacks));
  guest_activity->callbacks = ToGuestAddr(guest_callbacks);
  return guest_activity;
}

void FreeGuestNativeActivity(Guest_ANativeActivity* guest_activity) {
  delete GetGuestCallbacks(guest_activity);
  delete guest_activity;
}

void Wrap_OnStart(ANativeActivity* activity) {
  Guest_ANativeActivity* guest_activity = ToGuestNativeActivity(activity);
  GuestAddr func = GetGuestCallbacks(guest_activity)->onStart;
  if (func == 0) {
    return;
  }
  GuestCall call;
  call.AddArgGuestAddr(ToGuestAddr(guest_activity));
  call.RunVoid(func);
}

void Wrap_OnResume(ANativeActivity* activity) {
  Guest_ANativeActivity* guest_activity = ToGuestNativeActivity(activity);
  GuestAddr func = GetGuestCallbacks(guest_activity)->onResume;
  if (func == 0) {
    return;
  }
  GuestCall call;
  call.AddArgGuestAddr(ToGuestAddr(guest_activity));
  call.RunVoid(func);
}

void* Wrap_OnSaveInstanceState(ANativeActivity* activity, size_t* outSize) {
  Guest_ANativeActivity* guest_activity = ToGuestNativeActivity(activity);
  GuestAddr func = GetGuestCallbacks(guest_activity)->onSaveInstanceState;
  if (func == 0) {
    return nullptr;
  }
  GuestCall call;
  call.AddArgGuestAddr(ToGuestAddr(guest_activity));
  call.AddArgGuestAddr(ToGuestAddr(outSize));
  return ToHostAddr<void>(call.RunResGuestAddr(func));
}

void Wrap_OnPause(ANativeActivity* activity) {
  Guest_ANativeActivity* guest_activity = ToGuestNativeActivity(activity);
  GuestAddr func = GetGuestCallbacks(guest_activity)->onPause;
  if (func == 0) {
    return;
  }
  GuestCall call;
  call.AddArgGuestAddr(ToGuestAddr(guest_activity));
  call.RunVoid(func);
}

void Wrap_OnStop(ANativeActivity* activity) {
  Guest_ANativeActivity* guest_activity = ToGuestNativeActivity(activity);
  GuestAddr func = GetGuestCallbacks(guest_activity)->onStop;
  if (func == 0) {
    return;
  }
  GuestCall call;
  call.AddArgGuestAddr(ToGuestAddr(guest_activity));
  call.RunVoid(func);
}

void Wrap_OnDestroy(ANativeActivity* activity) {
  Guest_ANativeActivity* guest_activity = ToGuestNativeActivity(activity);
  GuestAddr func = GetGuestCallbacks(guest_activity)->onDestroy;
  if (func != 0) {
    GuestCall call;
    call.AddArgGuestAddr(ToGuestAddr(guest_activity));
    call.RunVoid(func);
  }
  activity->instance = nullptr;
  FreeGuestNativeActivity(guest_activity);
}

void Wrap_OnWindowFocusChanged(ANativeActivity* activity, int hasFocus) {
  Guest_ANativeActivity* guest_activity = ToGuestNativeActivity(activity);
  GuestAddr func = GetGuestCallbacks(guest_activity)->onWindowFocusChanged;
  if (func == 0) {
    return;
  }
  GuestCall call;
  call.AddArgGuestAddr(ToGuestAddr(guest_activity));
  call.AddArgInt32(hasFocus);
  call.RunVoid(func);
}

void Wrap_OnNativeWindowCreated(ANativeActivity* activity, ANativeWindow* window) {
  Guest_ANativeActivity* guest_activity = ToGuestNativeActivity(activity);
  GuestAddr func = GetGuestCallbacks(guest_activity)->onNativeWindowCreated;
  if (func == 0) {
    return;
  }
  GuestCall call;
  call.AddArgGuestAddr(ToGuestAddr(guest_activity));
  call.AddArgGuestAddr(ToGuestAddr(window));
  call.RunVoid(func);
}

void Wrap_OnNativeWindowResized(ANativeActivity* activity, ANativeWindow* window) {
  Guest_ANativeActivity* guest_activity = ToGuestNativeActivity(activity);
  GuestAddr func = GetGuestCallbacks(guest_activity)->onNativeWindowResized;
  if (func == 0) {
    return;
  }
  GuestCall call;
  call.AddArgGuestAddr(ToGuestAddr(guest_activity));
  call.AddArgGuestAddr(ToGuestAddr(window));
  call.RunVoid(func);
}

void Wrap_OnNativeWindowRedrawNeeded(ANativeActivity* activity, ANativeWindow* window) {
  Guest_ANativeActivity* guest_activity = ToGuestNativeActivity(activity);
  GuestAddr func = GetGuestCallbacks(guest_activity)->onNativeWindowRedrawNeeded;
  if (func == 0) {
    return;
  }
  GuestCall call;
  call.AddArgGuestAddr(ToGuestAddr(guest_activity));
  call.AddArgGuestAddr(ToGuestAddr(window));
  call.RunVoid(func);
}

void Wrap_OnNativeWindowDestroyed(ANativeActivity* activity, ANativeWindow* window) {
  Guest_ANativeActivity* guest_activity = ToGuestNativeActivity(activity);
  GuestAddr func = GetGuestCallbacks(guest_activity)->onNativeWindowDestroyed;
  if (func == 0) {
    return;
  }
  GuestCall call;
  call.AddArgGuestAddr(ToGuestAddr(guest_activity));
  call.AddArgGuestAddr(ToGuestAddr(window));
  call.RunVoid(func);
}

void Wrap_OnInputQueueCreated(ANativeActivity* activity, AInputQueue* queue) {
  Guest_ANativeActivity* guest_activity = ToGuestNativeActivity(activity);
  GuestAddr func = GetGuestCallbacks(guest_activity)->onInputQueueCreated;
  if (func == 0) {
    return;
  }
  GuestCall call;
  call.AddArgGuestAddr(ToGuestAddr(guest_activity));
  call.AddArgGuestAddr(ToGuestAddr(queue));
  call.RunVoid(func);
}

void Wrap_OnInputQueueDestroyed(ANativeActivity* activity, AInputQueue* queue) {
  Guest_ANativeActivity* guest_activity = ToGuestNativeActivity(activity);
  GuestAddr func = GetGuestCallbacks(guest_activity)->onInputQueueDestroyed;
  if (func == 0) {
    return;
  }
  GuestCall call;
  call.AddArgGuestAddr(ToGuestAddr(guest_activity));
  call.AddArgGuestAddr(ToGuestAddr(queue));
  call.RunVoid(func);
}

void Wrap_OnContentRectChanged(ANativeActivity* activity, const ARect* rect) {
  Guest_ANativeActivity* guest_activity = ToGuestNativeActivity(activity);
  GuestAddr func = GetGuestCallbacks(guest_activity)->onContentRectChanged;
  if (func == 0) {
    return;
  }
  GuestCall call;
  call.AddArgGuestAddr(ToGuestAddr(guest_activity));
  call.AddArgGuestAddr(ToGuestAddr(const_cast<ARect*>(rect)));
  call.RunVoid(func);
}

void Wrap_OnConfigurationChanged(ANativeActivity* activity) {
  Guest_ANativeActivity* guest_activity = ToGuestNativeActivity(activity);
  GuestAddr func = GetGuestCallbacks(guest_activity)->onConfigurationChanged;
  if (func == 0) {
    return;
  }
  GuestCall call;
  call.AddArgGuestAddr(ToGuestAddr(guest_activity));
  call.RunVoid(func);
}

void Wrap_OnLowMemory(ANativeActivity* activity) {
  Guest_ANativeActivity* guest_activity = ToGuestNativeActivity(activity);
  GuestAddr func = GetGuestCallbacks(guest_activity)->onLowMemory;
  if (func == 0) {
    return;
  }
  GuestCall call;
  call.AddArgGuestAddr(ToGuestAddr(guest_activity));
  call.RunVoid(func);
}

void WrapNativeActivityCallbacks(ANativeActivity* activity) {
  ANativeActivityCallbacks* callbacks = activity->callbacks;
  callbacks->onStart = Wrap_OnStart;
  callbacks->onResume = Wrap_OnResume;
  callbacks->onSaveInstanceState = Wrap_OnSaveInstanceState;
  callbacks->onPause = Wrap_OnPause;
  callbacks->onStop = Wrap_OnStop;
  callbacks->onDestroy = Wrap_OnDestroy;
  callbacks->onWindowFocusChanged = Wrap_OnWindowFocusChanged;
  callbacks->onNativeWindowCreated = Wrap_OnNativeWindowCreated;
  callbacks->onNativeWindowResized = Wrap_OnNativeWindowResized;
  callbacks->onNativeWindowRedrawNeeded = Wrap_OnNativeWindowRedrawNeeded;
  callbacks->onNativeWindowDestroyed = Wrap_OnNativeWindowDestroyed;
  callbacks->onInputQueueCreated = Wrap_OnInputQueueCreated;
  callbacks->onInputQueueDestroyed = Wrap_OnInputQueueDestroyed;
  callbacks->onContentRectChanged = Wrap_OnContentRectChanged;
  callbacks->onConfigurationChanged = Wrap_OnConfigurationChanged;
  callbacks->onLowMemory = Wrap_OnLowMemory;
}

// Call Native Activity creation function. If it has a different architecture,
// we pass a copy of ANativeActivity to the function. The real Native Activity
// callbacks are filled with wrappers that call real functions.
void CreateGuestNativeActivity(GuestAddr on_create,
                               ANativeActivity* activity,
                               void* saved,
                               size_t saved_size) {
  // Create a copy of ANativeActivity that will be passed to the guest function.
  Guest_ANativeActivity* guest_activity = AllocGuestNativeActivity();
  // This field is reserved for arbitrary application usage. Since we don't
  // pass pointer to real ANativeActivity to the application, we can use this
  // field for our purposes.
  activity->instance = guest_activity;
  guest_activity->host_native_activity = activity;
  guest_activity->vm = ToGuestJavaVM(activity->vm);
  guest_activity->env = ToGuestJNIEnv(activity->env);
  guest_activity->externalDataPath = ToGuestAddr(const_cast<char*>(activity->externalDataPath));
  guest_activity->internalDataPath = ToGuestAddr(const_cast<char*>(activity->internalDataPath));
  guest_activity->sdkVersion = activity->sdkVersion;
  guest_activity->activity = activity->clazz;
  guest_activity->assetManager = ToGuestAddr(activity->assetManager);
  guest_activity->obbPath = ToGuestAddr(activity->obbPath);
  GuestCall guest_call;
  guest_call.AddArgGuestAddr(ToGuestAddr(guest_activity));
  guest_call.AddArgGuestAddr(ToGuestAddr(saved));
  guest_call.AddArgGuestSize(saved_size);
  guest_call.RunVoid(on_create);

  // Real callbacks are filled with wrappers.
  WrapNativeActivityCallbacks(activity);
}

// void ANativeActivity_createFunc(
//     ANativeActivity* activity, void* savedState, size_t savedStateSize);
void RunGuestNativeActivityOnCreate(GuestAddr pc, GuestArgumentBuffer* buf) {
  auto&& [activity, saved_state, saved_state_size] =
      GuestArgumentsReferences<ANativeActivity_createFunc>(buf);
  CreateGuestNativeActivity(pc, activity, saved_state, saved_state_size);
}

}  // namespace

HostCode WrapGuestNativeActivityOnCreate(GuestAddr pc) {
  return WrapGuestFunctionImpl(pc,
                               kGuestFunctionWrapperSignature<ANativeActivity_createFunc>,
                               RunGuestNativeActivityOnCreate,
                               "ANativeActivity_onCreate");
}

}  // namespace berberis
