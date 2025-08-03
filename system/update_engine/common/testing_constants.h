//
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
//

#ifndef UPDATE_ENGINE_COMMON_TESTING_CONSTANTS_H_
#define UPDATE_ENGINE_COMMON_TESTING_CONSTANTS_H_

[[maybe_unused]] static constexpr auto&& kUnittestPrivateKeyPath =
    "unittest_key.pem";
[[maybe_unused]] static constexpr auto&& kUnittestOTACertsPath = "otacerts.zip";
[[maybe_unused]] static constexpr auto&& kUnittestPublicKeyPath =
    "unittest_key.pub.pem";
[[maybe_unused]] static constexpr auto&& kUnittestPrivateKey2Path =
    "unittest_key2.pem";
[[maybe_unused]] static constexpr auto&& kUnittestPublicKey2Path =
    "unittest_key2.pub.pem";
[[maybe_unused]] static constexpr auto&& kUnittestPrivateKeyRSA4096Path =
    "unittest_key_RSA4096.pem";
[[maybe_unused]] static constexpr auto&& kUnittestPublicKeyRSA4096Path =
    "unittest_key_RSA4096.pub.pem";
[[maybe_unused]] static constexpr auto&& kUnittestPrivateKeyECPath =
    "unittest_key_EC.pem";
[[maybe_unused]] static constexpr auto&& kUnittestPublicKeyECPath =
    "unittest_key_EC.pub.pem";

#endif  // UPDATE_ENGINE_COMMON_TESTING_CONSTANTS_H_
