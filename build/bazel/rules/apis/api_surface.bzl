# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Constants for Android API surfaces"""

PUBLIC_API = "publicapi"
SYSTEM_API = "systemapi"
TEST_API = "testapi"
MODULE_LIB_API = "module-libapi"
SYSTEM_SERVER_API = "system-serverapi"
INTRA_CORE_API = "intracoreapi"
CORE_PLATFORM_API = "core_platformapi"

# VENDOR_API is API surface provided by system to vendor
# Also known as LLNDK.
VENDOR_API = "vendorapi"

# TOOLCHAIN_API is a special API surface provided by ART to compile other API domains
# (e.g. core-lambda-stubs required to compile java files containing lambdas)
# This is not part of go/android-api-types, and is not available to apps at runtime
TOOLCHAIN_API = "toolchainapi"

ALL_API_SURFACES = [
    PUBLIC_API,
    SYSTEM_API,
    TEST_API,
    MODULE_LIB_API,
    SYSTEM_SERVER_API,
    INTRA_CORE_API,
    CORE_PLATFORM_API,
    VENDOR_API,
    TOOLCHAIN_API,
]
