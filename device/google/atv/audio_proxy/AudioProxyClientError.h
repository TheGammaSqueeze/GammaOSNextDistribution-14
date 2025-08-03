// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#pragma once

namespace audio_proxy {

enum {
  ERROR_UNEXPECTED = 1,

  // The arguments don't meet requirements.
  ERROR_INVALID_ARGS = 2,

  // Failure happens when creating FMQ.
  ERROR_FMQ_CREATION_FAILURE = 3,
};

}  // namespace audio_proxy