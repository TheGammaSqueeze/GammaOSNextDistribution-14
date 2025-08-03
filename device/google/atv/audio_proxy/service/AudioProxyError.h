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
namespace service {

enum {
  ERROR_UNEXPECTED = 1,

  // Error caused by AIDL transactions. It doesn't include client/server logical
  // failures.
  ERROR_AIDL_FAILURE = 2,

  // Error caused by HIDL transactions. It doesn't include client/server logical
  // failures.
  ERROR_HIDL_FAILURE = 3,

  // The server already has a registered IStreamProvider.
  ERROR_STREAM_PROVIDER_EXIST = 4,

  // Invalid command line args.
  ERROR_INVALID_ARGS = 5,
};

}  // namespace service
}  // namespace audio_proxy