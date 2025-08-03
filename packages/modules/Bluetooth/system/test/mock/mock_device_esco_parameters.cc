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
// Mock include file to share data between tests and mock
#include "test/mock/mock_device_esco_parameters.h"

#include "test/common/mock_functions.h"

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace device_esco_parameters {

// Function state capture and return values, if needed
struct esco_parameters_for_codec esco_parameters_for_codec;

}  // namespace device_esco_parameters
}  // namespace mock
}  // namespace test

// Mocked functions, if any
enh_esco_params_t esco_parameters_for_codec(esco_codec_t codec) {
  inc_func_call_count(__func__);
  return test::mock::device_esco_parameters::esco_parameters_for_codec(codec);
}

enh_esco_params_t esco_parameters_for_codec(esco_codec_t codec, bool /* b */) {
  inc_func_call_count(__func__);
  return test::mock::device_esco_parameters::esco_parameters_for_codec(codec);
}
// Mocked functions complete
// END mockcify generation
