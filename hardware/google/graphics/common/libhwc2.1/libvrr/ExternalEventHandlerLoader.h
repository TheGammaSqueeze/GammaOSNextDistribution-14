/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include <dlfcn.h>

#include "interface/Event.h"

namespace android::hardware::graphics::composer {

class ExternalEventHandlerLoader {
public:
    ExternalEventHandlerLoader(const char* libName, void* interface, void* host,
                               const char* panelName)
          : mLibHandle(dlopen(libName, RTLD_LAZY | RTLD_LOCAL), &dlclose) {
        if (!mLibHandle) {
            ALOGE("Unable to open %s, error = %s", libName, dlerror());
            return;
        }

        createExternalEventHandler_t createExternalEventHandler =
                reinterpret_cast<decltype(createExternalEventHandler)>(
                        dlsym(mLibHandle.get(), "createExternalEventHandler"));
        if (createExternalEventHandler == nullptr) {
            ALOGE("Unable to load createExternalEventHandler, error = %s", dlerror());
            return;
        }

        mExternalEventHandlerDestructor =
                reinterpret_cast<decltype(mExternalEventHandlerDestructor)>(
                        dlsym(mLibHandle.get(), "destroyExternalEventHandler"));
        if (mExternalEventHandlerDestructor == nullptr) {
            ALOGE("Unable to load destroyExternalEventHandler, error = %s", dlerror());
            return;
        }

        // Assign the event handler only when both the create and destroy functions are successfully
        // loaded.
        mExternalEventHandler = createExternalEventHandler(interface, host, panelName);
    }

    ~ExternalEventHandlerLoader() { mExternalEventHandlerDestructor(mExternalEventHandler); }

    ExternalEventHandler* getEventHandler() { return mExternalEventHandler; }

private:
    using RaiiLibrary = std::unique_ptr<void, decltype(dlclose)*>;

    RaiiLibrary mLibHandle;

    destroyExternalEventHandler_t mExternalEventHandlerDestructor = nullptr;

    ExternalEventHandler* mExternalEventHandler = nullptr;
};

} // namespace android::hardware::graphics::composer
