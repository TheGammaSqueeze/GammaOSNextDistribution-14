// Copyright 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expresso or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#pragma once

#include "magma/Decoder.h"

struct RenderThreadInfoMagma {
    // Create new instance. Only call this once per thread.
    // Future calls to get() will return this instance until
    // it is destroyed.
    RenderThreadInfoMagma(uint32_t context_id);

    // Destructor.
    ~RenderThreadInfoMagma();

    // Return the current thread's instance, if any, or NULL.
    static RenderThreadInfoMagma* get();

    // Decoder state.
    // TODO(b/271593488): Support dynamic detection of host device.
    std::unique_ptr<gfxstream::magma::Decoder> mMagmaDec;
};
