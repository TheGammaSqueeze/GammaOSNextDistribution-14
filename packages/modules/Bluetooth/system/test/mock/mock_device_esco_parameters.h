/*
 * Copyright 2021 The Android Open Source Project
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

/*
 * Generated mock file from original source file
 *   Functions generated:1
 *
 *  mockcify.pl ver 0.3.0
 */

#include <functional>

// Original included files, if any
#include "base/logging.h"
#include "device/include/esco_parameters.h"

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace device_esco_parameters {

// Shared state between mocked functions and tests
// Name: esco_parameters_for_codec
// Params: esco_codec_t codec
// Return: enh_esco_params_t
struct esco_parameters_for_codec {
  enh_esco_params_t return_value{};
  std::function<enh_esco_params_t(esco_codec_t codec)> body{
      [this](esco_codec_t /* codec */) { return return_value; }};
  enh_esco_params_t operator()(esco_codec_t codec) { return body(codec); };
};
extern struct esco_parameters_for_codec esco_parameters_for_codec;

}  // namespace device_esco_parameters
}  // namespace mock
}  // namespace test

// END mockcify generation
