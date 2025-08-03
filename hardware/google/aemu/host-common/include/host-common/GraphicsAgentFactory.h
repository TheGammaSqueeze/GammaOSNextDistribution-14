// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#pragma once

struct QAndroidEmulatorWindowAgent;
struct QAndroidDisplayAgent;
struct QAndroidRecordScreenAgent;
struct QAndroidMultiDisplayAgent;
struct QAndroidVmOperations;
typedef int (*LineConsumerCallback)(void* opaque, const char* buff, int len);

#define GRAPHICS_AGENTS_LIST(X)          \
    X(QAndroidEmulatorWindowAgent, emu)         \
    X(QAndroidDisplayAgent, display)         \
    X(QAndroidRecordScreenAgent, record)        \
    X(QAndroidMultiDisplayAgent, multi_display) \
    X(QAndroidVmOperations, vm)                 \

namespace android {
namespace emulation {
#define DEFINE_GRAPHICS_AGENT_GETTER(typ, name) \
    virtual const typ* const android_get_##typ() const;

// The default graphics agent factory will not do anything, it will
// leave the graphics agents intact.
//
// You an call injectGraphicsAgents multiple times with this factory.
//
// If you want to override existing agents you can subclass this factory,
// override the method of interest and call injectGraphicsAgents, it will replace
// the existing agents with the one your factory provides.
class GraphicsAgentFactory {
public:
    virtual ~GraphicsAgentFactory() = default;
    GRAPHICS_AGENTS_LIST(DEFINE_GRAPHICS_AGENT_GETTER)
};


// Call this method to inject the graphics agents into the emulator. You usually
// want to call this function *BEFORE* any calls to getGraphicsAgents are made.
//
// You can provide a factory that will be used to construct all the individual
// agents.
//
// Note: It is currently not safe to inject agents after the first injection has
// taken place.
void injectGraphicsAgents(
        const GraphicsAgentFactory& factory);

}  // namespace emulation
}  // namespace android

extern "C" {

#define GRAPHICS_AGENTS_DEFINE_POINTER(type, name) const type* name;
typedef struct GraphicsAgents {
    GRAPHICS_AGENTS_LIST(GRAPHICS_AGENTS_DEFINE_POINTER)
} GraphicsAgents;

const GraphicsAgents* getGraphicsAgents();

} // extern "C"
