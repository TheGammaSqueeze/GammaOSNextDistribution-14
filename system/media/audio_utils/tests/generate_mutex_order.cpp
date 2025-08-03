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

#include <iostream>

// To dump the mutex code to stdout:
//
// $ clang++ generate_mutex_order.cpp
// $ ./a.out
//

constexpr const char* mutexes[] {
  // These mutexes obey partial ordering rules.
  // 1) AudioFlinger::mutex() -> PatchCommandThread::mutex() -> MelReporter::mutex().
  // 2) If both AudioFlinger::mutex() and AudioFlinger::hardwareMutex() must be held,
  //    always take mutex() before hardwareMutex().
  // 3) AudioFlinger::clientMutex() protects mClients and mNotificationClients,
  //    must be locked after mutex() and ThreadBase::mutex() if both must be locked
  //    avoids acquiring AudioFlinger::mutex() from inside thread loop.
  // 4) AudioFlinger -> ThreadBase -> EffectChain -> EffectBase(EffectModule)
  // 5) EffectHandle -> ThreadBase -> EffectChain -> EffectBase(EffectModule)


  "Spatializer_Mutex",         // AP - must come before EffectHandle_Mutex
  "AudioPolicyEffects_Mutex",  // AP - never hold AudioPolicyEffects_Mutex while calling APS,
                               // not sure if this is still true.
  "EffectHandle_Mutex",        // AF - must be after AudioPolicyEffects_Mutex
  "EffectBase_PolicyMutex",    // AF - Held for AudioSystem::registerEffect, must come
                               // after EffectHandle_Mutex and before AudioPolicyService_Mutex

  "AudioPolicyService_Mutex",  // AP
  "CommandThread_Mutex",       // AP
  "AudioCommand_Mutex",        // AP
  "UidPolicy_Mutex",           // AP

  "AudioFlinger_Mutex",            // AF
  "AudioFlinger_HardwareMutex",    // AF
  "DeviceEffectManager_Mutex",     // AF
  "PatchCommandThread_Mutex",      // AF
  "ThreadBase_Mutex",              // AF
  "AudioFlinger_ClientMutex",      // AF
  "MelReporter_Mutex",             // AF
  "EffectChain_Mutex",             // AF
  "DeviceEffectProxy_ProxyMutex",  // AF: used for device effects (which have no chain).
  "EffectBase_Mutex",              // AF

  // These mutexes are in leaf objects
  // and are presented afterwards in arbitrary order.

  "AudioFlinger_UnregisteredWritersMutex",       // AF
  "AsyncCallbackThread_Mutex",                   // AF
  "ConfigEvent_Mutex",                           // AF
  "OutputTrack_TrackMetadataMutex",              // AF
  "PassthruPatchRecord_ReadMutex",               // AF
  "PatchCommandThread_ListenerMutex",            // AF
  "PlaybackThread_AudioTrackCbMutex",            // AF
  "AudioPolicyService_NotificationClientsMutex", // AP
  "MediaLogNotifier_Mutex",                      // AF
  "OtherMutex", // DO NOT CHANGE THIS: OtherMutex is used for mutexes without a specified order.
                // An OtherMutex will always be the lowest order mutex and cannot acquire
                // another named mutex while being held.
};

using namespace std;

// Utility program to print out the mutex
// ordering and exclusion as listed above.

int main() {
  cout << "// Lock order\n";
  cout << "enum class MutexOrder : uint32_t {\n";

  for (size_t i = 0; i < std::size(mutexes); ++i) {
      cout << "    k" << mutexes[i] << " = " << i << ",\n";
  }
  cout << "    kSize = " << std::size(mutexes) << ",\n";
  cout << "};\n";

  cout << "\n// Lock by name\n";
  cout << "inline constexpr const char* const gMutexNames[] = {\n";
  for (size_t i = 0; i < std::size(mutexes); ++i) {
      cout << "    \"" << mutexes[i] << "\",\n";
  }
  cout << "};\n";

  cout << "\n// Forward declarations\n";
  cout << "class AudioMutexAttributes;\n";
  cout << "template <typename T> class mutex_impl;\n";
  cout << "using mutex = mutex_impl<AudioMutexAttributes>;\n";

  cout << "\n// Capabilities in priority order\n"
       << "// (declaration only, value is nullptr)\n";
  const char *last = nullptr;
  for (auto mutex : mutexes) {
    if (last == nullptr) {
      cout << "inline mutex* " << mutex << ";\n";
    } else {
      cout << "inline mutex* " << mutex
           << "\n        ACQUIRED_AFTER(android::audio_utils::"
           << last << ");\n";
    }
    last = mutex;
  }
  cout << "\n";

  cout << "// Exclusion by capability\n";
  last = nullptr;
  for (size_t i = 0; i < std::size(mutexes); ++i) {
    // exclusion is defined in reverse order of priority.
    auto mutex = mutexes[std::size(mutexes) - i - 1];
    if (last == nullptr) {
      cout << "#define EXCLUDES_BELOW_" << mutex << "\n";
    } else {
      cout << "#define EXCLUDES_BELOW_" << mutex
           << " \\\n    EXCLUDES_" << last << "\n";
    }
    cout << "#define EXCLUDES_" << mutex
         << " \\\n    EXCLUDES(android::audio_utils::" << mutex << ")"
         << " \\\n    EXCLUDES_BELOW_" << mutex << "\n\n";
    last = mutex;
  }

  cout << "#define EXCLUDES_AUDIO_ALL"
       << " \\\n    EXCLUDES_" << last << "\n\n";
  return 0;
}
