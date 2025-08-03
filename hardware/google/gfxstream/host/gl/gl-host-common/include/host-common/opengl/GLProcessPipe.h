// Copyright 2020 The Android Open Source Project
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

#include <functional>

namespace android {
namespace opengl {

// registerGLProcessPipeService() registers a "GLProcessPipe" pipe service that is
// used to detect GL process exits.
void registerGLProcessPipeService();

void forEachProcessPipeId(std::function<void(uint64_t)>);
void forEachProcessPipeIdRunAndErase(std::function<void(uint64_t)>);

}  // namespace opengl
}  // namespace android
