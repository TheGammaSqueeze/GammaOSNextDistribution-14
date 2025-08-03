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
#include "host-common/GraphicsAgentFactory.h"

#include "vm_operations.h"
#include "multi_display_agent.h"
#include "window_agent.h"
#include "record_screen_agent.h"

#include <stdio.h>

struct QAndroidAutomationAgent;

using android::emulation::GraphicsAgentFactory;

static GraphicsAgents sGraphicsAgents{0};
static bool isInitialized = false;


const GraphicsAgents* getGraphicsAgents() {
    if (!isInitialized) {
        // Let's not get involved with undefined behavior, if this happens the
        // developer is not calling injectGraphicsAgents early enough.
        fprintf(stderr, "Accessing graphics agents before injecting them.\n");
        *(uint32_t*)(1234) = 5;
    }
    return &sGraphicsAgents;
}

#define DEFINE_GRAPHICS_AGENT_GETTER_IMPL(typ, name)                   \
    const typ* const GraphicsAgentFactory::android_get_##typ() const { \
        return sGraphicsAgents.name;                                   \
    };

GRAPHICS_AGENTS_LIST(DEFINE_GRAPHICS_AGENT_GETTER_IMPL)

#define GRAPHICS_AGENT_SETTER(typ, name) \
    sGraphicsAgents.name = factory.android_get_##typ();

void android::emulation::injectGraphicsAgents(const GraphicsAgentFactory& factory) {
    if (isInitialized) {
        // This is not necessarily an error, as there are cases where this might be acceptable.
        fprintf(stderr, "Warning: graphics agents were already injected!\n");
    }
    GRAPHICS_AGENTS_LIST(GRAPHICS_AGENT_SETTER);
    isInitialized = true;
}
